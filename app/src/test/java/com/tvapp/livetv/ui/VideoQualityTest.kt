package com.tvapp.livetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQualityTest {
    @Test
    fun classifiesReportedTrackDimensions() {
        assertEquals("4K", VideoQuality.resolutionLabel(3840, 2160, ""))
        assertEquals("1080p", VideoQuality.resolutionLabel(1920, 1080, ""))
        assertEquals("1080i", VideoQuality.resolutionLabel(1920, 1080, "", isTif = true))
        assertEquals("720p", VideoQuality.resolutionLabel(1280, 720, ""))
        assertEquals("576p", VideoQuality.resolutionLabel(720, 576, ""))
        assertEquals("576i", VideoQuality.resolutionLabel(720, 576, "", isTif = true))
    }

    @Test
    fun classifiesTifVideoFormatWithoutUsingChannelName() {
        assertEquals("4K", VideoQuality.resolutionLabel(0, 0, "VIDEO_FORMAT_2160P", isTif = true))
        assertEquals("1080i", VideoQuality.resolutionLabel(0, 0, "VIDEO_FORMAT_1080I", isTif = true))
        assertEquals("720p", VideoQuality.resolutionLabel(0, 0, "VIDEO_FORMAT_720P", isTif = true))
        assertEquals("576i", VideoQuality.resolutionLabel(0, 0, "VIDEO_FORMAT_576I", isTif = true))
        assertNull(VideoQuality.resolutionLabel(0, 0, ""))
        assertNull(VideoQuality.resolutionLabel(0, 0, "TRT 4K HD"))
    }
}
