package com.tvapp.livetv.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvPlaybackSnapshotTest {
    private val playing = IptvPlaybackSnapshot(
        positionMillis = 1_000, durationMillis = 0, bufferedPositionMillis = 4_000,
        isPlaying = true, isSeekable = false, kind = IptvContentKind.LIVE,
        distanceToLiveDefaultMillis = 1_000, ready = true,
    )

    @Test fun `live badge requires reported timing and ready playback`() {
        assertTrue(playing.isAtLiveEdge())
        assertFalse(playing.copy(distanceToLiveDefaultMillis = null).isAtLiveEdge())
        assertFalse(playing.copy(ready = false).isAtLiveEdge())
        assertFalse(playing.copy(isPlaying = false).isAtLiveEdge())
    }

    @Test fun `behind live and invalid offsets never appear live`() {
        assertFalse(playing.copy(distanceToLiveDefaultMillis = 5_000).isAtLiveEdge())
        assertFalse(playing.copy(distanceToLiveDefaultMillis = -1).isAtLiveEdge())
        assertTrue(playing.copy(distanceToLiveDefaultMillis = 0).isAtLiveEdge())
        assertTrue(playing.copy(distanceToLiveDefaultMillis = 2_000).isAtLiveEdge())
    }
}
