package com.tvapp.livetv.playback

import android.media.tv.TvTrackInfo
import android.media.tv.TvView
import android.net.Uri
import com.tvapp.livetv.model.LiveChannel

class TifPlaybackController(private val tvView: TvView) {
    private var tracks: List<TvTrackInfo> = emptyList()
    var onTracksChanged: ((List<TvTrackInfo>) -> Unit)? = null
    var onVideoStateChanged: ((available: Boolean, reason: Int?) -> Unit)? = null

    init {
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onTracksChanged(inputId: String, tracks: List<TvTrackInfo>) {
                if (tracks.isEmpty()) return
                val merged = (tracks + this@TifPlaybackController.tracks)
                    .distinctBy { track -> track.type to track.id }
                if (merged == this@TifPlaybackController.tracks) return
                this@TifPlaybackController.tracks = merged
                onTracksChanged?.invoke(merged)
            }

            override fun onVideoAvailable(inputId: String) {
                onVideoStateChanged?.invoke(true, null)
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                onVideoStateChanged?.invoke(false, reason)
            }
        })
    }

    fun play(channel: LiveChannel) {
        require(channel.source == LiveChannel.Source.TIF)
        tracks = emptyList()
        tvView.tune(channel.inputId, Uri.parse(channel.uri))
    }

    fun audioTracks(): List<TvTrackInfo> = tracks.filter { it.type == TvTrackInfo.TYPE_AUDIO }

    fun allTracks(): List<TvTrackInfo> = tracks.toList()

    fun subtitleTracks(): List<TvTrackInfo> = tracks.filter {
        it.type == TvTrackInfo.TYPE_SUBTITLE
    }

    fun selectedTrackId(type: Int): String? = tvView.getSelectedTrack(type)

    fun selectAudio(trackId: String) {
        tvView.selectTrack(TvTrackInfo.TYPE_AUDIO, trackId)
    }

    fun selectSubtitle(trackId: String?) {
        tvView.selectTrack(TvTrackInfo.TYPE_SUBTITLE, trackId)
    }

    fun setMuted(muted: Boolean) {
        tvView.setStreamVolume(if (muted) 0f else 1f)
    }

    fun stop() {
        tracks = emptyList()
        tvView.setStreamVolume(1f)
        tvView.reset()
    }
}
