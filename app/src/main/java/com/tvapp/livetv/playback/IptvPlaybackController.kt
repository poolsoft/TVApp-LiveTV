package com.tvapp.livetv.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.tvapp.livetv.model.LiveChannel

@OptIn(UnstableApi::class)
class IptvPlaybackController(
    context: Context,
    private val playerView: PlayerView,
) {
    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    var onPlaybackError: ((PlaybackException) -> Unit)? = null
    var onPlaybackReady: (() -> Unit)? = null
    var onContentKindChanged: ((IptvContentKind) -> Unit)? = null

    fun play(channel: LiveChannel) {
        require(channel.source == LiveChannel.Source.IPTV)
        val exoPlayer = player ?: ExoPlayer.Builder(appContext).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        onPlaybackReady?.invoke()
                        onContentKindChanged?.invoke(contentKind())
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    onPlaybackError?.invoke(error)
                }
            })
            player = created
            playerView.player = created
        }
        val dataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(channel.userAgent ?: DEFAULT_USER_AGENT)
        channel.referrer?.let { referrer ->
            dataSource.setDefaultRequestProperties(mapOf("Referer" to referrer))
        }
        val mediaItem = MediaItem.Builder()
            .setUri(channel.uri)
            .setMediaId(channel.sourceKey)
            .build()
        val mediaSource = DefaultMediaSourceFactory(dataSource).createMediaSource(mediaItem)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun stop() {
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

    fun seekBy(offsetMillis: Long) {
        player?.let { current ->
            if (contentKind() != IptvContentKind.VOD) return
            val duration = current.duration.takeUnless { it == C.TIME_UNSET } ?: Long.MAX_VALUE
            current.seekTo((current.currentPosition + offsetMillis).coerceIn(0L, duration))
        }
    }

    fun goLive(): Boolean {
        val current = player ?: return false
        if (!current.isCurrentMediaItemLive) return false
        current.seekToDefaultPosition()
        current.play()
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

    fun playbackSnapshot(): IptvPlaybackSnapshot {
        val current = player
        return IptvPlaybackSnapshot(
            positionMillis = current?.currentPosition ?: 0L,
            durationMillis = current?.duration?.takeUnless { it == C.TIME_UNSET } ?: 0L,
            bufferedPositionMillis = current?.bufferedPosition ?: 0L,
            isPlaying = current?.isPlaying == true,
            kind = contentKind(),
        )
    }

    fun setMuted(muted: Boolean) {
        player?.volume = if (muted) 0f else 1f
    }

    fun release() {
        playerView.player = null
        player?.release()
        player = null
    }

    private companion object {
        const val DEFAULT_USER_AGENT = "TVApp/0.1 AndroidTV"
    }
}

enum class IptvContentKind { LIVE, VOD, UNKNOWN }

data class IptvPlaybackSnapshot(
    val positionMillis: Long,
    val durationMillis: Long,
    val bufferedPositionMillis: Long,
    val isPlaying: Boolean,
    val kind: IptvContentKind,
)
