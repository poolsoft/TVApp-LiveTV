package com.tvapp.livetv.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `VodMetadataExtractor` sabit HTML fiksturleriyle cocrukturulur (AGENTS.md: test
 * cevrimdisi calisir). Sira sozlesmesi: JSON-LD `contentUrl` > OpenGraph/Twitter >
 * microdata > JS blob > ciplak akis baglantilari.
 */
class VodMetadataExtractorTest {

    @Test
    fun testJsonLdContentUrlWins() {
        val html = """
            <html><body>
            <script type="application/ld+json">{"@context":"https://schema.org","@type":"Clip","contentUrl":"https://cdn.kanald.com/geo/abc.smil/playlist.m3u8"}</script>
            </body></html>
        """.trimIndent()

        val result = VodMetadataExtractor.extract(html, "https://www.kanald.com.tr/izle/bir-film")

        assertEquals("https://cdn.kanald.com/geo/abc.smil/playlist.m3u8", result.streamUrl)
        assertEquals(emptyList<String>(), result.embedUrls)
    }

    @Test
    fun testJsonLdContentUrlArrayForm() {
        val html = """
            <script type="application/ld+json">{"@type":"VideoObject","contentUrl":["https://cdn.site.com/vod/a.m3u8"]}</script>
        """.trimIndent()

        assertEquals("https://cdn.site.com/vod/a.m3u8", VodMetadataExtractor.extract(html, "https://site.com/watch/1").streamUrl)
    }

    @Test
    fun testOpenGraphVideoTags() {
        val html = """
            <html><head>
            <meta property="og:video:secure_url" content="https://cdn.site.com/hls/master.m3u8">
            </head><body></body></html>
        """.trimIndent()

        assertEquals("https://cdn.site.com/hls/master.m3u8", VodMetadataExtractor.extract(html, "https://site.com/watch/2").streamUrl)
    }

    @Test
    fun testOpenGraphReversedAttributeOrderAndEscapes() {
        // content onde, property sonda; ayrica JSON kacislari (\/) temizlenmeli.
        val html = """<meta content="https:\/\/cdn.site.com\/vod\/film.mp4" property="og:video">"""

        assertEquals("https://cdn.site.com/vod/film.mp4", VodMetadataExtractor.extract(html, "https://site.com/watch/3").streamUrl)
    }

    @Test
    fun testHtmlEntitiesInStreamUrlAreDecoded() {
        val html = """
            <meta property="og:video" content="https://cdn.site.com/hls/master.m3u8?token=1&amp;sig=2">
        """.trimIndent()

        assertEquals(
            "https://cdn.site.com/hls/master.m3u8?token=1&sig=2",
            VodMetadataExtractor.extract(html, "https://site.com/watch/4").streamUrl
        )
    }

    @Test
    fun testAnalyticsTagIsNeverAStream() {
        val html = """
            <meta property="og:video" content="https://www.googletagmanager.com/gtm.js?id=GTM-1234">
        """.trimIndent()

        val result = VodMetadataExtractor.extract(html, "https://site.com/watch/5")

        assertNull(result.streamUrl)
        assertEquals(emptyList<String>(), result.embedUrls)
    }

    @Test
    fun testMicrodataContentUrl() {
        val html = """
            <html><body><meta itemprop="contentUrl" content="https://cdn.site.com/hls/index.m3u8"></body></html>
        """.trimIndent()

        assertEquals("https://cdn.site.com/hls/index.m3u8", VodMetadataExtractor.extract(html, "https://site.com/watch/6").streamUrl)
    }

    @Test
    fun testPlayerJsBlobFileAttribute() {
        val html = """
            <script>var player = {"file": "https://cdn.site.com/vod/movie.mp4", "image": "https://cdn.site.com/poster.jpg"};</script>
        """.trimIndent()

        assertEquals("https://cdn.site.com/vod/movie.mp4", VodMetadataExtractor.extract(html, "https://site.com/watch/7").streamUrl)
    }

    @Test
    fun testBareManifestBeatsDirectFileAndImages() {
        val html = """
            <html><body>
            <img src="https://cdn.site.com/poster.jpg">
            <a href="https://cdn.site.com/vod/movie.mp4">mp4</a>
            <a href="https://cdn.site.com/hls/index.m3u8">izle</a>
            </body></html>
        """.trimIndent()

        assertEquals("https://cdn.site.com/hls/index.m3u8", VodMetadataExtractor.extract(html, "https://site.com/watch/8").streamUrl)
    }

    @Test
    fun testIframeEmbedsResolvedAgainstPageUrl() {
        val html = """
            <html><body>
            <iframe src="/embed/abc" frameborder="0"></iframe>
            <iframe data-src="//cdn.site.com/player/xyz"></iframe>
            </body></html>
        """.trimIndent()

        val result = VodMetadataExtractor.extract(html, "https://site.com/watch/9")

        assertNull(result.streamUrl)
        assertEquals(
            listOf("https://site.com/embed/abc", "https://cdn.site.com/player/xyz"),
            result.embedUrls
        )
    }

    @Test
    fun testEmbedUrlFromJsonLdJoinsIframeEmbeds() {
        val html = """
            <iframe src="https://embed.site/a"></iframe>
            <script type="application/ld+json">{"@type":"VideoObject","embedUrl":"https://embed.site/b"}</script>
        """.trimIndent()

        val result = VodMetadataExtractor.extract(html, "https://site.com/watch/10")

        assertNull(result.streamUrl)
        assertEquals(listOf("https://embed.site/a", "https://embed.site/b"), result.embedUrls)
    }

    @Test
    fun testBlankHtmlYieldsEmptyExtraction() {
        val result = VodMetadataExtractor.extract("   ", "https://site.com/watch/11")

        assertNull(result.streamUrl)
        assertEquals(emptyList<String>(), result.embedUrls)
    }
}
