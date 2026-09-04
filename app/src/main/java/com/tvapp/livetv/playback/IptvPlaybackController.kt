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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView
import com.tvapp.livetv.model.LiveChannel
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
    private val retryRunnable = Runnable {
        player?.let { current ->
            current.prepare()
            current.playWhenReady = true
        }
    }
    var onPlaybackError: ((PlaybackException) -> Unit)? = null
    var onPlaybackReady: (() -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null
    var onContentKindChanged: ((IptvContentKind) -> Unit)? = null
    var onTracksChanged: (() -> Unit)? = null

    fun play(channel: LiveChannel, startPositionMillis: Long = 0L) {
        require(channel.source == LiveChannel.Source.IPTV)
        released = false
        retryHandler.removeCallbacks(retryRunnable)
        retryCount = 0
        selectedVideoTrackId = null
        resetProgressObservation()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_AFTER_REBUFFER_MS,
            )
            .build()
        val trackSelectionFactory = AdaptiveTrackSelection.Factory(
            ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            ADAPTIVE_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            ADAPTIVE_MIN_DURATION_TO_RETAIN_MS,
            ADAPTIVE_BANDWIDTH_FRACTION,
        )
        val selector = trackSelector ?: DefaultTrackSelector(appContext, trackSelectionFactory).also {
            trackSelector = it
        }
        val exoPlayer = player ?: ExoPlayer.Builder(appContext)
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
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            onBuffering?.invoke(true)
                        }
                        Player.STATE_READY -> {
                            retryHandler.removeCallbacks(retryRunnable)
                            retryCount = 0
                            onBuffering?.invoke(false)
                            onPlaybackReady?.invoke()
                            onContentKindChanged?.invoke(contentKind())
                        }
                        Player.STATE_ENDED, Player.STATE_IDLE -> {
                            onBuffering?.invoke(false)
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!released && retryCount < MAX_RETRY_COUNT) {
                        val delay = RETRY_BASE_DELAY_MS * (1L shl retryCount)
                        retryCount++
                        retryHandler.removeCallbacks(retryRunnable)
                        retryHandler.postDelayed(retryRunnable, delay)
                    } else {
                        onPlaybackError?.invoke(error)
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    onTracksChanged?.invoke()
                }
            })
            player = created
            playerView.player = created
        }
        val dataSource = IptvDataSourceFactory.create(channel.userAgent, channel.referrer)
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(channel.uri)
            .setMediaId(channel.sourceKey)
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
        )
        mediaSourceFactory = sourceFactory
        val mediaSource = sourceFactory.createMediaSource(mediaItem)
        if (startPositionMillis > 0L) {
            exoPlayer.setMediaSource(mediaSource, startPositionMillis)
        } else {
            exoPlayer.setMediaSource(mediaSource)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun stop() {
        retryHandler.removeCallbacks(retryRunnable)
        retryCount = 0
        onBuffering?.invoke(false)
        player?.stop()
        player?.clearMediaItems()
    }

    fun contentKind(): IptvContentKind {
        val current = player ?: return IptvContentKind.UNKNOWN
        return when {
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
        val current = player ?: return false
        retryHandler.removeCallbacks(retryRunnable)
        retryCount = 0
        current.prepare()
        current.playWhenReady = true
        return true
    }

    fun catchUpToLive(maximumOffsetMillis: Long): Boolean {
        val current = player ?: return false
        if (!current.isCurrentMediaItemLive) return false
        val offset = current.currentLiveOffset
        if (offset == C.TIME_UNSET || offset <= maximumOffsetMillis) return false
        current.seekToDefaultPosition()
        current.play()
        return true
    }

    fun recoverIfStalled(maximumStallMillis: Long): Boolean {
        val current = player ?: return false
        val now = SystemClock.elapsedRealtime()
        if (
            !current.playWhenReady ||
            current.playbackState == Player.STATE_IDLE ||
            current.playbackState == Player.STATE_ENDED
        ) {
            lastObservedPosition = current.currentPosition
            lastProgressAt = now
            return false
        }
        val position = current.currentPosition
        if (lastObservedPosition == C.TIME_UNSET || position - lastObservedPosition >= 500L) {
            lastObservedPosition = position
            lastProgressAt = now
            return false
        }
        if (now - lastProgressAt < maximumStallMillis) return false
        if (current.isCurrentMediaItemLive) current.seekToDefaultPosition()
        else current.seekTo(position)
        current.prepare()
        current.play()
        resetProgressObservation()
        return true
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

    fun setMuted(muted: Boolean) {
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
        released = true
        retryHandler.removeCallbacks(retryRunnable)
        onBuffering?.invoke(false)
        playerView.player = null
        player?.release()
        player = null
        trackSelector = null
        mediaSourceFactory = null
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
            videoCodec = videoFormat?.sampleMimeType,
            audioCodec = if (hasDolby) "dolby" else null,
            bitrate = videoFormat?.bitrate?.takeIf { it > 0 },
            bufferedDurationMillis = p.totalBufferedDuration,
            hasAudio = audioList.isNotEmpty(),
            hasSubtitles = subList.isNotEmpty(),
            audioLanguage = firstAudioLang,
            subtitleLanguage = firstSubLang,
            hasDolby = hasDolby,
            isAdaptive = isAdaptive,
            estimatedBandwidthBps = estimatedBw,
        )
    }

    private fun resetProgressObservation() {
        lastObservedPosition = C.TIME_UNSET
        lastProgressAt = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val MAX_RETRY_COUNT = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
        const val MIN_BUFFER_MS = 4_000
        const val MAX_BUFFER_MS = 30_000
        const val BUFFER_FOR_PLAYBACK_MS = 500
        const val BUFFER_AFTER_REBUFFER_MS = 2_500
        const val ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 2_500
        const val ADAPTIVE_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 1_000
        const val ADAPTIVE_MIN_DURATION_TO_RETAIN_MS = 2_000
        const val ADAPTIVE_BANDWIDTH_FRACTION = 0.75f

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

enum class IptvPlaybackProfile(
    val maximumWidth: Int,
    val maximumHeight: Int,
    val maximumBitrate: Int,
) {
    PRIMARY(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
    SECONDARY(1_280, 720, 3_000_000),
    GRID(960, 540, 1_500_000),
}

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
)
