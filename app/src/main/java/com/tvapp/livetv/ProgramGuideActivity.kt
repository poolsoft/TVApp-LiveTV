package com.tvapp.livetv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.tvapp.livetv.ui.GuideScheduleAdapter
import com.tvapp.livetv.ui.ProgramGuideChannelAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgramGuideActivity : TvRemoteActivity() {
    private lateinit var binding: ActivityProgramGuideBinding
    private lateinit var channelRepository: ChannelRepository
    private lateinit var programRepository: ProgramRepository
    private lateinit var parentalControlStore: ParentalControlStore
    private lateinit var channelAdapter: ProgramGuideChannelAdapter
    private lateinit var scheduleAdapter: GuideScheduleAdapter
    private var channels: List<LiveChannel> = emptyList()
    private var focusedChannel: LiveChannel? = null
    private var programs: List<ProgramSummary> = emptyList()
    private val programSchedules = mutableMapOf<String, List<ProgramSummary>>()
    private var focusJob: Job? = null
    private var scheduleJob: Job? = null
    private var currentProgramsJob: Job? = null
    private var currentProgramsRequestId = 0L
    private var focusedChannelIndex = 0
    private var focusedProgramIndex = 0
    private lateinit var reminderStore: ProgramReminderStore
    private lateinit var reminderScheduler: ProgramReminderScheduler
    private var pendingReminder: Pair<LiveChannel, ProgramSummary>? = null
    private val programGuideHandler = Handler(Looper.getMainLooper())
    private var currentPrograms: Map<String, ProgramSummary> = emptyMap()
    private var epgSourceMode = EpgSourceMode.MERGED
    private val programGuideRefresh = object : Runnable {
        override fun run() {
            refreshCurrentPrograms()
            programGuideHandler.postDelayed(this, GUIDE_REFRESH_INTERVAL_MS)
        }
    }

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
        scheduleAdapter = GuideScheduleAdapter(
            ::onScheduleRowFocused,
            ::onScheduleProgramFocused,
            { channel, program ->
                focusedChannel = channel
                openProgram(program)
            },
            { channel, program ->
                focusedChannel = channel
                toggleReminder(program)
            },
        )
        binding.guideChannelList.layoutManager = LinearLayoutManager(this)
        binding.guideChannelList.adapter = channelAdapter
        binding.programList.layoutManager = LinearLayoutManager(this)
        binding.programList.adapter = scheduleAdapter
        applyPercentageGeometry()
        binding.guideDate.text = SimpleDateFormat(
            "EEEE, d MMMM",
            resources.configuration.locales[0],
        ).format(Date()).replaceFirstChar {
            it.uppercase(resources.configuration.locales[0])
        }
        updateEpgSourceLabel()
        updateTimelineRuler(timelineWindowStartMillis)
        loadChannels()
    }

    override fun onResume() {
        super.onResume()
        programGuideHandler.post(programGuideRefresh)
    }

    override fun onPause() {
        programGuideHandler.removeCallbacks(programGuideRefresh)
        super.onPause()
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
            currentPrograms = emptyMap()
            channelAdapter.submitList(loaded, currentPrograms)
            scheduleAdapter.submitChannels(
                loaded,
                timelineWindowStartMillis,
                timelineWindowStartMillis + TIMELINE_WINDOW_MS,
            )
            if (loaded.isEmpty()) {
                binding.emptyPrograms.visibility = View.VISIBLE
                binding.emptyPrograms.setText(R.string.no_channels_title)
                return@launch
            }
            val preferredKey = intent.getStringExtra(EXTRA_CURRENT_SOURCE_KEY)
            val initial = loaded.firstOrNull { it.sourceKey == preferredKey } ?: loaded.first()
            focusedChannelIndex = loaded.indexOf(initial).coerceAtLeast(0)
            refreshCurrentPrograms()
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
            refreshCurrentPrograms()
            loadPrograms(channel)
        }
    }

    private fun loadPrograms(
        channel: LiveChannel,
        focusTimeline: Boolean = false,
        preferredTimeMillis: Long? = null,
    ) {
        focusedChannel = channel
        binding.selectedChannelNumber.text = channel.displayNumber
        binding.selectedChannelName.text = channel.displayName
        scheduleJob?.cancel()
        scheduleJob = lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val requestedMode = epgSourceMode
            val center = channels.indexOfFirst { it.sourceKey == channel.sourceKey }.coerceAtLeast(0)
            val from = (center - GUIDE_CHANNEL_RADIUS).coerceAtLeast(0)
            val to = (center + GUIDE_CHANNEL_RADIUS + 1).coerceAtMost(channels.size)
            val visibleChannels = channels.subList(from, to)
            val loadedSchedules = withContext(Dispatchers.IO) {
                runCatching {
                    visibleChannels.associate { item ->
                        item.sourceKey to when (requestedMode) {
                            EpgSourceMode.TIF -> programRepository.tifProgramsForChannel(
                                item, now - PAST_WINDOW_MS, now + GUIDE_WINDOW_MS,
                            )
                            EpgSourceMode.XMLTV -> programRepository.xmlTvProgramsForChannel(
                                item, now - PAST_WINDOW_MS, now + GUIDE_WINDOW_MS,
                            )
                            EpgSourceMode.MERGED -> programRepository.programsForChannel(
                                item, now - PAST_WINDOW_MS, now + GUIDE_WINDOW_MS,
                            )
                        }
                    }
                }.getOrDefault(emptyMap())
            }
            if (focusedChannel?.sourceKey != channel.sourceKey || epgSourceMode != requestedMode) {
                return@launch
            }
            programSchedules.putAll(loadedSchedules)
            scheduleAdapter.updateSchedules(loadedSchedules)
            currentPrograms = currentPrograms.toMutableMap().apply {
                loadedSchedules.forEach { (sourceKey, schedule) ->
                    val current = schedule.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
                    if (current == null) remove(sourceKey) else put(sourceKey, current)
                }
            }
            channelAdapter.submitPrograms(currentPrograms)
            programs = loadedSchedules[channel.sourceKey].orEmpty()
            // Empty schedules are represented in their own channel row. A full-screen empty
            // state here would hide useful schedules belonging to the other visible channels.
            binding.emptyPrograms.visibility = View.GONE
            if (programs.isEmpty()) {
                binding.detailTitle.setText(R.string.no_epg_data)
                binding.detailTime.text = ""
                binding.detailDescription.text = ""
                binding.detailDescription.visibility = View.GONE
                binding.detailDescriptionScroll.visibility = View.GONE
                if (focusTimeline) scheduleAdapter.focusRow(binding.programList, center)
            } else {
                val targetTime = preferredTimeMillis ?: now
                val selectedProgram = programs.firstOrNull {
                    targetTime in it.startTimeMillis until it.endTimeMillis
                } ?: programs.minByOrNull { kotlin.math.abs(it.startTimeMillis - targetTime) }
                    ?: programs.first()
                focusedProgramIndex = programs.indexOf(selectedProgram).coerceAtLeast(0)
                showProgramDetail(selectedProgram)
                if (focusTimeline) {
                    scheduleAdapter.focusProgram(binding.programList, center, targetTime)
                } else {
                    binding.programList.scrollToPosition(center)
                }
            }
        }
    }

    private fun onScheduleRowFocused(row: Int, channel: LiveChannel) {
        focusedChannelIndex = row
        focusedChannel = channel
        focusedProgramIndex = 0
        programs = programSchedules[channel.sourceKey].orEmpty()
        channelAdapter.select(channel.sourceKey)
        binding.guideChannelList.scrollToPosition(row)
        binding.selectedChannelNumber.text = channel.displayNumber
        binding.selectedChannelName.text = channel.displayName
        if (!programSchedules.containsKey(channel.sourceKey)) loadPrograms(channel)
    }

    private fun onScheduleProgramFocused(
        row: Int,
        channel: LiveChannel,
        program: ProgramSummary,
    ) {
        focusedChannelIndex = row
        focusedChannel = channel
        programs = programSchedules[channel.sourceKey].orEmpty()
        focusedProgramIndex = programs.indexOf(program).coerceAtLeast(0)
        channelAdapter.select(channel.sourceKey)
        binding.guideChannelList.scrollToPosition(row)
        binding.selectedChannelNumber.text = channel.displayNumber
        binding.selectedChannelName.text = channel.displayName
        showProgramDetail(program)
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
        binding.detailDescriptionScroll.visibility = binding.detailDescription.visibility
        binding.detailDescriptionScroll.scrollTo(0, 0)
    }

    private fun updateTimelineRuler(startMillis: Long) {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        listOf(
            binding.timelineTime0,
            binding.timelineTime1,
            binding.timelineTime2,
            binding.timelineTime3,
        ).forEachIndexed { index, label ->
            label.text = format.format(Date(startMillis + index * TIMELINE_STEP_MS))
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

    private fun focusScheduleChannel(offset: Int) {
        if (channels.isEmpty()) return
        val target = (focusedChannelIndex + offset).mod(channels.size)
        val channel = channels[target]
        val preferredTime = programs.getOrNull(focusedProgramIndex)?.startTimeMillis
            ?: System.currentTimeMillis()
        focusedChannelIndex = target
        focusedChannel = channel
        channelAdapter.select(channel.sourceKey)
        binding.guideChannelList.scrollToPosition(target)
        binding.selectedChannelNumber.text = channel.displayNumber
        binding.selectedChannelName.text = channel.displayName

        val cachedPrograms = programSchedules[channel.sourceKey]
        if (cachedPrograms != null) {
            programs = cachedPrograms
            val selected = cachedPrograms.firstOrNull {
                preferredTime in it.startTimeMillis until it.endTimeMillis
            } ?: cachedPrograms.minByOrNull {
                kotlin.math.abs(it.startTimeMillis - preferredTime)
            }
            if (selected != null) {
                focusedProgramIndex = cachedPrograms.indexOf(selected).coerceAtLeast(0)
                showProgramDetail(selected)
                scheduleAdapter.focusProgram(binding.programList, target, preferredTime)
            } else {
                focusedProgramIndex = 0
                binding.detailTitle.setText(R.string.no_epg_data)
                binding.detailTime.text = ""
                binding.detailDescription.text = ""
                binding.detailDescription.visibility = View.GONE
                binding.detailDescriptionScroll.visibility = View.GONE
                scheduleAdapter.focusRow(binding.programList, target)
            }
            loadPrograms(channel, preferredTimeMillis = preferredTime)
        } else {
            loadPrograms(channel, focusTimeline = true, preferredTimeMillis = preferredTime)
        }
    }

    private fun moveScheduleProgram(direction: Int): Boolean {
        val target = focusedProgramIndex + direction
        if (target !in programs.indices) {
            if (direction < 0) focusChannel(focusedChannel?.sourceKey)
            return direction < 0
        }
        val program = programs[target]
        val midpoint = program.startTimeMillis + (program.endTimeMillis - program.startTimeMillis) / 2
        if (midpoint !in timelineWindowStartMillis until (timelineWindowStartMillis + TIMELINE_WINDOW_MS)) {
            timelineWindowStartMillis = alignHalfHour(program.startTimeMillis - TIMELINE_STEP_MS)
            updateTimelineRuler(timelineWindowStartMillis)
            scheduleAdapter.updateWindow(
                timelineWindowStartMillis,
                timelineWindowStartMillis + TIMELINE_WINDOW_MS,
            )
        }
        focusedProgramIndex = target
        scheduleAdapter.focusProgram(binding.programList, focusedChannelIndex, midpoint)
        return true
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
        scheduleAdapter.updateReminders(
            channels.associate { it.sourceKey to remindedStartsFor(it.sourceKey) },
        )
    }

    private fun remindedStartsFor(sourceKey: String): Set<Long> =
        reminderStore.reminders()
            .asSequence()
            .filter { it.sourceKey == sourceKey }
            .map { it.startTimeMillis }
            .toSet()

    private fun refreshCurrentPrograms() {
        if (channels.isEmpty()) return
        val from = (focusedChannelIndex - GUIDE_CHANNEL_RADIUS).coerceAtLeast(0)
        val to = (focusedChannelIndex + GUIDE_CHANNEL_RADIUS + 1).coerceAtMost(channels.size)
        val centerSourceKey = focusedChannel?.sourceKey
            ?: channels.getOrNull(focusedChannelIndex)?.sourceKey
            ?: return
        val window = channels.subList(from, to).sortedByDescending {
            it.sourceKey == centerSourceKey
        }
        val requestedMode = epgSourceMode
        val requestId = ++currentProgramsRequestId
        currentProgramsJob?.cancel()
        currentProgramsJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val now = System.currentTimeMillis()
                    when (requestedMode) {
                        EpgSourceMode.MERGED ->
                            programRepository.currentProgramsForListWindow(window, now)
                        else -> window.mapNotNull { channel ->
                            val items = when (requestedMode) {
                                EpgSourceMode.TIF -> programRepository.tifProgramsForChannel(
                                    channel, now - PAST_WINDOW_MS, now + GUIDE_WINDOW_MS,
                                )
                                EpgSourceMode.XMLTV -> programRepository.xmlTvProgramsForChannel(
                                    channel, now - PAST_WINDOW_MS, now + GUIDE_WINDOW_MS,
                                )
                                EpgSourceMode.MERGED -> emptyList()
                            }
                            items.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
                                ?.let { channel.sourceKey to it }
                        }.toMap()
                    }
                }
            }
            if (
                requestId != currentProgramsRequestId ||
                epgSourceMode != requestedMode ||
                (focusedChannel?.sourceKey
                    ?: channels.getOrNull(focusedChannelIndex)?.sourceKey) != centerSourceKey
            ) return@launch
            val fresh = result.getOrNull() ?: return@launch
            val now = System.currentTimeMillis()
            val windowKeys = window.mapTo(mutableSetOf(), LiveChannel::sourceKey)
            currentPrograms = currentPrograms.filter { (key, program) ->
                key !in windowKeys || key in fresh ||
                    now in program.startTimeMillis until program.endTimeMillis
            } + fresh
            channelAdapter.submitPrograms(currentPrograms)
        }
    }

    private fun selectEpgSource(mode: EpgSourceMode) {
        if (epgSourceMode == mode) return
        epgSourceMode = mode
        currentPrograms = emptyMap()
        programSchedules.clear()
        channelAdapter.submitPrograms(emptyMap())
        scheduleAdapter.submitChannels(
            channels,
            timelineWindowStartMillis,
            timelineWindowStartMillis + TIMELINE_WINDOW_MS,
        )
        updateEpgSourceLabel()
        focusedChannel?.let(::loadPrograms)
        refreshCurrentPrograms()
    }

    private fun updateEpgSourceLabel() {
        binding.guideSource.setText(
            when (epgSourceMode) {
                EpgSourceMode.TIF -> R.string.epg_source_tif
                EpgSourceMode.XMLTV -> R.string.epg_source_xmltv
                EpgSourceMode.MERGED -> R.string.epg_source_merged
            },
        )
    }

    override fun dispatchKeyEvent(rawEvent: KeyEvent): Boolean {
        val event = rawEvent.asTvRemoteEvent()
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> when {
                    binding.programList.hasFocus() -> true.also { focusScheduleChannel(-1) }
                    binding.guideChannelList.hasFocus() -> moveRecyclerFocus(binding.guideChannelList, -1)
                    else -> false
                }.also { handled -> if (handled) return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> when {
                    binding.programList.hasFocus() -> true.also { focusScheduleChannel(1) }
                    binding.guideChannelList.hasFocus() -> moveRecyclerFocus(binding.guideChannelList, 1)
                    else -> false
                }.also { handled -> if (handled) return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                    binding.guideChannelList.hasFocus() -> {
                        if (programs.isEmpty()) {
                            scheduleAdapter.focusRow(binding.programList, focusedChannelIndex)
                        } else {
                            scheduleAdapter.focusProgram(
                                binding.programList,
                                focusedChannelIndex,
                                programs.getOrNull(focusedProgramIndex)?.startTimeMillis
                                    ?: System.currentTimeMillis(),
                            )
                        }
                        true
                    }
                    binding.programList.hasFocus() -> moveScheduleProgram(1)
                    else -> false
                }.also { handled -> if (handled) return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (binding.programList.hasFocus()) {
                    moveScheduleProgram(-1)
                    return true
                } else if (binding.detailDescriptionScroll.hasFocus()) {
                    scheduleAdapter.focusProgram(
                        binding.programList,
                        focusedChannelIndex,
                        programs.getOrNull(focusedProgramIndex)?.startTimeMillis
                            ?: System.currentTimeMillis(),
                    )
                    return true
                }
                KeyEvent.KEYCODE_INFO -> {
                    if (binding.detailDescriptionScroll.hasFocus()) {
                        scheduleAdapter.focusProgram(
                            binding.programList,
                            focusedChannelIndex,
                            programs.getOrNull(focusedProgramIndex)?.startTimeMillis
                                ?: System.currentTimeMillis(),
                        )
                    } else if (binding.detailDescriptionScroll.visibility == View.VISIBLE) {
                        binding.detailDescriptionScroll.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_GUIDE -> {
                    finish()
                    return true
                }
                KeyEvent.KEYCODE_PROG_RED -> {
                    selectEpgSource(EpgSourceMode.TIF)
                    return true
                }
                KeyEvent.KEYCODE_PROG_GREEN -> {
                    selectEpgSource(EpgSourceMode.XMLTV)
                    return true
                }
                KeyEvent.KEYCODE_PROG_BLUE -> {
                    selectEpgSource(EpgSourceMode.MERGED)
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
        scheduleJob?.cancel()
        currentProgramsJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CURRENT_SOURCE_KEY = "current-source-key"
        const val EXTRA_SELECTED_SOURCE_KEY = "selected-source-key"
        private const val HEADER_HEIGHT_FRACTION = 0.11f
        private const val DETAIL_HEIGHT_FRACTION = 0.31f
        private const val OVERLAY_WIDTH_FRACTION = 0.82f
        private const val OUTER_HORIZONTAL_PADDING_FRACTION = 0.025f
        private const val COLUMN_PADDING_FRACTION = 0.008f
        private const val CHANNEL_FOCUS_DELAY_MS = 250L
        private const val FOCUS_RETRY_DELAY_MS = 60L
        private const val GUIDE_REFRESH_INTERVAL_MS = 15_000L
        private const val PAST_WINDOW_MS = 2 * 60 * 60 * 1_000L
        private const val GUIDE_WINDOW_MS = 24 * 60 * 60 * 1_000L
        private const val GUIDE_CHANNEL_RADIUS = 6
        private const val TIMELINE_STEP_MS = 60 * 60 * 1_000L
        private const val TIMELINE_WINDOW_MS = 3 * TIMELINE_STEP_MS
    }

    private var timelineWindowStartMillis = alignHalfHour(System.currentTimeMillis() - 30 * 60_000L)

    private fun alignHalfHour(value: Long): Long {
        val step = 30 * 60_000L
        return value - value.mod(step)
    }

    private enum class EpgSourceMode { TIF, XMLTV, MERGED }
}
