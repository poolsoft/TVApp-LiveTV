package com.tvapp.livetv.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedLogoTrackerTest {

    @Test
    fun recordFailureMarksKeyAsFailedWithinTtl() {
        val tracker = FailedLogoTracker(maxEntries = 10, ttlMillis = 1_000L)
        val now = 10_000L

        tracker.recordFailure("http://example.com/logo1.png", now)

        assertTrue(tracker.isFailed("http://example.com/logo1.png", now + 500L))
        assertEquals(1, tracker.size())
    }

    @Test
    fun isFailedReturnsFalseAndEvictsWhenTtlExpires() {
        val tracker = FailedLogoTracker(maxEntries = 10, ttlMillis = 1_000L)
        val now = 10_000L

        tracker.recordFailure("http://example.com/logo1.png", now)

        // TTL dolduktan sonra false donmeli ve kayit silinmeli
        assertFalse(tracker.isFailed("http://example.com/logo1.png", now + 1_000L))
        assertEquals(0, tracker.size())
    }

    @Test
    fun recordSuccessRemovesKey() {
        val tracker = FailedLogoTracker(maxEntries = 10, ttlMillis = 1_000L)
        val now = 10_000L

        tracker.recordFailure("http://example.com/logo1.png", now)
        assertTrue(tracker.isFailed("http://example.com/logo1.png", now + 100L))

        tracker.recordSuccess("http://example.com/logo1.png")
        assertFalse(tracker.isFailed("http://example.com/logo1.png", now + 100L))
        assertEquals(0, tracker.size())
    }

    @Test
    fun trimsOldestEntriesWhenMaxCapacityExceeded() {
        val tracker = FailedLogoTracker(maxEntries = 3, ttlMillis = 10_000L)
        val now = 10_000L

        tracker.recordFailure("logo1", now)
        tracker.recordFailure("logo2", now + 10)
        tracker.recordFailure("logo3", now + 20)

        assertEquals(3, tracker.size())
        assertTrue(tracker.isFailed("logo1", now + 30))

        // 4. kayit eklendiginde en eski olan (veya en az erisilen) atilmali
        tracker.recordFailure("logo4", now + 40)
        assertEquals(3, tracker.size())
        assertTrue(tracker.isFailed("logo4", now + 50))
    }

    @Test
    fun clearRemovesAllEntries() {
        val tracker = FailedLogoTracker(maxEntries = 10, ttlMillis = 10_000L)
        val now = 10_000L

        tracker.recordFailure("logo1", now)
        tracker.recordFailure("logo2", now)
        assertEquals(2, tracker.size())

        tracker.clear()
        assertEquals(0, tracker.size())
        assertFalse(tracker.isFailed("logo1", now))
    }
}
