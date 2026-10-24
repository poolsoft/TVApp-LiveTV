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

    fun isDirectStream(url: String): Boolean {
        val clean = url.trim()
        if (clean.contains("playlist.m3u8", ignoreCase = true)) return true
        val noQuery = clean.substringBefore("?")
        return DIRECT_STREAM_PATTERN.matcher(noQuery).matches()
    }

    suspend fun resolve(rawUrl: String, context: android.content.Context? = null): String = withContext(Dispatchers.IO) {
        val targetUrl = rawUrl.trim()
        if (targetUrl.isEmpty()) return@withContext targetUrl
        if (isDirectStream(targetUrl)) return@withContext targetUrl

        // 1. Fast path: Not a direct stream; attempt to resolve via HTTP GET and HTML regex
        val fastResolved = runCatching {
            extractStreamFromPage(targetUrl, depth = 0)
        }.getOrNull()

        if (!fastResolved.isNullOrBlank() && isDirectStream(fastResolved)) {
            return@withContext fastResolved
        }

        // 2. Slow/dynamic path: If HTTP didn't resolve and context is available, run Headless WebView sniffer
        if (context != null) {
            val webViewResolved = runCatching {
                resolveViaHeadlessWebView(context, targetUrl)
            }.getOrNull()
            if (!webViewResolved.isNullOrBlank()) {
                return@withContext webViewResolved
            }
        }

        fastResolved ?: targetUrl
    }

    private suspend fun resolveViaHeadlessWebView(context: android.content.Context, pageUrl: String): String? =
        withContext(Dispatchers.Main) {
            val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
            var webView: android.webkit.WebView? = null
            try {
                val appCtx = context.applicationContext
                val wv = android.webkit.WebView(appCtx)
                webView = wv
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = USER_AGENT
                }

                wv.webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): android.webkit.WebResourceResponse? {
                        val reqUrl = request?.url?.toString()
                        if (!reqUrl.isNullOrEmpty()) {
                            val lower = reqUrl.lowercase()
                            if (lower.contains(".m3u8") || (lower.contains(".mp4") && !lower.contains("ad") && !lower.contains("analytic"))) {
                                deferred.complete(reqUrl)
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Inject basic sniffer script to trigger hidden video/iframe sources
                        view?.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    var v = document.querySelector('video');
                                    if (v && v.src && v.src.indexOf('http') === 0) {
                                        console.log('dom_stream_detected:' + v.src);
                                    }
                                } catch(e) {}
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }

                wv.webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        val text = message?.message().orEmpty()
                        if (text.startsWith("dom_stream_detected:")) {
                            val stream = text.removePrefix("dom_stream_detected:").trim()
                            if (stream.startsWith("http")) {
                                deferred.complete(stream)
                            }
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

        // 1. Check for JSON-LD VideoObject contentUrl
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

        // 2. Look for direct .m3u8 stream links inside HTML
        val m3u8Matcher = M3U8_REGEX.matcher(html)
        if (m3u8Matcher.find()) {
            val stream = m3u8Matcher.group(0)
            if (!stream.isNullOrBlank()) {
                return stream.replace("\\/", "/")
            }
        }

        // 3. Look for direct .mp4 video links inside HTML
        val mp4Matcher = MP4_REGEX.matcher(html)
        if (mp4Matcher.find()) {
            val stream = mp4Matcher.group(0)
            if (!stream.isNullOrBlank()) {
                return stream.replace("\\/", "/")
            }
        }

        // 4. Look for video iframe embeds
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

    private fun fetchHtml(urlString: String): String? = runCatching {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            instanceFollowRedirects = true
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
}
