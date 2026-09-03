package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvProviderAddressTest {
    @Test
    fun `normalizes Xtream panel URLs`() {
        assertEquals("http://panel.example:8080", XtreamClient.normalizeBaseUrl("panel.example:8080/"))
        assertEquals(
            "https://panel.example",
            XtreamClient.normalizeBaseUrl("https://panel.example/player_api.php?username=a"),
        )
    }

    @Test
    fun `normalizes common Stalker portal URLs`() {
        assertEquals(
            "http://portal.example/portal.php",
            StalkerClient.normalizeEndpoint("portal.example/c/"),
        )
        assertEquals(
            "http://portal.example/stalker_portal/server/load.php",
            StalkerClient.normalizeEndpoint("portal.example/stalker_portal/c/"),
        )
        assertEquals(
            "https://portal.example/server/load.php",
            StalkerClient.normalizeEndpoint("https://portal.example/server/load.php"),
        )
    }
}
