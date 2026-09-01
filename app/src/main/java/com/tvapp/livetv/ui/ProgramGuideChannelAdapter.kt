package com.tvapp.livetv.ui

import android.media.tv.TvContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import com.tvapp.livetv.R
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ItemGuideChannelBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.image.ChannelLogoLoader

class ProgramGuideChannelAdapter(
    private val onFocused: (LiveChannel) -> Unit,
    private val onSelected: (LiveChannel) -> Unit,
    private val isParentalLocked: (LiveChannel) -> Boolean,
) : RecyclerView.Adapter<ProgramGuideChannelAdapter.ViewHolder>() {
    private val channels = mutableListOf<LiveChannel>()
    private var currentPrograms: Map<Long, ProgramSummary> = emptyMap()
    private var selectedKey: String? = null

    fun submitList(items: List<LiveChannel>, programs: Map<Long, ProgramSummary>) {
        channels.clear()
        channels.addAll(items)
        currentPrograms = programs
        notifyDataSetChanged()
    }

    fun select(sourceKey: String) {
        selectedKey = sourceKey
        notifyDataSetChanged()
    }

    fun positionOf(sourceKey: String?): Int = channels.indexOfFirst { it.sourceKey == sourceKey }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemGuideChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int = channels.size

    inner class ViewHolder(private val binding: ItemGuideChannelBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: LiveChannel) = with(binding) {
            val rowHeight = (root.resources.displayMetrics.heightPixels * ROW_HEIGHT_FRACTION).toInt()
            root.layoutParams = root.layoutParams.apply { height = rowHeight }
            channelLogo.layoutParams = channelLogo.layoutParams.apply {
                width = (rowHeight * LOGO_WIDTH_FRACTION).toInt()
                height = (rowHeight * LOGO_HEIGHT_FRACTION).toInt()
            }
            channelNumber.layoutParams = channelNumber.layoutParams.apply {
                width = (rowHeight * NUMBER_WIDTH_FRACTION).toInt()
            }
            channelNumber.text = channel.displayNumber
            channelName.text = channel.displayName
            val program = currentPrograms[channel.id].takeIf {
                channel.source == LiveChannel.Source.TIF
            }
            currentProgram.text = program?.title ?: root.context.getString(R.string.no_program_information)
            programProgress.progress = program?.let {
                val duration = (it.endTimeMillis - it.startTimeMillis).coerceAtLeast(1L)
                (((System.currentTimeMillis() - it.startTimeMillis) * 100L) / duration)
                    .toInt().coerceIn(0, 100)
            } ?: 0
            if (channel.source == LiveChannel.Source.IPTV) {
                ChannelLogoLoader.load(channelLogo, channel.logoUrl, R.drawable.ic_tv)
            } else {
                channelLogo.dispose()
                channelLogo.setImageResource(R.drawable.ic_tv)
                runCatching { channelLogo.setImageURI(TvContract.buildChannelLogoUri(channel.id)) }
                if (channelLogo.drawable == null) channelLogo.setImageResource(R.drawable.ic_tv)
            }
            root.isSelected = channel.sourceKey == selectedKey
            guideLockIcon.visibility = if (
                channel.locked || channel.encrypted || isParentalLocked(channel)
            ) View.VISIBLE else View.GONE
            root.setOnFocusChangeListener { _, focused -> if (focused) onFocused(channel) }
            root.setOnClickListener { onSelected(channel) }
        }
    }

    private companion object {
        const val ROW_HEIGHT_FRACTION = 0.072f
        const val LOGO_WIDTH_FRACTION = 0.62f
        const val LOGO_HEIGHT_FRACTION = 0.46f
        const val NUMBER_WIDTH_FRACTION = 0.78f
    }
}
