package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StalkerSessionStoreTest {

    @Test
    fun `cached token is returned before ttl expiry`() {
        StalkerSessionStore.reset()
        var now = 1_000L
        StalkerSessionStore.nowMillis = { now }
        StalkerSessionStore.put("http://portal/portal.php", "00:1A:79:00:00:01", "tok-1")
        now += StalkerSessionStore.TOKEN_TTL_MS - 1
        assertEquals(
            "tok-1",
            StalkerSessionStore.token("http://portal/portal.php", "00:1A:79:00:00:01"),
        )
    }

    @Test
    fun `token is not returned after ttl expiry`() {
        StalkerSessionStore.reset()
        var now = 1_000L
        StalkerSessionStore.nowMillis = { now }
        StalkerSessionStore.put("http://portal/portal.php", "00:1A:79:00:00:01", "tok-1")
        now += StalkerSessionStore.TOKEN_TTL_MS
        assertNull(StalkerSessionStore.token("http://portal/portal.php", "00:1A:79:00:00:01"))
    }

    @Test
    fun `different portals keep separate tokens`() {
        StalkerSessionStore.reset()
        var now = 1_000L
        StalkerSessionStore.nowMillis = { now }
        StalkerSessionStore.put("http://a/portal.php", "00:1A:79:00:00:01", "tok-a")
        StalkerSessionStore.put("http://b/portal.php", "00:1A:79:00:00:01", "tok-b")
        assertEquals("tok-a", StalkerSessionStore.token("http://a/portal.php", "00:1A:79:00:00:01"))
        assertEquals("tok-b", StalkerSessionStore.token("http://b/portal.php", "00:1A:79:00:00:01"))
    }
}
