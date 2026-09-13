package com.tvapp.livetv.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvPlaybackEngineModeTest {
    @Test
    fun channelOverrideTakesPriorityOverDefault() {
        assertEquals(
            IptvPlaybackEngineMode.IJK,
            resolveIptvPlaybackEngineMode("IJK", IptvPlaybackEngineMode.MEDIA3),
        )
    }

    @Test
    fun missingOrInvalidOverrideUsesDefault() {
        assertEquals(
            IptvPlaybackEngineMode.AUTO_FALLBACK,
            resolveIptvPlaybackEngineMode(null, IptvPlaybackEngineMode.AUTO_FALLBACK),
        )
        assertEquals(
            IptvPlaybackEngineMode.MEDIA3,
            resolveIptvPlaybackEngineMode("UNKNOWN", IptvPlaybackEngineMode.MEDIA3),
        )
    }
}
