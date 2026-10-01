package com.tvapp.livetv

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tvapp.livetv.data.local.IptvChannelEntity
import com.tvapp.livetv.data.local.IptvSourceEntity
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.ui.AlphabetJump
import com.tvapp.livetv.ui.AlphabetTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlphabetCatalogTest {
    @Test
    fun filteredInitialsAndJumpPositionsUseOnlyMatchingRows() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TVAppDatabase::class.java).build()
        try {
            val dao = database.iptvDao()
            val sourceId = dao.insertSource(IptvSourceEntity(name = "Test", location = "test", kind = "file"))
            dao.upsertChannels(listOf(
                channel(sourceId, "TRT", 0), channel(sourceId, "ATV", 20),
                channel(sourceId, " t kanal", 30), channel(sourceId, "Z film", 40, type = "VOD"),
                channel(sourceId, "B spor", 50, category = "Spor"),
            ))
            val targets = AlphabetJump.targets(dao.libraryInitials(sourceId, "Genel", "LIVE", "")
                .map { it.initial to it.firstIndex })
            assertEquals(listOf(AlphabetTarget("A", 20), AlphabetTarget("T", 0)), targets)
            assertEquals(1, dao.libraryCountBefore(sourceId, "Genel", "LIVE", "", 20))
            assertEquals("ATV", dao.getLibraryPageFrom(sourceId, "Genel", "LIVE", "", 20, 1).single().displayName)
            assertEquals(5, dao.channelCount(sourceId))
            assertEquals(0, dao.selectedChannelCount(sourceId))
        } finally {
            database.close()
        }
    }

    private fun channel(sourceId: Long, name: String, index: Int, type: String = "LIVE", category: String = "Genel") =
        IptvChannelEntity(
            sourceKey = "test:$index", sourceId = sourceId, displayName = name, originalIndex = index,
            contentType = type, groupTitle = category, lastSeenAt = 1, streamUrl = "https://example.invalid/test",
            tvgId = null, tvgName = null, logoUrl = null, userAgent = null, referrer = null,
        )
}
