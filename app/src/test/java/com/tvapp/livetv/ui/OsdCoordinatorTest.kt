package com.tvapp.livetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsdCoordinatorTest {
    @Test
    fun `channel panel keeps companion infobar but disables IPTV interaction`() {
        val coordinator = OsdCoordinator {}

        coordinator.showChannelPanel(expanded = true, iptvChrome = true)

        assertEquals(PrimaryOsd.CHANNEL_PANEL, coordinator.state.primaryOsd)
        assertTrue(coordinator.state.infoBarVisible)
        assertTrue(coordinator.state.iptvChromeVisible)
        assertFalse(coordinator.state.iptvControlsInteractive)
        assertTrue(coordinator.state.channelPanelExpanded)

        coordinator.hideInfoBar()
        assertTrue(coordinator.state.infoBarVisible)

        coordinator.hideChannelPanel(keepInfoBar = true)
        assertEquals(PrimaryOsd.NONE, coordinator.state.primaryOsd)
        assertTrue(coordinator.state.infoBarVisible)
        coordinator.hideInfoBar()
        assertFalse(coordinator.state.infoBarVisible)
    }

    @Test
    fun `IPTV controls replace channel panel and own the infobar`() {
        val coordinator = OsdCoordinator {}
        coordinator.showChannelPanel(expanded = false, iptvChrome = true)

        coordinator.showIptvControls(interactive = true)

        assertEquals(PrimaryOsd.IPTV_CONTROLS, coordinator.state.primaryOsd)
        assertTrue(coordinator.state.infoBarVisible)
        assertTrue(coordinator.state.iptvControlsInteractive)
        assertFalse(coordinator.state.channelPanelExpanded)
    }

    @Test
    fun `recent channels replaces every other primary OSD`() {
        val coordinator = OsdCoordinator {}
        coordinator.showIptvControls(interactive = true)

        coordinator.showRecentChannels()

        assertEquals(PrimaryOsd.RECENT_CHANNELS, coordinator.state.primaryOsd)
        assertFalse(coordinator.state.infoBarVisible)
        assertFalse(coordinator.state.iptvChromeVisible)
    }

    @Test
    fun `grid mode clears primary OSD state`() {
        val coordinator = OsdCoordinator {}
        coordinator.showChannelPanel(expanded = true, iptvChrome = false)

        coordinator.setPlaybackMode(PlaybackSurfaceMode.IPTV_GRID)

        assertEquals(PlaybackSurfaceMode.IPTV_GRID, coordinator.state.playbackMode)
        assertEquals(PrimaryOsd.NONE, coordinator.state.primaryOsd)
        assertFalse(coordinator.state.infoBarVisible)
    }

    @Test
    fun `parental lock remains primary when infobar refreshes`() {
        val coordinator = OsdCoordinator {}
        coordinator.showParentalLock()

        coordinator.showInfoBar(iptvChrome = false)

        assertEquals(PrimaryOsd.PARENTAL_LOCK, coordinator.state.primaryOsd)
        assertTrue(coordinator.state.infoBarVisible)
    }
}
