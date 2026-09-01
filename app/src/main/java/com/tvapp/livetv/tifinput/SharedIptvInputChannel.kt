package com.tvapp.livetv.tifinput

data class SharedIptvInputChannel(
    val sourceKey: String,
    val displayName: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val epgId: String?,
    val userAgent: String?,
    val referrer: String?,
)
