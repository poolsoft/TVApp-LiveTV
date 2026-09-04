package com.tvapp.livetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQualityTest {
    @Test
    fun classifiesReportedTrackDimensions() {
        assertEquals("4K", VideoQuality.label(3840, 2160, ""))
        assertEquals("FHD", VideoQuality.label(1920, 1080, ""))
        assertEquals("HD", VideoQuality.label(1280, 720, ""))
        assertEquals("SD", VideoQuality.label(720, 576, ""))
    }

    @Test
    fun classifiesTifVideoFormatWithoutUsingChannelName() {
        assertEquals("4K", VideoQuality.label(0, 0, "VIDEO_FORMAT_2160P"))
        assertEquals("FHD", VideoQuality.label(0, 0, "VIDEO_FORMAT_1080I"))
        assertEquals("HD", VideoQuality.label(0, 0, "VIDEO_FORMAT_720P"))
        assertEquals("SD", VideoQuality.label(0, 0, "VIDEO_FORMAT_576I"))
        assertNull(VideoQuality.label(0, 0, ""))
        assertNull(VideoQuality.label(0, 0, "TRT 4K HD"))
    }
}
