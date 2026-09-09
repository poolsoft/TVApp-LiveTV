package com.tvapp.livetv.ui

enum class RemoteKey {
    BACK,
    SETTINGS,
    LAST_CHANNEL,
    MEDIA,
    DIRECTION,
    CHANNEL,
    COLOR,
    OK,
    NUMBER,
    INFO,
    GUIDE,
    INPUT,
    MENU,
    OTHER,
}

enum class RemoteKeyPhase {
    DOWN,
    REPEAT,
    UP,
}

enum class RemoteAction {
    FORWARD_TO_DIALOG,
    HANDLE_SETTINGS_PRESS,
    HANDLE_GRID,
    HANDLE_MULTI_VIEW,
    DISMISS_STATUS,
    DISMISS_RECENT_CHANNELS,
    HANDLE_PARENTAL_LOCK,
    HANDLE_IPTV_CONTROLS,
    HANDLE_CHANNEL_PANEL,
    HIDE_INFO_BAR,
    SHOW_RECENT_CHANNELS,
    HANDLE_LAST_CHANNEL_PRESS,
    HANDLE_IPTV_MEDIA,
    HANDLE_PLAYBACK,
}

data class RemoteUiContext(
    val playbackUiState: PlaybackUiState,
    val dialogOwnsInput: Boolean = false,
    val isIptv: Boolean = false,
)

/** Chooses the single UI layer that owns a remote event. */
class RemoteActionRouter {
    fun route(
        context: RemoteUiContext,
        key: RemoteKey,
        phase: RemoteKeyPhase,
    ): RemoteAction {
        if (context.dialogOwnsInput) return RemoteAction.FORWARD_TO_DIALOG
        if (key == RemoteKey.SETTINGS) return RemoteAction.HANDLE_SETTINGS_PRESS

        when (context.playbackUiState.playbackMode) {
            PlaybackSurfaceMode.IPTV_GRID -> return RemoteAction.HANDLE_GRID
            PlaybackSurfaceMode.MULTI_VIEW -> return RemoteAction.HANDLE_MULTI_VIEW
            else -> Unit
        }

        return when (context.playbackUiState.primaryOsd) {
            PrimaryOsd.STATUS -> if (key == RemoteKey.BACK) {
                RemoteAction.DISMISS_STATUS
            } else {
                RemoteAction.HANDLE_PLAYBACK
            }
            PrimaryOsd.RECENT_CHANNELS -> if (key == RemoteKey.BACK) {
                RemoteAction.DISMISS_RECENT_CHANNELS
            } else {
                RemoteAction.HANDLE_PLAYBACK
            }
            PrimaryOsd.PARENTAL_LOCK -> RemoteAction.HANDLE_PARENTAL_LOCK
            PrimaryOsd.IPTV_CONTROLS -> RemoteAction.HANDLE_IPTV_CONTROLS
            PrimaryOsd.CHANNEL_PANEL -> RemoteAction.HANDLE_CHANNEL_PANEL
            PrimaryOsd.NONE -> routePlayback(context, key, phase)
        }
    }

    private fun routePlayback(
        context: RemoteUiContext,
        key: RemoteKey,
        phase: RemoteKeyPhase,
    ): RemoteAction = when {
        key == RemoteKey.BACK && phase == RemoteKeyPhase.DOWN &&
            context.playbackUiState.infoBarVisible -> RemoteAction.HIDE_INFO_BAR
        key == RemoteKey.BACK && phase == RemoteKeyPhase.DOWN -> RemoteAction.SHOW_RECENT_CHANNELS
        key == RemoteKey.LAST_CHANNEL -> RemoteAction.HANDLE_LAST_CHANNEL_PRESS
        key == RemoteKey.MEDIA && context.isIptv -> RemoteAction.HANDLE_IPTV_MEDIA
        else -> RemoteAction.HANDLE_PLAYBACK
    }
}
