package com.tvapp.livetv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.data.IptvLibraryPage
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
    private lateinit var resumeStore: IptvResumeStore

    private lateinit var continueAdapter: VodHomeAdapter
    private lateinit var gridAdapter: VodHomeAdapter

    private var searchQuery = ""
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0

    // Sequential paging cursor across sources: index into sourceIds plus the
    // per-source offset. The full catalog is never held in memory.
    private var sourceIds: List<Long> = emptyList()
    private var nextSourceIndex = 0
    /** Keyset cursor: the next originalIndex to fetch for [nextSourceIndex]. */
    private var nextSourceFromIndex = 0
    private var exhausted = false
    private var loadingPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVodHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = IptvRepository(this)
        debugLog = CrashReportStore(this)
        history = PlaybackHistoryStore(this)
        resumeStore = IptvResumeStore(this)

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
        binding.vodGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)

        binding.vodSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    searchQuery = s?.toString().orEmpty().trim()
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
        ChannelLogoLoader.configure(this, DeviceCapabilitiesSession.get(this))
        // Returned from playback: resume positions and history may have changed.
        lifecycleScope.launch {
            refreshContinueRow()
        }
    }

    private suspend fun refreshContinueRow() {
        val items = withContext(Dispatchers.IO) {
            ContinueWatchingRepository(
                historyKeys = history::keys,
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
        nextSourceIndex = 0
        nextSourceFromIndex = 0
        exhausted = false
        gridAdapter.submitList(emptyList())
        loadNextGridPage()
    }

    private fun loadNextGridPage() {
        if (loadingPage || exhausted) return
        loadingPage = true
        val generation = loadGeneration
        val query = searchQuery
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val page = withContext(Dispatchers.IO) { fetchNextGridPage(query) }
            if (generation != loadGeneration) return@launch
            loadingPage = false
            if (page.isEmpty()) {
                exhausted = true
            } else {
                val existing = gridAdapter.currentList
                val existingKeys = existing.mapTo(mutableSetOf()) { it.channel.sourceKey }
                gridAdapter.submitList(
                    existing + page.filter { it.channel.sourceKey !in existingKeys },
                )
            }
            updateEmptyState()
        }
    }

    /** Fetches one bounded page, walking across sources when one runs out. */
    private suspend fun fetchNextGridPage(query: String): List<ContinueWatchingItem> {
        val collected = mutableListOf<LiveChannel>()
        while (nextSourceIndex < sourceIds.size && collected.size < PAGE_SIZE) {
            val sourceId = sourceIds[nextSourceIndex]
            val remaining = PAGE_SIZE - collected.size
            val page = runCatching {
                repository.libraryLiveChannelsPageFrom(
                    sourceId = sourceId,
                    category = null,
                    contentType = CONTENT_TYPE_VOD,
                    limit = remaining,
                    fromIndex = nextSourceFromIndex,
                    query = query,
                )
            }.getOrDefault(IptvLibraryPage(emptyList(), null, null))
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
        return collected.map { ContinueWatchingItem(channel = it, resumeEntry = null) }
    }

    private fun updateEmptyState() {
        val empty = gridAdapter.itemCount == 0 &&
            binding.continueWatchingRow.visibility != View.VISIBLE
        binding.vodEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun openItem(item: ContinueWatchingItem) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VOD_SOURCE_KEY, item.channel.sourceKey)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
                        lifecycleScope.launch(Dispatchers.IO) {
                            resumeStore.clear(item.channel.sourceKey)
                        }
                        openItem(item.copy(resumeEntry = null))
                    }
                    1 -> {
                        history.remove(item.channel.sourceKey)
                        lifecycleScope.launch {
                            refreshContinueRow()
                        }
                    }
                }
            }
            .show()
    }

    override fun onBackPressed() {
        // Back first clears search state, then leaves the screen.
        if (binding.vodSearch.text.isNotBlank()) {
            binding.vodSearch.setText("")
            return
        }
        super.onBackPressed()
    }

    companion object {
        const val PAGE_SIZE = 60
        private const val PAGE_THRESHOLD = 12
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val CONTENT_TYPE_VOD = "VOD"
    }
}
