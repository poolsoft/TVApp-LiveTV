package com.tvapp.livetv.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `WebVideoResolver` icin ag yapmadan calisan birim testler: aday filtresi, segment
 * ayrimi, headless siralama ve `extractStreamFromPage`in `html` sozlesmesi.
 *
 * `f1fb402` gerilemesi buradan cikmisti: `html` verildiginde sayfa tekrar indirilmemeli,
 * `html = null` iye cozucu agda olmasa bile istisna firlatmamali.
 */
class WebVideoResolverTest {

    @Test
    fun testNetworkCandidateRejectsAdsAnalyticsAndStaticAssets() {
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://www.google-analytics.com/collect?v=1"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://www.googletagmanager.com/gtm.js?id=GTM-1"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://connect.facebook.net/en_US/sdk.js"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/poster.jpg"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://site.com/styles/app.css"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://site.com/fonts/brand.woff2"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://site.com/favicon.ico"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("   "))
    }

    @Test
    fun testNetworkCandidateAcceptsStreamsAndShortCutsReklami() {
        assertTrue(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/hls/master.m3u8?token=1"))
        assertTrue(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/streams/master.txt"))
        assertTrue(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/dash/manifest.mpd"))
        assertTrue(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/video/movie.mp4"))
        assertTrue(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/hls/seg-1.ts"))
        // Kisa tanitima ait dosyalar akis adayi sayilmaz.
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/ads/ad_bumper.mp4"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/promo/preview_teaser.mp4"))
        assertFalse(WebVideoResolver.isNetworkMediaCandidate("https://cdn.site.com/banner/spot.webm"))
    }

    @Test
    fun testSegmentLikeSeparatesPartsFromManifests() {
        assertTrue(WebVideoResolver.isSegmentLike("https://cdn.site.com/hls/12345.ts"))
        assertTrue(WebVideoResolver.isSegmentLike("https://cdn.site.com/hls/init.mp4"))
        assertTrue(WebVideoResolver.isSegmentLike("https://cdn.site.com/hls/seg-9999.ts?token=x"))
        assertTrue(WebVideoResolver.isSegmentLike("https://cdn.site.com/hls/segment001.ts"))
        assertTrue(WebVideoResolver.isSegmentLike("https://cdn.site.com/hls/frag12.m4s"))
        // Manifest ve ana dosyalar parca degildir.
        assertFalse(WebVideoResolver.isSegmentLike("https://cdn.site.com/hls/master.m3u8"))
        assertFalse(WebVideoResolver.isSegmentLike("https://cdn.site.com/hls/index.m3u8"))
        assertFalse(WebVideoResolver.isSegmentLike("https://cdn.site.com/hls/chunklist.m3u8"))
        assertFalse(WebVideoResolver.isSegmentLike("https://cdn.site.com/vod/movie.mp4"))
    }

    @Test
    fun testStrongCandidateDoesNotRequireMasterKeyword() {
        // f1fb402: index.m3u8 de guclu adaydir; "master" aranmaz.
        assertTrue(WebVideoResolver.isStrongCandidate(stream("https://cdn.site.com/hls/master.m3u8")))
        assertTrue(WebVideoResolver.isStrongCandidate(stream("https://cdn.site.com/hls/index.m3u8")))
        assertTrue(WebVideoResolver.isStrongCandidate(stream("https://cdn.site.com/streams/master.txt")))
        // Parca ve dogrudan dosya guclu aday degildir.
        assertFalse(WebVideoResolver.isStrongCandidate(stream("https://cdn.site.com/hls/seg-1.m3u8")))
        assertFalse(WebVideoResolver.isStrongCandidate(stream("https://cdn.site.com/vod/movie.mp4")))
        assertFalse(WebVideoResolver.isStrongCandidate(stream("https://cdn.site.com/poster.jpg")))
    }

    @Test
    fun testHeadlessRankingPrefersManifestThenKeepsArrivalOrder() {
        val mp4A = stream("https://cdn.site.com/vod/a.mp4")
        val dashA = stream("https://cdn.site.com/dash/a.mpd")
        val hlsA = stream("https://cdn.site.com/hls/a.m3u8")
        val mp4B = stream("https://cdn.site.com/vod/b.mp4")
        val masqueraded = stream("https://cdn.site.com/streams/master.txt")
        val dashB = stream("https://cdn.site.com/dash/b.mpd")

        val ranked = WebVideoResolver.rankHeadlessCandidates(listOf(mp4A, dashA, hlsA, mp4B, masqueraded, dashB))

        assertEquals(listOf(hlsA, masqueraded, dashA, dashB, mp4A, mp4B), ranked)
    }

    @Test
    fun testHeadlessRankingBoundsListAndHandlesEmpty() {
        val many = (1..10).map { stream("https://cdn.site.com/vod/clip$it.mp4") }

        assertEquals(WebVideoResolver.MAX_HEADLESS_CANDIDATES, WebVideoResolver.rankHeadlessCandidates(many).size)
        assertEquals(emptyList<WebVideoResolver.ResolvedStream>(), WebVideoResolver.rankHeadlessCandidates(emptyList()))
    }

    @Test
    fun testSuppliedHtmlIsUsedWithoutRefetchingPage() {
        // f1fb402 sozlesmesi: html verildiyse sayfa URL'sine hic gidilmez.
        // Sayfa URL'si bilerek gecersiz; yine de aday bulunmali.
        val html = """<html><body><a href="https://cdn.site.com/hls/master.m3u8">izle</a></body></html>"""

        assertEquals(
            "https://cdn.site.com/hls/master.m3u8",
            WebVideoResolver.extractStreamFromPage("", depth = 0, html = html)
        )
    }

    @Test
    fun testStructuralExtractionWinsBeforeRegexFallback() {
        val html = """
            <html><body>
            <script type="application/ld+json">{"@type":"VideoObject","contentUrl":"https://cdn.site.com/geo/x.smil/playlist.m3u8"}</script>
            <a href="https://cdn.site.com/vod/yedek.mp4">yedek</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://cdn.site.com/geo/x.smil/playlist.m3u8",
            WebVideoResolver.extractStreamFromPage("https://site.com/watch/1", depth = 0, html = html)
        )
    }

    @Test
    fun testNullHtmlWithUnreachablePageReturnsNull() {
        // HTML verilmedi ve sayfa indirilemiyor: aday yok, istisna yok (NPE regresyonu).
        assertNull(WebVideoResolver.extractStreamFromPage("", depth = 0, html = null))
        assertNull(WebVideoResolver.extractStreamFromPage("indirilemez-bir-girdi", depth = 0))
    }

    @Test
    fun testUselessHtmlYieldsNoCandidateAndNoException() {
        assertNull(
            WebVideoResolver.extractStreamFromPage(
                "https://site.com/watch/2",
                depth = 0,
                html = "<html><body><p>Sayfa bulunamadi</p></body></html>"
            )
        )
    }

    @Test
    fun testRecursionDepthIsCapped() {
        val html = """<a href="https://cdn.site.com/hls/master.m3u8">izle</a>"""

        assertNull(WebVideoResolver.extractStreamFromPage("https://site.com/watch/3", depth = 3, html = html))
    }

    private fun stream(url: String) = WebVideoResolver.ResolvedStream(url)
}
