package com.tvapp.livetv.playback

import android.media.AudioManager
import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tvapp.livetv.model.LiveChannel
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.misc.ITrackInfo

/** Minimal IJK bridge. TVApp keeps ownership of every visible playback control. */
internal class IptvIjkFallbackPlayer(
    private val playerView: PlayerView,
) {
    private var player: IjkMediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var startPositionMillis = 0L
    private var playbackSpeed = 1f
    private var muted = false
    private val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(
        androidx.media3.ui.R.id.exo_content_frame,
    )

    var onPrepared: (() -> Unit)? = null
    var onFirstFrame: (() -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    var onTracksChanged: (() -> Unit)? = null
    var onVideoSizeChanged: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((Int, Int) -> Unit)? = null

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            player?.setSurface(holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            player?.setSurface(holder.surface)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            player?.setSurface(null)
        }
    }

    fun start(
        channel: LiveChannel,
        positionMillis: Long,
        speed: Float,
        initiallyMuted: Boolean,
    ): Boolean {
        release()
        // Stalker commands require a network handshake. Media3 resolves them on its loader thread;
        // the first fallback experiment deliberately avoids moving that operation onto the UI thread.
        if (channel.uri.startsWith("tvapp-stalker:", ignoreCase = true)) return false
        val videoSurface = playerView.videoSurfaceView as? SurfaceView ?: return false
        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        surfaceView = videoSurface
        startPositionMillis = positionMillis.coerceAtLeast(0L)
        playbackSpeed = speed
        muted = initiallyMuted

        val created = IjkMediaPlayer()
        player = created
        IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_WARN)
        created.setAudioStreamType(AudioManager.STREAM_MUSIC)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
        created.setOption(
            IjkMediaPlayer.OPT_CATEGORY_PLAYER,
            "mediacodec-handle-resolution-change",
            1L,
        )
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-fallback", 1L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L)
        created.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 15_000_000L)
        created.setOnPreparedListener { mediaPlayer ->
            if (player !== created) return@setOnPreparedListener
            if (startPositionMillis > 0L) mediaPlayer.seekTo(startPositionMillis)
            created.setSpeed(playbackSpeed)
            applyVolume()
            mediaPlayer.start()
            onPrepared?.invoke()
            onTracksChanged?.invoke()
        }
        created.setOnInfoListener { _, what, _ ->
            if (player !== created) return@setOnInfoListener true
            when (what) {
                IjkMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> onFirstFrame?.invoke()
                IjkMediaPlayer.MEDIA_INFO_BUFFERING_START -> onBufferingChanged?.invoke(true)
                IjkMediaPlayer.MEDIA_INFO_BUFFERING_END -> onBufferingChanged?.invoke(false)
            }
            true
        }
        created.setOnVideoSizeChangedListener { _, width, height, sarNum, sarDen ->
            if (player === created) {
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

        videoSurface.holder.addCallback(surfaceCallback)
        if (videoSurface.holder.surface.isValid) created.setSurface(videoSurface.holder.surface)
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

    fun tracks(type: Int): List<IptvTrackOption> = player?.trackInfo.orEmpty()
        .mapIndexedNotNull { index, track ->
            if (track.trackType != type) return@mapIndexedNotNull null
            IptvTrackOption(
                id = "ijk:$index",
                language = track.language,
                label = track.infoInline,
                mimeType = null,
                selected = player?.getSelectedTrack(type) == index,
            )
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
        return IptvTechnicalSnapshot(
            width = current.videoWidth.takeIf { it > 0 },
            height = current.videoHeight.takeIf { it > 0 },
            videoCodec = current.mediaInfo?.mVideoDecoder,
            audioCodec = current.mediaInfo?.mAudioDecoder,
            bufferedDurationMillis = 0L,
            hasAudio = audioTracks.isNotEmpty(),
            hasSubtitles = subtitleTracks.isNotEmpty(),
            audioLanguage = audioTracks.firstOrNull()?.language,
            subtitleLanguage = subtitleTracks.firstOrNull()?.language,
        )
    }

    fun release() {
        surfaceView?.holder?.removeCallback(surfaceCallback)
        surfaceView = null
        val current = player
        player = null
        current?.let {
            it.setSurface(null)
            it.release()
        }
        contentFrame?.setAspectRatio(0f)
        playerView.setShutterBackgroundColor(Color.BLACK)
    }

    private fun applyVolume() {
        val volume = if (muted) 0f else 1f
        player?.setVolume(volume, volume)
    }

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
