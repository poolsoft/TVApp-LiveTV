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
            "ORDER BY COALESCE(u.sortOrder, 2147483647), s.name, c.originalIndex",
    )
    fun getSharedChannels(): List<SharedIptvInputChannel>

    @Query("SELECT * FROM iptv_sources ORDER BY name")
    suspend fun getSources(): List<IptvSourceEntity>

    @Query("SELECT * FROM iptv_sources WHERE location = :location LIMIT 1")
    suspend fun getSourceByLocation(location: String): IptvSourceEntity?

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

    @Query("SELECT * FROM iptv_channels WHERE sourceId = :sourceId ORDER BY originalIndex")
    suspend fun getChannelsForSource(sourceId: Long): List<IptvChannelEntity>

    @Query("SELECT * FROM iptv_channels WHERE sourceId = :sourceId AND selected = 1")
    suspend fun getSelectedChannelsForSource(sourceId: Long): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "ORDER BY originalIndex LIMIT :limit OFFSET :offset",
    )
    suspend fun getChannelsPage(
        sourceId: Long,
        category: String?,
        limit: Int,
        offset: Int,
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "ORDER BY originalIndex LIMIT :limit OFFSET :offset",
    )
    suspend fun getLibraryPage(
        sourceId: Long,
        category: String?,
        contentType: String,
        limit: Int,
        offset: Int,
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND (originalIndex > :anchorIndex " +
            "OR (originalIndex = :anchorIndex AND sourceKey > :anchorKey)) " +
            "ORDER BY originalIndex, sourceKey LIMIT :limit",
    )
    suspend fun getLibraryPageAfter(
        sourceId: Long,
        category: String?,
        contentType: String,
        anchorIndex: Int,
        anchorKey: String,
        limit: Int,
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND (originalIndex < :anchorIndex " +
            "OR (originalIndex = :anchorIndex AND sourceKey < :anchorKey)) " +
            "ORDER BY originalIndex DESC, sourceKey DESC LIMIT :limit",
    )
    suspend fun getLibraryPageBefore(
        sourceId: Long,
        category: String?,
        contentType: String,
        anchorIndex: Int,
        anchorKey: String,
        limit: Int,
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "ORDER BY originalIndex DESC, sourceKey DESC LIMIT :limit",
    )
    suspend fun getLibraryLastPage(
        sourceId: Long,
        category: String?,
        contentType: String,
        limit: Int,
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType) " +
            "AND originalIndex >= :targetIndex " +
            "ORDER BY originalIndex, sourceKey LIMIT :limit",
    )
    suspend fun getLibraryPageAtOrAfter(
        sourceId: Long,
        category: String?,
        contentType: String,
        targetIndex: Int,
        limit: Int,
    ): List<IptvChannelEntity>

    @Query(
        "SELECT COUNT(*) FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:contentType = 'ALL' OR contentType = :contentType)",
    )
    suspend fun libraryCount(sourceId: Long, category: String?, contentType: String): Int

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:query = '' OR displayName LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR COALESCE(groupTitle, '') LIKE '%' || :query || '%' COLLATE NOCASE) " +
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
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:query = '' OR displayName LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR COALESCE(groupTitle, '') LIKE '%' || :query || '%' COLLATE NOCASE) " +
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
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:query = '' OR displayName LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR COALESCE(groupTitle, '') LIKE '%' || :query || '%' COLLATE NOCASE) " +
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
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:query = '' OR displayName LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR COALESCE(groupTitle, '') LIKE '%' || :query || '%' COLLATE NOCASE) " +
            "AND (:selectedOnly = 0 OR selected = 1) " +
            "ORDER BY originalIndex DESC, sourceKey DESC LIMIT :limit",
    )
    suspend fun getSelectionLastPage(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        limit: Int,
    ): List<IptvChannelEntity>

    @Query(
        "SELECT * FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:query = '' OR displayName LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR COALESCE(groupTitle, '') LIKE '%' || :query || '%' COLLATE NOCASE) " +
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
    ): List<IptvChannelEntity>

    @Query(
        "SELECT COUNT(*) FROM iptv_channels " +
            "WHERE sourceId = :sourceId " +
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:query = '' OR displayName LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR COALESCE(groupTitle, '') LIKE '%' || :query || '%' COLLATE NOCASE) " +
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
            "AND (:category IS NULL OR TRIM(groupTitle) = :category) " +
            "AND (:query = '' OR displayName LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR COALESCE(groupTitle, '') LIKE '%' || :query || '%' COLLATE NOCASE)",
    )
    suspend fun setFilteredChannelsSelected(
        sourceId: Long,
        category: String?,
        query: String,
        selected: Boolean,
    ): Int

    @Query(
        "SELECT DISTINCT TRIM(groupTitle) FROM iptv_channels " +
            "WHERE sourceId = :sourceId AND groupTitle IS NOT NULL " +
            "AND TRIM(groupTitle) != '' ORDER BY TRIM(groupTitle) COLLATE NOCASE",
    )
    suspend fun getCategoriesForSource(sourceId: Long): List<String>

    @Query(
        "SELECT c.* FROM iptv_channels c " +
            "INNER JOIN iptv_sources s ON s.id = c.sourceId " +
            "WHERE s.enabled = 1 ORDER BY s.name, c.originalIndex",
    )
    suspend fun getAllEnabledLibraryChannels(): List<IptvChannelEntity>

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
}
