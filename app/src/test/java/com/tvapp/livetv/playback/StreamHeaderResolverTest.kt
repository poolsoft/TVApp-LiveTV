package com.tvapp.livetv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamHeaderResolverTest {

    @Test
    fun resolvesHeadersForKanalDAndDemirorenUrls() {
        val urls = listOf(
            "https://live.duhnet.tv/S2/HLS_LIVE/kanald/playlist.m3u8",
            "https://demiroren-live.ercdn.net/kanald/kanald.m3u8",
            "https://stream.kanald.com.tr/live.m3u8",
            "https://live.duhnet.tv/S2/HLS_LIVE/cnnturk/playlist.m3u8",
        )
        for (url in urls) {
            val resolved = StreamHeaderResolver.resolve(url)
            assertEquals("https://www.kanald.com.tr/", resolved.referrer)
            assertEquals("https://www.kanald.com.tr", resolved.origin)
        }
    }

    @Test
    fun resolvesHeadersForAtvAndTurkuvazUrls() {
        val urls = listOf(
            "https://trkvz-live.ercdn.net/atvhd/atvhd.m3u8",
            "https://video-live.atv.com.tr/atv/live.m3u8",
            "https://trkvz-live.ercdn.net/ahaberhd/ahaberhd.m3u8",
            "https://trkvz-live.ercdn.net/asporhd/asporhd.m3u8",
        )
        for (url in urls) {
            val resolved = StreamHeaderResolver.resolve(url)
            assertEquals("https://www.atv.com.tr/", resolved.referrer)
            assertEquals("https://www.atv.com.tr", resolved.origin)
        }
    }

    @Test
    fun resolvesHeadersForShowTvAndCinerUrls() {
        val urls = listOf(
            "https://ciner-live.ercdn.net/showtv/showtv.m3u8",
            "https://stream.showtv.com.tr/live/master.m3u8",
            "https://ciner-live.ercdn.net/haberturk/haberturk.m3u8",
        )
        for (url in urls) {
            val resolved = StreamHeaderResolver.resolve(url)
            assertEquals("https://www.showtv.com.tr/", resolved.referrer)
            assertEquals("https://www.showtv.com.tr", resolved.origin)
        }
    }

    @Test
    fun resolvesHeadersForStarTvAndDogusUrls() {
        val urls = listOf(
            "https://dogus-live.ercdn.net/startv/startv.m3u8",
            "https://stream.startv.com.tr/live.m3u8",
            "https://dogus-live.ercdn.net/ntv/ntv.m3u8",
        )
        for (url in urls) {
            val resolved = StreamHeaderResolver.resolve(url)
            assertEquals("https://www.startv.com.tr/", resolved.referrer)
            assertEquals("https://www.startv.com.tr", resolved.origin)
        }
    }

    @Test
    fun resolvesHeadersForTv8NowTvAndTrtUrls() {
        val tv8 = StreamHeaderResolver.resolve("https://live.tv8.com.tr/hls/tv8/master.m3u8")
        assertEquals("https://www.tv8.com.tr/", tv8.referrer)
        assertEquals("https://www.tv8.com.tr", tv8.origin)

        val nowTv = StreamHeaderResolver.resolve("https://nowtv-live.ercdn.net/nowtv/nowtv.m3u8")
        assertEquals("https://www.nowtv.com.tr/", nowTv.referrer)
        assertEquals("https://www.nowtv.com.tr", nowTv.origin)

        val tabii = StreamHeaderResolver.resolve("https://trt.com.tr/live/trt1.m3u8")
        assertEquals("https://www.tabii.com/", tabii.referrer)
        assertEquals("https://www.tabii.com", tabii.origin)
    }

    @Test
    fun preservesExistingUserHeaders() {
        val customReferrer = "https://custom.provider.com/"
        val customOrigin = "https://custom.provider.com"
        val resolved = StreamHeaderResolver.resolve(
            url = "https://live.duhnet.tv/S2/HLS_LIVE/kanald/playlist.m3u8",
            existingReferrer = customReferrer,
            existingOrigin = customOrigin,
        )
        assertEquals(customReferrer, resolved.referrer)
        assertEquals(customOrigin, resolved.origin)
    }

    @Test
    fun leavesHeadersNullForUnknownDomains() {
        val resolved = StreamHeaderResolver.resolve("https://myiptvserver.com:8080/live/user/pass/123.m3u8")
        assertNull(resolved.referrer)
        assertNull(resolved.origin)
    }
}

