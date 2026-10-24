package com.tvapp.livetv.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserComponentsTest {

    @Test
    fun testCursorGeometryClamp() {
        val clamped = CursorGeometry.clamp(-50f, 1500f, 1920, 1080)
        assertEquals(0f, clamped.x, 0.01f)
        assertEquals(1079f, clamped.y, 0.01f)
    }

    @Test
    fun testCursorGeometryStep() {
        val current = CursorGeometry.Point(100f, 100f)
        val stepped = CursorGeometry.step(current, 1, 0, 10f, 1920, 1080)
        assertEquals(110f, stepped.x, 0.01f)
        assertEquals(100f, stepped.y, 0.01f)
    }

    @Test
    fun testCursorGeometrySpeedAcceleration() {
        val baseSpeed = CursorGeometry.speedForHoldMs(0L)
        val maxSpeed = CursorGeometry.speedForHoldMs(1000L)
        assertTrue(maxSpeed > baseSpeed)
    }

    @Test
    fun testUrlNormalizer() {
        val normal = UrlNormalizer.normalize("example.com")
        assertEquals("https://example.com", normal)

        val full = UrlNormalizer.normalize("https://google.com")
        assertEquals("https://google.com", full)

        val search = UrlNormalizer.normalize("film izle")
        assertTrue(search.contains("google.com/search?q=film+izle") || search.contains("google.com/search?q=film%20izle"))
    }

    @Test
    fun testMediaUrlClassifier() {
        assertEquals(MediaKind.MANIFEST_HLS, MediaUrlClassifier.classify("https://cdn.site.com/live/stream.m3u8"))
        assertEquals(MediaKind.MANIFEST_HLS, MediaUrlClassifier.classify("https://cdn.site.com/hls/master.m3u8?token=xyz"))
        assertEquals(MediaKind.MANIFEST_DASH, MediaUrlClassifier.classify("https://cdn.site.com/dash/manifest.mpd"))
        assertEquals(MediaKind.PROGRESSIVE, MediaUrlClassifier.classify("https://cdn.site.com/video/movie.mp4"))
        assertEquals(MediaKind.SUBTITLE, MediaUrlClassifier.classify("https://cdn.site.com/sub/turkish.vtt"))
        assertEquals(MediaKind.SEGMENT, MediaUrlClassifier.classify("https://cdn.site.com/hls/segment_001.ts"))
        assertEquals(MediaKind.SEGMENT, MediaUrlClassifier.classify("https://cdn.site.com/hls/init.mp4"))
    }

    @Test
    fun testStreamCandidateSelector() {
        val candidates = listOf(
            StreamCandidate("https://cdn.site.com/video/clip.mp4", MediaKind.PROGRESSIVE, 1),
            StreamCandidate("https://cdn.site.com/hls/index.m3u8", MediaKind.MANIFEST_HLS, 2),
            StreamCandidate("https://cdn.site.com/hls/master.m3u8", MediaKind.MANIFEST_HLS, 3)
        )

        val best = StreamCandidateSelector.selectBest(candidates)
        assertNotNull(best)
        // Master manifest en oncelikli secilmelidir
        assertEquals("https://cdn.site.com/hls/master.m3u8", best?.url)
    }

    @Test
    fun testPlaybackHeadersOrigin() {
        val origin = PlaybackHeaders.originOf("https://sub.domain.com:8080/path/test.m3u8")
        assertEquals("https://sub.domain.com:8080", origin)

        val defaultOrigin = PlaybackHeaders.originOf("https://example.com/playlist.m3u8")
        assertEquals("https://example.com", defaultOrigin)
    }
}
