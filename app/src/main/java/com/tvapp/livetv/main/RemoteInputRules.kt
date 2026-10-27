package com.tvapp.livetv.main

import android.view.KeyEvent

/**
 * Faz 4: Kumanda tus haritalandirmalari ve yonlendirme karar kurallari.
 *
 * Davranis degisikligi yok: MainActivity.dispatchKeyEvent icindeki
 * kosullarin saf, bagimsiz ve test edilebilir karsiliklaridir.
 */
object RemoteInputRules {

    const val KEYCODE_QUICK_SETTINGS_FALLBACK = 312
    const val KEYCODE_CAPTIONS_ALT = 175
    const val KEYCODE_TELETEXT_ALT = 233

    /** Ayarlar tuslari: SETTINGS, TV_CONTENTS_MENU ve cihazlara ozel QUICK_SETTINGS. */
    fun isSettingsKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_SETTINGS ||
            keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU ||
            keyCode == KEYCODE_QUICK_SETTINGS_FALLBACK

    /** IPTV medya kontrolleri tuslari (Play, Pause, Stop, FF, Rewind, Next, Prev). */
    fun isIptvMediaKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> true
        else -> false
    }

    /** OK tuslari: DPAD_CENTER ve ENTER. */
    fun isOkKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER

    /** Sayisal tus karsiligi (0..9); diger tuslar icin null. */
    fun digitFromKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_0 -> 0
        KeyEvent.KEYCODE_1 -> 1
        KeyEvent.KEYCODE_2 -> 2
        KeyEvent.KEYCODE_3 -> 3
        KeyEvent.KEYCODE_4 -> 4
        KeyEvent.KEYCODE_5 -> 5
        KeyEvent.KEYCODE_6 -> 6
        KeyEvent.KEYCODE_7 -> 7
        KeyEvent.KEYCODE_8 -> 8
        KeyEvent.KEYCODE_9 -> 9
        else -> null
    }

    /** Oynatmayi engelleyen herhangi bir OSD katmani (panel, status, PIN vs.) acik mi? */
    fun hasNoBlockingPlaybackOsd(
        isChannelPanelClosed: Boolean,
        isStatusVisible: Boolean,
        isRecentVisible: Boolean,
        isParentalVisible: Boolean,
        numberInputEmpty: Boolean,
    ): Boolean = isChannelPanelClosed &&
        !isStatusVisible &&
        !isRecentVisible &&
        !isParentalVisible &&
        numberInputEmpty

    /** VOD icerikte hizli seek yapilabilir mi? */
    fun canQuickSeek(
        isIptv: Boolean,
        isVod: Boolean,
        hasNoBlockingPlaybackOsd: Boolean,
        iptvControlsInteractive: Boolean,
        keyCode: Int,
    ): Boolean = isIptv &&
        isVod &&
        hasNoBlockingPlaybackOsd &&
        !iptvControlsInteractive &&
        (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)

    /** Hizli seek yon ve milisaniye ofseti. */
    fun quickSeekOffset(keyCode: Int, stepMs: Long): Long? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> -stepMs
        KeyEvent.KEYCODE_DPAD_RIGHT -> stepMs
        else -> null
    }

    /** OK tusu uzun/kisa basim gate'ine girmeli mi (canli yayinda OSD yokken)? */
    fun canTriggerOkGate(
        isOkKey: Boolean,
        hasNoBlockingPlaybackOsd: Boolean,
        isVod: Boolean,
        iptvControlsInteractive: Boolean,
    ): Boolean = isOkKey && hasNoBlockingPlaybackOsd && !isVod && !iptvControlsInteractive

    /** Yesil tus IPTV kontrol cubugunun ses/kanal tercihine mi ait? */
    fun isGreenKeyOwnedByIptvControls(
        isIptv: Boolean,
        isChannelPanelClosed: Boolean,
        iptvControlsInteractive: Boolean,
        isPlaybackContainerVisible: Boolean,
    ): Boolean = isIptv &&
        isChannelPanelClosed &&
        iptvControlsInteractive &&
        isPlaybackContainerVisible
}
