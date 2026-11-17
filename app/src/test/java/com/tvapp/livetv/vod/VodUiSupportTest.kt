package com.tvapp.livetv.vod

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodUiSupportTest {

    @Test
    fun labelsHlsWithQualityAndDomain() {
        val label = VodUiSupport.buildVodStreamLabel(
            1,
            "https://hls8.playmix.uno/hls/xyz/master.txt?token=abc&res=1080p",
        )
        assertEquals("1. HLS [1080p] - hls8.playmix.uno", label)
    }

    @Test
    fun labelsDashStream() {
        val label = VodUiSupport.buildVodStreamLabel(2, "https://cdn.site.com/dash/manifest.mpd")
        assertEquals("2. DASH - cdn.site.com", label)
    }

    @Test
    fun labelsMp4Stream() {
        val label = VodUiSupport.buildVodStreamLabel(
            3,
            "https://cdn.site.com/video/film_720p.mp4?token=x",
        )
        assertEquals("3. MP4 [720p] - cdn.site.com", label)
    }

    @Test
    fun parsesEpisodeJsonList() {
        val raw = """[{"title":"Pilot","videoUrl":"https://cdn/x1.mp4"},""" +
            """{"name":"Bolum 2","url":"https://cdn/x2.mp4"}]"""
        val episodes = VodUiSupport.parseEpisodesJson(raw)
        assertEquals(2, episodes.size)
        assertEquals("Pilot", episodes[0].title)
        assertEquals("https://cdn/x1.mp4", episodes[0].url)
        assertEquals("Bolum 2", episodes[1].title)
    }

    @Test
    fun parsesInvalidEpisodeJsonAsEmpty() {
        assertEquals(0, VodUiSupport.parseEpisodesJson(null).size)
        assertEquals(0, VodUiSupport.parseEpisodesJson("not json").size)
        assertEquals(0, VodUiSupport.parseEpisodesJson("""{"a":1}""").size)
    }

    @Test
    fun buildsDynamicEpisodeTitle() {
        val obj = JSONObject("""{"season":1,"episode":3,"title":"Final","videoUrl":"https://cdn/f.mp4"}""")
        val parsed = VodUiSupport.dynamicEpisode(obj, 0)
        assertEquals("1. Sezon 3. Bolum - Final", parsed?.title)
        assertEquals("https://cdn/f.mp4", parsed?.url)
    }

    @Test
    fun dynamicEpisodeWithoutTitleOmitsSuffix() {
        val obj = JSONObject("""{"episode":2,"videoUrl":"https://cdn/f2.mp4"}""")
        val parsed = VodUiSupport.dynamicEpisode(obj, 1)
        assertEquals("1. Sezon 2. Bolum", parsed?.title)
    }

    @Test
    fun dynamicEpisodeWithoutUrlIsNull() {
        val obj = JSONObject("""{"episode":2,"title":"X"}""")
        assertNull(VodUiSupport.dynamicEpisode(obj, 1))
        assertNull(VodUiSupport.dynamicEpisode(null, 0))
    }

    @Test
    fun providerDefaultEpisodeTitleMatchesTurkishAndAsciiForms() {
        assertTrue(VodUiSupport.isProviderDefaultEpisodeTitle("3. Bölüm", 3))
        assertTrue(VodUiSupport.isProviderDefaultEpisodeTitle("3. Bolum", 3))
        assertTrue(VodUiSupport.isProviderDefaultEpisodeTitle("3. BÖLÜM", 3))
        assertTrue(VodUiSupport.isProviderDefaultEpisodeTitle("Bölüm 3", 3, allowReversed = true))
        assertFalse(VodUiSupport.isProviderDefaultEpisodeTitle("Bölüm 3", 3))
        assertFalse(VodUiSupport.isProviderDefaultEpisodeTitle("Final", 3))
    }

    @Test
    fun dynamicEpisodeUsesInjectedLabels() {
        val labels = object : VodUiSupport.EpisodeLabels {
            override fun seasonEpisode(season: Int, episode: Int) = "S$season:E$episode"
            override fun seasonEpisodeTitle(season: Int, episode: Int, title: String) =
                "S$season:E$episode:$title"
            override fun untitledEpisode(index: Int) = "#$index"
        }
        val withTitle = JSONObject("""{"season":2,"episode":5,"title":"Final","videoUrl":"https://cdn/f.mp4"}""")
        assertEquals("S2:E5:Final", VodUiSupport.dynamicEpisode(withTitle, 0, labels)?.title)
        val withoutTitle = JSONObject("""{"season":2,"episode":5,"videoUrl":"https://cdn/f.mp4"}""")
        assertEquals("S2:E5", VodUiSupport.dynamicEpisode(withoutTitle, 0, labels)?.title)
        val repeatedTitle = JSONObject("""{"season":2,"episode":5,"title":"5. Bölüm","videoUrl":"https://cdn/f.mp4"}""")
        assertEquals("S2:E5", VodUiSupport.dynamicEpisode(repeatedTitle, 0, labels)?.title)
    }

    @Test
    fun parseEpisodesJsonUsesInjectedUntitledLabel() {
        val labels = object : VodUiSupport.EpisodeLabels {
            override fun seasonEpisode(season: Int, episode: Int) = "S$season:E$episode"
            override fun seasonEpisodeTitle(season: Int, episode: Int, title: String) =
                "S$season:E$episode:$title"
            override fun untitledEpisode(index: Int) = "#$index"
        }
        val episodes = VodUiSupport.parseEpisodesJson(
            """[{"videoUrl":"https://cdn/x1.mp4"}]""",
            labels,
        )
        assertEquals("#1", episodes.single().title)
    }
}
