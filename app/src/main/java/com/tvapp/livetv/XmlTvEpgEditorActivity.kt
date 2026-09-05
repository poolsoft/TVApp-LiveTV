package com.tvapp.livetv

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.XmlTvChannelOption
import com.tvapp.livetv.data.XmlTvMatcher
import com.tvapp.livetv.data.XmlTvRepository
import com.tvapp.livetv.data.local.UserChannelEntity
import com.tvapp.livetv.databinding.ActivityXmltvEpgEditorBinding
import com.tvapp.livetv.databinding.ItemXmltvMatchBinding
import com.tvapp.livetv.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XmlTvEpgEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityXmltvEpgEditorBinding
    private val channelRepository by lazy { ChannelRepository(this) }
    private val xmlTvRepository by lazy { XmlTvRepository(this) }
    private val adapter = MatchAdapter(::selectRow)
    private var channels = emptyList<LiveChannel>()
    private var preferences = emptyMap<String, UserChannelEntity>()
    private var options = emptyList<XmlTvChannelOption>()
    private var selectedChannel: LiveChannel? = null
    private var sourceFilter: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityXmltvEpgEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        loadData()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                page(-1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                page(1)
                return true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> if (selectedChannel != null) {
                cycleSourceFilter()
                return true
            }
            KeyEvent.KEYCODE_PROG_RED -> if (selectedChannel != null) {
                saveOverride(null)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (selectedChannel != null) showChannels() else finish()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Triple(
                    channelRepository.channels(includeHidden = true).getOrThrow(),
                    channelRepository.channelPreferences(),
                    xmlTvRepository.channelCatalog(),
                )
            }
            channels = loaded.first
            preferences = loaded.second
            options = loaded.third
            showChannels()
        }
    }

    private fun showChannels(focusKey: String? = null) {
        selectedChannel = null
        sourceFilter = null
        binding.title.setText(R.string.xmltv_match_editor)
        binding.subtitle.setText(R.string.xmltv_match_editor_summary)
        binding.hint.setText(R.string.xmltv_match_editor_hint)
        val matchIndex = XmlTvMatcher.Index(options)
        val rows = channels.map { channel ->
            val preference = preferences[channel.sourceKey]
            val manual = preference?.epgIdOverride
            val automatic = if (manual == null) matchIndex.resolve(channel) else null
            val match = if (manual != null) {
                options.firstOrNull { it.sourceId == preference.epgSourceIdOverride && it.channelId == manual }
            } else {
                automatic?.option
            }
            val status = when {
                manual != null && match != null -> getString(R.string.xmltv_match_manual, match.channelName)
                manual != null -> getString(R.string.xmltv_match_missing_manual, manual)
                match != null -> {
                    val type = automatic?.type
                    val label = if (type == XmlTvMatcher.MatchType.ID) R.string.xmltv_match_by_id else R.string.xmltv_match_by_name
                    getString(label, match.channelName)
                }
                else -> getString(R.string.xmltv_match_none)
            }
            MatchRow(channel.sourceKey, channel.displayNumber, channel.displayName, channel.epgId.orEmpty(), status)
        }
        adapter.submitList(rows) { focusKey?.let(::focusRow) ?: focusRow(rows.firstOrNull()?.key) }
    }

    private fun showOptions(channel: LiveChannel) {
        selectedChannel = channel
        sourceFilter = null
        renderOptions()
    }

    private fun renderOptions() {
        val channel = selectedChannel ?: return
        val visible = options.filter { sourceFilter == null || it.sourceId == sourceFilter }
        val sourceName = sourceFilter?.let { id -> options.firstOrNull { it.sourceId == id }?.sourceName }
            ?: getString(R.string.all_sources)
        binding.title.text = channel.displayName
        binding.subtitle.text = getString(R.string.xmltv_choose_match, sourceName, visible.size)
        binding.hint.setText(R.string.xmltv_option_hint)
        adapter.submitList(visible.map { option ->
            MatchRow(
                key = "${option.sourceId}:${option.channelId}",
                number = "",
                name = option.channelName,
                detail = getString(R.string.xmltv_option_detail, option.sourceName, option.channelId, option.programCount),
                status = "",
                option = option,
            )
        }) { focusRow(adapter.currentList.firstOrNull()?.key) }
    }

    private fun selectRow(row: MatchRow) {
        val channel = selectedChannel
        if (channel == null) {
            channels.firstOrNull { it.sourceKey == row.key }?.let(::showOptions)
        } else {
            row.option?.let { saveOverride(it) }
        }
    }

    private fun saveOverride(option: XmlTvChannelOption?) {
        val channel = selectedChannel ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                channelRepository.setEpgOverride(channel.sourceKey, option?.channelId, option?.sourceId)
            }
            preferences = preferences.toMutableMap().apply {
                val old = this[channel.sourceKey]
                if (old != null) put(channel.sourceKey, old.copy(epgIdOverride = option?.channelId, epgSourceIdOverride = option?.sourceId))
            }
            showChannels(channel.sourceKey)
        }
    }

    private fun cycleSourceFilter() {
        val ids = listOf<Long?>(null) + options.map(XmlTvChannelOption::sourceId).distinct()
        sourceFilter = ids[(ids.indexOf(sourceFilter) + 1).mod(ids.size)]
        renderOptions()
    }

    private fun page(direction: Int) {
        val manager = binding.list.layoutManager as LinearLayoutManager
        val current = manager.findFirstVisibleItemPosition().coerceAtLeast(0)
        binding.list.smoothScrollToPosition((current + direction * 8).coerceIn(0, (adapter.itemCount - 1).coerceAtLeast(0)))
    }

    private fun focusRow(key: String?) {
        val position = adapter.currentList.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: return
        binding.list.scrollToPosition(position)
        binding.list.post { binding.list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() }
    }

    private data class MatchRow(
        val key: String,
        val number: String,
        val name: String,
        val detail: String,
        val status: String,
        val option: XmlTvChannelOption? = null,
    )

    private class MatchAdapter(private val onClick: (MatchRow) -> Unit) :
        ListAdapter<MatchRow, MatchAdapter.Holder>(object : DiffUtil.ItemCallback<MatchRow>() {
            override fun areItemsTheSame(oldItem: MatchRow, newItem: MatchRow) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: MatchRow, newItem: MatchRow) = oldItem == newItem
        }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemXmltvMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onClick,
        )
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

        class Holder(private val binding: ItemXmltvMatchBinding, private val onClick: (MatchRow) -> Unit) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(row: MatchRow) {
                binding.number.text = row.number
                binding.name.text = row.name
                binding.detail.text = row.detail
                binding.status.text = row.status
                binding.root.setOnClickListener { onClick(row) }
            }
        }
    }
}
