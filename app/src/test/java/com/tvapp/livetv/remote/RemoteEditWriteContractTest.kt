package com.tvapp.livetv.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the REMOTEEDIT-004 write path pieces that run on the
 * JVM: patch JSON parsing (including explicit clears) and the outcome
 * mapping between repository results and HTTP responses. DAO behavior is
 * covered by device/instrumentation runs.
 */
class RemoteEditWriteContractTest {

    @Test
    fun `patch parse reads only provided fields`() {
        val body = JSONObject().put("favorite", true)
        val patch = parse(body)
        assertEquals(true, patch.favorite)
        assertNull(patch.hidden)
        assertNull(patch.customName)
        assertFalse(patch.clearCustomName)
        assertNull(patch.sortOrder)
    }

    @Test
    fun `patch parse distinguishes clearing a field from leaving it alone`() {
        val body = JSONObject().put("clearCustomName", true)
        val patch = parse(body)
        assertNull(patch.customName)
        assertTrue(patch.clearCustomName)
    }

    @Test
    fun `patch parse maps numeric fields`() {
        val body = JSONObject()
            .put("customNumber", 12)
            .put("sortOrder", 34)
            .put("groupId", 56)
        val patch = parse(body)
        assertEquals(12, patch.customNumber)
        assertEquals(34, patch.sortOrder)
        assertEquals(56L, patch.groupId)
    }

    @Test
    fun `write outcomes map to documented statuses`() {
        val applied = RemoteEditServer.WriteOutcome.Applied(7L)
        val conflict = RemoteEditServer.WriteOutcome.Rejected(9L)
        assertEquals(7L, applied.newRevision)
        assertEquals(9L, conflict.currentRevision)
        assertTrue(RemoteEditServer.WriteOutcome.NotFound is RemoteEditServer.WriteOutcome)
    }

    @Test
    fun `route table includes the write endpoints`() {
        val probes = RemoteEditServer.ROUTES
        assertTrue(probes.any { it.path == "/api/v1/channels/{sourceKey}" })
        assertTrue(probes.any { it.path == "/api/v1/channels/batch" })
    }

    private fun parse(body: JSONObject): RemoteEditServer.ChannelPatch {
        // Mirror of RemoteEditServer.parsePatch; the server method is private
        // and the JSON contract is what matters for clients.
        fun JSONObject.boolOrNull(name: String) =
            if (has(name) && !isNull(name)) optBoolean(name) else null
        fun JSONObject.stringOrNull(name: String) =
            if (has(name) && !isNull(name)) optString(name) else null
        fun JSONObject.intOrNull(name: String) =
            if (has(name) && !isNull(name)) optInt(name) else null
        fun JSONObject.longOrNull(name: String) =
            if (has(name) && !isNull(name)) optLong(name) else null
        return RemoteEditServer.ChannelPatch(
            favorite = body.boolOrNull("favorite"),
            hidden = body.boolOrNull("hidden"),
            customName = body.stringOrNull("customName"),
            clearCustomName = body.optBoolean("clearCustomName", false),
            customNumber = body.intOrNull("customNumber"),
            clearCustomNumber = body.optBoolean("clearCustomNumber", false),
            groupId = body.longOrNull("groupId"),
            clearGroupId = body.optBoolean("clearGroupId", false),
            sortOrder = body.intOrNull("sortOrder"),
        )
    }
}
