package com.tvapp.livetv.playback

import org.junit.Assert.*
import org.junit.Test

class LiveStreamContinuationTest {
    private fun eligible(enabled: Boolean = true, kind: String? = "LIVE", playing: Boolean = true,
        dynamic: Boolean = false, loading: Boolean = false, duration: Long = 12_000,
        position: Long = 9_000, buffered: Long = 12_000, next: Boolean = false) =
        shouldQueueLiveContinuation(enabled, kind, playing, dynamic, loading, duration, position, buffered, next)

    @Test fun onlyFullyLoadedFiniteLivePartsAreQueuedNearTheirEnd() {
        assertTrue(eligible())
        assertTrue(eligible(position = 12_000))
        assertFalse(eligible(position = 8_999))
        assertFalse(eligible(buffered = 10_000))
        assertFalse(eligible(loading = true))
        assertFalse(eligible(duration = -1))
        assertFalse(eligible(dynamic = true))
    }

    @Test fun vodArchivePauseDisabledSourcesAndDuplicateNextAreExcluded() {
        assertFalse(eligible(enabled = false))
        assertFalse(eligible(kind = "VOD"))
        assertFalse(eligible(kind = "CATCHUP"))
        assertFalse(eligible(kind = null))
        assertFalse(eligible(playing = false))
        assertFalse(eligible(next = true))
    }

    @Test fun shortPartsAccumulateButHealthyPartsResetTheLimit() {
        assertEquals(1, consecutiveShortLiveParts(0, 100))
        assertEquals(4, consecutiveShortLiveParts(3, 100))
        assertEquals(0, consecutiveShortLiveParts(3, 12_000))
    }
}
