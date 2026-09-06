package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ItemEditorChannelBinding
import com.tvapp.livetv.model.LiveChannel

class ChannelEditorAdapter(
    private val onFocused: (LiveChannel) -> Unit,
    private val onClicked: (LiveChannel) -> Unit,
    private val isParentalLocked: (LiveChannel) -> Boolean,
) : RecyclerView.Adapter<ChannelEditorAdapter.ViewHolder>() {
    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<LiveChannel>() {
        override fun areItemsTheSame(oldItem: LiveChannel, newItem: LiveChannel) =
            oldItem.sourceKey == newItem.sourceKey

        override fun areContentsTheSame(oldItem: LiveChannel, newItem: LiveChannel) =
            oldItem == newItem
    })
    private var selectedKeys: Set<String> = emptySet()
    private var movingKeys: Set<String> = emptySet()
    var focusedSourceKey: String? = null
        private set

    fun submitList(items: List<LiveChannel>, commitCallback: (() -> Unit)? = null) =
        differ.submitList(items.toList(), commitCallback)

    fun notifyChannelStateChanged(sourceKey: String) {
        val position = differ.currentList.indexOfFirst { it.sourceKey == sourceKey }
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
        holder.bind(differ.currentList[position])
    }

    override fun getItemCount(): Int = differ.currentList.size

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
            val locked = channel.encrypted || channel.locked || isParentalLocked(channel)
            editorEncryptedIcon.visibility = if (locked) View.VISIBLE else View.GONE
            editorEncryptedIcon.contentDescription = root.context.getString(
                if (isParentalLocked(channel)) R.string.locked_channel else R.string.encrypted_channel,
            )
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
