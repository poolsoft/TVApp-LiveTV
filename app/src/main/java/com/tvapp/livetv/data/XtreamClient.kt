package com.tvapp.livetv.data

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone

data class XtreamEpgListing(
    val title: String,
    val description: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
)

internal class XtreamClient(
    serverUrl: String,
    private val username: String,
    private val password: String,
) {
    val baseUrl = normalizeBaseUrl(serverUrl)

    fun verifyAccount() {
        val response = requestText(null)
        val user = JSONObject(response).optJSONObject("user_info")
            ?: error("Xtream sunucusu hesap bilgisi döndürmedi.")
        require(user.optInt("auth", 0) == 1) { "Xtream kullanıcı adı veya parola hatalı." }
        val status = user.optString("status")
        require(status.isBlank() || status.equals("Active", ignoreCase = true)) {
            "Xtream hesabı etkin değil: $status"
        }
    }

    fun channels(): List<ParsedIptvChannel> = buildList {
        val liveCategories = categories("get_live_categories")
        readStreams("get_live_streams") { item ->
            val id = item.id ?: return@readStreams
            add(
                ParsedIptvChannel(
                    name = item.name.ifBlank { "Kanal $id" },
                    streamUrl = "$baseUrl/live/${encodePath(username)}/${encodePath(password)}/$id.ts",
                    tvgId = item.epgId,
                    tvgName = item.name,
                    logoUrl = item.icon,
                    groupTitle = liveCategories[item.categoryId] ?: item.categoryId,
                    contentType = "LIVE",
                ),
            )
        }

        val vodCategories = categories("get_vod_categories")
        readStreams("get_vod_streams") { item ->
            val id = item.id ?: return@readStreams
            val extension = item.extension?.trim()?.trimStart('.')?.takeIf(String::isNotBlank) ?: "mp4"
            add(
                ParsedIptvChannel(
                    name = item.name.ifBlank { "Film $id" },
                    streamUrl = "$baseUrl/movie/${encodePath(username)}/${encodePath(password)}/$id.$extension",
                    tvgName = item.name,
                    logoUrl = item.icon,
                    groupTitle = vodCategories[item.categoryId] ?: item.categoryId,
                    contentType = "VOD",
                ),
            )
        }
    }

    private fun categories(action: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        open(action).useConnection { connection ->
            JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var id: String? = null
                    var name: String? = null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "category_id" -> id = reader.scalarString()
                            "category_name" -> name = reader.scalarString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (!id.isNullOrBlank() && !name.isNullOrBlank()) result[id] = name
                }
                reader.endArray()
            }
        }
        return result
    }

    private fun readStreams(action: String, emit: (StreamItem) -> Unit) {
        open(action).useConnection { connection ->
            JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var id: String? = null
                    var name = ""
                    var icon: String? = null
                    var categoryId: String? = null
                    var epgId: String? = null
                    var extension: String? = null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "stream_id" -> id = reader.scalarString()
                            "name" -> name = reader.scalarString().orEmpty()
                            "stream_icon" -> icon = reader.scalarString()
                            "category_id" -> categoryId = reader.scalarString()
                            "epg_channel_id" -> epgId = reader.scalarString()
                            "container_extension" -> extension = reader.scalarString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    emit(StreamItem(id, name, icon, categoryId, epgId, extension))
                }
                reader.endArray()
            }
        }
    }

    fun shortEpg(streamId: String, limit: Int = SHORT_EPG_LISTING_LIMIT): List<XtreamEpgListing> {
        val listings = mutableListOf<XtreamEpgListing>()
        open(
            "get_short_epg",
            mapOf("stream_id" to streamId, "limit" to limit.toString()),
        ).useConnection { connection ->
            JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "epg_listings" -> readListings(reader, listings)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }
        return listings
    }

    private fun readListings(reader: JsonReader, out: MutableList<XtreamEpgListing>) {
        reader.beginArray()
        while (reader.hasNext()) {
            var title = ""
            var description = ""
            var start = ""
            var end = ""
            var startTimestamp = 0L
            var stopTimestamp = 0L
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "title" -> title = reader.scalarString().orEmpty()
                    "description" -> description = reader.scalarString().orEmpty()
                    "start" -> start = reader.scalarString().orEmpty()
                    "end" -> end = reader.scalarString().orEmpty()
                    "start_timestamp" -> startTimestamp = reader.scalarString()?.toLongOrNull() ?: 0L
                    "stop_timestamp" -> stopTimestamp = reader.scalarString()?.toLongOrNull() ?: 0L
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            val decodedTitle = decodeListingField(title)
            val times = resolveListingTimes(startTimestamp, stopTimestamp, start, end)
            if (decodedTitle.isNotBlank() && times != null) {
                out += XtreamEpgListing(
                    title = decodedTitle,
                    description = decodeListingField(description),
                    startTimeMillis = times.first,
                    endTimeMillis = times.second,
                )
            }
        }
        reader.endArray()
    }

    private fun requestText(action: String?): String = open(action).useConnection { connection ->
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun open(action: String?, extras: Map<String, String> = emptyMap()): HttpURLConnection {
        val query = buildString {
            append("username=").append(encode(username))
            append("&password=").append(encode(password))
            action?.let { append("&action=").append(encode(it)) }
            extras.forEach { (key, value) ->
                append('&').append(encode(key)).append('=').append(encode(value))
            }
        }
        return (URL("$baseUrl/player_api.php?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T = try {
        connect()
        check(responseCode in 200..299) { "Xtream HTTP $responseCode $responseMessage" }
        block(this)
    } finally {
        disconnect()
    }

    private fun JsonReader.scalarString(): String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.STRING -> nextString()
        JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> { skipValue(); null }
    }

    private data class StreamItem(
        val id: String?,
        val name: String,
        val icon: String?,
        val categoryId: String?,
        val epgId: String?,
        val extension: String?,
    )

    companion object {
        fun decodeListingField(encoded: String): String {
            val trimmed = encoded.trim()
            if (trimmed.isEmpty()) return ""
            return runCatching {
                String(Base64.getDecoder().decode(trimmed), Charsets.UTF_8)
            }.getOrDefault(trimmed)
        }

        fun streamIdFromHttpUrl(value: String): String? {
            val path = value.trim().substringBefore('?').substringBefore('#')
            if (!path.contains("/live/", ignoreCase = true)) return null
            val id = path.substringAfterLast('/').substringBeforeLast('.').trim()
            return id.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        }

        fun resolveListingTimes(
            startTimestamp: Long,
            stopTimestamp: Long,
            startText: String,
            endText: String,
        ): Pair<Long, Long>? {
            var start = startTimestamp * 1_000L
            var stop = stopTimestamp * 1_000L
            if (start <= 0L) start = parseListingTimeText(startText)
            if (stop <= 0L) stop = parseListingTimeText(endText)
            if (start <= 0L || stop <= start) return null
            return start to stop
        }

        private fun parseListingTimeText(value: String): Long {
            val text = value.trim()
            if (text.isEmpty()) return 0L
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return runCatching { format.parse(text)?.time ?: 0L }.getOrDefault(0L)
        }

        fun normalizeBaseUrl(value: String): String {
            val withScheme = value.trim().let {
                if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
            }
            return withScheme.substringBefore("/player_api.php")
                .substringBefore("/get.php")
                .trimEnd('/')
        }

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
        private fun encodePath(value: String) = encode(value).replace("+", "%20")
        const val SHORT_EPG_LISTING_LIMIT = 4
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val USER_AGENT = "TVApp/0.1 AndroidTV"
    }
}
