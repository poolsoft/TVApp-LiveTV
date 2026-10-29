package com.tvapp.livetv.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Tum sayfa ag isteklerini dinleyerek video akislarini sniffer'a ileten,
 * sayfadaki video etiketlerini otomatik oynatarak (autoplay) akislari tetikleyen ve
 * reklam sunucularini engelleyen WebViewClient.
 */
class SniffingWebViewClient(
    private val sniffer: StreamSniffer,
    private val enableAutoplayClicker: Boolean = true,
    private val onNavigate: (String) -> Unit = {},
    private val onLoaded: (String) -> Unit = {}
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            sniffer.onPageStarted(url)
            onNavigate(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) {
            onLoaded(url)
            if (enableAutoplayClicker) {
                injectAutoplayScript(view)
            }
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val scheme = request?.url?.scheme?.lowercase() ?: ""
        if (scheme != "http" && scheme != "https") {
            // Intent veya harici semalari WebView icinde calistirmayip yutuyoruz
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val url = req.url?.toString() ?: return null

        // Reklam sunucularini engelle
        if (MediaUrlClassifier.isAdHost(url)) {
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        }

        sniffer.onRequest(url, req.requestHeaders.orEmpty())
        return super.shouldInterceptRequest(view, request)
    }

    private fun injectAutoplayScript(view: WebView?) {
        val js = """
            (function() {
                function triggerPlay() {
                    try {
                        // 1. Sayfadaki tum video ve ses etiketlerini sessizce oynat
                        var vids = document.querySelectorAll('video, audio');
                        for (var i = 0; i < vids.length; i++) {
                            try {
                                vids[i].muted = true;
                                var p = vids[i].play();
                                if (p && typeof p.catch === 'function') { p.catch(function(){}); }
                            } catch(e) {}
                        }

                        // 2. Sadece video oynatici butonlarina tikla (Linklere veya dis ogelere ASLA tiklama!)
                        var selectors = [
                            '.play-btn', '.vjs-big-play-button', '.jw-display-icon-container',
                            '.plyr__control--overlaid', 'button[aria-label="Play"]', 'button[aria-label*="Play"]',
                            'button[aria-label*="Oynat"]', '.play-button', '#play-button', '.play_button',
                            '.click-to-play', '.vjs-play-control'
                        ];
                        for (var s = 0; s < selectors.length; s++) {
                            var elements = document.querySelectorAll(selectors[s]);
                            for (var j = 0; j < elements.length; j++) {
                                var el = elements[j];
                                if (el.tagName === 'A' || el.closest('a')) continue;
                                try { el.click(); } catch(e) {}
                            }
                        }

                        // 3. Erisilebilir iframelerin icindeki videolari da tetikle
                        var iframes = document.querySelectorAll('iframe');
                        for (var f = 0; f < iframes.length; f++) {
                            try {
                                var doc = iframes[f].contentDocument || iframes[f].contentWindow.document;
                                if (doc) {
                                    var ivs = doc.querySelectorAll('video, audio');
                                    for (var iv = 0; iv < ivs.length; iv++) {
                                        ivs[iv].muted = true;
                                        var ip = ivs[iv].play();
                                        if (ip && typeof ip.catch === 'function') { ip.catch(function(){}); }
                                    }
                                }
                            } catch(e) {}
                        }
                    } catch(err) {}
                }

                triggerPlay();
                var attempts = 0;
                var interval = setInterval(function() {
                    attempts++;
                    triggerPlay();
                    if (attempts >= 6) {
                        clearInterval(interval);
                    }
                }, 800);
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }
}
