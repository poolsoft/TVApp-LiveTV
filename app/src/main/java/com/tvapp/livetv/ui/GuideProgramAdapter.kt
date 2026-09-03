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
) : RecyclerView.Adapter<GuideProgramAdapter.ViewHolder>() {
    private val programs = mutableListOf<ProgramSummary>()

    fun submitList(items: List<ProgramSummary>) {
        programs.clear()
        programs.addAll(items)
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
            root.layoutParams = root.layoutParams.apply {
                height = (root.resources.displayMetrics.heightPixels * ROW_HEIGHT_FRACTION).toInt()
            }
            programTime.layoutParams = programTime.layoutParams.apply {
                width = (root.resources.displayMetrics.widthPixels * TIME_WIDTH_FRACTION).toInt()
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
            root.setOnFocusChangeListener { _, focused ->
                root.scaleX = if (focused) 1.02f else 1.0f
                root.scaleY = if (focused) 1.02f else 1.0f
                if (focused) onFocused(program)
            }
            root.setOnClickListener { onSelected(program) }
        }
    }

    private companion object {
        const val ROW_HEIGHT_FRACTION = 0.082f
        const val TIME_WIDTH_FRACTION = 0.055f
    }
}
