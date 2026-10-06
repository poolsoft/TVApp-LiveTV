package com.tvapp.livetv

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tvapp.livetv.data.local.TVAppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IptvNetworkMigrationTest {
    @Test fun exportedVersion29MigratesAndRoomValidatesWithoutLosingUserData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.tvapp.livetv.data.local.TVAppDatabase/29.json").bufferedReader().use {
                JSONObject(it.readText()).getJSONObject("database")
            }
        val name = "fixture-network-migration-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(29) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (index in 0 until entities.length()) {
                            val entity = entities.getJSONObject(index)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                            for (item in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                            val triggers = entity.optJSONArray("contentSyncTriggers") ?: org.json.JSONArray()
                            for (item in 0 until triggers.length()) db.execSQL(triggers.getString(item))
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                        db.execSQL("INSERT INTO iptv_sources (id,name,location,kind,enabled,lastUpdatedAt,continuousLiveReconnect,liveReconnectLeadMillis) VALUES (7,'Existing','fixture','URL',1,100,1,4750)")
                        db.execSQL("INSERT INTO iptv_channels (sourceKey,sourceId,displayName,streamUrl,originalIndex,contentType,selected,lastSeenAt,matchKey,catchUpDays) VALUES ('fixture:old',7,'Old','https://fixture.invalid/live.ts',42,'LIVE',1,100,'old',0)")
                        db.execSQL("INSERT INTO user_channels (sourceKey,sourceType,originalDisplayNumber,lastKnownName,sortOrder,favorite,hidden,lastSeenAt) VALUES ('fixture:old','IPTV','43','Old',9,1,0,100)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        var room: TVAppDatabase? = null
        try {
            helper.writableDatabase
            helper.close()
            room = Room.databaseBuilder(context, TVAppDatabase::class.java, name)
                .addMigrations(TVAppDatabase.MIGRATION_29_30).addCallback(TVAppDatabase.IPTV_SEARCH_CALLBACK).build()
            val source = room.iptvDao().getSource(7)!!
            assertEquals("Existing", source.name)
            assertEquals(4_750, source.liveReconnectLeadMillis)
            assertTrue(source.continuousLiveReconnect)
            assertNull(source.connectionLimit())
            assertNull(source.defaultOrigin)
            assertTrue(room.iptvDao().getChannel("fixture:old")!!.selected)
            val preference = room.channelDao().getAllChannels().single()
            assertEquals(9, preference.sortOrder)
            assertTrue(preference.favorite)
        } finally {
            helper.close()
            room?.close()
            context.deleteDatabase(name)
        }
    }
}
