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
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TVAppDatabase::class.java)
            .addCallback(TVAppDatabase.IPTV_SEARCH_CALLBACK)
            .build()
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
            val initials = listOf("T", "t")
            assertEquals(2, dao.libraryCount(sourceId, "Genel", "LIVE", "", initials, true))
            val first = dao.getLibraryPageFrom(sourceId, "Genel", "LIVE", "", 0, 1, initials, true).single()
            assertEquals("TRT", first.displayName)
            val next = dao.getLibraryPageAfter(sourceId, "Genel", "LIVE", "", first.originalIndex, first.sourceKey, 1, initials, true).single()
            assertEquals(" t kanal", next.displayName)
            assertEquals(first, dao.getLibraryPageBefore(sourceId, "Genel", "LIVE", "", next.originalIndex, next.sourceKey, 1, initials, true).single())
            assertEquals(next, dao.getLibraryLastPage(sourceId, "Genel", "LIVE", "", 1, initials, true).single())
            assertEquals(next, dao.getAlphabetPageAtOrdinal(sourceId, "Genel", "LIVE", "", initials, 1, 1).single())
            assertEquals(0, dao.libraryCount(sourceId, "Genel", "LIVE", "", emptyList(), true))
            assertEquals(3, dao.libraryCount(sourceId, "Genel", "LIVE", ""))
            assertEquals(1, dao.libraryCount(sourceId, "Genel", "LIVE", "kanal*", initials, true))
            assertEquals(5, dao.channelCount(sourceId))
            assertEquals(0, dao.selectedChannelCount(sourceId))
            val other = dao.insertSource(IptvSourceEntity(name = "Turkish", location = "other", kind = "file"))
            dao.upsertChannels(listOf(
                channel(other, "izmir", 0), channel(other, "IRMAK", 1),
                channel(other, "İZ TV", 2), channel(other, "ışık", 3),
                channel(other, "3 haber", 4), channel(other, "! spor", 5),
            ))
            val groups = dao.libraryInitials(other, null, "ALL", "")
                .groupBy { AlphabetJump.letter(it.initial) }
                .mapValues { (_, rows) -> rows.map { it.initial } }
            assertEquals(2, dao.libraryCount(other, null, "ALL", "", groups.getValue("İ"), true))
            assertEquals(2, dao.libraryCount(other, null, "ALL", "", groups.getValue("I"), true))
            assertEquals(2, dao.libraryCount(other, null, "ALL", "", groups.getValue("#"), true))
            assertEquals(5, dao.channelCount(sourceId))
        } finally {
            database.close()
        }
    }

    private fun channel(sourceId: Long, name: String, index: Int, type: String = "LIVE", category: String = "Genel") =
        IptvChannelEntity(
            sourceKey = "test:$sourceId:$index", sourceId = sourceId, displayName = name, originalIndex = index,
            contentType = type, groupTitle = category, lastSeenAt = 1, streamUrl = "https://example.invalid/test",
            tvgId = null, tvgName = null, logoUrl = null, userAgent = null, referrer = null,
        )
}
