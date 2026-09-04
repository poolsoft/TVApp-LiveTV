package com.tvapp.livetv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.ProgramRepository
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ActivityProgramGuideBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.reminder.ProgramReminder
import com.tvapp.livetv.reminder.ProgramReminderScheduler
import com.tvapp.livetv.reminder.ProgramReminderStore
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
    private var focusedChannelIndex = 0
    private var focusedProgramIndex = 0
    private lateinit var reminderStore: ProgramReminderStore
    private lateinit var reminderScheduler: ProgramReminderScheduler
    private var pendingReminder: Pair<LiveChannel, ProgramSummary>? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingReminder
        pendingReminder = null
        when {
            granted && pending != null -> scheduleReminder(pending.first, pending.second)
            !granted -> Toast.makeText(
                this,
                R.string.reminder_needs_notification_permission,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgramGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        channelRepository = ChannelRepository(this)
        programRepository = ProgramRepository(this)
        parentalControlStore = ParentalControlStore(this)
        reminderStore = ProgramReminderStore(this)
        reminderScheduler = ProgramReminderScheduler(this)
        channelAdapter = ProgramGuideChannelAdapter(
            ::scheduleChannelPrograms,
            ::openChannel,
        ) { channel -> parentalControlStore.isLocked(channel.sourceKey) }
        programAdapter = GuideProgramAdapter(
            ::showProgramDetail,
            ::openProgram,
            ::toggleReminder,
        )
        binding.guideChannelList.layoutManager = LinearLayoutManager(this)
        binding.guideChannelList.adapter = channelAdapter
        binding.programList.layoutManager = LinearLayoutManager(this)
        binding.programList.adapter = programAdapter
        applyPercentageGeometry()
        binding.guideDate.text = SimpleDateFormat(
            "EEEE, d MMMM",
            resources.configuration.locales[0],
        ).format(Date()).replaceFirstChar {
            it.uppercase(resources.configuration.locales[0])
        }
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
                    programRepository.currentProgramsForChannels(loaded)
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
            focusedChannelIndex = loaded.indexOf(initial).coerceAtLeast(0)
            channelAdapter.select(initial.sourceKey)
            loadPrograms(initial)
            focusChannel(initial.sourceKey)
        }
    }

    private fun scheduleChannelPrograms(channel: LiveChannel) {
        focusedChannel = channel
        focusedChannelIndex = channels.indexOfFirst { it.sourceKey == channel.sourceKey }
            .coerceAtLeast(0)
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
            programAdapter.submitList(loaded, remindedStartsFor(channel.sourceKey))
            binding.emptyPrograms.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
            if (loaded.isEmpty()) {
                binding.detailTitle.setText(R.string.no_epg_data)
                binding.detailTime.text = ""
                binding.detailDescription.text = ""
                binding.detailDescription.visibility = View.GONE
            } else {
                val current = loaded.firstOrNull {
                    now in it.startTimeMillis until it.endTimeMillis
                } ?: loaded.first()
                focusedProgramIndex = loaded.indexOf(current).coerceAtLeast(0)
                showProgramDetail(current)
            }
        }
    }

    private fun showProgramDetail(program: ProgramSummary) {
        focusedProgramIndex = programs.indexOf(program).takeIf { it >= 0 }
            ?: focusedProgramIndex
        binding.detailTitle.text = program.title.ifBlank { getString(R.string.untitled_program) }
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startStr = format.format(Date(program.startTimeMillis))
        val endStr = format.format(Date(program.endTimeMillis))
        val durationMin = ((program.endTimeMillis - program.startTimeMillis) / 60_000L).coerceAtLeast(1L)
        val now = System.currentTimeMillis()
        val timeStr = if (now in program.startTimeMillis until program.endTimeMillis) {
            val remainMin = ((program.endTimeMillis - now) / 60_000L).coerceAtLeast(1L)
            getString(
                R.string.program_time_duration_remaining,
                startStr,
                endStr,
                durationMin,
                remainMin,
            )
        } else {
            getString(R.string.program_time_duration, startStr, endStr, durationMin)
        }
        binding.detailTime.text = timeStr
        binding.detailDescription.text = program.description
        binding.detailDescription.visibility = if (program.description.isBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }
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
        val current = if (recyclerView === binding.guideChannelList) {
            focusedChannelIndex
        } else {
            focusedProgramIndex
        }
        val target = when {
            offset < 0 && current == 0 -> count - 1
            offset > 0 && current == count - 1 -> 0
            else -> (current + offset).coerceIn(0, count - 1)
        }
        if (recyclerView === binding.guideChannelList) {
            focusedChannelIndex = target
        } else {
            focusedProgramIndex = target
        }
        focusRecyclerPosition(recyclerView, target)
        return true
    }

    private fun focusRecyclerPosition(recyclerView: RecyclerView, position: Int) {
        val count = recyclerView.adapter?.itemCount ?: 0
        if (position !in 0 until count) return
        recyclerView.scrollToPosition(position)
        recyclerView.post {
            val item = recyclerView.findViewHolderForAdapterPosition(position)?.itemView
            if (item?.requestFocus() != true) {
                recyclerView.postDelayed({
                    recyclerView.findViewHolderForAdapterPosition(position)
                        ?.itemView
                        ?.requestFocus()
                }, FOCUS_RETRY_DELAY_MS)
            }
        }
    }

    private fun toggleReminder(program: ProgramSummary) {
        val channel = focusedChannel ?: return
        val now = System.currentTimeMillis()
        if (program.startTimeMillis <= now) {
            Toast.makeText(this, R.string.reminder_past_program, Toast.LENGTH_LONG).show()
            return
        }
        val existing = reminderStore.reminderFor(channel.sourceKey, program.startTimeMillis)
        if (existing != null) {
            reminderStore.remove(existing.id)
            reminderScheduler.cancel(existing.id)
            refreshReminderBadges()
            Toast.makeText(this, R.string.reminder_removed, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingReminder = channel to program
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        scheduleReminder(channel, program)
    }

    private fun scheduleReminder(channel: LiveChannel, program: ProgramSummary) {
        val reminder = ProgramReminder.of(
            sourceKey = channel.sourceKey,
            channelName = channel.displayName,
            programTitle = program.title,
            startTimeMillis = program.startTimeMillis,
        )
        reminderStore.put(reminder)
        reminderScheduler.schedule(reminder)
        refreshReminderBadges()
        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(program.startTimeMillis))
        Toast.makeText(
            this,
            getString(R.string.reminder_set_for_program, timeText),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun refreshReminderBadges() {
        val channel = focusedChannel ?: return
        programAdapter.submitList(programs, remindedStartsFor(channel.sourceKey))
    }

    private fun remindedStartsFor(sourceKey: String): Set<Long> =
        reminderStore.reminders()
            .asSequence()
            .filter { it.sourceKey == sourceKey }
            .map { it.startTimeMillis }
            .toSet()

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
                    focusRecyclerPosition(binding.programList, focusedProgramIndex)
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
                KeyEvent.KEYCODE_TV_CONTENTS_MENU,
                312 -> {
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
        private const val HEADER_HEIGHT_FRACTION = 0.11f
        private const val DETAIL_HEIGHT_FRACTION = 0.22f
        private const val OVERLAY_WIDTH_FRACTION = 0.68f
        private const val OUTER_HORIZONTAL_PADDING_FRACTION = 0.025f
        private const val COLUMN_PADDING_FRACTION = 0.008f
        private const val CHANNEL_FOCUS_DELAY_MS = 250L
        private const val FOCUS_RETRY_DELAY_MS = 60L
        private const val PAST_WINDOW_MS = 2 * 60 * 60 * 1_000L
        private const val GUIDE_WINDOW_MS = 24 * 60 * 60 * 1_000L
    }
}
