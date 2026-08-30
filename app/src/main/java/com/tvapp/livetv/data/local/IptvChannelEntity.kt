package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "iptv_channels",
    foreignKeys = [
        ForeignKey(
            entity = IptvSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId"), Index("originalIndex"), Index("selected")],
)
data class IptvChannelEntity(
    @PrimaryKey val sourceKey: String,
    val sourceId: Long,
    val tvgId: String?,
    val tvgName: String?,
    val displayName: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val userAgent: String?,
    val referrer: String?,
    val originalIndex: Int,
    val selected: Boolean = false,
    val lastSeenAt: Long,
)
