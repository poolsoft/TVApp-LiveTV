package com.tvapp.livetv.browser

import android.annotation.SuppressLint
import android.app.Activity
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.tvapp.livetv.MainActivity
import com.tvapp.livetv.R

/**
 * Android TV icin sanal imlec ve video akis yakalama (stream sniffing) destekli web tarayicisi.
 */
class TvBrowserActivity : Activity() {

    private lateinit var layout: CursorLayout
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var btnNavBack: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnGo: Button
    private lateinit var btnPlayStream: Button
    private lateinit var floatingPlayChip: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var cursor: CursorController
    private lateinit var sniffer: StreamSniffer

    private var currentPlaybackRequest: PlaybackRequest? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_browser)

        initViews()
        setupSniffer()
        setupWebView()
        setupCursor()
        setupListeners()

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_HOME_URL
        loadUrl(initialUrl)
    }

    private fun initViews() {
        layout = findViewById(R.id.browser_cursor_layout)
        webView = findViewById(R.id.browser_web_view)
        urlInput = findViewById(R.id.edit_browser_url)
        btnNavBack = findViewById(R.id.btn_browser_nav_back)
        btnReload = findViewById(R.id.btn_browser_reload)
        btnGo = findViewById(R.id.btn_browser_go)
        btnPlayStream = findViewById(R.id.btn_play_stream)
        floatingPlayChip = findViewById(R.id.browser_floating_play_chip)
        progressBar = findViewById(R.id.browser_progress_bar)
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
            userAgentString = DESKTOP_USER_AGENT
        }

        webView.webViewClient = SniffingWebViewClient(
            sniffer = sniffer,
            onNavigate = { url ->
                urlInput.setText(url)
            },
            onLoaded = { url ->
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
        cursor = CursorController(webView) {
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

        // Ilk acilista imleci ekranin ortasina konumlandir
        webView.post {
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

        private const val DEFAULT_HOME_URL = "https://www.google.com"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }
}
