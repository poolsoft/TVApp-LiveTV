package com.tvapp.livetv.browser

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sayfa isteklerini dinleyerek video akislarini yakalayan sniffer bileseni.
 *
 * [schedule] ana thread'te calisan bir zamanlayicidir (UI handler): duyuru aninda
 * cerez okundugu icin akis bildirimi oradan yapilir.
 */
class StreamSniffer(
    private val userAgent: () -> String,
    private val onStreamAvailable: (PlaybackRequest) -> Unit,
    private val onCleared: () -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val debugLog: (String) -> Unit = { message -> android.util.Log.d("StreamSniffer", message) }
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
        debugLog("SNIFFER_DETECT | kind=$kind, url=$url")
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
        if (!hasStream()) return
        // Cerez okumasi ana thread istiyor; aday listesi thread-guvenli oldugu icin
        // duyuru ek bekleme olmadan ana thread'e tasinir.
        schedule(ANNOUNCE_DELAY_MS) { announceNow() }
    }

    private fun announceNow() {
        if (!hasStream()) return
        val req = bestRequest() ?: return
        debugLog("SNIFFER_ANNOUNCE | url=${req.url}, headersCount=${req.headers.size}")
        onStreamAvailable(req)
    }

    fun hasStream(): Boolean = candidates.any { it.kind in PLAYABLE }

    fun bestRequest(): PlaybackRequest? {
        val best = StreamCandidateSelector.selectBest(candidates) ?: return null
        val cookie = WebCookies.read(best.url)
        val headers = PlaybackHeaders.build(best.url, pageUrl, userAgent(), best.headers)
            .let { if (!cookie.isNullOrEmpty()) it + ("Cookie" to cookie) else it }
        return PlaybackRequest(best.url, headers, emptyList(), pageUrl)
    }

    companion object {
        const val PROGRESSIVE_GRACE_MS = 1200L

        /** Duyurunun ana thread'e tasinirken eklenen bekleme (ms). */
        const val ANNOUNCE_DELAY_MS = 0L

        private val PLAYABLE = setOf(
            MediaKind.MANIFEST_HLS,
            MediaKind.MANIFEST_DASH,
            MediaKind.PROGRESSIVE
        )

        private val COLLECTED = PLAYABLE + MediaKind.SUBTITLE
    }
}
