package com.tvapp.livetv.data

import com.tvapp.livetv.data.local.UserChannelEntity
import com.tvapp.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMergerTest {
    @Test
    fun merge_appliesUserOrderGeneratedNumberNameAndFavorite() {
        val first = channel("first", "1", "TRT 1")
        val second = channel("second", "2", "ATV")
        val preferences = listOf(
            preference(first, sortOrder = 20),
            preference(
                second,
                sortOrder = 10,
                customName = "ATV HD",
                favorite = true,
            ),
        )

        val result = ChannelMerger.merge(listOf(first, second), preferences)

        assertEquals(listOf("second", "first"), result.map { it.sourceKey })
        assertEquals(listOf("1", "2"), result.map { it.displayNumber })
        assertEquals("ATV HD", result.first().displayName)
        assertTrue(result.first().favorite)
    }

    @Test
    fun merge_removesHiddenChannel() {
        val visible = channel("visible", "1", "TRT 1")
        val hidden = channel("hidden", "2", "ATV")

        val result = ChannelMerger.merge(
            listOf(visible, hidden),
            listOf(
                preference(visible, sortOrder = 0),
                preference(hidden, sortOrder = 1, hidden = true),
            ),
        )

        assertEquals(listOf("visible"), result.map { it.sourceKey })
        assertFalse(result.first().favorite)
    }

    @Test
    fun merge_canIncludeHiddenChannelForEditor() {
        val hidden = channel("hidden", "2", "ATV")

        val result = ChannelMerger.merge(
            listOf(hidden),
            listOf(preference(hidden, sortOrder = 0, hidden = true)),
            includeHidden = true,
        )

        assertEquals(1, result.size)
        assertTrue(result.first().hidden)
    }

    private fun channel(key: String, number: String, name: String) = LiveChannel(
        id = key.hashCode().toLong(),
        sourceKey = key,
        inputId = "input/HW0",
        displayNumber = number,
        displayName = name,
        uri = "content://android.media.tv/channel/1",
    )

    private fun preference(
        channel: LiveChannel,
        sortOrder: Int,
        customNumber: Int? = null,
        customName: String? = null,
        favorite: Boolean = false,
        hidden: Boolean = false,
    ) = UserChannelEntity(
        sourceKey = channel.sourceKey,
        sourceType = channel.source.name,
        originalDisplayNumber = channel.displayNumber,
        lastKnownName = channel.displayName,
        customNumber = customNumber,
        customName = customName,
        sortOrder = sortOrder,
        favorite = favorite,
        hidden = hidden,
        lastSeenAt = 1L,
    )
}
