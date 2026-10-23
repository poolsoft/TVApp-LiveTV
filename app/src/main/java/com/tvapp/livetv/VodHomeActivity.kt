package com.tvapp.livetv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.IptvSourceSummary
import com.tvapp.livetv.data.VodFilter
import com.tvapp.livetv.data.VodRepository
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.VodCatalogItem
import com.tvapp.livetv.data.local.VodTmdbEntity
import com.tvapp.livetv.databinding.ActivityVodHomeBinding
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.playback.IptvResumeStore
import com.tvapp.livetv.playback.PlaybackHistoryStore
import com.tvapp.livetv.vod.CleanedVodTitle
import com.tvapp.livetv.vod.TmdbApiClient
import com.tvapp.livetv.vod.VodCategoryAdapter
import com.tvapp.livetv.vod.VodCategoryItem
import com.tvapp.livetv.vod.VodDisplayItem
import com.tvapp.livetv.vod.VodPosterAdapter
import com.tvapp.livetv.vod.VodSourceAdapter
import com.tvapp.livetv.vod.VodTitleCleaner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Modern 3-column cinematic VOD dashboard with TMDB metadata enrichment. */
class VodHomeActivity : TvRemoteActivity() {
    private lateinit var binding: ActivityVodHomeBinding
    private lateinit var repository: VodRepository
    private lateinit var iptv: IptvRepository
    private lateinit var debugLog: CrashReportStore
    private lateinit var history: PlaybackHistoryStore
    private lateinit var resumeStore: IptvResumeStore
    private lateinit var database: TVAppDatabase

    private lateinit var popularAdapter: VodPosterAdapter
    private lateinit var recentAdapter: VodPosterAdapter
    private lateinit var sourceAdapter: VodSourceAdapter
    private lateinit var categoryAdapter: VodCategoryAdapter

    private val preferences by lazy { getSharedPreferences("vod-library", MODE_PRIVATE) }
    private var sources: List<IptvSourceSummary> = emptyList()
    private var sourceId = -1L
    private var filter = VodFilter()
    private var currentHeroItem: VodDisplayItem? = null

    private var loadJob: Job? = null
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
        sourceId = preferences.getLong("source", -1L)

        setupRecyclerViews()
        setupSidebarActions()
        updateClock()
        loadData()
        binding.vodNavHome.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockRunnable)
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
        // Popüler Filmler Rayı
        popularAdapter = VodPosterAdapter(
            onItemClick = { displayItem -> playItem(displayItem.catalogItem) },
            onItemFocused = { displayItem -> updateHeroBanner(displayItem) },
        )
        binding.vodRecyclerPopular.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.vodRecyclerPopular.adapter = popularAdapter

        // Son Eklenenler Rayı
        recentAdapter = VodPosterAdapter(
            onItemClick = { displayItem -> playItem(displayItem.catalogItem) },
            onItemFocused = { displayItem -> updateHeroBanner(displayItem) },
        )
        binding.vodRecyclerRecent.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.vodRecyclerRecent.adapter = recentAdapter

        // Sağ Panel - Kaynaklar
        sourceAdapter = VodSourceAdapter { sourceSummary ->
            sourceId = sourceSummary.source.id
            preferences.edit().putLong("source", sourceId).apply()
            loadData()
        }
        binding.vodRecyclerSources.layoutManager = LinearLayoutManager(this)
        binding.vodRecyclerSources.adapter = sourceAdapter

        // Sağ Panel - Kategoriler
        categoryAdapter = VodCategoryAdapter { catItem ->
            filter = if (filter.category == catItem.name) {
                filter.copy(category = null)
            } else {
                filter.copy(category = catItem.name)
            }
            loadData()
        }
        binding.vodRecyclerCategories.layoutManager = LinearLayoutManager(this)
        binding.vodRecyclerCategories.adapter = categoryAdapter
    }

    private fun setupSidebarActions() {
        binding.vodNavHome.setOnClickListener {
            filter = VodFilter()
            loadData()
        }
        binding.vodNavLive.setOnClickListener { openLiveTv() }
        binding.vodNavMovies.setOnClickListener {
            filter = filter.copy(section = "MOVIES")
            loadData()
        }
        binding.vodNavSeries.setOnClickListener {
            filter = filter.copy(section = "SERIES")
            loadData()
        }
        binding.vodNavContinue.setOnClickListener {
            filter = filter.copy(view = "CONTINUE")
            loadData()
        }
        binding.vodNavFilter.setOnClickListener {
            showSearchDialog()
        }
        binding.vodSearchBox.setOnClickListener {
            showSearchDialog()
        }

        binding.vodHeroBtnPlay.setOnClickListener {
            currentHeroItem?.let { playItem(it.catalogItem) }
        }
        binding.vodHeroBtnDetails.setOnClickListener {
            currentHeroItem?.let { item ->
                showHeroDetailsDialog(item)
            }
        }
    }

    private fun updateHeroBanner(displayItem: VodDisplayItem) {
        currentHeroItem = displayItem
        val cleaned = if (displayItem.tmdb == null) VodTitleCleaner.clean(displayItem.catalogItem.name) else null
        val title = displayItem.tmdb?.title ?: cleaned?.cleanTitle ?: displayItem.catalogItem.name
        binding.vodHeroTitle.text = title

        val rating = displayItem.tmdb?.rating ?: 0.0
        if (rating > 0.0) {
            binding.vodHeroRating.visibility = View.VISIBLE
            binding.vodHeroRating.text = "★ $rating"
        } else {
            binding.vodHeroRating.visibility = View.GONE
        }

        binding.vodHeroYear.text = displayItem.tmdb?.releaseYear ?: cleaned?.year ?: ""

        val genres = displayItem.tmdb?.genres
        if (!genres.isNullOrBlank()) {
            binding.vodHeroGenres.visibility = View.VISIBLE
            binding.vodHeroGenres.text = genres
        } else {
            binding.vodHeroGenres.visibility = View.GONE
        }

        val overview = displayItem.tmdb?.overview ?: displayItem.catalogItem.description
        binding.vodHeroOverview.text = if (!overview.isNullOrBlank()) overview else "Özet bilgisi bulunmuyor."

        val posterUrl = displayItem.tmdb?.posterUrl ?: displayItem.catalogItem.logoUrl
        binding.vodHeroPoster.load(posterUrl) {
            crossfade(true)
            placeholder(R.drawable.bg_vod_poster_card)
            error(R.drawable.bg_vod_poster_card)
        }

        val backdropUrl = displayItem.tmdb?.backdropUrl ?: posterUrl
        binding.vodHeroBackdrop.load(backdropUrl) {
            crossfade(true)
        }
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                // 1. Kaynakları yükle
                sources = withContext(Dispatchers.IO) { iptv.sources() }
                if (sourceId <= 0L && sources.isNotEmpty()) {
                    sourceId = sources.first().source.id
                }

                val activeSource = sources.find { it.source.id == sourceId }
                binding.vodBottomActiveSource.text = activeSource?.source?.name ?: "IPTV"

                sourceAdapter.submitList(sources, sourceId)

                // 2. Kategorileri yükle
                val catStrings = withContext(Dispatchers.IO) {
                    repository.categories(sourceId, filter.section)
                }
                val catItems = catStrings.filterNotNull().map {
                    VodCategoryItem(name = it, count = 0)
                }
                categoryAdapter.submitList(catItems, filter.category)

                // 3. İçerikleri yükle (Popüler ve Son Eklenenler)
                val popularPage = withContext(Dispatchers.IO) {
                    repository.page(sourceId, filter.copy(order = "SOURCE"), keys = emptyList(), page = 0)
                }
                val recentPage = withContext(Dispatchers.IO) {
                    repository.page(sourceId, filter.copy(order = "NAME"), keys = emptyList(), page = 0)
                }

                // 4. TMDB önbelleğini sorgula
                val allKeys = (popularPage + recentPage).map { it.sourceKey }.distinct()
                val cachedTmdb = withContext(Dispatchers.IO) {
                    database.vodTmdbDao().getBySourceKeys(allKeys).associateBy { it.sourceKey }
                }

                val popularDisplay = popularPage.map { VodDisplayItem(it, cachedTmdb[it.sourceKey]) }
                val recentDisplay = recentPage.map { VodDisplayItem(it, cachedTmdb[it.sourceKey]) }

                popularAdapter.submitList(popularDisplay)
                recentAdapter.submitList(recentDisplay)

                // İlk öğeyi Hero Banner'a ata
                if (popularDisplay.isNotEmpty()) {
                    updateHeroBanner(popularDisplay.first())
                } else if (recentDisplay.isNotEmpty()) {
                    updateHeroBanner(recentDisplay.first())
                }

                // 5. Arka planda eksik TMDB verilerini zenginleştir
                enrichMissingTmdb(popularPage + recentPage, cachedTmdb)

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                debugLog.recordDebug("VOD_HOME_LOAD_FAILED | ${e.javaClass.simpleName}")
            }
        }
    }

    private fun enrichMissingTmdb(items: List<VodCatalogItem>, cached: Map<String, VodTmdbEntity>) {
        tmdbJob?.cancel()
        tmdbJob = lifecycleScope.launch(Dispatchers.IO) {
            val missing = items.filter { it.sourceKey !in cached }.distinctBy { it.name }
            for (item in missing.take(20)) {
                try {
                    val cleaned = VodTitleCleaner.clean(item.name)
                    val tmdbResult = if (item.kind == "SERIES") {
                        TmdbApiClient.searchTvShow(cleaned.cleanTitle, cleaned.year)
                    } else {
                        TmdbApiClient.searchMovie(cleaned.cleanTitle, cleaned.year)
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
                            popularAdapter.updateTmdbItem(item.sourceKey, entity)
                            recentAdapter.updateTmdbItem(item.sourceKey, entity)
                            if (currentHeroItem?.catalogItem?.sourceKey == item.sourceKey) {
                                val updatedHero = currentHeroItem?.copy(tmdb = entity)
                                if (updatedHero != null) {
                                    updateHeroBanner(updatedHero)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Devam et
                }
            }
        }
    }

    private fun playItem(item: VodCatalogItem) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VOD_SOURCE_KEY, item.sourceKey)
            putExtra(MainActivity.EXTRA_VOD_START_OVER, false)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun openLiveTv() {
        finish()
    }

    private fun showHeroDetailsDialog(displayItem: VodDisplayItem) {
        val title = displayItem.tmdb?.title ?: displayItem.catalogItem.name
        val overview = displayItem.tmdb?.overview ?: displayItem.catalogItem.description ?: "Özet bulunmuyor."
        val genres = displayItem.tmdb?.genres ?: "Sinema"
        val year = displayItem.tmdb?.releaseYear ?: ""
        val rating = displayItem.tmdb?.rating?.let { "★ $it / 10" } ?: ""

        val message = "$year · $genres\n$rating\n\n$overview"

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Hemen İzle") { _, _ -> playItem(displayItem.catalogItem) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showSearchDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Film veya dizi adı..."
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle("VOD Arama")
            .setView(input)
            .setPositiveButton(R.string.apply) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    filter = filter.copy(query = query)
                    loadData()
                }
            }
            .setNeutralButton(R.string.clear_search) { _, _ ->
                filter = filter.copy(query = "")
                loadData()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> {
                    openLiveTv()
                    return true
                }
                KeyEvent.KEYCODE_PROG_GREEN -> {
                    filter = filter.copy(view = "CONTINUE")
                    loadData()
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
