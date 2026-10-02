package com.tvapp.livetv.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Provider-reported metadata only. A series has no playable stream URL. */
@Entity(tableName = "vod_metadata", foreignKeys = [ForeignKey(
    entity = IptvSourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index(value = ["sourceId", "kind", "name"]),
    Index(value = ["parentKey", "seasonNumber", "episodeNumber"])])
data class VodMetadataEntity(
    @PrimaryKey val sourceKey: String,
    val sourceId: Long,
    val kind: String,
    val providerId: String,
    val parentKey: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val name: String,
    val logoUrl: String? = null,
    val category: String? = null,
    val description: String? = null,
    val durationMillis: Long? = null,
    val streamUrl: String? = null,
    val updatedAt: Long,
)

data class VodCatalogItem(
    val sourceKey: String,
    val sourceId: Long,
    val name: String,
    val logoUrl: String?,
    val category: String?,
    val kind: String,
    val providerId: String?,
    val parentKey: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val description: String?,
    val durationMillis: Long?,
    val ordinal: Int,
    val favorite: Boolean,
)
