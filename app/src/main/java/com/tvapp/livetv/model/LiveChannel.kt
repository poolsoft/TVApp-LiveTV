package com.tvapp.livetv.model

data class LiveChannel(
    val id: Long,
    val sourceKey: String,
    val inputId: String,
    val displayNumber: String,
    val displayName: String,
    val uri: String,
    val videoFormat: String? = null,
    val serviceType: String? = null,
    val locked: Boolean = false,
    val encrypted: Boolean = false,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val epgId: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val iptvContentType: String? = null,
    val source: Source = Source.TIF,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val groupId: Long? = null,
) {
    enum class Source { TIF, IPTV }
}
