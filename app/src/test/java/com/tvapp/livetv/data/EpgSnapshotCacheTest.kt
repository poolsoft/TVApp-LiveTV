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

        val cached = EpgSnapshotCache.current("iptv:1", now + 1_999L)
        assertTrue(cached.found)
        assertNull(cached.program)
        assertFalse(EpgSnapshotCache.current("iptv:1", now + 2_000L).found)
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
