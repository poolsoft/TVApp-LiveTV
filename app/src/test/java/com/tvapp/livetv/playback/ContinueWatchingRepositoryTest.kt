package com.tvapp.livetv.playback

import com.tvapp.livetv.model.LiveChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingRepositoryTest {

    private fun iptvChannel(
        sourceKey: String,
        contentType: String? = "VOD",
    ): LiveChannel = LiveChannel(
        sourceKey = sourceKey,
        id = sourceKey.hashCode().toLong(),
        inputId = "iptv",
        source = LiveChannel.Source.IPTV,
        displayName = "Channel $sourceKey",
        displayNumber = "1",
        uri = "http://example.org/$sourceKey",
        iptvContentType = contentType,
    )

    private fun tifChannel(sourceKey: String): LiveChannel = LiveChannel(
        sourceKey = sourceKey,
        id = sourceKey.hashCode().toLong(),
        inputId = "tif",
        source = LiveChannel.Source.TIF,
        displayName = "TIF $sourceKey",
        displayNumber = "2",
        uri = "content://test/$sourceKey",
    )

    @Test
    fun `keeps history order and deduplicates`() = runBlocking {
        val repository = ContinueWatchingRepository(
            historyKeys = { listOf("b", "a", "b") },
            resumeEntry = { null },
            resolveChannel = { key ->
                when (key) {
                    "a" -> iptvChannel("a")
                    "b" -> iptvChannel("b")
                    else -> null
                }
            },
        )

        val items = repository.items()
        assertEquals(listOf("b", "a"), items.map { it.channel.sourceKey })
    }

    @Test
    fun `drops unresolved and non-VOD channels`() = runBlocking {
        val repository = ContinueWatchingRepository(
            historyKeys = { listOf("tif", "live", "missing", "vod") },
            resumeEntry = { null },
            resolveChannel = { key ->
                when (key) {
                    "tif" -> tifChannel("tif")
                    "live" -> iptvChannel("live", contentType = "LIVE")
                    "vod" -> iptvChannel("vod")
                    else -> null
                }
            },
        )

        val items = repository.items()
        assertEquals(listOf("vod"), items.map { it.channel.sourceKey })
    }

    @Test
    fun `respects limit`() = runBlocking {
        val keys = (1..20).map { "k$it" }
        val repository = ContinueWatchingRepository(
            historyKeys = { keys },
            resumeEntry = { null },
            resolveChannel = { key -> iptvChannel(key) },
        )

        assertEquals(ContinueWatchingRepository.MAX_ITEMS, repository.items().size)
        assertEquals(3, repository.items(limit = 3).size)
    }

    @Test
    fun `attaches resume entries`() = runBlocking {
        val entry = IptvResumeEntry(sourceKey = "vod", positionMillis = 120_000L, updatedAt = 5L)
        val repository = ContinueWatchingRepository(
            historyKeys = { listOf("vod") },
            resumeEntry = { entry },
            resolveChannel = { iptvChannel("vod") },
        )

        val item = repository.items().single()
        assertTrue(item.hasResume)
        assertEquals(120_000L, item.resumeEntry?.positionMillis)
    }

    @Test
    fun `empty history yields empty list`() = runBlocking {
        val repository = ContinueWatchingRepository(
            historyKeys = { emptyList() },
            resumeEntry = { null },
            resolveChannel = { null },
        )

        val items = repository.items()
        assertTrue(items.isEmpty())
        assertNull(items.firstOrNull()?.resumeEntry)
        assertFalse(items.any { it.hasResume })
    }
}
