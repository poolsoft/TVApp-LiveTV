package com.tvapp.livetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQualityTest {
    @Test
    fun classifiesReportedTrackDimensions() {
        assertEquals("4K", VideoQuality.resolutionLabel(3840, 2160, ""))
        assertEquals("FHD", VideoQuality.resolutionLabel(1920, 1080, ""))
        assertEquals("HD", VideoQuality.resolutionLabel(1280, 720, ""))
        assertEquals("SD", VideoQuality.resolutionLabel(720, 576, ""))
    }

    @Test
    fun classifiesTifVideoFormatWithoutUsingChannelName() {
        assertEquals("4K", VideoQuality.resolutionLabel(0, 0, "VIDEO_FORMAT_2160P"))
        assertEquals("1080i", VideoQuality.resolutionLabel(0, 0, "VIDEO_FORMAT_1080I"))
        assertEquals("720p", VideoQuality.resolutionLabel(0, 0, "VIDEO_FORMAT_720P"))
        assertEquals("576i", VideoQuality.resolutionLabel(0, 0, "VIDEO_FORMAT_576I"))
        assertNull(VideoQuality.resolutionLabel(0, 0, ""))
        assertNull(VideoQuality.resolutionLabel(0, 0, "TRT 4K HD"))
    }
}
