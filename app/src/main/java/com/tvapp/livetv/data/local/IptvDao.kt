package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

@Dao
interface IptvDao {
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

    @Upsert
    suspend fun upsertChannels(channels: List<IptvChannelEntity>)
}
