package com.tvapp.livetv.vod

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.data.IptvSourceSummary
import com.tvapp.livetv.databinding.ItemVodCategoryCardBinding
import com.tvapp.livetv.databinding.ItemVodSourceCardBinding

class VodSourceAdapter(
    private val onSourceClick: (IptvSourceSummary) -> Unit,
) : RecyclerView.Adapter<VodSourceAdapter.ViewHolder>() {

    private var items: List<IptvSourceSummary> = emptyList()
    private var activeSourceId: Long? = null

    fun submitList(sources: List<IptvSourceSummary>, activeId: Long?) {
        items = sources
        activeSourceId = activeId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVodSourceCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemVodSourceCardBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onSourceClick(items[pos])
                }
            }
        }

        fun bind(item: IptvSourceSummary) {
            binding.vodSourceName.text = item.source.name
            binding.vodSourceDetails.text = "${item.channelCount} içerik"

            val isActive = item.source.id == activeSourceId
            binding.vodSourceIndicator.setBackgroundResource(
                if (isActive) R.drawable.key_green else R.drawable.key_yellow
            )
        }
    }
}

data class VodCategoryItem(
    val name: String,
    val count: Int,
    val icon: String = "📁",
)

class VodCategoryAdapter(
    private val onCategoryClick: (VodCategoryItem) -> Unit,
) : RecyclerView.Adapter<VodCategoryAdapter.ViewHolder>() {

    private var items: List<VodCategoryItem> = emptyList()
    private var selectedCategory: String? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items.getOrNull(position)?.name.hashCode().toLong()
    }

    fun submitList(categories: List<VodCategoryItem>, selected: String?) {
        items = categories
        selectedCategory = selected
        notifyDataSetChanged()
    }

    fun setSelectedCategory(selected: String?) {
        if (selectedCategory == selected) return
        val prevIndex = items.indexOfFirst { it.name == selectedCategory }
        selectedCategory = selected
        val newIndex = items.indexOfFirst { it.name == selectedCategory }
        if (prevIndex != -1) notifyItemChanged(prevIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVodCategoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemVodCategoryCardBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onCategoryClick(items[pos])
                }
            }
        }

        fun bind(item: VodCategoryItem) {
            binding.vodCatName.text = item.name
            binding.vodCatIcon.text = item.icon
            binding.vodCatCount.text = if (item.count > 0) item.count.toString() else ""

            val isSelected = item.name == selectedCategory
            binding.vodCatName.setTextColor(
                binding.root.context.getColor(if (isSelected) R.color.accent else R.color.text_primary)
            )
        }
    }
}
