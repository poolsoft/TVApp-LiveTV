package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ItemEditorChannelBinding
import com.tvapp.livetv.model.LiveChannel

class ChannelEditorAdapter(
    private val onFocused: (LiveChannel) -> Unit,
    private val onClicked: (LiveChannel) -> Unit,
    private val isParentalLocked: (LiveChannel) -> Boolean,
) : RecyclerView.Adapter<ChannelEditorAdapter.ViewHolder>() {

    private val items = mutableListOf<LiveChannel>()
    private var selectedKeys: Set<String> = emptySet()
    private var movingKeys: Set<String> = emptySet()
    var focusedSourceKey: String? = null
        private set

    val currentList: List<LiveChannel>
        get() = items

    fun submitList(newItems: List<LiveChannel>, commitCallback: (() -> Unit)? = null) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        commitCallback?.invoke()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in items.indices || toPosition !in items.indices || fromPosition == toPosition) return
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        notifyItemChanged(fromPosition, PAYLOAD_NUMBER)
        notifyItemChanged(toPosition, PAYLOAD_NUMBER)
    }

    fun notifyChannelStateChanged(sourceKey: String) {
        val position = items.indexOfFirst { it.sourceKey == sourceKey }
        if (position >= 0) notifyItemChanged(position, PAYLOAD_STATE)
    }

    fun setSelection(selected: Set<String>, moving: Set<String> = emptySet()) {
        selectedKeys = selected
        movingKeys = moving
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemEditorChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val positionSafe = holder.bindingAdapterPosition
            if (positionSafe != RecyclerView.NO_POSITION && positionSafe in items.indices) {
                if (payloads.contains(PAYLOAD_NUMBER)) {
                    holder.updateNumber(positionSafe + 1)
                }
                if (payloads.contains(PAYLOAD_STATE)) {
                    holder.updateState(items[positionSafe])
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemEditorChannelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun updateNumber(displayOrder: Int) {
            binding.channelNumber.text = displayOrder.toString()
        }

        fun updateState(channel: LiveChannel) = with(binding) {
            selectionMark.text = when (channel.sourceKey) {
                in movingKeys -> "↕"
                in selectedKeys -> "✓"
                else -> ""
            }
            val locked = channel.encrypted || channel.locked || isParentalLocked(channel)
            editorEncryptedIcon.visibility = if (locked) View.VISIBLE else View.GONE
            editorHiddenIcon.visibility = if (channel.hidden) View.VISIBLE else View.GONE
            editorFavoriteIcon.visibility = if (channel.favorite) View.VISIBLE else View.GONE
            root.alpha = if (channel.hidden) 0.52f else 1f
        }

        fun bind(channel: LiveChannel, position: Int) = with(binding) {
            channelNumber.text = (position + 1).toString()
            channelName.text = channel.displayName
            updateState(channel)
            editorQuality.text = channel.qualityLabel()
            editorQuality.visibility = if (editorQuality.text.isNullOrBlank()) View.GONE else View.VISIBLE
            editorSourceIcon.setImageResource(
                if (channel.source == LiveChannel.Source.IPTV) {
                    R.drawable.ic_source_iptv
                } else {
                    R.drawable.ic_source_tif
                },
            )
            editorSourceIcon.contentDescription = root.context.getString(
                if (channel.source == LiveChannel.Source.IPTV) {
                    R.string.iptv_source
                } else {
                    R.string.tif_source
                },
            )
            editorTypeIcon.setImageResource(
                if (channel.isRadioChannel()) R.drawable.ic_radio else R.drawable.ic_channel_tv,
            )
            editorEncryptedIcon.contentDescription = root.context.getString(
                if (isParentalLocked(channel)) R.string.locked_channel else R.string.encrypted_channel,
            )

            root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val currentPos = bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION && currentPos in items.indices) {
                        val currentChannel = items[currentPos]
                        focusedSourceKey = currentChannel.sourceKey
                        onFocused(currentChannel)
                    }
                }
            }
            root.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos in items.indices) {
                    onClicked(items[currentPos])
                }
            }
        }
    }

    private companion object {
        const val PAYLOAD_STATE = "state"
        const val PAYLOAD_NUMBER = "number"
    }
}
