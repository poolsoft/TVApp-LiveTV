package com.tvapp.livetv.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class XtreamConnectionTest {
    @Test fun playbackHttpHeadersIncludeOriginAndRefererTogether() {
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val headers = AtomicReference<List<String>>()
            val worker = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val input = socket.getInputStream().bufferedReader()
                    val request = mutableListOf<String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        request += line
                    }
                    headers.set(request)
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\ntest".toByteArray())
                }
            }
            val source = com.tvapp.livetv.playback.IptvDataSourceFactory.create("Fixture Agent",
                "https://referrer.invalid/", "https://origin.invalid").createDataSource()
            try {
                source.open(androidx.media3.datasource.DataSpec(android.net.Uri.parse("http://127.0.0.1:${server.localPort}/fixture")))
                assertEquals(4, source.read(ByteArray(4), 0, 4))
            } finally { source.close(); worker.join(12_000) }
            val request = headers.get()
            assertTrue(request.contains("Origin: https://origin.invalid"))
            assertTrue(request.contains("Referer: https://referrer.invalid/"))
            assertTrue(request.contains("User-Agent: Fixture Agent"))
        }
    }

    @Test fun accountMetadataAndBoundedCategoryRequestsUseRealHttpResponses() {
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val categories = CountDownLatch(2)
            val accountRequests = AtomicInteger()
            val failure = AtomicReference<Throwable>()
            val worker = thread {
                val connections = mutableListOf<Thread>()
                try {
                    repeat(8) {
                        val socket = server.accept()
                        connections += thread {
                            try {
                                socket.use {
                                    socket.soTimeout = 10_000
                                    val input = socket.getInputStream().bufferedReader()
                                    val request = input.readLine()
                                    while (!input.readLine().isNullOrEmpty()) { /* Fixture request headers. */ }
                                    val query = request.substringAfter('?').substringBefore(' ').split('&')
                                        .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
                                    val action = query["action"]
                                    if (action?.endsWith("categories") == true) {
                                        categories.countDown()
                                        check(categories.await(5, TimeUnit.SECONDS)) { "Category requests were not concurrent" }
                                    }
                                    val body = when (action) {
                                        "get_live_categories" -> """[{"category_id":"1","category_name":"Live"}]"""
                                        "get_vod_categories" -> """[{"category_id":"2","category_name":"Movies"}]"""
                                        "get_live_streams" -> """[{"stream_id":7,"name":"Fixture live","category_id":"1","epg_channel_id":"fixture.epg"}]"""
                                        "get_vod_streams" -> """[{"stream_id":8,"name":"Fixture movie","category_id":"2","container_extension":"mkv"}]"""
                                        else -> when (accountRequests.incrementAndGet()) {
                                            1 -> """{"user_info":{"auth":1,"status":"Active","exp_date":"1800000000","active_cons":"0","max_connections":"2"}}"""
                                            2 -> """{"user_info":{"auth":1,"status":null,"exp_date":null,"max_connections":"0"}}"""
                                            3 -> """{"user_info":{"auth":1,"status":"Expired"}}"""
                                            else -> """{"user_info":{"auth":0}}"""
                                        }
                                    }
                                    val bytes = body.toByteArray()
                                    socket.getOutputStream().apply {
                                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                        write(bytes); flush()
                                    }
                                }
                            } catch (error: Throwable) { failure.compareAndSet(null, error) }
                        }
                    }
                } catch (error: Throwable) { failure.compareAndSet(null, error) }
                finally { connections.forEach { it.join(12_000) } }
            }
            try {
                val client = XtreamClient("http://127.0.0.1:${server.localPort}", "fixture user", "fixture")
                assertEquals(XtreamAccountInfo("Active", 1_800_000_000_000, 0, 2), client.verifyAccount())
                val channels = client.channels().toList()
                assertEquals(listOf("LIVE", "VOD"), channels.map { it.contentType })
                assertEquals(listOf("Live", "Movies"), channels.map { it.groupTitle })
                assertTrue(channels.last().streamUrl.endsWith("/movie/fixture%20user/fixture/8.mkv"))
                assertEquals(XtreamAccountInfo(null, null, null, null), client.verifyAccount())
                assertEquals("Expired", client.accountInfo().status)
                assertTrue(runCatching { client.verifyAccount() }.exceptionOrNull() is IllegalArgumentException)
            } finally { server.close(); worker.join(12_000) }
            failure.get()?.let { throw AssertionError("Fixture server failed", it) }
        }
    }
}
