package com.tvapp.livetv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvLiveReconnectTest {

    @Test
    fun `normal end of input has no backoff`() {
        assertEquals(0L, liveReconnectBackoffMillis(0))
    }

    @Test
    fun `failures back off exponentially and are capped`() {
        assertEquals(300L, liveReconnectBackoffMillis(1))
        assertEquals(600L, liveReconnectBackoffMillis(2))
        assertEquals(1_200L, liveReconnectBackoffMillis(3))
        assertEquals(2_400L, liveReconnectBackoffMillis(4))
        assertEquals(LIVE_RECONNECT_MAX_DELAY_MS, liveReconnectBackoffMillis(9))
    }

    @Test
    fun `connection summary hides query credentials`() {
        val summary = liveConnectionSummary(
            "http://user:secret@srv.example:8080/play/live.php?mac=00:1A:79&stream=99&play_token=secret",
        )
        assertEquals("http://srv.example", summary)
    }

    @Test
    fun `connection summary omits default port`() {
        val summary = liveConnectionSummary("http://srv.example/live.php")
        assertEquals("http://srv.example", summary)
    }

    @Test
    fun `empty connection never reconnects`() {
        assertFalse(shouldReconnectAfterEndOfInput(0, connectionDeliveredBytes = false))
    }

    @Test fun `only unbounded transport streams can reconnect`() {
        assertTrue(canReconnectLiveRequest("video/mp2t; charset=binary", 0, -1, 1))
        assertFalse(canReconnectLiveRequest("application/vnd.apple.mpegurl", 0, -1, 1))
        assertFalse(canReconnectLiveRequest("video/mp4", 0, -1, 1))
        assertFalse(canReconnectLiveRequest("video/mp2t", 0, 100, 1))
        assertFalse(canReconnectLiveRequest("video/mp2t", 100, -1, 1))
        assertFalse(canReconnectLiveRequest("video/mp2t", 0, -1, 2))
    }

    @Test fun `end of input reconnects only inside failure budget`() {
        assertTrue(shouldReconnectAfterEndOfInput(0, true))
        assertFalse(shouldReconnectAfterEndOfInput(LIVE_RECONNECT_MAX_CONSECUTIVE_FAILURES, true))
    }
}
