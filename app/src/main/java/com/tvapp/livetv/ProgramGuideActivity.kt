package com.tvapp.livetv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.ProgramRepository
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ActivityProgramGuideBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.settings.ParentalControlStore
import com.tvapp.livetv.ui.GuideProgramAdapter
import com.tvapp.livetv.ui.ProgramGuideChannelAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgramGuideActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProgramGuideBinding
    private lateinit var channelRepository: ChannelRepository
    private lateinit var programRepository: ProgramRepository
    private lateinit var parentalControlStore: ParentalControlStore
    private lateinit var channelAdapter: ProgramGuideChannelAdapter
    private lateinit var programAdapter: GuideProgramAdapter
    private var channels: List<LiveChannel> = emptyList()
    private var focusedChannel: LiveChannel? = null
    private var programs: List<ProgramSummary> = emptyList()
    private var focusJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgramGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        channelRepository = ChannelRepository(this)
        programRepository = ProgramRepository(this)
        parentalControlStore = ParentalControlStore(this)
        channelAdapter = ProgramGuideChannelAdapter(
            ::scheduleChannelPrograms,
            ::openChannel,
        ) { channel -> parentalControlStore.isLocked(channel.sourceKey) }
        programAdapter = GuideProgramAdapter(::showProgramDetail, ::openProgram)
        binding.guideChannelList.layoutManager = LinearLayoutManager(this)
        binding.guideChannelList.adapter = channelAdapter
        binding.programList.layoutManager = LinearLayoutManager(this)
        binding.programList.adapter = programAdapter
        applyPercentageGeometry()
        binding.guideDate.text = SimpleDateFormat(
            "EEEE, d MMMM",
            Locale("tr"),
        ).format(Date()).replaceFirstChar { it.uppercase(Locale("tr")) }
        loadChannels()
    }

    private fun applyPercentageGeometry() {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        binding.guideOverlay.layoutParams = binding.guideOverlay.layoutParams.apply {
            this.width = (width * OVERLAY_WIDTH_FRACTION).toInt()
        }
        binding.guideHeader.layoutParams = binding.guideHeader.layoutParams.apply {
            this.height = (height * HEADER_HEIGHT_FRACTION).toInt()
        }
        binding.guideHeader.setPadding(
            (width * OUTER_HORIZONTAL_PADDING_FRACTION).toInt(),
            0,
            (width * OUTER_HORIZONTAL_PADDING_FRACTION).toInt(),
            0,
        )
        binding.guideChannelColumn.setPadding(
            (width * COLUMN_PADDING_FRACTION).toInt(),
            0,
            (width * COLUMN_PADDING_FRACTION).toInt(),
            0,
        )
        binding.programDetail.layoutParams = binding.programDetail.layoutParams.apply {
            this.height = (height * DETAIL_HEIGHT_FRACTION).toInt()
        }
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                channelRepository.channels().getOrDefault(emptyList())
            }
            channels = loaded
            val currentPrograms = withContext(Dispatchers.IO) {
                runCatching {
                    programRepository.currentPrograms(
                        loaded.asSequence()
                            .filter { it.source == LiveChannel.Source.TIF }
                            .map { it.id }
                            .toSet(),
                    )
                }.getOrDefault(emptyMap())
            }
            channelAdapter.submitList(loaded, currentPrograms)
            if (loaded.isEmpty()) {
                binding.emptyPrograms.visibility = View.VISIBLE
                binding.emptyPrograms.setText(R.string.no_channels_title)
                return@launch
            }
            val preferredKey = intent.getStringExtra(EXTRA_CURRENT_SOURCE_KEY)
            val initial = loaded.firstOrNull { it.sourceKey == preferredKey } ?: loaded.first()
            channelAdapter.select(initial.sourceKey)
            loadPrograms(initial)
            focusChannel(initial.sourceKey)
        }
    }

    private fun scheduleChannelPrograms(channel: LiveChannel) {
        focusedChannel = channel
        channelAdapter.select(channel.sourceKey)
        focusJob?.cancel()
        focusJob = lifecycleScope.launch {
            delay(CHANNEL_FOCUS_DELAY_MS)
            loadPrograms(channel)
        }
    }

    private fun loadPrograms(channel: LiveChannel) {
        focusedChannel = channel
        binding.selectedChannelNumber.text = channel.displayNumber
        binding.selectedChannelName.text = channel.displayName
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    programRepository.programsForChannel(
                        channel = channel,
                        startTimeMillis = now - PAST_WINDOW_MS,
                        endTimeMillis = now + GUIDE_WINDOW_MS,
                    )
                }.getOrDefault(emptyList())
            }
            if (focusedChannel?.sourceKey != channel.sourceKey) return@launch
            programs = loaded
            programAdapter.submitList(loaded)
            binding.emptyPrograms.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
            if (loaded.isEmpty()) {
                binding.detailTitle.setText(R.string.no_epg_data)
                binding.detailTime.text = ""
            } else {
                val current = loaded.firstOrNull {
                    now in it.startTimeMillis until it.endTimeMillis
                } ?: loaded.first()
                showProgramDetail(current)
            }
        }
    }

    private fun showProgramDetail(program: ProgramSummary) {
        binding.detailTitle.text = program.title.ifBlank { getString(R.string.untitled_program) }
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.detailTime.text = getString(
            R.string.program_time_format,
            format.format(Date(program.startTimeMillis)),
            format.format(Date(program.endTimeMillis)),
        )
    }

    private fun openChannel(channel: LiveChannel) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_SELECTED_SOURCE_KEY, channel.sourceKey),
        )
        finish()
    }

    private fun openProgram(program: ProgramSummary) {
        val now = System.currentTimeMillis()
        if (now in program.startTimeMillis until program.endTimeMillis) {
            focusedChannel?.let(::openChannel)
        } else {
            showProgramDetail(program)
        }
    }

    private fun focusChannel(sourceKey: String?) {
        val position = channelAdapter.positionOf(sourceKey).takeIf { it >= 0 } ?: 0
        focusRecyclerPosition(binding.guideChannelList, position)
    }

    private fun moveRecyclerFocus(recyclerView: RecyclerView, offset: Int): Boolean {
        val count = recyclerView.adapter?.itemCount ?: 0
        if (count == 0) return false
        val focusedView = recyclerView.findFocus()
        val current = focusedView?.let { recyclerView.findContainingViewHolder(it) }
            ?.bindingAdapterPosition
            ?.takeIf { it != RecyclerView.NO_POSITION }
            ?: 0
        val target = (current + offset).coerceIn(0, count - 1)
        focusRecyclerPosition(recyclerView, target)
        return true
    }

    private fun focusRecyclerPosition(recyclerView: RecyclerView, position: Int) {
        recyclerView.scrollToPosition(position)
        recyclerView.postDelayed({
            recyclerView.findViewHolderForAdapterPosition(position)
                ?.itemView
                ?.requestFocus()
        }, FOCUS_AFTER_LAYOUT_DELAY_MS)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> when {
                    binding.programList.hasFocus() -> moveRecyclerFocus(binding.programList, -1)
                    binding.guideChannelList.hasFocus() -> moveRecyclerFocus(binding.guideChannelList, -1)
                    else -> false
                }.also { handled -> if (handled) return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> when {
                    binding.programList.hasFocus() -> moveRecyclerFocus(binding.programList, 1)
                    binding.guideChannelList.hasFocus() -> moveRecyclerFocus(binding.guideChannelList, 1)
                    else -> false
                }.also { handled -> if (handled) return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (binding.guideChannelList.hasFocus() &&
                    programs.isNotEmpty()
                ) {
                    focusRecyclerPosition(binding.programList, 0)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (binding.programList.hasFocus()) {
                    focusChannel(focusedChannel?.sourceKey)
                    return true
                }
                KeyEvent.KEYCODE_INFO,
                KeyEvent.KEYCODE_GUIDE -> {
                    finish()
                    return true
                }
                KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_TV_CONTENTS_MENU -> {
                    startActivity(Intent(this, DisplaySettingsActivity::class.java))
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        focusJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CURRENT_SOURCE_KEY = "current-source-key"
        const val EXTRA_SELECTED_SOURCE_KEY = "selected-source-key"
        private const val HEADER_HEIGHT_FRACTION = 0.12f
        private const val DETAIL_HEIGHT_FRACTION = 0.18f
        private const val OVERLAY_WIDTH_FRACTION = 0.66f
        private const val OUTER_HORIZONTAL_PADDING_FRACTION = 0.03f
        private const val COLUMN_PADDING_FRACTION = 0.008f
        private const val CHANNEL_FOCUS_DELAY_MS = 250L
        private const val FOCUS_AFTER_LAYOUT_DELAY_MS = 100L
        private const val PAST_WINDOW_MS = 2 * 60 * 60 * 1_000L
        private const val GUIDE_WINDOW_MS = 24 * 60 * 60 * 1_000L
    }
}
