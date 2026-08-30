package com.tvapp.livetv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvInputInfo
import android.os.SystemClock
import androidx.room.withTransaction
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.UserChannelEntity
import com.tvapp.livetv.model.LiveChannel

class ChannelRepository(context: Context) {
    private val tifRepository = TifRepository(context)
    private val iptvRepository = IptvRepository(context)
    private val database = TVAppDatabase.getInstance(context)
    private val channelDao = database.channelDao()
    private val debugLog = CrashReportStore(context)

    fun tunerInputs(): List<TvInputInfo> = tifRepository.tunerInputs()

    fun tunerSetupIntent(): Intent? = tunerInputs()
        .firstNotNullOfOrNull { input -> input.createSetupIntent() }

    suspend fun channels(
        includeHidden: Boolean = false,
        refreshSources: Boolean = false,
    ): Result<List<LiveChannel>> = runCatching {
        val startedAt = SystemClock.elapsedRealtime()
        val iptvChannels = iptvRepository.channels()
        val afterIptv = SystemClock.elapsedRealtime()
        val tifResult = tifRepository.channels(forceRefresh = refreshSources)
        val source = tifResult.getOrElse { error ->
            if (iptvChannels.isEmpty()) throw error else emptyList()
        } + iptvChannels
        val afterTif = SystemClock.elapsedRealtime()
        val merged = database.withTransaction {
            val now = System.currentTimeMillis()
            val existingRows = channelDao.getAllChannels()
            val existing = existingRows.associateBy { it.sourceKey }
            var nextSortOrder = (existingRows.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val synchronized = source.map { channel ->
                val saved = existing[channel.sourceKey]
                if (saved == null) {
                    UserChannelEntity(
                        sourceKey = channel.sourceKey,
                        sourceType = channel.source.name,
                        originalDisplayNumber = channel.displayNumber,
                        lastKnownName = channel.displayName,
                        sortOrder = nextSortOrder++,
                        lastSeenAt = now,
                    )
                } else if (
                    saved.sourceType != channel.source.name ||
                    saved.originalDisplayNumber != channel.displayNumber ||
                    saved.lastKnownName != channel.displayName
                ) {
                    saved.copy(
                        sourceType = channel.source.name,
                        originalDisplayNumber = channel.displayNumber,
                        lastKnownName = channel.displayName,
                        lastSeenAt = now,
                    )
                } else {
                    saved
                }
            }
            val changed = synchronized.filter { entity -> existing[entity.sourceKey] != entity }
            if (changed.isNotEmpty()) channelDao.upsertChannels(changed)
            ChannelMerger.merge(source, synchronized, includeHidden)
        }
        val finishedAt = SystemClock.elapsedRealtime()
        debugLog.recordDebug(
            "CHANNEL_LOAD_TIMING | iptv=${afterIptv - startedAt}ms, " +
                "tif=${afterTif - afterIptv}ms, roomMerge=${finishedAt - afterTif}ms, " +
                "total=${finishedAt - startedAt}ms, count=${merged.size}, refresh=$refreshSources",
        )
        merged
    }

    suspend fun setFavorite(sourceKey: String, favorite: Boolean) =
        channelDao.setFavorite(sourceKey, favorite)

    suspend fun setHidden(sourceKey: String, hidden: Boolean) =
        channelDao.setHidden(sourceKey, hidden)

    suspend fun setCustomNumber(sourceKey: String, number: Int?) =
        channelDao.setCustomNumber(sourceKey, number)

    suspend fun setCustomName(sourceKey: String, name: String?) =
        channelDao.setCustomName(sourceKey, name)

    suspend fun setGroup(sourceKey: String, groupId: Long?) =
        channelDao.setGroup(sourceKey, groupId)

    suspend fun setSortOrder(sourceKey: String, sortOrder: Int) =
        channelDao.setSortOrder(sourceKey, sortOrder)

    suspend fun moveChannel(sourceKey: String, offset: Int) = database.withTransaction {
        val ordered = channelDao.getOrderedChannels()
        val currentIndex = ordered.indexOfFirst { it.sourceKey == sourceKey }
        if (currentIndex < 0) return@withTransaction
        val targetIndex = (currentIndex + offset).coerceIn(0, ordered.lastIndex)
        if (targetIndex == currentIndex) return@withTransaction
        val current = ordered[currentIndex]
        val target = ordered[targetIndex]
        channelDao.setSortOrder(current.sourceKey, target.sortOrder)
        channelDao.setSortOrder(target.sourceKey, current.sortOrder)
    }

    suspend fun replaceOrder(sourceKeys: List<String>) = database.withTransaction {
        persistOrder(sourceKeys)
    }

    suspend fun moveChannelsToNumber(
        sourceKeys: Set<String>,
        startNumber: Int,
        activeOrder: List<String>? = null,
    ) =
        database.withTransaction {
            val currentOrder = activeOrder ?: channelDao.getOrderedChannels().map { it.sourceKey }
            val reordered = ChannelOrderer.moveToPosition(currentOrder, sourceKeys, startNumber)
            persistOrder(reordered)
        }

    private suspend fun persistOrder(sourceKeys: List<String>) {
        val byKey = channelDao.getAllChannels().associateBy { it.sourceKey }
        val updated = sourceKeys.mapIndexedNotNull { index, sourceKey ->
            byKey[sourceKey]?.copy(sortOrder = index)
        }
        if (updated.isNotEmpty()) channelDao.upsertChannels(updated)
    }
}
