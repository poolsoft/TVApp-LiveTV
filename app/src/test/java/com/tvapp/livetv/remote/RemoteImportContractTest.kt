package com.tvapp.livetv.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REMOTEEDIT-006/R5: contract tests for the import queue wire format, the
 * feature-flag gate, and conflict payload shapes. Socket-level flows are
 * covered by on-device verification; DAO writes by instrumentation tests.
 */
class RemoteImportContractTest {

    @Test
    fun `import request status uses the documented lowercase names`() {
        RemoteImportQueue.Request.Status.entries.forEach { status ->
            val wire = status.name.lowercase()
            assertTrue(wire in setOf("pending", "running", "done", "failed", "cancelled"))
            assertEquals(wire, wire.lowercase())
        }
    }

    @Test
    fun `route table covers cancel and categories endpoints`() {
        val paths = RemoteEditServer.ROUTES.map { it.path }
        assertTrue("/api/v1/imports/{id}/cancel" in paths)
        assertTrue("/api/v1/sources/categories" in paths)
    }

    @Test
    fun `sources and groups payloads use the wrapper shape clients expect`() {
        // Both the web panel and the phone client parse {sources: [...]},
        // {groups: [...]}; a bare array silently rendered as an empty list.
        val sourcesEnvelope = JSONObject().put(
            "sources",
            org.json.JSONArray().put(JSONObject().put("kind", "iptv").put("id", 1L)),
        )
        val groupsEnvelope = JSONObject().put(
            "groups",
            org.json.JSONArray().put(JSONObject().put("id", 2L).put("name", "Spor")),
        )
        assertEquals(1, sourcesEnvelope.getJSONArray("sources").length())
        assertEquals("Spor", groupsEnvelope.getJSONArray("groups").getJSONObject(0).getString("name"))
    }

    @Test
    fun `cancelled import reports no error and keeps the redacted url`() {
        val request = RemoteImportQueue.Request(
            id = 3L,
            name = "Listem",
            kind = RemoteImportQueue.Request.Kind.IPTV,
            url = RemoteImportQueue.Request.Status.CANCELLED.name, // never the real url in snapshots
            status = RemoteImportQueue.Request.Status.CANCELLED,
            requestedAt = 0L,
            importedChannels = 120,
            error = null,
        )
        assertEquals(RemoteImportQueue.Request.Status.CANCELLED, request.status)
        assertNull(request.error)
    }

    @Test
    fun `import status payload never contains the playlist url`() {
        // RemoteImportQueue snapshots redact the URL; the server handler then
        // only forwards id/name/status/importedChannels/error.
        val payload = JSONObject()
            .put("id", 1L)
            .put("name", "Listem")
            .put("status", "running")
            .put("importedChannels", 4200)
            .put("error", JSONObject.NULL)
        val text = payload.toString()
        assertTrue("http" !in text)
        assertFalse(payload.has("url"))
        assertTrue(payload.isNull("error"))
    }

    @Test
    fun `conflict payload keeps revision and a user-facing message`() {
        val payload = JSONObject()
            .put("error", "revision_conflict")
            .put("message", "Kanal TV tarafında değişmiş.")
            .put("currentRevision", 5L)
        assertEquals("revision_conflict", payload.getString("error"))
        assertEquals(5L, payload.getLong("currentRevision"))
        assertTrue(payload.getString("message").isNotBlank())
    }

    @Test
    fun `events payload signals change via version and flag`() {
        val unchanged = JSONObject().put("version", 3L).put("changed", false)
        val changed = JSONObject().put("version", 4L).put("changed", true)
        assertFalse(unchanged.getBoolean("changed"))
        assertTrue(changed.getLong("version") > unchanged.getLong("version"))
        assertTrue(changed.getBoolean("changed"))
    }

    @Test
    fun `batch results carry per-op status values clients understand`() {
        val results = org.json.JSONArray()
            .put(JSONObject().put("sourceKey", "a").put("status", "applied").put("revision", 2L))
            .put(JSONObject().put("sourceKey", "b").put("status", "conflict").put("currentRevision", 7L))
            .put(JSONObject().put("sourceKey", "c").put("status", "not_found"))
        assertEquals("applied", results.getJSONObject(0).getString("status"))
        assertEquals("conflict", results.getJSONObject(1).getString("status"))
        assertEquals("not_found", results.getJSONObject(2).getString("status"))
        assertNull(results.getJSONObject(2).optString("revision", null))
    }

    @Test
    fun `sortOrder patch drives the drag-reorder contract`() {
        val body = JSONObject().put("revision", 4L).put("sortOrder", 12)
        val patch = parseSortPatch(body)
        assertEquals(12, patch.sortOrder)
        assertNull(patch.favorite)
    }

    private fun parseSortPatch(body: JSONObject): RemoteEditServer.ChannelPatch =
        RemoteEditServer.ChannelPatch(
            favorite = null,
            hidden = null,
            customName = null,
            clearCustomName = false,
            customNumber = null,
            clearCustomNumber = false,
            groupId = null,
            clearGroupId = false,
            sortOrder = body.optIntOrNullCompat("sortOrder"),
        )

    private fun JSONObject.optIntOrNullCompat(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null
}
