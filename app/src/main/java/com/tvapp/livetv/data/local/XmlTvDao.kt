package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface XmlTvDao {
    @Query(
        "SELECT sourceId, channelId, channelName, COUNT(*) AS programCount " +
            "FROM xmltv_programs GROUP BY sourceId, channelId, channelName " +
            "ORDER BY channelName COLLATE NOCASE",
    )
    fun channelCatalog(): List<XmlTvChannelCatalogRow>

    @Query("SELECT * FROM xmltv_programs")
    fun allPrograms(): List<XmlTvProgramEntity>

    @Update
    fun updatePrograms(programs: List<XmlTvProgramEntity>)

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
            "AND (:sourceId IS NULL OR sourceId = :sourceId) " +
            "AND ((:epgId != '' AND normalizedChannelId = :epgId) " +
            "OR normalizedChannelName = :channelName OR normalizedChannelId = :channelName) " +
            "ORDER BY startTimeMillis",
    )
    fun programs(
        epgId: String,
        channelName: String,
        sourceId: Long?,
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

data class XmlTvChannelCatalogRow(
    val sourceId: Long,
    val channelId: String,
    val channelName: String,
    val programCount: Int,
)
