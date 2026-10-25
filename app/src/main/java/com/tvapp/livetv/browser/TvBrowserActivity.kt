package com.tvapp.livetv.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tvapp.livetv.MainActivity
import com.tvapp.livetv.R

/**
 * Android TV icin sanal imlec, D-pad gezinme, ayarlar ve video akis yakalama destekli web tarayicisi.
 */
class TvBrowserActivity : Activity() {

    private lateinit var mainVerticalContainer: LinearLayout
    private lateinit var fullscreenContainer: FrameLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var hasAutoFullscreened = false
    private var isWebFullscreen = false

    private lateinit var layout: CursorLayout
    private lateinit var webView: WebView
    private lateinit var topBar: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var btnClearUrl: ImageButton
    private lateinit var btnExitTv: ImageButton
    private lateinit var btnTopMouseMode: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnNavBack: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnGo: Button
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnPlayStream: Button
    private lateinit var floatingPlayChip: TextView
    private lateinit var progressBar: ProgressBar

    // Home Dashboard bilesenleri
    private lateinit var homeDashboard: ScrollView
    private lateinit var homeSearchInput: EditText
    private lateinit var btnHomeSearch: Button
    private lateinit var homeFavoritesTitle: TextView
    private lateinit var homeFavoritesContainer: LinearLayout

    // URL Hizli Eylem Cubugu bilesenleri
    private lateinit var actionBar: LinearLayout
    private lateinit var btnActionClear: Button
    private lateinit var btnActionGo: Button
    private lateinit var btnActionMouse: Button
    private lateinit var btnActionCom: Button
    private lateinit var btnActionHttps: Button
    private lateinit var btnActionFavorite: Button
    private lateinit var btnActionReload: Button
    private lateinit var btnActionPlay: Button

    private lateinit var cursor: CursorController
    private lateinit var sniffer: StreamSniffer

    private var currentPlaybackRequest: PlaybackRequest? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var currentSpeedMultiplier = 1.0f

    private var topBarVisible = true
    private val autoHideDelayMs = 3500L
    private val autoHideRunnable = Runnable { hideTopBar() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_browser)

        loadPreferences()
        initViews()
        setupSniffer()
        setupWebView()
        setupCursor()
        setupListeners()
        updateMouseModeButton()

        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (!initialUrl.isNullOrBlank()) {
            loadUrl(initialUrl)
        } else {
            showHomeDashboard()
        }
        resetAutoHideTimer()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val speedIndex = prefs.getInt(KEY_CURSOR_SPEED, 1)
        currentSpeedMultiplier = when (speedIndex) {
            0 -> 0.65f
            2 -> 1.5f
            else -> 1.0f
        }
    }

    private fun initViews() {
        layout = findViewById(R.id.browser_cursor_layout)
        webView = findViewById(R.id.browser_web_view)
        topBar = findViewById(R.id.browser_top_bar)
        urlInput = findViewById(R.id.edit_browser_url)
        btnClearUrl = findViewById(R.id.btn_browser_clear_url)
        btnExitTv = findViewById(R.id.btn_browser_exit_tv)
        btnTopMouseMode = findViewById(R.id.btn_browser_mouse_mode)
        btnHome = findViewById(R.id.btn_browser_home)
        btnNavBack = findViewById(R.id.btn_browser_nav_back)
        btnReload = findViewById(R.id.btn_browser_reload)
        btnGo = findViewById(R.id.btn_browser_go)
        btnFavorite = findViewById(R.id.btn_browser_favorite)
        btnSettings = findViewById(R.id.btn_browser_settings)
        btnPlayStream = findViewById(R.id.btn_play_stream)
        floatingPlayChip = findViewById(R.id.browser_floating_play_chip)
        progressBar = findViewById(R.id.browser_progress_bar)

        // Home Dashboard
        homeDashboard = findViewById(R.id.browser_home_dashboard)
        homeSearchInput = findViewById(R.id.home_search_input)
        btnHomeSearch = findViewById(R.id.btn_home_search)
        homeFavoritesTitle = findViewById(R.id.home_favorites_title)
        homeFavoritesContainer = findViewById(R.id.home_favorites_container)

        // URL Hizli Eylem Cubugu
        actionBar = findViewById(R.id.browser_url_action_bar)
        btnActionClear = findViewById(R.id.btn_action_clear)
        btnActionGo = findViewById(R.id.btn_action_go)
        btnActionMouse = findViewById(R.id.btn_action_mouse)
        btnActionCom = findViewById(R.id.btn_action_com)
        btnActionHttps = findViewById(R.id.btn_action_https)
        btnActionFavorite = findViewById(R.id.btn_action_favorite)
        btnActionReload = findViewById(R.id.btn_action_reload)
        btnActionPlay = findViewById(R.id.btn_action_play)

        mainVerticalContainer = findViewById(R.id.browser_main_vertical_container)
        fullscreenContainer = findViewById(R.id.fullscreen_custom_view_container)

        layout.topBar = topBar
        layout.dashboard = homeDashboard
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = currentFocus?.windowToken ?: urlInput.windowToken
        imm?.hideSoftInputFromWindow(token, 0)
        urlInput.clearFocus()
        homeSearchInput.clearFocus()
    }

    private fun showHomeDashboard() {
        homeDashboard.visibility = View.VISIBLE
        urlInput.setText("")
        populateHomeFavorites()
        showTopBar()
        homeSearchInput.requestFocus()
    }

    private fun hideHomeDashboard() {
        homeDashboard.visibility = View.GONE
    }

    private fun showTopBar() {
        topBarVisible = true
        topBar.visibility = View.VISIBLE
    }

    private fun hideTopBar() {
        // Ust bar sabit olarak tepede kalir, sayfa alt ucundan baslar
    }

    private fun resetAutoHideTimer() {
        // Sabit barda auto-hide gerekmez
    }

    private fun setupSniffer() {
        sniffer = StreamSniffer(
            userAgent = { webView.settings.userAgentString },
            onStreamAvailable = { request ->
                uiHandler.post {
                    currentPlaybackRequest = request
                    showStreamDetected(request)
                }
            },
            onCleared = {
                uiHandler.post {
                    currentPlaybackRequest = null
                    btnPlayStream.visibility = View.GONE
                    btnActionPlay.visibility = View.GONE
                    floatingPlayChip.visibility = View.GONE
                }
            },
            schedule = { delayMs, task -> uiHandler.postDelayed(task, delayMs) }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = if (desktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
        }

        webView.webViewClient = SniffingWebViewClient(
            sniffer = sniffer,
            onNavigate = { url ->
                urlInput.setText(url)
                updateFavoriteIcon()
                resetAutoHideTimer()
            },
            onLoaded = { _ ->
                progressBar.visibility = View.GONE
                updateFavoriteIcon()
                resetAutoHideTimer()
            }
        )

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view
                customViewCallback = callback
                fullscreenContainer.removeAllViews()
                fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                fullscreenContainer.visibility = View.VISIBLE
                mainVerticalContainer.visibility = View.GONE
                view?.requestFocus()
            }

            override fun onHideCustomView() {
                fullscreenContainer.visibility = View.GONE
                fullscreenContainer.removeAllViews()
                mainVerticalContainer.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
                webView.requestFocus()
            }
        }
    }

    private fun setupCursor() {
        cursor = CursorController(webView, speedMultiplier = { currentSpeedMultiplier }) {
            layout.invalidate()
        }
        cursor.dashboard = homeDashboard

        cursor.onCursorMove = { _, y ->
            if (!isWebFullscreen) {
                val barLimit = layout.getTopBarBottomLimit()
                if (y < barLimit + 40f && !topBarVisible) {
                    showTopBar()
                } else {
                    resetAutoHideTimer()
                }
            }
        }

        layout.webView = webView
        layout.cursor = cursor
        layout.playChip = floatingPlayChip
        layout.onChipClick = {
            onPlayStreamClicked()
        }
        layout.onBack = {
            if (customView != null) {
                webView.webChromeClient?.onHideCustomView()
                true
            } else if (isWebFullscreen) {
                disableWebFullscreen()
                true
            } else if (homeDashboard.visibility == View.VISIBLE) {
                finish()
                true
            } else if (webView.canGoBack()) {
                webView.goBack()
                true
            } else {
                showHomeDashboard()
                true
            }
        }
        layout.onExitPage = {
            finish()
        }
        layout.onTopBarRequested = {
            if (!isWebFullscreen) {
                showTopBar()
            }
        }

        // Mod degistiginde (OK uzun basilinca) TV bildirimini goster
        layout.onModeChanged = { mode ->
            updateMouseModeButton()
            val msgRes = if (mode == CursorController.Mode.CURSOR) {
                R.string.browser_mode_cursor
            } else {
                R.string.browser_mode_focus
            }
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
            if (!isWebFullscreen) {
                showTopBar()
            }
        }

        // Fare en tepeye ulastiginda dogrudan ust bara odaklan
        cursor.onReachedTop = {
            if (!isWebFullscreen) {
                showTopBar()
                layout.enterTopBar()
            }
        }

        // Ust bar yerlestiginde farenin ust sinirini belirle ve imleci ortala
        topBar.post {
            val limit = layout.getTopBarBottomLimit()
            cursor.minY = limit
            cursor.center(webView.width, webView.height)
        }
    }

    private fun setupListeners() {
        btnExitTv.setOnClickListener {
            finish()
        }

        btnTopMouseMode.setOnClickListener {
            cursor.toggleMode()
            updateMouseModeButton()
            val msgRes = if (cursor.mode == CursorController.Mode.CURSOR) {
                R.string.browser_mode_cursor
            } else {
                R.string.browser_mode_focus
            }
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
            resetAutoHideTimer()
        }

        // URL Alanina girildiginde alt eylem cubugunu ac, odak cikinca kapat
        val checkActionBarFocus: () -> Unit = {
            topBar.postDelayed({
                val hasActionFocus = btnActionClear.hasFocus() || btnActionGo.hasFocus() ||
                    btnActionMouse.hasFocus() || btnActionCom.hasFocus() || btnActionHttps.hasFocus() ||
                    btnActionFavorite.hasFocus() || btnActionReload.hasFocus() || btnActionPlay.hasFocus()
                if (!hasActionFocus && !urlInput.hasFocus()) {
                    actionBar.visibility = View.GONE
                }
            }, 120)
        }

        urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                actionBar.visibility = View.VISIBLE
            } else {
                checkActionBarFocus()
            }
        }

        val actionFocusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkActionBarFocus()
            }
        }
        btnActionClear.onFocusChangeListener = actionFocusListener
        btnActionGo.onFocusChangeListener = actionFocusListener
        btnActionMouse.onFocusChangeListener = actionFocusListener
        btnActionCom.onFocusChangeListener = actionFocusListener
        btnActionHttps.onFocusChangeListener = actionFocusListener
        btnActionFavorite.onFocusChangeListener = actionFocusListener
        btnActionReload.onFocusChangeListener = actionFocusListener
        btnActionPlay.onFocusChangeListener = actionFocusListener

        btnHome.setOnClickListener {
            showHomeDashboard()
        }

        btnNavBack.setOnClickListener {
            if (homeDashboard.visibility == View.VISIBLE) {
                finish()
            } else if (webView.canGoBack()) {
                webView.goBack()
            } else {
                showHomeDashboard()
            }
        }

        btnReload.setOnClickListener {
            if (homeDashboard.visibility == View.VISIBLE) {
                populateHomeFavorites()
            } else {
                webView.reload()
            }
            resetAutoHideTimer()
        }

        btnClearUrl.setOnClickListener {
            urlInput.setText("")
            urlInput.requestFocus()
        }

        btnGo.setOnClickListener {
            val text = urlInput.text.toString()
            loadUrl(text)
            resetAutoHideTimer()
        }

        btnFavorite.setOnClickListener {
            toggleFavorite()
            resetAutoHideTimer()
        }

        btnFavorite.setOnLongClickListener {
            showFavoritesDialog()
            true
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // URL Hizli Eylem Cubugu Buton Dinleyicileri
        btnActionClear.setOnClickListener {
            urlInput.setText("")
            urlInput.requestFocus()
            resetAutoHideTimer()
        }

        btnActionGo.setOnClickListener {
            val text = urlInput.text.toString()
            loadUrl(text)
            resetAutoHideTimer()
        }

        btnActionMouse.setOnClickListener {
            cursor.toggleMode()
            updateMouseModeButton()
            val msgRes = if (cursor.mode == CursorController.Mode.CURSOR) {
                R.string.browser_mode_cursor
            } else {
                R.string.browser_mode_focus
            }
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
            resetAutoHideTimer()
        }

        btnActionCom.setOnClickListener {
            val cur = urlInput.text.toString()
            if (!cur.endsWith(".com") && !cur.contains(".com/")) {
                urlInput.append(".com")
            }
            urlInput.requestFocus()
            urlInput.setSelection(urlInput.text.length)
            resetAutoHideTimer()
        }

        btnActionHttps.setOnClickListener {
            val cur = urlInput.text.toString()
            if (!cur.startsWith("http://") && !cur.startsWith("https://")) {
                urlInput.setText("https://$cur")
            }
            urlInput.requestFocus()
            urlInput.setSelection(urlInput.text.length)
            resetAutoHideTimer()
        }

        btnActionFavorite.setOnClickListener {
            toggleFavorite()
            resetAutoHideTimer()
        }

        btnActionReload.setOnClickListener {
            if (homeDashboard.visibility == View.VISIBLE) {
                populateHomeFavorites()
            } else {
                webView.reload()
            }
            resetAutoHideTimer()
        }

        btnActionPlay.setOnClickListener {
            onPlayStreamClicked()
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadUrl(urlInput.text.toString())
                resetAutoHideTimer()
                true
            } else {
                false
            }
        }

        btnHomeSearch.setOnClickListener {
            val q = homeSearchInput.text.toString().trim()
            if (q.isNotBlank()) {
                loadUrl(q)
            }
        }

        homeSearchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val q = homeSearchInput.text.toString().trim()
                if (q.isNotBlank()) {
                    loadUrl(q)
                    true
                } else false
            } else false
        }

        btnPlayStream.setOnClickListener {
            onPlayStreamClicked()
        }

        floatingPlayChip.setOnClickListener {
            onPlayStreamClicked()
        }
    }

    private fun populateHomeFavorites() {
        val favs = getSavedFavorites()
        if (favs.isEmpty()) {
            homeFavoritesTitle.visibility = View.GONE
            homeFavoritesContainer.visibility = View.GONE
            homeFavoritesContainer.removeAllViews()
            return
        }
        homeFavoritesTitle.visibility = View.VISIBLE
        homeFavoritesContainer.visibility = View.VISIBLE
        homeFavoritesContainer.removeAllViews()

        val density = resources.displayMetrics.density
        val chunked = favs.chunked(3)
        for (rowItems in chunked) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (14 * density).toInt())
                }
            }

            for ((index, item) in rowItems.withIndex()) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_browser_card)
                    isFocusable = true
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        (72 * density).toInt(),
                        1f
                    ).apply {
                        if (index > 0) {
                            setMargins((14 * density).toInt(), 0, 0, 0)
                        }
                    }
                    val titleView = TextView(this@TvBrowserActivity).apply {
                        text = item.first.take(24)
                        setTextColor(getColor(R.color.accent))
                        textSize = 15f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        maxLines = 1
                    }
                    val urlView = TextView(this@TvBrowserActivity).apply {
                        text = item.second.substringBefore('?').take(32)
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 11f
                        maxLines = 1
                    }
                    addView(titleView)
                    addView(urlView)
                    setOnClickListener {
                        loadUrl(item.second)
                    }
                    setOnLongClickListener {
                        showFavoriteActionsDialog(item.first, item.second)
                        true
                    }
                }
                row.addView(card)
            }
            for (i in rowItems.size until 3) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        1,
                        1f
                    ).apply {
                        setMargins((14 * density).toInt(), 0, 0, 0)
                    }
                }
                row.addView(spacer)
            }
            homeFavoritesContainer.addView(row)
        }
    }

    private fun getSavedFavorites(): MutableList<Pair<String, String>> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_FAVORITES, null)
        val list = mutableListOf<Pair<String, String>>()
        if (rawJson != null) {
            try {
                val array = org.json.JSONArray(rawJson)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val u = obj.optString("url")
                    val t = obj.optString("title")
                    if (u.isNotBlank()) list.add(t to u)
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        if (list.isEmpty()) {
            list.addAll(
                listOf(
                    "Google" to "https://www.google.com",
                    "YouTube" to "https://www.youtube.com",
                    "HDFilmcehennemi" to "https://www.hdfilmcehennemi.life",
                    "Filmmodu" to "https://www.filmmodu.cx",
                    "DiziBOX" to "https://www.dizibox.pw",
                    "Diziwatch" to "https://diziwatch.net"
                )
            )
        }
        return list
    }

    private fun saveFavorites(list: List<Pair<String, String>>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val array = org.json.JSONArray()
        list.forEach { (t, u) ->
            val obj = org.json.JSONObject()
            obj.put("title", t)
            obj.put("url", u)
            array.put(obj)
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    private fun updateFavoriteIcon() {
        val currentUrl = webView.url ?: urlInput.text.toString()
        val favs = getSavedFavorites()
        val isFav = favs.any { it.second.equals(currentUrl, ignoreCase = true) }
        if (isFav) {
            btnFavorite.setColorFilter(getColor(R.color.accent))
        } else {
            btnFavorite.setColorFilter(getColor(R.color.text_primary))
        }
    }

    private fun toggleFavorite() {
        val currentUrl = webView.url ?: urlInput.text.toString()
        if (currentUrl.isBlank()) return
        val title = webView.title?.takeIf { it.isNotBlank() } ?: currentUrl
        val favs = getSavedFavorites()
        val existingIndex = favs.indexOfFirst { it.second.equals(currentUrl, ignoreCase = true) }
        if (existingIndex >= 0) {
            favs.removeAt(existingIndex)
            saveFavorites(favs)
            Toast.makeText(this, R.string.browser_favorite_removed, Toast.LENGTH_SHORT).show()
        } else {
            favs.add(0, title to currentUrl)
            saveFavorites(favs)
            Toast.makeText(this, R.string.browser_favorite_added, Toast.LENGTH_SHORT).show()
        }
        updateFavoriteIcon()
        if (homeDashboard.visibility == View.VISIBLE) {
            populateHomeFavorites()
        }
    }

    private fun showFavoritesDialog() {
        val favs = getSavedFavorites()
        if (favs.isEmpty()) {
            Toast.makeText(this, R.string.browser_no_favorites, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = favs.map { "${it.first}\n${it.second}" }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.browser_favorites)
            .setItems(labels) { _, which ->
                val selected = favs.getOrNull(which) ?: return@setItems
                showFavoriteActionsDialog(selected.first, selected.second)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFavoriteActionsDialog(title: String, url: String) {
        val options = arrayOf(
            getString(R.string.browser_open_favorite),
            getString(R.string.browser_delete_favorite)
        )
        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(title)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> loadUrl(url)
                    1 -> {
                        val favs = getSavedFavorites()
                        favs.removeAll { it.second.equals(url, ignoreCase = true) }
                        saveFavorites(favs)
                        updateFavoriteIcon()
                        if (homeDashboard.visibility == View.VISIBLE) {
                            populateHomeFavorites()
                        }
                        Toast.makeText(this, R.string.browser_favorite_removed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onPlayStreamClicked() {
        if (!isWebFullscreen) {
            enableWebFullscreen()
        } else {
            disableWebFullscreen()
        }
    }

    private fun showStreamSelectionDialog(streams: List<PlaybackRequest>) {
        val items = streams.mapIndexed { index, req ->
            buildStreamLabel(index + 1, req.url)
        }.toTypedArray()

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.browser_select_stream)
            .setItems(items) { dialog, which ->
                dialog.dismiss()
                val selected = streams.getOrNull(which) ?: return@setItems
                playDetectedStream(selected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildStreamLabel(index: Int, url: String): String {
        val cleanUrl = url.substringBefore('?')
        val ext = cleanUrl.substringAfterLast('.', "")
        val quality = when {
            url.contains("1080", ignoreCase = true) -> " [1080p]"
            url.contains("720", ignoreCase = true) -> " [720p]"
            url.contains("480", ignoreCase = true) -> " [480p]"
            url.contains("360", ignoreCase = true) -> " [360p]"
            else -> ""
        }
        val type = when {
            url.contains(".m3u8", ignoreCase = true) -> "HLS"
            url.contains(".mpd", ignoreCase = true) -> "DASH"
            url.contains(".mp4", ignoreCase = true) -> "MP4"
            else -> ext.uppercase().take(4)
        }
        val domain = runCatching { java.net.URI(url).host }.getOrNull() ?: ""
        return "$index. $type$quality - $domain"
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_MENU) {
            showSettingsDialog()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentSpeed = prefs.getInt(KEY_CURSOR_SPEED, 1)
        val blockAds = prefs.getBoolean(KEY_BLOCK_ADS, true)
        val desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, true)

        val speedLabel = when (currentSpeed) {
            0 -> getString(R.string.browser_speed_slow)
            2 -> getString(R.string.browser_speed_fast)
            else -> getString(R.string.browser_speed_normal)
        }

        val options = arrayOf(
            "${getString(R.string.browser_cursor_speed)}: $speedLabel",
            "${getString(R.string.browser_block_ads)}: ${if (blockAds) "✓" else "✗"}",
            "${getString(R.string.browser_desktop_mode)}: ${if (desktopMode) "✓" else "✗"}",
            getString(R.string.browser_favorites),
            getString(R.string.browser_clear_cache)
        )

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.browser_settings)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> showCursorSpeedDialog()
                    1 -> {
                        val next = !blockAds
                        prefs.edit().putBoolean(KEY_BLOCK_ADS, next).apply()
                        showSettingsDialog()
                    }
                    2 -> {
                        val next = !desktopMode
                        prefs.edit().putBoolean(KEY_DESKTOP_MODE, next).apply()
                        webView.settings.userAgentString = if (next) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                        webView.reload()
                    }
                    3 -> showFavoritesDialog()
                    4 -> {
                        webView.clearCache(true)
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        Toast.makeText(this, R.string.browser_cache_cleared, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCursorSpeedDialog() {
        val speeds = arrayOf(
            getString(R.string.browser_speed_slow),
            getString(R.string.browser_speed_normal),
            getString(R.string.browser_speed_fast)
        )
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentSpeed = prefs.getInt(KEY_CURSOR_SPEED, 1)

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.browser_cursor_speed)
            .setSingleChoiceItems(speeds, currentSpeed) { dialog, which ->
                prefs.edit().putInt(KEY_CURSOR_SPEED, which).apply()
                currentSpeedMultiplier = when (which) {
                    0 -> 0.65f
                    2 -> 1.5f
                    else -> 1.0f
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadUrl(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return
        if (isWebFullscreen) {
            disableWebFullscreen()
        }
        hideKeyboard()
        hasAutoFullscreened = false
        hideHomeDashboard()
        val target = UrlNormalizer.normalize(trimmed)
        urlInput.setText(target)
        progressBar.visibility = View.VISIBLE
        webView.loadUrl(target)
    }

    private fun updateMouseModeButton() {
        val isCursor = cursor.mode == CursorController.Mode.CURSOR
        if (isCursor) {
            btnTopMouseMode.setColorFilter(Color.parseColor("#00E5FF"))
            btnActionMouse.text = getString(R.string.browser_mode_cursor_short)
            btnActionMouse.setTextColor(Color.parseColor("#00E5FF"))
        } else {
            btnTopMouseMode.setColorFilter(Color.parseColor("#94A3B8"))
            btnActionMouse.text = getString(R.string.browser_mode_focus_short)
            btnActionMouse.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun showStreamDetected(request: PlaybackRequest) {
        btnPlayStream.visibility = View.VISIBLE
        btnActionPlay.visibility = View.VISIBLE
        floatingPlayChip.visibility = View.VISIBLE
        if (!isWebFullscreen) {
            showTopBar()
        }
        if (!hasAutoFullscreened) {
            hasAutoFullscreened = true
            enableWebFullscreen()
        }
    }

    private fun enableWebFullscreen() {
        val js = """
            (function() {
                try {
                    var styleId = 'tvapp-fullscreen-style';
                    if (!document.getElementById(styleId)) {
                        var st = document.createElement('style');
                        st.id = styleId;
                        st.innerHTML = `
                            .tvapp-web-fullscreen {
                                position: fixed !important;
                                top: 0 !important;
                                left: 0 !important;
                                width: 100vw !important;
                                height: 100vh !important;
                                max-width: 100vw !important;
                                max-height: 100vh !important;
                                z-index: 2147483647 !important;
                                background: #000 !important;
                                margin: 0 !important;
                                padding: 0 !important;
                                border: none !important;
                            }
                            .tvapp-web-fullscreen iframe, .tvapp-web-fullscreen video {
                                width: 100% !important;
                                height: 100% !important;
                                max-width: 100% !important;
                                max-height: 100% !important;
                                border: none !important;
                                object-fit: contain !important;
                            }
                            html.tvapp-lock-body, body.tvapp-lock-body {
                                overflow: hidden !important;
                            }
                        `;
                        document.head.appendChild(st);
                    }

                    var candidateSelectors = [
                        '.video-player-container-here',
                        '.player-container',
                        '#videoplayer',
                        '#player',
                        '.jwplayer',
                        '.video-js',
                        '.plyr',
                        'iframe[src*="embed"]',
                        'iframe[src*="video"]',
                        'iframe[src*="player"]',
                        'iframe[allowfullscreen]',
                        'div[class*="player"]',
                        'div[id*="player"]',
                        'iframe',
                        'video'
                    ];

                    var target = null;
                    for (var i = 0; i < candidateSelectors.length; i++) {
                        var el = document.querySelector(candidateSelectors[i]);
                        if (el && el.offsetWidth > 100 && el.offsetHeight > 80) {
                            target = el;
                            break;
                        }
                    }
                    if (!target) {
                        target = document.querySelector('iframe') || document.querySelector('video');
                    }

                    if (target) {
                        target.classList.add('tvapp-web-fullscreen');
                        document.documentElement.classList.add('tvapp-lock-body');
                        document.body.classList.add('tvapp-lock-body');

                        var playBtn = target.querySelector('.play-that-video, .play-icon, button[class*="play"], [aria-label*="play" i], .jw-display-icon-display, .jw-icon-playback');
                        if (playBtn) {
                            playBtn.click();
                        }

                        var vid = target.tagName === 'VIDEO' ? target : target.querySelector('video');
                        if (vid) {
                            try { vid.play(); } catch(e){}
                            try { if (vid.webkitEnterFullscreen) vid.webkitEnterFullscreen(); } catch(e){}
                        }
                        return 'SUCCESS';
                    }
                    return 'NO_TARGET';
                } catch(err) {
                    return 'ERROR: ' + err.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) {
            isWebFullscreen = true
            topBar.visibility = View.GONE
            cursor.stopMove()
            webView.requestFocus()
            // Sanal medya oynat komutu gonder
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            Toast.makeText(this, R.string.browser_fullscreen_enabled, Toast.LENGTH_SHORT).show()
        }
    }

    private fun disableWebFullscreen() {
        val js = """
            (function() {
                try {
                    var elements = document.querySelectorAll('.tvapp-web-fullscreen');
                    elements.forEach(function(el) {
                        el.classList.remove('tvapp-web-fullscreen');
                    });
                    document.documentElement.classList.remove('tvapp-lock-body');
                    document.body.classList.remove('tvapp-lock-body');
                    return 'RESTORED';
                } catch(e) {
                    return 'ERROR: ' + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) {
            isWebFullscreen = false
            topBar.visibility = View.VISIBLE
            Toast.makeText(this, R.string.browser_fullscreen_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
            return
        }
        if (isWebFullscreen) {
            disableWebFullscreen()
            return
        }
        super.onBackPressed()
    }

    private fun playDetectedStream(request: PlaybackRequest) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_VOD_STREAM_URL, request.url)
            val title = if (request.title.isNotBlank() && !request.title.startsWith("http")) {
                request.title
            } else {
                webView.title?.takeIf { it.isNotBlank() } ?: "Web Video"
            }
            putExtra(MainActivity.EXTRA_VOD_TITLE, title)
            request.headers["Referer"]?.let { putExtra(EXTRA_VOD_REFERRER, it) }
            request.headers["Origin"]?.let { putExtra(EXTRA_VOD_ORIGIN, it) }
            request.headers["User-Agent"]?.let { putExtra(EXTRA_VOD_USER_AGENT, it) }
            request.headers["Cookie"]?.let { putExtra(EXTRA_VOD_COOKIE, it) }
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.stopLoading()
        webView.destroy()
    }

    companion object {
        const val EXTRA_URL = "extra_browser_url"
        const val EXTRA_VOD_REFERRER = "com.tvapp.livetv.extra.VOD_REFERRER"
        const val EXTRA_VOD_ORIGIN = "com.tvapp.livetv.extra.VOD_ORIGIN"
        const val EXTRA_VOD_USER_AGENT = "com.tvapp.livetv.extra.VOD_USER_AGENT"
        const val EXTRA_VOD_COOKIE = "com.tvapp.livetv.extra.VOD_COOKIE"

        private const val PREFS_NAME = "tv_browser_prefs"
        private const val KEY_CURSOR_SPEED = "cursor_speed"
        private const val KEY_BLOCK_ADS = "block_ads"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_FAVORITES = "browser_favorites"

        private const val DEFAULT_HOME_URL = "https://www.google.com"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
}
