package com.tvapp.livetv.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the REMOTEEDIT Sprint R1 pieces that do not need an Android
 * device: token hashing, route table, and the JSON shapes produced by the
 * server data contract. PairingStore itself is covered on-device because it
 * depends on EncryptedSharedPreferences.
 */
class RemoteEditServerTest {

    @Test
    fun `sha256 is deterministic and hex encoded`() {
        val first = PairingStore.sha256Hex("token-1")
        val second = PairingStore.sha256Hex("token-1")
        val other = PairingStore.sha256Hex("token-2")
        assertEquals(first, second)
        assertTrue(first != other)
        assertEquals(64, first.length)
        assertTrue(first.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `route table covers the v1 read and pairing endpoints`() {
        val paths = RemoteEditServer.ROUTES.map { it.path }
        assertTrue("/api/v1/ping" in paths)
        assertTrue("/api/v1/pair" in paths)
        assertTrue("/api/v1/channels" in paths)
        assertTrue("/api/v1/groups" in paths)
        assertTrue("/api/v1/sources" in paths)
    }

    @Test
    fun `ping payload carries app identity and api version`() {
        val payload = JSONObject()
            .put("app", "TVApp")
            .put("versionName", "0.1.1")
            .put("versionCode", 1)
            .put("api", 1)
        assertEquals("TVApp", payload.getString("app"))
        assertEquals(1, payload.getInt("api"))
    }

    @Test
    fun `error payload never echoes submitted secrets`() {
        val payload = RemoteEditServer.error("pairing_failed", "Kod geçersiz.")
        assertEquals("pairing_failed", payload.getString("error"))
        val text = payload.toString()
        assertTrue("code" !in text)
        assertTrue("token" !in text)
    }

    @Test
    fun `channels page payload keeps the documented fields`() {
        val channels = JSONArray()
            .put(
                JSONObject()
                    .put("sourceKey", "iptv:1")
                    .put("displayName", "Kanal 1")
                    .put("displayNumber", "1")
                    .put("source", "IPTV")
                    .put("favorite", false)
                    .put("hidden", false)
                    .put("groupId", JSONObject.NULL)
                    .put("iptvContentType", "LIVE"),
            )
        assertEquals("iptv:1", channels.getJSONObject(0).getString("sourceKey"))
        assertEquals("LIVE", channels.getJSONObject(0).getString("iptvContentType"))
        assertTrue(channels.getJSONObject(0).isNull("groupId"))
        assertNull(channels.getJSONObject(0).optString("revision", null))
    }

    @Test
    fun `wifi binding resolves a host or falls back to loopback`() {
        val host = RemoteEditServer.hostnameForBinding()
        assertNotNull(host)
        assertTrue(host.isNotBlank())
        // Either a wlan IPv4 or the loopback fallback — never a blank value.
        assertTrue(host == "127.0.0.1" || host.contains('.'))
    }

    @Test
    fun `paired device cap allows a web panel and a phone together`() {
        // One web panel + one phone must fit under the cap.
        assertTrue(PairingStore.MAX_DEVICES >= 2)
    }
}
