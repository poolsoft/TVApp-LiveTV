package com.tvapp.livetv.data

import com.tvapp.livetv.data.local.IptvChannelEntity

internal object CatchUpUrlResolver {
    fun resolve(
        template: String,
        channel: IptvChannelEntity,
        startTimeMillis: Long,
        endTimeMillis: Long,
    ): String {
        val startSeconds = startTimeMillis / 1_000L
        val durationSeconds = ((endTimeMillis - startTimeMillis) / 1_000L).coerceAtLeast(1L)
        val resolved = template
            .replace("${'$'}{start}", startSeconds.toString())
            .replace("{start}", startSeconds.toString())
            .replace("{utc}", startSeconds.toString())
            .replace("{timestamp}", startSeconds.toString())
            .replace("{duration}", durationSeconds.toString())
            .replace("${'$'}{duration}", durationSeconds.toString())
            .replace("{duration:60}", (durationSeconds / 60L).coerceAtLeast(1L).toString())
            .replace("{offset}", startSeconds.toString())
            .replace("{channel_id}", channel.tvgId.orEmpty())
        return when {
            resolved.startsWith("?") -> channel.streamUrl.substringBefore('?') + resolved
            resolved.startsWith("&") -> channel.streamUrl + resolved
            else -> resolved
        }
    }
}
