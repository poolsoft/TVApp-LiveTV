package com.tvapp.livetv.vod

import org.junit.Assert.assertEquals
import org.junit.Test

class VodTitleCleanerTest {

    @Test
    fun cleansComplexIptvTitleWithYearAndTags() {
        val input = "⚡ Hızlı ve Öfkeli 10 (2023) 1080p TR DUBLAJ ⚡"
        val result = VodTitleCleaner.clean(input)
        assertEquals("Hızlı ve Öfkeli 10", result.cleanTitle)
        assertEquals("2023", result.year)
    }

    @Test
    fun cleansDuneTitleWithUhd() {
        val input = "Dune: Part Two (2024) 4K UHD Remux"
        val result = VodTitleCleaner.clean(input)
        assertEquals("Dune Part Two", result.cleanTitle)
        assertEquals("2024", result.year)
    }

    @Test
    fun preservesSimpleTitleWithoutYear() {
        val input = "Kemal Sunal Meraklı Köfteci"
        val result = VodTitleCleaner.clean(input)
        assertEquals("Kemal Sunal Meraklı Köfteci", result.cleanTitle)
        assertEquals(null, result.year)
    }

    @Test
    fun cleansDeadpoolWithAltyazi() {
        val input = "[TR ALTYAZI] Deadpool & Wolverine 2024 WEB-DL 1080p"
        val result = VodTitleCleaner.clean(input)
        assertEquals("Deadpool & Wolverine", result.cleanTitle)
        assertEquals("2024", result.year)
    }
}
