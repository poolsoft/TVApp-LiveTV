package com.tvapp.livetv.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract for the REMOTEEDIT rollback migration (DB v25 → v24). The SQL
 * itself runs only on a device (see MIGRATION_25_24 in TVAppDatabase); these
 * unit tests pin the schema contract it must produce so a future schema
 * change cannot silently break the downgrade path. Schemas are read from the
 * repo's app/schemas directory (unit tests do not package them).
 */
class Migration25To24ContractTest {

    @Test
    fun `schema pins version 24 after remote edit rollback`() {
        // @Database is BINARY-retention, so the version is pinned via the
        // exported schema file instead of reflection.
        val versionRegex = Regex("\"version\":\\s*(\\d+)")
        val match = requireNotNull(versionRegex.find(schemaFile().readText(Charsets.UTF_8)))
        assertEquals(24, match.groupValues[1].toInt())
    }

    @Test
    fun `v24 schema keeps every pre-remote-edit user_channels column`() {
        val columns = v24UserChannelColumns
        assertEquals(
            setOf(
                "sourceKey",
                "sourceType",
                "originalDisplayNumber",
                "lastKnownName",
                "customNumber",
                "customName",
                "sortOrder",
                "favorite",
                "hidden",
                "groupId",
                "epgIdOverride",
                "epgSourceIdOverride",
                "playbackEngineOverride",
                "lastSeenAt",
            ),
            columns,
        )
    }

    @Test
    fun `v24 schema has no remote edit artifacts`() {
        assertTrue("revision" !in v24UserChannelColumns)
    }

    private fun schemaFile(): File {
        // Repo layout: working dir is <root>/app for Gradle-run unit tests.
        return File("schemas/com.tvapp.livetv.data.local.TVAppDatabase/24.json")
            .takeIf(File::isFile)
            ?: File("app/schemas/com.tvapp.livetv.data.local.TVAppDatabase/24.json")
                .takeIf(File::isFile)
            ?: throw AssertionError("Schema 24.json not found from working dir ${File(".").absolutePath}")
    }

    private val v24UserChannelColumns: Set<String> by lazy {
        val schema = schemaFile().readText(Charsets.UTF_8)
        val createStart = schema.indexOf("CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`sourceKey`")
        val createEnd = schema.indexOf("PRIMARY KEY", createStart)
        val create = schema.substring(createStart, createEnd)
        Regex("`([a-zA-Z]+)`").findAll(create).map { it.groupValues[1] }
            .filter { it != "TABLE_NAME" }
            .toSet()
    }
}
