package com.tvapp.livetv

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.IptvPageAnchor
import com.tvapp.livetv.data.IptvPageDirection
import com.tvapp.livetv.data.local.IptvChannelListProjection
import com.tvapp.livetv.databinding.ActivityIptvChannelSelectionBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IptvChannelSelectionActivity : TvRemoteActivity() {
    private lateinit var binding: ActivityIptvChannelSelectionBinding
    private lateinit var repository: IptvRepository
    private lateinit var preview: IptvPlaybackController
    private lateinit var channelListAdapter: ArrayAdapter<String>
    private var sourceId = -1L
    private var channels: List<IptvChannelListProjection> = emptyList()
    private var categories: List<String> = emptyList()
    private var selectedCategory: String? = null
    private var searchQuery = ""
    private var selectedOnly = false
    private var selectedCount = 0
    private var filteredCount = 0
    private var windowStart = 0
    private var hasPreviousPage = false
    private var hasNextPage = false
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
        updateSelectedFilterUi()
        optimizeMobileColorActions()
        loadInitialData()
    }

    private fun optimizeMobileColorActions() {
        if (!BuildConfig.MOBILE_UI_ENABLED) return
        val minimum = (48 * resources.displayMetrics.density).toInt()
        listOf(
            binding.clearButton,
            binding.selectAllButton,
            binding.categoryButton,
            binding.selectedFilterButton,
            binding.saveButton,
        ).forEach { button ->
            button.minimumHeight = minimum
            button.isFocusable = false
        }
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
            if (selectedOnly && !selected) {
                lifecycleScope.launch {
                    flushSelectionOverrides()
                    reloadFromStart(requestFocus = true)
                }
            }
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
        binding.clearButton.setOnClickListener { applyBulkSelection(selected = false) }
        binding.selectAllButton.setOnClickListener { applyBulkSelection(selected = true) }
        binding.categoryButton.setOnClickListener {
            binding.categoryFilter.requestFocus()
            binding.categoryFilter.performClick()
        }
        binding.selectedFilterButton.setOnClickListener {
            lifecycleScope.launch {
                flushSelectionOverrides()
                selectedOnly = !selectedOnly
                updateSelectedFilterUi()
                reloadFromStart(requestFocus = true)
            }
        }
        binding.saveButton.setOnClickListener { saveSelection() }
    }

    private fun applyBulkSelection(selected: Boolean) {
        if (sourceId < 0) return
        binding.clearButton.isEnabled = false
        binding.selectAllButton.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                flushSelectionOverrides()
                val changed = withContext(Dispatchers.IO) {
                    repository.setFilteredChannelsSelected(
                        sourceId,
                        selectedCategory,
                        searchQuery,
                        selected,
                    )
                }
                selectedCount = withContext(Dispatchers.IO) {
                    repository.selectedChannelCount(sourceId)
                }
                changed
            }
            binding.clearButton.isEnabled = true
            binding.selectAllButton.isEnabled = true
            result.onSuccess { changed ->
                selectionOverrides.clear()
                if (!selected) selectedOnly = false
                updateSelectedFilterUi()
                setResult(RESULT_OK)
                binding.status.text = getString(R.string.iptv_bulk_selection_complete, changed)
                reloadFromStart(requestFocus = true)
            }.onFailure { error ->
                binding.status.text = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun updateSelectedFilterUi() {
        binding.selectedFilterButton.isSelected = selectedOnly
        binding.selectedFilterLabel.setText(
            if (selectedOnly) R.string.show_all_iptv_channels else R.string.selected_iptv_only,
        )
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
        windowStart = 0
        filteredCount = 0
        hasPreviousPage = false
        hasNextPage = false
        loading = false
        focusedPosition = 0
        channelListAdapter.clear()
        channelListAdapter.notifyDataSetChanged()
        loadWindow(IptvPageDirection.FIRST, requestFocus)
    }

    private fun loadWindow(
        direction: IptvPageDirection,
        requestFocus: Boolean = false,
        targetIndex: Int = 0,
    ) {
        if (loading || sourceId < 0) return
        val anchor = when (direction) {
            IptvPageDirection.NEXT -> channels.lastOrNull()?.pageAnchor()
            IptvPageDirection.PREVIOUS -> channels.firstOrNull()?.pageAnchor()
            else -> null
        }
        if ((direction == IptvPageDirection.NEXT || direction == IptvPageDirection.PREVIOUS) &&
            anchor == null
        ) return
        loading = true
        val generation = filterGeneration
        pageJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val total = repository.selectionCount(
                        sourceId,
                        selectedCategory,
                        searchQuery,
                        selectedOnly,
                    )
                    val page = repository.selectionWindow(
                        sourceId,
                        selectedCategory,
                        searchQuery,
                        selectedOnly,
                        PAGE_SIZE,
                        direction,
                        anchor,
                        targetIndex,
                    )
                    total to page
                }
            }
            if (generation != filterGeneration) return@launch
            loading = false
            result.onSuccess { (total, loaded) ->
                filteredCount = total
                val effective = loaded.map { channel ->
                    channel.copy(selected = selectionOverrides[channel.sourceKey] ?: channel.selected)
                }.filter { !selectedOnly || it.selected }
                windowStart = when (direction) {
                    IptvPageDirection.FIRST -> 0
                    IptvPageDirection.NEXT -> windowStart + channels.size
                    IptvPageDirection.PREVIOUS -> (windowStart - effective.size).coerceAtLeast(0)
                    IptvPageDirection.LAST -> (total - effective.size).coerceAtLeast(0)
                    IptvPageDirection.AT_INDEX -> targetIndex.coerceIn(
                        0,
                        (total - effective.size).coerceAtLeast(0),
                    )
                }
                channels = effective
                hasPreviousPage = windowStart > 0
                hasNextPage = windowStart + effective.size < total
                focusedPosition = when (direction) {
                    IptvPageDirection.PREVIOUS, IptvPageDirection.LAST ->
                        effective.lastIndex.coerceAtLeast(0)
                    else -> 0
                }
                renderChannels(requestFocus)
            }.onFailure { error ->
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

    private fun channelLabel(channel: IptvChannelListProjection): String = listOfNotNull(
        channel.displayName,
        channel.groupTitle?.takeIf(String::isNotBlank),
    ).joinToString("  ·  ")

    private fun isSelected(channel: IptvChannelListProjection): Boolean =
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
            val previewChannel = withContext(Dispatchers.IO) {
                repository.channel(channel.sourceKey)
            }
            if (channels.getOrNull(focusedPosition)?.sourceKey != channel.sourceKey) {
                return@launch
            }
            runCatching {
                requireNotNull(previewChannel) { getString(R.string.channel_not_found) }
                preview.play(previewChannel)
            }
                .onFailure { error ->
                    binding.previewState.visibility = View.VISIBLE
                    binding.previewState.text = getString(
                        R.string.iptv_preview_error_detail,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
        }
    }

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
        val rawTarget = binding.channelList.selectedItemPosition + direction * visible.coerceAtLeast(1)
        if (direction < 0 && rawTarget < 0 && hasPreviousPage) {
            loadWindow(IptvPageDirection.PREVIOUS, requestFocus = true)
            return true
        }
        if (direction > 0 && rawTarget > channels.lastIndex && hasNextPage) {
            loadWindow(IptvPageDirection.NEXT, requestFocus = true)
            return true
        }
        val target = rawTarget.coerceIn(0, channels.lastIndex)
        binding.channelList.setSelection(target)
        return true
    }

    override fun dispatchKeyEvent(rawEvent: KeyEvent): Boolean {
        val event = rawEvent.asTvRemoteEvent()
        if (event.action == KeyEvent.ACTION_DOWN && binding.channelList.hasFocus()) {
            digitForKeyCode(event.keyCode)?.let { digit ->
                appendNumberDigit(digit)
                return true
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> if (binding.channelList.selectedItemPosition <= 0) {
                    if (hasPreviousPage) {
                        loadWindow(IptvPageDirection.PREVIOUS, requestFocus = true)
                    } else {
                        loadWindow(IptvPageDirection.LAST, requestFocus = true)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (
                    binding.channelList.selectedItemPosition >= channels.lastIndex
                ) {
                    if (hasNextPage) {
                        loadWindow(IptvPageDirection.NEXT, requestFocus = true)
                    } else {
                        loadWindow(IptvPageDirection.FIRST, requestFocus = true)
                    }
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
        channels = emptyList()
        windowStart = index
        hasPreviousPage = index > 0
        hasNextPage = false
        loading = false
        focusedPosition = 0
        channelListAdapter.clear()
        channelListAdapter.notifyDataSetChanged()
        loadWindow(
            IptvPageDirection.AT_INDEX,
            requestFocus = true,
            targetIndex = index,
        )
    }

    private fun IptvChannelListProjection.pageAnchor() = IptvPageAnchor(originalIndex, sourceKey)

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
        private const val SEARCH_DELAY_MS = 350L
        private const val PREVIEW_DELAY_MS = 900L
        private const val NUMBER_ENTRY_DELAY_MS = 1_000L
        private const val MAX_NUMBER_DIGITS = 4
    }
}
