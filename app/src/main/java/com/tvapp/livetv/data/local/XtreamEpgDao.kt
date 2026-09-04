package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface XtreamEpgDao {
    @Query("SELECT COUNT(*) FROM xtream_epg_programs")
    fun programCount(): Int

    @Insert
    fun insertPrograms(programs: List<XtreamEpgProgramEntity>)

    @Query("DELETE FROM xtream_epg_programs")
    fun clearPrograms()

    @Query(
        "SELECT * FROM xtream_epg_programs WHERE endTimeMillis > :start AND startTimeMillis < :end " +
            "AND ((:epgId != '' AND normalizedChannelId = :epgId) " +
            "OR normalizedChannelName = :channelName OR normalizedChannelId = :channelName) " +
            "ORDER BY startTimeMillis",
    )
    fun programs(
        epgId: String,
        channelName: String,
        start: Long,
        end: Long,
    ): List<XtreamEpgProgramEntity>

    @Query(
        "SELECT * FROM xtream_epg_programs WHERE startTimeMillis <= :now AND endTimeMillis > :now " +
            "AND (normalizedChannelId IN (:channelKeys) OR normalizedChannelName IN (:channelKeys)) " +
            "ORDER BY startTimeMillis",
    )
    fun currentPrograms(
        channelKeys: List<String>,
        now: Long,
    ): List<XtreamEpgProgramEntity>
}
