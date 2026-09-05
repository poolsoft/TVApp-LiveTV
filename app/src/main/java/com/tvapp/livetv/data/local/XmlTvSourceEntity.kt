package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "xmltv_sources",
    indices = [Index(value = ["location"], unique = true)],
)
data class XmlTvSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String,
    val kind: String,
    val lastUpdatedAt: Long,
)
