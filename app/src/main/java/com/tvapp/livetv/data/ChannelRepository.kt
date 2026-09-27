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

    fun physicalInputs(): List<TvInputInfo> = tifRepository.physicalInputs()

    fun tifChannelRawValues(channelId: Long): Result<List<Pair<String, String>>> =
        tifRepository.channelRawValues(channelId)

    fun tunerSetupIntent(): Intent? = tunerInputs()
        .firstNotNullOfOrNull { input -> input.createSetupIntent() }

    suspend fun channels(
        includeHidden: Boolean = false,
        refreshSources: Boolean = false,
        includeTif: Boolean = true,
    ): Result<List<LiveChannel>> = runCatching {
        val startedAt = SystemClock.elapsedRealtime()
        val iptvChannels = iptvRepository.channels()
        val afterIptv = SystemClock.elapsedRealtime()
        val tifChannels = if (includeTif) {
            tifRepository.channels(forceRefresh = refreshSources).getOrElse { error ->
                if (iptvChannels.isEmpty()) throw error else emptyList()
            }
        } else {
            emptyList()
        }
        val source = tifChannels + iptvChannels
        val afterTif = SystemClock.elapsedRealtime()
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
        if (changed.isNotEmpty()) {
            database.withTransaction {
                channelDao.upsertChannels(changed)
            }
        }
        val merged = ChannelMerger.merge(source, synchronized, includeHidden)
        if (merged.isNotEmpty()) {
            cachedChannels = merged
        }
        val finishedAt = SystemClock.elapsedRealtime()
        debugLog.recordDebug(
            "CHANNEL_LOAD_TIMING | iptv=${afterIptv - startedAt}ms, " +
                "tif=${if (includeTif) "${afterTif - afterIptv}ms" else "skipped"}, " +
                "roomMerge=${finishedAt - afterTif}ms, " +
                "total=${finishedAt - startedAt}ms, count=${merged.size}, " +
                "refresh=$refreshSources, includeTif=$includeTif",
        )
        merged
    }

    suspend fun setFavorite(sourceKey: String, favorite: Boolean) =
        channelDao.setFavorite(sourceKey, favorite)

    suspend fun setFavorite(channel: LiveChannel, favorite: Boolean) = database.withTransaction {
        val existing = channelDao.getChannel(channel.sourceKey)
        val row = existing ?: UserChannelEntity(
            sourceKey = channel.sourceKey,
            sourceType = channel.source.name,
            originalDisplayNumber = channel.displayNumber,
            lastKnownName = channel.displayName,
            sortOrder = (channelDao.maxSortOrder() ?: -1) + 1,
            lastSeenAt = System.currentTimeMillis(),
        )
        channelDao.upsertChannels(listOf(row.copy(favorite = favorite)))
    }

    suspend fun isFavorite(sourceKey: String): Boolean =
        channelDao.getChannel(sourceKey)?.favorite == true

    suspend fun setHidden(sourceKey: String, hidden: Boolean) =
        channelDao.setHidden(sourceKey, hidden)

    suspend fun setCustomNumber(sourceKey: String, number: Int?) =
        channelDao.setCustomNumber(sourceKey, number)

    suspend fun setCustomName(sourceKey: String, name: String?) =
        channelDao.setCustomName(sourceKey, name)

    suspend fun setGroup(sourceKey: String, groupId: Long?) =
        channelDao.setGroup(sourceKey, groupId)

    suspend fun channelPreference(sourceKey: String): UserChannelEntity? =
        channelDao.getChannel(sourceKey)

    suspend fun channelPreferences(): Map<String, UserChannelEntity> =
        channelDao.getAllChannels().associateBy(UserChannelEntity::sourceKey)

    suspend fun setEpgOverride(sourceKey: String, epgId: String?, sourceId: Long?) {
        channelDao.setEpgOverride(sourceKey, epgId, sourceId)
        EpgSnapshotCache.invalidate(sourceKey)
    }

    suspend fun setPlaybackEngineOverride(channel: LiveChannel, engine: String?) =
        database.withTransaction {
            val existing = channelDao.getChannel(channel.sourceKey)
            val row = existing ?: UserChannelEntity(
                sourceKey = channel.sourceKey,
                sourceType = channel.source.name,
                originalDisplayNumber = channel.displayNumber,
                lastKnownName = channel.displayName,
                sortOrder = (channelDao.maxSortOrder() ?: -1) + 1,
                lastSeenAt = System.currentTimeMillis(),
            )
            channelDao.upsertChannels(listOf(row.copy(playbackEngineOverride = engine)))
        }

    suspend fun setSortOrder(sourceKey: String, sortOrder: Int) =
        channelDao.setSortOrder(sourceKey, sortOrder)

    /** REMOTEEDIT-004 result for a revision-guarded edit. */
    sealed class RemoteEditResult {
        data class Success(val newRevision: Long) : RemoteEditResult()
        data class Conflict(val currentRevision: Long) : RemoteEditResult()
        data object NotFound : RemoteEditResult()
    }

    /** REMOTEEDIT-004: revision-guarded field patch applied through the same
     *  DAO writes the TV UI uses. A row that appears later (created from a
     *  LiveChannel on demand) starts at revision 0, so a client that saw the
     *  empty list can still patch it with revision 0. */
    suspend fun remotePatchChannel(
        sourceKey: String,
        expectedRevision: Long,
        favorite: Boolean? = null,
        hidden: Boolean? = null,
        customName: String? = null,
        clearCustomName: Boolean = false,
        customNumber: Int? = null,
        clearCustomNumber: Boolean = false,
        groupId: Long? = null,
        clearGroupId: Boolean = false,
        sortOrder: Int? = null,
    ): RemoteEditResult = database.withTransaction {
        val existing = channelDao.getChannel(sourceKey)
        if (existing == null) {
            if (expectedRevision != 0L) return@withTransaction RemoteEditResult.Conflict(0L)
            // Row does not exist yet: nothing editable to patch.
            return@withTransaction RemoteEditResult.NotFound
        }
        if (existing.revision != expectedRevision) {
            return@withTransaction RemoteEditResult.Conflict(existing.revision)
        }
        if (clearCustomName) channelDao.setCustomNameBumpingRevision(sourceKey, null)
        if (clearCustomNumber) channelDao.setCustomNumberBumpingRevision(sourceKey, null)
        if (clearGroupId) channelDao.setGroupBumpingRevision(sourceKey, null)
        if (favorite != null) channelDao.setFavoriteBumpingRevision(sourceKey, favorite)
        if (hidden != null) channelDao.setHiddenBumpingRevision(sourceKey, hidden)
        if (!clearCustomName && customName != null) {
            channelDao.setCustomNameBumpingRevision(sourceKey, customName)
        }
        if (!clearCustomNumber && customNumber != null) {
            channelDao.setCustomNumberBumpingRevision(sourceKey, customNumber)
        }
        if (!clearGroupId && groupId != null) {
            channelDao.setGroupBumpingRevision(sourceKey, groupId)
        }
        if (sortOrder != null) channelDao.setSortOrderBumpingRevision(sourceKey, sortOrder)
        RemoteEditResult.Success(channelDao.revisionOf(sourceKey) ?: existing.revision)
    }

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

    companion object {
        @Volatile
        private var cachedChannels: List<LiveChannel> = emptyList()

        fun cachedChannels(): List<LiveChannel> = cachedChannels
    }
}
