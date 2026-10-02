package com.tvapp.livetv.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChannelContentTypeTest {
    private val channel = LiveChannel(1, "fixture", "fixture", "1", "Film HD", "", source = LiveChannel.Source.IPTV)

    @Test fun vodUsesSourceMetadataNotTitleOrQuality() {
        assertFalse(channel.isVodContent())
        assertTrue(channel.copy(iptvContentType = "vod").isVodContent())
        assertFalse(channel.copy(iptvContentType = "LIVE").isVodContent())
        assertFalse(channel.copy(iptvContentType = "CATCHUP").isVodContent())
        assertFalse(channel.copy(source = LiveChannel.Source.TIF, iptvContentType = "VOD").isVodContent())
    }
}
