package com.tvapp.livetv.data.local

/** sourceKey → revision pair for batch remote paging lookups. */
data class SourceKeyRevision(
    val sourceKey: String,
    val revision: Long,
)

/** Lightweight row used by paged IPTV lists. Playback credentials are loaded on demand. */
data class IptvChannelListProjection(
    val sourceKey: String,
    val sourceId: Long,
    val tvgId: String?,
    val tvgName: String?,
    val displayName: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val originalIndex: Int,
    val contentType: String,
    val selected: Boolean,
)
