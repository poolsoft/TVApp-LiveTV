package com.tvapp.livetv.data.local

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
