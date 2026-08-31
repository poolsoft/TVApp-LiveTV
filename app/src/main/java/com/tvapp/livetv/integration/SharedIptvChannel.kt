package com.tvapp.livetv.integration

data class SharedIptvChannel(
    val sourceKey: String,
    val displayName: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val epgId: String?,
    val userAgent: String?,
    val referrer: String?,
)
