package com.tvapp.livetv.main

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteInputRulesTest {

    @Test
    fun settingsKeysAreRecognized() {
        assertTrue(RemoteInputRules.isSettingsKey(KeyEvent.KEYCODE_SETTINGS))
        assertTrue(RemoteInputRules.isSettingsKey(KeyEvent.KEYCODE_TV_CONTENTS_MENU))
        assertTrue(RemoteInputRules.isSettingsKey(312))
        assertFalse(RemoteInputRules.isSettingsKey(KeyEvent.KEYCODE_MENU))
        assertFalse(RemoteInputRules.isSettingsKey(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun iptvMediaKeysAreRecognized() {
        assertTrue(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertTrue(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertTrue(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertTrue(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_MEDIA_STOP))
        assertTrue(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertTrue(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertTrue(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertTrue(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertFalse(RemoteInputRules.isIptvMediaKey(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun okKeysAreRecognized() {
        assertTrue(RemoteInputRules.isOkKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(RemoteInputRules.isOkKey(KeyEvent.KEYCODE_ENTER))
        assertFalse(RemoteInputRules.isOkKey(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun digitKeysAreExtractedProperly() {
        assertEquals(0, RemoteInputRules.digitFromKey(KeyEvent.KEYCODE_0))
        assertEquals(1, RemoteInputRules.digitFromKey(KeyEvent.KEYCODE_1))
        assertEquals(5, RemoteInputRules.digitFromKey(KeyEvent.KEYCODE_5))
        assertEquals(9, RemoteInputRules.digitFromKey(KeyEvent.KEYCODE_9))
        assertNull(RemoteInputRules.digitFromKey(KeyEvent.KEYCODE_A))
        assertNull(RemoteInputRules.digitFromKey(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun hasNoBlockingPlaybackOsdChecksAllPanels() {
        assertTrue(
            RemoteInputRules.hasNoBlockingPlaybackOsd(
                isChannelPanelClosed = true,
                isStatusVisible = false,
                isRecentVisible = false,
                isParentalVisible = false,
                numberInputEmpty = true,
            ),
        )

        assertFalse(
            RemoteInputRules.hasNoBlockingPlaybackOsd(
                isChannelPanelClosed = false,
                isStatusVisible = false,
                isRecentVisible = false,
                isParentalVisible = false,
                numberInputEmpty = true,
            ),
        )

        assertFalse(
            RemoteInputRules.hasNoBlockingPlaybackOsd(
                isChannelPanelClosed = true,
                isStatusVisible = true,
                isRecentVisible = false,
                isParentalVisible = false,
                numberInputEmpty = true,
            ),
        )

        assertFalse(
            RemoteInputRules.hasNoBlockingPlaybackOsd(
                isChannelPanelClosed = true,
                isStatusVisible = false,
                isRecentVisible = false,
                isParentalVisible = false,
                numberInputEmpty = false,
            ),
        )
    }

    @Test
    fun canQuickSeekRequiresVodAndNoBlockingOsd() {
        assertTrue(
            RemoteInputRules.canQuickSeek(
                isIptv = true,
                isVod = true,
                hasNoBlockingPlaybackOsd = true,
                iptvControlsInteractive = false,
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
            ),
        )

        // Live icerikte calismaz
        assertFalse(
            RemoteInputRules.canQuickSeek(
                isIptv = true,
                isVod = false,
                hasNoBlockingPlaybackOsd = true,
                iptvControlsInteractive = false,
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
            ),
        )

        // Kontroller interaktifken quick-seek degil kontrol cubugu yonetir
        assertFalse(
            RemoteInputRules.canQuickSeek(
                isIptv = true,
                isVod = true,
                hasNoBlockingPlaybackOsd = true,
                iptvControlsInteractive = true,
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
            ),
        )
    }

    @Test
    fun quickSeekOffsetCalculatesSteps() {
        assertEquals(-10000L, RemoteInputRules.quickSeekOffset(KeyEvent.KEYCODE_DPAD_LEFT, 10000L))
        assertEquals(10000L, RemoteInputRules.quickSeekOffset(KeyEvent.KEYCODE_DPAD_RIGHT, 10000L))
        assertNull(RemoteInputRules.quickSeekOffset(KeyEvent.KEYCODE_DPAD_UP, 10000L))
    }

    @Test
    fun canTriggerOkGateIsExclusiveToLivePlaybackWithoutOsd() {
        assertTrue(
            RemoteInputRules.canTriggerOkGate(
                isOkKey = true,
                hasNoBlockingPlaybackOsd = true,
                isVod = false,
                iptvControlsInteractive = false,
            ),
        )

        // VOD iken dogrudan play/pause veya kontroller gelir, quick hub degil
        assertFalse(
            RemoteInputRules.canTriggerOkGate(
                isOkKey = true,
                hasNoBlockingPlaybackOsd = true,
                isVod = true,
                iptvControlsInteractive = false,
            ),
        )
    }
}
