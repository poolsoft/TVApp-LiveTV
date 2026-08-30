package com.tvapp.livetv.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

    fun play(channel: LiveChannel) {
        require(channel.source == LiveChannel.Source.IPTV)
        val exoPlayer = player ?: ExoPlayer.Builder(appContext).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) onPlaybackReady?.invoke()
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
