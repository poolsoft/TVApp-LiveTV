package com.tvapp.livetv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TvRemoteKeyMappingTest {
    @Test
    fun functionKeysMapToTvRemoteKeys() {
        val mappings = mapOf(
            KeyEvent.KEYCODE_F1 to KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_F2 to KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_F3 to KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_F4 to KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_F5 to KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_F6 to KeyEvent.KEYCODE_PROG_GREEN,
            KeyEvent.KEYCODE_F7 to KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_F8 to KeyEvent.KEYCODE_PROG_BLUE,
            KeyEvent.KEYCODE_F9 to KeyEvent.KEYCODE_TV_INPUT,
            KeyEvent.KEYCODE_F10 to KeyEvent.KEYCODE_LAST_CHANNEL,
            KeyEvent.KEYCODE_F11 to KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_F12 to KeyEvent.KEYCODE_CHANNEL_UP,
        )

        mappings.forEach { (keyboardKey, remoteKey) ->
            assertEquals(remoteKey, tvRemoteKeyCode(keyboardKey))
        }
    }

    @Test
    fun navigationShortcutsMapWithoutChangingOtherKeys() {
        assertEquals(KeyEvent.KEYCODE_BACK, tvRemoteKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(KeyEvent.KEYCODE_CHANNEL_UP, tvRemoteKeyCode(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(KeyEvent.KEYCODE_CHANNEL_DOWN, tvRemoteKeyCode(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, tvRemoteKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
