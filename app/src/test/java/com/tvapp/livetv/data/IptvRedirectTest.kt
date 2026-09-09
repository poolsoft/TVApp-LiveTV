package com.tvapp.livetv.data

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IptvRedirectTest {
    @Test
    fun resolvesRelativePlaylistRedirect() {
        val result = resolveHttpRedirect(
            URL("https://example.com/api/list.php?user=test"),
            "../generated/channels.m3u8",
        )

        assertEquals("https://example.com/generated/channels.m3u8", result.toString())
    }

    @Test
    fun rejectsNonHttpRedirect() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveHttpRedirect(URL("https://example.com/list.php"), "file:///private/list.m3u")
        }
    }
}
