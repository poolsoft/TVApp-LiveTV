package com.tvapp.livetv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated VOD home screen: source filter pills, Continue Watching strip plus
 * a paged 16:9 VOD grid with focus hero. Live TV playback stays untouched;
 * selecting an item hands off to MainActivity through
 * [MainActivity.EXTRA_VOD_SOURCE_KEY].
 */
class VodHomeActivity : TvRemoteActivity() {
    private lateinit var binding: ActivityVodHomeBinding
    private lateinit var repository: IptvRepository
    private lateinit var debugLog: CrashReportStore
    private lateinit var history: PlaybackHistoryStore
    private lateinit var resumeStore: IptvResumeStore

    private lateinit var continueAdapter: VodHomeAdapter
    private lateinit var gridAdapter: VodHomeAdapter
    private lateinit var pillsAdapter: SourcePillsAdapter
    private lateinit var genrePillsAdapter: SourcePillsAdapter

    private var searchQuery = ""
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0

    // Source filter: null = all sources. Genre/category filter: null = all VOD.
    private var selectedSourceId: Long? = null
    private var selectedCategory: String? = null

    // Paged VOD query cursor: one flat, alpha-ordered page stream.
    private var nextOffset = 0
    private var exhausted = false
    private var loadingPage = false
    private var genreAutoScrollRunnable: Runnable? = null

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
            onFocusChanged = ::showHero,
            showResumeDetails = true,
        )
        gridAdapter = VodHomeAdapter(
            onItemClick = ::openItem,
            onItemLongClick = ::showItemActions,
            onFocusChanged = ::showHero,
            showResumeDetails = false,
        )
        pillsAdapter = SourcePillsAdapter { sourceId, _ ->
            val changed = selectedSourceId != sourceId
            if (changed) {
                selectedSourceId = sourceId
                pillsAdapter.selectedSourceId = sourceId
                pillsAdapter.notifyDataSetChanged()
                reloadGrid()
            }
        }
        genrePillsAdapter = SourcePillsAdapter { _, category ->
            val changed = selectedCategory != category
            if (changed) {
                selectedCategory = category
                genrePillsAdapter.selectedCategory = category
                genrePillsAdapter.notifyDataSetChanged()
                reloadGrid()
            }
        }
        binding.continueWatchingRow.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.continueWatchingRow.adapter = continueAdapter
        binding.vodGrid.adapter = gridAdapter
        binding.vodGrid.layoutManager = GridLayoutManager(this, GRID_SPAN)
        binding.vodSourcePills.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.vodSourcePills.adapter = pillsAdapter
        binding.vodGenrePills.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.vodGenrePills.adapter = genrePillsAdapter
        // Auto-scroll the genre bar toward its end over time when nothing is focused there,
        // so users discover the available categories without opening anything.
        genreAutoScrollRunnable = object : Runnable {
            override fun run() {
                val manager = binding.vodGenrePills.layoutManager as? LinearLayoutManager ?: return
                val target = genrePillsAdapter.itemCount - 1
                if (target <= 0) return
                if (binding.vodGenrePills.focusedChild == null) {
                    val visible = manager.findLastCompletelyVisibleItemPosition()
                    if (visible < target) {
                        binding.vodGenrePills.smoothScrollToPosition(visible + 1)
                    } else {
                        binding.vodGenrePills.smoothScrollToPosition(0)
                    }
                }
                binding.vodGenrePills.postDelayed(this, GENRE_SCROLL_INTERVAL_MS)
            }
        }

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
                val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
                if (!exhausted && !loadingPage &&
                    manager.findLastVisibleItemPosition() >= gridAdapter.itemCount - PAGE_THRESHOLD
                ) {
                    loadNextGridPage()
                }
            }
        })

        lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) {
                repository.sources().map { it.source.id to it.source.name }
            }
            val categories = withContext(Dispatchers.IO) {
                repository.vodCategories().map { it to classifyCategory(it) }
            }
            pillsAdapter.mode = SourcePillsAdapter.PillMode.SOURCE
            pillsAdapter.submitPills(sources, emptyList())
            genrePillsAdapter.mode = SourcePillsAdapter.PillMode.GENRE
            genrePillsAdapter.submitPills(emptyList(), categories)
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
        genreAutoScrollRunnable?.let { binding.vodGenrePills.postDelayed(it, GENRE_SCROLL_INTERVAL_MS) }
    }

    override fun onStop() {
        super.onStop()
        genreAutoScrollRunnable?.let { binding.vodGenrePills.removeCallbacks(it) }
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

    private fun showHero(item: ContinueWatchingItem?) {
        if (item == null) {
            binding.vodHero.visibility = View.GONE
            return
        }
        binding.vodHero.visibility = View.VISIBLE
        binding.vodHeroTitle.text = item.channel.displayName
        val entry = item.resumeEntry
        binding.vodHeroSubtitle.text = if (entry != null && entry.durationMillis > 0L) {
            val left = VodHomeAdapter.progressText(entry.positionMillis, entry.durationMillis)
            getString(R.string.vod_hero_resume_left, left)
        } else {
            item.channel.displayName
        }
    }

    private fun reloadGrid() {
        loadGeneration++
        nextOffset = 0
        exhausted = false
        showHero(null)
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

    /** Fetches one bounded, alpha-ordered VOD page for the current filters. */
    private suspend fun fetchNextGridPage(query: String): List<ContinueWatchingItem> {
        // Built-in genre pills filter across categories by name; raw pills match exactly.
        val rawCategory = selectedCategory?.takeUnless { it == GENRE_MOVIES || it == GENRE_SERIES }
        var page = runCatching {
            repository.vodPage(
                sourceId = selectedSourceId ?: 0L,
                category = rawCategory,
                limit = PAGE_SIZE,
                offset = nextOffset,
                query = query,
            )
        }.getOrDefault(emptyList())
        if (selectedCategory == GENRE_MOVIES || selectedCategory == GENRE_SERIES) {
            val wanted = if (selectedCategory == GENRE_MOVIES) GENRE_MOVIES else GENRE_SERIES
            page = page.filter { classifyCategory(it.groupTitle ?: "") == wanted }
        }
        nextOffset += PAGE_SIZE
        return page.map { ContinueWatchingItem(channel = it, resumeEntry = null) }
    }

    /** Groups raw group titles into the built-in Movie/Series pills. */
    private fun classifyCategory(title: String): String? {
        val value = title.lowercase()
        val seriesMarkers = listOf("dizi", "series", "sezon", "season", "episode", "bölüm")
        val movieMarkers = listOf("film", "movie", "cinema", "sinema")
        return when {
            seriesMarkers.any(value::contains) -> GENRE_SERIES
            movieMarkers.any(value::contains) -> GENRE_MOVIES
            else -> null
        }
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

    /**
     * Two filter pill rows: source pills (All + one per source) and genre pills
     * (All + Movies + Series + up to N raw VOD categories).
     */
    class SourcePillsAdapter(
        private val onSelect: (Long?, String?) -> Unit,
    ) : RecyclerView.Adapter<SourcePillsAdapter.PillViewHolder>() {

        sealed class Pill {
            abstract val id: String

            data class Source(val sourceId: Long?) : Pill() {
                override val id: String get() = "s$sourceId"
            }

            data class Genre(val category: String?) : Pill() {
                override val id: String get() = "g$category"
            }
        }

        enum class PillMode { SOURCE, GENRE }

        private val pills = mutableListOf<Pill>()
        private val sourceNames = mutableMapOf<Long, String>()
        private val genreLabels = mutableMapOf<String?, String>()
        var mode: PillMode = PillMode.SOURCE
        var selectedSourceId: Long? = null
        var selectedCategory: String? = null

        fun submitPills(
            sources: List<Pair<Long, String>>,
            categories: List<Pair<String, String?>>,
        ) {
            sourceNames.clear()
            sources.forEach { (id, name) -> sourceNames[id] = name }
            genreLabels.clear()
            pills.clear()
            if (mode == PillMode.SOURCE) {
                pills.add(Pill.Source(null))
                sources.forEach { (id, _) -> pills.add(Pill.Source(id)) }
            } else {
                pills.add(Pill.Genre(null))
                pills.add(Pill.Genre(GENRE_MOVIES))
                pills.add(Pill.Genre(GENRE_SERIES))
                // Raw categories that were not already mapped to Movies/Series pills.
                categories.forEach { (title, mapped) ->
                    if (mapped == null && genreLabels.size < MAX_RAW_CATEGORIES) {
                        genreLabels[title] = title
                        pills.add(Pill.Genre(title))
                    }
                }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PillViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_vod_source_pill, parent, false)
            return PillViewHolder(view)
        }

        override fun onBindViewHolder(holder: PillViewHolder, position: Int) {
            when (val pill = pills[position]) {
                is Pill.Source -> {
                    val label = pill.sourceId?.let { sourceNames[it] }
                        ?: holder.itemView.context.getString(R.string.vod_source_all)
                    holder.bind(pill, label, selectedSourceId == pill.sourceId)
                }
                is Pill.Genre -> {
                    val label = pill.category?.let { genreLabels[it] }
                        ?: holder.itemView.context.getString(R.string.vod_source_all)
                    holder.bind(pill, label, selectedCategory == pill.category)
                }
            }
        }

        override fun getItemCount(): Int = pills.size

        inner class PillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val label: TextView = itemView.findViewById(R.id.vod_pill_label)
            private var pill: Pill? = null

            init {
                itemView.setOnClickListener {
                    when (val p = pill) {
                        is Pill.Source -> onSelect(p.sourceId, selectedCategory)
                        is Pill.Genre -> onSelect(selectedSourceId, p.category)
                        null -> Unit
                    }
                }
                itemView.setOnFocusChangeListener { view, hasFocus ->
                    view.translationZ = if (hasFocus) 8f else 0f
                    view.scaleX = if (hasFocus) 1.06f else 1.0f
                    view.scaleY = if (hasFocus) 1.06f else 1.0f
                    if (hasFocus) {
                        val active = when (val p = pill) {
                            is Pill.Source -> p.sourceId == selectedSourceId
                            is Pill.Genre -> p.category == selectedCategory
                            null -> false
                        }
                        itemView.setBackgroundResource(
                            if (active) R.drawable.bg_vod_pill_active else R.drawable.bg_vod_pill_selected,
                        )
                    } else {
                        val active = when (val p = pill) {
                            is Pill.Source -> p.sourceId == selectedSourceId
                            is Pill.Genre -> p.category == selectedCategory
                            null -> false
                        }
                        itemView.setBackgroundResource(
                            if (active) R.drawable.bg_vod_pill_active else R.drawable.bg_vod_pill,
                        )
                    }
                }
            }

            fun bind(item: SourcePillsAdapter.Pill, text: String, active: Boolean) {
                pill = item
                label.text = text
                itemView.isFocusable = true
                itemView.isClickable = true
                itemView.setBackgroundResource(
                    if (active) R.drawable.bg_vod_pill_active else R.drawable.bg_vod_pill,
                )
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 60
        private const val PAGE_THRESHOLD = 12
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val GRID_SPAN = 5
        private const val GENRE_SCROLL_INTERVAL_MS = 3000L
        private const val GENRE_MOVIES = "__movies__"
        private const val GENRE_SERIES = "__series__"
        private const val MAX_RAW_CATEGORIES = 12
    }
}
