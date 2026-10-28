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

    /** Headless sniffer en fazla kac aday toplar; fazlasi kesilir. */
    private const val MAX_HEADLESS_CANDIDATES = 6
    /** Headless sayfa bekleme ust siniri; Var olan davranis korunur (10 sn). */
    private const val HEADLESS_TIMEOUT_MS = 10_000L

    private val SEGMENT_WORDS = setOf(
        "init", "dashinit", "seg", "segment", "chunk", "frag", "fragment",
    )
    private val SEGMENT_PREFIX_ALL =
        Regex("^(init|seg|segment|chunk|frag|fragment)\\d+$", RegexOption.IGNORE_CASE)

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
        Pattern.compile(
            "<iframe[^>]+?(?:src|data-src|data-lazy-src|data-href)\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE,
        )

    private val DATA_PV_REGEX =
        Pattern.compile("data-pv=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    private val CORE_JS_REGEX =
        Pattern.compile("src=[\"'](https?://[^\"']+/assets/js/core\\.js)[\"']", Pattern.CASE_INSENSITIVE)

    private val STREAM_PROP_REGEX =
        Pattern.compile("\"stream\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)

    fun isDirectStream(url: String): Boolean = VodUrlClassifier.isDirectStream(url)

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
            lower.contains("master.m3u8") || lower.contains("manifest") ||
            lower.contains("master.txt") || lower.contains("playlist.txt")
        ) {
            return true
        }

        // Direct video files (excluding short ad videos)
        if ((lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".webm") || lower.contains(".ts")) &&
            !lower.contains("ad_") && !lower.contains("banner") && !lower.contains("preview_")) {
            return true
        }

        return false
    }

    data class ResolvedStream(
        val streamUrl: String,
        val userAgent: String? = null,
        val referrer: String? = null,
        val origin: String? = null,
        val cookie: String? = null,
        val isPlayable: Boolean = true,
    )

    fun originOf(url: String): String? {
        val uri = try { java.net.URI(url) } catch (e: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    suspend fun resolveStream(rawUrl: String, context: android.content.Context? = null): ResolvedStream = withContext(Dispatchers.IO) {
        val targetUrl = rawUrl.trim()
        if (targetUrl.isEmpty()) return@withContext ResolvedStream(targetUrl, isPlayable = false)
        if (isDirectStream(targetUrl)) return@withContext ResolvedStream(targetUrl, userAgent = null, isPlayable = true)

        val pageOrigin = originOf(targetUrl)

        // 1. Fast path: Check for quick static links in page HTML (e.g. data-pv, json-ld, direct m3u8)
        val fastResolved = runCatching {
            extractStreamFromPage(targetUrl, depth = 0)
        }.getOrNull()

        if (!fastResolved.isNullOrBlank() && isDirectStream(fastResolved)) {
            val cookie = runCatching { android.webkit.CookieManager.getInstance().getCookie(fastResolved) }.getOrNull()
            return@withContext ResolvedStream(
                streamUrl = fastResolved,
                userAgent = USER_AGENT,
                referrer = targetUrl,
                origin = pageOrigin,
                cookie = cookie,
                isPlayable = true,
            )
        }

        // 2. Dynamic stream sniffer path: Use attached WebView with network interception
        if (context != null) {
            val webViewResolved = runCatching {
                resolveViaHeadlessWebView(context, targetUrl)
            }.getOrNull()
            if (webViewResolved != null && webViewResolved.isPlayable) {
                return@withContext webViewResolved
            }
        }

        val fallback = fastResolved ?: targetUrl
        val fallbackCookie = runCatching { android.webkit.CookieManager.getInstance().getCookie(fallback) }.getOrNull()
        ResolvedStream(
            streamUrl = fallback,
            userAgent = if (fastResolved != null) USER_AGENT else null,
            referrer = targetUrl,
            origin = pageOrigin,
            cookie = fallbackCookie,
            isPlayable = isDirectStream(fallback),
        )
    }

    suspend fun resolve(rawUrl: String, context: android.content.Context? = null): String =
        resolveStream(rawUrl, context).streamUrl

    /**
     * Sayfa URL'sinden tum oynatilabilir adaylari dondurur.
     *
     * Siralama: once statik cikarma (ucuz), sonra headless sniffer (pahali).
     * Coklu alternatif kaynak (Close/Rapidrame tarzi) bu listede gorunur;
     * cagiran tek adayda direkt oynatir, cok adayda secim sunar.
     *
     * Cozulmus CDN URL'leri omurludur; DB'ye yazilmaz, her oynatmada bu
     * fonksiyon taze cagrilir. Saklanan her zaman sayfa URL'sidir.
     */
    suspend fun resolveCandidates(
        rawUrl: String,
        context: android.content.Context? = null,
    ): List<ResolvedStream> = withContext(Dispatchers.IO) {
        val targetUrl = rawUrl.trim()
        if (targetUrl.isEmpty()) return@withContext emptyList()
        if (isDirectStream(targetUrl)) {
            return@withContext listOf(ResolvedStream(targetUrl, userAgent = null, isPlayable = true))
        }
        val pageOrigin = originOf(targetUrl)
        val candidates = linkedMapOf<String, ResolvedStream>()

        val staticFound = runCatching { collectStaticStreams(targetUrl, depth = 0) }.getOrNull().orEmpty()
        for (streamUrl in staticFound) {
            if (!candidates.containsKey(streamUrl)) {
                val cookie = runCatching {
                    android.webkit.CookieManager.getInstance().getCookie(streamUrl)
                }.getOrNull()
                candidates[streamUrl] = ResolvedStream(
                    streamUrl = streamUrl,
                    userAgent = USER_AGENT,
                    referrer = targetUrl,
                    origin = pageOrigin,
                    cookie = cookie,
                    isPlayable = true,
                )
            }
            if (candidates.size >= 6) break
        }
        if (candidates.isNotEmpty()) return@withContext candidates.values.toList()

        if (context != null) {
            val sniffed = runCatching {
                collectViaHeadlessWebView(context, targetUrl)
            }.getOrNull().orEmpty()
            for (item in sniffed) {
                if (!candidates.containsKey(item.streamUrl)) {
                    candidates[item.streamUrl] = item
                }
                if (candidates.size >= 6) break
            }
        }
        candidates.values.toList()
    }

    private fun collectStaticStreams(pageUrl: String, depth: Int): List<String> {
        if (depth > 2) return emptyList()
        val html = fetchHtml(pageUrl) ?: return emptyList()
        val found = linkedSetOf<String>()

        val extraction = runCatching { VodMetadataExtractor.extract(html, pageUrl) }.getOrNull()
        extraction?.streamUrl?.takeIf { isDirectStream(it) }?.let { found.add(it) }

        // Site-ozel hizli dallar korunur (Pilavyer s.php vb.)
        extractStreamFromPage(pageUrl, depth)?.takeIf { isDirectStream(it) }?.let { found.add(it) }

        val embeds = linkedSetOf<String>()
        extraction?.embedUrls.orEmpty().forEach { embeds.add(it) }
        IFRAME_SRC_REGEX.matcher(html).let { m ->
            while (m.find()) {
                val raw = m.group(1).orEmpty().trim()
                if (raw.startsWith("http")) embeds.add(raw)
                if (embeds.size >= 8) break
            }
        }
        for (embed in embeds) {
            if (isDirectStream(embed)) {
                found.add(embed)
            } else {
                collectStaticStreams(embed, depth + 1).forEach { found.add(it) }
            }
            if (found.size >= 6) break
        }
        return found.toList()
    }

    /**
     * Headless WebView ile sayfayi acar ve gordugu tum oynatilabilir adaylari toplar.
     *
     * Tek sonuc donduren eski davranisin yerine gecmistir: sahnesen (`handleVod`)
     * artik cok kaynakli sayfalarda (Close/Rapidrame tarzi) secim sunabilir.
     *
     * Kurallar:
     *  - Reklam/analiz/statik dosya adaylari atlanir.
     *  - Guclu bir aday (HLS/DASH manifest) bulunur bekleyebilen adaylar covalr,
     *    kucuk poster parcalari beklemek icin tam 10 saniye tüketilmez.
     *  - Reklam hostlari, canli yayin degil de uplink/segment gibi gürültüler atlanir.
     *  - Sonuc her zaman bosluklu olmayan URL listesidir; sira: guclu aday once.
     */
    private suspend fun collectViaHeadlessWebView(
        context: android.content.Context,
        pageUrl: String,
    ): List<ResolvedStream> = withContext(Dispatchers.Main) {
        val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
        val collected = linkedMapOf<String, ResolvedStream>()
        var webView: android.webkit.WebView? = null
        var attachedParent: android.view.ViewGroup? = null
        try {
            val appCtx = context.applicationContext
            val wv = android.webkit.WebView(appCtx)
            webView = wv

            // Attach to Activity DecorView invisibly so Chrome engine enables full layout,
            // DOM rendering and video autoplay without background throttle
            val activity = context as? android.app.Activity
            val decor = activity?.window?.decorView as? android.view.ViewGroup
            if (decor != null) {
                wv.alpha = 0.01f
                decor.addView(wv, android.view.ViewGroup.LayoutParams(1920, 1080))
                attachedParent = decor
            } else {
                wv.layout(0, 0, 1920, 1080)
            }

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

            fun onSniffedUrl(url: String?, reqReferrer: String?) {
                if (url.isNullOrBlank() || deferred.isCompleted) return
                val clean = url.trim()
                if (isNetworkMediaCandidate(clean)) {
                    if (collected.containsKey(clean)) return
                    val cookie = runCatching { android.webkit.CookieManager.getInstance().getCookie(clean) }.getOrNull()
                    val ref = reqReferrer?.takeIf { it.isNotBlank() } ?: pageUrl
                    val orig = originOf(ref)
                    collected[clean] = ResolvedStream(
                        streamUrl = clean,
                        userAgent = USER_AGENT,
                        referrer = ref,
                        origin = orig,
                        cookie = cookie,
                        isPlayable = true,
                    )
                    if (collected.size >= MAX_HEADLESS_CANDIDATES) {
                        deferred.complete(Unit)
                        return
                    }
                    val strong = collected.values.first { it.streamUrl == clean }
                    if (isStrongCandidate(strong)) {
                        deferred.complete(Unit)
                    }
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

                        // 4. Lazy iframe aktivasyonu + autoplay trigger helper.
                        //    hdfilmcehennemi tarzi sayfalar iframe'i `data-src` ile gizler;
                        //    `src`'ye kopyalanmadan ag istegi olusmaz, sniffer bosa bekler.
                        //    Kopyalama + overlay tiklamasi embed'in yuklenmesini tetikler.
                        function activateLazyIframes() {
                            try {
                                var iframes = document.querySelectorAll('iframe[data-src], iframe[data-lazy-src], iframe[data-href]');
                                for (var i = 0; i < iframes.length; i++) {
                                    try {
                                        var fr = iframes[i];
                                        if (!fr.src || fr.src === '' || fr.src === 'about:blank') {
                                            var lazy = fr.getAttribute('data-src') || fr.getAttribute('data-lazy-src') || fr.getAttribute('data-href');
                                            if (lazy) { fr.src = lazy; }
                                        }
                                    } catch(e){}
                                }
                            } catch(e){}
                        }

                        function triggerAutoplay() {
                            activateLazyIframes();
                            var vids = document.querySelectorAll('video, audio');
                            for (var i = 0; i < vids.length; i++) {
                                try {
                                    if (vids[i].src) report(vids[i].src);
                                    var sources = vids[i].querySelectorAll('source');
                                    for (var s = 0; s < sources.length; s++) {
                                        if (sources[s].src) report(sources[s].src);
                                    }
                                    vids[i].muted = true;
                                    var p = vids[i].play();
                                    if (p && typeof p.catch === 'function') { p.catch(function(){}); }
                                } catch(e){}
                            }

                            var buttons = document.querySelectorAll(
                                '.play-that-video, .play-icon, .play-btn, .vjs-big-play-button, .jw-display-icon-container, .plyr__control--overlaid, .play, .plyr__control, button[aria-label*="Play"], button[aria-label*="Oynat"], [aria-label*="play" i], [data-player], [class*="play"], [id*="play"], .vjs-play-control, .click-to-play'
                            );
                            for (var b = 0; b < buttons.length; b++) {
                                try { buttons[b].click(); } catch(e){}
                            }

                            try {
                                var iframes2 = document.querySelectorAll('iframe');
                                for (var f = 0; f < iframes2.length; f++) {
                                    try {
                                        var doc = iframes2[f].contentDocument || iframes2[f].contentWindow.document;
                                        if (doc) {
                                            var ivs = doc.querySelectorAll('video, audio');
                                            for (var iv = 0; iv < ivs.length; iv++) {
                                                ivs[iv].muted = true;
                                                var ip = ivs[iv].play();
                                                if (ip && typeof ip.catch === 'function') { ip.catch(function(){}); }
                                            }
                                        }
                                    } catch(e){}
                                }
                            } catch(e){}
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
                    val reqUrl = request?.url?.toString()
                    val reqRef = request?.requestHeaders?.entries?.firstOrNull {
                        it.key.equals("Referer", ignoreCase = true)
                    }?.value
                    onSniffedUrl(reqUrl, reqRef)
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onLoadResource(view: android.webkit.WebView?, url: String?) {
                    super.onLoadResource(view, url)
                    onSniffedUrl(url, pageUrl)
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
                        onSniffedUrl(stream, pageUrl)
                    }
                    return super.onConsoleMessage(message)
                }
            }

            wv.loadUrl(pageUrl)

            kotlinx.coroutines.withTimeoutOrNull(HEADLESS_TIMEOUT_MS) {
                deferred.await()
            }
        } finally {
            runCatching {
                attachedParent?.removeView(webView)
                webView?.stopLoading()
                webView?.destroy()
            }
        }
        rankHeadlessCandidates(collected.values)
    }

    /**
     * Guclu aday = HLS manifest izni alan en iyi manifest; segment/poster gibi
     * gürültü icin listeyi bekletme. Tam manifest alindiginda listeyi doldur.
     */
    private fun isStrongCandidate(item: ResolvedStream): Boolean {
        val lower = item.streamUrl.lowercase()
        val isManifest = lower.contains(".m3u8") || lower.contains("master.txt") || lower.contains("playlist.txt")
        if (!isManifest) return false
        // Yayin/canli posteri ve donma adam dizileri degil, ana manifest olan aday
        val isArtifact = lower.endsWith(".jpg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".svg")
        val isSegment = isSegmentLike(lower)
        return !isArtifact && !isSegment && lower.contains("master")
    }

    /**
     * Guclu olmayan tum adaylar (sites that only give direct files) icin
     * son 10 saniye doldurulmus listeyi siraya koyar.
     *
     * Once HLS/DASH manifest, sonra dogrudan progressive dosya; ayni sirada
     * gorulen aday once gelir ( `.m3u8?token=...` once `chunklist` gibi).
     */
    private fun rankHeadlessCandidates(items: Collection<ResolvedStream>): List<ResolvedStream> {
        if (items.isEmpty()) return emptyList()
        val ordered = items.toList()
        val keyFn: (ResolvedStream) -> Int = { item ->
            val lower = item.streamUrl.lowercase()
            when {
                lower.contains(".m3u8") || lower.contains("master.txt") -> 0
                lower.contains(".mpd") -> 1
                else -> 2
            }
        }
        return ordered
            .withIndex()
            .sortedWith(compareBy({ (_, item) -> keyFn(item) }, { (idx, _) -> idx }))
            .map { (_, item) -> item }
            .take(MAX_HEADLESS_CANDIDATES)
    }

    /** Segment parca adlarini ayirt etmek icin yol adi sozlugu (browser siniflariyla ayni dil). */
    private fun isSegmentLike(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        if (name.isEmpty()) return false
        if (name.all { it.isDigit() }) return true
        val words = name.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return words.any { it in SEGMENT_WORDS } || SEGMENT_PREFIX_ALL.matches(name)
    }

    private suspend fun resolveViaHeadlessWebView(context: android.content.Context, pageUrl: String): ResolvedStream? =
        collectViaHeadlessWebView(context, pageUrl).firstOrNull()

    private fun extractStreamFromPage(pageUrl: String, depth: Int): String? {
        if (depth > 2) return null
        val html = fetchHtml(pageUrl) ?: return null

        // 0. Evrensel yapisal cikarma: JSON-LD / OpenGraph / microdata / JS blob.
        //    Site-ozel dallardan once calisir; ilk bulunan oynatilabilir aday kazanir.
        val extraction = runCatching { VodMetadataExtractor.extract(html, pageUrl) }.getOrNull()
        if (!extraction?.streamUrl.isNullOrBlank() && isDirectStream(extraction!!.streamUrl!!)) {
            return extraction.streamUrl
        }

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

        // 5. Look for video iframe embeds (`src` + lazy `data-src` varyantlari).
        //    Tembel yuklenen iframe'ler (`data-src`) hdfilmcehennemi tarzinda gizli
        //    embed zincirlerini tasir; recursion ayni cikarmayla devam eder.
        val iframeMatcher = IFRAME_SRC_REGEX.matcher(html)
        while (iframeMatcher.find()) {
            val iframeSrc = iframeMatcher.group(1)
            if (!iframeSrc.isNullOrBlank() && iframeSrc.startsWith("http")) {
                if (isDirectStream(iframeSrc)) return iframeSrc
                val nested = extractStreamFromPage(iframeSrc, depth + 1)
                if (nested != null) return nested
            }
        }
        extraction?.embedUrls.orEmpty().forEach { embedUrl ->
            if (isDirectStream(embedUrl)) return embedUrl
            val nested = extractStreamFromPage(embedUrl, depth + 1)
            if (nested != null) return nested
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
