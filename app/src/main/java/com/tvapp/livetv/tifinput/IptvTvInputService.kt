package com.tvapp.livetv.tifinput

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvInputService
import android.net.Uri
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class IptvTvInputService : TvInputService() {
    override fun onCreateSession(inputId: String): Session = PlaybackSession(this)

    @OptIn(UnstableApi::class)
    private class PlaybackSession(context: Context) : Session(context) {
        private val appContext = context.applicationContext
        private var player: ExoPlayer? = null
        private var surface: Surface? = null
        private var volume = 1f

        override fun onTune(channelUri: Uri): Boolean {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING)
            val metadata = loadMetadata(channelUri) ?: run {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                return false
            }
            releasePlayer()
            val headers = buildMap {
                metadata.referrer?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
            }
            val httpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(metadata.userAgent?.takeIf(String::isNotBlank) ?: DEFAULT_USER_AGENT)
                .setDefaultRequestProperties(headers)
            player = ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .build()
                .also { exoPlayer ->
                    exoPlayer.setVideoSurface(surface)
                    exoPlayer.volume = volume
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                notifyContentAllowed()
                                notifyVideoAvailable()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                        }
                    })
                    exoPlayer.setMediaItem(MediaItem.fromUri(metadata.streamUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            notifyChannelRetuned(channelUri)
            return true
        }

        override fun onSetSurface(surface: Surface?): Boolean {
            this.surface = surface
            player?.setVideoSurface(surface)
            return true
        }

        override fun onSetStreamVolume(volume: Float) {
            this.volume = volume
            player?.volume = volume
        }

        override fun onSetCaptionEnabled(enabled: Boolean) = Unit

        override fun onRelease() {
            releasePlayer()
            surface = null
        }

        private fun releasePlayer() {
            player?.release()
            player = null
        }

        private fun loadMetadata(channelUri: Uri): IptvInputChannelMetadata? =
            appContext.contentResolver.query(
                channelUri,
                arrayOf(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                IptvInputChannelMetadata.decode(cursor.getBlob(0))
            }

        private companion object {
            const val DEFAULT_USER_AGENT = "TVApp/0.1 AndroidTV"
        }
    }
}
