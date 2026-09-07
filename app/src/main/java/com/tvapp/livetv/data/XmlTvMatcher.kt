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
            ?: uniqueRelaxedMatch(channel.displayName, options)?.let { Resolution(it, MatchType.NAME) }
    }

    data class Resolution(val option: XmlTvChannelOption, val type: MatchType)
    enum class MatchType { ID, NAME }

    class Index(options: List<XmlTvChannelOption>) {
        private val byId = options.associateBy { it.channelId.normalizeEpgKey() }
        private val byName = options.associateBy { it.channelName.normalizeEpgKey() }
        private val relaxedByName = options.groupBy { it.channelName.normalizeRelaxedEpgKey() }
        private val relaxedById = options.groupBy { it.channelId.normalizeRelaxedEpgKey() }

        fun resolve(channel: LiveChannel): Resolution? {
            val epgId = channel.epgId?.normalizeEpgKey()?.takeIf(String::isNotBlank)
            val name = channel.displayName.normalizeEpgKey()
            return epgId?.let(byId::get)?.let { Resolution(it, MatchType.ID) }
                ?: byName[name]?.let { Resolution(it, MatchType.NAME) }
                ?: byId[name]?.let { Resolution(it, MatchType.NAME) }
                ?: relaxedUnique(channel.displayName)?.let { Resolution(it, MatchType.NAME) }
        }

        private fun relaxedUnique(name: String): XmlTvChannelOption? {
            val key = name.normalizeRelaxedEpgKey()
            return (relaxedByName[key].orEmpty() + relaxedById[key].orEmpty())
                .distinctBy { it.sourceId to it.channelId }
                .singleOrNull()
        }
    }

    private fun uniqueRelaxedMatch(
        name: String,
        options: List<XmlTvChannelOption>,
    ): XmlTvChannelOption? {
        val key = name.normalizeRelaxedEpgKey()
        return options.filter {
            it.channelName.normalizeRelaxedEpgKey() == key ||
                it.channelId.normalizeRelaxedEpgKey() == key
        }.distinctBy { it.sourceId to it.channelId }.singleOrNull()
    }
}

fun String.normalizeEpgKey(): String = lowercase(Locale.ROOT)
    .trim()
    .replace(Regex("[._-][a-z]{2}$"), "")
    .replace(Regex("[^a-z0-9çğıöşü]+"), "")

internal fun String.normalizeRelaxedEpgKey(): String = normalizeEpgKey()
    .replace(Regex("(?:uhd|fhd|hd|sd|4k)$"), "")
