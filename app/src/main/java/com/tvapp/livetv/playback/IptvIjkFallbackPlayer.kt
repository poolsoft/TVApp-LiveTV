package com.tvapp.livetv.playback

import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.ui.isRadioChannel
import java.util.Locale
import tv.danmaku.ijk.media.player.IjkMediaMeta
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.misc.IMediaFormat
import tv.danmaku.ijk.media.player.misc.ITrackInfo

/** Minimal IJK bridge. TVApp keeps ownership of every visible playback control. */
internal class IptvIjkFallbackPlayer(
    private val playerView: PlayerView,
) {
    private var player: IjkMediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null
    private var textureSurface: Surface? = null
    private var startPositionMillis = 0L
    private var playbackSpeed = 1f
    private var muted = false
    private var firstFrameRendered = false
    private var videoSizeObserved = false
    private val videoStartHandler = Handler(Looper.getMainLooper())
    private val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(
        androidx.media3.ui.R.id.exo_content_frame,
    )

    var onPrepared: (() -> Unit)? = null
    var onFirstFrame: (() -> Unit)? = null
    var onFrameRendered: (() -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    var onTracksChanged: (() -> Unit)? = null
    var onVideoSizeChanged: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((Int, Int) -> Unit)? = null
    var onVideoStartTimeout: (() -> Unit)? = null

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            player?.setDisplay(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            player?.setDisplay(holder)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            player?.setDisplay(null)
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            attachTextureSurface(surface)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            attachTextureSurface(surface)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            player?.setSurface(null)
            textureSurface?.release()
            textureSurface = null
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            onFrameRendered?.invoke()
        }
    }

    fun start(
        channel: LiveChannel,
        positionMillis: Long,
        speed: Float,
        initiallyMuted: Boolean,
        forceSoftwareVideoDecoder: Boolean = false,
    ): Boolean {
        release()
        // Stalker commands require a network handshake. Media3 resolves them on its loader thread;
        // the first fallback experiment deliberately avoids moving that operation onto the UI thread.
        if (channel.uri.startsWith("tvapp-stalker:", ignoreCase = true)) return false
        val videoSurface = playerView.videoSurfaceView ?: return false
        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        playerView.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter)?.visibility =
            android.view.View.GONE
        startPositionMillis = positionMillis.coerceAtLeast(0L)
        playbackSpeed = speed
        muted = initiallyMuted
        firstFrameRendered = false
        videoSizeObserved = false

        val created = IjkMediaPlayer()
        player = created
        IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_WARN)
        created.setAudioStreamType(AudioManager.STREAM_MUSIC)
        val hardwareDecoderEnabled = if (forceSoftwareVideoDecoder) 0L else 1L
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", hardwareDecoderEnabled)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", hardwareDecoderEnabled)
        listOf(
            "mediacodec-avc",
            "mediacodec-hevc",
            "mediacodec-mpeg2",
            "mediacodec-mpeg4",
        ).forEach { codecOption ->
            created.setOption(
                IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                codecOption,
                hardwareDecoderEnabled,
            )
        }
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 0L)
        created.setOption(
            IjkMediaPlayer.OPT_CATEGORY_PLAYER,
            "mediacodec-handle-resolution-change",
            0L,
        )
        created.setOption(
            IjkMediaPlayer.OPT_CATEGORY_PLAYER,
            "mediacodec-auto-fallback",
            if (forceSoftwareVideoDecoder) 0L else 1L,
        )
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 0L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 0L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 15_000_000L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1L)
        created.setOnPreparedListener { mediaPlayer ->
            if (player !== created) return@setOnPreparedListener
            if (startPositionMillis > 0L) mediaPlayer.seekTo(startPositionMillis)
            created.setSpeed(playbackSpeed)
            applyVolume()
            mediaPlayer.start()
            onPrepared?.invoke()
            onTracksChanged?.invoke()
            videoStartHandler.postDelayed(
                {
                    val tracks = runCatching { created.trackInfo.orEmpty().toList() }
                        .getOrDefault(emptyList())
                    val hasVideoTrack = tracks.any {
                        it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_VIDEO
                    }
                    val hasAudioTrack = tracks.any {
                        it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO
                    }
                    val expectsVideo = !channel.isRadioChannel()
                    val missingVideoMetadata = tracks.isEmpty() && expectsVideo
                    if (
                        player === created &&
                        !firstFrameRendered &&
                        (videoSizeObserved || hasVideoTrack || missingVideoMetadata) &&
                        !(hasAudioTrack && !hasVideoTrack && !videoSizeObserved)
                    ) {
                        onVideoStartTimeout?.invoke()
                    }
                },
                VIDEO_START_TIMEOUT_MS,
            )
        }
        created.setOnInfoListener { _, what, _ ->
            if (player !== created) return@setOnInfoListener true
            when (what) {
                IjkMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    firstFrameRendered = true
                    videoStartHandler.removeCallbacksAndMessages(null)
                    onFirstFrame?.invoke()
                }
                IjkMediaPlayer.MEDIA_INFO_BUFFERING_START -> onBufferingChanged?.invoke(true)
                IjkMediaPlayer.MEDIA_INFO_BUFFERING_END -> onBufferingChanged?.invoke(false)
            }
            true
        }
        created.setOnVideoSizeChangedListener { _, width, height, sarNum, sarDen ->
            if (player === created) {
                videoSizeObserved = width > 0 && height > 0
                val pixelRatio = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen else 1f
                contentFrame?.setAspectRatio(
                    if (width > 0 && height > 0) width.toFloat() * pixelRatio / height else 0f,
                )
                onVideoSizeChanged?.invoke()
            }
        }
        created.setOnCompletionListener {
            if (player === created) onCompletion?.invoke()
        }
        created.setOnErrorListener { _, what, extra ->
            if (player === created) onError?.invoke(what, extra)
            true
        }

        when (videoSurface) {
            is SurfaceView -> {
                surfaceView = videoSurface
                videoSurface.holder.addCallback(surfaceCallback)
                if (videoSurface.holder.surface.isValid) created.setDisplay(videoSurface.holder)
            }
            is TextureView -> {
                textureView = videoSurface
                videoSurface.surfaceTextureListener = textureListener
                videoSurface.surfaceTexture?.takeIf { videoSurface.isAvailable }
                    ?.let(::attachTextureSurface)
            }
            else -> {
                release()
                return false
            }
        }
        val headers = buildMap {
            put("User-Agent", channel.userAgent?.takeIf(String::isNotBlank) ?: DEFAULT_USER_AGENT)
            channel.referrer?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
        }
        created.setDataSource(channel.uri, headers)
        created.prepareAsync()
        return true
    }

    fun stop() {
        player?.stop()
    }

    fun play() {
        player?.start()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.start() }
    }

    fun seekTo(positionMillis: Long) {
        player?.seekTo(positionMillis.coerceAtLeast(0L))
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        player?.setSpeed(speed)
    }

    fun setMuted(value: Boolean) {
        muted = value
        applyVolume()
    }

    fun positionMillis(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun durationMillis(): Long = player?.duration?.coerceAtLeast(0L) ?: 0L

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun bufferedDurationMillis(): Long = player?.let { current ->
        maxOf(
            runCatching { current.videoCachedDuration }.getOrDefault(0L),
            runCatching { current.audioCachedDuration }.getOrDefault(0L),
        ).coerceAtLeast(0L)
    } ?: 0L

    fun droppedFrameRate(): Float? = player?.let { current ->
        runCatching { current.dropFrameRate }.getOrNull()?.takeIf { it >= 0f }
    }

    fun tracks(type: Int): List<IptvTrackOption> {
        val current = player ?: return emptyList()
        val meta = mediaMeta(current)
        return current.trackInfo.orEmpty().mapIndexedNotNull { index, track ->
            if (track.trackType != type) return@mapIndexedNotNull null
            val stream = meta?.mStreams?.getOrNull(index)
            IptvTrackOption(
                id = "ijk:$index",
                language = track.language,
                label = track.infoInline,
                mimeType = runCatching {
                    track.format?.getString(IMediaFormat.KEY_MIME)
                }.getOrNull(),
                selected = current.getSelectedTrack(type) == index,
                width = stream?.mWidth?.takeIf { it > 0 } ?: track.formatValue(IMediaFormat.KEY_WIDTH),
                height = stream?.mHeight?.takeIf { it > 0 } ?: track.formatValue(IMediaFormat.KEY_HEIGHT),
                bitrate = stream?.mBitrate?.positiveInt(),
            )
        }
    }

    fun selectTrack(type: Int, id: String): Boolean {
        val index = id.removePrefix("ijk:").toIntOrNull() ?: return false
        val tracks = player?.trackInfo ?: return false
        if (tracks.getOrNull(index)?.trackType != type) return false
        player?.selectTrack(index)
        return true
    }

    fun clearTrack(type: Int): Boolean {
        val selected = player?.getSelectedTrack(type)?.takeIf { it >= 0 } ?: return false
        player?.deselectTrack(selected)
        return true
    }

    fun technicalSnapshot(): IptvTechnicalSnapshot {
        val current = player ?: return IptvTechnicalSnapshot()
        val tracks = current.trackInfo.orEmpty()
        val audioTracks = tracks.filter { it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO }
        val subtitleTracks = tracks.filter {
            it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_SUBTITLE ||
                it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT
        }
        val meta = mediaMeta(current)
        val video = meta?.mVideoStream
        val audio = meta?.mAudioStream
        val audioCodec = audio?.mCodecName ?: current.mediaInfo?.mAudioDecoder
        val normalizedAudioCodec = audioCodec?.lowercase(Locale.ROOT).orEmpty()
        val outputFps = runCatching { current.videoOutputFramesPerSecond }
            .getOrNull()
            ?.takeIf { it > 0f }
        val networkSpeed = runCatching { current.tcpSpeed }.getOrDefault(0L)
        return IptvTechnicalSnapshot(
            width = video?.mWidth?.takeIf { it > 0 } ?: current.videoWidth.takeIf { it > 0 },
            height = video?.mHeight?.takeIf { it > 0 } ?: current.videoHeight.takeIf { it > 0 },
            videoCodec = video?.mCodecName ?: current.mediaInfo?.mVideoDecoder,
            audioCodec = audioCodec,
            bitrate = (video?.mBitrate?.takeIf { it > 0 }
                ?: runCatching { current.bitRate }.getOrDefault(0L).takeIf { it > 0 })?.positiveInt(),
            bufferedDurationMillis = bufferedDurationMillis(),
            hasAudio = audioTracks.isNotEmpty() || audio != null,
            hasSubtitles = subtitleTracks.isNotEmpty(),
            audioLanguage = audioTracks.firstOrNull()?.language ?: audio?.mLanguage,
            subtitleLanguage = subtitleTracks.firstOrNull()?.language,
            hasDolby = normalizedAudioCodec.contains("ac3") ||
                normalizedAudioCodec.contains("eac3") ||
                normalizedAudioCodec.contains("dolby"),
            isAdaptive = tracks.count { it.trackType == ITrackInfo.MEDIA_TRACK_TYPE_VIDEO } > 1,
            estimatedBandwidthBps = networkSpeed.takeIf { it > 0 }?.let { it * 8L },
            containerFormat = meta?.mFormat,
            videoProfile = video?.mCodecProfile,
            framesPerSecond = outputFps ?: video?.frameRate(),
            droppedFrameRate = droppedFrameRate(),
            audioSampleRateHz = audio?.mSampleRate?.takeIf { it > 0 },
            audioChannelCount = audio?.mChannelLayout?.takeIf { it > 0 }
                ?.let(java.lang.Long::bitCount)
                ?.takeIf { it > 0 },
        )
    }

    fun release() {
        videoStartHandler.removeCallbacksAndMessages(null)
        surfaceView?.holder?.removeCallback(surfaceCallback)
        surfaceView = null
        textureView?.takeIf { it.surfaceTextureListener === textureListener }
            ?.surfaceTextureListener = null
        textureView = null
        textureSurface?.release()
        textureSurface = null
        val current = player
        player = null
        current?.let {
            it.setDisplay(null)
            it.setSurface(null)
            it.release()
        }
        contentFrame?.setAspectRatio(0f)
        playerView.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter)?.visibility =
            android.view.View.VISIBLE
        playerView.setShutterBackgroundColor(Color.BLACK)
    }

    private fun applyVolume() {
        val volume = if (muted) 0f else 1f
        player?.setVolume(volume, volume)
    }

    private fun attachTextureSurface(surfaceTexture: SurfaceTexture) {
        textureSurface?.release()
        textureSurface = Surface(surfaceTexture).also { player?.setSurface(it) }
    }

    private fun mediaMeta(current: IjkMediaPlayer): IjkMediaMeta? = runCatching {
        current.mediaMeta?.let(IjkMediaMeta::parse)
    }.getOrNull()

    private fun ITrackInfo.formatValue(key: String): Int? = runCatching {
        format?.getInteger(key)
    }.getOrNull()?.takeIf { it > 0 }

    private fun Long.positiveInt(): Int? = takeIf { it > 0 }
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()

    private fun IjkMediaMeta.IjkStreamMeta.frameRate(): Float? = if (mFpsNum > 0 && mFpsDen > 0) {
        mFpsNum.toFloat() / mFpsDen
    } else {
        null
    }

    private companion object {
        const val VIDEO_START_TIMEOUT_MS = 12_000L
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
