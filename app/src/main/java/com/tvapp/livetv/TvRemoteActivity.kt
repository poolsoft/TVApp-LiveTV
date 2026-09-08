package com.tvapp.livetv

import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

open class TvRemoteActivity : AppCompatActivity() {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        super.dispatchKeyEvent(event.asTvRemoteEvent())
}

internal fun KeyEvent.asTvRemoteEvent(): KeyEvent {
    val mappedKeyCode = tvRemoteKeyCode(keyCode)
    if (mappedKeyCode == keyCode) return this
    return KeyEvent(
        downTime,
        eventTime,
        action,
        mappedKeyCode,
        repeatCount,
        metaState,
        deviceId,
        scanCode,
        flags,
        source,
    )
}

internal fun tvRemoteKeyCode(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_ESCAPE -> KeyEvent.KEYCODE_BACK
        KeyEvent.KEYCODE_F1 -> KeyEvent.KEYCODE_INFO
        KeyEvent.KEYCODE_F2 -> KeyEvent.KEYCODE_GUIDE
        KeyEvent.KEYCODE_F3 -> KeyEvent.KEYCODE_MENU
        KeyEvent.KEYCODE_F4 -> KeyEvent.KEYCODE_SETTINGS
        KeyEvent.KEYCODE_F5 -> KeyEvent.KEYCODE_PROG_RED
        KeyEvent.KEYCODE_F6 -> KeyEvent.KEYCODE_PROG_GREEN
        KeyEvent.KEYCODE_F7 -> KeyEvent.KEYCODE_PROG_YELLOW
        KeyEvent.KEYCODE_F8 -> KeyEvent.KEYCODE_PROG_BLUE
        KeyEvent.KEYCODE_F9 -> KeyEvent.KEYCODE_TV_INPUT
        KeyEvent.KEYCODE_F10 -> KeyEvent.KEYCODE_LAST_CHANNEL
        KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_PAGE_DOWN -> KeyEvent.KEYCODE_CHANNEL_DOWN
        KeyEvent.KEYCODE_F12, KeyEvent.KEYCODE_PAGE_UP -> KeyEvent.KEYCODE_CHANNEL_UP
        else -> keyCode
}
