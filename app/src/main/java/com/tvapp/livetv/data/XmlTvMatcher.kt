package com.tvapp.livetv.data

import com.tvapp.livetv.model.LiveChannel
import java.util.Locale

object XmlTvMatcher {
    fun automaticMatch(channel: LiveChannel, options: List<XmlTvChannelOption>): XmlTvChannelOption? =
        automaticResolution(channel, options)?.option

    fun automaticResolution(channel: LiveChannel, options: List<XmlTvChannelOption>): Resolution? =
        Index(options).resolve(channel)

    data class Resolution(val option: XmlTvChannelOption, val type: MatchType)
    enum class MatchType { EXACT_ID, EXACT_NAME, NORMALIZED_NAME }

    class Index(options: List<XmlTvChannelOption>) {
        private val exactById = options.groupBy { it.channelId.literalEpgKey() }
        private val exactByName = options.groupBy { it.channelName.literalEpgKey() }
        private val normalizedById = options.groupBy { it.channelId.normalizeExactEpgKey() }
        private val normalizedByName = options.groupBy { it.channelName.normalizeExactEpgKey() }
        private val relaxedByName = options.groupBy { it.channelName.normalizeRelaxedEpgKey() }
        private val relaxedById = options.groupBy { it.channelId.normalizeRelaxedEpgKey() }

        fun resolve(channel: LiveChannel): Resolution? {
            val epgId = channel.epgId?.takeIf(String::isNotBlank)
            val name = channel.displayName
            return epgId?.let { unique(exactById[it.literalEpgKey()]) }
                ?.let { Resolution(it, MatchType.EXACT_ID) }
                ?: unique(exactByName[name.literalEpgKey()])
                    ?.let { Resolution(it, MatchType.EXACT_NAME) }
                ?: epgId?.let { unique(normalizedById[it.normalizeExactEpgKey()]) }
                    ?.let { Resolution(it, MatchType.NORMALIZED_NAME) }
                ?: unique(normalizedByName[name.normalizeExactEpgKey()])
                    ?.let { Resolution(it, MatchType.NORMALIZED_NAME) }
                ?: unique(normalizedById[name.normalizeExactEpgKey()])
                    ?.let { Resolution(it, MatchType.NORMALIZED_NAME) }
                ?: relaxedUnique(name)?.let { Resolution(it, MatchType.NORMALIZED_NAME) }
        }

        private fun relaxedUnique(name: String): XmlTvChannelOption? {
            val key = name.normalizeRelaxedEpgKey()
            return (relaxedByName[key].orEmpty() + relaxedById[key].orEmpty())
                .distinctBy { it.sourceId to it.channelId }
                .singleOrNull()
        }

        private fun unique(candidates: List<XmlTvChannelOption>?): XmlTvChannelOption? = candidates
            .orEmpty()
            .distinctBy { it.sourceId to it.channelId }
            .singleOrNull()
    }
}

internal fun String.literalEpgKey(): String = lowercase(Locale.ROOT).trim()

fun String.normalizeEpgKey(): String = lowercase(Locale.ROOT)
    .trim()
    .replace(Regex("[._-][a-z]{2}$"), "")
    .replace(Regex("(?:[\\s._-]+)?(?:uhd|fhd|hd|sd|4k)$"), "")
    .replace(Regex("[^a-z0-9çğıöşü]+"), "")

internal fun String.normalizeExactEpgKey(): String = lowercase(Locale.ROOT)
    .trim()
    .replace(Regex("[._-][a-z]{2}$"), "")
    .replace(Regex("[^a-z0-9çğıöşü]+"), "")

internal fun String.normalizeRelaxedEpgKey(): String = normalizeEpgKey()
