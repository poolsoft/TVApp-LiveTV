package com.tvapp.livetv.playback

import org.junit.Assert.*
import org.junit.Test

class LiveStreamContinuationTest {
    private fun eligible(enabled: Boolean = true, kind: String? = "LIVE", playing: Boolean = true,
        dynamic: Boolean = false, loading: Boolean = false, duration: Long = 12_000,
        position: Long = 9_000, buffered: Long = 12_000, next: Boolean = false, lead: Int = 3_000) =
        shouldQueueLiveContinuation(enabled, kind, playing, dynamic, loading, duration, position, buffered, next, lead)

    @Test fun configurableLeadIsUsedAtItsBoundary() {
        assertTrue(eligible(position = 7_000, lead = 5_000))
        assertFalse(eligible(position = 6_999, lead = 5_000))
        assertTrue(eligible(position = 11_750, lead = 250))
        assertFalse(eligible(position = 11_749, lead = 250))
        val options = com.tvapp.livetv.settings.IptvSourcePlaybackOptions()
        assertEquals(5_000, options.copy(liveReconnectLeadMillis = 5_000).reconnectLeadMillis())
        for (invalid in listOf(0, 249, 251, 5_250)) {
            assertEquals(3_000, options.copy(liveReconnectLeadMillis = invalid).reconnectLeadMillis())
        }
    }

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
