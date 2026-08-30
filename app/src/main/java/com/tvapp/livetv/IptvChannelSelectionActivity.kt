package com.tvapp.livetv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.local.IptvChannelEntity
import com.tvapp.livetv.databinding.ActivityIptvChannelSelectionBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IptvChannelSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIptvChannelSelectionBinding
    private lateinit var repository: IptvRepository
    private lateinit var preview: IptvPlaybackController
    private var sourceId: Long = -1L
    private var allChannels: List<IptvChannelEntity> = emptyList()
    private var channels: List<IptvChannelEntity> = emptyList()
    private val selectedKeys = mutableSetOf<String>()
    private var categories: List<String> = emptyList()
    private var selectedCategory: String? = null
    private var previewJob: Job? = null
    private var focusedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIptvChannelSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = IptvRepository(this)
        preview = IptvPlaybackController(this, binding.previewPlayer)
        preview.onPlaybackReady = { binding.previewState.visibility = View.GONE }
        preview.onPlaybackError = { error ->
            binding.previewState.visibility = View.VISIBLE
            binding.previewState.text = getString(
                R.string.iptv_preview_error_detail,
                error.message ?: error.errorCodeName,
            )
        }
        sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, -1L)
        binding.title.text = intent.getStringExtra(EXTRA_SOURCE_NAME)
            ?: getString(R.string.select_iptv_channels)
        binding.channelList.choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
        binding.channelList.setOnItemClickListener { _, _, position, _ ->
            channels.getOrNull(position)?.let { channel ->
                if (binding.channelList.isItemChecked(position)) {
                    selectedKeys.add(channel.sourceKey)
                } else {
                    selectedKeys.remove(channel.sourceKey)
                }
            }
            updateSelectionCount()
        }
        binding.channelList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                focusedPosition = position
                schedulePreview(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.clearButton.setOnClickListener {
            channels.forEach { selectedKeys.remove(it.sourceKey) }
            channels.indices.forEach { binding.channelList.setItemChecked(it, false) }
            updateSelectionCount()
        }
        binding.categoryButton.setOnClickListener {
            binding.categoryFilter.requestFocus()
            binding.categoryFilter.performClick()
        }
        binding.categoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                selectedCategory = categories.getOrNull(position - 1)
                if (allChannels.isNotEmpty()) applyCategoryFilter()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.saveButton.setOnClickListener { saveSelection() }
        loadChannels()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> {
                binding.clearButton.performClick()
                true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> {
                saveSelection()
                true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                binding.categoryButton.performClick()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && binding.channelList.hasFocus()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    binding.clearButton.requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    binding.saveButton.requestFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        previewJob?.cancel()
        preview.release()
        super.onDestroy()
    }

    private fun loadChannels() {
        binding.status.setText(R.string.iptv_channels_loading)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.sourceChannels(sourceId) }
            }
            result.onSuccess { loaded ->
                allChannels = loaded
                selectedKeys.clear()
                selectedKeys.addAll(loaded.filter { it.selected }.map { it.sourceKey })
                categories = loaded.asSequence()
                    .mapNotNull { it.groupTitle?.trim()?.takeIf(String::isNotBlank) }
                    .distinct()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
                    .toList()
                binding.categoryFilter.adapter = ArrayAdapter(
                    this@IptvChannelSelectionActivity,
                    R.layout.item_iptv_category,
                    listOf(getString(R.string.all_iptv_categories)) + categories,
                ).apply {
                    setDropDownViewResource(R.layout.item_iptv_category)
                }
                applyCategoryFilter(requestListFocus = true)
            }.onFailure { error ->
                binding.status.text = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun saveSelection() {
        binding.saveButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.setSelectedChannels(sourceId, selectedKeys) }
            }
            result.onSuccess {
                setResult(RESULT_OK)
                finish()
            }.onFailure { error ->
                binding.saveButton.isEnabled = true
                binding.status.text = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun updateSelectionCount() {
        binding.status.text = getString(
            R.string.iptv_filtered_selection_count,
            selectedKeys.size,
            allChannels.size,
            channels.size,
        )
    }

    private fun applyCategoryFilter(requestListFocus: Boolean = false) {
        val focusedKey = channels.getOrNull(focusedPosition)?.sourceKey
        channels = allChannels.filter { channel ->
            selectedCategory == null || channel.groupTitle?.trim() == selectedCategory
        }
        binding.channelList.adapter = ArrayAdapter(
            this,
            R.layout.item_iptv_channel_selection,
            channels.map { channel ->
                listOfNotNull(
                    channel.displayName,
                    channel.groupTitle?.takeIf(String::isNotBlank),
                ).joinToString("  ·  ")
            },
        )
        channels.forEachIndexed { index, channel ->
            binding.channelList.setItemChecked(index, channel.sourceKey in selectedKeys)
        }
        focusedPosition = channels.indexOfFirst { it.sourceKey == focusedKey }
            .takeIf { it >= 0 }
            ?: channels.indexOfFirst { it.sourceKey in selectedKeys }.takeIf { it >= 0 }
            ?: 0
        binding.channelList.setSelection(focusedPosition)
        if (requestListFocus) binding.channelList.requestFocus()
        updateSelectionCount()
    }

    private fun schedulePreview(position: Int) {
        val channel = channels.getOrNull(position) ?: return
        binding.previewName.text = channel.displayName
        binding.previewGroup.text = channel.groupTitle.orEmpty()
        binding.previewState.visibility = View.VISIBLE
        binding.previewState.setText(R.string.iptv_preview_loading)
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            delay(PREVIEW_DELAY_MS)
            runCatching { preview.play(channel.toLiveChannel()) }
                .onFailure { error ->
                    binding.previewState.visibility = View.VISIBLE
                    binding.previewState.text = getString(
                        R.string.iptv_preview_error_detail,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
        }
    }

    private fun IptvChannelEntity.toLiveChannel() = LiveChannel(
        id = sourceKey.hashCode().toLong(),
        sourceKey = sourceKey,
        inputId = "iptv:$sourceId",
        displayNumber = (originalIndex + 1).toString(),
        displayName = displayName,
        uri = streamUrl,
        logoUrl = logoUrl,
        groupTitle = groupTitle,
        epgId = tvgId,
        userAgent = userAgent,
        referrer = referrer,
        source = LiveChannel.Source.IPTV,
    )

    companion object {
        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_SOURCE_NAME = "source_name"
        private const val PREVIEW_DELAY_MS = 900L
    }
}
