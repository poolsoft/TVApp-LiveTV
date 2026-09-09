package com.tvapp.livetv.ui

enum class PrimaryOsd {
    NONE,
    CHANNEL_PANEL,
    IPTV_CONTROLS,
    RECENT_CHANNELS,
    PARENTAL_LOCK,
    STATUS,
}

enum class PlaybackSurfaceMode {
    SINGLE,
    IPTV_GRID,
    MULTI_VIEW,
    IPTV_OVERLAY,
    INTERNAL_MINI_PLAYER,
}

data class PlaybackUiState(
    val primaryOsd: PrimaryOsd = PrimaryOsd.NONE,
    val playbackMode: PlaybackSurfaceMode = PlaybackSurfaceMode.SINGLE,
    val infoBarVisible: Boolean = false,
    val iptvChromeVisible: Boolean = false,
    val iptvControlsInteractive: Boolean = false,
    val channelPanelExpanded: Boolean = false,
)

class OsdCoordinator(
    private val render: (PlaybackUiState) -> Unit,
) {
    var state: PlaybackUiState = PlaybackUiState()
        private set

    fun showInfoBar(iptvChrome: Boolean, interactive: Boolean = false) {
        val retainedPrimary = when (state.primaryOsd) {
            PrimaryOsd.CHANNEL_PANEL, PrimaryOsd.PARENTAL_LOCK -> state.primaryOsd
            PrimaryOsd.IPTV_CONTROLS -> if (iptvChrome) PrimaryOsd.IPTV_CONTROLS else PrimaryOsd.NONE
            else -> PrimaryOsd.NONE
        }
        update(
            state.copy(
                primaryOsd = retainedPrimary,
                infoBarVisible = true,
                iptvChromeVisible = iptvChrome,
                iptvControlsInteractive = interactive && iptvChrome,
            ),
        )
    }

    fun hideInfoBar() {
        if (state.primaryOsd == PrimaryOsd.CHANNEL_PANEL) return
        update(
            state.copy(
                primaryOsd = if (state.primaryOsd == PrimaryOsd.IPTV_CONTROLS) {
                    PrimaryOsd.NONE
                } else {
                    state.primaryOsd
                },
                infoBarVisible = false,
                iptvChromeVisible = false,
                iptvControlsInteractive = false,
            ),
        )
    }

    fun showChannelPanel(expanded: Boolean, iptvChrome: Boolean) {
        update(
            state.copy(
                primaryOsd = PrimaryOsd.CHANNEL_PANEL,
                infoBarVisible = true,
                iptvChromeVisible = iptvChrome,
                iptvControlsInteractive = false,
                channelPanelExpanded = expanded,
            ),
        )
    }

    fun hideChannelPanel(keepInfoBar: Boolean) {
        if (state.primaryOsd != PrimaryOsd.CHANNEL_PANEL) return
        update(
            state.copy(
                primaryOsd = PrimaryOsd.NONE,
                infoBarVisible = keepInfoBar,
                channelPanelExpanded = false,
            ),
        )
    }

    fun showIptvControls(interactive: Boolean) {
        update(
            state.copy(
                primaryOsd = PrimaryOsd.IPTV_CONTROLS,
                infoBarVisible = true,
                iptvChromeVisible = true,
                iptvControlsInteractive = interactive,
                channelPanelExpanded = false,
            ),
        )
    }

    fun hideIptvControls(hideInfoBar: Boolean) {
        update(
            state.copy(
                primaryOsd = if (state.primaryOsd == PrimaryOsd.IPTV_CONTROLS) {
                    PrimaryOsd.NONE
                } else {
                    state.primaryOsd
                },
                infoBarVisible = if (hideInfoBar) false else state.infoBarVisible,
                iptvChromeVisible = false,
                iptvControlsInteractive = false,
            ),
        )
    }

    fun showRecentChannels() = replacePrimary(PrimaryOsd.RECENT_CHANNELS)

    fun hideRecentChannels() {
        if (state.primaryOsd == PrimaryOsd.RECENT_CHANNELS) replacePrimary(PrimaryOsd.NONE)
    }

    fun showParentalLock() {
        update(
            state.copy(
                primaryOsd = PrimaryOsd.PARENTAL_LOCK,
                infoBarVisible = true,
                iptvChromeVisible = false,
                iptvControlsInteractive = false,
                channelPanelExpanded = false,
            ),
        )
    }

    fun hideParentalLock() {
        if (state.primaryOsd == PrimaryOsd.PARENTAL_LOCK) replacePrimary(PrimaryOsd.NONE)
    }

    fun showStatus() = replacePrimary(PrimaryOsd.STATUS)

    fun hideStatus() {
        if (state.primaryOsd == PrimaryOsd.STATUS) replacePrimary(PrimaryOsd.NONE)
    }

    fun setPlaybackMode(mode: PlaybackSurfaceMode) {
        val clearsOsd = mode == PlaybackSurfaceMode.IPTV_GRID
        update(
            state.copy(
                playbackMode = mode,
                primaryOsd = if (clearsOsd) PrimaryOsd.NONE else state.primaryOsd,
                infoBarVisible = if (clearsOsd) false else state.infoBarVisible,
                iptvChromeVisible = if (clearsOsd) false else state.iptvChromeVisible,
                iptvControlsInteractive = if (clearsOsd) false else state.iptvControlsInteractive,
                channelPanelExpanded = if (clearsOsd) false else state.channelPanelExpanded,
            ),
        )
    }

    fun clearOsd() {
        update(
            state.copy(
                primaryOsd = PrimaryOsd.NONE,
                infoBarVisible = false,
                iptvChromeVisible = false,
                iptvControlsInteractive = false,
                channelPanelExpanded = false,
            ),
        )
    }

    fun reset() = update(PlaybackUiState())

    private fun replacePrimary(primaryOsd: PrimaryOsd) {
        update(
            state.copy(
                primaryOsd = primaryOsd,
                infoBarVisible = false,
                iptvChromeVisible = false,
                iptvControlsInteractive = false,
                channelPanelExpanded = false,
            ),
        )
    }

    private fun update(next: PlaybackUiState) {
        if (state == next) return
        state = next
        render(next)
    }
}
