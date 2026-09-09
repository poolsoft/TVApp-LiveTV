package com.tvapp.livetv.benchmark

import android.content.Intent
import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(PACKAGE_NAME) {
        val intent = Intent().setClassName(PACKAGE_NAME, "$PACKAGE_NAME.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivityAndWait(intent)
        device.pressKeyCode(KeyEvent.KEYCODE_MENU)
        repeat(8) { device.pressDPadDown() }
        device.pressBack()
        device.pressKeyCode(KeyEvent.KEYCODE_PROG_BLUE)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.tvapp.livetv"
    }
}
