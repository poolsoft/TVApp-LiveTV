package com.tvapp.livetv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.IptvSourceSummary
import com.tvapp.livetv.data.VodFilter
import com.tvapp.livetv.data.VodRepository
import com.tvapp.livetv.data.local.VodCatalogItem
import com.tvapp.livetv.databinding.ActivityVodHomeBinding
import com.tvapp.livetv.databinding.DialogVodDetailBinding
import com.tvapp.livetv.databinding.DialogVodFiltersBinding
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.image.ChannelLogoLoader
import com.tvapp.livetv.platform.DeviceCapabilitiesSession
import com.tvapp.livetv.playback.IptvResumeEntry
import com.tvapp.livetv.playback.IptvResumeStore
import com.tvapp.livetv.playback.PlaybackHistoryStore
import com.tvapp.livetv.ui.VodCard
import com.tvapp.livetv.ui.VodHomeAdapter
import com.tvapp.livetv.ui.TvUiMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** VOD catalog stays separate from live playback and holds one bounded page. */
class VodHomeActivity : TvRemoteActivity() {
    private lateinit var binding: ActivityVodHomeBinding
    private lateinit var repository: VodRepository
    private lateinit var iptv: IptvRepository
    private lateinit var debugLog: CrashReportStore
    private lateinit var history: PlaybackHistoryStore
    private lateinit var legacyHistory: PlaybackHistoryStore
    private lateinit var resumeStore: IptvResumeStore
    private lateinit var gridAdapter: VodHomeAdapter
    private lateinit var continueAdapter: VodHomeAdapter
    private val preferences by lazy { getSharedPreferences("vod-library", MODE_PRIVATE) }
    private var sources: List<IptvSourceSummary> = emptyList()
    private var sourceId = -1L
    private var filter = VodFilter()
    private var pageIndex = 0
    private var total = 0
    private var focusedKey: String? = null
    private var focusedPosition = 0
    private var parentTitle: String? = null
    private var lastItem: VodCatalogItem? = null
    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var dialog: AlertDialog? = null
    private var loading = false
    private var initialized = false
    private var pendingFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        binding = ActivityVodHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = VodRepository(this)
        iptv = IptvRepository(this)
        debugLog = CrashReportStore(this)
        history = PlaybackHistoryStore(this, "vod-playback-history")
        legacyHistory = PlaybackHistoryStore(this)
        resumeStore = IptvResumeStore(this)
        sourceId = preferences.getLong("source", -1L)
        gridAdapter = VodHomeAdapter(::showDetail, ::showItemActions, { card, position ->
            focusedKey = card.item.sourceKey
            focusedPosition = position
            saveFilters()
        })
        continueAdapter = VodHomeAdapter(::showDetail, ::showItemActions, { _, _ -> }, compact = true)
        binding.vodGrid.layoutManager = GridLayoutManager(this,
            (resources.configuration.screenWidthDp / 190).coerceIn(2, 5))
        binding.vodGrid.adapter = gridAdapter
        binding.continueWatchingRow.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.continueWatchingRow.adapter = continueAdapter
        binding.vodLiveAction.setOnClickListener { openLiveTv() }
        binding.vodResumeAction.setOnClickListener { resumeLast() }
        binding.vodContinueAction.setOnClickListener { resumeLast() }
        binding.vodSourceAction.setOnClickListener { showSources() }
        binding.vodListAction.setOnClickListener { showSources() }
        binding.vodFilterAction.setOnClickListener { showFilters() }
        binding.vodSearchAction.setOnClickListener { showFilters() }
        val colorSize = (TvUiMetrics.COLOR_KEY_DP * resources.displayMetrics.density).toInt()
        listOf(binding.vodLiveAction, binding.vodContinueAction, binding.vodListAction, binding.vodSearchAction).forEach { action ->
            action.compoundDrawablesRelative[0]?.let { marker ->
                marker.setBounds(0, 0, colorSize, colorSize)
                action.setCompoundDrawablesRelative(marker, null, null, null)
            }
        }
        binding.vodCategoryAction.setOnClickListener { showCategories() }
        binding.vodMovies.setOnClickListener { selectSection("MOVIE") }
        binding.vodSeries.setOnClickListener { selectSection("SERIES") }
        binding.vodPrevious.setOnClickListener { changePage(-1) }
        binding.vodNext.setOnClickListener { changePage(1) }
        binding.vodEmpty.setOnClickListener { if (sources.isEmpty()) showSources() else loadPage(refreshSeries = true) }
        binding.root.post { binding.vodMovies.requestFocus() }
    }

    override fun onStart() {
        super.onStart()
        ChannelLogoLoader.configure(this, DeviceCapabilitiesSession.get(this))
        loadSources()
    }

    private fun loadSources() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        setLoading(true)
        loadJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { iptv.sources().filter { it.source.enabled } }
                if (generation != loadGeneration) return@launch
                sources = result
                val selected = sources.firstOrNull { it.source.id == sourceId } ?: sources.lastOrNull()
                if (selected == null) {
                    sourceId = -1
                    setLoading(false)
                    gridAdapter.submitList(emptyList())
                    showEmpty(R.string.vod_no_sources, retry = true)
                    updateHeader()
                    return@launch
                }
                if (!initialized || selected.source.id != sourceId) {
                    sourceId = selected.source.id
                    restoreFilters()
                    initialized = true
                }
                loadPage(restoreFocus = focusedKey != null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showFailure(error) }
        }
    }

    private fun restoreFilters() {
        val prefix = "$sourceId:"
        filter = VodFilter(category = preferences.getString(prefix + "category", null),
            query = preferences.getString(prefix + "query", "").orEmpty(),
            section = preferences.getString(prefix + "section", "MOVIE").orEmpty().takeIf { it in listOf("MOVIE", "SERIES") } ?: "MOVIE",
            view = preferences.getString(prefix + "view", "ALL").orEmpty().takeIf { it in VIEWS } ?: "ALL",
            order = preferences.getString(prefix + "order", "SOURCE").orEmpty().takeIf { it in listOf("SOURCE", "NAME") } ?: "SOURCE",
            parentKey = preferences.getString(prefix + "parent", null),
            season = preferences.getInt(prefix + "season", -1).takeIf { it >= 0 })
        pageIndex = preferences.getInt(prefix + "page", 0).coerceAtLeast(0)
        focusedKey = preferences.getString(prefix + "focus", null)
        focusedPosition = preferences.getInt(prefix + "position", 0).coerceIn(0, PAGE_SIZE - 1)
    }

    private fun saveFilters() {
        if (sourceId < 0) return
        val prefix = "$sourceId:"
        preferences.edit().putLong("source", sourceId)
            .putString(prefix + "category", filter.category).putString(prefix + "query", filter.query)
            .putString(prefix + "section", filter.section).putString(prefix + "view", filter.view)
            .putString(prefix + "order", filter.order).putString(prefix + "parent", filter.parentKey)
            .putInt(prefix + "season", filter.season ?: -1).putInt(prefix + "page", pageIndex)
            .putString(prefix + "focus", focusedKey).putInt(prefix + "position", focusedPosition).apply()
    }

    private fun keysFor(view: String): List<String> = when (view) {
        "CONTINUE" -> resumeStore.entries().map { it.sourceKey }.take(100)
        "RECENT" -> (history.keys() + legacyHistory.keys()).distinct().take(100)
        else -> emptyList()
    }

    private fun loadPage(restoreFocus: Boolean = false, refreshSeries: Boolean = false) {
        if (sourceId < 0) return
        loadJob?.cancel()
        detailJob?.cancel()
        val generation = ++loadGeneration
        val source = sourceId
        val snapshot = filter
        val requestedPage = pageIndex
        val resumes = resumeStore.entries().associateBy(IptvResumeEntry::sourceKey)
        val keys = keysFor(snapshot.view)
        pendingFocus = restoreFocus
        setLoading(true)
        binding.vodEmpty.visibility = View.GONE
        updateHeader()
        loadJob = lifecycleScope.launch {
            try {
                var cacheFailure: Exception? = null
                val result = withContext(Dispatchers.IO) {
                    if (snapshot.section == "SERIES" && snapshot.parentKey == null) {
                        try { repository.refreshSeries(source, force = refreshSeries) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { cacheFailure = error }
                    }
                    val count = repository.count(source, snapshot, keys)
                    val actualPage = requestedPage.coerceAtMost(((count - 1).coerceAtLeast(0)) / PAGE_SIZE)
                    val rows = repository.page(source, snapshot, keys, actualPage)
                    val continueRows = repository.page(source, VodFilter(section = snapshot.section, view = "CONTINUE"),
                        resumes.keys.toList(), 0).take(8)
                    val last = lastVodKey()?.let { repository.item(it) }
                    val parent = snapshot.parentKey?.let { repository.item(it) }
                    PageResult(rows, count, actualPage, continueRows, last, parent?.name)
                }
                if (generation != loadGeneration) return@launch
                total = result.count
                pageIndex = result.page
                parentTitle = result.parentTitle
                lastItem = result.last
                updateLast(resumes)
                continueAdapter.submitList(result.continueRows.filter { it.sourceKey != result.last?.sourceKey }
                    .map { VodCard(it, resumes[it.sourceKey]) })
                val showContinue = result.continueRows.any { it.sourceKey != result.last?.sourceKey } &&
                    snapshot.parentKey == null && resources.configuration.screenHeightDp >= 600
                binding.continueWatchingRow.visibility = if (showContinue) View.VISIBLE else View.GONE
                binding.continueWatchingLabel.visibility = binding.continueWatchingRow.visibility
                gridAdapter.submitList(result.rows.map { VodCard(it, resumes[it.sourceKey]) }) {
                    if (generation == loadGeneration) {
                        setLoading(false)
                        if (result.rows.isEmpty()) showEmpty(if (cacheFailure != null) R.string.vod_load_failed else R.string.vod_empty,
                            retry = cacheFailure != null)
                        updateHeader()
                        restoreGridFocus()
                    }
                }
                cacheFailure?.let { debugLog.recordDebug("VOD_SERIES_CACHE_FAILED | ${it.javaClass.simpleName}") }
                saveFilters()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (generation == loadGeneration) showFailure(error) }
        }
    }

    private data class PageResult(val rows: List<VodCatalogItem>, val count: Int, val page: Int,
        val continueRows: List<VodCatalogItem>, val last: VodCatalogItem?, val parentTitle: String?)

    private fun setLoading(value: Boolean) {
        loading = value
        binding.vodLoading.visibility = if (value) View.VISIBLE else View.GONE
        binding.vodPrevious.isEnabled = !value && pageIndex > 0
        binding.vodNext.isEnabled = !value && (pageIndex + 1) * PAGE_SIZE < total
    }

    private fun showEmpty(message: Int, retry: Boolean = false) {
        binding.vodEmpty.setText(message)
        binding.vodEmpty.isFocusable = retry
        binding.vodEmpty.isClickable = retry
        binding.vodEmpty.visibility = View.VISIBLE
    }

    private fun showFailure(error: Exception) {
        setLoading(false)
        showEmpty(R.string.vod_load_failed, retry = true)
        debugLog.recordDebug("VOD_CATALOG_FAILED | ${error.javaClass.simpleName}")
    }

    private fun updateHeader() {
        binding.vodSourceAction.text = sources.firstOrNull { it.source.id == sourceId }?.source?.name
            ?: getString(R.string.iptv_library_sources)
        binding.vodMovies.isSelected = filter.section == "MOVIE"
        binding.vodSeries.isSelected = filter.section == "SERIES"
        binding.vodCategoryAction.text = parentTitle?.let { getString(R.string.vod_season_title, it, filter.season ?: 0) }
            ?: filter.category ?: getString(R.string.vod_all_categories)
        binding.vodSummary.text = listOfNotNull(viewLabel(filter.view), filter.query.takeIf(String::isNotBlank),
            getString(R.string.vod_count, total)).joinToString(" · ")
        binding.vodPage.text = getString(R.string.vod_page_count, pageIndex + 1,
            ((total + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1))
    }

    private fun updateLast(resumes: Map<String, IptvResumeEntry>) {
        val last = lastItem
        binding.vodLastRow.visibility = if (last == null) View.GONE else View.VISIBLE
        binding.vodContinueAction.isEnabled = last != null
        if (last == null) return
        binding.vodLastName.text = last.name
        val resume = resumes[last.sourceKey]
        binding.vodLastPosition.text = resume?.let { getString(R.string.vod_saved_position, VodHomeAdapter.formatDuration(it.positionMillis)) }
            ?: getString(R.string.vod_recent)
        binding.vodResumeAction.setText(if (resume != null) R.string.vod_continue_item else R.string.vod_return_item)
        binding.vodResumeAction.contentDescription = getString(R.string.vod_continue_named, last.name)
        ChannelLogoLoader.load(binding.vodLastImage, last.logoUrl, R.drawable.ic_vod)
    }

    private fun restoreGridFocus() {
        if (!pendingFocus || gridAdapter.itemCount == 0 || dialog?.isShowing == true) return
        pendingFocus = false
        val position = gridAdapter.currentList.indexOfFirst { it.item.sourceKey == focusedKey }
            .takeIf { it >= 0 } ?: focusedPosition.coerceAtMost(gridAdapter.itemCount - 1)
        binding.vodGrid.scrollToPosition(position)
        binding.vodGrid.doOnLayout {
            binding.vodGrid.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        }
    }

    private fun changePage(delta: Int) {
        if (loading || total == 0) return
        val target = (pageIndex + delta).coerceIn(0, (total - 1) / PAGE_SIZE)
        if (target == pageIndex) return
        pageIndex = target
        focusedKey = null
        focusedPosition = if (delta > 0) 0 else PAGE_SIZE - 1
        loadPage(restoreFocus = true)
    }

    private fun changeFilter(value: VodFilter) {
        filter = value
        pageIndex = 0
        focusedKey = null
        focusedPosition = 0
        parentTitle = null
        saveFilters()
        loadPage()
    }

    private fun selectSection(section: String) {
        if (filter.section == section && filter.parentKey == null) return
        changeFilter(filter.copy(section = section, category = null, parentKey = null, season = null))
    }

    private fun showSources() {
        if (sources.isEmpty()) {
            showDialog(AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog).setTitle(R.string.iptv_library_sources)
                .setMessage(R.string.vod_no_sources).setPositiveButton(R.string.manage_iptv_sources) { _, _ ->
                    startActivity(Intent(this, IptvSourcesActivity::class.java))
                }.setNegativeButton(R.string.close, null).create())
            return
        }
        showDialog(AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog).setTitle(R.string.iptv_library_sources)
            .setSingleChoiceItems(sources.map { it.source.name }.toTypedArray(), sources.indexOfFirst { it.source.id == sourceId }) { d, i ->
                d.dismiss()
                saveFilters()
                sourceId = sources[i].source.id
                restoreFilters()
                loadPage(restoreFocus = true)
            }.setNegativeButton(R.string.close, null).create())
    }

    private fun showCategories() {
        if (filter.parentKey != null) { showSeasons(filter.parentKey!!); return }
        val source = sourceId
        val section = filter.section
        detailJob?.cancel()
        detailJob = lifecycleScope.launch {
            try {
                val categories = withContext(Dispatchers.IO) { repository.categories(source, section) }
                if (source != sourceId || section != filter.section) return@launch
                val labels = arrayOf(getString(R.string.vod_all_categories), *categories.toTypedArray())
                showDialog(AlertDialog.Builder(this@VodHomeActivity, R.style.Theme_TVApp_Dialog).setTitle(R.string.iptv_category_filter)
                    .setSingleChoiceItems(labels, categories.indexOf(filter.category) + 1) { d, i ->
                        d.dismiss(); changeFilter(filter.copy(category = categories.getOrNull(i - 1)))
                    }.setNegativeButton(R.string.close, null).create())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showFailure(error) }
        }
    }

    private fun showFilters() {
        val builder = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
        val form = DialogVodFiltersBinding.inflate(android.view.LayoutInflater.from(builder.context))
        var view = filter.view
        var order = filter.order
        form.vodSearch.setText(filter.query)
        fun labels() {
            form.vodViewFilter.text = viewLabel(view)
            form.vodOrderFilter.setText(if (order == "NAME") R.string.vod_sort_name else R.string.vod_sort_source)
        }
        labels()
        form.vodViewFilter.setOnClickListener { view = VIEWS[(VIEWS.indexOf(view) + 1) % VIEWS.size]; labels() }
        form.vodOrderFilter.setOnClickListener { order = if (order == "NAME") "SOURCE" else "NAME"; labels() }
        val d = builder.setTitle(R.string.vod_search_filters).setView(form.root)
            .setPositiveButton(R.string.apply) { _, _ ->
                hideKeyboard(form.vodSearch)
                changeFilter(filter.copy(query = form.vodSearch.text.toString().trim(), view = view, order = order))
            }.setNeutralButton(R.string.clear_search) { _, _ ->
                hideKeyboard(form.vodSearch)
                changeFilter(filter.copy(query = "", view = "ALL", category = null, order = "SOURCE"))
            }.setNegativeButton(R.string.close, null).create()
        form.vodSearch.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(form.vodSearch); form.vodViewFilter.requestFocus(); true
            } else false
        }
        showDialog(d)
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        form.vodViewFilter.requestFocus()
    }

    private fun viewLabel(view: String): String = getString(when (view) {
        "FAVORITES" -> R.string.vod_favorites
        "CONTINUE" -> R.string.continue_watching
        "RECENT" -> R.string.vod_recent
        else -> R.string.all_channels
    })

    private fun showDetail(card: VodCard) {
        if (loading) return
        rememberCatalogFocus(card)
        val builder = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
        val detail = DialogVodDetailBinding.inflate(android.view.LayoutInflater.from(builder.context))
        var item = card.item
        var navigating = false
        val resumes = resumeStore.entries().associateBy(IptvResumeEntry::sourceKey)
        val resume = resumes[item.sourceKey]
        fun render() {
            ChannelLogoLoader.load(detail.vodDetailImage, item.logoUrl, R.drawable.ic_vod)
            detail.vodDetailInfo.text = listOfNotNull(item.category,
                item.durationMillis?.takeIf { it > 0 }?.let(VodHomeAdapter::formatDuration),
                resume?.let { getString(R.string.vod_saved_position, VodHomeAdapter.formatDuration(it.positionMillis)) }).joinToString("\n")
            detail.vodDetailDescription.text = item.description?.takeIf(String::isNotBlank) ?: getString(R.string.vod_description_unknown)
            detail.vodDetailFavorite.isSelected = item.favorite
            detail.vodDetailFavorite.contentDescription = getString(if (item.favorite) R.string.remove_favorite else R.string.add_favorite)
        }
        render()
        val d = builder.setTitle(item.name).setView(detail.root)
            .setPositiveButton(if (item.kind == "SERIES") R.string.vod_seasons else if (resume != null) R.string.vod_continue_item else R.string.iptv_play_label) { _, _ ->
                navigating = true
                if (item.kind == "SERIES") showSeasons(item.sourceKey) else playItem(item)
            }.setNegativeButton(R.string.close, null)
        if (resume != null) d.setNeutralButton(R.string.vod_resume_from_start) { _, _ -> navigating = true; playItem(item, startOver = true) }
        val shown = d.create()
        detail.vodDetailFavorite.setOnClickListener {
            val favorite = !item.favorite
            detail.vodDetailFavorite.isEnabled = false
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { repository.setFavorite(item, favorite) }
                    item = item.copy(favorite = favorite); render()
                    // Refresh this bounded page after dismissing, without moving focus behind the dialog.
                    saveFilters()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { debugLog.recordDebug("VOD_FAVORITE_FAILED | ${error.javaClass.simpleName}") }
                finally { detail.vodDetailFavorite.isEnabled = true }
            }
        }
        showDialog(shown, onDismiss = { if (!navigating) loadPage(restoreFocus = true) })
        detail.vodDetailScroll.layoutParams.height = (resources.displayMetrics.heightPixels * .25f).toInt()
        detailJob?.cancel()
        detailJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.details(item) }
                if (shown.isShowing) { item = result.copy(favorite = item.favorite); render() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { debugLog.recordDebug("VOD_DETAILS_FAILED | ${error.javaClass.simpleName}") }
        }
    }

    private fun showSeasons(key: String) {
        detailJob?.cancel()
        binding.vodLoading.visibility = View.VISIBLE
        detailJob = lifecycleScope.launch {
            try {
                val seasons = withContext(Dispatchers.IO) { repository.refreshEpisodes(key); repository.seasons(key) }
                binding.vodLoading.visibility = if (loading) View.VISIBLE else View.GONE
                if (seasons.isEmpty()) {
                    showDialog(AlertDialog.Builder(this@VodHomeActivity, R.style.Theme_TVApp_Dialog).setTitle(R.string.vod_seasons)
                        .setMessage(R.string.vod_episodes_unavailable).setPositiveButton(R.string.close, null).create())
                } else showDialog(AlertDialog.Builder(this@VodHomeActivity, R.style.Theme_TVApp_Dialog).setTitle(R.string.vod_seasons)
                    .setItems(seasons.map { getString(R.string.vod_season, it) }.toTypedArray()) { _, i ->
                        changeFilter(filter.copy(parentKey = key, season = seasons[i], section = "SERIES", category = null, query = "", view = "ALL", order = "SOURCE"))
                    }.setNegativeButton(R.string.close, null).create())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showFailure(error) }
        }
    }

    private fun showItemActions(card: VodCard) {
        if (loading) return
        rememberCatalogFocus(card)
        val item = card.item
        val recent = item.sourceKey in keysFor("RECENT")
        val labels = mutableListOf(getString(if (item.favorite) R.string.remove_favorite else R.string.add_favorite))
        if (recent) labels += getString(R.string.vod_remove_from_continue)
        showDialog(AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog).setTitle(item.name)
            .setItems(labels.toTypedArray()) { _, i ->
                lifecycleScope.launch {
                    try {
                        if (i == 0) withContext(Dispatchers.IO) { repository.setFavorite(item, !item.favorite) }
                        else {
                            history.remove(item.sourceKey); legacyHistory.remove(item.sourceKey); resumeStore.clear(item.sourceKey)
                        }
                        loadPage(restoreFocus = true)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { showFailure(error) }
                }
            }.setNegativeButton(R.string.close, null).create())
    }

    private fun rememberCatalogFocus(card: VodCard) {
        val position = gridAdapter.currentList.indexOfFirst { it.item.sourceKey == card.item.sourceKey }
        if (position >= 0) {
            focusedKey = card.item.sourceKey
            focusedPosition = position
            saveFilters()
        }
    }

    private fun showDialog(value: AlertDialog, onDismiss: (() -> Unit)? = null) {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = value
        value.setOnDismissListener {
            if (dialog === value) dialog = null
            hideKeyboard(value.currentFocus)
            onDismiss?.invoke()
        }
        value.show()
    }

    private fun playItem(item: VodCatalogItem, startOver: Boolean = false) {
        if (item.kind == "SERIES") { showSeasons(item.sourceKey); return }
        saveFilters()
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_VOD_SOURCE_KEY, item.sourceKey)
            .putExtra(MainActivity.EXTRA_VOD_START_OVER, startOver)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun lastVodKey(): String? = getSharedPreferences(MainActivity.MODE_PREFS, MODE_PRIVATE).getString(MainActivity.LAST_VOD_KEY, null)
    private fun resumeLast() { lastItem?.let { playItem(it) } }
    private fun openLiveTv() {
        saveFilters()
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_LIVE_MODE, true)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    private fun hideKeyboard(view: View?) {
        val token = view?.windowToken ?: binding.root.windowToken
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(token, 0)
    }

    override fun onStop() { saveFilters(); super.onStop() }
    override fun onBackPressed() {
        if (dialog?.isShowing == true) { dialog?.dismiss(); return }
        if (filter.parentKey != null) { changeFilter(filter.copy(parentKey = null, season = null)); return }
        if (filter.query.isNotBlank() || filter.view != "ALL" || filter.category != null) {
            changeFilter(filter.copy(query = "", view = "ALL", category = null)); return
        }
        binding.vodLiveAction.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (dialog?.isShowing == true) return super.dispatchKeyEvent(event)
        val key = tvRemoteKeyCode(event.keyCode)
        if (key in listOf(KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_YELLOW,
                KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_CHANNEL_UP)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) when (key) {
                KeyEvent.KEYCODE_PROG_RED -> openLiveTv()
                KeyEvent.KEYCODE_PROG_GREEN -> resumeLast()
                KeyEvent.KEYCODE_PROG_YELLOW -> showSources()
                KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_SEARCH -> showFilters()
                KeyEvent.KEYCODE_CHANNEL_DOWN -> changePage(1)
                KeyEvent.KEYCODE_CHANNEL_UP -> changePage(-1)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && binding.vodGrid.hasFocus()) {
            val columns = (binding.vodGrid.layoutManager as GridLayoutManager).spanCount
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && focusedPosition >= gridAdapter.itemCount - columns &&
                (pageIndex + 1) * PAGE_SIZE < total) { changePage(1); return true }
            if (key == KeyEvent.KEYCODE_DPAD_UP && focusedPosition < columns && pageIndex > 0) { changePage(-1); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val PAGE_SIZE = VodRepository.PAGE_SIZE
        private val VIEWS = listOf("ALL", "FAVORITES", "CONTINUE", "RECENT")
    }
}
