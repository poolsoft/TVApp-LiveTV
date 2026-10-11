package com.tvapp.livetv.data.local

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveConnectionPreferenceTest {
    private fun channel(key: String) = UserChannelEntity(
        sourceKey = key, sourceType = "IPTV", originalDisplayNumber = "1",
        lastKnownName = "Test", sortOrder = 0, lastSeenAt = 0,
    )

    @Test fun `existing and new channels default to standard method`() {
        assertFalse(channel("iptv:1").removeLiveOverlap)
        val fields = fields(31)
        val flag = fields.getValue("removeLiveOverlap")
        assertEquals("0", flag.getString("defaultValue"))
        assertTrue(flag.getBoolean("notNull"))
    }

    @Test fun `choice is channel specific and catalog metadata changes preserve it`() {
        val chosen = channel("iptv:1").copy(removeLiveOverlap = true, favorite = true)
        assertTrue(chosen.copy(lastKnownName = "Updated").removeLiveOverlap)
        assertFalse(channel("iptv:2").removeLiveOverlap)
        assertTrue(chosen.copy(removeLiveOverlap = false).favorite)
        assertFalse(chosen.copy(removeLiveOverlap = false).removeLiveOverlap)
    }

    @Test fun `schema migration retains all previous channel columns`() {
        assertEquals(fields(30).keys + "removeLiveOverlap", fields(31).keys)
        for ((name, old) in fields(30)) {
            assertEquals(old.toString(), fields(31).getValue(name).toString())
        }
    }

    private fun fields(version: Int): Map<String, JSONObject> {
        val relative = "schemas/com.tvapp.livetv.data.local.TVAppDatabase/$version.json"
        val file = File(relative).takeIf(File::isFile) ?: File("app/$relative")
        val entities = JSONObject(file.readText()).getJSONObject("database").getJSONArray("entities")
        val entity = (0 until entities.length()).map(entities::getJSONObject)
            .single { it.getString("tableName") == "user_channels" }
        val fields = entity.getJSONArray("fields")
        return (0 until fields.length()).map(fields::getJSONObject).associateBy { it.getString("columnName") }
    }
}
