package com.tvapp.livetv.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ItemGuideScheduleRowBinding
import com.tvapp.livetv.model.LiveChannel

class GuideScheduleAdapter(
    private val onChannelFocused: (Int, LiveChannel) -> Unit,
    private val onProgramFocused: (Int, LiveChannel, ProgramSummary) -> Unit,
    private val onProgramSelected: (LiveChannel, ProgramSummary) -> Unit,
    private val onReminderToggle: (LiveChannel, ProgramSummary) -> Unit,
) : RecyclerView.Adapter<GuideScheduleAdapter.ViewHolder>() {
    private val channels = mutableListOf<LiveChannel>()
    private val schedules = mutableMapOf<String, List<ProgramSummary>>()
    private var reminders: Map<String, Set<Long>> = emptyMap()
    private var windowStartMillis = 0L
    private var windowEndMillis = 0L
    private var selectedChannelKey: String? = null
    private var selectedProgramStart: Long? = null

    init {
        setHasStableIds(true)
    }

    fun submitChannels(items: List<LiveChannel>, startMillis: Long, endMillis: Long) {
        channels.clear()
        channels.addAll(items)
        schedules.clear()
        windowStartMillis = startMillis
        windowEndMillis = endMillis
        notifyDataSetChanged()
    }

    fun updateSchedules(values: Map<String, List<ProgramSummary>>) {
        values.forEach { (key, programs) -> schedules[key] = programs }
        values.keys.forEach { key ->
            channels.indexOfFirst { it.sourceKey == key }
                .takeIf { it >= 0 }
                ?.let { notifyItemChanged(it) }
        }
    }

    fun updateWindow(startMillis: Long, endMillis: Long) {
        windowStartMillis = startMillis
        windowEndMillis = endMillis
        notifyItemRangeChanged(0, itemCount)
    }

    fun updateReminders(values: Map<String, Set<Long>>) {
        reminders = values
        notifyItemRangeChanged(0, itemCount)
    }

    fun programsFor(row: Int): List<ProgramSummary> = channels.getOrNull(row)
        ?.let { schedules[it.sourceKey] }
        .orEmpty()

    fun focusProgram(recyclerView: RecyclerView, row: Int, preferredTimeMillis: Long) {
        if (row !in channels.indices) return
        recyclerView.scrollToPosition(row)
        recyclerView.post {
            val holder = recyclerView.findViewHolderForAdapterPosition(row) as? ViewHolder
            if (holder?.focusNearestProgram(preferredTimeMillis) != true) {
                holder?.focusRow()
            }
        }
    }

    fun focusRow(recyclerView: RecyclerView, row: Int) {
        if (row !in channels.indices) return
        recyclerView.scrollToPosition(row)
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

    override fun getItemCount(): Int = channels.size

    inner class ViewHolder(
        private val binding: ItemGuideScheduleRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val programViews = mutableListOf<Pair<ProgramSummary, View>>()

        fun bind(channel: LiveChannel, row: Int) {
            val rowHeight = (binding.root.resources.displayMetrics.heightPixels * ROW_HEIGHT_FRACTION).toInt()
            binding.root.layoutParams = binding.root.layoutParams.apply { height = rowHeight }
            binding.programCells.removeAllViews()
            programViews.clear()
            val visiblePrograms = schedules[channel.sourceKey].orEmpty()
                .asSequence()
                .filter { it.endTimeMillis > windowStartMillis && it.startTimeMillis < windowEndMillis }
                .sortedBy { it.startTimeMillis }
                .toList()
            if (visiblePrograms.isEmpty()) {
                binding.programCells.addView(emptyCell(channel, row))
                return
            }
            var cursor = windowStartMillis
            visiblePrograms.forEach { program ->
                val clippedStart = maxOf(program.startTimeMillis, windowStartMillis)
                val clippedEnd = minOf(program.endTimeMillis, windowEndMillis)
                if (clippedStart > cursor) addSpacer(clippedStart - cursor)
                val view = programCell(channel, program, row)
                binding.programCells.addView(
                    view,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        durationWeight(clippedEnd - clippedStart),
                    ),
                )
                programViews += program to view
                cursor = maxOf(cursor, clippedEnd)
            }
            if (cursor < windowEndMillis) addSpacer(windowEndMillis - cursor)
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

        fun focusNearestProgram(preferredTimeMillis: Long): Boolean {
            val target = programViews.firstOrNull { (program, _) ->
                preferredTimeMillis in program.startTimeMillis until program.endTimeMillis
            } ?: programViews.minByOrNull { (program, _) ->
                kotlin.math.abs(program.startTimeMillis - preferredTimeMillis)
            }
            return target?.second?.requestFocus() == true
        }

        fun focusRow() {
            (0 until binding.programCells.childCount)
                .asSequence()
                .map(binding.programCells::getChildAt)
                .firstOrNull(View::isFocusable)
                ?.requestFocus()
        }

        private fun emptyCell(channel: LiveChannel, row: Int): TextView = TextView(binding.root.context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            background = ContextCompat.getDrawable(context, R.drawable.bg_guide_item)
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setPadding(dp(12), 0, dp(12), 0)
            setText(R.string.no_epg_data)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
            setOnFocusChangeListener { _, focused -> if (focused) onChannelFocused(row, channel) }
        }

        private fun programCell(channel: LiveChannel, program: ProgramSummary, row: Int): TextView =
            TextView(binding.root.context).apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_guide_program_cell)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                maxLines = 2
                setPadding(dp(10), dp(5), dp(10), dp(5))
                text = program.title.ifBlank { context.getString(R.string.untitled_program) }
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 13f
                val now = System.currentTimeMillis()
                val archived = channel.source == LiveChannel.Source.IPTV &&
                    channel.catchUpDays > 0 &&
                    (!channel.catchUpSource.isNullOrBlank() || channel.catchUpMode == "xtream") &&
                    program.endTimeMillis <= now &&
                    program.endTimeMillis >= now - channel.catchUpDays * DAY_MILLIS
                val reminded = program.startTimeMillis in reminders[channel.sourceKey].orEmpty()
                setCompoundDrawablesWithIntrinsicBounds(
                    if (archived) R.drawable.ic_archive else 0,
                    0,
                    if (reminded) R.drawable.ic_clock_small else 0,
                    0,
                )
                compoundDrawablePadding = if (archived || reminded) dp(5) else 0
                isSelected = channel.sourceKey == selectedChannelKey &&
                    program.startTimeMillis == selectedProgramStart
                setOnFocusChangeListener { view, focused ->
                    view.scaleX = if (focused) 1.015f else 1f
                    view.scaleY = if (focused) 1.015f else 1f
                    if (focused) {
                        selectedChannelKey = channel.sourceKey
                        selectedProgramStart = program.startTimeMillis
                        onProgramFocused(row, channel, program)
                    }
                }
                setOnClickListener { onProgramSelected(channel, program) }
                setOnLongClickListener {
                    onReminderToggle(channel, program)
                    true
                }
            }

        private fun addSpacer(durationMillis: Long) {
            binding.programCells.addView(
                View(binding.root.context),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    durationWeight(durationMillis),
                ),
            )
        }

        private fun durationWeight(durationMillis: Long): Float =
            (durationMillis / 60_000f).coerceAtLeast(0.1f)

        private fun dp(value: Int): Int =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val ROW_HEIGHT_FRACTION = 0.072f
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
