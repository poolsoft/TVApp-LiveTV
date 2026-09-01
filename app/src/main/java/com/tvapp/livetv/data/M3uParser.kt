package com.tvapp.livetv.data

import java.io.Reader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParsedIptvChannel(
    val name: String,
    val streamUrl: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val contentType: String = "LIVE",
)

object M3uParser {
    private val attributePattern = Regex("([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"")

    fun parse(reader: Reader): List<ParsedIptvChannel> = sequence(reader).toList()

    fun sequence(reader: Reader): Sequence<ParsedIptvChannel> = sequence {
        var parsedCount = 0
        var pending: PendingChannel? = null
        reader.buffered().use { buffered ->
            while (true) {
                val rawLine = buffered.readLine() ?: break
                val line = rawLine.trim().removePrefix("\uFEFF")
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        pending = parseExtInf(line)
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        pending = pending?.copy(groupTitle = line.substringAfter(':').trim())
                    }
                    line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) -> {
                        pending = pending?.copy(userAgent = line.substringAfter('=').trim())
                    }
                    line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) -> {
                        pending = pending?.copy(referrer = line.substringAfter('=').trim())
                    }
                    line.isNotBlank() && !line.startsWith('#') -> {
                        val stream = parseStreamLocation(line)
                        val metadata = pending ?: PendingChannel(name = "Kanal ${parsedCount + 1}")
                        val channel = ParsedIptvChannel(
                            name = metadata.name.ifBlank {
                                metadata.tvgName.orEmpty().ifBlank { "Kanal ${parsedCount + 1}" }
                            },
                            streamUrl = stream.url,
                            tvgId = metadata.tvgId.nullIfBlank(),
                            tvgName = metadata.tvgName.nullIfBlank(),
                            logoUrl = metadata.logoUrl.nullIfBlank(),
                            groupTitle = metadata.groupTitle.nullIfBlank(),
                            userAgent = stream.userAgent ?: metadata.userAgent.nullIfBlank(),
                            referrer = stream.referrer ?: metadata.referrer.nullIfBlank(),
                            contentType = contentType(metadata.durationSeconds, stream.url, metadata.groupTitle),
                        )
                        if (channel.streamUrl.startsWith("http", ignoreCase = true)) {
                            yield(channel)
                        }
                        parsedCount++
                        pending = null
                    }
                }
            }
        }
    }

    private fun parseExtInf(line: String): PendingChannel {
        val attributes = attributePattern.findAll(line).associate { match ->
            match.groupValues[1].lowercase() to match.groupValues[2].trim()
        }
        val separator = commaOutsideQuotes(line)
        val displayName = separator?.let { line.substring(it + 1).trim() }.orEmpty()
        val duration = line.substringAfter(':', "")
            .substringBefore(' ')
            .substringBefore(',')
            .toLongOrNull()
        return PendingChannel(
            name = displayName.ifBlank { attributes["tvg-name"].orEmpty() },
            tvgId = attributes["tvg-id"],
            tvgName = attributes["tvg-name"],
            logoUrl = attributes["tvg-logo"],
            groupTitle = attributes["group-title"],
            durationSeconds = duration,
        )
    }

    private fun contentType(durationSeconds: Long?, url: String, group: String?): String {
        if (durationSeconds != null && durationSeconds > 0) return "VOD"
        val value = "${url.lowercase()} ${group.orEmpty().lowercase()}"
        return if (VOD_MARKERS.any(value::contains)) "VOD" else "LIVE"
    }

    private fun commaOutsideQuotes(value: String): Int? {
        var quoted = false
        value.forEachIndexed { index, character ->
            when (character) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return index
            }
        }
        return null
    }

    private fun parseStreamLocation(value: String): StreamLocation {
        val url = value.substringBefore('|').trim()
        val options = value.substringAfter('|', "")
            .split('&')
            .mapNotNull { item ->
                val key = item.substringBefore('=', "").trim().lowercase()
                if (key.isBlank()) return@mapNotNull null
                val decoded = URLDecoder.decode(
                    item.substringAfter('=', ""),
                    StandardCharsets.UTF_8.name(),
                )
                key to decoded
            }.toMap()
        return StreamLocation(
            url = url,
            userAgent = options["user-agent"] ?: options["useragent"],
            referrer = options["referer"] ?: options["referrer"],
        )
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf(String::isNotBlank)

    private data class PendingChannel(
        val name: String,
        val tvgId: String? = null,
        val tvgName: String? = null,
        val logoUrl: String? = null,
        val groupTitle: String? = null,
        val userAgent: String? = null,
        val referrer: String? = null,
        val durationSeconds: Long? = null,
    )

    private data class StreamLocation(
        val url: String,
        val userAgent: String?,
        val referrer: String?,
    )

    private val VOD_MARKERS = listOf(
        ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".m4v", ".webm",
        "/movie/", "/movies/", "/series/", "/vod/", " vod", "film",
        "movie", "cinema", "sinema", "dizi", "series", "sezon",
        "season", "episode", "bölüm",
    )
}
