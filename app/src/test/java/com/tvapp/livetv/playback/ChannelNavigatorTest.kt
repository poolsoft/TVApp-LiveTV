package com.tvapp.livetv.playback

import com.tvapp.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNavigatorTest {
    private val channels = listOf(
        channel("trt", "1"),
        channel("atv", "2"),
        channel("show", "5"),
    )

    @Test
    fun adjacent_wrapsAndStartsAtListEdge() {
        assertEquals("trt", ChannelNavigator.adjacent(channels, "show", 1)?.sourceKey)
        assertEquals("show", ChannelNavigator.adjacent(channels, "trt", -1)?.sourceKey)
        assertEquals("trt", ChannelNavigator.adjacent(channels, null, 1)?.sourceKey)
    }

    @Test
    fun byNumber_usesDisplayedNumber() {
        assertEquals("show", ChannelNavigator.byNumber(channels, "5")?.sourceKey)
        assertNull(ChannelNavigator.byNumber(channels, "3"))
    }

    @Test
    fun previousDistinct_skipsCurrentEntries() {
        assertEquals(
            "trt",
            ChannelNavigator.previousDistinct(
                channels,
                listOf("atv", "atv", "trt"),
                "atv",
            )?.sourceKey,
        )
    }

    private fun channel(key: String, number: String) = LiveChannel(
        id = key.hashCode().toLong(),
        sourceKey = key,
        inputId = "input/HW0",
        displayNumber = number,
        displayName = key,
        uri = "content://android.media.tv/channel/$number",
    )
}
