package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.image.ChannelLogoLoader
import com.tvapp.livetv.playback.ContinueWatchingItem

/**
 * Shared adapter for the VOD home rows (Continue Watching strip and VOD grid).
 * Uses stable IDs and DiffUtil so refreshes never blink the whole list.
 * Two view types: resume cards show remaining time + thin progress bar.
 */
class VodHomeAdapter(
    private val onItemClick: (ContinueWatchingItem) -> Unit,
    private val onItemLongClick: (ContinueWatchingItem) -> Unit,
    private val onFocusChanged: (ContinueWatchingItem?) -> Unit,
    private val showResumeDetails: Boolean,
) : ListAdapter<ContinueWatchingItem, VodHomeAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vod_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemId(position: Int): Long =
        getItem(position).channel.sourceKey.hashCode().toLong()

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clearLogo()
        super.onViewRecycled(holder)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.vod_card_root)
        private val logo: ImageView = itemView.findViewById(R.id.vod_card_logo)
        private val name: TextView = itemView.findViewById(R.id.vod_card_name)
        private val badge: TextView = itemView.findViewById(R.id.vod_card_badge)
        private val progress: TextView = itemView.findViewById(R.id.vod_card_progress)
        private val progressBar: LinearLayout =
            itemView.findViewById(R.id.vod_card_progress_bar)
        private val progressFill: View = itemView.findViewById(R.id.vod_card_progress_fill)
        private var bound: ContinueWatchingItem? = null

        init {
            itemView.setOnClickListener { bound?.let(onItemClick) }
            itemView.setOnLongClickListener { bound?.let(onItemLongClick) != null }
            itemView.setOnFocusChangeListener { view, hasFocus ->
                // Netflix-style focus: enlarge the focused card, dim it out when not.
                view.animate()
                    .scaleX(if (hasFocus) 1.08f else 1.0f)
                    .scaleY(if (hasFocus) 1.08f else 1.0f)
                    .setDuration(FOCUS_ANIM_MS)
                    .start()
                root.setBackgroundResource(
                    if (hasFocus) R.drawable.bg_vod_card_focused else R.drawable.bg_vod_card,
                )
                itemView.translationZ = if (hasFocus) 8f else 0f
                if (hasFocus) bound?.let(onFocusChanged)
            }
        }

        fun bind(item: ContinueWatchingItem) {
            bound = item
            name.text = item.channel.displayName
            ChannelLogoLoader.load(logo, item.channel.logoUrl, R.drawable.app_banner)
            val entry = item.resumeEntry
            val hasPosition = entry != null && entry.positionMillis > 0L
            if (showResumeDetails && hasPosition) {
                val played = entry.positionMillis
                val total = entry.durationMillis
                if (total > 0L && played < total) {
                    val percent = ((played * 100L) / total).toInt().coerceIn(1, 99)
                    badge.text = "%$percent"
                    badge.visibility = View.VISIBLE
                    progressBar.visibility = View.VISIBLE
                    progressBar.post {
                        val w = progressBar.width
                        progressFill.layoutParams = progressFill.layoutParams.apply {
                            if (this is LinearLayout.LayoutParams) this.width = w * percent / 100
                        }
                        progressFill.requestLayout()
                    }
                    progress.text = progressText(played, total)
                    progress.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    progress.visibility = View.GONE
                }
            } else {
                badge.visibility = View.GONE
                progressBar.visibility = View.GONE
                progress.visibility = View.GONE
            }
        }

        fun clearLogo() {
            ChannelLogoLoader.load(logo, null, R.drawable.app_banner)
        }
    }

    companion object {
        const val FOCUS_ANIM_MS = 180L

        val DIFF = object : DiffUtil.ItemCallback<ContinueWatchingItem>() {
            override fun areItemsTheSame(
                oldItem: ContinueWatchingItem,
                newItem: ContinueWatchingItem,
            ): Boolean = oldItem.channel.sourceKey == newItem.channel.sourceKey

            override fun areContentsTheSame(
                oldItem: ContinueWatchingItem,
                newItem: ContinueWatchingItem,
            ): Boolean = oldItem == newItem
        }

        fun formatDuration(millis: Long): String {
            val totalSeconds = millis / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun progressText(playedMillis: Long, totalMillis: Long): String =
            formatDuration((totalMillis - playedMillis).coerceAtLeast(0L))
    }
}
