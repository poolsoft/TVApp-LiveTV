package com.tvapp.livetv.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgSnapshotCacheTest {
    @After
    fun tearDown() = EpgSnapshotCache.clear()

    @Test
    fun `bulk invalidation drops every snapshot for new data`() {
        val now = 5_000L
        val program = ProgramSummary("News", now - 1_000L, now + 30_000L)
        EpgSnapshotCache.putNowNext("tif:1", NowNextPrograms(program, null), now)
        EpgSnapshotCache.putCurrent("iptv:1", program, now)

        EpgSnapshotCache.invalidateAll()

        assertEquals(0, EpgSnapshotCache.cachedSnapshotCount())
        assertFalse(EpgSnapshotCache.current("tif:1", now).found)
        assertNull(EpgSnapshotCache.nowNext("iptv:1", now))
    }

    @Test
    fun `bulk invalidation invalidates entries stored before it but keeps later ones`() {
        val now = 7_000L
        val before = ProgramSummary("Before", now - 1_000L, now + 30_000L)
        EpgSnapshotCache.putNowNext("tif:1", NowNextPrograms(before, null), now)
        EpgSnapshotCache.invalidateAll()
        assertFalse(EpgSnapshotCache.current("tif:1", now).found)

        val after = ProgramSummary("After", now - 1_000L, now + 30_000L)
        EpgSnapshotCache.putNowNext("tif:2", NowNextPrograms(after, null), now)
        assertTrue(EpgSnapshotCache.current("tif:2", now).found)
        assertEquals(after, EpgSnapshotCache.nowNext("tif:2", now)?.current)
    }

    @Test
    fun `selective invalidation drops only the targeted channel`() {
        val now = 9_000L
        val program = ProgramSummary("News", now - 1_000L, now + 30_000L)
        EpgSnapshotCache.putNowNext("tif:1", NowNextPrograms(program, null), now)
        EpgSnapshotCache.putNowNext("tif:2", NowNextPrograms(program, null), now)

        EpgSnapshotCache.invalidate("tif:1")

        assertFalse(EpgSnapshotCache.current("tif:1", now).found)
        assertTrue(EpgSnapshotCache.current("tif:2", now).found)
        assertEquals(program, EpgSnapshotCache.nowNext("tif:2", now)?.current)
    }

    @Test
    fun `now-next snapshot is shared with current lookup until program end`() {
        val now = 10_000L
        val program = ProgramSummary("News", now - 1_000L, now + 30_000L)

        EpgSnapshotCache.putNowNext("tif:1", NowNextPrograms(program, null), now)

        assertEquals(program, EpgSnapshotCache.nowNext("tif:1", now)?.current)
        assertEquals(program, EpgSnapshotCache.current("tif:1", now).program)
        assertFalse(EpgSnapshotCache.current("tif:1", now + 30_000L).found)
    }

    @Test
    fun `empty lookup is cached briefly and remains distinguishable from a miss`() {
        val now = 20_000L
        EpgSnapshotCache.putCurrent("iptv:1", null, now)

        val cached = EpgSnapshotCache.current("iptv:1", now + EpgSnapshotCache.NEGATIVE_CACHE_MS - 1L)
        assertTrue(cached.found)
        assertNull(cached.program)
        assertFalse(EpgSnapshotCache.current("iptv:1", now + EpgSnapshotCache.NEGATIVE_CACHE_MS).found)
    }

    @Test
    fun `list window update refreshes an existing now-next snapshot`() {
        val now = 30_000L
        val old = ProgramSummary("Old", now - 1_000L, now + 30_000L)
        val fresh = ProgramSummary("Fresh", now - 1_000L, now + 60_000L)
        EpgSnapshotCache.putNowNext("tif:1", NowNextPrograms(old, null), now)

        EpgSnapshotCache.putCurrent("tif:1", fresh, now + 1_000L)

        assertEquals(fresh, EpgSnapshotCache.nowNext("tif:1", now + 1_000L)?.current)
    }
}
