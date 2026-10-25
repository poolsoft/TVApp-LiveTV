package com.tvapp.livetv.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.tvapp.livetv.MainActivity
import com.tvapp.livetv.R

/**
 * Android TV icin sanal imlec, D-pad gezinme, ayarlar ve video akis yakalama destekli web tarayicisi.
 */
class TvBrowserActivity : Activity() {

    private lateinit var layout: CursorLayout
    private lateinit var webView: WebView
    private lateinit var topBar: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var btnNavBack: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnGo: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnPlayStream: Button
    private lateinit var floatingPlayChip: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var cursor: CursorController
    private lateinit var sniffer: StreamSniffer

    private var currentPlaybackRequest: PlaybackRequest? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var currentSpeedMultiplier = 1.0f

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

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_HOME_URL
        loadUrl(initialUrl)
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
        btnNavBack = findViewById(R.id.btn_browser_nav_back)
        btnReload = findViewById(R.id.btn_browser_reload)
        btnGo = findViewById(R.id.btn_browser_go)
        btnSettings = findViewById(R.id.btn_browser_settings)
        btnPlayStream = findViewById(R.id.btn_play_stream)
        floatingPlayChip = findViewById(R.id.browser_floating_play_chip)
        progressBar = findViewById(R.id.browser_progress_bar)

        layout.topBar = topBar
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
            },
            onLoaded = { _ ->
                progressBar.visibility = View.GONE
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
        }
    }

    private fun setupCursor() {
        cursor = CursorController(webView, speedMultiplier = { currentSpeedMultiplier }) {
            layout.invalidate()
        }

        layout.webView = webView
        layout.cursor = cursor
        layout.playChip = floatingPlayChip
        layout.onChipClick = {
            currentPlaybackRequest?.let { playDetectedStream(it) }
        }
        layout.onBack = {
            if (webView.canGoBack()) {
                webView.goBack()
                true
            } else {
                false
            }
        }
        layout.onExitPage = {
            finish()
        }

        // Mod degistiginde (OK uzun basilinca) TV bildirimini goster
        layout.onModeChanged = { mode ->
            val msgRes = if (mode == CursorController.Mode.CURSOR) {
                R.string.browser_mode_cursor
            } else {
                R.string.browser_mode_focus
            }
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
        }

        // Fare en tepeye ulastiginda dogrudan ust bara odaklan
        cursor.onReachedTop = {
            layout.enterTopBar()
        }

        // Ust bar yerlestiginde farenin ust sinirini belirle ve imleci ortala
        topBar.post {
            val limit = layout.getTopBarBottomLimit()
            cursor.minY = limit
            cursor.center(webView.width, webView.height)
        }
    }

    private fun setupListeners() {
        btnNavBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        btnNavBack.setOnLongClickListener {
            finish()
            true
        }

        btnReload.setOnClickListener {
            webView.reload()
        }

        btnGo.setOnClickListener {
            val text = urlInput.text.toString()
            loadUrl(text)
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadUrl(urlInput.text.toString())
                true
            } else {
                false
            }
        }

        btnPlayStream.setOnClickListener {
            currentPlaybackRequest?.let { playDetectedStream(it) }
                ?: Toast.makeText(this, R.string.browser_no_stream_yet, Toast.LENGTH_SHORT).show()
        }

        floatingPlayChip.setOnClickListener {
            currentPlaybackRequest?.let { playDetectedStream(it) }
        }
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
                    3 -> {
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
        val target = UrlNormalizer.normalize(rawInput)
        urlInput.setText(target)
        progressBar.visibility = View.VISIBLE
        webView.loadUrl(target)
    }

    private fun showStreamDetected(request: PlaybackRequest) {
        btnPlayStream.visibility = View.VISIBLE
        floatingPlayChip.visibility = View.VISIBLE
        Toast.makeText(this, R.string.browser_stream_detected, Toast.LENGTH_SHORT).show()
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
        finish()
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

        private const val DEFAULT_HOME_URL = "https://www.google.com"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
}
