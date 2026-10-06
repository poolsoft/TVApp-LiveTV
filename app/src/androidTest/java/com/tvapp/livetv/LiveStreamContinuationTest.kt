package com.tvapp.livetv

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvapp.livetv.data.local.IptvSourceEntity
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackController
import com.tvapp.livetv.playback.IptvContentKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class LiveStreamContinuationTest {
    @Test fun rebufferIndicatorIsTransparentAndSeparateFromInitialLoading() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val channelField = MainActivity::class.java.getDeclaredField("currentChannel").apply { isAccessible = true }
                val blackField = MainActivity::class.java.getDeclaredField("blackScreenActive").apply { isAccessible = true }
                val originalChannel = channelField.get(activity)
                val originalBlack = blackField.getBoolean(activity)
                val method = MainActivity::class.java.getDeclaredMethod("setIptvBufferingVisible",
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                val corner = activity.findViewById<View>(R.id.iptv_buffering_corner)
                val center = activity.findViewById<View>(R.id.iptv_buffering_container)
                try {
                    channelField.set(activity, LiveChannel(1, "fixture:indicator", "iptv:0", "1",
                        "Indicator test fixture", "fixture:indicator", source = LiveChannel.Source.IPTV))
                    blackField.setBoolean(activity, false)
                    method.invoke(activity, true, true)
                    assertEquals(View.VISIBLE, corner.visibility)
                    assertEquals(View.GONE, center.visibility)
                    assertEquals(Color.TRANSPARENT, (corner.background as ColorDrawable).color)
                    assertFalse(corner.isFocusable)
                    method.invoke(activity, true, false)
                    assertEquals(View.GONE, corner.visibility)
                    assertEquals(View.VISIBLE, center.visibility)
                    method.invoke(activity, false, false)
                    assertEquals(View.GONE, corner.visibility)
                    assertEquals(View.GONE, center.visibility)
                } finally {
                    channelField.set(activity, originalChannel)
                    blackField.setBoolean(activity, originalBlack)
                }
            }
        }
    }

    @Test fun finiteLivePartsArePreloadedAndTransitionWithoutRetuning() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = TVAppDatabase.getInstance(context).iptvDao()
        val source = dao.insertSource(IptvSourceEntity(name = "Continuation test fixture",
            location = "fixture:continuation:${System.nanoTime()}", kind = "FILE",
            continuousLiveReconnect = true, automaticRecovery = false, liveReconnectLeadMillis = 5_000))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val transitions = CountDownLatch(2)
        val queued = CountDownLatch(1)
        val requests = AtomicInteger()
        val bytes = pcmFixture()
        var queuedPosition = -1L
        var failure: Throwable? = null
        var controller: IptvPlaybackController? = null
        var listenerAttached = false
        ServerSocket(0).use { server ->
            val worker = thread {
                try {
                    while (!server.isClosed) server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().bufferedReader()
                        var start = 0
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Range:", true)) start = line.substringAfter("bytes=").substringBefore('-').toInt()
                        }
                        requests.incrementAndGet()
                        val headers = if (start > 0)
                            "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-${bytes.lastIndex}/${bytes.size}\r\n"
                        else "HTTP/1.1 200 OK\r\n"
                        socket.getOutputStream().apply {
                            write((headers + "Content-Type: audio/wav\r\nContent-Length: ${bytes.size - start}\r\nConnection: close\r\n\r\n").toByteArray())
                            write(bytes, start, bytes.size - start)
                            flush()
                        }
                    }
                } catch (error: Exception) { if (!server.isClosed) failure = error }
            }
            try {
                instrumentation.runOnMainSync {
                    val view = PlayerView(context)
                    controller = IptvPlaybackController(context, view).apply {
                        setMuted(true)
                        onPlaybackError = { failure = it }
                        onPlaybackReady = {
                            if (!listenerAttached) {
                                listenerAttached = true
                                view.player!!.addListener(object : Player.Listener {
                                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                                        assertTrue(view.player!!.mediaItemCount <= 2)
                                        if (view.player!!.mediaItemCount == 2 && queued.count > 0) {
                                            queuedPosition = view.player!!.currentPosition
                                            queued.countDown()
                                        }
                                    }
                                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) transitions.countDown()
                                    }
                                })
                            }
                        }
                        play(LiveChannel(1, "fixture:continuation:$source", "iptv:$source", "1", "Test live radio",
                            "http://127.0.0.1:${server.localPort}/live.wav", iptvContentType = "LIVE",
                            source = LiveChannel.Source.IPTV))
                    }
                }
                assertTrue("No early queue; failure=$failure", queued.await(15, TimeUnit.SECONDS))
                assertTrue("Queued at $queuedPosition", queuedPosition in 2900..4499)
                assertTrue("No repeated transitions; failure=$failure", transitions.await(20, TimeUnit.SECONDS))
                assertNull(failure)
                instrumentation.runOnMainSync {
                    assertEquals(IptvContentKind.LIVE, controller!!.contentKind())
                    controller!!.pause()
                }
                Thread.sleep(1000)
                val pausedRequests = requests.get()
                Thread.sleep(1500)
                assertEquals(pausedRequests, requests.get())
            } finally {
                instrumentation.runOnMainSync { controller?.release() }
                server.close()
                worker.join(5000)
                dao.getSource(source)?.let { dao.deleteSource(it) }
            }
        }
    }

    // Eight seconds of valid PCM audio, served as a finite HTTP response.
    private fun pcmFixture(): ByteArray = ByteBuffer.allocate(44 + 8000 * 8 * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(capacity() - 8); put("WAVEfmt ".toByteArray())
        putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(capacity() - 44)
        while (remaining() > 0) putShort(0)
    }.array()
}
