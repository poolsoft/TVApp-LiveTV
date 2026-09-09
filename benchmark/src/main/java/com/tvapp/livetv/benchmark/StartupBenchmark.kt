package com.tvapp.livetv.benchmark

import android.content.Intent
import android.view.KeyEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startTvApp()
    }

    @Test
    fun channelPanelFastNavigation() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startTvApp()
        device.pressKeyCode(KeyEvent.KEYCODE_MENU)
        device.wait(Until.hasObject(By.res(PACKAGE_NAME, "channel_panel")), 2_000)
        repeat(20) { device.pressDPadDown() }
        repeat(20) { device.pressDPadUp() }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.startTvApp() {
        val intent = Intent().setClassName(PACKAGE_NAME, "$PACKAGE_NAME.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivityAndWait(intent)
    }

    private companion object {
        const val PACKAGE_NAME = "com.tvapp.livetv"
    }
}
