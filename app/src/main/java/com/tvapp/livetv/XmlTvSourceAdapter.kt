package com.tvapp.livetv

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.tvapp.livetv.data.XmlTvRepository
import com.tvapp.livetv.data.XmlTvSourceSummary

/**
 * Stable adapter for the XMLTV source list. Inflates [R.layout.item_iptv_source]
 * directly and binds by view id, unlike [ArrayAdapter] which requires the legacy
 * `@android:id/text1` row that the shared layout no longer contains.
 */
class XmlTvSourceAdapter(
    context: Context,
    private var summaries: List<XmlTvSourceSummary>,
) : ArrayAdapter<XmlTvSourceSummary>(context, R.layout.item_iptv_source, summaries) {

    private val layoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = summaries.size

    fun submit(newSummaries: List<XmlTvSourceSummary>) {
        summaries = newSummaries
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: layoutInflater.inflate(R.layout.item_iptv_source, parent, false)
        val summary = summaries[position]
        row.findViewById<TextView>(R.id.source_item_name).text = summary.source.name
        row.findViewById<TextView>(R.id.source_item_location).text = locationLabel(summary)
        row.findViewById<TextView>(R.id.source_item_details).text = detailsLabel(summary)
        return row
    }

    private fun locationLabel(summary: XmlTvSourceSummary): String =
        context.getString(
            if (summary.source.kind == XmlTvRepository.KIND_URL) {
                R.string.xmltv_source_url
            } else {
                R.string.xmltv_source_file
            },
        )

    private fun detailsLabel(summary: XmlTvSourceSummary): String =
        context.getString(
            R.string.xmltv_source_row_status,
            summary.source.name,
            locationLabel(summary),
            summary.channelCount,
            summary.programCount,
            context.getString(
                if (summary.source.enabled) R.string.source_enabled else R.string.source_disabled,
            ),
            summary.source.lastUpdatedAt.takeIf { it > 0L }?.let {
                android.text.format.DateFormat.getDateFormat(context).format(java.util.Date(it))
            } ?: context.getString(R.string.never),
            summary.source.lastError
                ?.let { context.getString(R.string.source_error_short, it) }
                .orEmpty(),
        )
}
