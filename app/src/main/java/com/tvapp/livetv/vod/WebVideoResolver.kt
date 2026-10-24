package com.tvapp.livetv.vod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Resolves playable video stream URLs (m3u8, mp4) from raw video URLs or webpage URLs.
 * Handles platforms like Kanal D, NowTV, FilmModu, Dizibox, etc.
 */
object WebVideoResolver {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val DIRECT_STREAM_PATTERN =
        Pattern.compile(".*\\.(m3u8|mp4|mkv|ts|mpd)(\\?.*)?$", Pattern.CASE_INSENSITIVE)

    private val M3U8_REGEX =
        Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE)

    private val MP4_REGEX =
        Pattern.compile("https?://[^\\s\"'<>]+\\.mp4[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE)

    private val JSON_LD_REGEX =
        Pattern.compile("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

    private val CONTENT_URL_REGEX =
        Pattern.compile("\"contentUrl\"\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    private val EMBED_URL_REGEX =
        Pattern.compile("\"embedUrl\"\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    private val IFRAME_SRC_REGEX =
        Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    private val DATA_PV_REGEX =
        Pattern.compile("data-pv=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    private val CORE_JS_REGEX =
        Pattern.compile("src=[\"'](https?://[^\"']+/assets/js/core\\.js)[\"']", Pattern.CASE_INSENSITIVE)

    private val STREAM_PROP_REGEX =
        Pattern.compile("\"stream\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)

    fun isDirectStream(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty()) return false
        val lower = clean.lowercase()
        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("stream.php") ||
            lower.contains("/hls/") || lower.contains("playlist.m3u8")
        ) {
            return true
        }
        val noQuery = clean.substringBefore("?")
        return DIRECT_STREAM_PATTERN.matcher(noQuery).matches()
    }

    private fun isNetworkMediaCandidate(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty()) return false
        val lower = clean.lowercase()

        // Exclude static assets, analytics, ads
        if (lower.contains("google-analytics") || lower.contains("googletagmanager") ||
            lower.contains("facebook.net") || lower.contains("doubleclick") ||
            lower.contains("adnxs") || lower.contains("clarity.ms") || lower.contains("hotjar") ||
            lower.contains("favicon.ico") || lower.endsWith(".css") || lower.endsWith(".png") ||
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") ||
            lower.endsWith(".svg") || lower.endsWith(".woff") || lower.endsWith(".woff2") ||
            lower.endsWith(".ttf") || (lower.endsWith(".js") && !lower.contains("s.php"))
        ) {
            return false
        }

        // Check for media extensions or streaming paths
        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("stream.php") ||
            lower.contains("/hls/") || lower.contains("/dash/") || lower.contains("playlist.m3u8") ||
            lower.contains("master.m3u8") || lower.contains("manifest")
        ) {
            return true
        }

        // Direct video files (excluding short ad videos)
        if (lower.contains(".mp4") && !lower.contains("ad_") && !lower.contains("banner") && !lower.contains("preview_")) {
            return true
        }

        return false
    }

    data class ResolvedStream(
        val streamUrl: String,
        val userAgent: String? = USER_AGENT,
        val referrer: String? = null,
        val origin: String? = null,
        val isPlayable: Boolean = true,
    )

    suspend fun resolveStream(rawUrl: String, context: android.content.Context? = null): ResolvedStream = withContext(Dispatchers.IO) {
        val targetUrl = rawUrl.trim()
        if (targetUrl.isEmpty()) return@withContext ResolvedStream(targetUrl, isPlayable = false)
        if (isDirectStream(targetUrl)) return@withContext ResolvedStream(targetUrl, isPlayable = true)

        val pageUri = runCatching { android.net.Uri.parse(targetUrl) }.getOrNull()
        val pageOrigin = pageUri?.let { "${it.scheme}://${it.host}" }

        // 1. Fast path: Check for quick static links in page HTML (e.g. data-pv, json-ld, direct m3u8)
        val fastResolved = runCatching {
            extractStreamFromPage(targetUrl, depth = 0)
        }.getOrNull()

        if (!fastResolved.isNullOrBlank() && isDirectStream(fastResolved)) {
            return@withContext ResolvedStream(
                streamUrl = fastResolved,
                referrer = targetUrl,
                origin = pageOrigin,
                isPlayable = true,
            )
        }

        // 2. Dynamic stream sniffer path: Use Headless WebView network interception
        if (context != null) {
            val webViewResolved = runCatching {
                resolveViaHeadlessWebView(context, targetUrl)
            }.getOrNull()
            if (!webViewResolved.isNullOrBlank() && isDirectStream(webViewResolved)) {
                return@withContext ResolvedStream(
                    streamUrl = webViewResolved,
                    referrer = targetUrl,
                    origin = pageOrigin,
                    isPlayable = true,
                )
            }
        }

        val fallback = fastResolved ?: targetUrl
        ResolvedStream(
            streamUrl = fallback,
            referrer = targetUrl,
            origin = pageOrigin,
            isPlayable = isDirectStream(fallback),
        )
    }

    suspend fun resolve(rawUrl: String, context: android.content.Context? = null): String =
        resolveStream(rawUrl, context).streamUrl

    private suspend fun resolveViaHeadlessWebView(context: android.content.Context, pageUrl: String): String? =
        withContext(Dispatchers.Main) {
            val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
            var webView: android.webkit.WebView? = null
            try {
                val appCtx = context.applicationContext
                val wv = android.webkit.WebView(appCtx)
                webView = wv
                wv.layout(0, 0, 1920, 1080)
                wv.onResume()
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = USER_AGENT
                    allowFileAccess = true
                    allowContentAccess = true
                    javaScriptCanOpenWindowsAutomatically = true
                }

                fun onSniffedUrl(url: String?) {
                    if (url.isNullOrBlank() || deferred.isCompleted) return
                    val clean = url.trim()
                    if (isNetworkMediaCandidate(clean)) {
                        deferred.complete(clean)
                    }
                }

                val universalHookScript = """
                    (function() {
                        if (window.__sniff_installed) return;
                        window.__sniff_installed = true;

                        function report(u) {
                            if (u && typeof u === 'string' && u.indexOf('http') === 0) {
                                console.log('net_stream_sniff:' + u);
                            }
                        }

                        // 1. Hook fetch
                        var origFetch = window.fetch;
                        if (origFetch) {
                            window.fetch = function() {
                                var arg = arguments[0];
                                if (typeof arg === 'string') {
                                    report(arg);
                                } else if (arg && arg.url) {
                                    report(arg.url);
                                }
                                return origFetch.apply(this, arguments);
                            };
                        }

                        // 2. Hook XMLHttpRequest
                        var origOpen = XMLHttpRequest.prototype.open;
                        XMLHttpRequest.prototype.open = function(method, url) {
                            report(url);
                            return origOpen.apply(this, arguments);
                        };

                        // 3. Hook HTMLMediaElement src
                        try {
                            var mediaProto = HTMLMediaElement.prototype;
                            var origSrcDesc = Object.getOwnPropertyDescriptor(mediaProto, 'src');
                            if (origSrcDesc && origSrcDesc.set) {
                                Object.defineProperty(mediaProto, 'src', {
                                    set: function(val) {
                                        report(val);
                                        return origSrcDesc.set.apply(this, arguments);
                                    },
                                    get: origSrcDesc.get,
                                    configurable: true
                                });
                            }
                        } catch(e) {}

                        // 4. Autoplay trigger helper
                        function triggerAutoplay() {
                            var vids = document.querySelectorAll('video, audio');
                            for (var i = 0; i < vids.length; i++) {
                                try {
                                    if (vids[i].src) report(vids[i].src);
                                    var sources = vids[i].querySelectorAll('source');
                                    for (var s = 0; s < sources.length; s++) {
                                        if (sources[s].src) report(sources[s].src);
                                    }
                                    vids[i].muted = true;
                                    vids[i].play();
                                } catch(e){}
                            }
                            var buttons = document.querySelectorAll(
                                '.play, .vjs-big-play-button, .plyr__control, button[aria-label*="Play"], button[aria-label*="Oynat"], [data-player], .jw-display-icon-container, [class*="play"], [id*="play"]'
                            );
                            for (var b = 0; b < buttons.length; b++) {
                                try { buttons[b].click(); } catch(e){}
                            }
                        }

                        triggerAutoplay();
                        var count = 0;
                        var timer = setInterval(function() {
                            count++;
                            triggerAutoplay();
                            if (count > 10) clearInterval(timer);
                        }, 500);
                    })();
                """.trimIndent()

                wv.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onReceivedSslError(
                        view: android.webkit.WebView?,
                        handler: android.webkit.SslErrorHandler?,
                        error: android.net.http.SslError?,
                    ) {
                        handler?.proceed()
                    }

                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): Boolean {
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): android.webkit.WebResourceResponse? {
                        onSniffedUrl(request?.url?.toString())
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onLoadResource(view: android.webkit.WebView?, url: String?) {
                        super.onLoadResource(view, url)
                        onSniffedUrl(url)
                    }

                    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view?.evaluateJavascript(universalHookScript, null)
                    }

                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(universalHookScript, null)
                    }
                }

                wv.webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        val text = message?.message().orEmpty()
                        if (text.startsWith("net_stream_sniff:")) {
                            val stream = text.removePrefix("net_stream_sniff:").trim()
                            onSniffedUrl(stream)
                        }
                        return super.onConsoleMessage(message)
                    }
                }

                wv.loadUrl(pageUrl)

                kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    deferred.await()
                }
            } finally {
                runCatching {
                    webView?.stopLoading()
                    webView?.destroy()
                }
            }
        }

    private fun extractStreamFromPage(pageUrl: String, depth: Int): String? {
        if (depth > 2) return null
        val html = fetchHtml(pageUrl) ?: return null

        // 1. Check for Pilavyer data-pv & s.php
        val pvMatcher = DATA_PV_REGEX.matcher(html)
        if (pvMatcher.find()) {
            val slug = pvMatcher.group(1)?.trim()
            if (!slug.isNullOrEmpty()) {
                val host = if (html.contains("play2.pilavyerplay.top")) {
                    "https://play2.pilavyerplay.top"
                } else {
                    val coreMatcher = CORE_JS_REGEX.matcher(html)
                    if (coreMatcher.find()) {
                        coreMatcher.group(1)?.substringBefore("/assets/js/core.js") ?: "https://play2.pilavyerplay.top"
                    } else "https://play2.pilavyerplay.top"
                }
                val sUrl = "$host/assets/js/s.php?s=$slug"
                val sHtml = fetchHtml(sUrl, referrer = pageUrl)
                if (!sHtml.isNullOrBlank()) {
                    val streamMatcher = STREAM_PROP_REGEX.matcher(sHtml)
                    if (streamMatcher.find()) {
                        val stream = streamMatcher.group(1)?.replace("\\/", "/")?.replace("\\u0026", "&")
                        if (!stream.isNullOrBlank()) return stream
                    }
                }
            }
        }

        // 2. Check for JSON-LD VideoObject contentUrl
        val jsonLdMatcher = JSON_LD_REGEX.matcher(html)
        while (jsonLdMatcher.find()) {
            val jsonText = jsonLdMatcher.group(1).orEmpty()
            val contentMatcher = CONTENT_URL_REGEX.matcher(jsonText)
            if (contentMatcher.find()) {
                val stream = contentMatcher.group(1)
                if (!stream.isNullOrBlank() && stream.startsWith("http")) {
                    return stream.replace("\\/", "/")
                }
            }
            val embedMatcher = EMBED_URL_REGEX.matcher(jsonText)
            if (embedMatcher.find()) {
                val embedUrl = embedMatcher.group(1)?.replace("\\/", "/")
                if (!embedUrl.isNullOrBlank() && embedUrl.startsWith("http")) {
                    if (isDirectStream(embedUrl)) return embedUrl
                    val nested = extractStreamFromPage(embedUrl, depth + 1)
                    if (nested != null) return nested
                }
            }
        }

        // 3. Look for direct .m3u8 stream links inside HTML
        val m3u8Matcher = M3U8_REGEX.matcher(html)
        if (m3u8Matcher.find()) {
            val stream = m3u8Matcher.group(0)
            if (!stream.isNullOrBlank()) {
                return stream.replace("\\/", "/")
            }
        }

        // 4. Look for direct .mp4 video links inside HTML
        val mp4Matcher = MP4_REGEX.matcher(html)
        if (mp4Matcher.find()) {
            val stream = mp4Matcher.group(0)
            if (!stream.isNullOrBlank()) {
                return stream.replace("\\/", "/")
            }
        }

        // 5. Look for video iframe embeds
        val iframeMatcher = IFRAME_SRC_REGEX.matcher(html)
        while (iframeMatcher.find()) {
            val iframeSrc = iframeMatcher.group(1)
            if (!iframeSrc.isNullOrBlank() && iframeSrc.startsWith("http")) {
                if (isDirectStream(iframeSrc)) return iframeSrc
                val nested = extractStreamFromPage(iframeSrc, depth + 1)
                if (nested != null) return nested
            }
        }

        return null
    }

    private fun fetchHtml(urlString: String, referrer: String? = null): String? = runCatching {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            if (!referrer.isNullOrBlank()) {
                setRequestProperty("Referer", referrer)
            }
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            instanceFollowRedirects = true
            if (this is javax.net.ssl.HttpsURLConnection) {
                sslSocketFactory = trustAllSslContext.socketFactory
                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) return null
            conn.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private val trustAllSslContext: javax.net.ssl.SSLContext by lazy {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
            }
        )
        javax.net.ssl.SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }
    }
}
