package com.tvapp.livetv.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionTimeoutTest {
    private var now = 0L
    private val timeout = WebSessionTimeout { now }

    @Test fun idleSessionExpiresAfterFifteenMinutes() {
        now = WebSessionTimeout.IDLE_TIMEOUT_MILLIS - 1
        assertFalse(timeout.isExpired(false))
        now++
        assertTrue(timeout.isExpired(false))
    }

    @Test fun longOperationKeepsSessionOpen() {
        now = 40 * 60_000L
        assertFalse(timeout.isExpired(true))
        assertFalse(timeout.isExpired(false))
        now += WebSessionTimeout.IDLE_TIMEOUT_MILLIS
        assertTrue(timeout.isExpired(false))
    }

    @Test fun successfulActivityRestartsIdlePeriod() {
        now = 14 * 60_000L
        timeout.touch()
        now = 20 * 60_000L
        assertFalse(timeout.isExpired(false))
        now = 29 * 60_000L
        assertTrue(timeout.isExpired(false))
    }

    @Test fun statusChecksDoNotKeepIdleSessionAlive() {
        repeat(15) {
            now = it * 60_000L
            assertFalse(timeout.isExpired(false))
        }
        now = WebSessionTimeout.IDLE_TIMEOUT_MILLIS
        assertTrue(timeout.isExpired(false))
    }
}
