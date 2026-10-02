package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.data.local.VodCatalogItem
import com.tvapp.livetv.image.ChannelLogoLoader
import com.tvapp.livetv.playback.IptvResumeEntry

data class VodCard(val item: VodCatalogItem, val resume: IptvResumeEntry? = null)

class VodHomeAdapter(
    private val onItemClick: (VodCard) -> Unit,
    private val onItemLongClick: (VodCard) -> Unit,
    private val onFocusChanged: (VodCard, Int) -> Unit,
    private val compact: Boolean = false,
) : ListAdapter<VodCard, VodHomeAdapter.ViewHolder>(DIFF) {
    init { setHasStableIds(true) }
    override fun getItemId(position: Int): Long = getItem(position).item.sourceKey
        .fold(1125899906842597L) { hash, ch -> 31 * hash + ch.code }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vod_card, parent, false)
        if (compact) view.layoutParams.width = (200 * parent.resources.displayMetrics.density).toInt()
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
    override fun onViewRecycled(holder: ViewHolder) { holder.clearLogo(); super.onViewRecycled(holder) }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logo: ImageView = itemView.findViewById(R.id.vod_card_logo)
        private val name: TextView = itemView.findViewById(R.id.vod_card_name)
        private val progress: TextView = itemView.findViewById(R.id.vod_card_progress)
        private var bound: VodCard? = null
        init {
            if (compact) logo.visibility = View.GONE
            itemView.setOnClickListener { bound?.let(onItemClick) }
            itemView.setOnLongClickListener { bound?.let(onItemLongClick) != null }
            itemView.setOnFocusChangeListener { _, focus ->
                if (focus && bindingAdapterPosition != RecyclerView.NO_POSITION) bound?.let { onFocusChanged(it, bindingAdapterPosition) }
            }
        }
        fun bind(card: VodCard) {
            bound = card
            name.text = card.item.name
            itemView.contentDescription = card.item.name
            if (!compact) ChannelLogoLoader.load(logo, card.item.logoUrl, R.drawable.ic_vod)
            progress.text = listOfNotNull(
                if (card.item.favorite) itemView.context.getString(R.string.vod_favorites) else null,
                card.resume?.let { formatDuration(it.positionMillis) },
            ).joinToString(" · ")
        }
        fun clearLogo() { bound = null; ChannelLogoLoader.load(logo, null, R.drawable.ic_vod) }
    }
    companion object {
        val DIFF = object : DiffUtil.ItemCallback<VodCard>() {
            override fun areItemsTheSame(old: VodCard, new: VodCard) = old.item.sourceKey == new.item.sourceKey
            override fun areContentsTheSame(old: VodCard, new: VodCard) = old == new
        }
        fun formatDuration(millis: Long): String {
            val seconds = millis.coerceAtLeast(0) / 1000
            return if (seconds >= 3600) String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format("%02d:%02d", seconds / 60, seconds % 60)
        }
    }
}
