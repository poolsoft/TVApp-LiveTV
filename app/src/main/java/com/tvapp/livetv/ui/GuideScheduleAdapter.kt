package com.tvapp.livetv.ui

import android.graphics.Typeface
import android.media.tv.TvContract
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import com.tvapp.livetv.R
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ItemGuideScheduleRowBinding
import com.tvapp.livetv.image.ChannelLogoLoader
import com.tvapp.livetv.model.LiveChannel

class GuideScheduleAdapter(
    private val onChannelFocused: (Int, LiveChannel) -> Unit,
    private val onChannelSelected: (LiveChannel) -> Unit,
    private val isParentalLocked: (LiveChannel) -> Boolean,
    private val onProgramFocused: (Int, LiveChannel, ProgramSummary) -> Unit,
    private val onProgramSelected: (LiveChannel, ProgramSummary) -> Unit,
    private val onReminderToggle: (LiveChannel, ProgramSummary) -> Unit,
) : RecyclerView.Adapter<GuideScheduleAdapter.ViewHolder>() {

    private val channels = mutableListOf<LiveChannel>()
    private val schedules = mutableMapOf<String, List<ProgramSummary>>()
    private var currentPrograms: Map<String, ProgramSummary> = emptyMap()
    private var reminders: Map<String, Set<Long>> = emptyMap()
    private var windowStartMillis = 0L
    private var windowEndMillis = 0L
    private var selectedChannelKey: String? = null
    private var selectedProgramStart: Long? = null
    private var lastFocusedPosition: Int = RecyclerView.NO_POSITION

    init {
        setHasStableIds(true)
    }

    fun submitChannels(
        items: List<LiveChannel>,
        programs: Map<String, ProgramSummary>,
        startMillis: Long,
        endMillis: Long,
    ) {
        channels.clear()
        channels.addAll(items)
        currentPrograms = programs
        schedules.clear()
        windowStartMillis = startMillis
        windowEndMillis = endMillis
        notifyDataSetChanged()
    }

    fun updateCurrentPrograms(programs: Map<String, ProgramSummary>) {
        val previous = currentPrograms
        currentPrograms = programs
        channels.forEachIndexed { index, channel ->
            if (previous[channel.sourceKey] != programs[channel.sourceKey]) {
                notifyItemChanged(index, PAYLOAD_CHANNEL_PROGRAM)
            }
        }
    }

    fun updateSchedules(values: Map<String, List<ProgramSummary>>) {
        values.forEach { (key, list) -> schedules[key] = list }
        values.keys.forEach { key ->
            channels.indexOfFirst { it.sourceKey == key }
                .takeIf { it >= 0 }
                ?.let { notifyItemChanged(it, PAYLOAD_SCHEDULES) }
        }
    }

    fun updateWindow(startMillis: Long, endMillis: Long) {
        windowStartMillis = startMillis
        windowEndMillis = endMillis
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SCHEDULES)
    }

    fun updateReminders(values: Map<String, Set<Long>>) {
        reminders = values
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SCHEDULES)
    }

    fun selectChannel(sourceKey: String?) {
        if (selectedChannelKey == sourceKey) return
        val previous = positionOf(selectedChannelKey)
        selectedChannelKey = sourceKey
        if (previous >= 0) notifyItemChanged(previous, PAYLOAD_SELECTION)
        positionOf(sourceKey).takeIf { it >= 0 }?.let {
            notifyItemChanged(it, PAYLOAD_SELECTION)
        }
    }

    fun positionOf(sourceKey: String?): Int = channels.indexOfFirst { it.sourceKey == sourceKey }

    fun channelAt(position: Int): LiveChannel? = channels.getOrNull(position)

    fun programsFor(row: Int): List<ProgramSummary> = channels.getOrNull(row)
        ?.let { schedules[it.sourceKey] }
        .orEmpty()

    fun focusChannel(recyclerView: RecyclerView, row: Int, allowScroll: Boolean = true) {
        if (row !in channels.indices) return
        if (allowScroll || recyclerView.findViewHolderForAdapterPosition(row) == null) {
            recyclerView.scrollToPosition(row)
        }
        recyclerView.post {
            val holder = recyclerView.findViewHolderForAdapterPosition(row) as? ViewHolder
            holder?.focusChannel()
        }
    }

    fun focusProgram(
        recyclerView: RecyclerView,
        row: Int,
        preferredTimeMillis: Long,
        allowScroll: Boolean = true,
    ) {
        if (row !in channels.indices) return
        if (allowScroll || recyclerView.findViewHolderForAdapterPosition(row) == null) {
            recyclerView.scrollToPosition(row)
        }
        recyclerView.post {
            val holder = recyclerView.findViewHolderForAdapterPosition(row) as? ViewHolder
            if (holder?.focusNearestProgram(preferredTimeMillis) != true) {
                holder?.focusRow()
            }
        }
    }

    fun focusRow(recyclerView: RecyclerView, row: Int, allowScroll: Boolean = true) {
        if (row !in channels.indices) return
        if (allowScroll || recyclerView.findViewHolderForAdapterPosition(row) == null) {
            recyclerView.scrollToPosition(row)
        }
        recyclerView.post {
            (recyclerView.findViewHolderForAdapterPosition(row) as? ViewHolder)?.focusRow()
        }
    }

    fun refreshLiveState(recyclerView: RecyclerView) {
        (0 until recyclerView.childCount).forEach { index ->
            (recyclerView.getChildViewHolder(recyclerView.getChildAt(index)) as? ViewHolder)
                ?.refreshLiveState()
        }
    }

    override fun getItemId(position: Int): Long = channels[position].sourceKey.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemGuideScheduleRowBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(channels[position], position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val channel = channels[position]
        if (PAYLOAD_SELECTION in payloads) holder.bindSelection(channel)
        if (PAYLOAD_CHANNEL_PROGRAM in payloads) holder.bindChannelCurrentProgram(channel)
        if (PAYLOAD_SCHEDULES in payloads) holder.bindScheduleCells(channel, position)
    }

    override fun getItemCount(): Int = channels.size

    inner class ViewHolder(
        val binding: ItemGuideScheduleRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val programViews = mutableListOf<Pair<ProgramSummary, View>>()

        fun bind(channel: LiveChannel, row: Int) {
            val rowHeight = (binding.root.resources.displayMetrics.heightPixels * ROW_HEIGHT_FRACTION).toInt()
            binding.root.layoutParams = binding.root.layoutParams.apply { height = rowHeight }

            bindChannelCell(channel, row, rowHeight)
            bindScheduleCells(channel, row)
        }

        private fun bindChannelCell(channel: LiveChannel, row: Int, rowHeight: Int) = with(binding) {
            channelNumber.text = channel.displayNumber
            channelName.text = channel.displayName
            bindChannelCurrentProgram(channel)

            if (channel.source == LiveChannel.Source.IPTV) {
                ChannelLogoLoader.load(channelLogo, channel.logoUrl, R.drawable.ic_tv)
            } else {
                channelLogo.dispose()
                channelLogo.setImageResource(R.drawable.ic_tv)
                runCatching { channelLogo.setImageURI(TvContract.buildChannelLogoUri(channel.id)) }
                if (channelLogo.drawable == null) channelLogo.setImageResource(R.drawable.ic_tv)
            }

            guideLockIcon.visibility = if (
                channel.locked || channel.encrypted || isParentalLocked(channel)
            ) View.VISIBLE else View.GONE

            bindSelection(channel)

            channelCell.setOnClickListener { onChannelSelected(channel) }
            channelCell.setOnFocusChangeListener { _, focused ->
                if (focused) {
                    selectedChannelKey = channel.sourceKey
                    onChannelFocused(row, channel)
                    val pos = bindingAdapterPosition
                    if (pos in channels.indices) {
                        val movingUp = lastFocusedPosition != RecyclerView.NO_POSITION && pos < lastFocusedPosition
                        lastFocusedPosition = pos
                        val candidates = if (movingUp) {
                            (pos - 1 downTo 0).asSequence().map { channels[it] }
                        } else {
                            channels.asSequence().drop(pos + 1)
                        }.map { candidate ->
                            if (candidate.source == LiveChannel.Source.IPTV) candidate.logoUrl
                            else TvContract.buildChannelLogoUri(candidate.id)
                        }
                        ChannelLogoLoader.prefetch(root.context, candidates)
                    }
                }
            }

            channelCell.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    val targetTime = System.currentTimeMillis()
                    if (focusNearestProgram(targetTime)) return@setOnKeyListener true
                    if (focusRow()) return@setOnKeyListener true
                }
                false
            }
        }

        fun bindSelection(channel: LiveChannel) {
            binding.channelCell.isSelected = channel.sourceKey == selectedChannelKey
        }

        fun bindChannelCurrentProgram(channel: LiveChannel) = with(binding) {
            val program = currentPrograms[channel.sourceKey]
            currentProgram.text = program?.title
                ?: root.context.getString(R.string.no_program_information)
            programProgress.progress = program?.let {
                val duration = (it.endTimeMillis - it.startTimeMillis).coerceAtLeast(1L)
                (((System.currentTimeMillis() - it.startTimeMillis) * 100L) / duration)
                    .toInt().coerceIn(0, 100)
            } ?: 0
        }

        fun bindScheduleCells(channel: LiveChannel, row: Int) {
            programViews.clear()
            val visiblePrograms = schedules[channel.sourceKey].orEmpty()
                .asSequence()
                .filter { it.endTimeMillis > windowStartMillis && it.startTimeMillis < windowEndMillis }
                .sortedBy { it.startTimeMillis }
                .toList()

            var childIndex = 0

            if (visiblePrograms.isEmpty()) {
                obtainEmptyCell(childIndex, channel, row)
                childIndex++
                trimExcessViews(childIndex)
                return
            }

            var cursor = windowStartMillis
            visiblePrograms.forEach { program ->
                val clippedStart = maxOf(program.startTimeMillis, windowStartMillis)
                val clippedEnd = minOf(program.endTimeMillis, windowEndMillis)
                if (clippedStart > cursor) {
                    obtainSpacer(childIndex, durationWeight(clippedStart - cursor))
                    childIndex++
                }
                val weight = durationWeight(clippedEnd - clippedStart)
                val view = obtainProgramCell(childIndex, channel, program, row, weight)
                childIndex++
                programViews += program to view
                cursor = maxOf(cursor, clippedEnd)
            }
            if (cursor < windowEndMillis) {
                obtainSpacer(childIndex, durationWeight(windowEndMillis - cursor))
                childIndex++
            }
            trimExcessViews(childIndex)
            refreshLiveState()
        }

        fun refreshLiveState() {
            val now = System.currentTimeMillis()
            programViews.forEach { (program, view) ->
                val textView = view as TextView
                val isLive = now in program.startTimeMillis until program.endTimeMillis
                textView.isActivated = isLive
                textView.setTypeface(null, if (isLive) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        fun focusChannel(): Boolean = binding.channelCell.requestFocus()

        fun focusNearestProgram(preferredTimeMillis: Long): Boolean {
            val target = programViews.firstOrNull { (program, _) ->
                preferredTimeMillis in program.startTimeMillis until program.endTimeMillis
            } ?: programViews.minByOrNull { (program, _) ->
                kotlin.math.abs(program.startTimeMillis - preferredTimeMillis)
            }
            return target?.second?.requestFocus() == true
        }

        fun focusRow(): Boolean {
            return (0 until binding.programCells.childCount)
                .asSequence()
                .map(binding.programCells::getChildAt)
                .firstOrNull(View::isFocusable)
                ?.requestFocus() == true
        }

        private fun trimExcessViews(keepCount: Int) {
            val total = binding.programCells.childCount
            if (total > keepCount) {
                binding.programCells.removeViews(keepCount, total - keepCount)
            }
        }

        private fun obtainSpacer(index: Int, weight: Float): View {
            val existing = binding.programCells.getChildAt(index)
            if (existing != null && existing.tag == TAG_SPACER) {
                val lp = existing.layoutParams as? LinearLayout.LayoutParams
                if (lp == null || lp.weight != weight) {
                    existing.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
                }
                return existing
            }
            val spacer = View(binding.root.context).apply {
                tag = TAG_SPACER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            }
            if (existing != null) {
                binding.programCells.removeViewAt(index)
            }
            binding.programCells.addView(spacer, index)
            return spacer
        }

        private fun obtainEmptyCell(index: Int, channel: LiveChannel, row: Int): TextView {
            val existing = binding.programCells.getChildAt(index) as? TextView
            val cell = if (existing != null && existing.tag == TAG_EMPTY_CELL) {
                existing
            } else {
                if (existing != null) binding.programCells.removeViewAt(index)
                createEmptyCell().also { binding.programCells.addView(it, index) }
            }
            updateEmptyCell(cell, channel, row)
            return cell
        }

        private fun obtainProgramCell(
            index: Int,
            channel: LiveChannel,
            program: ProgramSummary,
            row: Int,
            weight: Float,
        ): TextView {
            val existing = binding.programCells.getChildAt(index) as? TextView
            val cell = if (existing != null && existing.tag == TAG_PROGRAM_CELL) {
                existing
            } else {
                if (existing != null) binding.programCells.removeViewAt(index)
                createProgramCell().also { binding.programCells.addView(it, index) }
            }
            updateProgramCell(cell, channel, program, row, weight)
            return cell
        }

        private fun createEmptyCell(): TextView = TextView(binding.root.context).apply {
            tag = TAG_EMPTY_CELL
            background = ContextCompat.getDrawable(context, R.drawable.bg_guide_item)
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setPadding(dp(12), 0, dp(12), 0)
            setText(R.string.no_program_information)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
        }

        private fun updateEmptyCell(cell: TextView, channel: LiveChannel, row: Int) {
            val lp = cell.layoutParams as? LinearLayout.LayoutParams
            if (lp == null || lp.weight != 1f) {
                cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            cell.setOnFocusChangeListener { _, focused ->
                if (focused) onChannelFocused(row, channel)
            }
            cell.setOnClickListener { onChannelSelected(channel) }
            cell.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    binding.channelCell.requestFocus()
                    return@setOnKeyListener true
                }
                false
            }
        }

        private fun createProgramCell(): TextView = TextView(binding.root.context).apply {
            tag = TAG_PROGRAM_CELL
            background = ContextCompat.getDrawable(context, R.drawable.bg_guide_program_cell)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            maxLines = 2
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 13f
        }

        private fun updateProgramCell(
            cell: TextView,
            channel: LiveChannel,
            program: ProgramSummary,
            row: Int,
            weight: Float,
        ) {
            val lp = cell.layoutParams as? LinearLayout.LayoutParams
            if (lp == null || lp.weight != weight) {
                cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            }
            cell.text = program.title.ifBlank { cell.context.getString(R.string.untitled_program) }
            val now = System.currentTimeMillis()
            val archived = channel.source == LiveChannel.Source.IPTV &&
                channel.catchUpDays > 0 &&
                (!channel.catchUpSource.isNullOrBlank() || channel.catchUpMode == "xtream") &&
                program.endTimeMillis <= now &&
                program.endTimeMillis >= now - channel.catchUpDays * DAY_MILLIS
            val reminded = program.startTimeMillis in reminders[channel.sourceKey].orEmpty()
            cell.setCompoundDrawablesWithIntrinsicBounds(
                if (archived) R.drawable.ic_archive else 0,
                0,
                if (reminded) R.drawable.ic_clock_small else 0,
                0,
            )
            cell.compoundDrawablePadding = if (archived || reminded) dp(5) else 0
            cell.isSelected = channel.sourceKey == selectedChannelKey &&
                program.startTimeMillis == selectedProgramStart
            cell.setOnFocusChangeListener { _, focused ->
                if (focused) {
                    selectedChannelKey = channel.sourceKey
                    selectedProgramStart = program.startTimeMillis
                    onProgramFocused(row, channel, program)
                }
            }
            cell.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    val isFirst = programViews.firstOrNull()?.second === cell
                    if (isFirst) {
                        binding.channelCell.requestFocus()
                        return@setOnKeyListener true
                    }
                }
                false
            }
            cell.setOnClickListener { onProgramSelected(channel, program) }
            cell.setOnLongClickListener {
                onReminderToggle(channel, program)
                true
            }
        }

        private fun durationWeight(durationMillis: Long): Float =
            (durationMillis / 60_000f).coerceAtLeast(0.1f)

        private fun dp(value: Int): Int =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val ROW_HEIGHT_FRACTION = 0.072f
        const val LOGO_WIDTH_FRACTION = 0.62f
        const val LOGO_HEIGHT_FRACTION = 0.46f
        const val NUMBER_WIDTH_FRACTION = 0.78f
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
        const val TAG_SPACER = "guide_spacer"
        const val TAG_EMPTY_CELL = "guide_empty_cell"
        const val TAG_PROGRAM_CELL = "guide_program_cell"
        const val PAYLOAD_SELECTION = "selection"
        const val PAYLOAD_CHANNEL_PROGRAM = "channel_program"
        const val PAYLOAD_SCHEDULES = "schedules"
    }
}
