package com.tvapp.livetv.diagnostics

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF-028 eki: gezinme tuşları için tercih yazımı yapılmaz; `SharedPreferences.commit()`
 * arayüz iş parçasında senkron disk yazımı olduğu için her ok tuşunda gezinmeyi geciktiriyordu.
 * Çökme adli tıbbı için anlamlı olan tuşlar (renk, GERİ, rakam, menü) yazılmaya devam eder.
 */
class EditorKeyTraceTest {

    @Test
    fun navigationKeysDoNotWritePreferences() {
        val navigationKeys = intArrayOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
        )
        navigationKeys.forEach { keyCode ->
            assertTrue(
                "keyCode=$keyCode gezinme tuşu olmalı",
                EditorKeyTrace.isNavigationKey(keyCode),
            )
            assertFalse(
                "keyCode=$keyCode için tercih yazılmamalı",
                EditorKeyTrace.persistsToPreferences(keyCode),
            )
        }
    }

    @Test
    fun actionKeysStillWritePreferences() {
        val actionKeys = intArrayOf(
            KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_PROG_GREEN,
            KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_PROG_BLUE,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_5,
        )
        actionKeys.forEach { keyCode ->
            assertFalse(
                "keyCode=$keyCode gezinme tuşu sayılmamalı",
                EditorKeyTrace.isNavigationKey(keyCode),
            )
            assertTrue(
                "keyCode=$keyCode için tercih yazılmalı",
                EditorKeyTrace.persistsToPreferences(keyCode),
            )
        }
    }
}
