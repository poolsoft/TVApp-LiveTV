package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "iptv_sources",
    indices = [Index(value = ["location"], unique = true)],
)
data class IptvSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String,
    val kind: String,
    val enabled: Boolean = true,
    val lastUpdatedAt: Long = 0,
)
