package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface XmlTvDao {
    @Query("SELECT COUNT(*) FROM xmltv_programs")
    fun programCount(): Int

    @Insert
    fun insertPrograms(programs: List<XmlTvProgramEntity>)

    @Query("DELETE FROM xmltv_programs")
    fun clearPrograms()

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
}
