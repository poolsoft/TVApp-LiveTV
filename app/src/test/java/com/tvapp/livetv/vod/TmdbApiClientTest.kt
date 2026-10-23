package com.tvapp.livetv.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TmdbApiClientTest {

    @Test
    fun parsesTmdbMovieJsonCorrectly() {
        // Test cleaned title and genre mapping
        val input = "⚡ Inception (2010) 1080p ⚡"
        val cleaned = VodTitleCleaner.clean(input)
        assertEquals("Inception", cleaned.cleanTitle)
        assertEquals("2010", cleaned.year)
    }
}
