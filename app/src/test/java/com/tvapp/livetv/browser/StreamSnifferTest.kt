package com.tvapp.livetv.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRASH-001: CookieManager ana thread'ten kullanilmak istenir. Sniffer istekleri WebKit'in
 * arka plan thread'inden geldigi icin akis duyurusu (ve dolayisiyla cerez okumasi) cagri
 * thread'inde degil, host'un ana thread zamanlayicisinda calismalidir.
 */
class StreamSnifferTest {

    private class Recorder {
        val logs = mutableListOf<String>()
        val streams = mutableListOf<PlaybackRequest>()
        private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

        val sniffer = StreamSniffer(
            userAgent = { "TestUA/1.0" },
            onStreamAvailable = { streams += it },
            onCleared = { },
            schedule = { delayMs, task -> scheduled += delayMs to task },
            debugLog = { logs += it }
        )

        fun scheduledDelays(): List<Long> = scheduled.map { it.first }

        /** Zamanlanan gorevleri ana thread'i taklit ederek sirayla calistirir. */
        fun runScheduled() {
            while (scheduled.isNotEmpty()) {
                scheduled.removeAt(0).second.invoke()
            }
        }
    }

    @Test
    fun testManifestAnnounceIsDeferredToMainThread() {
        val recorder = Recorder()
        recorder.sniffer.onPageStarted("https://site.com/watch/1")

        recorder.sniffer.onRequest("https://cdn.site.com/hls/master.m3u8?token=x")

        // Cagri thread'inda bildirim yapilmaz; yalnızca ana thread gorevi zamanlanir.
        assertTrue(recorder.streams.isEmpty())
        assertEquals(listOf(StreamSniffer.ANNOUNCE_DELAY_MS), recorder.scheduledDelays())

        recorder.runScheduled()

        assertEquals(1, recorder.streams.size)
        val request = recorder.streams.single()
        assertEquals("https://cdn.site.com/hls/master.m3u8?token=x", request.url)
        assertEquals("TestUA/1.0", request.headers["User-Agent"])
        assertEquals("https://site.com/watch/1", request.headers["Referer"])
        // JVM testinde CookieManager yoktur: cerez bulunamazsa akis basliksiz üretilmeye devam eder.
        assertNull(request.headers["Cookie"])
        assertTrue(recorder.logs.any { it.startsWith("SNIFFER_DETECT") })
        assertTrue(recorder.logs.any { it.startsWith("SNIFFER_ANNOUNCE") })
    }

    @Test
    fun testProgressiveAnnouncesAfterGrace() {
        val recorder = Recorder()
        recorder.sniffer.onPageStarted("https://site.com/watch/2")

        recorder.sniffer.onRequest("https://cdn.site.com/video/movie.mp4")

        assertTrue(recorder.streams.isEmpty())
        assertEquals(listOf(StreamSniffer.PROGRESSIVE_GRACE_MS), recorder.scheduledDelays())

        recorder.runScheduled()

        assertEquals("https://cdn.site.com/video/movie.mp4", recorder.streams.single().url)
    }

    @Test
    fun testPageChangeCancelsPendingProgressiveAnnounce() {
        val recorder = Recorder()
        recorder.sniffer.onPageStarted("https://site.com/watch/3")
        recorder.sniffer.onRequest("https://cdn.site.com/video/movie.mp4")

        recorder.sniffer.onPageStarted("https://site.com/watch/4")
        recorder.runScheduled()

        assertTrue(recorder.streams.isEmpty())
    }

    @Test
    fun testSubtitleOnlyNeverAnnounces() {
        val recorder = Recorder()
        recorder.sniffer.onPageStarted("https://site.com/watch/5")

        recorder.sniffer.onRequest("https://cdn.site.com/sub/turkish.vtt")

        assertTrue(recorder.streams.isEmpty())
        assertTrue(recorder.scheduledDelays().isEmpty())
        assertNull(recorder.sniffer.bestRequest())
    }
}
