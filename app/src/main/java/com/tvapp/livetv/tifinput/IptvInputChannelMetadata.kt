package com.tvapp.livetv.tifinput

import org.json.JSONObject

data class IptvInputChannelMetadata(
    val sourceKey: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val epgId: String?,
    val userAgent: String?,
    val referrer: String?,
) {
    fun encode(): ByteArray = JSONObject().apply {
        put(KEY_OWNER, OWNER)
        put(KEY_SOURCE, sourceKey)
        put(KEY_URL, streamUrl)
        putNullable(KEY_LOGO, logoUrl)
        putNullable(KEY_GROUP, groupTitle)
        putNullable(KEY_EPG, epgId)
        putNullable(KEY_AGENT, userAgent)
        putNullable(KEY_REFERRER, referrer)
    }.toString().toByteArray(Charsets.UTF_8)

    companion object {
        const val OWNER = "com.tvapp.livetv"
        const val PROVIDER_ID_PREFIX = "tvapp-iptv:"
        private const val KEY_OWNER = "owner"
        private const val KEY_SOURCE = "sourceKey"
        private const val KEY_URL = "streamUrl"
        private const val KEY_LOGO = "logoUrl"
        private const val KEY_GROUP = "groupTitle"
        private const val KEY_EPG = "epgId"
        private const val KEY_AGENT = "userAgent"
        private const val KEY_REFERRER = "referrer"

        fun from(channel: SharedIptvInputChannel) = IptvInputChannelMetadata(
            channel.sourceKey,
            channel.streamUrl,
            channel.logoUrl,
            channel.groupTitle,
            channel.epgId,
            channel.userAgent,
            channel.referrer,
        )

        fun decode(data: ByteArray?): IptvInputChannelMetadata? = runCatching {
            val json = JSONObject(data?.toString(Charsets.UTF_8) ?: return null)
            if (json.optString(KEY_OWNER) != OWNER) return null
            IptvInputChannelMetadata(
                sourceKey = json.getString(KEY_SOURCE),
                streamUrl = json.getString(KEY_URL),
                logoUrl = json.optNullable(KEY_LOGO),
                groupTitle = json.optNullable(KEY_GROUP),
                epgId = json.optNullable(KEY_EPG),
                userAgent = json.optNullable(KEY_AGENT),
                referrer = json.optNullable(KEY_REFERRER),
            )
        }.getOrNull()

        private fun JSONObject.optNullable(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
    }
}

private fun JSONObject.putNullable(key: String, value: String?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}
