package com.tvapp.livetv.ui

import android.media.tv.TvContract
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import com.tvapp.livetv.R
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ItemChannelBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.image.ChannelLogoLoader

class ChannelAdapter(
    private val onSelected: (LiveChannel) -> Unit,
    private val onManage: (LiveChannel) -> Unit,
    private val onFocused: (LiveChannel) -> Unit,
    private val isParentalLocked: (LiveChannel) -> Boolean,
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {
    private val channels = mutableListOf<LiveChannel>()
    private var programs: Map<Long, ProgramSummary> = emptyMap()
    private var rowOptions = ChannelRowOptions()
    private var showIptvMembership = false
    private var selectedId: Long? = null

    fun submitList(items: List<LiveChannel>) {
        val previous = channels.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = items.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition].sourceKey == items[newItemPosition].sourceKey

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition] == items[newItemPosition]
        })
        channels.clear()
        channels.addAll(items)
        diff.dispatchUpdatesTo(this)
    }

    fun appendItems(items: List<LiveChannel>) {
        if (items.isEmpty()) return
        val start = channels.size
        channels.addAll(items)
        notifyItemRangeInserted(start, items.size)
    }

    fun select(sourceKey: String) {
        val oldId = selectedId
        val channel = channels.firstOrNull { it.sourceKey == sourceKey } ?: return
        if (oldId == channel.id) return
        selectedId = channel.id
        oldId?.let { id ->
            val oldIndex = channels.indexOfFirst { it.id == id }
            if (oldIndex >= 0) notifyItemChanged(oldIndex, PAYLOAD_SELECTION)
        }
        val newIndex = channels.indexOfFirst { it.sourceKey == sourceKey }
        if (newIndex >= 0) notifyItemChanged(newIndex, PAYLOAD_SELECTION)
    }

    fun submitPrograms(items: Map<Long, ProgramSummary>) {
        val previous = programs
        programs = items
        channels.forEachIndexed { index, channel ->
            if (previous[channel.id] != items[channel.id]) {
                notifyItemChanged(index, PAYLOAD_PROGRAM)
            }
        }
    }

    fun applyRowOptions(options: ChannelRowOptions) {
        rowOptions = options
        notifyItemRangeChanged(0, itemCount)
    }

    fun showIptvMembership(show: Boolean) {
        if (showIptvMembership == show) return
        showIptvMembership = show
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.bind(channel, programs[channel.id])
    }

    override fun onBindViewHolder(
        holder: ChannelViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val channel = channels[position]
        if (PAYLOAD_PROGRAM in payloads) holder.bindProgram(channel, programs[channel.id])
        if (PAYLOAD_SELECTION in payloads) holder.bindSelection(channel)
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: LiveChannel, program: ProgramSummary?) = with(binding) {
            val metrics = root.resources.displayMetrics
            val screenHeight = metrics.heightPixels
            val rowHeight = (screenHeight * ROW_HEIGHT_FRACTION).toInt()
            root.layoutParams = root.layoutParams.apply { height = rowHeight }
            channelLogo.layoutParams = channelLogo.layoutParams.apply {
                width = (rowHeight * LOGO_WIDTH_FRACTION).toInt()
                height = (rowHeight * LOGO_HEIGHT_FRACTION).toInt()
            }
            channelNumber.layoutParams = channelNumber.layoutParams.apply {
                width = (metrics.widthPixels * NUMBER_WIDTH_FRACTION).toInt()
            }
            val displayNumber = channel.displayNumber.ifBlank { "-" }
            channelNumber.text = displayNumber
            channelNumber.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                when (displayNumber.length) {
                    in 0..3 -> 16f
                    4 -> 13f
                    else -> 11f
                },
            )
            channelName.text = channel.displayName
            channelQuality.visibility = View.GONE
            channelTypeIcon.setImageResource(
                when {
                    channel.isRadioChannel() -> R.drawable.ic_radio
                    channel.iptvContentType == "VOD" -> R.drawable.ic_vod
                    channel.source == LiveChannel.Source.IPTV -> R.drawable.ic_live
                    else -> R.drawable.ic_channel_tv
                },
            )
            channelTypeIcon.contentDescription = root.context.getString(
                if (channel.iptvContentType == "VOD") R.string.iptv_library_vod
                else R.string.channel_type,
            )
            channelMainListMarker.visibility = if (
                showIptvMembership && channel.source == LiveChannel.Source.IPTV && channel.inMainList
            ) View.VISIBLE else View.GONE
            val locked = channel.encrypted || channel.locked || isParentalLocked(channel)
            channelEncryptedIcon.visibility = if (locked) View.VISIBLE else View.GONE
            channelEncryptedIcon.contentDescription = root.context.getString(
                if (isParentalLocked(channel)) R.string.locked_channel else R.string.encrypted_channel,
            )
            channelHiddenIcon.visibility = View.GONE
            channelFavoriteIcon.visibility = View.GONE
            bindProgram(channel, program)
            val fallbackLogo = if (channel.isRadioChannel()) {
                R.drawable.ic_radio
            } else {
                R.drawable.ic_tv
            }
            if (channel.source == LiveChannel.Source.IPTV) {
                ChannelLogoLoader.load(channelLogo, channel.logoUrl, fallbackLogo)
            } else {
                channelLogo.dispose()
                channelLogo.setImageResource(fallbackLogo)
                runCatching {
                    channelLogo.setImageURI(TvContract.buildChannelLogoUri(channel.id))
                }
                if (channelLogo.drawable == null) {
                    channelLogo.setImageResource(fallbackLogo)
                }
            }
            channelLogo.visibility = if (rowOptions.showLogo) View.VISIBLE else View.GONE
            channelProgram.visibility = if (rowOptions.showProgram) View.VISIBLE else View.GONE
            programProgress.visibility = if (rowOptions.showProgress) View.VISIBLE else View.GONE
            sourceBadge.visibility = View.GONE
            bindSelection(channel)
            root.setOnClickListener {
                select(channel.sourceKey)
                onSelected(channel)
            }
            root.setOnLongClickListener {
                onManage(channel)
                true
            }
            root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) onFocused(channel)
            }
        }

        fun bindProgram(channel: LiveChannel, program: ProgramSummary?) = with(binding) {
            channelProgram.text = program?.title.orEmpty().ifBlank {
                root.context.getString(R.string.no_program_information)
            }
            val now = System.currentTimeMillis()
            programProgress.progress = if (program == null) {
                0
            } else {
                val duration = (program.endTimeMillis - program.startTimeMillis).coerceAtLeast(1L)
                (((now - program.startTimeMillis) * 100L / duration).coerceIn(0L, 100L)).toInt()
            }
            sourceBadge.text = channel.source.name
        }

        fun bindSelection(channel: LiveChannel) {
            binding.root.isSelected = channel.id == selectedId
        }
    }

    private companion object {
        const val ROW_HEIGHT_FRACTION = 0.105f
        const val LOGO_WIDTH_FRACTION = 0.58f
        const val LOGO_HEIGHT_FRACTION = 0.45f
        const val NUMBER_WIDTH_FRACTION = 0.042f
        const val PAYLOAD_PROGRAM = "program"
        const val PAYLOAD_SELECTION = "selection"
    }
}

data class ChannelRowOptions(
    val showLogo: Boolean = false,
    val showProgram: Boolean = true,
    val showProgress: Boolean = true,
    val showSourceBadge: Boolean = false,
)
