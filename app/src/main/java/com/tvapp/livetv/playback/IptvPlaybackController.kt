package com.tvapp.livetv.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.settings.IptvPlaybackPreferencesStore
import com.tvapp.livetv.settings.IptvSourcePlaybackOptions
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.diagnostics.CrashReportStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tvapp.livetv.ui.isRadioChannel
import java.util.Locale

@OptIn(UnstableApi::class)
class IptvPlaybackController(
    context: Context,
    private val playerView: PlayerView,
    private val profile: IptvPlaybackProfile = IptvPlaybackProfile.PRIMARY,
) {
    private val appContext = context.applicationContext
    private val retryHandler = Handler(Looper.getMainLooper())
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext).build()
    private var trackSelector: DefaultTrackSelector? = null
    private var player: ExoPlayer? = null
    private var mediaSourceFactory: MediaSource.Factory? = null
    private var retryCount = 0
    private var selectedVideoTrackId: String? = null
    private var released = false
    private var lastObservedPosition = C.TIME_UNSET
    private var lastProgressAt = SystemClock.elapsedRealtime()
    private var currentChannel: LiveChannel? = null
    private var hasReachedReady = false
    private var explicitLoading = true
    private var healthPhase = IptvPlaybackPhase.IDLE
    private var tuneStartedAt = 0L
    private var videoSurfaceAttachedAt = 0L
    private var firstFrameAt: Long? = null
    @Volatile private var lastFrameAt: Long? = null
    private var lastErrorCode: String? = null
    private var lastFailureClass: IptvPlaybackFailureClass? = null
    private var recoveryAttempt = 0
    private var recoveryExhausted = false
    private var bufferingStartedAt: Long? = null
    /** ElapsedRealtime deadline of the currently scheduled delayed watchdog
     *  re-prepare, or null when no recovery is pending. Kept separately from
     *  retryHandler bookkeeping so state transitions can cancel a pending
     *  attempt even after the handler callbacks were already removed. */
    private var pendingRecoveryAt: Long? = null
    private val playbackPreferencesStore = IptvPlaybackPreferencesStore(appContext)
    private var playbackPreferences = playbackPreferencesStore.load()
    private var targetBufferSeconds = playbackPreferences.targetBufferSeconds
    private var vodPlaybackSpeed = playbackPreferences.vodPlaybackSpeed
    private var muted = false
    private val preparationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var preparationJob: Job? = null
    private var bufferSaveJob: Job? = null
    private val database by lazy { TVAppDatabase.getInstance(appContext) }
    private var sourcePlaybackOptions = IptvSourcePlaybackOptions()
    private var automaticRecovery = true
    private var maximumVideoHeight = 0
    private var shortLiveParts = 0
    private var continuationExhausted = false

    /** Incremented on every play()/stop()/release(). Everything queued on
     *  retryHandler and every player callback carries the generation it was
     *  created under; work and events from an older generation are dropped at
     *  dispatch time so a channel switch cannot be corrupted by stale state. */
    private var tuneGeneration = 0L
    private val retryRunnable = Runnable {
        val generation = tuneGeneration
        player?.let { current ->
            if (generation != tuneGeneration || released) return@let
            explicitLoading = true
            tuneStartedAt = SystemClock.elapsedRealtime()
            bufferingStartedAt = null
            updateHealthPhase(IptvPlaybackPhase.PREPARING)
            current.prepare()
            current.playWhenReady = true
        }
    }
    /** Executes one deferred watchdog re-prepare. Runs on the main handler
     *  after the backoff delay; guarded by tune generation so a channel
     *  switch or stop can never let it fire into the new playback. */
    private val recoveryRunnable = Runnable {
        val generation = tuneGeneration
        pendingRecoveryAt = null
        if (released || generation != tuneGeneration) return@Runnable
        currentChannel ?: return@Runnable
        val current = player ?: return@Runnable
        explicitLoading = false
        bufferingStartedAt = SystemClock.elapsedRealtime()
        updateHealthPhase(IptvPlaybackPhase.BUFFERING)
        current.prepare()
        current.playWhenReady = true
        resetProgressObservation()
        tuneStartedAt = SystemClock.elapsedRealtime()
    }
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val generation = tuneGeneration
            if (released || currentChannel == null || generation != tuneGeneration) return
            queueLiveContinuation(generation)
            evaluateWatchdog(SystemClock.elapsedRealtime())?.let { reason ->
                if (generation == tuneGeneration && !released) {
                    recoverFromWatchdog(reason, generation)
                }
            }
            if (generation == tuneGeneration && !released) {
                retryHandler.postDelayed(this, if (continuousLiveEnabled()) 250L else WATCHDOG_INTERVAL_MS)
            }
        }
    }
    var onPlaybackError: ((PlaybackException) -> Unit)? = null
    var onPlaybackReady: (() -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    var onBuffering: ((IptvBufferingState) -> Unit)? = null
    var onContentKindChanged: ((IptvContentKind) -> Unit)? = null
    var onTracksChanged: (() -> Unit)? = null
    var onHealthChanged: ((IptvPlaybackHealthSnapshot) -> Unit)? = null
    var onRecovery: ((IptvRecoveryEvent) -> Unit)? = null

    /** Cancels a watchdog re-prepare that is still waiting out its backoff.
     *  Called when the stream makes progress on its own (READY state, first
     *  frame) or the tune ends, so a delayed attempt never fires into a
     *  recovered or replaced playback. */
    private fun cancelPendingWatchdogRecovery() {
        pendingRecoveryAt = null
        retryHandler.removeCallbacks(recoveryRunnable)
    }

    /** Schedules a backoff delay for watchdog recovery attempts beyond the
     *  first: attempt 1 re-prepares immediately, later attempts wait 2s, 4s,
     *  6s... capped at 8s so a struggling server is given room to breathe
     *  instead of being hammered with instant re-prepares. */
    private fun scheduleWatchdogRecovery() {
        val generation = tuneGeneration
        val delayMillis = iptvWatchdogRecoveryDelayMillis(
            attempt = recoveryAttempt,
            base = WATCHDOG_RECOVERY_BACKOFF_BASE_MS,
            max = WATCHDOG_RECOVERY_BACKOFF_MAX_MS,
        )
        if (delayMillis <= 0L) {
            recoveryRunnable.run()
            return
        }
        pendingRecoveryAt = SystemClock.elapsedRealtime() + delayMillis
        retryHandler.postDelayed(recoveryRunnable, delayMillis)
    }

    fun play(channel: LiveChannel, startPositionMillis: Long = 0L) {
        require(channel.source == LiveChannel.Source.IPTV)
        preparationJob?.cancel()
        released = true
        tuneGeneration++
        val generation = tuneGeneration
        retryHandler.removeCallbacks(retryRunnable)
        retryHandler.removeCallbacks(watchdogRunnable)
        cancelPendingWatchdogRecovery()
        player?.stop()
        player?.clearMediaItems()
        currentChannel = channel
        automaticRecovery = false
        healthPhase = IptvPlaybackPhase.PREPARING
        preparationJob = preparationScope.launch {
            var preparedChannel = channel
            val options = try {
                withContext(Dispatchers.IO) {
                    val source = sourceId(channel)?.let { database.iptvDao().getSource(it) }
                    preparedChannel = channel.copy(
                        userAgent = channel.userAgent ?: source?.defaultUserAgent,
                        referrer = channel.referrer ?: source?.defaultReferrer,
                        origin = channel.origin ?: source?.defaultOrigin,
                    )
                    source?.let { IptvSourcePlaybackOptions(it.liveBufferSeconds, it.vodBufferSeconds,
                        it.maximumVideoHeight, it.automaticRecovery, it.continuousLiveReconnect,
                        it.liveReconnectLeadMillis) } ?: IptvSourcePlaybackOptions()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation != tuneGeneration) return@launch
                CrashReportStore(appContext).recordDebug("IPTV_PROFILE_LOAD_FAILURE | ${error.javaClass.simpleName}")
                healthPhase = IptvPlaybackPhase.FAILED
                onPlaybackError?.invoke(PlaybackException(
                    appContext.getString(com.tvapp.livetv.R.string.iptv_profile_load_error), error, PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                ))
                return@launch
            }
            if (generation != tuneGeneration) return@launch
            try {
                playPrepared(preparedChannel, startPositionMillis, options)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                released = true
                automaticRecovery = false
                player?.stop()
                healthPhase = IptvPlaybackPhase.FAILED
                CrashReportStore(appContext).recordDebug("IPTV_PREPARATION_FAILURE | ${error.javaClass.simpleName}")
                onPlaybackError?.invoke(PlaybackException(
                    appContext.getString(com.tvapp.livetv.R.string.iptv_error_generic), error, PlaybackException.ERROR_CODE_UNSPECIFIED,
                ))
            }
        }
    }

    private fun sourceId(channel: LiveChannel): Long? = channel.inputId
        .takeIf { it.startsWith("iptv:") }?.removePrefix("iptv:")?.toLongOrNull()

    private fun playPrepared(channel: LiveChannel, startPositionMillis: Long, options: IptvSourcePlaybackOptions) {
        released = false
        retryCount = 0
        selectedVideoTrackId = null
        currentChannel = channel
        val previousBufferSeconds = targetBufferSeconds
        playbackPreferences = playbackPreferencesStore.load()
        sourcePlaybackOptions = options
        shortLiveParts = 0
        continuationExhausted = false
        val effective = options.resolve(playbackPreferences, channel.iptvContentType.equals("VOD", true))
        targetBufferSeconds = effective.bufferSeconds
        automaticRecovery = effective.automaticRecovery
        maximumVideoHeight = effective.maximumVideoHeight
        vodPlaybackSpeed = playbackPreferences.vodPlaybackSpeed
        if (player != null && previousBufferSeconds != targetBufferSeconds) {
            playerView.player = null
            player?.release()
            player = null
            trackSelector = null
        }
        hasReachedReady = false
        explicitLoading = true
        tuneStartedAt = SystemClock.elapsedRealtime()
        firstFrameAt = null
        lastFrameAt = null
        videoSurfaceAttachedAt = 0L
        lastErrorCode = null
        lastFailureClass = null
        recoveryAttempt = 0
        recoveryExhausted = false
        bufferingStartedAt = null
        resetProgressObservation()
        healthPhase = IptvPlaybackPhase.PREPARING
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setEnableDecoderFallback(true)
            // EXTENSION_RENDERER_MODE_PREFER puts software extension renderers
            // ahead of the built-in renderers, so streams the hardware decoder
            // rejects (or the device decoder fails on) fall through to the
            // bundled software decoders without a restart. Grid cells still cap
            // resolution/bitrate via IptvPlaybackProfile so 4-cell layouts do
            // not overwhelm low-end SoCs.
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
        val maximumBufferMs = if (profile == IptvPlaybackProfile.PRIMARY) {
            (targetBufferSeconds * 1_000).coerceAtLeast(MIN_BUFFER_MS)
        } else {
            SECONDARY_MAX_BUFFER_MS
        }
        val loadControl = DefaultLoadControl.Builder().apply {
            setBufferDurationsMs(
                MIN_BUFFER_MS.coerceAtMost(maximumBufferMs),
                maximumBufferMs,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_AFTER_REBUFFER_MS,
            )
            setBackBuffer(BACK_BUFFER_MS, true)
            setPrioritizeTimeOverSizeThresholds(true)
        }.build()
        val trackSelectionFactory = AdaptiveTrackSelection.Factory(
            ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            ADAPTIVE_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            ADAPTIVE_MIN_DURATION_TO_RETAIN_MS,
            ADAPTIVE_BANDWIDTH_FRACTION,
        )
        val selector = trackSelector ?: DefaultTrackSelector(appContext, trackSelectionFactory).also {
            trackSelector = it
        }
        val exoPlayer = player ?: ExoPlayer.Builder(appContext, renderersFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .build().also { created ->
            created.trackSelectionParameters = created.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(profile.maximumWidth, profile.maximumHeight)
                .setMaxVideoBitrate(profile.maximumBitrate)
                .build()
            created.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (released) return
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            if (bufferingStartedAt == null) bufferingStartedAt = SystemClock.elapsedRealtime()
                            updateHealthPhase(IptvPlaybackPhase.BUFFERING)
                            onBuffering?.invoke(
                                if (!hasReachedReady || explicitLoading) IptvBufferingState.LOADING
                                else IptvBufferingState.BUFFERING,
                            )
                        }
                        Player.STATE_READY -> {
                            bufferingStartedAt = null
                            retryHandler.removeCallbacks(retryRunnable)
                            retryCount = 0
                            cancelPendingWatchdogRecovery()
                            hasReachedReady = true
                            explicitLoading = false
                            updateHealthPhase(IptvPlaybackPhase.READY)
                            onBuffering?.invoke(IptvBufferingState.NONE)
                            onPlaybackReady?.invoke()
                            onContentKindChanged?.invoke(contentKind())
                        }
                        Player.STATE_ENDED, Player.STATE_IDLE -> {
                            bufferingStartedAt = null
                            cancelPendingWatchdogRecovery()
                            updateHealthPhase(
                                if (playbackState == Player.STATE_ENDED) {
                                    IptvPlaybackPhase.ENDED
                                } else {
                                    IptvPlaybackPhase.IDLE
                                },
                            )
                            onBuffering?.invoke(IptvBufferingState.NONE)
                            if (playbackState == Player.STATE_ENDED) {
                                if (continuousLiveEnabled()) {
                                    queueLiveContinuation(tuneGeneration)
                                    return
                                }
                                onPlaybackEnded?.invoke()
                            }
                            if (
                                playbackState == Player.STATE_ENDED &&
                                !currentChannel?.iptvContentType.equals("VOD", ignoreCase = true)
                            ) {
                                val generation = tuneGeneration
                                retryHandler.post {
                                    if (!released && currentChannel != null && generation == tuneGeneration) {
                                        recoverFromWatchdog(IptvRecoveryReason.LIVE_STREAM_ENDED)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    lastErrorCode = error.errorCodeName
                    lastFailureClass = classifyIptvPlaybackFailure(error.errorCodeName)
                    updateHealthPhase(IptvPlaybackPhase.FAILED)
                    // Connection-type failures (DNS, timeout, HTTP status) rarely
                    // recover by re-preparing the same URI: retry once quickly so a
                    // transient blip is covered, then surface the error so the
                    // alternative-stream path in the app can take over instead of
                    // stalling the screen in silent retries for several seconds.
                    val isConnectionFailure = lastFailureClass in CONNECTION_FAILURE_CLASSES
                    val maxRetries = if (isConnectionFailure) 1 else MAX_RETRY_COUNT
                    if (!released && automaticRecovery && retryCount < maxRetries) {
                        val delay = if (isConnectionFailure) {
                            CONNECTION_RETRY_DELAY_MS
                        } else {
                            RETRY_BASE_DELAY_MS * (1L shl retryCount)
                        }
                        retryCount++
                        retryHandler.removeCallbacks(retryRunnable)
                        retryHandler.postDelayed(retryRunnable, delay)
                    } else {
                        onPlaybackError?.invoke(error)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (released || !continuousLiveEnabled() || reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                    val generation = tuneGeneration
                    retryHandler.post {
                        if (generation != tuneGeneration || released) return@post
                        val current = player ?: return@post
                        if (current.currentMediaItemIndex > 0) {
                            current.removeMediaItems(0, current.currentMediaItemIndex)
                            resetProgressObservation()
                            CrashReportStore(appContext).recordDebug("IPTV_LIVE_CONTINUATION | transitioned")
                        }
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady || released || !continuousLiveEnabled()) return
                    val current = player ?: return
                    val next = current.currentMediaItemIndex + 1
                    if (next < current.mediaItemCount) current.removeMediaItems(next, current.mediaItemCount)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    onTracksChanged?.invoke()
                }

                override fun onRenderedFirstFrame() {
                    val now = SystemClock.elapsedRealtime()
                    if (firstFrameAt == null) firstFrameAt = now
                    lastFrameAt = now
                    cancelPendingWatchdogRecovery()
                    recoveryAttempt = 0
                    recoveryExhausted = false
                    onHealthChanged?.invoke(healthSnapshot())
                }
            })
            created.setVideoFrameMetadataListener { _, _, _, _ ->
                val now = SystemClock.elapsedRealtime()
                if (now - (lastFrameAt ?: 0L) >= FRAME_HEALTH_SAMPLE_INTERVAL_MS) {
                    lastFrameAt = now
                }
            }
            player = created
            playerView.player = created
        }
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(profile.maximumWidth, minOf(
                profile.maximumHeight, maximumVideoHeight.takeIf { it > 0 } ?: Int.MAX_VALUE,
            ))
            .setMaxVideoBitrate(profile.maximumBitrate)
            .build()
        exoPlayer.volume = if (muted) 0f else 1f
        playerView.setKeepContentOnPlayerReset(continuousLiveEnabled())
        exoPlayer.setPreloadConfiguration(if (continuousLiveEnabled())
            ExoPlayer.PreloadConfiguration(sourcePlaybackOptions.reconnectLeadMillis() * 1_000L)
            else ExoPlayer.PreloadConfiguration.DEFAULT)
        val dataSource = IptvDataSourceFactory.create(channel.userAgent, channel.referrer, channel.origin)
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(channel.uri)
            .setMediaId(channel.sourceKey)
        if (channel.iptvContentType.equals("LIVE", ignoreCase = true)) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(liveTargetOffsetMillis())
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build(),
            )
        }
        channel.subtitleUrl?.takeIf(String::isNotBlank)?.let { subtitleUrl ->
            mediaItemBuilder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                        .setMimeType(subtitleMimeType(subtitleUrl))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        val mediaItem = mediaItemBuilder.build()
        val sourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(appContext, dataSource).setTransferListener(bandwidthMeter),
            IptvDataSourceFactory.createExtractors(),
        )
        mediaSourceFactory = sourceFactory
        val mediaSource = sourceFactory.createMediaSource(mediaItem)
        if (startPositionMillis > 0L) {
            exoPlayer.setMediaSource(mediaSource, startPositionMillis)
        } else {
            exoPlayer.setMediaSource(mediaSource)
        }
        exoPlayer.prepare()
        exoPlayer.setPlaybackSpeed(if (channel.iptvContentType.equals("VOD", true)) vodPlaybackSpeed else 1f)
        exoPlayer.playWhenReady = true
        if (automaticRecovery || continuousLiveEnabled()) retryHandler.postDelayed(watchdogRunnable, 500L)
        onHealthChanged?.invoke(healthSnapshot())
    }

    fun stop() {
        preparationJob?.cancel()
        tuneGeneration++
        retryHandler.removeCallbacks(retryRunnable)
        retryHandler.removeCallbacks(watchdogRunnable)
        cancelPendingWatchdogRecovery()
        retryCount = 0
        onBuffering?.invoke(IptvBufferingState.NONE)
        player?.stop()
        player?.clearMediaItems()
        currentChannel = null
        bufferingStartedAt = null
        updateHealthPhase(IptvPlaybackPhase.IDLE)
    }

    /** Source key this controller is currently tuned to, or null when idle.
     *  Used by the UI to discard stale callbacks that were queued for a
     *  previous channel while a single shared player instance is reused. */
    fun tunedSourceKey(): String? = currentChannel?.sourceKey

    fun reattachVideoSurface() {
        val activePlayer = player ?: return
        cancelPendingWatchdogRecovery()
        videoSurfaceAttachedAt = SystemClock.elapsedRealtime()
        lastObservedPosition = C.TIME_UNSET
        playerView.player = null
        playerView.player = activePlayer
    }

    fun contentKind(): IptvContentKind {
        val current = player ?: return IptvContentKind.UNKNOWN
        return when {
            continuousLiveEnabled() -> IptvContentKind.LIVE
            current.isCurrentMediaItemLive -> IptvContentKind.LIVE
            current.duration != C.TIME_UNSET && current.duration > 0L -> IptvContentKind.VOD
            else -> IptvContentKind.UNKNOWN
        }
    }

    fun togglePlayPause() {
        player?.let { current -> if (current.isPlaying) current.pause() else current.play() }
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        cancelPendingWatchdogRecovery()
        player?.pause()
    }

    fun stopVod() {
        player?.let { current ->
            current.pause()
            current.seekTo(0L)
        }
    }

    fun restartVod() {
        player?.let { current ->
            current.seekTo(0L)
            current.play()
        }
    }

    val currentPosition: Long
        get() = player?.currentPosition ?: 0L

    val duration: Long
        get() = player?.duration?.takeUnless { it == C.TIME_UNSET } ?: 0L

    fun isCurrentStreamLive(): Boolean {
        val current = player ?: return true
        return current.isCurrentMediaItemLive || current.duration == C.TIME_UNSET
    }

    fun seekTo(positionMillis: Long): Boolean {
        val current = player ?: return false
        val total = current.duration.takeUnless { it == C.TIME_UNSET } ?: Long.MAX_VALUE
        current.seekTo(positionMillis.coerceIn(0L, total))
        return true
    }

    fun seekBy(offsetMillis: Long): Boolean {
        val current = player ?: return false
        if (contentKind() != IptvContentKind.VOD && !current.isCurrentMediaItemSeekable) return false
        val duration = current.duration.takeUnless { it == C.TIME_UNSET } ?: Long.MAX_VALUE
        current.seekTo((current.currentPosition + offsetMillis).coerceIn(0L, duration))
        return true
    }

    fun goLive(): Boolean {
        val current = player ?: return false
        if (!current.isCurrentMediaItemLive) return false
        current.seekToDefaultPosition()
        current.play()
        return true
    }

    fun retry(): Boolean {
        if (continuousLiveEnabled()) {
            currentChannel?.let { play(it); return true }
        }
        if (released) {
            currentChannel?.let { play(it); return true }
            return false
        }
        val current = player ?: return false
        retryHandler.removeCallbacks(retryRunnable)
        cancelPendingWatchdogRecovery()
        retryCount = 0
        recoveryAttempt = 0
        recoveryExhausted = false
        explicitLoading = true
        current.prepare()
        current.playWhenReady = true
        return true
    }

    fun catchUpToLive(maximumOffsetMillis: Long): Boolean {
        val current = player ?: return false
        if (
            !current.isCurrentMediaItemLive ||
            current.playbackState != Player.STATE_READY ||
            !current.isPlaying
        ) {
            return false
        }
        val offset = current.currentLiveOffset
        if (offset == C.TIME_UNSET || offset <= maximumOffsetMillis) return false
        current.seekToDefaultPosition()
        current.play()
        return true
    }

    fun targetBufferSeconds(): Int = targetBufferSeconds

    fun automaticRecoveryEnabled(): Boolean = automaticRecovery

    fun setTargetBufferSeconds(seconds: Int): Int {
        if (profile != IptvPlaybackProfile.PRIMARY) return targetBufferSeconds
        val updated = seconds.takeIf { it in com.tvapp.livetv.settings.IptvPlaybackPreferences.BUFFER_OPTIONS }
            ?: return targetBufferSeconds
        if (updated == targetBufferSeconds) return updated
        targetBufferSeconds = updated
        val channel = currentChannel
        val isVod = channel?.iptvContentType.equals("VOD", true)
        val sourceBuffer = if (isVod) sourcePlaybackOptions.vodBufferSeconds else sourcePlaybackOptions.liveBufferSeconds
        val id = channel?.let(::sourceId)
        if (sourceBuffer == null || id == null) playbackPreferencesStore.saveTargetBufferSeconds(updated)
        channel ?: return updated
        val resumePosition = player?.currentPosition?.takeIf {
            contentKind() == IptvContentKind.VOD
        } ?: 0L
        val generation = tuneGeneration
        bufferSaveJob?.cancel()
        bufferSaveJob = preparationScope.launch {
            try {
                if (sourceBuffer != null && id != null) withContext(Dispatchers.IO) {
                    if (isVod) database.iptvDao().updateVodBuffer(id, updated)
                    else database.iptvDao().updateLiveBuffer(id, updated)
                }
                if (generation != tuneGeneration) return@launch
                // LoadControl cannot be changed on an existing player.
                playerView.player = null
                player?.release()
                player = null
                trackSelector = null
                play(channel, resumePosition)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CrashReportStore(appContext).recordDebug("IPTV_PROFILE_SAVE_FAILURE | ${error.javaClass.simpleName}")
            }
        }
        return updated
    }

    fun vodPlaybackSpeed(): Float = vodPlaybackSpeed



    fun setVodPlaybackSpeed(speed: Float): Float {
        if (profile != IptvPlaybackProfile.PRIMARY) return vodPlaybackSpeed
        if (speed !in com.tvapp.livetv.settings.IptvPlaybackPreferences.SPEED_OPTIONS) return vodPlaybackSpeed
        vodPlaybackSpeed = speed
        playbackPreferencesStore.saveVodPlaybackSpeed(speed)
        if (contentKind() == IptvContentKind.VOD) {
            player?.setPlaybackSpeed(speed)
        }
        return speed
    }

    fun technicalSnapshot(): IptvTechnicalSnapshot {
        val current = player
        val video = current?.videoFormat
        val audio = current?.audioFormat
        return IptvTechnicalSnapshot(
            width = video?.width?.takeIf { it > 0 },
            height = video?.height?.takeIf { it > 0 },
            videoCodec = video?.codecs ?: video?.sampleMimeType?.substringAfter('/'),
            audioCodec = audio?.codecs ?: audio?.sampleMimeType?.substringAfter('/'),
            bitrate = video?.bitrate?.takeIf { it > 0 },
            bufferedDurationMillis = current?.let {
                (it.bufferedPosition - it.currentPosition).coerceAtLeast(0L)
            } ?: 0L,
        )
    }

    fun playbackSnapshot(): IptvPlaybackSnapshot {
        val current = player
        return IptvPlaybackSnapshot(
            positionMillis = current?.currentPosition ?: 0L,
            durationMillis = current?.duration?.takeUnless { it == C.TIME_UNSET } ?: 0L,
            bufferedPositionMillis = current?.bufferedPosition ?: 0L,
            isPlaying = current?.isPlaying == true,
            isSeekable = current?.isCurrentMediaItemSeekable == true,
            kind = contentKind(),
        )
    }

    fun healthSnapshot(): IptvPlaybackHealthSnapshot {
        val now = SystemClock.elapsedRealtime()
        val current = player
        val technical = currentTechnicalInfo()
        val position = current?.currentPosition ?: 0L
        val isPlaying = current?.isPlaying == true
        if (isPlaying &&
            (lastObservedPosition == C.TIME_UNSET || position - lastObservedPosition >= 500L)
        ) {
            lastObservedPosition = position
            lastProgressAt = now
        }
        return IptvPlaybackHealthSnapshot(
            phase = healthPhase,
            contentKind = contentKind(),
            isPlaying = isPlaying,
            firstFrameRendered = firstFrameAt != null,
            startupDurationMillis = firstFrameAt?.let { (it - tuneStartedAt).coerceAtLeast(0L) },
            timeSinceLastFrameMillis = lastFrameAt?.let { (now - it).coerceAtLeast(0L) },
            timeSinceLastProgressMillis = if (current == null) null else {
                (now - lastProgressAt).coerceAtLeast(0L)
            },
            positionMillis = position,
            bufferedDurationMillis = technical.bufferedDurationMillis,
            bitrateBps = technical.bitrate,
            estimatedBandwidthBps = technical.estimatedBandwidthBps,
            width = technical.width,
            height = technical.height,
            videoCodec = technical.videoCodec,
            audioCodec = technical.audioCodec,
            droppedFrames = current?.videoDecoderCounters?.droppedBufferCount ?: 0,
            droppedFrameRate = technical.droppedFrameRate,
            framesPerSecond = technical.framesPerSecond,
            containerFormat = technical.containerFormat,
            videoProfile = technical.videoProfile,
            audioSampleRateHz = technical.audioSampleRateHz,
            audioChannelCount = technical.audioChannelCount,
            retryAttempt = retryCount,
            lastErrorCode = lastErrorCode,
            lastFailureClass = lastFailureClass,
        )
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        player?.volume = if (muted) 0f else 1f
    }

    fun audioTracks(): List<IptvTrackOption> = tracksOfType(C.TRACK_TYPE_AUDIO)

    fun subtitleTracks(): List<IptvTrackOption> = tracksOfType(C.TRACK_TYPE_TEXT)

    fun videoTracks(): List<IptvTrackOption> = tracksOfType(C.TRACK_TYPE_VIDEO)

    fun videoTrackOverrideId(): String? = selectedVideoTrackId

    fun selectAudioTrack(id: String): Boolean = selectTrack(C.TRACK_TYPE_AUDIO, id)

    fun selectVideoTrack(id: String?): Boolean {
        val current = player ?: return false
        val builder = current.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (id != null) {
            val target = findTrack(C.TRACK_TYPE_VIDEO, id) ?: return false
            builder.setOverrideForType(
                TrackSelectionOverride(target.first.mediaTrackGroup, target.second),
            )
        }
        current.trackSelectionParameters = builder.build()
        selectedVideoTrackId = id
        return true
    }

    fun selectSubtitleTrack(id: String?): Boolean {
        val current = player ?: return false
        val builder = current.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (id == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val target = findTrack(C.TRACK_TYPE_TEXT, id) ?: return false
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(
                    TrackSelectionOverride(target.first.mediaTrackGroup, target.second),
                )
        }
        current.trackSelectionParameters = builder.build()
        return true
    }

    fun addExternalSubtitle(uri: Uri, mimeType: String? = null): Boolean {
        val current = player ?: return false
        val currentItem = current.currentMediaItem ?: return false
        val sourceFactory = mediaSourceFactory ?: return false
        val position = current.currentPosition
        val shouldPlay = current.playWhenReady
        val subtitles = currentItem.localConfiguration?.subtitleConfigurations.orEmpty() +
            MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mimeType ?: subtitleMimeType(uri.toString()))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        val updatedItem = currentItem.buildUpon()
            .setSubtitleConfigurations(subtitles.distinctBy { it.uri })
            .build()
        current.setMediaSource(sourceFactory.createMediaSource(updatedItem), position)
        current.prepare()
        current.playWhenReady = shouldPlay
        return true
    }

    private fun tracksOfType(type: Int): List<IptvTrackOption> {
        val current = player ?: return emptyList()
        return current.currentTracks.groups.flatMapIndexed { groupIndex, group ->
            if (group.type != type) return@flatMapIndexed emptyList()
            (0 until group.length).map { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                IptvTrackOption(
                    id = "$groupIndex:$trackIndex",
                    language = format.language,
                    label = format.label,
                    mimeType = format.sampleMimeType,
                    selected = group.isTrackSelected(trackIndex),
                    width = format.width.takeIf { it > 0 },
                    height = format.height.takeIf { it > 0 },
                    bitrate = format.bitrate.takeIf { it > 0 },
                )
            }
        }
    }

    private fun selectTrack(type: Int, id: String): Boolean {
        val current = player ?: return false
        val target = findTrack(type, id) ?: return false
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .clearOverridesOfType(type)
            .setOverrideForType(TrackSelectionOverride(target.first.mediaTrackGroup, target.second))
            .build()
        return true
    }

    private fun findTrack(type: Int, id: String): Pair<Tracks.Group, Int>? {
        val current = player ?: return null
        val parts = id.split(':')
        val groupIndex = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val trackIndex = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val group = current.currentTracks.groups.getOrNull(groupIndex) ?: return null
        if (group.type != type || trackIndex !in 0 until group.length) return null
        return group to trackIndex
    }

    fun release() {
        preparationScope.coroutineContext.cancelChildren()
        tuneGeneration++
        released = true
        cancelPendingWatchdogRecovery()
        retryHandler.removeCallbacks(retryRunnable)
        retryHandler.removeCallbacks(watchdogRunnable)
        onBuffering?.invoke(IptvBufferingState.NONE)
        playerView.player = null
        player?.release()
        player = null
        trackSelector = null
        mediaSourceFactory = null
        currentChannel = null
        bufferingStartedAt = null
        updateHealthPhase(IptvPlaybackPhase.RELEASED)
    }

    fun currentTechnicalInfo(): IptvTechnicalSnapshot {
        val p = player ?: return IptvTechnicalSnapshot()
        val videoFormat = p.videoFormat
        val videoWidth = videoFormat?.width?.takeIf { it > 0 } ?: p.videoSize.width.takeIf { it > 0 }
        val videoHeight = videoFormat?.height?.takeIf { it > 0 } ?: p.videoSize.height.takeIf { it > 0 }
        val videoList = videoTracks()
        val isAdaptive = videoList.size > 1 && selectedVideoTrackId == null
        val estimatedBw = bandwidthMeter.bitrateEstimate.takeIf { it > 0 }
        val audioList = audioTracks()
        val subList = subtitleTracks()
        val allTrackFormats = p.currentTracks.groups.flatMap { group ->
            (0 until group.length).map { group.getTrackFormat(it) }
        }
        val hasDolby = allTrackFormats.any { fmt ->
            val mime = (fmt.sampleMimeType ?: "").lowercase(Locale.ROOT)
            val code = (fmt.codecs ?: "").lowercase(Locale.ROOT)
            mime.contains("ac3") || mime.contains("eac3") || mime.contains("dolby") ||
                code.contains("ac-3") || code.contains("ec-3")
        }
        val firstAudioLang = audioList.firstOrNull { it.selected }?.language
            ?: audioList.firstOrNull()?.language
        val firstSubLang = subList.firstOrNull { it.selected }?.language
            ?: subList.firstOrNull()?.language
        return IptvTechnicalSnapshot(
            width = videoWidth,
            height = videoHeight,
            videoCodec = videoFormat?.codecs ?: videoFormat?.sampleMimeType,
            audioCodec = p.audioFormat?.codecs ?: p.audioFormat?.sampleMimeType
                ?: if (hasDolby) "dolby" else null,
            bitrate = videoFormat?.bitrate?.takeIf { it > 0 },
            bufferedDurationMillis = p.totalBufferedDuration,
            hasAudio = audioList.isNotEmpty(),
            hasSubtitles = subList.isNotEmpty(),
            audioLanguage = firstAudioLang,
            subtitleLanguage = firstSubLang,
            hasDolby = hasDolby,
            isAdaptive = isAdaptive,
            estimatedBandwidthBps = estimatedBw,
            containerFormat = videoFormat?.containerMimeType,
            videoProfile = videoFormat?.codecs,
            framesPerSecond = videoFormat?.frameRate?.takeIf { it > 0f },
            audioSampleRateHz = p.audioFormat?.sampleRate?.takeIf { it > 0 },
            audioChannelCount = p.audioFormat?.channelCount?.takeIf { it > 0 },
        )
    }

    private fun resetProgressObservation() {
        lastObservedPosition = C.TIME_UNSET
        lastProgressAt = SystemClock.elapsedRealtime()
    }

    private fun continuousLiveEnabled(): Boolean = sourcePlaybackOptions.continuousLiveReconnect &&
        currentChannel?.iptvContentType.equals("LIVE", true)

    private fun queueLiveContinuation(generation: Long) {
        if (released || generation != tuneGeneration || continuationExhausted) return
        val current = player ?: return
        if (current.playbackState != Player.STATE_READY && current.playbackState != Player.STATE_ENDED) return
        if (current.currentMediaItemIndex != 0) return
        if (!shouldQueueLiveContinuation(sourcePlaybackOptions.continuousLiveReconnect,
                currentChannel?.iptvContentType, current.playWhenReady, current.isCurrentMediaItemDynamic,
                current.isLoading, current.duration, current.currentPosition, current.bufferedPosition,
                current.currentMediaItemIndex + 1 < current.mediaItemCount,
                sourcePlaybackOptions.reconnectLeadMillis())) return
        val item = current.currentMediaItem ?: return
        val factory = mediaSourceFactory ?: return
        shortLiveParts = consecutiveShortLiveParts(shortLiveParts, current.duration)
        if (shortLiveParts > MAX_RETRY_COUNT) {
            continuationExhausted = true
            updateHealthPhase(IptvPlaybackPhase.FAILED)
            CrashReportStore(appContext).recordDebug("IPTV_LIVE_CONTINUATION | short_parts_exhausted")
            onPlaybackError?.invoke(PlaybackException(appContext.getString(com.tvapp.livetv.R.string.iptv_error_generic),
                null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            return
        }
        // Keep the current period and decoder alive; only one following part is retained.
        current.addMediaSource(factory.createMediaSource(item))
        CrashReportStore(appContext).recordDebug("IPTV_LIVE_CONTINUATION | queued | remainingMs=${(current.duration - current.currentPosition).coerceAtLeast(0)}")
        if (current.playbackState == Player.STATE_ENDED) {
            current.seekToNextMediaItem()
            current.prepare()
        }
    }

    private fun evaluateWatchdog(now: Long): IptvRecoveryReason? {
        if (!automaticRecovery) return null
        val channel = currentChannel ?: return null
        if (pendingRecoveryAt != null) return null
        if (recoveryExhausted) return null
        val current = player ?: return null
        if (!current.playWhenReady || current.playbackState == Player.STATE_ENDED) {
            return null
        }
        val bufferingFor = bufferingStartedAt?.let { now - it } ?: 0L
        val position = current.currentPosition
        val progressAdvanced = lastObservedPosition == C.TIME_UNSET ||
            position - lastObservedPosition >= MIN_PROGRESS_MS
        if (progressAdvanced) {
            lastObservedPosition = position
            lastProgressAt = now
            if (firstFrameAt != null || channel.isRadioChannel()) {
                recoveryAttempt = 0
                recoveryExhausted = false
            }
        }
        return decideIptvWatchdogReason(
            observation = IptvWatchdogObservation(
                expectsVideo = !channel.isRadioChannel(),
                firstFrameRendered = firstFrameAt != null,
                startupMillis = now - maxOf(tuneStartedAt, videoSurfaceAttachedAt),
                isIdle = current.playbackState == Player.STATE_IDLE,
                isBuffering = current.playbackState == Player.STATE_BUFFERING,
                bufferingMillis = bufferingFor,
                isReadyAndPlaying = current.playbackState == Player.STATE_READY && current.isPlaying,
                stalledProgressMillis = if (progressAdvanced) 0L else now - lastProgressAt,
                staleFrameMillis = lastFrameAt?.let { now - maxOf(it, videoSurfaceAttachedAt) },
            ),
            firstFrameTimeoutMillis = FIRST_FRAME_TIMEOUT_MS,
            bufferingTimeoutMillis = BUFFERING_TIMEOUT_MS,
            stallTimeoutMillis = STALL_TIMEOUT_MS,
        )
    }

    private fun recoverFromWatchdog(reason: IptvRecoveryReason, generation: Long = tuneGeneration) {
        if (!automaticRecovery) return
        val current = player ?: return
        if (generation != tuneGeneration || released) return
        recoveryAttempt++
        if (recoveryAttempt > MAX_WATCHDOG_RECOVERY_COUNT) {
            recoveryExhausted = true
            onRecovery?.invoke(
                IptvRecoveryEvent(reason, IptvRecoveryAction.EXHAUSTED, recoveryAttempt - 1),
            )
            onPlaybackError?.invoke(
                PlaybackException(
                    "IPTV watchdog exhausted: $reason",
                    IllegalStateException("IPTV watchdog exhausted: $reason"),
                    PlaybackException.ERROR_CODE_TIMEOUT,
                ),
            )
            return
        }
        onRecovery?.invoke(IptvRecoveryEvent(reason, IptvRecoveryAction.REPREPARE, recoveryAttempt))
        // Attempt 1 re-prepares immediately; later attempts wait out their
        // backoff first so an overloaded server is not re-hit every 2s. The
        // watchdog stays quiet while a recovery is pending, and the wait is
        // cancelled if the stream recovers on its own meanwhile.
        cancelPendingWatchdogRecovery()
        scheduleWatchdogRecovery()
    }

    private fun updateHealthPhase(phase: IptvPlaybackPhase) {
        if (healthPhase == phase) return
        healthPhase = phase
        onHealthChanged?.invoke(healthSnapshot())
    }

    private fun liveTargetOffsetMillis(): Long {
        if (targetBufferSeconds <= 0) return DEFAULT_LIVE_TARGET_OFFSET_MS
        return (targetBufferSeconds * 500L).coerceIn(
            MIN_LIVE_TARGET_OFFSET_MS,
            MAX_LIVE_TARGET_OFFSET_MS,
        )
    }

    private companion object {
        const val MAX_RETRY_COUNT = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
        private val CONNECTION_FAILURE_CLASSES = setOf(
            IptvPlaybackFailureClass.NETWORK,
            IptvPlaybackFailureClass.HTTP,
            IptvPlaybackFailureClass.TIMEOUT,
        )
        const val CONNECTION_RETRY_DELAY_MS = 300L
        const val MIN_BUFFER_MS = 8_000
        const val SECONDARY_MAX_BUFFER_MS = 10_000
        const val BUFFER_FOR_PLAYBACK_MS = 800
        const val BUFFER_AFTER_REBUFFER_MS = 2_500
        const val BACK_BUFFER_MS = 15_000
        const val FRAME_HEALTH_SAMPLE_INTERVAL_MS = 250L
        const val WATCHDOG_INTERVAL_MS = 2_000L
        const val FIRST_FRAME_TIMEOUT_MS = 15_000L
        const val BUFFERING_TIMEOUT_MS = 25_000L
        const val STALL_TIMEOUT_MS = 12_000L
        const val MIN_PROGRESS_MS = 500L
        const val MAX_WATCHDOG_RECOVERY_COUNT = 2
        const val WATCHDOG_RECOVERY_BACKOFF_BASE_MS = 2_000L
        const val WATCHDOG_RECOVERY_BACKOFF_MAX_MS = 8_000L
        const val ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 2_500
        const val ADAPTIVE_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 1_000
        const val ADAPTIVE_MIN_DURATION_TO_RETAIN_MS = 2_000
        const val ADAPTIVE_BANDWIDTH_FRACTION = 0.82f
        const val DEFAULT_LIVE_TARGET_OFFSET_MS = 6_000L
        const val MIN_LIVE_TARGET_OFFSET_MS = 3_000L
        const val MAX_LIVE_TARGET_OFFSET_MS = 10_000L
        fun subtitleMimeType(url: String): String = when (
            url.substringBefore('?').substringAfterLast('.', "").lowercase()
        ) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ttml", "xml" -> MimeTypes.APPLICATION_TTML
            "ssa", "ass" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
}

enum class IptvContentKind { LIVE, VOD, UNKNOWN }

enum class IptvBufferingState { NONE, LOADING, BUFFERING }

enum class IptvPlaybackProfile(
    val maximumWidth: Int,
    val maximumHeight: Int,
    val maximumBitrate: Int,
) {
    PRIMARY(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
    SECONDARY(1_280, 720, 3_000_000),
    GRID(960, 540, 1_500_000),
}

/**
 * Chooses the playback profile for a Multi-View cell based on how many cells
 * are visible. Four cells must fit low-end SoCs, so they get a tighter cap,
 * while a two-cell layout can afford the secondary (720p) profile.
 */
fun gridProfileForCellCount(cellCount: Int): IptvPlaybackProfile = when {
    cellCount >= FOUR_CELL_GRID_STREAMS -> IptvPlaybackProfile.GRID
    else -> IptvPlaybackProfile.SECONDARY
}

private const val FOUR_CELL_GRID_STREAMS = 4

data class IptvPlaybackSnapshot(
    val positionMillis: Long,
    val durationMillis: Long,
    val bufferedPositionMillis: Long,
    val isPlaying: Boolean,
    val isSeekable: Boolean,
    val kind: IptvContentKind,
)

data class IptvTrackOption(
    val id: String,
    val language: String?,
    val label: String?,
    val mimeType: String?,
    val selected: Boolean,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
)

data class IptvTechnicalSnapshot(
    val width: Int? = null,
    val height: Int? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val bitrate: Int? = null,
    val bufferedDurationMillis: Long = 0L,
    val hasAudio: Boolean = false,
    val hasSubtitles: Boolean = false,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val hasDolby: Boolean = false,
    val isAdaptive: Boolean = false,
    val estimatedBandwidthBps: Long? = null,
    val containerFormat: String? = null,
    val videoProfile: String? = null,
    val framesPerSecond: Float? = null,
    val droppedFrameRate: Float? = null,
    val audioSampleRateHz: Int? = null,
    val audioChannelCount: Int? = null,
)
