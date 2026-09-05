package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface XmlTvDao {
    @Query("SELECT * FROM xmltv_sources ORDER BY name COLLATE NOCASE")
    fun sources(): List<XmlTvSourceEntity>

    @Query("SELECT * FROM xmltv_sources WHERE location = :location LIMIT 1")
    fun sourceByLocation(location: String): XmlTvSourceEntity?

    @Insert
    fun insertSource(source: XmlTvSourceEntity): Long

    @Query("UPDATE xmltv_sources SET name = :name, lastUpdatedAt = :updatedAt WHERE id = :sourceId")
    fun updateSource(sourceId: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM xmltv_sources WHERE id = :sourceId")
    fun deleteSource(sourceId: Long)

    @Query("SELECT COUNT(*) FROM xmltv_programs")
    fun programCount(): Int

    @Insert
    fun insertPrograms(programs: List<XmlTvProgramEntity>)

    @Query("DELETE FROM xmltv_programs")
    fun clearPrograms()

    @Query("DELETE FROM xmltv_programs WHERE sourceId = :sourceId")
    fun clearPrograms(sourceId: Long)

    @Query(
        "SELECT * FROM xmltv_programs WHERE endTimeMillis > :start AND startTimeMillis < :end " +
            "AND ((:epgId != '' AND normalizedChannelId = :epgId) " +
            "OR normalizedChannelName = :channelName OR normalizedChannelId = :channelName) " +
            "ORDER BY startTimeMillis",
    )
    fun programs(
        epgId: String,
        channelName: String,
        start: Long,
        end: Long,
    ): List<XmlTvProgramEntity>

    @Query(
        "SELECT * FROM xmltv_programs WHERE startTimeMillis <= :now AND endTimeMillis > :now " +
            "AND (normalizedChannelId IN (:channelKeys) OR normalizedChannelName IN (:channelKeys)) " +
            "ORDER BY startTimeMillis",
    )
    fun currentPrograms(
        channelKeys: List<String>,
        now: Long,
    ): List<XmlTvProgramEntity>
}
