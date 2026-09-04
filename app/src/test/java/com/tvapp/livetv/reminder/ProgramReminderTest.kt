package com.tvapp.livetv.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramReminderTest {

    @Test
    fun `id is deterministic for the same channel and start time`() {
        val first = ProgramReminder.idFor("iptv:42", 1_700_000_000_000L)
        val second = ProgramReminder.idFor("iptv:42", 1_700_000_000_000L)
        assertEquals(first, second)
    }

    @Test
    fun `id differs between channels and start times`() {
        val start = 1_700_000_000_000L
        assertNotEquals(
            ProgramReminder.idFor("iptv:42", start),
            ProgramReminder.idFor("tif:7", start),
        )
        assertNotEquals(
            ProgramReminder.idFor("iptv:42", start),
            ProgramReminder.idFor("iptv:42", start + 60_000L),
        )
    }

    @Test
    fun `of builds reminder with stable id`() {
        val reminder = ProgramReminder.of(
            sourceKey = "iptv:42",
            channelName = "Test TV",
            programTitle = "Haberler",
            startTimeMillis = 1_700_000_000_000L,
        )
        assertEquals(ProgramReminder.idFor("iptv:42", 1_700_000_000_000L), reminder.id)
        assertEquals("Test TV", reminder.channelName)
        assertEquals("Haberler", reminder.programTitle)
    }

    @Test
    fun `pruneExpired drops finished reminders and keeps upcoming ones`() {
        val now = 2_000L
        val reminders = listOf(
            ProgramReminder.of("a", "A", "old", 1_000L),
            ProgramReminder.of("a", "A", "at-boundary", 2_000L),
            ProgramReminder.of("b", "B", "future", 3_000L),
        )
        val result = reminders.pruneExpired(now)
        assertEquals(1, result.size)
        assertEquals("future", result.single().programTitle)
    }

    @Test
    fun `pruneExpired keeps an empty list empty`() {
        assertTrue(emptyList<ProgramReminder>().pruneExpired(42L).isEmpty())
    }
}
