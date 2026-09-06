package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface XmlTvDao {
    @Query(
        "SELECT sourceId, channelId, channelName, COUNT(*) AS programCount " +
            "FROM xmltv_programs WHERE sourceId IN " +
            "(SELECT id FROM xmltv_sources WHERE enabled = 1) " +
            "GROUP BY sourceId, channelId, channelName " +
            "ORDER BY channelName COLLATE NOCASE",
    )
    fun channelCatalog(): List<XmlTvChannelCatalogRow>

    @Query("SELECT * FROM xmltv_programs")
    fun allPrograms(): List<XmlTvProgramEntity>

    @Update
    fun updatePrograms(programs: List<XmlTvProgramEntity>)

    @Query("SELECT * FROM xmltv_sources ORDER BY name COLLATE NOCASE")
    fun sources(): List<XmlTvSourceEntity>

    @Query("SELECT COUNT(*) FROM xmltv_programs WHERE sourceId = :sourceId")
    fun sourceProgramCount(sourceId: Long): Int

    @Query(
        "SELECT COUNT(DISTINCT channelId) FROM xmltv_programs WHERE sourceId = :sourceId",
    )
    fun sourceChannelCount(sourceId: Long): Int

    @Query("SELECT * FROM xmltv_sources WHERE location = :location LIMIT 1")
    fun sourceByLocation(location: String): XmlTvSourceEntity?

    @Query("SELECT * FROM xmltv_sources WHERE id = :sourceId LIMIT 1")
    fun sourceById(sourceId: Long): XmlTvSourceEntity?

    @Insert
    fun insertSource(source: XmlTvSourceEntity): Long

    @Query(
        "UPDATE xmltv_sources SET name = :name, location = :location, kind = :kind, " +
            "lastUpdatedAt = :updatedAt, lastError = NULL WHERE id = :sourceId",
    )
    fun updateSource(
        sourceId: Long,
        name: String,
        location: String,
        kind: String,
        updatedAt: Long,
    )

    @Query("UPDATE xmltv_sources SET name = :name WHERE id = :sourceId")
    fun renameSource(sourceId: Long, name: String)

    @Query("UPDATE xmltv_sources SET enabled = :enabled WHERE id = :sourceId")
    fun setSourceEnabled(sourceId: Long, enabled: Boolean)

    @Query("UPDATE xmltv_sources SET lastError = :error WHERE id = :sourceId")
    fun setSourceError(sourceId: Long, error: String?)

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
        "SELECT * FROM xmltv_programs WHERE sourceId IN " +
            "(SELECT id FROM xmltv_sources WHERE enabled = 1) " +
            "AND endTimeMillis > :start AND startTimeMillis < :end " +
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
        "SELECT * FROM xmltv_programs WHERE sourceId IN " +
            "(SELECT id FROM xmltv_sources WHERE enabled = 1) " +
            "AND startTimeMillis <= :now AND endTimeMillis > :now " +
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
