package com.tvapp.livetv

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.AbsListView
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
    private lateinit var channelListAdapter: ArrayAdapter<String>
    private var sourceId = -1L
    private var channels: List<IptvChannelEntity> = emptyList()
    private var categories: List<String> = emptyList()
    private var selectedCategory: String? = null
    private var searchQuery = ""
    private var selectedOnly = false
    private var selectedCount = 0
    private var filteredCount = 0
    private var offset = 0
    private var exhausted = true
    private var loading = false
    private var filterGeneration = 0
    private val selectionOverrides = linkedMapOf<String, Boolean>()
    private var previewJob: Job? = null
    private var pageJob: Job? = null
    private var searchJob: Job? = null
    private var numberJob: Job? = null
    private var numberInput = ""
    private var focusedPosition = 0
    private var initializingCategories = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIptvChannelSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = IptvRepository(this)
        preview = IptvPlaybackController(this, binding.previewPlayer)
        configurePreview()
        sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, -1L)
        binding.title.text = intent.getStringExtra(EXTRA_SOURCE_NAME)
            ?: getString(R.string.select_iptv_channels)
        configureList()
        configureFilters()
        configureActions()
        loadInitialData()
    }

    private fun configurePreview() {
        preview.onPlaybackReady = { binding.previewState.visibility = View.GONE }
        preview.onPlaybackError = { error ->
            binding.previewState.visibility = View.VISIBLE
            binding.previewState.text = getString(
                R.string.iptv_preview_error_detail,
                error.message ?: error.errorCodeName,
            )
        }
    }

    private fun configureList() {
        channelListAdapter = ArrayAdapter(
            this,
            R.layout.item_iptv_channel_selection,
            mutableListOf(),
        )
        binding.channelList.adapter = channelListAdapter
        binding.channelList.choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
        binding.channelList.setOnItemClickListener { _, _, position, _ ->
            val channel = channels.getOrNull(position) ?: return@setOnItemClickListener
            val selected = !isSelected(channel)
            selectionOverrides[channel.sourceKey] = selected
            if (selected) selectedCount++ else selectedCount--
            channels = channels.toMutableList().also {
                it[position] = channel.copy(selected = selected)
            }
            binding.channelList.setItemChecked(position, selected)
            updateStatus()
            if (selectedOnly && !selected) reloadFromStart(requestFocus = true)
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
        binding.channelList.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit

            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int,
            ) {
                if (
                    totalItemCount > 0 &&
                    firstVisibleItem + visibleItemCount >= totalItemCount - PREFETCH_DISTANCE
                ) loadNextPage()
            }
        })
    }

    private fun configureFilters() {
        binding.categoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                if (initializingCategories) return
                selectedCategory = categories.getOrNull(position - 1)
                reloadFromStart(requestFocus = true)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(SEARCH_DELAY_MS)
                    searchQuery = text?.toString()?.trim().orEmpty()
                    reloadFromStart(requestFocus = false)
                }
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })
    }

    private fun configureActions() {
        binding.clearButton.setOnClickListener {
            val selectedVisible = channels.filter(::isSelected)
            selectedVisible.forEach { selectionOverrides[it.sourceKey] = false }
            selectedCount = (selectedCount - selectedVisible.size).coerceAtLeast(0)
            channels = channels.map { it.copy(selected = false) }
            channels.indices.forEach { binding.channelList.setItemChecked(it, false) }
            updateStatus()
            if (selectedOnly) reloadFromStart(requestFocus = true)
        }
        binding.categoryButton.setOnClickListener {
            binding.categoryFilter.requestFocus()
            binding.categoryFilter.performClick()
        }
        binding.selectedFilterButton.setOnClickListener {
            lifecycleScope.launch {
                flushSelectionOverrides()
                selectedOnly = !selectedOnly
                binding.selectedFilterButton.isSelected = selectedOnly
                reloadFromStart(requestFocus = true)
            }
        }
        binding.saveButton.setOnClickListener { saveSelection() }
    }

    private fun loadInitialData() {
        binding.status.setText(R.string.iptv_channels_loading)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.sourceCategories(sourceId) to
                        repository.selectedChannelCount(sourceId)
                }
            }
            result.onSuccess { (loadedCategories, loadedSelectedCount) ->
                categories = loadedCategories
                selectedCount = loadedSelectedCount
                initializingCategories = true
                binding.categoryFilter.adapter = ArrayAdapter(
                    this@IptvChannelSelectionActivity,
                    R.layout.item_iptv_category,
                    listOf(getString(R.string.all_iptv_categories)) + categories,
                ).apply { setDropDownViewResource(R.layout.item_iptv_category) }
                binding.categoryFilter.setSelection(0)
                initializingCategories = false
                reloadFromStart(requestFocus = true)
            }.onFailure { error ->
                binding.status.text = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun reloadFromStart(requestFocus: Boolean) {
        filterGeneration++
        pageJob?.cancel()
        previewJob?.cancel()
        preview.stop()
        channels = emptyList()
        offset = 0
        filteredCount = 0
        exhausted = false
        loading = false
        focusedPosition = 0
        channelListAdapter.clear()
        channelListAdapter.notifyDataSetChanged()
        loadNextPage(requestFocus)
    }

    private fun loadNextPage(requestFocus: Boolean = false) {
        if (loading || exhausted || sourceId < 0) return
        loading = true
        val generation = filterGeneration
        val pageOffset = offset
        pageJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val total = repository.selectionCount(
                        sourceId,
                        selectedCategory,
                        searchQuery,
                        selectedOnly,
                    )
                    val page = repository.selectionPage(
                        sourceId,
                        selectedCategory,
                        searchQuery,
                        selectedOnly,
                        PAGE_SIZE,
                        pageOffset,
                    )
                    total to page
                }
            }
            if (generation != filterGeneration) return@launch
            loading = false
            result.onSuccess { (total, loaded) ->
                filteredCount = total
                offset += loaded.size
                exhausted = offset >= total || loaded.size < PAGE_SIZE
                val effective = loaded.map { channel ->
                    channel.copy(selected = selectionOverrides[channel.sourceKey] ?: channel.selected)
                }.filter { !selectedOnly || it.selected }
                channels = channels + effective
                renderChannels(requestFocus && pageOffset == 0)
            }.onFailure { error ->
                exhausted = true
                binding.status.text = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun renderChannels(requestFocus: Boolean) {
        val selectedKey = channels.getOrNull(focusedPosition)?.sourceKey
        channelListAdapter.setNotifyOnChange(false)
        channelListAdapter.clear()
        channelListAdapter.addAll(channels.map(::channelLabel))
        channelListAdapter.notifyDataSetChanged()
        channels.forEachIndexed { index, channel ->
            binding.channelList.setItemChecked(index, isSelected(channel))
        }
        focusedPosition = channels.indexOfFirst { it.sourceKey == selectedKey }
            .takeIf { it >= 0 }
            ?: focusedPosition.coerceIn(0, channels.lastIndex.coerceAtLeast(0))
        binding.channelList.setSelection(focusedPosition)
        if (requestFocus && channels.isNotEmpty()) binding.channelList.requestFocus()
        updateStatus()
    }

    private fun channelLabel(channel: IptvChannelEntity): String = listOfNotNull(
        channel.displayName,
        channel.groupTitle?.takeIf(String::isNotBlank),
    ).joinToString("  ·  ")

    private fun isSelected(channel: IptvChannelEntity): Boolean =
        selectionOverrides[channel.sourceKey] ?: channel.selected

    private fun updateStatus() {
        binding.status.text = getString(
            R.string.iptv_selection_page_count,
            selectedCount.coerceAtLeast(0),
            channels.size,
            filteredCount,
        )
    }

    private fun saveSelection() {
        binding.saveButton.isEnabled = false
        lifecycleScope.launch {
            runCatching { flushSelectionOverrides() }
                .onSuccess {
                    setResult(RESULT_OK)
                    finish()
                }
                .onFailure { error ->
                    binding.saveButton.isEnabled = true
                    binding.status.text = error.message ?: error.javaClass.simpleName
                }
        }
    }

    private suspend fun flushSelectionOverrides() {
        if (selectionOverrides.isEmpty()) return
        val snapshot = selectionOverrides.toMap()
        withContext(Dispatchers.IO) {
            repository.setChannelsSelected(
                snapshot.filterValues { it }.keys.toList(),
                selected = true,
            )
            repository.setChannelsSelected(
                snapshot.filterValues { !it }.keys.toList(),
                selected = false,
            )
        }
        snapshot.keys.forEach(selectionOverrides::remove)
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
        epgId = tvgId?.takeIf(String::isNotBlank)
            ?: tvgName?.takeIf(String::isNotBlank),
        userAgent = userAgent,
        referrer = referrer,
        source = LiveChannel.Source.IPTV,
    )

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_PROG_RED -> binding.clearButton.performClick().let { true }
        KeyEvent.KEYCODE_PROG_GREEN -> saveSelection().let { true }
        KeyEvent.KEYCODE_PROG_YELLOW -> binding.categoryButton.performClick().let { true }
        KeyEvent.KEYCODE_PROG_BLUE -> binding.selectedFilterButton.performClick().let { true }
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> pageList(-1)
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> pageList(1)
        else -> super.onKeyDown(keyCode, event)
    }

    private fun pageList(direction: Int): Boolean {
        if (channels.isEmpty()) return true
        val visible = binding.channelList.lastVisiblePosition - binding.channelList.firstVisiblePosition
        val target = (binding.channelList.selectedItemPosition + direction * visible.coerceAtLeast(1))
            .coerceIn(0, channels.lastIndex)
        binding.channelList.setSelection(target)
        if (direction > 0 && target >= channels.lastIndex - PREFETCH_DISTANCE) loadNextPage()
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && binding.channelList.hasFocus()) {
            digitForKeyCode(event.keyCode)?.let { digit ->
                appendNumberDigit(digit)
                return true
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> if (binding.channelList.selectedItemPosition <= 0) {
                    jumpToFilteredIndex(filteredCount - 1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (
                    exhausted && binding.channelList.selectedItemPosition >= channels.lastIndex
                ) {
                    jumpToFilteredIndex(0)
                    return true
                }
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

    private fun appendNumberDigit(digit: Int) {
        if (numberInput.length >= MAX_NUMBER_DIGITS) numberInput = ""
        numberInput += digit
        binding.status.text = getString(R.string.iptv_selection_jump, numberInput)
        numberJob?.cancel()
        numberJob = lifecycleScope.launch {
            delay(NUMBER_ENTRY_DELAY_MS)
            val index = numberInput.toIntOrNull()?.minus(1)
            numberInput = ""
            if (index != null && index in 0 until filteredCount) {
                jumpToFilteredIndex(index)
            } else {
                updateStatus()
            }
        }
    }

    private fun jumpToFilteredIndex(index: Int) {
        if (index !in 0 until filteredCount) return
        filterGeneration++
        pageJob?.cancel()
        previewJob?.cancel()
        preview.stop()
        val pageStart = (index / PAGE_SIZE) * PAGE_SIZE
        channels = emptyList()
        offset = pageStart
        exhausted = false
        loading = false
        focusedPosition = index - pageStart
        channelListAdapter.clear()
        channelListAdapter.notifyDataSetChanged()
        loadNextPage(requestFocus = true)
    }

    private fun digitForKeyCode(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    override fun onDestroy() {
        previewJob?.cancel()
        pageJob?.cancel()
        searchJob?.cancel()
        numberJob?.cancel()
        preview.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_SOURCE_NAME = "source_name"
        private const val PAGE_SIZE = 200
        private const val PREFETCH_DISTANCE = 20
        private const val SEARCH_DELAY_MS = 350L
        private const val PREVIEW_DELAY_MS = 900L
        private const val NUMBER_ENTRY_DELAY_MS = 1_000L
        private const val MAX_NUMBER_DIGITS = 4
    }
}
