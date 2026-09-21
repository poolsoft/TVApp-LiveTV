package com.tvapp.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.R

/**
 * Vertical list of category rows; each row is a titled, horizontally
 * scrollable band of VOD cards navigable with the D-pad.
 */
class VodRowsAdapter(
    private val rows: List<VodRow>,
    private val childAdapterFactory: (Boolean) -> VodHomeAdapter,
) : RecyclerView.Adapter<VodRowsAdapter.RowViewHolder>() {

    data class VodRow(
        val title: String,
        val items: List<com.tvapp.livetv.playback.ContinueWatchingItem>,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vod_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    override fun onViewRecycled(holder: RowViewHolder) {
        holder.rowRecyclerView.adapter = null
        super.onViewRecycled(holder)
    }

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rowRecyclerView: RecyclerView =
            itemView.findViewById(R.id.vod_row_items)
        private val title: TextView = itemView.findViewById(R.id.vod_row_title)

        fun bind(row: VodRow) {
            title.text = row.title
            rowRecyclerView.layoutManager = LinearLayoutManager(
                itemView.context,
                RecyclerView.HORIZONTAL,
                false,
            )
            rowRecyclerView.adapter = childAdapterFactory(false)
            (rowRecyclerView.adapter as VodHomeAdapter).submitList(row.items)
        }
    }
}
