package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.tvapp.livetv.tifinput.SharedIptvInputChannel

@Dao
interface IptvDao {
    @Query(
        "SELECT c.sourceKey AS sourceKey, " +
            "COALESCE(u.customName, c.displayName) AS displayName, " +
            "c.streamUrl AS streamUrl, c.logoUrl AS logoUrl, " +
            "c.groupTitle AS groupTitle, c.tvgId AS epgId, " +
            "c.userAgent AS userAgent, c.referrer AS referrer " +
            "FROM iptv_channels c " +
            "INNER JOIN iptv_sources s ON s.id = c.sourceId " +
            "LEFT JOIN user_channels u ON u.sourceKey = c.sourceKey " +
            "WHERE s.enabled = 1 AND c.selected = 1 AND COALESCE(u.hidden, 0) = 0 " +
            "ORDER BY COALESCE(u.sortOrder, 2147483647), s.name, c.originalIndex " +
            "LIMIT :limit OFFSET :offset",
    )
    fun getSharedChannelsPage(limit: Int, offset: Int): List<SharedIptvInputChannel>

    @Query("SELECT * FROM iptv_sources ORDER BY name")
    suspend fun getSources(): List<IptvSourceEntity>

    @Query("SELECT * FROM iptv_sources WHERE location = :location LIMIT 1")
    suspend fun getSourceByLocation(location: String): IptvSourceEntity?

    @Query("SELECT * FROM iptv_sources WHERE id = :sourceId LIMIT 1")
    suspend fun getSource(sourceId: Long): IptvSourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: IptvSourceEntity): Long

    @Update
    suspend fun updateSource(source: IptvSourceEntity)

    @Upsert
    suspend fun upsertSources(sources: List<IptvSourceEntity>)

    @Delete
    suspend fun deleteSource(source: IptvSourceEntity)

    @Query("DELETE FROM iptv_sources")
    suspend fun deleteAllSources()

    @Query("SELECT COUNT(*) FROM iptv_channels WHERE sourceId = :sourceId")
    suspend fun channelCount(sourceId: Long): Int

    @Query("SELECT COUNT(*) FROM iptv_channels WHERE sourceId = :sourceId AND selected = 1")
    suspend fun selectedChannelCount(sourceId: Long): Int

    @Query("SELECT * FROM iptv_channels WHERE sourceId = :sourceId AND selected = 1")
    suspend fun getSelectedChannelsForSource(sourceId: Long): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels WHERE sourceId = :sourceId AND " +
            "(originalIndex > :anchorIndex OR " +
            "(originalIndex = :anchorIndex AND sourceKey > :anchorKey)) " +
            "ORDER BY originalIndex, sourceKey LIMIT :limit",
    )
    suspend fun getBackupPageAfter(
        sourceId: Long,
        anchorIndex: Int,
        anchorKey: String,
        limit: Int,
    ): List<IptvChannelEntity>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "ORDER BY originalIndex LIMIT :limit OFFSET :offset",
    )
    suspend fun getChannelsPage(
        sourceId: Long,
        category: String?,
        limit: Int,
        offset: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "ORDER BY originalIndex LIMIT :limit OFFSET :offset",
    )
    suspend fun getLibraryPage(
        sourceId: Long,
        category: String?,
        contentType: String,
        query: String,
        limit: Int,
        offset: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND (originalIndex > :anchorIndex " +
            "OR (originalIndex = :anchorIndex AND sourceKey > :anchorKey)) " +
            "ORDER BY originalIndex, sourceKey LIMIT :limit",
    )
    suspend fun getLibraryPageAfter(
        sourceId: Long,
        category: String?,
        contentType: String,
        query: String,
        anchorIndex: Int,
        anchorKey: String,
        limit: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND (originalIndex < :anchorIndex " +
            "OR (originalIndex = :anchorIndex AND sourceKey < :anchorKey)) " +
            "ORDER BY originalIndex DESC, sourceKey DESC LIMIT :limit",
    )
    suspend fun getLibraryPageBefore(
        sourceId: Long,
        category: String?,
        contentType: String,
        query: String,
        anchorIndex: Int,
        anchorKey: String,
        limit: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "ORDER BY originalIndex DESC, sourceKey DESC LIMIT :limit",
    )
    suspend fun getLibraryLastPage(
        sourceId: Long,
        category: String?,
        contentType: String,
        query: String,
        limit: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND originalIndex >= :targetIndex " +
            "ORDER BY originalIndex, sourceKey LIMIT :limit",
    )
    suspend fun getLibraryPageAtOrAfter(
        sourceId: Long,
        category: String?,
        contentType: String,
        query: String,
        targetIndex: Int,
        limit: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT COUNT(*) FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query))",
    )
    suspend fun libraryCount(sourceId: Long, category: String?, contentType: String, query: String): Int

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND (:selectedOnly = 0 OR selected = 1) " +
            "ORDER BY originalIndex LIMIT :limit OFFSET :offset",
    )
    suspend fun getSelectionPage(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND (:selectedOnly = 0 OR selected = 1) " +
            "AND (originalIndex > :anchorIndex " +
            "OR (originalIndex = :anchorIndex AND sourceKey > :anchorKey)) " +
            "ORDER BY originalIndex, sourceKey LIMIT :limit",
    )
    suspend fun getSelectionPageAfter(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        anchorIndex: Int,
        anchorKey: String,
        limit: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND (:selectedOnly = 0 OR selected = 1) " +
            "AND (originalIndex < :anchorIndex " +
            "OR (originalIndex = :anchorIndex AND sourceKey < :anchorKey)) " +
            "ORDER BY originalIndex DESC, sourceKey DESC LIMIT :limit",
    )
    suspend fun getSelectionPageBefore(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        anchorIndex: Int,
        anchorKey: String,
        limit: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND (:selectedOnly = 0 OR selected = 1) " +
            "ORDER BY originalIndex DESC, sourceKey DESC LIMIT :limit",
    )
    suspend fun getSelectionLastPage(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        limit: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT sourceKey, sourceId, tvgId, tvgName, displayName, logoUrl, groupTitle, " +
            "originalIndex, contentType, selected FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND (:selectedOnly = 0 OR selected = 1) " +
            "AND originalIndex >= :targetIndex " +
            "ORDER BY originalIndex, sourceKey LIMIT :limit",
    )
    suspend fun getSelectionPageAtOrAfter(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        targetIndex: Int,
        limit: Int,
    ): List<IptvChannelListProjection>

    @Query(
        "SELECT COUNT(*) FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query)) " +
            "AND (:selectedOnly = 0 OR selected = 1)",
    )
    suspend fun selectionCount(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
    ): Int

    @Query(
        "UPDATE iptv_channels SET selected = :selected " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR groupTitle = :category) " +
            "AND (:query = '' OR sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
            "WHERE iptv_channel_search MATCH :query))",
    )
    suspend fun setFilteredChannelsSelected(
        sourceId: Long,
        category: String?,
        query: String,
        selected: Boolean,
    ): Int

    @Query(
        "SELECT DISTINCT groupTitle FROM iptv_channels " +
            "WHERE sourceId = :sourceId AND groupTitle IS NOT NULL " +
            "AND groupTitle != '' ORDER BY groupTitle COLLATE NOCASE",
    )
    suspend fun getCategoriesForSource(sourceId: Long): List<String>

    @Query("SELECT * FROM iptv_channels WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getChannel(sourceKey: String): IptvChannelEntity?

    @Query(
        "SELECT candidate.* FROM iptv_channels candidate " +
            "INNER JOIN iptv_channels active ON active.sourceKey = :sourceKey " +
            "INNER JOIN iptv_sources candidateSource ON candidateSource.id = candidate.sourceId " +
            "WHERE candidateSource.enabled = 1 AND candidate.sourceKey != active.sourceKey " +
            "AND candidate.streamUrl != active.streamUrl " +
            "AND candidate.contentType = active.contentType " +
            "AND ((active.tvgId IS NOT NULL AND TRIM(active.tvgId) != '' " +
            "AND candidate.tvgId = active.tvgId) " +
            "OR (active.tvgName IS NOT NULL AND TRIM(active.tvgName) != '' " +
            "AND candidate.tvgName = active.tvgName) " +
            "OR candidate.displayName = active.displayName COLLATE NOCASE) " +
            "ORDER BY CASE WHEN candidate.sourceId = active.sourceId THEN 0 ELSE 1 END, " +
            "candidate.originalIndex LIMIT 8",
    )
    suspend fun getAlternativeChannels(sourceKey: String): List<IptvChannelEntity>

    @Query(
        "SELECT c.* FROM iptv_channels c " +
            "INNER JOIN iptv_sources s ON s.id = c.sourceId " +
            "WHERE s.enabled = 1 AND c.selected = 1 ORDER BY s.name, c.originalIndex",
    )
    suspend fun getEnabledChannels(): List<IptvChannelEntity>

    @Query("DELETE FROM iptv_channels WHERE sourceId = :sourceId")
    suspend fun deleteChannelsForSource(sourceId: Long)

    @Query("UPDATE iptv_channels SET selected = 0 WHERE sourceId = :sourceId")
    suspend fun clearSelectedChannels(sourceId: Long)

    @Query("UPDATE iptv_channels SET selected = 1 WHERE sourceKey IN (:sourceKeys)")
    suspend fun selectChannels(sourceKeys: List<String>)

    @Query("UPDATE iptv_channels SET selected = :selected WHERE sourceKey = :sourceKey")
    suspend fun setChannelSelected(sourceKey: String, selected: Boolean)

    @Query("UPDATE iptv_channels SET selected = :selected WHERE sourceKey IN (:sourceKeys)")
    suspend fun setChannelsSelected(sourceKeys: List<String>, selected: Boolean)

    @Upsert
    suspend fun upsertChannels(channels: List<IptvChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStagedChannels(channels: List<IptvChannelStagingEntity>)

    @Query("DELETE FROM iptv_channel_staging WHERE sessionId = :sessionId")
    suspend fun deleteStagingSession(sessionId: String)

    @Query("DELETE FROM iptv_channel_staging WHERE createdAt < :cutoff")
    suspend fun deleteStaleStaging(cutoff: Long)

    @Query("SELECT COUNT(*) FROM iptv_channel_staging WHERE sessionId = :sessionId")
    suspend fun stagingCount(sessionId: String): Int

    @Query(
        "UPDATE iptv_channel_staging SET resolvedSourceKey = CASE " +
            "WHEN EXISTS (SELECT 1 FROM iptv_channels old " +
            "WHERE old.sourceId = :sourceId " +
            "AND old.sourceKey = 'iptv:' || :sourceId || ':' || " +
            "iptv_channel_staging.identityHash) " +
            "THEN 'iptv:' || :sourceId || ':' || iptv_channel_staging.identityHash " +
            "WHEN TRIM(COALESCE(iptv_channel_staging.tvgId, '')) != '' " +
            "AND EXISTS (SELECT 1 FROM iptv_channels old " +
            "WHERE old.sourceId = :sourceId " +
            "AND old.tvgId = iptv_channel_staging.tvgId) " +
            "THEN (SELECT old.sourceKey FROM iptv_channels old " +
            "WHERE old.sourceId = :sourceId " +
            "AND old.tvgId = iptv_channel_staging.tvgId " +
            "ORDER BY old.selected DESC, old.originalIndex LIMIT 1) " +
            "WHEN EXISTS (SELECT 1 FROM iptv_channels old " +
            "WHERE old.sourceId = :sourceId " +
            "AND old.matchKey = iptv_channel_staging.matchKey) " +
            "THEN (SELECT old.sourceKey FROM iptv_channels old " +
            "WHERE old.sourceId = :sourceId " +
            "AND old.matchKey = iptv_channel_staging.matchKey " +
            "ORDER BY old.selected DESC, old.originalIndex LIMIT 1) " +
            "WHEN EXISTS (SELECT 1 FROM iptv_channels old " +
            "WHERE old.sourceId = :sourceId " +
            "AND TRIM(old.displayName) = TRIM(iptv_channel_staging.displayName) COLLATE NOCASE " +
            "AND TRIM(COALESCE(old.groupTitle, '')) = " +
            "TRIM(COALESCE(iptv_channel_staging.groupTitle, '')) COLLATE NOCASE) " +
            "THEN (SELECT old.sourceKey FROM iptv_channels old " +
            "WHERE old.sourceId = :sourceId " +
            "AND TRIM(old.displayName) = TRIM(iptv_channel_staging.displayName) COLLATE NOCASE " +
            "AND TRIM(COALESCE(old.groupTitle, '')) = " +
            "TRIM(COALESCE(iptv_channel_staging.groupTitle, '')) COLLATE NOCASE " +
            "ORDER BY old.selected DESC, old.originalIndex LIMIT 1) " +
            "ELSE 'iptv:' || :sourceId || ':' || iptv_channel_staging.identityHash END " +
            "WHERE sessionId = :sessionId",
    )
    suspend fun resolveStagedSourceKeys(sessionId: String, sourceId: Long)

    @Query(
        "DELETE FROM iptv_channel_staging WHERE sessionId = :sessionId AND EXISTS " +
            "(SELECT 1 FROM iptv_channel_staging newer " +
            "WHERE newer.sessionId = :sessionId " +
            "AND newer.resolvedSourceKey = iptv_channel_staging.resolvedSourceKey " +
            "AND newer.originalIndex > iptv_channel_staging.originalIndex)",
    )
    suspend fun discardSupersededStagedDuplicates(sessionId: String)

    @Query(
        "UPDATE iptv_channels SET " +
            "tvgId = (SELECT staged.tvgId FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "tvgName = (SELECT staged.tvgName FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "displayName = (SELECT staged.displayName FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "streamUrl = (SELECT staged.streamUrl FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "logoUrl = (SELECT staged.logoUrl FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "groupTitle = (SELECT staged.groupTitle FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "userAgent = (SELECT staged.userAgent FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "referrer = (SELECT staged.referrer FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "subtitleUrl = (SELECT staged.subtitleUrl FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "originalIndex = (SELECT staged.originalIndex FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "contentType = (SELECT staged.contentType FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "catchUpMode = (SELECT staged.catchUpMode FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "catchUpSource = (SELECT staged.catchUpSource FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "catchUpDays = (SELECT staged.catchUpDays FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey), " +
            "matchKey = (SELECT staged.matchKey FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey) " +
            "WHERE sourceId = :sourceId AND EXISTS (SELECT 1 FROM iptv_channel_staging staged " +
            "WHERE staged.sessionId = :sessionId AND staged.resolvedSourceKey = sourceKey " +
            "AND (iptv_channels.tvgId IS NOT staged.tvgId " +
            "OR iptv_channels.tvgName IS NOT staged.tvgName " +
            "OR iptv_channels.displayName IS NOT staged.displayName " +
            "OR iptv_channels.streamUrl IS NOT staged.streamUrl " +
            "OR iptv_channels.logoUrl IS NOT staged.logoUrl " +
            "OR iptv_channels.groupTitle IS NOT staged.groupTitle " +
            "OR iptv_channels.userAgent IS NOT staged.userAgent " +
            "OR iptv_channels.referrer IS NOT staged.referrer " +
            "OR iptv_channels.subtitleUrl IS NOT staged.subtitleUrl " +
            "OR iptv_channels.originalIndex IS NOT staged.originalIndex " +
            "OR iptv_channels.contentType IS NOT staged.contentType " +
            "OR iptv_channels.catchUpMode IS NOT staged.catchUpMode " +
            "OR iptv_channels.catchUpSource IS NOT staged.catchUpSource " +
            "OR iptv_channels.catchUpDays IS NOT staged.catchUpDays " +
            "OR iptv_channels.matchKey IS NOT staged.matchKey))",
    )
    suspend fun updateChangedStagedChannels(sessionId: String, sourceId: Long): Int

    @Query(
        "UPDATE iptv_channels SET lastSeenAt = :lastSeenAt " +
            "WHERE sourceId = :sourceId AND sourceKey IN " +
            "(SELECT resolvedSourceKey FROM iptv_channel_staging WHERE sessionId = :sessionId)",
    )
    suspend fun touchStagedChannels(sessionId: String, sourceId: Long, lastSeenAt: Long)

    @Query(
        "INSERT INTO iptv_channels " +
            "(sourceKey, sourceId, tvgId, tvgName, displayName, streamUrl, logoUrl, " +
            "groupTitle, userAgent, referrer, subtitleUrl, originalIndex, contentType, " +
            "selected, lastSeenAt, matchKey, catchUpMode, catchUpSource, catchUpDays) " +
            "SELECT staged.resolvedSourceKey, :sourceId, staged.tvgId, staged.tvgName, " +
            "staged.displayName, staged.streamUrl, staged.logoUrl, staged.groupTitle, " +
            "staged.userAgent, staged.referrer, staged.subtitleUrl, staged.originalIndex, " +
            "staged.contentType, 0, :lastSeenAt, staged.matchKey, staged.catchUpMode, " +
            "staged.catchUpSource, staged.catchUpDays " +
            "FROM iptv_channel_staging staged WHERE staged.sessionId = :sessionId " +
            "AND staged.resolvedSourceKey IS NOT NULL AND NOT EXISTS " +
            "(SELECT 1 FROM iptv_channels old WHERE old.sourceKey = staged.resolvedSourceKey)",
    )
    suspend fun insertNewStagedChannels(sessionId: String, sourceId: Long, lastSeenAt: Long): Long

    @Query(
        "DELETE FROM iptv_channels WHERE sourceId = :sourceId AND sourceKey NOT IN " +
            "(SELECT resolvedSourceKey FROM iptv_channel_staging " +
            "WHERE sessionId = :sessionId AND resolvedSourceKey IS NOT NULL)",
    )
    suspend fun deleteChannelsMissingFromStaging(sourceId: Long, sessionId: String)
}
