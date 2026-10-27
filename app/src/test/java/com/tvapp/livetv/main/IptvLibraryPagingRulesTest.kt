package com.tvapp.livetv.main

import com.tvapp.livetv.data.IptvPageDirection
import com.tvapp.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvLibraryPagingRulesTest {

    private fun channels(count: Int) = (1..count).map { index ->
        LiveChannel(
            id = index.toLong(),
            sourceKey = "k$index",
            inputId = "iptv",
            displayNumber = "0",
            displayName = "Kanal $index",
            uri = "https://cdn/$index",
            source = LiveChannel.Source.IPTV,
        )
    }

    @Test
    fun firstPageStartsAtZero() {
        val window = IptvLibraryPagingRules.window(
            direction = IptvPageDirection.FIRST,
            currentWindowStart = 900,
            previousWindowSize = 250,
            loadedSize = 250,
            totalCount = 15_000,
            targetIndex = 0,
        )
        assertEquals(0, window.windowStart)
        assertFalse(window.hasPrevious)
        assertTrue(window.hasNext)
    }

    @Test
    fun nextPageMovesByPreviousWindowSize() {
        assertEquals(
            500,
            IptvLibraryPagingRules.windowStart(
                direction = IptvPageDirection.NEXT,
                currentWindowStart = 250,
                previousWindowSize = 250,
                loadedSize = 250,
                totalCount = 15_000,
                targetIndex = 0,
            ),
        )
    }

    @Test
    fun previousPageClampsAtZero() {
        assertEquals(
            0,
            IptvLibraryPagingRules.windowStart(
                direction = IptvPageDirection.PREVIOUS,
                currentWindowStart = 100,
                previousWindowSize = 250,
                loadedSize = 250,
                totalCount = 15_000,
                targetIndex = 0,
            ),
        )
        assertEquals(
            750,
            IptvLibraryPagingRules.windowStart(
                direction = IptvPageDirection.PREVIOUS,
                currentWindowStart = 1000,
                previousWindowSize = 250,
                loadedSize = 250,
                totalCount = 15_000,
                targetIndex = 0,
            ),
        )
    }

    @Test
    fun lastPageAlignsToTotal() {
        val window = IptvLibraryPagingRules.window(
            direction = IptvPageDirection.LAST,
            currentWindowStart = 0,
            previousWindowSize = 250,
            loadedSize = 250,
            totalCount = 15_050,
            targetIndex = 0,
        )
        assertEquals(14_800, window.windowStart)
        assertEquals(14_800 + 250, window.windowStart + 250)
        assertTrue(window.hasPrevious)
        assertFalse(window.hasNext)
    }

    @Test
    fun lastPageNeverNegativeForShortCatalog() {
        // TotalCount ile sayfa boyu celisirse pencere 0'a cekilir ve ileri
        // gezinme kapanir (tasima oncesi davranis).
        val window = IptvLibraryPagingRules.window(
            direction = IptvPageDirection.LAST,
            currentWindowStart = 0,
            previousWindowSize = 0,
            loadedSize = 40,
            totalCount = 10,
            targetIndex = 0,
        )
        assertEquals(0, window.windowStart)
        assertFalse(window.hasPrevious)
        assertFalse(window.hasNext)
    }

    @Test
    fun atIndexClampsIntoTargetRange() {
        assertEquals(
            1000,
            IptvLibraryPagingRules.windowStart(
                direction = IptvPageDirection.AT_INDEX,
                currentWindowStart = 0,
                previousWindowSize = 0,
                loadedSize = 250,
                totalCount = 5000,
                targetIndex = 1000,
            ),
        )
        assertEquals(
            4750,
            IptvLibraryPagingRules.windowStart(
                direction = IptvPageDirection.AT_INDEX,
                currentWindowStart = 0,
                previousWindowSize = 0,
                loadedSize = 250,
                totalCount = 5000,
                targetIndex = 4999,
            ),
        )
        assertEquals(
            0,
            IptvLibraryPagingRules.windowStart(
                direction = IptvPageDirection.AT_INDEX,
                currentWindowStart = 0,
                previousWindowSize = 0,
                loadedSize = 250,
                totalCount = 5000,
                targetIndex = -5,
            ),
        )
    }

    @Test
    fun focusTargetUsesLastRowForBackwardMoves() {
        assertEquals(249, IptvLibraryPagingRules.focusTarget(IptvPageDirection.PREVIOUS, 250))
        assertEquals(249, IptvLibraryPagingRules.focusTarget(IptvPageDirection.LAST, 250))
        assertEquals(0, IptvLibraryPagingRules.focusTarget(IptvPageDirection.FIRST, 250))
        assertEquals(0, IptvLibraryPagingRules.focusTarget(IptvPageDirection.NEXT, 250))
        assertEquals(0, IptvLibraryPagingRules.focusTarget(IptvPageDirection.AT_INDEX, 250))
        assertEquals(0, IptvLibraryPagingRules.focusTarget(IptvPageDirection.LAST, 0))
    }

    @Test
    fun renumberUsesAbsoluteCatalogPosition() {
        val result = IptvLibraryPagingRules.renumber(channels(3), windowStart = 250)
        assertEquals(listOf("251", "252", "253"), result.map { it.displayNumber })
        assertEquals(listOf("Kanal 1", "Kanal 2", "Kanal 3"), result.map { it.displayName })
    }
}
