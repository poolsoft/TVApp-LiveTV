package com.tvapp.livetv.data

import com.tvapp.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProgramEpgMergeTest {

    @Test
    fun `refresh removes stale programs for channels with no current result`() {
        val refreshed = channel("refreshed")
        val untouched = channel("untouched")
        val result = replaceCurrentPrograms(
            existing = mapOf(
                refreshed.sourceKey to program("expired", 0L, 10L),
                untouched.sourceKey to program("keep", 0L, 10L),
            ),
            refreshedChannels = listOf(refreshed),
            fresh = emptyMap(),
        )

        assertFalse(result.containsKey(refreshed.sourceKey))
        assertEquals("keep", result[untouched.sourceKey]?.title)
    }

    @Test
    fun `refresh does not retain blank program titles`() {
        val refreshed = channel("refreshed")
        val result = replaceCurrentPrograms(
            existing = mapOf(refreshed.sourceKey to program("old", 0L, 10L)),
            refreshedChannels = listOf(refreshed),
            fresh = mapOf(refreshed.sourceKey to program(" ", 10L, 20L)),
        )

        assertFalse(result.containsKey(refreshed.sourceKey))
    }

    @Test
    fun `xmltv keeps priority only where it overlaps xtream`() {
        val xmlFuture = program("XML future", 200L, 300L)
        val xtreamCurrent = program("Xtream current", 100L, 200L)
        val xtreamDuplicate = program("Xtream future", 210L, 290L)

        val result = mergeProgramSources(
            primary = listOf(xmlFuture),
            fallback = listOf(xtreamCurrent, xtreamDuplicate),
        )

        assertEquals(listOf("Xtream current", "XML future"), result.map(ProgramSummary::title))
    }

    private fun channel(key: String) = LiveChannel(
        id = key.hashCode().toLong(),
        sourceKey = key,
        inputId = "input",
        displayNumber = "1",
        displayName = key,
        uri = "content://channel/$key",
    )

    private fun program(title: String, start: Long, end: Long) = ProgramSummary(
        title = title,
        startTimeMillis = start,
        endTimeMillis = end,
    )
}
