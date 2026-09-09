package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ItemGuideProgramBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuideProgramAdapter(
    private val onFocused: (ProgramSummary) -> Unit,
    private val onSelected: (ProgramSummary) -> Unit,
    private val onRemindToggle: ((ProgramSummary) -> Unit)? = null,
) : RecyclerView.Adapter<GuideProgramAdapter.ViewHolder>() {
    private val programs = mutableListOf<ProgramSummary>()
    private var remindedStarts: Set<Long> = emptySet()

    fun submitList(items: List<ProgramSummary>, remindedStarts: Set<Long> = emptySet()) {
        programs.clear()
        programs.addAll(items)
        this.remindedStarts = remindedStarts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemGuideProgramBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(programs[position])
    }

    override fun getItemCount(): Int = programs.size

    inner class ViewHolder(private val binding: ItemGuideProgramBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(program: ProgramSummary) = with(binding) {
            val density = root.resources.displayMetrics.density
            val durationMinutes = ((program.endTimeMillis - program.startTimeMillis) / 60_000f)
                .coerceAtLeast(1f)
            root.layoutParams = root.layoutParams.apply {
                width = (durationMinutes * WIDTH_DP_PER_MINUTE * density).toInt()
                    .coerceIn(
                        (MIN_WIDTH_DP * density).toInt(),
                        (MAX_WIDTH_DP * density).toInt(),
                    )
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            programTime.text = root.context.getString(
                R.string.program_time_format,
                timeFormat.format(Date(program.startTimeMillis)),
                timeFormat.format(Date(program.endTimeMillis)),
            )
            programTitle.text = program.title.ifBlank {
                root.context.getString(R.string.untitled_program)
            }
            val now = System.currentTimeMillis()
            liveBadge.visibility = if (now in program.startTimeMillis until program.endTimeMillis) {
                View.VISIBLE
            } else {
                View.GONE
            }
            reminderBadge.visibility =
                if (program.startTimeMillis in remindedStarts) View.VISIBLE else View.GONE
            root.setOnFocusChangeListener { _, focused ->
                root.scaleX = if (focused) 1.02f else 1.0f
                root.scaleY = if (focused) 1.02f else 1.0f
                if (focused) onFocused(program)
            }
            root.setOnClickListener { onSelected(program) }
            root.setOnLongClickListener {
                onRemindToggle?.invoke(program)
                onRemindToggle != null
            }
        }
    }

    private companion object {
        const val WIDTH_DP_PER_MINUTE = 3f
        const val MIN_WIDTH_DP = 132f
        const val MAX_WIDTH_DP = 480f
    }
}
