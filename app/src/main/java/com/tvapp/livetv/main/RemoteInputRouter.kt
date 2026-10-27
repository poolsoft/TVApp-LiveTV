package com.tvapp.livetv.main

import android.view.KeyEvent
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.ui.RemoteAction
import kotlinx.coroutines.CoroutineScope

/**
 * Faz 4: MainActivity'deki kumanda girdi orkestrasyonu ve yonlendirme router'i.
 *
 * Davranis degisikligi yok: MainActivity.dispatchKeyEvent icindeki
 * tum yonlendirme ve tus kapisi akisi buraya tasinmistir.
 * Activity'ye bagimlilik yalnizca [RemoteInputEnv] arayuzu uzerindendir.
 */
class RemoteInputRouter(
    private val scope: CoroutineScope,
    private val longPressTimeoutMillis: () -> Long,
    private val env: RemoteInputEnv,
) {

    private fun newGate() = LongPressKeyGate(scope, longPressTimeoutMillis)

    val alphabetRightKeyGate by lazy { newGate() }
    val settingsKeyGate by lazy { newGate() }
    val lastChannelKeyGate by lazy { newGate() }
    val okKeyGate by lazy { newGate() }
    val greenKeyGate by lazy { newGate() }
    val yellowKeyGate by lazy { newGate() }

    /** Tum tus kapilarini iptal eder (kapanis veya panel kapatma aninda). */
    fun cancelAllGates() {
        alphabetRightKeyGate.cancel()
        settingsKeyGate.cancel()
        lastChannelKeyGate.cancel()
        okKeyGate.cancel()
        greenKeyGate.cancel()
        yellowKeyGate.cancel()
    }

    /**
     * Gelen kumanda olayini (KeyEvent) TVApp katmanlarina yonlendirir.
     * Tuketildiyse true, tuketilmediyse fallbackSuper cagrilip sonucu doner.
     */
    fun dispatch(event: KeyEvent, fallbackSuper: (KeyEvent) -> Boolean): Boolean {
        if (env.isPlaybackErrorVisible()) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN) env.dismissStatusDialog()
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) env.retryPlaybackFromError()
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                if (event.action == KeyEvent.ACTION_DOWN) env.zap(1)
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                if (event.action == KeyEvent.ACTION_DOWN) env.zap(-1)
                return true
            }
            return when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> true
                else -> true
            }
        }

        if (env.isStatusOsd()) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN) env.dismissStatusDialog()
                return true
            }
            return when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> fallbackSuper(event)
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> true
                else -> true
            }
        }

        if (env.isVodPlaybackActive()) {
            if (env.handleVodPlayerOverlay(event)) return true
        }

        val routedAction = env.routeRemoteAction(event)
        if (routedAction == RemoteAction.FORWARD_TO_DIALOG) {
            return fallbackSuper(event)
        }
        if (routedAction == RemoteAction.IGNORE_CHANNEL_NAVIGATION) return true
        if (routedAction == RemoteAction.OPEN_VOD_LIBRARY) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) env.openVodLibrary()
            return true
        }

        if (env.isMultiViewAdding() && env.isChannelPanelVisible() && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) env.showIptvGridPicker(env.getMultiViewDraft())
            return true
        }

        if (RemoteInputRules.isSettingsKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                settingsKeyGate.onDown(
                    event.keyCode,
                    onLongPress = { env.openSystemSettings(it) },
                    onShortPress = { env.openDisplaySettings(it) },
                )
            } else if (event.action == KeyEvent.ACTION_UP) {
                settingsKeyGate.onUp()
            }
            return true
        }

        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_PROG_RED &&
            !env.isChannelPanelVisible() &&
            !env.isIptvGridActive()
        ) {
            if (env.returnToCatchUpChannel()) return true
        }

        if (routedAction == RemoteAction.HANDLE_GRID) {
            env.handleGridKeyEvent(event)
            return true
        }

        if (routedAction == RemoteAction.DISMISS_STATUS) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN) env.dismissStatusDialog()
                return true
            }
            return fallbackSuper(event)
        }

        if (routedAction == RemoteAction.DISMISS_RECENT_CHANNELS || env.isRecentChannelsOsd()) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                env.hideRecentChannels()
                return true
            }
            return fallbackSuper(event)
        }

        if (event.keyCode == KeyEvent.KEYCODE_LAST_CHANNEL) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                lastChannelKeyGate.onDown(
                    event.keyCode,
                    onLongPress = { env.showRecentChannels() },
                    onShortPress = { env.openPreviousChannel() },
                )
            } else if (event.action == KeyEvent.ACTION_UP) {
                lastChannelKeyGate.onUp()
            }
            return true
        }

        if (RemoteInputRules.isIptvMediaKey(event.keyCode) && env.isIptvChannel()) {
            if (event.action == KeyEvent.ACTION_DOWN) env.handleIptvMediaKey(event.keyCode)
            return true
        }

        val hasNoBlockingPlaybackOsd = env.hasNoBlockingPlaybackOsd()
        val isVod = env.isVodMode() || env.isVodContentKind()

        if (
            RemoteInputRules.canTriggerOkGate(
                RemoteInputRules.isOkKey(event.keyCode),
                hasNoBlockingPlaybackOsd,
                isVod,
                env.isIptvControlsInteractive(),
            )
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                okKeyGate.onDown(
                    event.keyCode,
                    onLongPress = { env.showQuickHubDialog() },
                    onShortPress = { env.toggleChannelPanel() },
                )
            } else if (event.action == KeyEvent.ACTION_UP) {
                okKeyGate.onUp()
            }
            return true
        }

        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            RemoteInputRules.canQuickSeek(
                env.isIptvChannel(),
                isVod,
                hasNoBlockingPlaybackOsd,
                env.isIptvControlsInteractive(),
                event.keyCode,
            )
        ) {
            val offset = RemoteInputRules.quickSeekOffset(event.keyCode, 10_000L)
            if (offset != null) {
                env.quickSeek(offset)
                return true
            }
        }

        val greenOwnedByIptvControls = RemoteInputRules.isGreenKeyOwnedByIptvControls(
            isIptv = env.isIptvChannel(),
            isChannelPanelClosed = !env.isChannelPanelVisible(),
            iptvControlsInteractive = env.isIptvControlsInteractive(),
            isPlaybackContainerVisible = env.isIptvPlaybackContainerVisible(),
        )

        if (
            event.action == KeyEvent.ACTION_DOWN &&
            env.isIptvChannel() &&
            !env.isChannelPanelVisible() &&
            env.isIptvControlsInteractive() &&
            env.isIptvPlaybackContainerVisible()
        ) {
            if (env.handleIptvControlsKey(event.keyCode)) return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_PROG_GREEN && !greenOwnedByIptvControls) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                env.cancelFocusedTune()
                greenKeyGate.onDown(
                    event.keyCode,
                    onLongPress = { env.showIptvGridPicker() },
                    onShortPress = {
                        env.cancelFocusedTune()
                        env.showIptvGridPicker()
                    },
                )
            } else if (event.action == KeyEvent.ACTION_UP) {
                greenKeyGate.onUp()
            }
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_PROG_YELLOW && env.isChannelPanelVisible()) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                yellowKeyGate.onDown(
                    event.keyCode,
                    onLongPress = { env.showChannelListModeDialog() },
                    onShortPress = { env.cycleChannelListMode() },
                )
            } else if (event.action == KeyEvent.ACTION_UP) {
                yellowKeyGate.onUp()
            }
            return true
        }

        if (
            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
            env.isChannelAlphabetNavigationEnabled() &&
            env.isChannelPanelVisible() &&
            env.isChannelListFocused()
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                alphabetRightKeyGate.onDown(
                    event.keyCode,
                    onLongPress = { env.openChannelAlphabet() },
                    onShortPress = { if (!event.isCanceled) env.pageChannelList(1) },
                )
            } else if (event.action == KeyEvent.ACTION_UP) {
                alphabetRightKeyGate.onUp()
            }
            return true
        }

        if (event.action != KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_WINDOW) return true
        if (event.action != KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) return true

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (env.isChannelPanelVisible()) env.scheduleChannelPanelClose()
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> env.handleBackNavigation()
                KeyEvent.KEYCODE_CHANNEL_UP -> env.zap(1)
                KeyEvent.KEYCODE_CHANNEL_DOWN -> env.zap(-1)
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (env.hasNumberInput()) {
                        env.commitChannelNumber()
                    } else if (env.isParentalLockVisible() && env.hasCurrentChannel()) {
                        env.requestChannelPin()
                    } else if (isVod) {
                        env.showIptvPlaybackControls(activateControls = true)
                    } else if (!env.isChannelPanelVisible()) {
                        env.toggleChannelPanel()
                    } else {
                        env.cancelFocusedTune()
                        return fallbackSuper(event)
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!env.isChannelPanelVisible()) {
                        env.zap(1)
                    } else if (env.wrapChannelList(-1)) {
                        Unit
                    } else {
                        return fallbackSuper(event)
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!env.isChannelPanelVisible()) {
                        env.zap(-1)
                    } else if (env.wrapChannelList(1)) {
                        Unit
                    } else {
                        return fallbackSuper(event)
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (env.isChannelPanelVisible() && env.isChannelListFocused()) {
                        env.pageChannelList(-1)
                    } else {
                        return fallbackSuper(event)
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (env.isChannelPanelVisible() && env.isChannelListFocused()) {
                        env.pageChannelList(1)
                    } else {
                        return fallbackSuper(event)
                    }
                }
                KeyEvent.KEYCODE_PROG_RED -> {
                    if (env.isChannelPanelVisible()) {
                        if (env.channelPanelContentIsIptvLibrary) {
                            env.openIptvEditor()
                        } else {
                            env.openChannelEditor()
                        }
                    } else {
                        env.showPlaybackModeDialog()
                    }
                }
                KeyEvent.KEYCODE_PROG_GREEN -> env.showIptvGridPicker()
                KeyEvent.KEYCODE_PROG_YELLOW -> {
                    if (!env.isChannelPanelVisible()) return fallbackSuper(event)
                }
                KeyEvent.KEYCODE_PROG_BLUE -> {
                    if (env.isChannelPanelVisible()) {
                        if (env.channelPanelContentIsIptvLibrary) {
                            env.showIptvLibraryFilterDialog()
                        } else {
                            env.showChannelSearchDialog()
                        }
                    } else {
                        env.openDisplaySettings(event.keyCode)
                    }
                }
                KeyEvent.KEYCODE_WINDOW -> env.enterTvPictureInPicture()
                KeyEvent.KEYCODE_LANGUAGE_SWITCH,
                KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
                KeyEvent.KEYCODE_TV_AUDIO_DESCRIPTION -> env.showAudioTracks()
                KeyEvent.KEYCODE_CAPTIONS,
                RemoteInputRules.KEYCODE_CAPTIONS_ALT -> env.showSubtitleTracks()
                KeyEvent.KEYCODE_TV_TELETEXT,
                RemoteInputRules.KEYCODE_TELETEXT_ALT -> env.showTxtTracks()
                KeyEvent.KEYCODE_TV_INPUT -> env.showPhysicalInputSelector()
                KeyEvent.KEYCODE_GUIDE -> env.openProgramGuide()
                KeyEvent.KEYCODE_SEARCH -> {
                    if (!env.isChannelPanelVisible()) env.showChannelPanel(expanded = false)
                    env.showChannelSearchDialog()
                }
                KeyEvent.KEYCODE_INFO -> {
                    if (env.isInfoBarVisible()) {
                        env.openProgramGuide()
                    } else {
                        env.showInfoBarOrChannel()
                    }
                }
                KeyEvent.KEYCODE_MENU -> env.toggleChannelPanel()
                else -> {
                    val digit = RemoteInputRules.digitFromKey(event.keyCode)
                        ?: return fallbackSuper(event)
                    env.appendChannelDigit(digit)
                }
            }
            return true
        }

        return fallbackSuper(event)
    }
}

/**
 * [RemoteInputRouter]'in bagimli oldugu MainActivity ortam arayuzu.
 */
interface RemoteInputEnv {
    fun isVodPlaybackActive(): Boolean
    fun handleVodPlayerOverlay(event: KeyEvent): Boolean
    fun isVodMode(): Boolean
    fun isVodContentKind(): Boolean
    fun isIptvChannel(): Boolean
    fun handleIptvMediaKey(keyCode: Int)
    fun quickSeek(offsetMs: Long)
    fun handleIptvControlsKey(keyCode: Int): Boolean

    fun routeRemoteAction(event: KeyEvent): RemoteAction
    fun isChannelPanelVisible(): Boolean
    fun isChannelListFocused(): Boolean
    fun isIptvControlsInteractive(): Boolean
    fun isIptvPlaybackContainerVisible(): Boolean
    fun isStatusOsd(): Boolean
    fun isRecentChannelsOsd(): Boolean
    fun hasNoBlockingPlaybackOsd(): Boolean
    fun returnToCatchUpChannel(): Boolean
    fun isMultiViewAdding(): Boolean
    fun getMultiViewDraft(): List<LiveChannel>
    fun isIptvGridActive(): Boolean
    fun handleGridKeyEvent(event: KeyEvent): Boolean
    fun isChannelAlphabetNavigationEnabled(): Boolean

    fun openSystemSettings(keyCode: Int)
    fun openDisplaySettings(keyCode: Int)
    fun showRecentChannels()
    fun openPreviousChannel()
    fun showQuickHubDialog()
    fun toggleChannelPanel()
    fun showIptvGridPicker(draft: List<LiveChannel>? = null)
    fun showChannelListModeDialog()
    fun cycleChannelListMode()
    fun openChannelAlphabet()
    fun cancelFocusedTune()

    fun scheduleChannelPanelClose()
    fun handleBackNavigation()
    fun zap(direction: Int)
    fun pageChannelList(direction: Int)
    fun wrapChannelList(direction: Int): Boolean
    fun hasNumberInput(): Boolean
    fun commitChannelNumber()
    fun isParentalLockVisible(): Boolean
    fun hasCurrentChannel(): Boolean
    fun requestChannelPin()
    fun showIptvPlaybackControls(activateControls: Boolean = false)
    fun openChannelEditor()
    fun openIptvEditor()
    fun showPlaybackModeDialog()
    fun showIptvLibraryFilterDialog()
    fun showChannelSearchDialog()
    fun enterTvPictureInPicture()
    fun showAudioTracks()
    fun showSubtitleTracks()
    fun showTxtTracks()
    fun showPhysicalInputSelector()
    fun openProgramGuide()
    fun showChannelPanel(expanded: Boolean)
    fun isInfoBarVisible(): Boolean
    fun showInfoBarOrChannel()
    fun appendChannelDigit(digit: Int)
    fun dismissStatusDialog()
    fun hideRecentChannels()
    fun openVodLibrary()
    fun isPlaybackErrorVisible(): Boolean
    fun retryPlaybackFromError()
    val channelPanelContentIsIptvLibrary: Boolean
}
