package com.tvapp.livetv.vod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R

data class VodEpisodeItem(
    val index: Int,
    val title: String,
    val sourceKey: String? = null,
    val streamUrl: String? = null,
    val isCurrent: Boolean = false,
)

class VodEpisodePanelAdapter(
    private val onEpisodeSelected: (VodEpisodeItem) -> Unit,
) : RecyclerView.Adapter<VodEpisodePanelAdapter.ViewHolder>() {

    private var items: List<VodEpisodeItem> = emptyList()
    private var selectedIndex: Int = -1

    fun submitList(newItems: List<VodEpisodeItem>, currentActiveIndex: Int = -1) {
        items = newItems
        selectedIndex = currentActiveIndex
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vod_episode_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isCurrent = position == selectedIndex || item.isCurrent
        holder.bind(item, isCurrent)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.episode_icon)
        private val title: TextView = itemView.findViewById(R.id.episode_title)
        private val activeBadge: TextView = itemView.findViewById(R.id.episode_active_badge)

        fun bind(item: VodEpisodeItem, isCurrent: Boolean) {
            title.text = item.title
            icon.visibility = if (isCurrent) View.VISIBLE else View.GONE
            activeBadge.visibility = if (isCurrent) View.VISIBLE else View.GONE
            itemView.isSelected = isCurrent

            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEpisodeSelected(items[pos])
                }
            }
        }
    }
}
