package com.tvapp.livetv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteControlSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun openPlayback() {
        openActivity(MainActivity::class.java.name)
    }

    @Test
    fun playbackOverlaysAcceptRemoteNavigationWithoutLeavingTvApp() {
        device.pressKeyCode(KeyEvent.KEYCODE_MENU)
        device.pressDPadDown()
        device.pressDPadUp()
        device.pressBack()
        device.pressKeyCode(KeyEvent.KEYCODE_INFO)
        device.pressBack()
        device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        device.pressDPadDown()
        device.pressDPadUp()
        device.pressBack()

        assertTvAppForeground()
    }

    @Test
    fun guideAndEditorAcceptDirectionalAndBackNavigation() {
        openActivity(ProgramGuideActivity::class.java.name)
        device.pressDPadRight()
        device.pressDPadDown()
        device.pressDPadLeft()
        device.pressBack()
        assertTvAppForeground()

        openActivity(ChannelEditorActivity::class.java.name)
        device.pressDPadDown()
        device.pressKeyCode(KeyEvent.KEYCODE_CHANNEL_DOWN)
        device.pressBack()
        assertTvAppForeground()
    }

    @Test
    fun settingsTabsRemainRemoteNavigable() {
        openActivity(DisplaySettingsActivity::class.java.name)
        device.pressDPadRight()
        device.pressDPadRight()
        device.pressDPadDown()
        device.pressDPadLeft()
        device.pressDPadRight()
        device.pressBack()

        assertTvAppForeground()
    }

    private fun openActivity(className: String) {
        context.startActivity(
            Intent().apply {
                component = ComponentName(context.packageName, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        device.wait(Until.hasObject(androidx.test.uiautomator.By.pkg(context.packageName).depth(0)), TIMEOUT_MS)
        assertTvAppForeground()
    }

    private fun assertTvAppForeground() {
        device.wait(Until.hasObject(androidx.test.uiautomator.By.pkg(context.packageName).depth(0)), TIMEOUT_MS)
        assertEquals(context.packageName, device.currentPackageName)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
