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
    fun `playback modes take priority over OSD state`() {
        assertEquals(
            RemoteAction.HANDLE_GRID,
            route(mode = PlaybackSurfaceMode.IPTV_GRID, primary = PrimaryOsd.CHANNEL_PANEL),
        )
        assertEquals(
            RemoteAction.HANDLE_MULTI_VIEW,
            route(mode = PlaybackSurfaceMode.MULTI_VIEW, primary = PrimaryOsd.IPTV_CONTROLS),
        )
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
        ),
        key,
        phase,
    )
}
