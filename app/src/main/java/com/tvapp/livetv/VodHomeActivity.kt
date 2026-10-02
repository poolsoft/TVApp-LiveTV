package com.tvapp.livetv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.image.ChannelLogoLoader
import com.tvapp.livetv.databinding.ActivityVodHomeBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.ContinueWatchingItem
import com.tvapp.livetv.playback.ContinueWatchingRepository
import com.tvapp.livetv.playback.IptvResumeStore
import com.tvapp.livetv.playback.PlaybackHistoryStore
import com.tvapp.livetv.platform.DeviceCapabilitiesSession
import com.tvapp.livetv.ui.VodHomeAdapter
import com.tvapp.livetv.TvRemoteActivity
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated VOD home screen: Continue Watching strip plus a paged VOD grid.
 * Live TV playback stays untouched; selecting an item hands off to
 * MainActivity through [MainActivity.EXTRA_VOD_SOURCE_KEY].
 */
class VodHomeActivity : TvRemoteActivity() {
    private lateinit var binding: ActivityVodHomeBinding
    private lateinit var repository: IptvRepository
    private lateinit var debugLog: CrashReportStore
    private lateinit var history: PlaybackHistoryStore
    private lateinit var legacyHistory: PlaybackHistoryStore
    private lateinit var resumeStore: IptvResumeStore

    private lateinit var continueAdapter: VodHomeAdapter
    private lateinit var gridAdapter: VodHomeAdapter

    private var searchQuery = ""
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0

    // Sequential paging cursor across sources: index into sourceIds plus the
    // per-source keyset. Each database request is bounded to one page.
    private var sourceIds: List<Long> = emptyList()
    private var nextSourceIndex = 0
    /** Keyset cursor: the next originalIndex to fetch for [nextSourceIndex]. */
    private var nextSourceFromIndex = 0
    private var exhausted = false
    private var loadingPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        binding = ActivityVodHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = IptvRepository(this)
        debugLog = CrashReportStore(this)
        history = PlaybackHistoryStore(this, "vod-playback-history")
        legacyHistory = PlaybackHistoryStore(this)
        resumeStore = IptvResumeStore(this)
        binding.vodLiveAction.setOnClickListener { openLiveTv() }
        binding.vodResumeAction.setOnClickListener { resumeLastVod() }
        binding.root.post { binding.vodLiveAction.requestFocus() }

        continueAdapter = VodHomeAdapter(
            onItemClick = ::openItem,
            onItemLongClick = ::showItemActions,
            onFocusChanged = {},
        )
        gridAdapter = VodHomeAdapter(
            onItemClick = ::openItem,
            onItemLongClick = ::showItemActions,
            onFocusChanged = {},
        )
        binding.continueWatchingRow.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.continueWatchingRow.adapter = continueAdapter
        binding.vodGrid.adapter = gridAdapter
        binding.vodGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(
            this, (resources.configuration.screenWidthDp / 290).coerceIn(1, 4),
        )
        binding.vodSearch.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                binding.vodGrid.requestFocus()
                true
            } else false
        }
        binding.vodEmpty.setOnClickListener { reloadGrid() }

        binding.vodSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty().trim()
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    searchQuery = query
                    reloadGrid()
                }
            }
        })

        binding.vodGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
                    ?: return
                if (!exhausted && !loadingPage &&
                    manager.findLastVisibleItemPosition() >= gridAdapter.itemCount - PAGE_THRESHOLD
                ) {
                    loadNextGridPage()
                }
            }
        })

        lifecycleScope.launch {
            sourceIds = withContext(Dispatchers.IO) { repository.sources().map { it.source.id } }
            refreshContinueRow()
            reloadGrid()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.vodResumeAction.visibility = if (lastVodKey() != null) View.VISIBLE else View.GONE
        ChannelLogoLoader.configure(this, DeviceCapabilitiesSession.get(this))
        // Returned from playback: resume positions and history may have changed.
        lifecycleScope.launch {
            refreshContinueRow()
        }
    }

    private suspend fun refreshContinueRow() {
        val items = withContext(Dispatchers.IO) {
            ContinueWatchingRepository(
                historyKeys = { (history.keys() + legacyHistory.keys()).distinct() },
                resumeEntry = { key -> resumeStore.entries().firstOrNull { it.sourceKey == key } },
                resolveChannel = { key -> runCatching { repository.channel(key) }.getOrNull() },
            ).items()
        }
        if (items.isEmpty()) {
            binding.continueWatchingRow.visibility = View.GONE
            binding.continueWatchingLabel.visibility = View.GONE
        } else {
            binding.continueWatchingRow.visibility = View.VISIBLE
            binding.continueWatchingLabel.visibility = View.VISIBLE
            continueAdapter.submitList(items)
        }
    }

    private fun reloadGrid() {
        loadGeneration++
        loadJob?.cancel()
        loadingPage = false
        nextSourceIndex = 0
        nextSourceFromIndex = 0
        exhausted = false
        val generation = loadGeneration
        gridAdapter.submitList(emptyList()) {
            if (generation == loadGeneration) loadNextGridPage()
        }
    }

    private fun loadNextGridPage() {
        if (loadingPage || exhausted) return
        loadingPage = true
        val generation = loadGeneration
        val query = searchQuery
        val sources = sourceIds.toList()
        val sourceIndex = nextSourceIndex
        val fromIndex = nextSourceFromIndex
        binding.vodEmpty.visibility = View.GONE
        binding.vodLoading.visibility = View.VISIBLE
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    fetchNextGridPage(query, sources, sourceIndex, fromIndex)
                }
                if (generation != loadGeneration) return@launch
                nextSourceIndex = result.sourceIndex
                nextSourceFromIndex = result.fromIndex
                exhausted = result.sourceIndex >= sources.size
                val existing = gridAdapter.currentList
                val existingKeys = existing.mapTo(mutableSetOf()) { it.channel.sourceKey }
                gridAdapter.submitList(
                    existing + result.items.filter { it.channel.sourceKey !in existingKeys },
                ) {
                    if (generation == loadGeneration) {
                        loadingPage = false
                        binding.vodLoading.visibility = View.GONE
                        updateEmptyState()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == loadGeneration) {
                    loadingPage = false
                    binding.vodLoading.visibility = View.GONE
                    binding.vodEmpty.setText(R.string.vod_load_failed)
                    binding.vodEmpty.isFocusable = true
                    binding.vodEmpty.isClickable = true
                    binding.vodEmpty.visibility = View.VISIBLE
                    debugLog.recordDebug("VOD_PAGE_FAILED | ${error.javaClass.simpleName}")
                }
            }
        }
    }

    /** Fetches one bounded page, walking across sources when one runs out. */
    private suspend fun fetchNextGridPage(
        query: String, sources: List<Long>, sourceIndex: Int, fromIndex: Int,
    ): GridPage {
        var nextSourceIndex = sourceIndex
        var nextSourceFromIndex = fromIndex
        val collected = mutableListOf<LiveChannel>()
        while (nextSourceIndex < sources.size && collected.size < PAGE_SIZE) {
            currentCoroutineContext().ensureActive()
            val sourceId = sources[nextSourceIndex]
            val remaining = PAGE_SIZE - collected.size
            val page = repository.libraryLiveChannelsPageFrom(
                sourceId = sourceId,
                category = null,
                contentType = CONTENT_TYPE_VOD,
                limit = remaining,
                fromIndex = nextSourceFromIndex,
                query = query,
            )
            if (page.channels.isEmpty()) {
                nextSourceIndex++
                nextSourceFromIndex = 0
                continue
            }
            collected.addAll(page.channels)
            if (page.channels.size < remaining) {
                nextSourceIndex++
                nextSourceFromIndex = 0
            } else {
                nextSourceFromIndex = (page.lastAnchor?.originalIndex ?: 0) + 1
            }
        }
        return GridPage(collected.map { ContinueWatchingItem(channel = it, resumeEntry = null) },
            nextSourceIndex, nextSourceFromIndex)
    }

    private data class GridPage(
        val items: List<ContinueWatchingItem>, val sourceIndex: Int, val fromIndex: Int,
    )

    private fun updateEmptyState() {
        binding.vodEmpty.setText(R.string.vod_empty)
        binding.vodEmpty.isFocusable = false
        binding.vodEmpty.isClickable = false
        val empty = gridAdapter.itemCount == 0 &&
            binding.continueWatchingRow.visibility != View.VISIBLE
        binding.vodEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun openItem(item: ContinueWatchingItem) = openItem(item, startOver = false)

    private fun openItem(item: ContinueWatchingItem, startOver: Boolean) {
        hideKeyboard()
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VOD_SOURCE_KEY, item.channel.sourceKey)
            putExtra(MainActivity.EXTRA_VOD_START_OVER, startOver)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun showItemActions(item: ContinueWatchingItem) {
        val actions = arrayOf(
            getString(R.string.vod_resume_from_start),
            getString(R.string.vod_remove_from_continue),
        )
        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(item.channel.displayName)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        openItem(item, startOver = true)
                    }
                    1 -> {
                        history.remove(item.channel.sourceKey)
                        legacyHistory.remove(item.channel.sourceKey)
                        lifecycleScope.launch {
                            refreshContinueRow()
                        }
                    }
                }
            }
            .show()
    }

    override fun onBackPressed() {
        hideKeyboard()
        if (binding.vodSearch.text.isNotBlank()) {
            binding.vodSearch.setText("")
            return
        }
        binding.vodLiveAction.requestFocus()
    }

    private fun lastVodKey(): String? = getSharedPreferences(MainActivity.MODE_PREFS, MODE_PRIVATE)
        .getString(MainActivity.LAST_VOD_KEY, null)

    private fun resumeLastVod() {
        val key = lastVodKey() ?: return
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_VOD_SOURCE_KEY, key)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun openLiveTv() {
        hideKeyboard()
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_LIVE_MODE, true)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.vodSearch.windowToken, 0)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = tvRemoteKeyCode(event.keyCode)
        if (key == KeyEvent.KEYCODE_PROG_RED || key == KeyEvent.KEYCODE_PROG_GREEN) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (key == KeyEvent.KEYCODE_PROG_RED) openLiveTv() else resumeLastVod()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val PAGE_SIZE = 60
        private const val PAGE_THRESHOLD = 12
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val CONTENT_TYPE_VOD = "VOD"
    }
}
