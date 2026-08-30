package com.tvapp.livetv.playback

import com.tvapp.livetv.model.LiveChannel

object ChannelNavigator {
    fun adjacent(
        channels: List<LiveChannel>,
        currentSourceKey: String?,
        offset: Int,
    ): LiveChannel? {
        if (channels.isEmpty()) return null
        val currentIndex = channels.indexOfFirst { it.sourceKey == currentSourceKey }
        if (currentIndex < 0) return if (offset >= 0) channels.first() else channels.last()
        return channels[(currentIndex + offset).mod(channels.size)]
    }

    fun byNumber(channels: List<LiveChannel>, number: String): LiveChannel? =
        channels.firstOrNull { it.displayNumber == number }

    fun previousDistinct(
        channels: List<LiveChannel>,
        historyKeys: List<String>,
        currentSourceKey: String?,
    ): LiveChannel? {
        val previousKey = historyKeys.firstOrNull { it != currentSourceKey } ?: return null
        return channels.firstOrNull { it.sourceKey == previousKey }
    }
}
