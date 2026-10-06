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
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import com.tvapp.livetv.data.local.VodMetadataEntity

data class XtreamEpgListing(
    val title: String,
    val description: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
)

data class XtreamAccountInfo(
    val status: String?,
    val expiresAtMillis: Long?,
    val activeConnections: Int?,
    val maximumConnections: Int?,
)

internal class XtreamClient(
    serverUrl: String,
    private val username: String,
    private val password: String,
) {
    val baseUrl = normalizeBaseUrl(serverUrl)

    fun verifyAccount(): XtreamAccountInfo {
        val account = accountInfo()
        require(account.status == null || account.status.equals("Active", ignoreCase = true)) {
            "Xtream hesabı etkin değil: ${account.status}"
        }
        return account
    }

    fun accountInfo(): XtreamAccountInfo {
        val response = requestText(null)
        val user = JSONObject(response).optJSONObject("user_info")
            ?: error("Xtream sunucusu hesap bilgisi döndürmedi.")
        require(user.optInt("auth", 0) == 1) { "Xtream kullanıcı adı veya parola hatalı." }
        val status = if (user.isNull("status")) "" else user.optString("status")
        return XtreamAccountInfo(
            status.takeIf(String::isNotBlank),
            user.optString("exp_date").toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 1_000 }?.times(1_000),
            user.optString("active_cons").toIntOrNull()?.takeIf { it >= 0 },
            user.optString("max_connections").toIntOrNull()?.takeIf { it > 0 },
        )
    }

    fun channels(): Sequence<ParsedIptvChannel> = sequence {
        // Only small category maps run concurrently; stream catalogs remain sequential and streamed.
        val categoryMaps = channelCategories()
        val liveCategories = categoryMaps.first
        for (item in streams("get_live_streams")) {
            val id = item.id ?: continue
            yield(
                ParsedIptvChannel(
                    name = item.name.ifBlank { "Kanal $id" },
                    streamUrl = "$baseUrl/live/${encodePath(username)}/${encodePath(password)}/$id.ts",
                    tvgId = item.epgId,
                    tvgName = item.name,
                    logoUrl = item.icon,
                    groupTitle = liveCategories[item.categoryId] ?: item.categoryId,
                    contentType = "LIVE",
                    catchUpMode = if (item.hasArchive) "xtream" else null,
                    catchUpDays = item.archiveDays,
                ),
            )
        }

        val vodCategories = categoryMaps.second
        for (item in streams("get_vod_streams")) {
            val id = item.id ?: continue
            val extension = item.extension?.trim()?.trimStart('.')?.takeIf(String::isNotBlank) ?: "mp4"
            yield(
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

    private fun channelCategories(): Pair<Map<String, String>, Map<String, String>> {
        val executor = Executors.newFixedThreadPool(2)
        val live = executor.submit<Map<String, String>> { categories("get_live_categories") }
        val vod = executor.submit<Map<String, String>> { categories("get_vod_categories") }
        return try {
            live.get() to vod.get()
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        } finally {
            live.cancel(true)
            vod.cancel(true)
            executor.shutdownNow()
        }
    }

    fun series(sourceId: Long, now: Long): Sequence<VodMetadataEntity> = sequence {
        val groups = categories("get_series_categories")
        open("get_series").useConnection { connection ->
            JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    val fields = reader.metadataFields()
                    val id = fields["series_id"]?.takeIf { it.all(Char::isDigit) && it.isNotBlank() } ?: continue
                    val name = fields["name"]?.takeIf(String::isNotBlank) ?: continue
                    yield(VodMetadataEntity(sourceKey = "vod:$sourceId:series:$id", sourceId = sourceId,
                        kind = "SERIES", providerId = id, name = name, logoUrl = fields["cover"],
                        category = groups[fields["category_id"]] ?: fields["category_id"],
                        description = fields["plot"], updatedAt = now))
                }
                reader.endArray()
            }
        }
    }

    /** Episodes are streamed, rather than buffering an entire provider response. */
    fun episodes(series: VodMetadataEntity, now: Long): Sequence<VodMetadataEntity> = sequence {
        open("get_series_info", mapOf("series_id" to series.providerId)).useConnection { connection ->
            JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() != "episodes") { reader.skipValue(); continue }
                    // Providers may return an empty array instead of an episodes object.
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val fields = reader.metadataFields()
                            episode(series, fields, fields["season"]?.toIntOrNull(), now)?.let { yield(it) }
                        }
                        reader.endArray()
                    } else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val season = reader.nextName().toIntOrNull()
                            if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); continue }
                            reader.beginArray()
                            while (reader.hasNext()) {
                                episode(series, reader.metadataFields(), season, now)?.let { yield(it) }
                            }
                            reader.endArray()
                        }
                        reader.endObject()
                    } else reader.skipValue()
                }
                reader.endObject()
            }
        }
    }

    private fun episode(series: VodMetadataEntity, fields: Map<String, String>, season: Int?, now: Long): VodMetadataEntity? {
        val id = fields["id"]?.takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return null
        val extension = fields["container_extension"]?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: return null
        return VodMetadataEntity(sourceKey = "vod:${series.sourceId}:episode:$id", sourceId = series.sourceId,
            kind = "EPISODE", providerId = id, parentKey = series.sourceKey,
            seasonNumber = season ?: fields["season"]?.toIntOrNull(), episodeNumber = fields["episode_num"]?.toIntOrNull(),
            name = fields["title"]?.takeIf(String::isNotBlank) ?: id,
            logoUrl = fields["movie_image"] ?: series.logoUrl, category = series.category,
            description = fields["plot"], durationMillis = fields["duration_secs"]?.toLongOrNull()?.takeIf { it > 0 }?.times(1000),
            streamUrl = "$baseUrl/series/${encodePath(username)}/${encodePath(password)}/$id.$extension", updatedAt = now)
    }

    fun movieDetails(key: String, sourceId: Long, streamId: String, now: Long): VodMetadataEntity? {
        var fields = emptyMap<String, String>()
        open("get_vod_info", mapOf("vod_id" to streamId)).useConnection { connection ->
            JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "info" && reader.peek() == JsonToken.BEGIN_OBJECT) fields = reader.metadataFields()
                    else reader.skipValue()
                }
                reader.endObject()
            }
        }
        if (fields.isEmpty()) return null
        return VodMetadataEntity(sourceKey = key, sourceId = sourceId, kind = "MOVIE", providerId = streamId,
            name = fields["name"].orEmpty(), logoUrl = fields["movie_image"], description = fields["plot"] ?: fields["description"],
            durationMillis = fields["duration_secs"]?.toLongOrNull()?.takeIf { it > 0 }?.times(1000), updatedAt = now)
    }

    private fun JsonReader.metadataFields(): Map<String, String> {
        if (peek() != JsonToken.BEGIN_OBJECT) { skipValue(); return emptyMap() }
        val fields = mutableMapOf<String, String>()
        beginObject()
        while (hasNext()) {
            val key = nextName()
            if (key == "info" && peek() == JsonToken.BEGIN_OBJECT) fields.putAll(metadataFields())
            else if (key in setOf("series_id", "name", "cover", "category_id", "plot", "id", "title",
                    "season", "episode_num", "container_extension", "movie_image", "duration_secs", "description")) {
                scalarString()?.takeIf { it != "null" && it.isNotBlank() }?.let { fields[key] = it }
            } else skipValue()
        }
        endObject()
        return fields
    }

    private fun streams(action: String): Sequence<StreamItem> = sequence {
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
                    var hasArchive = false
                    var archiveDays = 0
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "stream_id" -> id = reader.scalarString()
                            "name" -> name = reader.scalarString().orEmpty()
                            "stream_icon" -> icon = reader.scalarString()
                            "category_id" -> categoryId = reader.scalarString()
                            "epg_channel_id" -> epgId = reader.scalarString()
                            "container_extension" -> extension = reader.scalarString()
                            "tv_archive" -> hasArchive = reader.scalarString() == "1"
                            "tv_archive_duration" -> archiveDays = reader.scalarString()?.toIntOrNull() ?: 0
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    yield(StreamItem(id, name, icon, categoryId, epgId, extension, hasArchive, archiveDays))
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
        val hasArchive: Boolean,
        val archiveDays: Int,
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
        private const val CONNECTION_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val USER_AGENT = "TVApp/0.1 AndroidTV"
    }
}
