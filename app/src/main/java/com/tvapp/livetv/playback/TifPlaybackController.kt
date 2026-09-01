package com.tvapp.livetv.playback

import android.media.tv.TvTrackInfo
import android.media.tv.TvContract
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

    fun playPassthrough(inputId: String) {
        tracks = emptyList()
        tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
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
        // Some MediaTek TIF implementations keep an exact zero volume on the
        // hardware audio path after the TvView session is reset.
        tvView.setStreamVolume(if (muted) SAFE_MUTED_VOLUME else 1f)
    }

    fun stop() {
        tracks = emptyList()
        tvView.setStreamVolume(1f)
        tvView.reset()
    }

    private companion object {
        const val SAFE_MUTED_VOLUME = 0.001f
    }
}
