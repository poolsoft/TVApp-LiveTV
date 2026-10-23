package com.tvapp.livetv.ui

import android.app.Activity
import android.media.tv.TvTrackInfo
import androidx.appcompat.app.AlertDialog
import com.tvapp.livetv.R
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackController
import com.tvapp.livetv.playback.IptvTrackOption
import com.tvapp.livetv.playback.TifPlaybackController
import java.util.Locale

class TrackSelectionHelper(
    private val activity: Activity,
    private val playback: TifPlaybackController,
    private val iptvPlayback: IptvPlaybackController,
    private val getCurrentChannel: () -> LiveChannel?,
    private val showEditorError: (Int) -> Unit,
    private val rememberChannelAudioLanguage: (String?) -> Unit,
    private val rememberChannelSubtitle: (String?) -> Unit,
    private val rememberChannelSubtitlesOff: () -> Unit,
) {
    fun showAudioTracks() {
        if (getCurrentChannel()?.source == LiveChannel.Source.IPTV) {
            showIptvAudioTracks()
            return
        }
        val tracks = playback.audioTracks()
        if (tracks.isEmpty()) {
            showEditorError(R.string.tracks_not_available)
            return
        }
        val selected = playback.selectedTrackId(TvTrackInfo.TYPE_AUDIO)
        AlertDialog.Builder(activity)
            .setTitle(R.string.audio_tracks)
            .setSingleChoiceItems(
                tracks.map(::trackLabel).toTypedArray(),
                tracks.indexOfFirst { it.id == selected },
            ) { dialog, which ->
                val track = tracks[which]
                playback.selectAudio(track.id)
                rememberChannelAudioLanguage(track.language)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    fun showSubtitleTracks() {
        if (getCurrentChannel()?.source == LiveChannel.Source.IPTV) {
            showIptvSubtitleTracks()
            return
        }
        val tracks = playback.subtitleTracks()
        val selected = playback.selectedTrackId(TvTrackInfo.TYPE_SUBTITLE)
        val labels = buildList {
            add(activity.getString(R.string.subtitles_off))
            addAll(tracks.map(::trackLabel))
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.subtitle_tracks)
            .setSingleChoiceItems(labels, tracks.indexOfFirst { it.id == selected } + 1) { dialog, which ->
                if (which == 0) {
                    playback.selectSubtitle(null)
                    rememberChannelSubtitlesOff()
                } else {
                    val track = tracks[which - 1]
                    playback.selectSubtitle(track.id)
                    rememberChannelSubtitle(track.language)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    fun trackLabel(track: TvTrackInfo): String {
        val language = track.language?.takeIf(String::isNotBlank)?.let { code ->
            Locale.forLanguageTag(code).getDisplayLanguage(Locale("tr"))
                .takeIf(String::isNotBlank)
                ?: code.uppercase(Locale.ROOT)
        } ?: activity.getString(R.string.unknown_language)
        val description = track.description?.toString()?.takeIf(String::isNotBlank)
        return listOfNotNull(language, description).distinct().joinToString(" · ")
    }

    fun iptvTrackLabel(track: IptvTrackOption): String {
        val language = track.language?.takeIf(String::isNotBlank)?.let(::displayLanguage)
            ?: activity.getString(R.string.unknown_language)
        return listOfNotNull(language, track.label?.takeIf(String::isNotBlank))
            .distinct().joinToString(" · ")
    }

    fun displayLanguage(code: String): String = Locale.forLanguageTag(code)
        .getDisplayLanguage(Locale("tr"))
        .takeIf(String::isNotBlank)
        ?: code.uppercase(Locale.ROOT)

    private fun showIptvAudioTracks() {
        val tracks = iptvPlayback.audioTracks()
        if (tracks.isEmpty()) {
            showEditorError(R.string.tracks_not_available)
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.audio_tracks)
            .setSingleChoiceItems(
                tracks.map(::iptvTrackLabel).toTypedArray(),
                tracks.indexOfFirst(IptvTrackOption::selected),
            ) { dialog, which ->
                val track = tracks[which]
                iptvPlayback.selectAudioTrack(track.id)
                rememberChannelAudioLanguage(track.language)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showIptvSubtitleTracks() {
        val tracks = iptvPlayback.subtitleTracks()
        val labels = buildList {
            add(activity.getString(R.string.subtitles_off))
            addAll(tracks.map(::iptvTrackLabel))
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.subtitle_tracks)
            .setSingleChoiceItems(labels, tracks.indexOfFirst(IptvTrackOption::selected) + 1) { dialog, which ->
                if (which == 0) {
                    iptvPlayback.selectSubtitleTrack(null)
                    rememberChannelSubtitlesOff()
                } else {
                    val track = tracks[which - 1]
                    iptvPlayback.selectSubtitleTrack(track.id)
                    rememberChannelSubtitle(track.language)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }
}
