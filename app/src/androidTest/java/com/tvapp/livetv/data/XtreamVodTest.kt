package com.tvapp.livetv.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class XtreamVodTest {
    @Test fun providerSeasonsAndEpisodesAreParsedWithoutInventedMetadata() {
        ServerSocket(0).use { server ->
            server.soTimeout = 10000
            val responses = listOf(
                """[{"category_id":"4","category_name":"Drama"}]""",
                """[{"series_id":7,"name":"Fixture series","category_id":"4","plot":"Real plot","cover":null}]""",
                """{"episodes":{"2":[{"id":"8","episode_num":3,"title":"Third","container_extension":"mkv","info":{"duration_secs":120,"plot":"Episode plot"}},{"id":"9","title":"Missing container"}]}}""",
                """{"info":{"name":"Fixture movie","plot":"Movie plot","duration_secs":"60","movie_image":"https://example.invalid/poster.jpg"}}"""
            )
            var failure: Throwable? = null
            val worker = thread {
                try {
                    for (body in responses) server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { /* Consume fixture HTTP headers. */ }
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes); flush()
                        }
                    }
                } catch (error: Throwable) { failure = error }
            }
            try {
                val client = XtreamClient("http://127.0.0.1:${server.localPort}", "test user", "test")
                val series = client.series(1, 123).single()
                assertEquals("Drama", series.category)
                assertNull(series.logoUrl)
                val episode = client.episodes(series, 123).single()
                assertEquals(2, episode.seasonNumber)
                assertEquals(3, episode.episodeNumber)
                assertEquals(120000L, episode.durationMillis)
                assertTrue(episode.streamUrl!!.endsWith("/series/test%20user/test/8.mkv"))
                assertEquals("Episode plot", episode.description)
                val movie = client.movieDetails("fixture:movie", 1, "10", 123)!!
                assertEquals("Movie plot", movie.description)
                assertEquals(60000L, movie.durationMillis)
            } finally { worker.join(12000) }
            failure?.let { throw AssertionError("Fixture server failed", it) }
        }
    }
}
