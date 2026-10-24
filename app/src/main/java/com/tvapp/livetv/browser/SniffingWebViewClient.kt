package com.tvapp.livetv.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Tum sayfa ag isteklerini dinleyerek video akislarini sniffer'a ileten ve reklam/popup engellemesi yapan WebViewClient.
 */
class SniffingWebViewClient(
    private val sniffer: StreamSniffer,
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
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val scheme = request.url?.scheme?.lowercase() ?: ""
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
}
