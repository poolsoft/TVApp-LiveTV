package com.tvapp.livetv.playback

import android.media.tv.TvTrackInfo
import android.media.tv.TvContract
import android.media.tv.TvContentRating
import android.media.tv.TvView
import android.net.Uri
import com.tvapp.livetv.model.LiveChannel

data class TifVideoState(
    val available: Boolean? = null,
    val width: Int = 0,
    val height: Int = 0,
    val unavailableReason: Int? = null,
)

data class TifCallbackEvent(
    val timestampMillis: Long,
    val name: String,
    val values: Map<String, String>,
)

class TifPlaybackController(private val tvView: TvView) {
    private var tracks: List<TvTrackInfo> = emptyList()
    private var videoState = TifVideoState()
    private val callbackEvents = java.util.ArrayDeque<TifCallbackEvent>()
    var onTracksChanged: ((List<TvTrackInfo>) -> Unit)? = null
    var onVideoStateChanged: ((available: Boolean, reason: Int?) -> Unit)? = null
    var onVideoSizeChanged: ((width: Int, height: Int) -> Unit)? = null
    var onCallbackEvent: ((TifCallbackEvent) -> Unit)? = null

    init {
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onConnectionFailed(inputId: String) {
                recordCallback("connectionFailed", "inputId" to inputId)
            }

            override fun onDisconnected(inputId: String) {
                recordCallback("disconnected", "inputId" to inputId)
            }

            override fun onChannelRetuned(inputId: String, channelUri: Uri) {
                recordCallback(
                    "channelRetuned",
                    "inputId" to inputId,
                    "channelUri" to channelUri.toString(),
                )
            }

            override fun onTracksChanged(inputId: String, tracks: List<TvTrackInfo>) {
                recordCallback(
                    "tracksChanged",
                    "inputId" to inputId,
                    "count" to tracks.size.toString(),
                    "types" to tracks.joinToString { it.type.toString() },
                )
                if (tracks.isEmpty()) return
                val merged = (tracks + this@TifPlaybackController.tracks)
                    .distinctBy { track -> track.type to track.id }
                if (merged == this@TifPlaybackController.tracks) return
                this@TifPlaybackController.tracks = merged
                onTracksChanged?.invoke(merged)
            }

            override fun onTrackSelected(inputId: String, type: Int, trackId: String?) {
                recordCallback(
                    "trackSelected",
                    "inputId" to inputId,
                    "type" to type.toString(),
                    "trackId" to (trackId ?: "null"),
                )
            }

            override fun onVideoAvailable(inputId: String) {
                recordCallback("videoAvailable", "inputId" to inputId)
                videoState = videoState.copy(available = true, unavailableReason = null)
                val currentTracks = listOf(
                    TvTrackInfo.TYPE_VIDEO, TvTrackInfo.TYPE_AUDIO, TvTrackInfo.TYPE_SUBTITLE,
                ).flatMap { tvView.getTracks(it).orEmpty() }
                if (currentTracks.isNotEmpty()) onTracksChanged(inputId, currentTracks)
                onVideoStateChanged?.invoke(true, null)
            }

            override fun onVideoSizeChanged(inputId: String, width: Int, height: Int) {
                recordCallback(
                    "videoSizeChanged",
                    "inputId" to inputId,
                    "width" to width.toString(),
                    "height" to height.toString(),
                )
                if (width <= 0 || height <= 0) return
                videoState = videoState.copy(width = width, height = height)
                onVideoSizeChanged?.invoke(width, height)
                val sizeTrack = TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, SIZE_TRACK_ID)
                    .setVideoWidth(width)
                    .setVideoHeight(height)
                    .build()
                tracks = tracks.filterNot { it.id == SIZE_TRACK_ID } + sizeTrack
                onTracksChanged?.invoke(tracks)
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                recordCallback(
                    "videoUnavailable",
                    "inputId" to inputId,
                    "reason" to reason.toString(),
                )
                videoState = videoState.copy(available = false, unavailableReason = reason)
                onVideoStateChanged?.invoke(false, reason)
            }

            override fun onContentAllowed(inputId: String) {
                recordCallback("contentAllowed", "inputId" to inputId)
            }

            override fun onContentBlocked(inputId: String, rating: TvContentRating) {
                recordCallback(
                    "contentBlocked",
                    "inputId" to inputId,
                    "rating" to rating.flattenToString(),
                )
            }

            override fun onTimeShiftStatusChanged(inputId: String, status: Int) {
                recordCallback(
                    "timeShiftStatusChanged",
                    "inputId" to inputId,
                    "status" to status.toString(),
                )
            }
        })
    }

    fun play(channel: LiveChannel) {
        require(channel.source == LiveChannel.Source.TIF)
        tracks = emptyList()
        videoState = TifVideoState()
        callbackEvents.clear()
        recordCallback(
            "tuneRequested",
            "inputId" to channel.inputId,
            "channelUri" to channel.uri,
        )
        tvView.tune(channel.inputId, Uri.parse(channel.uri))
    }

    fun playPassthrough(inputId: String) {
        tracks = emptyList()
        videoState = TifVideoState()
        callbackEvents.clear()
        recordCallback("passthroughTuneRequested", "inputId" to inputId)
        tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
    }

    fun audioTracks(): List<TvTrackInfo> = tracks.filter { it.type == TvTrackInfo.TYPE_AUDIO }

    fun allTracks(): List<TvTrackInfo> = tracks.toList()

    fun currentVideoState(): TifVideoState = videoState

    fun callbackEventHistory(): List<TifCallbackEvent> = callbackEvents.toList()

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
        videoState = TifVideoState()
        callbackEvents.clear()
        tvView.setStreamVolume(1f)
        tvView.reset()
    }

    private fun recordCallback(name: String, vararg values: Pair<String, String>) {
        val event = TifCallbackEvent(System.currentTimeMillis(), name, linkedMapOf(*values))
        if (callbackEvents.size >= MAX_CALLBACK_EVENTS) callbackEvents.removeFirst()
        callbackEvents.addLast(event)
        onCallbackEvent?.invoke(event)
    }

    private companion object {
        const val MAX_CALLBACK_EVENTS = 64
        const val SIZE_TRACK_ID = "tvapp-reported-video-size"
        const val SAFE_MUTED_VOLUME = 0.001f
    }
}
