package com.tvapp.livetv.vod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tvapp.livetv.R
import com.tvapp.livetv.data.local.VodCatalogItem
import com.tvapp.livetv.data.local.VodTmdbEntity
import com.tvapp.livetv.databinding.ItemVodPosterCardBinding

data class VodDisplayItem(
    val catalogItem: VodCatalogItem,
    val tmdb: VodTmdbEntity? = null,
)

class VodPosterAdapter(
    private val onItemClick: (VodDisplayItem) -> Unit,
    private val onItemFocused: (VodDisplayItem) -> Unit,
) : ListAdapter<VodDisplayItem, VodPosterAdapter.ViewHolder>(DiffCallback) {

    fun updateTmdbItem(sourceKey: String, tmdb: VodTmdbEntity) {
        val items = currentList
        val index = items.indexOfFirst { it.catalogItem.sourceKey == sourceKey }
        if (index != -1) {
            val updated = items.toMutableList()
            updated[index] = updated[index].copy(tmdb = tmdb)
            submitList(updated)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVodPosterCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemVodPosterCardBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(pos))
                }
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                binding.root.setBackgroundResource(
                    if (hasFocus) R.drawable.bg_vod_poster_focused else R.drawable.bg_vod_poster_card
                )
                binding.root.scaleX = if (hasFocus) 1.03f else 1.0f
                binding.root.scaleY = if (hasFocus) 1.03f else 1.0f

                if (hasFocus) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemFocused(getItem(pos))
                    }
                }
            }
        }

        fun bind(item: VodDisplayItem) {
            val cleaned = if (item.tmdb == null) VodTitleCleaner.clean(item.catalogItem.name) else null
            val title = item.tmdb?.title ?: cleaned?.cleanTitle ?: item.catalogItem.name
            binding.vodCardTitle.text = title

            val year = item.tmdb?.releaseYear ?: cleaned?.year ?: ""
            binding.vodCardYear.text = year

            val rating = item.tmdb?.rating ?: 0.0
            if (rating > 0.0) {
                binding.vodCardRating.visibility = View.VISIBLE
                binding.vodCardRating.text = "★ $rating"
            } else {
                binding.vodCardRating.visibility = View.GONE
            }

            val posterUrl = item.tmdb?.posterUrl ?: item.catalogItem.logoUrl
            if (!posterUrl.isNullOrBlank()) {
                binding.vodCardPoster.load(posterUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_vod_default_poster)
                    error(R.drawable.bg_vod_default_poster)
                }
            } else {
                binding.vodCardPoster.setImageResource(R.drawable.bg_vod_default_poster)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<VodDisplayItem>() {
        override fun areItemsTheSame(oldItem: VodDisplayItem, newItem: VodDisplayItem): Boolean {
            return oldItem.catalogItem.sourceKey == newItem.catalogItem.sourceKey
        }

        override fun areContentsTheSame(oldItem: VodDisplayItem, newItem: VodDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}
