package com.tvapp.livetv.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `VodUrlClassifier` site-bagimsiz sinifinin sabit davranislari.
 *
 * Kritik sozlesme: bir web sayfasi yolu (izle/baslik/embed) dogrudan akis SAYILMAZ;
 * sorgu parametresindeki uzanti da sayfayi akise cevirmez.
 */
class VodUrlClassifierTest {

    @Test
    fun testWebpagePathsAreNotDirectStreams() {
        assertFalse(VodUrlClassifier.isDirectStream("https://kanald.com/izi-basla/program-12345"))
        assertFalse(VodUrlClassifier.isDirectStream("https://site.com/dizi/film-izle-orijinal-1080p"))
        assertFalse(VodUrlClassifier.isDirectStream("https://site.com/embed/abc123"))
        assertFalse(VodUrlClassifier.isDirectStream("https://site.com/player/app.js"))
        assertFalse(VodUrlClassifier.isDirectStream("https://site.com/poster/film.jpg"))
    }

    @Test
    fun testQueryParametersDoNotTurnPageIntoStream() {
        // Uzanti yolda degil sorguda ise sayfa yoludur; oynaticiya akis gibi gitmemeli.
        assertFalse(VodUrlClassifier.isDirectStream("https://site.com/watch?v=abc.mp4"))
        assertFalse(VodUrlClassifier.isDirectStream("https://site.com/video?id=1.mp4"))
        assertTrue(VodUrlClassifier.isDirectStream("https://cdn.site.com/hls/master.m3u8?token=xyz"))
    }

    @Test
    fun testMasqueradedAndClassicStreamsAreDirect() {
        assertTrue(VodUrlClassifier.isDirectStream("https://cdn.site.com/streams/master.txt"))
        assertTrue(VodUrlClassifier.isDirectStream("https://cdn.site.com/streams/playlist.txt"))
        assertTrue(VodUrlClassifier.isDirectStream("https://cdn.site.com/live/x.smil/playlist.m3u8"))
        assertTrue(VodUrlClassifier.isDirectStream("https://site.com/player/stream.php?id=5"))
        assertTrue(VodUrlClassifier.isDirectStream("https://cdn.site.com/video/film.mp4"))
        assertTrue(VodUrlClassifier.isDirectStream("https://cdn.site.com/dash/manifest.mpd"))
        assertTrue(VodUrlClassifier.isDirectStream("https://cdn.site.com/hls/index.m3u8"))
    }

    @Test
    fun testEmptyUrlIsNeverStream() {
        assertFalse(VodUrlClassifier.isDirectStream(""))
        assertFalse(VodUrlClassifier.isDirectStream("   "))
        assertFalse(VodUrlClassifier.isHlsUrl(""))
        assertFalse(VodUrlClassifier.isDashUrl(""))
    }

    @Test
    fun testHlsClassification() {
        assertTrue(VodUrlClassifier.isHlsUrl("https://cdn.site.com/hls/master.m3u8"))
        assertTrue(VodUrlClassifier.isHlsUrl("https://cdn.site.com/streams/master.txt"))
        assertTrue(VodUrlClassifier.isHlsUrl("https://cdn.site.com/live/x.smil/playlist.m3u8"))
        assertTrue(VodUrlClassifier.isHlsUrl("https://cdn.site.com/hls/playlist"))
        assertFalse(VodUrlClassifier.isHlsUrl("https://cdn.site.com/dash/manifest.mpd"))
        assertFalse(VodUrlClassifier.isHlsUrl("https://cdn.site.com/video/film.mp4"))
    }

    @Test
    fun testDashClassification() {
        assertTrue(VodUrlClassifier.isDashUrl("https://cdn.site.com/dash/manifest.mpd"))
        assertTrue(VodUrlClassifier.isDashUrl("https://cdn.site.com/dash/manifest"))
        assertFalse(VodUrlClassifier.isDashUrl("https://cdn.site.com/dash/index"))
        assertFalse(VodUrlClassifier.isDashUrl("https://cdn.site.com/hls/master.m3u8"))
    }

    @Test
    fun testMimeTypeForMasqueradedHls() {
        assertEquals("application/x-mpegURL", VodUrlClassifier.mimeType("https://cdn.site.com/streams/master.txt"))
        assertEquals("application/x-mpegURL", VodUrlClassifier.mimeType("https://cdn.site.com/hls/master.m3u8"))
        assertEquals("application/dash+xml", VodUrlClassifier.mimeType("https://cdn.site.com/dash/manifest.mpd"))
        // Uzanti taninmiyorsa mime yok; oynatici kendi tahminini kullanir.
        assertNull(VodUrlClassifier.mimeType("https://cdn.site.com/video/film.mp4"))
    }

    @Test
    fun testResolverDelegatesToClassifier() {
        assertTrue(WebVideoResolver.isDirectStream("https://cdn.site.com/hls/master.m3u8"))
        assertFalse(WebVideoResolver.isDirectStream("https://site.com/watch/the-matrix"))
    }
}
