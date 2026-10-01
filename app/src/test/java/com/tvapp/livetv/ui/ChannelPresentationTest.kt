package com.tvapp.livetv.ui

import com.tvapp.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelPresentationTest {
    private val channel = LiveChannel(1, "test", "input", "1", "Example UHD 4K FHD HD SD", "test")

    @Test
    fun nameDoesNotDetermineQuality() {
        assertNull(channel.qualityLabel())
        assertNull(channel.copy(videoFormat = "unknown").qualityLabel())
    }

    @Test
    fun reportedFormatOverridesMisleadingName() {
        assertEquals("576i", channel.copy(videoFormat = "VIDEO_FORMAT_576I").qualityLabel())
        assertEquals("1080p", channel.copy(videoFormat = "VIDEO_FORMAT_1080P").qualityLabel())
        assertEquals("4K", channel.copy(displayName = "Example SD", videoFormat = "VIDEO_FORMAT_2160P").qualityLabel())
    }
}
