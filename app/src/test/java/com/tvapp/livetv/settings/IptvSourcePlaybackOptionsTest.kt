package com.tvapp.livetv.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvSourcePlaybackOptionsTest {
    private val global = IptvPlaybackPreferences(targetBufferSeconds = 20, maximumVideoHeight = 1080)

    @Test fun unsetFieldsInheritGlobalPreferences() {
        assertEquals(EffectiveIptvPlaybackOptions(20, 1080, true), IptvSourcePlaybackOptions().resolve(global, false))
    }

    @Test fun liveAndVodUseIndependentBuffers() {
        val source = IptvSourcePlaybackOptions(5, 30, 720, false)
        assertEquals(EffectiveIptvPlaybackOptions(5, 720, false), source.resolve(global, false))
        assertEquals(EffectiveIptvPlaybackOptions(30, 720, false), source.resolve(global, true))
    }

    @Test fun explicitAutoDoesNotInheritGlobalLimit() {
        assertEquals(EffectiveIptvPlaybackOptions(0, 0, true), IptvSourcePlaybackOptions(0, maximumVideoHeight = 0).resolve(global, false))
    }

    @Test fun invalidStoredValuesFallBackToGlobalPreferences() {
        assertEquals(EffectiveIptvPlaybackOptions(20, 1080, true), IptvSourcePlaybackOptions(-1, maximumVideoHeight = -1).resolve(global, false))
    }
}
