package com.tvapp.livetv.browser

import android.webkit.CookieManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sayfa isteklerini dinleyerek video akislarini yakalayan sniffer bileseni.
 */
class StreamSniffer(
    private val userAgent: () -> String,
    private val onStreamAvailable: (PlaybackRequest) -> Unit,
    private val onCleared: () -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit
) {
    private val candidates = CopyOnWriteArrayList<StreamCandidate>()
    private val seq = AtomicInteger(0)

    @Volatile private var pageUrl: String = ""
    private val announced = AtomicBoolean(false)
    private val page = AtomicInteger(0)

    fun onPageStarted(url: String) {
        pageUrl = url
        candidates.clear()
        announced.set(false)
        page.incrementAndGet()
        onCleared()
    }

    fun onRequest(url: String, headers: Map<String, String> = emptyMap()) {
        val kind = MediaUrlClassifier.classify(url)
        if (kind !in COLLECTED) return
        candidates.add(StreamCandidate(url, kind, seq.incrementAndGet(), headers))
        when (kind) {
            MediaKind.MANIFEST_HLS,
            MediaKind.MANIFEST_DASH -> announce()
            MediaKind.PROGRESSIVE -> {
                val seenOn = page.get()
                schedule(PROGRESSIVE_GRACE_MS) {
                    if (page.get() == seenOn) announce()
                }
            }
            else -> Unit
        }
    }

    private fun announce() {
        if (hasStream() && announced.compareAndSet(false, true)) {
            val req = bestRequest()
            if (req != null) {
                onStreamAvailable(req)
            }
        }
    }

    fun hasStream(): Boolean = candidates.any { it.kind in PLAYABLE }

    fun bestRequest(): PlaybackRequest? {
        val best = StreamCandidateSelector.selectBest(candidates) ?: return null
        val cookie = runCatching { CookieManager.getInstance().getCookie(best.url) }.getOrNull()
        val headers = PlaybackHeaders.build(best.url, pageUrl, userAgent(), best.headers)
            .let { if (!cookie.isNullOrEmpty()) it + ("Cookie" to cookie) else it }
        return PlaybackRequest(best.url, headers, emptyList(), pageUrl)
    }

    companion object {
        const val PROGRESSIVE_GRACE_MS = 1200L

        private val PLAYABLE = setOf(
            MediaKind.MANIFEST_HLS,
            MediaKind.MANIFEST_DASH,
            MediaKind.PROGRESSIVE
        )

        private val COLLECTED = PLAYABLE + MediaKind.SUBTITLE
    }
}
