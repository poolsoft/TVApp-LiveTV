package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ItemEditorChannelBinding
import com.tvapp.livetv.model.LiveChannel

class ChannelEditorAdapter(
    private val onFocused: (LiveChannel) -> Unit,
    private val onClicked: (LiveChannel) -> Unit,
) : RecyclerView.Adapter<ChannelEditorAdapter.ViewHolder>() {
    private val channels = mutableListOf<LiveChannel>()
    private var selectedKeys: Set<String> = emptySet()
    private var movingKeys: Set<String> = emptySet()
    var focusedSourceKey: String? = null
        private set

    fun submitList(items: List<LiveChannel>) {
        val old = channels.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = items.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition].sourceKey == items[newItemPosition].sourceKey

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition] == items[newItemPosition]
        })
        channels.clear()
        channels.addAll(items)
        diff.dispatchUpdatesTo(this)
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
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int = channels.size

    inner class ViewHolder(
        private val binding: ItemEditorChannelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(channel: LiveChannel) = with(binding) {
            channelNumber.text = channel.displayNumber
            channelName.text = channel.displayName
            selectionMark.text = when (channel.sourceKey) {
                in movingKeys -> "↕"
                in selectedKeys -> "✓"
                else -> ""
            }
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
            editorEncryptedIcon.visibility = if (channel.encrypted) View.VISIBLE else View.GONE
            editorHiddenIcon.visibility = if (channel.hidden) View.VISIBLE else View.GONE
            editorFavoriteIcon.visibility = if (channel.favorite) View.VISIBLE else View.GONE
            root.alpha = if (channel.hidden) 0.52f else 1f
            root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    focusedSourceKey = channel.sourceKey
                    onFocused(channel)
                }
            }
            root.setOnClickListener { onClicked(channel) }
        }
    }

    private companion object {
        const val PAYLOAD_STATE = "state"
    }
}
