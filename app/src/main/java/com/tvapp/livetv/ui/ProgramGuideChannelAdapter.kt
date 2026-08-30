package com.tvapp.livetv.ui

import android.media.tv.TvContract
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ItemGuideChannelBinding
import com.tvapp.livetv.model.LiveChannel

class ProgramGuideChannelAdapter(
    private val onFocused: (LiveChannel) -> Unit,
    private val onSelected: (LiveChannel) -> Unit,
) : RecyclerView.Adapter<ProgramGuideChannelAdapter.ViewHolder>() {
    private val channels = mutableListOf<LiveChannel>()
    private var selectedKey: String? = null

    fun submitList(items: List<LiveChannel>) {
        channels.clear()
        channels.addAll(items)
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
            channelLogo.setImageResource(R.drawable.ic_tv)
            runCatching { channelLogo.setImageURI(TvContract.buildChannelLogoUri(channel.id)) }
            if (channelLogo.drawable == null) channelLogo.setImageResource(R.drawable.ic_tv)
            root.isSelected = channel.sourceKey == selectedKey
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
