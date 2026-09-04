package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "xtream_epg_programs",
    indices = [
        Index(value = ["normalizedChannelId", "startTimeMillis", "endTimeMillis"]),
        Index(value = ["normalizedChannelName", "startTimeMillis", "endTimeMillis"]),
    ],
)
data class XtreamEpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val channelName: String,
    val normalizedChannelId: String,
    val normalizedChannelName: String,
    val title: String,
    val description: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
)
