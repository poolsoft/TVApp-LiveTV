package com.tvapp.livetv

import androidx.room.Room
import android.util.JsonWriter
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvapp.livetv.data.local.IptvSourceEntity
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.settings.IptvSourcePlaybackOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.StringWriter
import com.tvapp.livetv.data.AppBackupRepository

@RunWith(AndroidJUnit4::class)
class IptvSourcePlaybackTest {
    @Test fun sourceBackupRoundTripAndOlderBackupsKeepInheritance() {
        // Exercise the existing private backup codec without opening the user's database.
        val companion = AppBackupRepository::class.java.getDeclaredField("Companion").apply {
            isAccessible = true
        }.get(null)
        val write = companion.javaClass.getDeclaredMethod("writeFields", JsonWriter::class.java, IptvSourceEntity::class.java).apply {
            isAccessible = true
        }
        val read = companion.javaClass.getDeclaredMethod("toIptvSource", JSONObject::class.java).apply {
            isAccessible = true
        }
        val source = IptvSourceEntity(id = 7, name = "Test", location = "test", kind = "file",
            liveBufferSeconds = 5, vodBufferSeconds = 30, maximumVideoHeight = 0, automaticRecovery = false)
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.beginObject()
            write.invoke(companion, writer, source)
            writer.endObject()
        }
        val json = JSONObject(output.toString())
        assertEquals(source, read.invoke(companion, json))
        listOf("liveBufferSeconds", "vodBufferSeconds", "maximumVideoHeight", "automaticRecovery").forEach(json::remove)
        assertEquals(source.copy(liveBufferSeconds = null, vodBufferSeconds = null,
            maximumVideoHeight = null, automaticRecovery = null), read.invoke(companion, json))
    }
    @Test fun sourceOptionsSurviveMetadataRefreshAndBufferUpdates() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TVAppDatabase::class.java).build()
        try {
            val dao = database.iptvDao()
            val id = dao.insertSource(IptvSourceEntity(name = "Test", location = "test", kind = "file"))
            assertEquals(IptvSourcePlaybackOptions(), dao.sourcePlaybackOptions(id))
            dao.updateSourcePlaybackOptions(id, 5, 30, 720, false)
            dao.updateSource(requireNotNull(dao.getSource(id)).copy(name = "Updated", lastUpdatedAt = 100))
            assertEquals(IptvSourcePlaybackOptions(5, 30, 720, false), dao.sourcePlaybackOptions(id))
            dao.updateVodBuffer(id, 45)
            dao.renameSource(id, "Renamed")
            assertEquals(IptvSourcePlaybackOptions(5, 45, 720, false), dao.sourcePlaybackOptions(id))
            dao.updateSourcePlaybackOptions(id, null, null, null, null)
            assertEquals(IptvSourcePlaybackOptions(), dao.sourcePlaybackOptions(id))
            assertNull(dao.sourcePlaybackOptions(id + 1))
        } finally {
            database.close()
        }
    }

    @Test fun migrationAddsNullablePreferencesWithoutDeletingSources() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE iptv_sources (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                        db.execSQL("INSERT INTO iptv_sources VALUES (7, 'Existing source')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        try {
            val db = helper.writableDatabase
            TVAppDatabase.MIGRATION_24_26.migrate(db)
            db.query("SELECT id, name, liveBufferSeconds, vodBufferSeconds, maximumVideoHeight, automaticRecovery FROM iptv_sources").use {
                check(it.moveToFirst())
                assertEquals(7, it.getInt(0))
                assertEquals("Existing source", it.getString(1))
                for (column in 2..5) check(it.isNull(column))
            }
        } finally {
            helper.close()
        }
    }

    @Test fun experimentalVersion25MigratesAndPreservesChannelPreferences() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE iptv_sources (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                        db.execSQL("INSERT INTO iptv_sources VALUES (7, 'Existing source')")
                        db.execSQL("CREATE TABLE channel_groups (id INTEGER PRIMARY KEY)")
                        db.execSQL("CREATE TABLE paired_devices (id INTEGER PRIMARY KEY)")
                        db.execSQL("CREATE TABLE user_channels (sourceKey TEXT PRIMARY KEY NOT NULL, sourceType TEXT NOT NULL, originalDisplayNumber TEXT NOT NULL, lastKnownName TEXT NOT NULL, customNumber INTEGER, customName TEXT, sortOrder INTEGER NOT NULL, favorite INTEGER NOT NULL, hidden INTEGER NOT NULL, groupId INTEGER, epgIdOverride TEXT, epgSourceIdOverride INTEGER, playbackEngineOverride TEXT, lastSeenAt INTEGER NOT NULL, revision INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO user_channels VALUES ('iptv:test', 'IPTV', '1', 'Test', 9, 'My channel', 2, 1, 0, NULL, 'test.epg', NULL, NULL, 100, 4)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        try {
            val db = helper.writableDatabase
            TVAppDatabase.MIGRATION_25_26.migrate(db)
            db.query("SELECT customNumber, customName, favorite, epgIdOverride FROM user_channels").use {
                check(it.moveToFirst())
                assertEquals(9, it.getInt(0))
                assertEquals("My channel", it.getString(1))
                assertEquals(1, it.getInt(2))
                assertEquals("test.epg", it.getString(3))
            }
            db.query("SELECT maximumVideoHeight FROM iptv_sources WHERE id = 7").use {
                check(it.moveToFirst())
                check(it.isNull(0))
            }
        } finally {
            helper.close()
        }
    }
}
