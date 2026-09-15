package com.tvapp.livetv.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class GridProfileSelectionTest {
    @Test
    fun fourCellGridUsesTightGridProfile() {
        assertEquals(IptvPlaybackProfile.GRID, gridProfileForCellCount(4))
        assertEquals(IptvPlaybackProfile.GRID, gridProfileForCellCount(5))
    }

    @Test
    fun twoAndThreeCellGridsUseSecondaryProfile() {
        assertEquals(IptvPlaybackProfile.SECONDARY, gridProfileForCellCount(2))
        assertEquals(IptvPlaybackProfile.SECONDARY, gridProfileForCellCount(3))
    }

    @Test
    fun degenerateCountsFallBackToSecondary() {
        assertEquals(IptvPlaybackProfile.SECONDARY, gridProfileForCellCount(0))
        assertEquals(IptvPlaybackProfile.SECONDARY, gridProfileForCellCount(1))
    }
}
