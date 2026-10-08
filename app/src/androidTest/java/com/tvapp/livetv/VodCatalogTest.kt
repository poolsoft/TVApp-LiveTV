package com.tvapp.livetv

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvapp.livetv.data.VodCatalogQuery
import com.tvapp.livetv.data.VodFilter
import com.tvapp.livetv.data.VodRepository
import com.tvapp.livetv.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VodCatalogTest {
    @Test fun migrationPreservesExistingSourceAndAddsCache() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE iptv_sources (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                        db.execSQL("INSERT INTO iptv_sources VALUES (7, 'Existing source')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        try {
            val db = helper.writableDatabase
            TVAppDatabase.MIGRATION_26_27.migrate(db)
            db.query("SELECT name FROM iptv_sources WHERE id=7").use {
                assertTrue(it.moveToFirst()); assertEquals("Existing source", it.getString(0))
            }
            db.query("SELECT * FROM vod_metadata").use { assertEquals(0, it.count); assertEquals(14, it.columnCount) }
        } finally { helper.close() }
    }
    @Test fun largeCatalogIsBoundedAndFiltersStayWithinSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TVAppDatabase::class.java).build()
        try {
            val source = db.iptvDao().insertSource(IptvSourceEntity(name = "Fixture", location = "fixture:vod", kind = "FILE"))
            for (batch in (0 until 15000).chunked(500)) {
                db.iptvDao().upsertChannels(batch.map { index ->
                    IptvChannelEntity("fixture:$index", source, null, null, "Movie $index", "https://example.invalid/$index.mp4",
                        null, if (index % 2 == 0) "Even" else "Odd", null, null, originalIndex = index,
                        contentType = "VOD", lastSeenAt = 1)
                })
            }
            val dao = db.vodDao()
            assertEquals(15000, dao.count(VodCatalogQuery.build(source, VodFilter(), count = true)))
            val last = dao.page(VodCatalogQuery.build(source, VodFilter(), offset = 14940, limit = 999))
            assertEquals(60, last.size)
            assertEquals("fixture:14940", last.first().sourceKey)
            assertEquals(7500, dao.count(VodCatalogQuery.build(source, VodFilter(category = "Even"), count = true)))
            val categoryCount = dao.count(VodCatalogQuery.build(source, VodFilter(category = "Even", query = "Movie 1"), count = true))
            assertTrue(dao.count(VodCatalogQuery.build(source, VodFilter(category = "Even", query = "Movie 1", searchWholeSource = true), count = true)) > categoryCount)
            assertEquals(0, dao.count(VodCatalogQuery.build(source + 1, VodFilter(), count = true)))
            assertEquals(0, dao.count(VodCatalogQuery.build(source, VodFilter(query = "%"), count = true)))
            assertEquals(0, dao.count(VodCatalogQuery.build(source, VodFilter(view = "CONTINUE"), count = true)))
            val recent = dao.page(VodCatalogQuery.build(source, VodFilter(view = "RECENT"), listOf("fixture:42", "fixture:1")))
            assertEquals(listOf("fixture:42", "fixture:1"), recent.map { it.sourceKey })
            db.channelDao().upsertChannels(listOf(UserChannelEntity(sourceKey = "fixture:42", sourceType = "IPTV",
                originalDisplayNumber = "", lastKnownName = "Movie 42", sortOrder = 0, favorite = true, lastSeenAt = 1)))
            assertEquals("fixture:42", dao.page(VodCatalogQuery.build(source, VodFilter(view = "FAVORITES"))).single().sourceKey)
            val series = VodMetadataEntity("fixture:series", source, "SERIES", "7", name = "Series", updatedAt = 1)
            dao.upsert(listOf(series, VodMetadataEntity("fixture:episode", source, "EPISODE", "8", parentKey = series.sourceKey,
                seasonNumber = 2, episodeNumber = 3, name = "Episode", streamUrl = "https://example.invalid/8.mkv", updatedAt = 1)))
            assertEquals(listOf(2), dao.seasons(series.sourceKey))
            assertEquals("SERIES", dao.page(VodCatalogQuery.build(source, VodFilter(section = "SERIES"))).single().kind)
            assertEquals("EPISODE", dao.page(VodCatalogQuery.build(source, VodFilter(parentKey = series.sourceKey, season = 2))).single().kind)
            dao.upsert(listOf(
                VodMetadataEntity("fixture:later", source, "EPISODE", "9", parentKey = series.sourceKey,
                    seasonNumber = 3, episodeNumber = 1, name = "Next season", streamUrl = "https://example.invalid/9.mkv", updatedAt = 1),
                VodMetadataEntity("fixture:earlier", source, "EPISODE", "6", parentKey = series.sourceKey,
                    seasonNumber = 2, episodeNumber = 2, name = "Earlier", streamUrl = "https://example.invalid/6.mkv", updatedAt = 1)))
            val repository = VodRepository(context, db)
            assertEquals("fixture:later", repository.nextEpisode("fixture:episode")?.sourceKey)
            assertNull(repository.nextEpisode("fixture:later"))
            assertEquals(listOf("fixture:episode"), repository.continueItems(source, "SERIES",
                listOf("fixture:episode", "fixture:earlier", "fixture:42")).map { it.sourceKey })
            assertTrue(repository.continueItems(source + 1, "SERIES", listOf("fixture:episode")).isEmpty())
            val combined = repository.continueItems(source, "ALL", listOf("fixture:42", "fixture:episode", "fixture:earlier"))
            assertEquals(listOf("fixture:42", "fixture:episode"), combined.map { it.sourceKey })
            assertEquals(2, repository.count(source, VodFilter(view = "CONTINUE"), combined.map { it.sourceKey }))
        } finally { db.close() }
    }
}
