package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "iptv_channel_staging",
    primaryKeys = ["sessionId", "originalIndex"],
    indices = [
        Index("sessionId"),
        Index("createdAt"),
        Index(value = ["sessionId", "resolvedSourceKey"]),
    ],
)
data class IptvChannelStagingEntity(
    val sessionId: String,
    val originalIndex: Int,
    val identityHash: String,
    val resolvedSourceKey: String? = null,
    val tvgId: String?,
    val tvgName: String?,
    val displayName: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val userAgent: String?,
    val referrer: String?,
    val subtitleUrl: String?,
    val contentType: String,
    val matchKey: String,
    val catchUpMode: String?,
    val catchUpSource: String?,
    val catchUpDays: Int,
    val createdAt: Long,
)
