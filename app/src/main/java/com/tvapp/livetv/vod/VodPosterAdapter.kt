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
    val resumeProgressPercent: Int = 0,
)

/**
 * Kartın bu bağlantı için kullanılacak ölçüsü (`UI-006`). [posterHeightPx] sıfır ise
 * düzenin kendi sabiti (124dp) korunur; kısa ekranda activity ölçüye göre kısa değer
 * verir ve yıl/puan satırı kapanır.
 */
data class VodCardMetrics(
    val posterHeightPx: Int = 0,
    val metaRowVisible: Boolean = true,
)

class VodPosterAdapter(
    private val onItemClick: (VodDisplayItem) -> Unit,
    private val onItemFocused: (VodDisplayItem) -> Unit,
    private val onItemLongClick: ((VodDisplayItem) -> Boolean)? = null,
) : ListAdapter<VodDisplayItem, VodPosterAdapter.ViewHolder>(DiffCallback) {

    private var metrics = VodCardMetrics()

    /**
     * Ölçü değişimini uygular (ekran/dönüşüm değişikliği). Değer gerçekten
     * değiştiyse yeniden bağlama gerekir: afiş yükseklikleri geri dönüştürülmüş
     * kartlarda da doğru olmalı.
     */
    fun applyMetrics(next: VodCardMetrics) {
        if (metrics == next) return
        metrics = next
        // Boş listede yeniden bağlama gerekmez: ilk bağlama yeni ölçüyü kullanır.
        if (itemCount > 0) notifyDataSetChanged()
    }

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

        /** Düzenin kendi afiş yüksekliği (XML 124dp); kısa ekranda bu değere dönülür. */
        private val standardPosterHeightPx = binding.vodCardPosterFrame.layoutParams.height

        /**
         * Kart ölçüsünü bağlama uygular. Her bağlamada yazılır: geri dönüştürülmüş
         * kartlar önceki (kısa) ölçüyü taşımasın.
         */
        private fun applyMetrics() {
            val targetHeight = metrics.posterHeightPx.takeIf { it > 0 } ?: standardPosterHeightPx
            val params = binding.vodCardPosterFrame.layoutParams
            if (params.height != targetHeight) {
                params.height = targetHeight
                binding.vodCardPosterFrame.layoutParams = params
            }
            binding.vodCardMetaRow.visibility = if (metrics.metaRowVisible) View.VISIBLE else View.GONE
        }

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(pos))
                }
            }

            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && onItemLongClick != null) {
                    onItemLongClick.invoke(getItem(pos))
                } else {
                    false
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
            applyMetrics()
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

            if (item.resumeProgressPercent in 1..99) {
                binding.vodCardResumeProgress.visibility = View.VISIBLE
                binding.vodCardResumeProgress.progress = item.resumeProgressPercent
            } else {
                binding.vodCardResumeProgress.visibility = View.GONE
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
