package com.tvapp.livetv

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvapp.livetv.data.local.*
import com.tvapp.livetv.tifinput.IptvInputChannelMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IptvNetworkStagingTest {
    @Test fun largeStagingRefreshPreservesSelectionPreferencesAndHeaderOverrides() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TVAppDatabase::class.java)
            .addCallback(TVAppDatabase.IPTV_SEARCH_CALLBACK).build()
        try {
            val dao = db.iptvDao()
            val source = dao.insertSource(IptvSourceEntity(name = "Fixture", location = "fixture:network", kind = "URL",
                defaultOrigin = "https://source.invalid", defaultUserAgent = "Source Agent"))
            dao.upsertChannels(listOf(IptvChannelEntity(sourceKey = "fixture:old", sourceId = source,
                tvgId = "fixture.id", tvgName = null, displayName = "Old", streamUrl = "https://fixture.invalid/old.ts",
                logoUrl = null, groupTitle = null, userAgent = null, referrer = null, originalIndex = 0,
                selected = true, lastSeenAt = 1)))
            val preference = UserChannelEntity("fixture:old", "IPTV", "1", "Old", sortOrder = 7,
                favorite = true, playbackEngineOverride = "MEDIA3", lastSeenAt = 1)
            db.channelDao().upsertChannels(listOf(preference))
            for (batch in (0 until 15_000).chunked(500)) {
                dao.insertStagedChannels(batch.map { index -> IptvChannelStagingEntity(
                    sessionId = "fixture", originalIndex = index, identityHash = "fixture:$index",
                    tvgId = if (index == 42) "fixture.id" else "id.$index", tvgName = null,
                    displayName = "New $index", streamUrl = "https://fixture.invalid/$index.ts", logoUrl = null,
                    groupTitle = null, userAgent = null, referrer = null,
                    origin = if (index == 42) "https://channel.invalid" else null, subtitleUrl = null,
                    contentType = "LIVE", matchKey = "new:$index", catchUpMode = null, catchUpSource = null,
                    catchUpDays = 0, createdAt = 2)
                })
            }
            db.withTransaction {
                dao.resolveStagedSourceKeys("fixture", source)
                dao.discardSupersededStagedDuplicates("fixture")
                dao.preserveStagedSelection("fixture")
                dao.deleteSourceChannels(source)
                dao.insertStagedAsSource("fixture", source, 2)
            }
            assertEquals(15_000, dao.channelCount(source))
            assertEquals(1, dao.selectedChannelCount(source))
            val channel = dao.getChannel("fixture:old")!!
            assertEquals(42, channel.originalIndex)
            assertEquals("https://channel.invalid", channel.origin)
            assertEquals(preference, db.channelDao().getAllChannels().single())
            val shared = dao.getSharedChannelsPage(10, 0).single()
            assertEquals("Source Agent", shared.userAgent)
            assertEquals("https://channel.invalid", shared.origin)
            val metadata = IptvInputChannelMetadata.from(shared)
            assertEquals(metadata, IptvInputChannelMetadata.decode(metadata.encode()))
            dao.upsertChannels(listOf(channel.copy(origin = null)))
            assertEquals("https://source.invalid", dao.getSharedChannelsPage(10, 0).single().origin)
            assertEquals(1, dao.getSelectionPage(source, null, "\"New\"", true, 60, 0).size)
        } finally { db.close() }
    }
}
