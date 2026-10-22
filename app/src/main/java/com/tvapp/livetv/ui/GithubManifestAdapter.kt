package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R
import com.tvapp.livetv.data.GithubManifestItem
import com.tvapp.livetv.image.ChannelLogoLoader

class GithubManifestAdapter(
    private val items: List<GithubManifestItem>,
    private val onSelectionChanged: (selectedCount: Int) -> Unit = {},
) : RecyclerView.Adapter<GithubManifestAdapter.ViewHolder>() {

    private val selectedIndices = (0 until items.size).toMutableSet()

    init {
        onSelectionChanged(selectedIndices.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_github_manifest, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.description.text = item.description.orEmpty()
        holder.description.visibility = if (item.description.isNullOrBlank()) View.GONE else View.VISIBLE

        if (!item.sourceFormat.isNullOrBlank()) {
            holder.tag.text = item.sourceFormat
            holder.tag.visibility = View.VISIBLE
        } else {
            holder.tag.visibility = View.GONE
        }

        ChannelLogoLoader.load(holder.poster, item.poster, R.drawable.ic_link)

        holder.checkbox.isChecked = selectedIndices.contains(position)

        holder.itemView.setOnClickListener {
            toggle(position)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun toggle(position: Int) {
        if (selectedIndices.contains(position)) {
            selectedIndices.remove(position)
        } else {
            selectedIndices.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged(selectedIndices.size)
    }

    fun selectAll() {
        selectedIndices.clear()
        for (i in items.indices) selectedIndices.add(i)
        notifyDataSetChanged()
        onSelectionChanged(selectedIndices.size)
    }

    fun deselectAll() {
        selectedIndices.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<GithubManifestItem> {
        return items.filterIndexed { index, _ -> selectedIndices.contains(index) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val poster: ImageView = itemView.findViewById(R.id.manifest_poster)
        val name: TextView = itemView.findViewById(R.id.manifest_name)
        val tag: TextView = itemView.findViewById(R.id.manifest_tag)
        val description: TextView = itemView.findViewById(R.id.manifest_description)
        val checkbox: CheckBox = itemView.findViewById(R.id.manifest_checkbox)
    }
}
