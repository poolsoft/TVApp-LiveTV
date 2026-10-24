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
    AUDIO_SUB,
    SPEED,
    FULLSCREEN,
    PLAY_PAUSE,
    TIMELINE,
    REWIND,
    FORWARD,
    LIST,
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
        binding.vodBtnList.setOnClickListener {
            selectedTarget = VodControlTarget.LIST
            activateSelectedTarget()
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

        binding.vodTimeDuration.text = if (state.durationMillis > 0) {
            formatPlaybackTime(state.durationMillis)
        } else {
            getString(R.string.unknown_value)
        }

        val isPlaying = state.isPlaying
        binding.vodBtnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
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

        highlight(binding.vodBtnPlayPause, selectedTarget == VodControlTarget.PLAY_PAUSE)
        if (selectedTarget == VodControlTarget.TIMELINE) {
            binding.vodTimelineRow.setBackgroundResource(R.drawable.bg_vod_timeline_selected)
            binding.vodTimelineRow.requestFocus()
        } else {
            binding.vodTimelineRow.background = null
        }

        highlight(binding.vodBtnRewind, selectedTarget == VodControlTarget.REWIND)
        highlight(binding.vodBtnForward, selectedTarget == VodControlTarget.FORWARD)
        highlight(binding.vodBtnList, selectedTarget == VodControlTarget.LIST)
    }

    fun activateSelectedTarget() {
        when (selectedTarget) {
            VodControlTarget.BACK,
            VodControlTarget.LIST -> onOpenVodLibrary()
            VodControlTarget.FULLSCREEN -> hideControls()
            VodControlTarget.TIMELINE,
            VodControlTarget.PLAY_PAUSE -> iptvPlayback.togglePlayPause()
            VodControlTarget.REWIND -> iptvPlayback.seekBy(-10_000L)
            VodControlTarget.FORWARD -> iptvPlayback.seekBy(10_000L)
            VodControlTarget.SPEED -> onShowSpeedChoices()
            VodControlTarget.AUDIO_SUB -> onShowAudioTracks()
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
                            VodControlTarget.REWIND,
                            VodControlTarget.FORWARD,
                            VodControlTarget.LIST -> VodControlTarget.PLAY_PAUSE
                            VodControlTarget.PLAY_PAUSE,
                            VodControlTarget.TIMELINE -> VodControlTarget.BACK
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
                            VodControlTarget.FULLSCREEN -> VodControlTarget.PLAY_PAUSE
                            VodControlTarget.PLAY_PAUSE,
                            VodControlTarget.TIMELINE -> VodControlTarget.REWIND
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
                            VodControlTarget.PLAY_PAUSE -> {
                                // Solunda eleman yok
                            }
                            VodControlTarget.LIST -> selectedTarget = VodControlTarget.FORWARD
                            VodControlTarget.FORWARD -> selectedTarget = VodControlTarget.REWIND
                            VodControlTarget.REWIND -> {
                                // Solunda eleman yok
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
                            VodControlTarget.PLAY_PAUSE -> selectedTarget = VodControlTarget.TIMELINE
                            VodControlTarget.TIMELINE -> {
                                iptvPlayback.seekBy(10_000L)
                                updateControls()
                            }
                            VodControlTarget.REWIND -> selectedTarget = VodControlTarget.FORWARD
                            VodControlTarget.FORWARD -> selectedTarget = VodControlTarget.LIST
                            VodControlTarget.LIST -> {
                                // Sağında eleman yok
                            }
                            else -> Unit
                        }
                        renderSelection()
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
