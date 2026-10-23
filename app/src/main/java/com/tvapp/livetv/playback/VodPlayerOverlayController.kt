package com.tvapp.livetv.playback

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
    TIMELINE,
    REWIND,
    PLAY_PAUSE,
    FORWARD,
    SPEED,
    AUDIO_SUB,
    RESTART,
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
) {
    var selectedTarget = VodControlTarget.PLAY_PAUSE
        private set

    private var controlsJob: Job? = null
    private var tickerJob: Job? = null
    var isShowingControls = false
        private set

    fun setup() {
        binding.vodBtnBack.setOnClickListener {
            onOpenVodLibrary()
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
        binding.vodBtnPlayPause.setOnClickListener {
            selectedTarget = VodControlTarget.PLAY_PAUSE
            activateSelectedTarget()
            showControls()
        }
        binding.vodBtnForward.setOnClickListener {
            selectedTarget = VodControlTarget.FORWARD
            activateSelectedTarget()
            showControls()
        }
        binding.vodBtnSpeed.setOnClickListener {
            selectedTarget = VodControlTarget.SPEED
            activateSelectedTarget()
            showControls()
        }
        binding.vodBtnAudioSub.setOnClickListener {
            selectedTarget = VodControlTarget.AUDIO_SUB
            activateSelectedTarget()
            showControls()
        }
        binding.vodBtnRestart.setOnClickListener {
            selectedTarget = VodControlTarget.RESTART
            activateSelectedTarget()
            showControls()
        }
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
        controlsJob = scope.launch {
            delay(8_000L)
            hideControls()
        }
    }

    fun updateControls() {
        val state = iptvPlayback.playbackSnapshot()
        val channel = getCurrentChannel()
        binding.vodPlayerTitle.text = channel?.displayName.orEmpty()
        binding.vodPlayerSubtitle.text = channel?.let { getSourceName(it) } ?: getString(R.string.iptv_library_vod)
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

        val remaining = (state.durationMillis - state.positionMillis).coerceAtLeast(0L)
        binding.vodTimeDuration.text = if (state.durationMillis > 0) {
            "-${formatPlaybackTime(remaining)} / ${formatPlaybackTime(state.durationMillis)}"
        } else {
            getString(R.string.unknown_value)
        }

        val isPlaying = state.isPlaying
        binding.vodBtnPlayPause.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            0,
            0,
            0,
        )
        binding.vodBtnPlayPause.setText(if (isPlaying) R.string.iptv_pause_label else R.string.iptv_play_label)
        binding.vodBtnSpeed.text = state.playbackSpeed?.let {
            String.format(Locale.getDefault(), "%.2gx", it)
        } ?: "1.0x"
    }

    fun renderSelection() {
        fun highlight(view: View, isSelected: Boolean) {
            val bgRes = if (isSelected) R.drawable.bg_vod_button_selected else R.drawable.bg_vod_button
            view.setBackgroundResource(bgRes)
            if (isSelected) view.requestFocus()
        }

        highlight(binding.vodBtnBack, selectedTarget == VodControlTarget.BACK)
        if (selectedTarget == VodControlTarget.TIMELINE) {
            binding.vodTimelineRow.setBackgroundResource(R.drawable.bg_vod_timeline_selected)
            binding.vodTimelineRow.requestFocus()
        } else {
            binding.vodTimelineRow.background = null
        }
        highlight(binding.vodBtnRewind, selectedTarget == VodControlTarget.REWIND)
        highlight(binding.vodBtnPlayPause, selectedTarget == VodControlTarget.PLAY_PAUSE)
        highlight(binding.vodBtnForward, selectedTarget == VodControlTarget.FORWARD)
        highlight(binding.vodBtnSpeed, selectedTarget == VodControlTarget.SPEED)
        highlight(binding.vodBtnAudioSub, selectedTarget == VodControlTarget.AUDIO_SUB)
        highlight(binding.vodBtnRestart, selectedTarget == VodControlTarget.RESTART)
    }

    fun cycleButton(direction: Int) {
        val buttonRow = listOf(
            VodControlTarget.REWIND,
            VodControlTarget.PLAY_PAUSE,
            VodControlTarget.FORWARD,
            VodControlTarget.SPEED,
            VodControlTarget.AUDIO_SUB,
            VodControlTarget.RESTART,
        )
        val currentIndex = buttonRow.indexOf(selectedTarget)
        val nextIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else buttonRow.lastIndex
        } else {
            (currentIndex + direction + buttonRow.size) % buttonRow.size
        }
        selectedTarget = buttonRow[nextIndex]
        renderSelection()
        scheduleAutoHide()
    }

    fun activateSelectedTarget() {
        when (selectedTarget) {
            VodControlTarget.BACK -> onOpenVodLibrary()
            VodControlTarget.TIMELINE,
            VodControlTarget.PLAY_PAUSE -> iptvPlayback.togglePlayPause()
            VodControlTarget.REWIND -> iptvPlayback.seekBy(-10_000L)
            VodControlTarget.FORWARD -> iptvPlayback.seekBy(10_000L)
            VodControlTarget.SPEED -> onShowSpeedChoices()
            VodControlTarget.AUDIO_SUB -> onShowAudioTracks()
            VodControlTarget.RESTART -> iptvPlayback.restartVod()
        }
        updateControls()
    }

    fun handleDispatchKeyEvent(event: KeyEvent): Boolean {
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
                        selectedTarget = VodControlTarget.PLAY_PAUSE
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
                        iptvPlayback.togglePlayPause()
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
                            VodControlTarget.BACK -> VodControlTarget.BACK
                            VodControlTarget.TIMELINE -> VodControlTarget.BACK
                            else -> VodControlTarget.TIMELINE
                        }
                        renderSelection()
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        selectedTarget = when (selectedTarget) {
                            VodControlTarget.BACK -> VodControlTarget.TIMELINE
                            VodControlTarget.TIMELINE -> VodControlTarget.PLAY_PAUSE
                            else -> selectedTarget
                        }
                        renderSelection()
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (selectedTarget == VodControlTarget.TIMELINE) {
                            iptvPlayback.seekBy(-10_000L)
                            updateControls()
                        } else if (selectedTarget != VodControlTarget.BACK) {
                            cycleButton(-1)
                        }
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (selectedTarget == VodControlTarget.TIMELINE) {
                            iptvPlayback.seekBy(10_000L)
                            updateControls()
                        } else if (selectedTarget != VodControlTarget.BACK) {
                            cycleButton(1)
                        }
                        showControls()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        val wasBack = selectedTarget == VodControlTarget.BACK
                        activateSelectedTarget()
                        if (!wasBack) {
                            showControls()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        iptvPlayback.togglePlayPause()
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
