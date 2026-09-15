package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.model.LiveChannel

class MultiViewChannelAdapter(
    private val onChannelToggled: (LiveChannel, Int) -> Unit,
) : RecyclerView.Adapter<MultiViewChannelAdapter.ViewHolder>() {

    private val channels = mutableListOf<LiveChannel>()
    private val selectedKeys = linkedMapOf<String, Int>()

    fun submitList(items: List<LiveChannel>, selected: Map<String, LiveChannel>) {
        channels.clear()
        channels.addAll(items)
        updateSelectedKeys(selected)
        notifyDataSetChanged()
    }

    fun updateSelectedKeys(selected: Map<String, LiveChannel>) {
        selectedKeys.clear()
        selected.keys.forEachIndexed { index, key ->
            selectedKeys[key] = index + 1
        }
    }

    override fun getItemCount(): Int = channels.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_multiview_channel_choice, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        val slot = selectedKeys[channel.sourceKey]

        holder.channelNumber.text = channel.displayNumber
        holder.channelName.text = channel.displayName

        if (slot != null) {
            holder.slotBadge.text = slot.toString()
            holder.slotBadge.visibility = View.VISIBLE
            holder.checkbox.isChecked = true
        } else {
            holder.slotBadge.visibility = View.INVISIBLE
            holder.checkbox.isChecked = false
        }

        val isTif = channel.source == LiveChannel.Source.TIF
        holder.sourceBadge.text = if (isTif) "DVB" else "IPTV"
        holder.sourceBadge.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                holder.itemView.context,
                if (isTif) R.color.badge_tif else R.color.badge_iptv
            )
        )

        holder.itemView.setOnClickListener {
            onChannelToggled(channel, holder.bindingAdapterPosition)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val slotBadge: TextView = view.findViewById(R.id.choice_slot_badge)
        val channelNumber: TextView = view.findViewById(R.id.choice_channel_number)
        val channelName: TextView = view.findViewById(R.id.choice_channel_name)
        val sourceBadge: TextView = view.findViewById(R.id.choice_source_badge)
        val checkbox: CheckBox = view.findViewById(R.id.choice_checkbox)
    }
}
