package com.tvapp.livetv.playback

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ActivityMainBinding
import com.tvapp.livetv.model.LiveChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VodControlTarget {
    BACK,
    AUDIO_SUB,
    SPEED,
    FULLSCREEN,
    TIMELINE,
    PLAY_PAUSE,
    REWIND,
    FORWARD,
    VOLUME,
    LIST,
    RETRY,
}

class VodPlayerOverlayController(
    private val binding: ActivityMainBinding,
    private val iptvPlayback: IptvPlaybackController,
    private val scope: LifecycleCoroutineScope,
    private val onOpenVodLibrary: () -> Unit,
    private val onShowSpeedChoices: () -> Unit,
    private val onShowAudioTracks: () -> Unit,
    private val onHideInfoBar: () -> Unit,
    private val getCurrentChannel: () -> LiveChannel?,
    private val getSourceName: (LiveChannel) -> String,
    private val formatPlaybackTime: (Long) -> String,
    private val getString: (Int) -> String,
    private val onOpenEpisodeList: (() -> Boolean)? = null,
    private val hasEpisodes: (() -> Boolean)? = null,
    private val onStopPlayback: (() -> Unit)? = null,
    private val onRetryPlayback: (() -> Unit)? = null,
) {
    var selectedTarget = VodControlTarget.PLAY_PAUSE
        private set

    private var controlsJob: Job? = null
    private var tickerJob: Job? = null
    var isShowingControls = false
        private set

    private var pendingTitle: String? = null
    private var pendingEpisodeTitle: String? = null
    private var episodePanelAdapter: com.tvapp.livetv.vod.VodEpisodePanelAdapter? = null

    fun setPendingTitle(title: String?, episodeTitle: String? = null) {
        pendingTitle = title
        pendingEpisodeTitle = episodeTitle
        updateControls()
    }

    fun getPendingTitle(): String? = pendingTitle
    fun getPendingEpisodeTitle(): String? = pendingEpisodeTitle

    fun setup() {
        binding.vodBtnBack.setOnClickListener {
            selectedTarget = VodControlTarget.BACK
            activateSelectedTarget()
        }
        binding.vodBtnAudioSub.setOnClickListener {
            selectedTarget = VodControlTarget.AUDIO_SUB
            activateSelectedTarget()
            showControls()
        }
        binding.vodBtnSpeed.setOnClickListener {
            selectedTarget = VodControlTarget.SPEED
            activateSelectedTarget()
            showControls()
        }
        binding.vodBtnFullscreen.setOnClickListener {
            selectedTarget = VodControlTarget.FULLSCREEN
            activateSelectedTarget()
        }
        binding.vodBtnPlayPause.setOnClickListener {
            selectedTarget = VodControlTarget.PLAY_PAUSE
            activateSelectedTarget()
            showControls()
        }
        binding.vodTimelineRow.setOnClickListener {
            selectedTarget = VodControlTarget.TIMELINE
            renderSelection()
            iptvPlayback.togglePlayPause()
            updateControls()
            showControls()
        }
        binding.vodBtnRewind.setOnClickListener {
            selectedTarget = VodControlTarget.REWIND
            activateSelectedTarget()
            showControls()
        }
        binding.vodBtnForward.setOnClickListener {
            selectedTarget = VodControlTarget.FORWARD
            activateSelectedTarget()
            showControls()
        }
        binding.vodVolumeContainer.setOnClickListener {
            selectedTarget = VodControlTarget.VOLUME
            activateSelectedTarget()
            showControls()
        }
        binding.vodBtnList.setOnClickListener {
            selectedTarget = VodControlTarget.LIST
            activateSelectedTarget()
        }
    }

    fun isEpisodePanelVisible(): Boolean {
        return binding.vodEpisodesPanel.visibility == View.VISIBLE
    }

    fun showEpisodePanel(
        episodes: List<com.tvapp.livetv.vod.VodEpisodeItem>,
        currentActiveIndex: Int = -1,
        onEpisodeSelected: (com.tvapp.livetv.vod.VodEpisodeItem) -> Unit,
    ) {
        val adapter = com.tvapp.livetv.vod.VodEpisodePanelAdapter { item ->
            hideEpisodePanel()
            onEpisodeSelected(item)
        }
        episodePanelAdapter = adapter
        binding.vodEpisodesRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
        binding.vodEpisodesRecycler.adapter = adapter
        adapter.submitList(episodes, currentActiveIndex)

        val seriesTitle = pendingTitle ?: getCurrentChannel()?.displayName ?: ""
        binding.vodEpisodesTitle.text = seriesTitle
        binding.vodEpisodesCount.text = "${episodes.size} ${getString(R.string.vod_btn_list_label)}"

        binding.vodEpisodesPanel.visibility = View.VISIBLE
        if (currentActiveIndex in episodes.indices) {
            binding.vodEpisodesRecycler.scrollToPosition(currentActiveIndex)
        }
        binding.vodEpisodesRecycler.requestFocus()
        controlsJob?.cancel() // Panel açıkken kontroller otomatik gizlenmesin
    }

    fun hideEpisodePanel() {
        binding.vodEpisodesPanel.visibility = View.GONE
        episodePanelAdapter = null
        selectedTarget = VodControlTarget.LIST
        renderSelection()
        scheduleAutoHide()
    }

    fun isOverlayVisible(): Boolean {
        return binding.vodPlayerOverlay.visibility == View.VISIBLE
    }

    fun showControls(autoHide: Boolean = true) {
        if (isShowingControls) return
        isShowingControls = true
        try {
            onHideInfoBar()
            binding.vodPlayerOverlay.visibility = View.VISIBLE
            updateControls()
            renderSelection()
            startTicker()
            if (autoHide) scheduleAutoHide()
        } finally {
            isShowingControls = false
        }
    }

    fun hideControls() {
        controlsJob?.cancel()
        tickerJob?.cancel()
        binding.vodPlayerOverlay.visibility = View.GONE
    }

    fun toggleControls() {
        if (isOverlayVisible()) hideControls() else showControls()
    }

    fun scheduleAutoHide() {
        controlsJob?.cancel()
        if (binding.iptvBufferingContainer.visibility == View.VISIBLE ||
            binding.blackoutView.visibility == View.VISIBLE ||
            binding.playbackErrorContainer.visibility == View.VISIBLE
        ) {
            return
        }
        val snapshot = iptvPlayback.playbackSnapshot()
        val health = iptvPlayback.healthSnapshot()
        if (!snapshot.isPlaying || health.phase != IptvPlaybackPhase.READY) {
            return
        }
        controlsJob = scope.launch {
            delay(8_000L)
            hideControls()
        }
    }

    fun isBufferingOrConnecting(): Boolean {
        if (binding.iptvBufferingContainer.visibility == View.VISIBLE ||
            binding.blackoutView.visibility == View.VISIBLE
        ) {
            return true
        }
        val health = iptvPlayback.healthSnapshot()
        return health.phase == IptvPlaybackPhase.PREPARING ||
            health.phase == IptvPlaybackPhase.BUFFERING
    }

    private fun isErrorVisible(): Boolean {
        return binding.playbackErrorContainer.visibility == View.VISIBLE
    }

    fun updateControls() {
        val state = iptvPlayback.playbackSnapshot()
        val channel = getCurrentChannel()?.takeIf { it.isVodContent() }
        val effectiveTitle = pendingTitle?.takeIf { it.isNotBlank() }
            ?: channel?.displayName?.takeIf { it.isNotBlank() }
        val episodeInfo: String? = pendingEpisodeTitle?.takeIf { it.isNotBlank() }
            ?: channel?.episodeTitle?.takeIf { it.isNotBlank() }
        if (episodeInfo != null && effectiveTitle != null) {
            binding.vodPlayerTitle.text = if (effectiveTitle.contains(episodeInfo, ignoreCase = true)) {
                effectiveTitle
            } else {
                "$effectiveTitle · $episodeInfo"
            }
            binding.vodPlayerSubtitle.text = episodeInfo
        } else {
            binding.vodPlayerTitle.text = effectiveTitle ?: getString(R.string.iptv_library_vod)
            binding.vodPlayerSubtitle.text = channel?.let { getSourceName(it) } ?: getString(R.string.iptv_library_vod)
        }
        binding.vodPlayerClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val duration = state.durationMillis.coerceAtLeast(1L)
        binding.vodProgressBar.isIndeterminate = state.durationMillis <= 0L
        binding.vodProgressBar.progress =
            ((state.positionMillis * 1_000L / duration).coerceIn(0L, 1_000L)).toInt()
        binding.vodProgressBar.secondaryProgress =
            ((state.bufferedPositionMillis * 1_000L / duration).coerceIn(0L, 1_000L)).toInt()

        binding.vodTimeCurrent.text = if (state.playbackSpeed != null) {
            formatPlaybackTime(state.positionMillis)
        } else {
            getString(R.string.unknown_value)
        }

        binding.vodTimeDuration.text = if (state.durationMillis > 0) {
            formatPlaybackTime(state.durationMillis)
        } else {
            getString(R.string.unknown_value)
        }

        val isBuffering = isBufferingOrConnecting()
        if (isBuffering) {
            binding.vodBtnPlayPause.setImageResource(R.drawable.ic_stop)
        } else {
            val isPlaying = state.isPlaying
            binding.vodBtnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        val episodesAvailable = hasEpisodes?.invoke() == true
        binding.vodBtnList.visibility = if (episodesAvailable) View.VISIBLE else View.GONE
        if (!episodesAvailable && selectedTarget == VodControlTarget.LIST) {
            selectedTarget = VodControlTarget.PLAY_PAUSE
        }

        updateVolumeUi()
    }

    private fun getAudioManager(): AudioManager? {
        return binding.root.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun updateVolumeUi() {
        val am = getAudioManager() ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        binding.vodVolumeBar.progress = (current * 100 / max).coerceIn(0, 100)
    }

    fun adjustVolume(delta: Int) {
        val am = getAudioManager() ?: return
        val dir = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        updateVolumeUi()
    }

    fun toggleVolumeMute() {
        val am = getAudioManager() ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        } else {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
        }
        updateVolumeUi()
    }

    fun renderSelection() {
        fun highlight(view: View, isSelected: Boolean) {
            val bgRes = if (isSelected) R.drawable.bg_vod_button_selected else R.drawable.bg_vod_button
            view.setBackgroundResource(bgRes)
            if (isSelected) view.requestFocus()
        }

        highlight(binding.vodBtnBack, selectedTarget == VodControlTarget.BACK)
        highlight(binding.vodBtnAudioSub, selectedTarget == VodControlTarget.AUDIO_SUB)
        highlight(binding.vodBtnSpeed, selectedTarget == VodControlTarget.SPEED)
        highlight(binding.vodBtnFullscreen, selectedTarget == VodControlTarget.FULLSCREEN)

        if (selectedTarget == VodControlTarget.TIMELINE) {
            binding.vodTimelineRow.setBackgroundResource(R.drawable.bg_vod_timeline_selected)
            binding.vodTimelineRow.requestFocus()
        } else {
            binding.vodTimelineRow.background = null
        }

        highlight(binding.vodBtnPlayPause, selectedTarget == VodControlTarget.PLAY_PAUSE)
        highlight(binding.vodBtnRewind, selectedTarget == VodControlTarget.REWIND)
        highlight(binding.vodBtnForward, selectedTarget == VodControlTarget.FORWARD)
        highlight(binding.vodVolumeContainer, selectedTarget == VodControlTarget.VOLUME)
        if (hasEpisodes?.invoke() == true) {
            highlight(binding.vodBtnList, selectedTarget == VodControlTarget.LIST)
        }

        if (selectedTarget == VodControlTarget.RETRY) {
            binding.playbackErrorRetryBtn.requestFocus()
        }
    }

    fun activateSelectedTarget() {
        when (selectedTarget) {
            VodControlTarget.BACK -> onOpenVodLibrary()
            VodControlTarget.LIST -> {
                if (onOpenEpisodeList?.invoke() != true) {
                    onOpenVodLibrary()
                }
            }
            VodControlTarget.FULLSCREEN -> hideControls()
            VodControlTarget.TIMELINE,
            VodControlTarget.PLAY_PAUSE -> {
                if (isBufferingOrConnecting()) {
                    onStopPlayback?.invoke()
                } else {
                    iptvPlayback.togglePlayPause()
                }
            }
            VodControlTarget.REWIND -> iptvPlayback.seekBy(-10_000L)
            VodControlTarget.FORWARD -> iptvPlayback.seekBy(10_000L)
            VodControlTarget.VOLUME -> toggleVolumeMute()
            VodControlTarget.SPEED -> onShowSpeedChoices()
            VodControlTarget.AUDIO_SUB -> onShowAudioTracks()
            VodControlTarget.RETRY -> {
                onRetryPlayback?.invoke()
                    ?: binding.playbackErrorRetryBtn.performClick()
            }
        }
        updateControls()
    }

    fun handleDispatchKeyEvent(event: KeyEvent): Boolean {
        if (isEpisodePanelVisible()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        hideEpisodePanel()
                        return true
                    }
                }
            }
            return binding.vodEpisodesRecycler.dispatchKeyEvent(event)
        }

        val isVisible = isOverlayVisible()
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> return true
            }
            return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (!isVisible) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        selectedTarget = if (isErrorVisible()) VodControlTarget.RETRY else VodControlTarget.PLAY_PAUSE
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        onOpenVodLibrary()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        iptvPlayback.seekBy(-10_000L)
                        selectedTarget = VodControlTarget.TIMELINE
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        iptvPlayback.seekBy(10_000L)
                        selectedTarget = VodControlTarget.TIMELINE
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (isBufferingOrConnecting()) {
                            onStopPlayback?.invoke()
                        } else {
                            iptvPlayback.togglePlayPause()
                        }
                        selectedTarget = VodControlTarget.PLAY_PAUSE
                        showControls()
                        return true
                    }
                }
            } else {
                scheduleAutoHide()
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        hideControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        selectedTarget = when (selectedTarget) {
                            VodControlTarget.PLAY_PAUSE,
                            VodControlTarget.REWIND,
                            VodControlTarget.FORWARD,
                            VodControlTarget.VOLUME,
                            VodControlTarget.LIST -> VodControlTarget.TIMELINE
                            VodControlTarget.TIMELINE -> {
                                if (isErrorVisible()) VodControlTarget.RETRY else VodControlTarget.BACK
                            }
                            VodControlTarget.RETRY -> VodControlTarget.BACK
                            else -> selectedTarget
                        }
                        renderSelection()
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        selectedTarget = when (selectedTarget) {
                            VodControlTarget.BACK,
                            VodControlTarget.AUDIO_SUB,
                            VodControlTarget.SPEED,
                            VodControlTarget.FULLSCREEN -> {
                                if (isErrorVisible()) VodControlTarget.RETRY else VodControlTarget.TIMELINE
                            }
                            VodControlTarget.RETRY -> VodControlTarget.TIMELINE
                            VodControlTarget.TIMELINE -> VodControlTarget.PLAY_PAUSE
                            else -> selectedTarget
                        }
                        renderSelection()
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (selectedTarget) {
                            VodControlTarget.FULLSCREEN -> selectedTarget = VodControlTarget.SPEED
                            VodControlTarget.SPEED -> selectedTarget = VodControlTarget.AUDIO_SUB
                            VodControlTarget.AUDIO_SUB -> selectedTarget = VodControlTarget.BACK
                            VodControlTarget.TIMELINE -> {
                                iptvPlayback.seekBy(-10_000L)
                                updateControls()
                            }
                            VodControlTarget.LIST -> selectedTarget = VodControlTarget.VOLUME
                            VodControlTarget.VOLUME -> selectedTarget = VodControlTarget.FORWARD
                            VodControlTarget.FORWARD -> selectedTarget = VodControlTarget.REWIND
                            VodControlTarget.REWIND -> selectedTarget = VodControlTarget.PLAY_PAUSE
                            VodControlTarget.PLAY_PAUSE -> {
                                // Sol sınır
                            }
                            VodControlTarget.RETRY -> {
                                selectedTarget = VodControlTarget.BACK
                            }
                            else -> Unit
                        }
                        renderSelection()
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (selectedTarget) {
                            VodControlTarget.BACK -> selectedTarget = VodControlTarget.AUDIO_SUB
                            VodControlTarget.AUDIO_SUB -> selectedTarget = VodControlTarget.SPEED
                            VodControlTarget.SPEED -> selectedTarget = VodControlTarget.FULLSCREEN
                            VodControlTarget.TIMELINE -> {
                                iptvPlayback.seekBy(10_000L)
                                updateControls()
                            }
                            VodControlTarget.PLAY_PAUSE -> selectedTarget = VodControlTarget.REWIND
                            VodControlTarget.REWIND -> selectedTarget = VodControlTarget.FORWARD
                            VodControlTarget.FORWARD -> selectedTarget = VodControlTarget.VOLUME
                            VodControlTarget.VOLUME -> {
                                if (hasEpisodes?.invoke() == true) {
                                    selectedTarget = VodControlTarget.LIST
                                }
                            }
                            VodControlTarget.LIST -> {
                                // Sağ sınır
                            }
                            VodControlTarget.RETRY -> {
                                selectedTarget = if (hasEpisodes?.invoke() == true) VodControlTarget.LIST else VodControlTarget.VOLUME
                            }
                            else -> Unit
                        }
                        renderSelection()
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        adjustVolume(1)
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        adjustVolume(-1)
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        val wasBackOrList = selectedTarget == VodControlTarget.BACK || selectedTarget == VodControlTarget.LIST
                        activateSelectedTarget()
                        if (!wasBackOrList) {
                            showControls()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (isBufferingOrConnecting()) {
                            onStopPlayback?.invoke()
                        } else {
                            iptvPlayback.togglePlayPause()
                        }
                        updateControls()
                        showControls()
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                updateControls()
            }
        }
    }
}
