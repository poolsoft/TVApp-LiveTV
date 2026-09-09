package com.tvapp.livetv.data

import com.tvapp.livetv.data.local.IptvChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CatchUpUrlResolverTest {
    @Test
    fun resolvesCommonM3uTokens() {
        val channel = IptvChannelEntity(
            sourceKey = "iptv:1:test",
            sourceId = 1,
            tvgId = "trt1.tr",
            tvgName = "TRT 1",
            displayName = "TRT 1",
            streamUrl = "https://example.com/live.m3u8",
            logoUrl = null,
            groupTitle = "Ulusal",
            userAgent = null,
            referrer = null,
            originalIndex = 0,
            lastSeenAt = 0,
        )

        val result = CatchUpUrlResolver.resolve(
            "https://example.com/{channel_id}/{utc}/{duration}/{duration:60}",
            channel,
            1_000_000L,
            1_121_000L,
        )

        assertEquals("https://example.com/trt1.tr/1000/121/2", result)
    }
}
