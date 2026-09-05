package com.tvapp.livetv.data

import com.tvapp.livetv.model.LiveChannel
import java.util.Locale

object XmlTvMatcher {
    fun automaticMatch(channel: LiveChannel, options: List<XmlTvChannelOption>): XmlTvChannelOption? =
        automaticResolution(channel, options)?.option

    fun automaticResolution(channel: LiveChannel, options: List<XmlTvChannelOption>): Resolution? {
        val epgId = channel.epgId?.normalizeEpgKey()?.takeIf(String::isNotBlank)
        val name = channel.displayName.normalizeEpgKey()
        return epgId?.let { id -> options.firstOrNull { it.channelId.normalizeEpgKey() == id } }
            ?.let { Resolution(it, MatchType.ID) }
            ?: options.firstOrNull { it.channelName.normalizeEpgKey() == name }
                ?.let { Resolution(it, MatchType.NAME) }
            ?: options.firstOrNull { it.channelId.normalizeEpgKey() == name }
                ?.let { Resolution(it, MatchType.NAME) }
    }

    data class Resolution(val option: XmlTvChannelOption, val type: MatchType)
    enum class MatchType { ID, NAME }
}

fun String.normalizeEpgKey(): String = lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9çğıöşü]+"), "")
    .removeSuffix("hd").removeSuffix("sd").removeSuffix("4k")
