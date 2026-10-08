package com.tvapp.livetv

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvapp.livetv.data.XmlTvRepository
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.XmlTvProgramEntity
import com.tvapp.livetv.data.local.XmlTvSourceEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XmlTvAtomicImportTest {
    private lateinit var database: TVAppDatabase
    private lateinit var repository: XmlTvRepository
    private var sourceId = 0L

    @Before
    fun createIsolatedDatabase() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, TVAppDatabase::class.java).build()
        repository = XmlTvRepository(context, database)
        sourceId = database.xmlTvDao().insertSource(XmlTvSourceEntity(
            name = "Test", location = "test-source", kind = "url", lastUpdatedAt = 1,
        ))
        database.xmlTvDao().insertPrograms(listOf(XmlTvProgramEntity(
            sourceId = sourceId, channelId = "test", channelName = "Test",
            normalizedChannelId = "test", normalizedChannelName = "test",
            title = "Old programme", description = "", startTimeMillis = 1, endTimeMillis = 2,
        )))
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun truncatedFeedAfterFullBatchPreservesExistingData() {
        val feed = buildString {
            append("<tv><channel id=\"test\"><display-name>Test</display-name></channel>")
            repeat(1_001) {
                append("<programme channel=\"test\" start=\"20990101000000 +0000\" stop=\"20990101010000 +0000\"><title>New</title></programme>")
            }
            append("<programme")
        }
        assertImportFailsAndPreservesOldData(feed)
    }

    @Test
    fun emptyFeedDoesNotEraseExistingGuide() {
        assertImportFailsAndPreservesOldData("<tv></tv>")
    }

    @Test
    fun expiredFeedDoesNotEraseExistingGuide() {
        assertImportFailsAndPreservesOldData(
            "<tv><programme channel=\"test\" start=\"20000101000000 +0000\" " +
                "stop=\"20000101010000 +0000\"><title>Expired</title></programme></tv>",
        )
    }

    @Test
    fun successCountMatchesPublishedSourceSummary() {
        val feed = "<tv><channel id=\"test\"><display-name>Test</display-name></channel>" +
            "<programme channel=\"test\" start=\"20000101000000 +0000\" " +
            "stop=\"20000101010000 +0000\"><title>Expired</title></programme>" +
            "<programme channel=\"test\" start=\"20990101000000 +0000\" " +
            "stop=\"20990101010000 +0000\"><title>Current</title></programme></tv>"
        val count = feed.byteInputStream().use {
            repository.importStream(it, "test-source", "Test", "url")
        }
        val summary = repository.sourceSummaries().single()
        assertEquals(1, count)
        assertEquals(count, summary.programCount)
        assertEquals(1, summary.channelCount)
        assertEquals(listOf("Current"), database.xmlTvDao().allPrograms().map { it.title })
    }

    private fun assertImportFailsAndPreservesOldData(feed: String) {
        val result = runCatching {
            feed.byteInputStream().use {
                repository.importStream(it, "test-source", "Test", "url")
            }
        }
        assertTrue(result.isFailure)
        val dao = database.xmlTvDao()
        assertEquals(listOf("Old programme"), dao.allPrograms().map { it.title })
        assertEquals(listOf(sourceId), dao.sources().map { it.id })
        assertEquals(1L, dao.sourceById(sourceId)!!.lastUpdatedAt)
        assertEquals(1, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM xmltv_sources").use {
            it.moveToFirst()
            it.getInt(0)
        })
    }
}
