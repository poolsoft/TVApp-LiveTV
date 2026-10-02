package com.tvapp.livetv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteActionRouterTest {
    private val router = RemoteActionRouter()

    @Test
    fun `dialog always owns remote input`() {
        RemoteKey.entries.forEach { key ->
            assertEquals(
                RemoteAction.FORWARD_TO_DIALOG,
                route(dialog = true, key = key),
            )
        }
    }

    @Test
    fun `VOD never routes channel number or last keys to live navigation`() {
        val keys = listOf(RemoteKey.CHANNEL, RemoteKey.NUMBER, RemoteKey.LAST_CHANNEL)
        val layers = listOf(PrimaryOsd.NONE, PrimaryOsd.IPTV_CONTROLS, PrimaryOsd.PARENTAL_LOCK)
        layers.forEach { primary ->
            keys.forEach { key ->
                RemoteKeyPhase.entries.forEach { phase ->
                    assertEquals(RemoteAction.IGNORE_CHANNEL_NAVIGATION,
                        route(primary = primary, isVod = true, key = key, phase = phase))
                }
            }
        }
        keys.forEach { key -> assertEquals(RemoteAction.FORWARD_TO_DIALOG,
            route(dialog = true, isVod = true, key = key)) }
    }

    @Test
    fun `VOD opens its own library and back dismisses overlays first`() {
        listOf(RemoteKey.OK, RemoteKey.MENU, RemoteKey.GUIDE, RemoteKey.BACK).forEach { key ->
            assertEquals(RemoteAction.OPEN_VOD_LIBRARY, route(isVod = true, key = key))
        }
        assertEquals(RemoteAction.HIDE_INFO_BAR, route(isVod = true, infoBar = true))
        assertEquals(RemoteAction.HANDLE_IPTV_CONTROLS,
            route(isVod = true, primary = PrimaryOsd.IPTV_CONTROLS))
        assertEquals(RemoteAction.HANDLE_IPTV_MEDIA,
            route(isVod = true, isIptv = true, key = RemoteKey.MEDIA))
    }

    @Test
    fun `channel picker owns input over background grid`() {
        assertEquals(
            RemoteAction.HANDLE_CHANNEL_PANEL,
            route(mode = PlaybackSurfaceMode.IPTV_GRID, primary = PrimaryOsd.CHANNEL_PANEL),
        )
    }

    @Test
    fun `grid keeps input when the channel picker is closed`() {
        assertEquals(RemoteAction.HANDLE_GRID, route(mode = PlaybackSurfaceMode.IPTV_GRID))
    }

    @Test
    fun `primary OSD owns direction channel color media and OK keys`() {
        val keys = listOf(
            RemoteKey.DIRECTION,
            RemoteKey.CHANNEL,
            RemoteKey.COLOR,
            RemoteKey.MEDIA,
            RemoteKey.OK,
        )
        val cases = listOf(
            PrimaryOsd.CHANNEL_PANEL to RemoteAction.HANDLE_CHANNEL_PANEL,
            PrimaryOsd.IPTV_CONTROLS to RemoteAction.HANDLE_IPTV_CONTROLS,
            PrimaryOsd.PARENTAL_LOCK to RemoteAction.HANDLE_PARENTAL_LOCK,
        )
        cases.forEach { (primary, action) ->
            keys.forEach { key -> assertEquals(action, route(primary = primary, key = key)) }
        }
    }

    @Test
    fun `back dismisses the topmost surface and never exits playback`() {
        assertEquals(RemoteAction.DISMISS_STATUS, route(primary = PrimaryOsd.STATUS))
        assertEquals(RemoteAction.DISMISS_RECENT_CHANNELS, route(primary = PrimaryOsd.RECENT_CHANNELS))
        assertEquals(
            RemoteAction.HIDE_INFO_BAR,
            route(infoBar = true),
        )
        assertEquals(RemoteAction.SHOW_RECENT_CHANNELS, route())
    }

    @Test
    fun `IPTV media and last channel have explicit owners`() {
        assertEquals(
            RemoteAction.HANDLE_IPTV_MEDIA,
            route(isIptv = true, key = RemoteKey.MEDIA),
        )
        assertEquals(
            RemoteAction.HANDLE_LAST_CHANNEL_PRESS,
            route(key = RemoteKey.LAST_CHANNEL),
        )
    }

    @Test
    fun `settings press remains global outside dialogs`() {
        assertEquals(
            RemoteAction.HANDLE_SETTINGS_PRESS,
            route(primary = PrimaryOsd.CHANNEL_PANEL, key = RemoteKey.SETTINGS),
        )
    }

    private fun route(
        dialog: Boolean = false,
        mode: PlaybackSurfaceMode = PlaybackSurfaceMode.SINGLE,
        primary: PrimaryOsd = PrimaryOsd.NONE,
        infoBar: Boolean = false,
        isIptv: Boolean = false,
        isVod: Boolean = false,
        key: RemoteKey = RemoteKey.BACK,
        phase: RemoteKeyPhase = RemoteKeyPhase.DOWN,
    ): RemoteAction = router.route(
        RemoteUiContext(
            playbackUiState = PlaybackUiState(
                primaryOsd = primary,
                playbackMode = mode,
                infoBarVisible = infoBar,
            ),
            dialogOwnsInput = dialog,
            isIptv = isIptv,
            isVod = isVod,
        ),
        key,
        phase,
    )
}
