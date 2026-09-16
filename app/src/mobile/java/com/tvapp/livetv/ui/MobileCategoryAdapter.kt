package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ItemMobileCategoryChipBinding

class MobileCategoryAdapter(
    private val onCategorySelected: (String) -> Unit,
) : RecyclerView.Adapter<MobileCategoryAdapter.CategoryViewHolder>() {

    private val categories = mutableListOf<String>()
    var selectedCategory: String = ""
        private set

    fun submitCategories(newCategories: List<String>, initialSelection: String? = null) {
        categories.clear()
        categories.addAll(newCategories)
        selectedCategory = initialSelection ?: categories.firstOrNull().orEmpty()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemMobileCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(
        private val binding: ItemMobileCategoryChipBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: String) {
            binding.chipText.text = category
            val isSelected = category == selectedCategory

            if (isSelected) {
                binding.chipText.setBackgroundResource(R.drawable.bg_mobile_chip_selected)
                binding.chipText.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.white),
                )
            } else {
                binding.chipText.setBackgroundResource(R.drawable.bg_mobile_chip_normal)
                binding.chipText.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.text_primary),
                )
            }

            binding.root.setOnClickListener {
                if (selectedCategory != category) {
                    val previousIndex = categories.indexOf(selectedCategory)
                    selectedCategory = category
                    val newIndex = categories.indexOf(category)
                    if (previousIndex >= 0) notifyItemChanged(previousIndex)
                    if (newIndex >= 0) notifyItemChanged(newIndex)
                    onCategorySelected(category)
                }
            }
        }
    }
}
