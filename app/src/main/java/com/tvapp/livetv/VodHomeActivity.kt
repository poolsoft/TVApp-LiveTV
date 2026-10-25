package com.tvapp.livetv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.IptvSourceSummary
import com.tvapp.livetv.data.VodFilter
import com.tvapp.livetv.data.VodRepository
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.VodCatalogItem
import com.tvapp.livetv.data.local.VodMetadataEntity
import com.tvapp.livetv.data.local.VodTmdbEntity
import com.tvapp.livetv.databinding.ActivityVodHomeBinding
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.playback.IptvResumeStore
import com.tvapp.livetv.playback.PlaybackHistoryStore
import com.tvapp.livetv.vod.TmdbApiClient
import com.tvapp.livetv.vod.VodCategoryAdapter
import com.tvapp.livetv.vod.VodCategoryItem
import com.tvapp.livetv.vod.VodDisplayItem
import com.tvapp.livetv.vod.VodPosterAdapter
import com.tvapp.livetv.vod.VodTitleCleaner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modern Grid Discovery VOD Home Screen.
 * Left panel: Source/playlist combo picker, Return to Live TV, Sort order, Categories.
 * Right panel: Continue Watching horizontal rail, 5-column paginated poster grid.
 */
class VodHomeActivity : TvRemoteActivity() {
    private lateinit var binding: ActivityVodHomeBinding
    private lateinit var repository: VodRepository
    private lateinit var iptv: IptvRepository
    private lateinit var debugLog: CrashReportStore
    private lateinit var history: PlaybackHistoryStore
    private lateinit var resumeStore: IptvResumeStore
    private lateinit var database: TVAppDatabase

    private lateinit var continueAdapter: VodPosterAdapter
    private lateinit var gridAdapter: VodPosterAdapter
    private lateinit var gridCategoryAdapter: VodCategoryAdapter

    private var gridCurrentPage = 0
    private var gridIsLoading = false
    private var gridHasMore = true
    private var gridOrder = "SOURCE"
    private var gridSection = "ALL"
    private var gridCategory: String? = null
    private var gridQuery: String = ""
    private var gridJob: Job? = null
    private val gridItems = mutableListOf<VodDisplayItem>()

    private val preferences by lazy { getSharedPreferences("vod-library", MODE_PRIVATE) }
    private var sources: List<IptvSourceSummary> = emptyList()
    private var sourceId = -1L

    private var lastPlayedSourceKey: String? = null
    private var lastFocusedGridPosition: Int = 0

    private var tmdbJob: Job? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 10000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        binding = ActivityVodHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = VodRepository(this)
        iptv = IptvRepository(this)
        debugLog = CrashReportStore(this)
        history = PlaybackHistoryStore(this, "vod-playback-history")
        resumeStore = IptvResumeStore(this)
        database = TVAppDatabase.getInstance(this)
        val intentSourceId = intent.getLongExtra("source", -1L)
        sourceId = if (intentSourceId > 0L) intentSourceId else preferences.getLong("source", -1L)
        gridCategory = preferences.getString("category", null)
        gridOrder = preferences.getString("order", "SOURCE") ?: "SOURCE"

        setupRecyclerViews()
        setupClickListeners()
        updateClock()
        loadSourcesAndInitialData()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockRunnable)
        loadContinueWatching(shouldFocus = true)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun updateClock() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.vodClock.text = timeFormat.format(Date())
    }

    private fun setupRecyclerViews() {
        // Devam Et (İzlemeye Devam Et) Rayı
        continueAdapter = VodPosterAdapter(
            onItemClick = { displayItem -> onVodItemClicked(displayItem) },
            onItemFocused = { /* Odak animasyonu adapter içinde */ },
            onItemLongClick = { displayItem ->
                showContinueContextMenu(displayItem)
                true
            },
        )
        binding.vodRecyclerContinue.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.vodRecyclerContinue.adapter = continueAdapter

        // Grid Afiş Adaptörü (5 Sütun)
        gridAdapter = VodPosterAdapter(
            onItemClick = { displayItem -> onVodItemClicked(displayItem) },
            onItemFocused = { item ->
                val pos = gridAdapter.currentList.indexOfFirst { it.catalogItem.sourceKey == item.catalogItem.sourceKey }
                if (pos != -1) lastFocusedGridPosition = pos
            },
            onItemLongClick = { displayItem ->
                showPosterContextMenu(displayItem)
                true
            },
        )
        val gridLayoutManager = GridLayoutManager(this, 5)
        binding.vodRecyclerGrid.layoutManager = gridLayoutManager
        binding.vodRecyclerGrid.adapter = gridAdapter

        // Sonsuz kaydırma (Pagination)
        binding.vodRecyclerGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    val visibleItemCount = gridLayoutManager.childCount
                    val totalItemCount = gridLayoutManager.itemCount
                    val firstVisibleItemPosition = gridLayoutManager.findFirstVisibleItemPosition()

                    if (!gridIsLoading && gridHasMore) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 8) {
                            loadNextGridPage()
                        }
                    }
                }
            }
        })

        // Sol Panel - Kategoriler
        gridCategoryAdapter = VodCategoryAdapter { catItem ->
            val allCatsLabel = getString(R.string.vod_all_categories)
            gridCategory = if (catItem.name == allCatsLabel || gridCategory == catItem.name) {
                null
            } else {
                catItem.name
            }
            preferences.edit().putString("category", gridCategory).apply()
            gridCategoryAdapter.setSelectedCategory(gridCategory ?: allCatsLabel)
            lastFocusedGridPosition = 0
            reloadGridData()
        }
        binding.vodGridRecyclerCategories.layoutManager = LinearLayoutManager(this)
        binding.vodGridRecyclerCategories.adapter = gridCategoryAdapter
    }

    private fun setupClickListeners() {
        // Liste Seçim Combo'su
        binding.vodSourceCombo.setOnClickListener {
            showSourceSelectionDialog()
        }

        // Canlı TV'ye Dön Butonu
        binding.vodBtnLiveTv.setOnClickListener {
            openLiveTv()
        }

        // Arama Kutusu
        binding.vodSearchBox.setOnClickListener {
            showSearchDialog()
        }

        // Sıralama Butonları
        binding.vodSortDefault.setOnClickListener {
            setGridSort("SOURCE", getString(R.string.vod_sort_default))
            it.requestFocus()
        }
        binding.vodSortNameAsc.setOnClickListener {
            setGridSort("NAME", getString(R.string.vod_sort_name_asc))
            it.requestFocus()
        }
        binding.vodSortNameDesc.setOnClickListener {
            setGridSort("NAME_DESC", getString(R.string.vod_sort_name_desc))
            it.requestFocus()
        }
        binding.vodSortRecent.setOnClickListener {
            setGridSort("ORDINAL_DESC", getString(R.string.vod_sort_recent))
            it.requestFocus()
        }

        updateSortButtonsUi()
    }

    private fun loadSourcesAndInitialData() {
        lifecycleScope.launch {
            try {
                sources = withContext(Dispatchers.IO) { iptv.sources() }.filter { it.source.enabled }
                if (sourceId > 0L && sources.none { it.source.id == sourceId }) {
                    sourceId = -1L
                    preferences.edit().putLong("source", -1L).apply()
                }
                updateSourceComboText()
                updateSortButtonsUi()
                binding.vodGridSelectedOrderLabel.text = when (gridOrder) {
                    "NAME" -> getString(R.string.vod_sort_name_asc)
                    "NAME_DESC" -> getString(R.string.vod_sort_name_desc)
                    "ORDINAL_DESC" -> getString(R.string.vod_sort_recent)
                    else -> getString(R.string.vod_sort_default)
                }
                ensureSourceReady(sourceId) {
                    loadGridCategories()
                    reloadGridData()
                    loadContinueWatching()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    debugLog.recordDebug("VOD Sources load failed: ${e.message}")
                }
            }
        }
    }

    private fun ensureSourceReady(targetSourceId: Long, onReady: () -> Unit) {
        if (targetSourceId <= 0L) {
            onReady()
            return
        }
        val targetSourceSummary = sources.find { it.source.id == targetSourceId }
        val isJsonVod = targetSourceSummary?.source?.kind == IptvRepository.KIND_JSON_VOD
        if (!isJsonVod) {
            onReady()
            return
        }
        lifecycleScope.launch {
            val totalInDb = withContext(Dispatchers.IO) {
                database.vodDao().countTotalBySourceId(targetSourceId)
            }
            if (totalInDb > 0) {
                onReady()
                return@launch
            }

            val progressDialog = AlertDialog.Builder(this@VodHomeActivity, R.style.Theme_TVApp_Dialog)
                .setTitle(R.string.vod_archive_label)
                .setMessage(getString(R.string.vod_archive_preparing))
                .setCancelable(false)
                .create()
            progressDialog.show()

            val success = withContext(Dispatchers.IO) {
                try {
                    val location = targetSourceSummary.source.location
                    val name = targetSourceSummary.source.name
                    val user = targetSourceSummary.source.username
                    iptv.importJsonVodUrl(
                        location = location,
                        name = name,
                        username = user
                    )
                    true
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        debugLog.recordDebug("JSON VOD fetch on-demand failed: ${e.message}")
                    }
                    false
                }
            }
            progressDialog.dismiss()
            if (success) {
                sources = withContext(Dispatchers.IO) { iptv.sources() }.filter { it.source.enabled }
                updateSourceComboText()
            }
            onReady()
        }
    }

    private fun updateSourceComboText() {
        val active = sources.find { it.source.id == sourceId }
        val name = active?.source?.name ?: getString(R.string.vod_all_sources)
        binding.vodSourceComboText.text = name
        binding.vodBottomActiveSource.text = name
    }

    private fun showSourceSelectionDialog() {
        if (sources.isEmpty()) return
        val options = mutableListOf<String>()
        val ids = mutableListOf<Long>()

        options.add(getString(R.string.vod_all_sources))
        ids.add(-1L)

        sources.forEach { summary ->
            options.add(summary.source.name)
            ids.add(summary.source.id)
        }

        val currentIndex = ids.indexOf(sourceId).coerceAtLeast(0)

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.vod_select_source)
            .setSingleChoiceItems(options.toTypedArray(), currentIndex) { dialog, which ->
                val selectedId = ids[which]
                sourceId = selectedId
                preferences.edit().putLong("source", sourceId).apply()
                gridCategory = null
                preferences.edit().putString("category", null).apply()
                lastFocusedGridPosition = 0
                gridItems.clear()
                gridAdapter.submitList(emptyList())
                updateSourceComboText()
                dialog.dismiss()
                ensureSourceReady(sourceId) {
                    loadGridCategories()
                    reloadGridData()
                    loadContinueWatching(shouldFocus = false)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun loadContinueWatching(shouldFocus: Boolean = true) {
        lifecycleScope.launch {
            try {
                val resumeEntries = withContext(Dispatchers.IO) { resumeStore.entries() }
                if (resumeEntries.isEmpty()) {
                    binding.vodContinueContainer.visibility = View.GONE
                    if (shouldFocus) {
                        focusGridOrSavedPosition()
                    }
                    return@launch
                }
                val keys = resumeEntries.map { it.sourceKey }
                val items = withContext(Dispatchers.IO) {
                    repository.resumeItems(keys)
                }
                if (items.isEmpty()) {
                    binding.vodContinueContainer.visibility = View.GONE
                    if (shouldFocus) {
                        focusGridOrSavedPosition()
                    }
                    return@launch
                }
                val allKeys = items.map { it.sourceKey }
                val cachedTmdb = withContext(Dispatchers.IO) {
                    database.vodTmdbDao().getBySourceKeys(allKeys).associateBy { it.sourceKey }
                }
                val displayItems = items.map { VodDisplayItem(it, cachedTmdb[it.sourceKey]) }
                binding.vodContinueContainer.visibility = View.VISIBLE
                continueAdapter.submitList(displayItems) {
                    if (isFinishing || isDestroyed) return@submitList
                    if (shouldFocus) {
                        val targetKey = lastPlayedSourceKey
                        val targetIndex = if (!targetKey.isNullOrEmpty()) {
                            displayItems.indexOfFirst { it.catalogItem.sourceKey == targetKey }
                        } else -1

                        if (targetIndex != -1) {
                            binding.vodRecyclerContinue.post {
                                binding.vodRecyclerContinue.scrollToPosition(targetIndex)
                                binding.vodRecyclerContinue.postDelayed({
                                    val vh = binding.vodRecyclerContinue.findViewHolderForAdapterPosition(targetIndex)
                                    vh?.itemView?.requestFocus() ?: binding.vodRecyclerContinue.requestFocus()
                                }, 60L)
                            }
                        } else {
                            focusGridOrSavedPosition()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    binding.vodContinueContainer.visibility = View.GONE
                    if (shouldFocus) {
                        focusGridOrSavedPosition()
                    }
                }
            }
        }
    }

    private fun focusGridOrSavedPosition() {
        binding.vodRecyclerGrid.post {
            if (gridAdapter.itemCount > 0) {
                val targetPos = lastFocusedGridPosition.coerceIn(0, gridAdapter.itemCount - 1)
                binding.vodRecyclerGrid.scrollToPosition(targetPos)
                binding.vodRecyclerGrid.postDelayed({
                    val vh = binding.vodRecyclerGrid.findViewHolderForAdapterPosition(targetPos)
                    vh?.itemView?.requestFocus() ?: binding.vodRecyclerGrid.requestFocus()
                }, 60L)
            } else {
                binding.vodRecyclerGrid.requestFocus()
            }
        }
    }

    private fun setGridSort(order: String, label: String) {
        if (gridOrder == order) return
        gridOrder = order
        preferences.edit().putString("order", gridOrder).apply()
        binding.vodGridSelectedOrderLabel.text = label
        updateSortButtonsUi()
        reloadGridData()
    }

    private fun updateSortButtonsUi() {
        binding.vodSortDefault.isSelected = (gridOrder == "SOURCE")
        binding.vodSortNameAsc.isSelected = (gridOrder == "NAME")
        binding.vodSortNameDesc.isSelected = (gridOrder == "NAME_DESC")
        binding.vodSortRecent.isSelected = (gridOrder == "ORDINAL_DESC")

        val accentColor = getColor(R.color.accent)
        val defaultColor = getColor(R.color.text_primary)
        binding.vodSortDefault.setTextColor(if (gridOrder == "SOURCE") accentColor else defaultColor)
        binding.vodSortNameAsc.setTextColor(if (gridOrder == "NAME") accentColor else defaultColor)
        binding.vodSortNameDesc.setTextColor(if (gridOrder == "NAME_DESC") accentColor else defaultColor)
        binding.vodSortRecent.setTextColor(if (gridOrder == "ORDINAL_DESC") accentColor else defaultColor)
    }

    private fun reloadGridData() {
        gridJob?.cancel()
        gridCurrentPage = 0
        gridHasMore = true
        gridItems.clear()
        gridAdapter.submitList(emptyList())
        loadNextGridPage()
    }

    private fun loadNextGridPage() {
        if (gridIsLoading || !gridHasMore) return
        gridIsLoading = true

        gridJob = lifecycleScope.launch {
            try {
                val currentFilter = VodFilter(
                    section = gridSection,
                    category = gridCategory,
                    query = gridQuery,
                    order = gridOrder,
                )
                val pageData = withContext(Dispatchers.IO) {
                    repository.page(sourceId, currentFilter, keys = emptyList(), page = gridCurrentPage)
                }

                if (pageData.isEmpty()) {
                    gridHasMore = false
                } else {
                    val keys = pageData.map { it.sourceKey }
                    val cachedTmdb = withContext(Dispatchers.IO) {
                        database.vodTmdbDao().getBySourceKeys(keys).associateBy { it.sourceKey }
                    }
                    val displayItems = pageData.map { VodDisplayItem(it, cachedTmdb[it.sourceKey]) }
                    gridItems.addAll(displayItems)
                    gridAdapter.submitList(gridItems.toList())
                    gridCurrentPage++
                    enrichMissingTmdb(pageData, cachedTmdb)
                }

                binding.vodGridEmptyState.visibility = if (gridItems.isEmpty()) View.VISIBLE else View.GONE

                val count = withContext(Dispatchers.IO) {
                    repository.count(sourceId, currentFilter, emptyList())
                }
                binding.vodGridItemCount.text = if (count > 0) "($count)" else ""
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    debugLog.recordDebug("VOD Grid page load error: ${e.message}")
                }
            } finally {
                gridIsLoading = false
            }
        }
    }

    private fun loadGridCategories() {
        lifecycleScope.launch {
            val catStrings = withContext(Dispatchers.IO) {
                repository.categories(sourceId, gridSection)
            }
            val list = mutableListOf<VodCategoryItem>()
            val allCatsLabel = getString(R.string.vod_all_categories)
            list.add(VodCategoryItem(name = allCatsLabel, count = 0, icon = "📁"))
            catStrings.filterNotNull().forEach {
                list.add(VodCategoryItem(name = it, count = 0, icon = "📁"))
            }
            val activeCat = gridCategory ?: allCatsLabel
            gridCategoryAdapter.submitList(list, activeCat)
            val activeIndex = list.indexOfFirst { it.name == activeCat }
            if (activeIndex > 0) {
                binding.vodGridRecyclerCategories.scrollToPosition(activeIndex)
            }
        }
    }

    private fun enrichMissingTmdb(items: List<VodCatalogItem>, cached: Map<String, VodTmdbEntity>) {
        tmdbJob?.cancel()
        tmdbJob = lifecycleScope.launch(Dispatchers.IO) {
            val missing = items.filter { it.logoUrl.isNullOrBlank() && it.sourceKey !in cached }.distinctBy { it.name }
            for (item in missing.take(15)) {
                try {
                    val cleaned = VodTitleCleaner.clean(item.name)
                    val isSeries = item.kind == "SERIES"
                    var tmdbResult = if (isSeries) {
                        TmdbApiClient.searchTvShow(cleaned.cleanTitle, cleaned.year)
                    } else {
                        TmdbApiClient.searchMovie(cleaned.cleanTitle, cleaned.year)
                    }

                    if (tmdbResult == null && !cleaned.year.isNullOrBlank()) {
                        tmdbResult = if (isSeries) {
                            TmdbApiClient.searchTvShow(cleaned.cleanTitle, null)
                        } else {
                            TmdbApiClient.searchMovie(cleaned.cleanTitle, null)
                        }
                    }

                    if (tmdbResult != null) {
                        val entity = VodTmdbEntity(
                            sourceKey = item.sourceKey,
                            tmdbId = tmdbResult.id,
                            title = tmdbResult.title,
                            originalTitle = tmdbResult.originalTitle,
                            overview = tmdbResult.overview,
                            posterUrl = tmdbResult.posterUrl,
                            backdropUrl = tmdbResult.backdropUrl,
                            rating = tmdbResult.rating,
                            releaseYear = tmdbResult.releaseYear,
                            genres = tmdbResult.genres.joinToString(", "),
                        )
                        database.vodTmdbDao().upsert(entity)

                        withContext(Dispatchers.Main) {
                            gridAdapter.updateTmdbItem(item.sourceKey, entity)
                            continueAdapter.updateTmdbItem(item.sourceKey, entity)
                        }
                    }
                } catch (_: Exception) {
                    // Devam et
                }
            }
        }
    }

    private fun onVodItemClicked(displayItem: VodDisplayItem) {
        val item = displayItem.catalogItem
        if (item.kind == "SERIES") {
            showEpisodesDialog(item)
        } else {
            playItem(item)
        }
    }

    private fun showEpisodesDialog(seriesItem: VodCatalogItem) {
        lifecycleScope.launch {
            val episodes = withContext(Dispatchers.IO) {
                repository.episodesForSeries(seriesItem.sourceKey)
            }
            if (episodes.isNotEmpty()) {
                val titles = episodes.map { ep ->
                    val s = ep.seasonNumber ?: 1
                    val e = ep.episodeNumber ?: 1
                    "${s}. Sezon ${e}. Bölüm - ${ep.name}"
                }.toTypedArray()

                AlertDialog.Builder(this@VodHomeActivity, R.style.Theme_TVApp_Dialog)
                    .setTitle(seriesItem.name)
                    .setItems(titles) { _, which ->
                        val selectedEp = episodes[which]
                        playEpisode(selectedEp)
                    }
                    .setNegativeButton(R.string.close, null)
                    .show()
                return@launch
            }

            // Check if episodes are stored as JSON in streamUrl
            val meta = withContext(Dispatchers.IO) {
                database.vodDao().metadata(seriesItem.sourceKey)
            }
            val rawEpisodes = meta?.streamUrl
            if (!rawEpisodes.isNullOrBlank() && rawEpisodes.startsWith("[")) {
                try {
                    val jsonArray = org.json.JSONArray(rawEpisodes)
                    if (jsonArray.length() > 0) {
                        val parsedEpisodes = mutableListOf<Pair<String, String>>()
                        for (i in 0 until jsonArray.length()) {
                            val epObj = jsonArray.optJSONObject(i)
                            if (epObj != null) {
                                val season = epObj.optInt("season", epObj.optInt("season_number", 1)).coerceAtLeast(1)
                                val epNumber = epObj.optInt("episode", epObj.optInt("episode_number", i + 1)).coerceAtLeast(1)
                                val rawTitle = epObj.optString("title").trim().ifEmpty {
                                    epObj.optString("name").trim()
                                }
                                val epTitle = if (rawTitle.isNotEmpty() &&
                                    !rawTitle.equals("$epNumber. Bölüm", true) &&
                                    !rawTitle.equals("Bölüm $epNumber", true)
                                ) {
                                    "$season. Sezon $epNumber. Bölüm - $rawTitle"
                                } else {
                                    "$season. Sezon $epNumber. Bölüm"
                                }
                                val epUrl = epObj.optString("videoUrl").trim().ifEmpty {
                                    epObj.optString("url").trim()
                                }
                                if (epUrl.isNotEmpty()) {
                                    parsedEpisodes.add(Pair(epTitle, epUrl))
                                }
                            }
                        }

                        if (parsedEpisodes.isNotEmpty()) {
                            val titles = parsedEpisodes.map { it.first }.toTypedArray()
                            AlertDialog.Builder(this@VodHomeActivity, R.style.Theme_TVApp_Dialog)
                                .setTitle(seriesItem.name)
                                .setItems(titles) { _, which ->
                                    val (epTitle, epUrl) = parsedEpisodes[which]
                                    playDynamicEpisode(seriesItem, which, epTitle, epUrl)
                                }
                                .setNegativeButton(R.string.close, null)
                                .show()
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    debugLog.recordDebug("Parse dynamic JSON episodes failed: ${e.message}")
                }
            }

            Toast.makeText(this@VodHomeActivity, R.string.vod_no_episodes_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun playDynamicEpisode(seriesItem: VodCatalogItem, epIndex: Int, epTitle: String, epUrl: String) {
        val epKey = "${seriesItem.sourceKey}:ep:${epIndex}_${epUrl.hashCode()}"
        lastPlayedSourceKey = epKey
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VOD_SOURCE_KEY, epKey)
            putExtra(MainActivity.EXTRA_VOD_STREAM_URL, epUrl)
            putExtra(MainActivity.EXTRA_VOD_TITLE, seriesItem.name)
            putExtra(MainActivity.EXTRA_VOD_EPISODE_TITLE, epTitle)
            putExtra(MainActivity.EXTRA_VOD_LOGO_URL, seriesItem.logoUrl)
            putExtra(MainActivity.EXTRA_VOD_START_OVER, false)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun playEpisode(episode: VodMetadataEntity) {
        lastPlayedSourceKey = episode.sourceKey
        val season = episode.seasonNumber ?: 1
        val epNum = episode.episodeNumber
        val subTitle = if (epNum != null) {
            if (episode.name.isNotBlank() && !episode.name.equals("$epNum. Bölüm", true)) {
                "$season. Sezon $epNum. Bölüm - ${episode.name}"
            } else {
                "$season. Sezon $epNum. Bölüm"
            }
        } else episode.name

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VOD_SOURCE_KEY, episode.sourceKey)
            putExtra(MainActivity.EXTRA_VOD_STREAM_URL, episode.streamUrl)
            putExtra(MainActivity.EXTRA_VOD_TITLE, episode.name)
            putExtra(MainActivity.EXTRA_VOD_EPISODE_TITLE, subTitle)
            putExtra(MainActivity.EXTRA_VOD_LOGO_URL, episode.logoUrl)
            putExtra(MainActivity.EXTRA_VOD_START_OVER, false)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun playItem(item: VodCatalogItem) {
        lastPlayedSourceKey = item.sourceKey
        lifecycleScope.launch {
            val (url, logo) = withContext(Dispatchers.IO) {
                var meta = database.vodDao().metadata(item.sourceKey)
                if (meta == null && item.sourceKey.contains(":ep:")) {
                    val parentKey = item.sourceKey.substringBefore(":ep:")
                    val parentMeta = database.vodDao().metadata(parentKey)
                    if (parentMeta != null && !parentMeta.streamUrl.isNullOrBlank() && parentMeta.streamUrl.startsWith("[")) {
                        try {
                            val epArray = org.json.JSONArray(parentMeta.streamUrl)
                            val epPart = item.sourceKey.substringAfter(":ep:")
                            val epIdx = epPart.substringBefore('_').toIntOrNull() ?: 0
                            if (epIdx in 0 until epArray.length()) {
                                val epObj = epArray.optJSONObject(epIdx)
                                val epUrl = epObj?.optString("videoUrl")?.trim()?.ifEmpty { epObj.optString("url").trim() }
                                if (!epUrl.isNullOrEmpty()) {
                                    meta = parentMeta.copy(sourceKey = item.sourceKey, streamUrl = epUrl)
                                }
                            }
                        } catch (e: Exception) {
                            debugLog.recordDebug("Parse dynamic JSON episode in playItem failed: ${e.message}")
                        }
                    }
                }
                if (meta != null && !meta.streamUrl.isNullOrBlank()) {
                    Pair(meta.streamUrl, meta.logoUrl ?: item.logoUrl)
                } else {
                    val ch = database.iptvDao().getChannel(item.sourceKey)
                    Pair(ch?.streamUrl, ch?.logoUrl ?: item.logoUrl)
                }
            }
            val intent = Intent(this@VodHomeActivity, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_VOD_SOURCE_KEY, item.sourceKey)
                putExtra(MainActivity.EXTRA_VOD_STREAM_URL, url)
                putExtra(MainActivity.EXTRA_VOD_TITLE, item.name)
                putExtra(MainActivity.EXTRA_VOD_LOGO_URL, logo)
                putExtra(MainActivity.EXTRA_VOD_START_OVER, false)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    private fun showPosterContextMenu(displayItem: VodDisplayItem) {
        val title = displayItem.tmdb?.title
            ?: VodTitleCleaner.clean(displayItem.catalogItem.name).cleanTitle
        val options = arrayOf(
            getString(R.string.vod_watch_later),
            getString(R.string.close),
        )

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(title)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        val duration = displayItem.catalogItem.durationMillis ?: 0L
                        resumeStore.markWatchLater(displayItem.catalogItem.sourceKey, duration)
                        Toast.makeText(this, R.string.vod_added_to_continue, Toast.LENGTH_SHORT).show()
                        lastPlayedSourceKey = displayItem.catalogItem.sourceKey
                        loadContinueWatching(shouldFocus = true)
                    }
                    1 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun showContinueContextMenu(displayItem: VodDisplayItem) {
        val title = displayItem.tmdb?.title
            ?: VodTitleCleaner.clean(displayItem.catalogItem.name).cleanTitle
        val options = arrayOf(
            getString(R.string.multiview_remove_channel),
            getString(R.string.close),
        )

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(title)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        resumeStore.clear(displayItem.catalogItem.sourceKey)
                        if (lastPlayedSourceKey == displayItem.catalogItem.sourceKey) {
                            lastPlayedSourceKey = null
                        }
                        loadContinueWatching(shouldFocus = true)
                    }
                    1 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun openLiveTv(targetSource: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_LIVE_MODE, true)
            if (targetSource != null) {
                putExtra(MainActivity.EXTRA_LIVE_TARGET_SOURCE, targetSource)
            }
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun showSearchDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Film veya dizi adı..."
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            if (gridQuery.isNotEmpty()) setText(gridQuery)
        }

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle("VOD Arama")
            .setView(input)
            .setPositiveButton(R.string.apply) { _, _ ->
                val query = input.text.toString().trim()
                gridQuery = query
                reloadGridData()
            }
            .setNeutralButton(R.string.clear_search) { _, _ ->
                gridQuery = ""
                reloadGridData()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    override fun onBackPressed() {
        openLiveTv()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    openLiveTv()
                    return true
                }
                KeyEvent.KEYCODE_PROG_RED -> {
                    openLiveTv()
                    return true
                }
                KeyEvent.KEYCODE_PROG_YELLOW -> {
                    showSourceSelectionDialog()
                    return true
                }
                KeyEvent.KEYCODE_PROG_BLUE -> {
                    showSearchDialog()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
