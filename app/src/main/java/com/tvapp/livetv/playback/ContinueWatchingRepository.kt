package com.tvapp.livetv.playback

import com.tvapp.livetv.model.LiveChannel

/**
 * Merges playback history order with IPTV VOD resume state into a bounded
 * "Continue Watching" list. Pure logic: stores are passed in as functions so
 * the merge is unit-testable without Android dependencies.
 */
class ContinueWatchingRepository(
    private val historyKeys: () -> List<String>,
    private val resumeEntry: (String) -> IptvResumeEntry?,
    private val resolveChannel: suspend (String) -> LiveChannel?,
) {
    suspend fun items(limit: Int = MAX_ITEMS): List<ContinueWatchingItem> {
        val seen = mutableSetOf<String>()
        val items = buildList {
            for (key in historyKeys()) {
                if (key in seen) continue
                seen.add(key)
                val channel = resolveChannel(key) ?: continue
                if (channel.source != LiveChannel.Source.IPTV) continue
                if (!channel.iptvContentType.equals(CONTENT_TYPE_VOD, ignoreCase = true)) continue
                add(
                    ContinueWatchingItem(
                        channel = channel,
                        resumeEntry = resumeEntry(key),
                    ),
                )
                if (size >= limit) break
            }
        }
        return items
    }

    companion object {
        const val MAX_ITEMS = 8
        const val CONTENT_TYPE_VOD = "VOD"
    }
}

data class ContinueWatchingItem(
    val channel: LiveChannel,
    val resumeEntry: IptvResumeEntry?,
) {
    val hasResume: Boolean get() = resumeEntry != null
}
