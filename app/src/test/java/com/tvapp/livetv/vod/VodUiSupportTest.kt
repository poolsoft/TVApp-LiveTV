package com.tvapp.livetv.vod

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
