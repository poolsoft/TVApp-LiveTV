package com.tvapp.livetv

import android.content.Context
import android.graphics.Color
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackController
import com.tvapp.livetv.settings.IptvSubtitleAppearanceStore
import com.tvapp.livetv.ui.IptvSubtitleAppearanceDialog
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IptvEnhancementsTest {
    @Test fun vodArrowsActivateTimelineWithPassiveInfoBarAndUnknownFormat() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
                val channel = field("currentChannel")
                val vod = field("vodMode")
                val kind = field("currentIptvContentKind")
                val interactive = field("iptvControlsInteractive")
                val original = listOf(channel.get(activity), vod.get(activity), kind.get(activity))
                val ids = listOf(R.id.channel_panel, R.id.status_panel, R.id.recent_channels_panel, R.id.parental_lock_panel)
                val visibilities = ids.map { activity.findViewById<android.view.View>(it).visibility }
                try {
                    channel.set(activity, LiveChannel(1, "fixture:vod-keys", "iptv:0", "", "VOD key test fixture",
                        "fixture:vod-keys", source = LiveChannel.Source.IPTV, iptvContentType = "VOD"))
                    vod.setBoolean(activity, true)
                    kind.set(activity, com.tvapp.livetv.playback.IptvContentKind.UNKNOWN)
                    ids.forEach { activity.findViewById<android.view.View>(it).visibility = android.view.View.GONE }
                    activity.findViewById<android.view.View>(R.id.info_bar).visibility = android.view.View.VISIBLE
                    interactive.setBoolean(activity, false)
                    activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
                    assertTrue(interactive.getBoolean(activity))
                    assertEquals("TIMELINE", field("iptvControlRow").get(activity)?.toString())
                    assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.channel_panel).visibility)
                } finally {
                    MainActivity::class.java.getDeclaredMethod("hideIptvPlaybackControls", Boolean::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(activity, true)
                    channel.set(activity, original[0]); vod.set(activity, original[1]); kind.set(activity, original[2])
                    ids.forEachIndexed { i, id -> activity.findViewById<android.view.View>(id).visibility = visibilities[i] }
                }
            }
        }
    }
    @Test fun subtitleDialogSupportsRemoteAndCancelPreservesPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = IptvSubtitleAppearanceStore(context)
        val original = store.load()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            store.save(original.copy(custom = true, sizePercent = 125, color = Color.YELLOW))
            ActivityScenario.launch(VodHomeActivity::class.java).use { scenario ->
                var customLabel = ""
                var offLabel = ""
                InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
                device.waitForIdle()
                scenario.onActivity {
                    customLabel = it.getString(R.string.iptv_subtitle_custom)
                    offLabel = it.getString(R.string.off)
                    IptvSubtitleAppearanceDialog.show(it)
                }
                device.waitForIdle()
                assertTrue(device.wait(Until.hasObject(By.textStartsWith(customLabel).focused(true)), 5000))
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "subtitle-appearance-test.png"))
                device.pressDPadCenter()
                assertTrue(device.wait(Until.hasObject(By.text(offLabel)), 5000))
                device.pressBack()
                device.pressBack()
                assertEquals(125, store.load().sizePercent)
                assertTrue(store.load().custom)
                scenario.onActivity { store.apply(PlayerView(it)) }
                var saved = false
                scenario.onActivity { IptvSubtitleAppearanceDialog.show(it) { saved = true } }
                assertTrue(device.wait(Until.hasObject(By.textStartsWith(customLabel).focused(true)), 5000))
                repeat(8) { device.pressDPadDown() }
                if (!device.hasObject(By.res("android", "button1").focused(true))) device.pressDPadRight()
                assertTrue(device.wait(Until.hasObject(By.res("android", "button1").focused(true)), 5000))
                device.pressDPadCenter()
                device.waitForIdle()
                scenario.onActivity { assertTrue(saved) }
            }
        } finally { store.save(original) }
    }

    @Test fun misleadingHlsExtensionFallsBackToSameUrlOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val requests = AtomicInteger()
        val paths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val bytes = ByteBuffer.allocate(44 + 16000).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(capacity() - 8); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(capacity() - 44)
        }.array()
        var controller: IptvPlaybackController? = null
        ServerSocket(0).use { server ->
            val worker = thread {
                try {
                    while (!server.isClosed) server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        paths += reader.readLine().substringBefore(" HTTP")
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        requests.incrementAndGet()
                        socket.getOutputStream().apply {
                            write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            write(bytes); flush()
                        }
                    }
                } catch (error: Exception) { if (!server.isClosed) throw error }
            }
            try {
                instrumentation.runOnMainSync {
                    controller = IptvPlaybackController(context, PlayerView(context)).apply {
                        setMuted(true)
                        onPlaybackReady = { ready.countDown() }
                        play(LiveChannel(1, "fixture:fallback", "iptv:0", "1", "Fallback test fixture",
                            "http://127.0.0.1:${server.localPort}/mislabelled.m3u8", source = LiveChannel.Source.IPTV,
                            iptvContentType = "VOD"))
                    }
                }
                assertTrue("Fallback did not reach READY", ready.await(20, TimeUnit.SECONDS))
                assertEquals(2, requests.get())
                assertEquals(1, paths.distinct().size)
            } finally {
                instrumentation.runOnMainSync { controller?.release() }
                server.close()
                worker.join(5000)
            }
        }
    }
}
