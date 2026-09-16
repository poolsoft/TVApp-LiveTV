package com.tvapp.livetv.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ItemMobileChannelBinding
import com.tvapp.livetv.image.ChannelLogoLoader
import com.tvapp.livetv.model.LiveChannel

class MobileChannelAdapter(
    private val onChannelClicked: (LiveChannel) -> Unit,
    private val onFavoriteToggled: (LiveChannel) -> Unit,
) : ListAdapter<LiveChannel, MobileChannelAdapter.ChannelViewHolder>(ChannelDiffCallback) {

    var currentPlayingKey: String? = null
        set(value) {
            val oldKey = field
            field = value
            if (oldKey != value) {
                currentList.forEachIndexed { index, channel ->
                    if (channel.sourceKey == oldKey || channel.sourceKey == value) {
                        notifyItemChanged(index)
                    }
                }
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemMobileChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChannelViewHolder(
        private val binding: ItemMobileChannelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: LiveChannel) {
            val isPlaying = channel.sourceKey == currentPlayingKey
            val context = binding.root.context

            binding.channelNumber.text = channel.displayNumber
            binding.channelName.text = channel.displayName

            val subtitle = channel.groupTitle?.takeIf { it.isNotBlank() }
                ?: channel.iptvContentType?.takeIf { it.isNotBlank() }
                ?: ""
            binding.channelEpgNow.text = subtitle
            binding.channelEpgNow.visibility = if (subtitle.isNotBlank()) View.VISIBLE else View.GONE

            // Kalite Rozeti (4K, FHD, HD)
            val nameUpper = channel.displayName.uppercase()
            val badgeText = when {
                nameUpper.contains("4K") || nameUpper.contains("UHD") -> "4K"
                nameUpper.contains("FHD") || nameUpper.contains("1080") -> "FHD"
                nameUpper.contains("HD") || nameUpper.contains("720") -> "HD"
                !channel.videoFormat.isNullOrBlank() -> channel.videoFormat
                else -> null
            }

            if (badgeText != null) {
                binding.channelBadge.text = badgeText
                binding.channelBadge.visibility = View.VISIBLE
            } else {
                binding.channelBadge.visibility = View.GONE
            }

            // Oynatılıyor Göstergesi
            binding.channelPlayingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE

            // Arkaplan Vurgusu
            if (isPlaying) {
                binding.channelItemRoot.setBackgroundColor(Color.parseColor("#1C2433"))
            } else {
                binding.channelItemRoot.setBackgroundResource(R.drawable.bg_channel)
            }

            // Favori Simgesi
            if (channel.favorite) {
                binding.channelFavorite.setImageResource(R.drawable.ic_star)
                binding.channelFavorite.setColorFilter(ContextCompat.getColor(context, R.color.accent))
            } else {
                binding.channelFavorite.setImageResource(R.drawable.ic_star)
                binding.channelFavorite.setColorFilter(Color.parseColor("#4B5563"))
            }

            binding.channelFavorite.setOnClickListener {
                onFavoriteToggled(channel)
            }

            // Logo Yükleyici
            ChannelLogoLoader.load(
                binding.channelLogo,
                channel.logoUrl,
                R.drawable.ic_tv,
            )

            // Tıklama
            binding.root.setOnClickListener {
                onChannelClicked(channel)
            }
        }
    }

    object ChannelDiffCallback : DiffUtil.ItemCallback<LiveChannel>() {
        override fun areItemsTheSame(oldItem: LiveChannel, newItem: LiveChannel): Boolean =
            oldItem.sourceKey == newItem.sourceKey

        override fun areContentsTheSame(oldItem: LiveChannel, newItem: LiveChannel): Boolean =
            oldItem == newItem
    }
}
