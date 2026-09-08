package com.tvapp.livetv.data

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal class StalkerClient(
    portalUrl: String,
    private val macAddress: String,
) {
    val endpoint = normalizeEndpoint(portalUrl)

    fun channels(): Sequence<ParsedIptvChannel> = sequence {
        val token = handshake()
        runCatching { request(token, "stb", "get_profile") }
        val genres = responseArray(request(token, "itv", "get_genres"))
            .associate { item -> item.optString("id") to item.optString("title") }
        for ((index, item) in channelItems(token).withIndex()) {
            val cmd = item.cmd.removePrefix("ffmpeg ").trim()
            if (cmd.isBlank()) continue
            val id = item.id.ifBlank { index.toString() }
            yield(
                ParsedIptvChannel(
                    name = item.name.ifBlank { "Kanal $id" },
                    streamUrl = StalkerStreamUri.create(endpoint, macAddress, cmd),
                    tvgId = item.xmlTvId.takeIf(String::isNotBlank),
                    tvgName = item.name.takeIf(String::isNotBlank),
                    logoUrl = item.logo.takeIf(String::isNotBlank),
                    groupTitle = genres[item.genreId],
                    contentType = "LIVE",
                ),
            )
        }
    }

    private fun channelItems(token: String): Sequence<ChannelItem> = sequence {
        val connection = open(token, "itv", "get_all_channels")
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Stalker HTTP ${connection.responseCode} ${connection.responseMessage}"
            }
            JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "js") {
                        when (reader.peek()) {
                            JsonToken.BEGIN_ARRAY -> reader.readChannelArray { yield(it) }
                            JsonToken.BEGIN_OBJECT -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    if (reader.nextName() == "data" &&
                                        reader.peek() == JsonToken.BEGIN_ARRAY
                                    ) {
                                        reader.readChannelArray { yield(it) }
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } finally {
            connection.disconnect()
        }
    }

    private inline fun JsonReader.readChannelArray(emit: (ChannelItem) -> Unit) {
        beginArray()
        while (hasNext()) {
            var id = ""
            var name = ""
            var cmd = ""
            var xmlTvId = ""
            var logo = ""
            var genreId = ""
            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "id" -> id = scalarString().orEmpty()
                    "name" -> name = scalarString().orEmpty()
                    "cmd" -> cmd = scalarString().orEmpty()
                    "xmltv_id" -> xmlTvId = scalarString().orEmpty()
                    "logo" -> logo = scalarString().orEmpty()
                    "tv_genre_id" -> genreId = scalarString().orEmpty()
                    else -> skipValue()
                }
            }
            endObject()
            emit(ChannelItem(id, name, cmd, xmlTvId, logo, genreId))
        }
        endArray()
    }

    fun resolve(cmd: String): String {
        val token = handshake()
        runCatching { request(token, "stb", "get_profile") }
        val response = request(
            token,
            "itv",
            "create_link",
            mapOf("cmd" to "ffmpeg $cmd", "series" to "0", "forced_storage" to "undefined"),
        )
        val js = response.opt("js")
        val resolved = when (js) {
            is JSONObject -> js.optString("cmd")
            else -> ""
        }.removePrefix("ffmpeg ").trim()
        require(resolved.startsWith("http://") || resolved.startsWith("https://")) {
            "Stalker yayın bağlantısı alınamadı."
        }
        return resolved
    }

    private fun handshake(): String {
        val response = request(null, "stb", "handshake", mapOf("token" to ""))
        val token = response.optJSONObject("js")?.optString("token").orEmpty()
        require(token.isNotBlank()) { "Stalker Portal doğrulaması başarısız." }
        return token
    }

    private fun request(
        token: String?,
        type: String,
        action: String,
        extras: Map<String, String> = emptyMap(),
    ): JSONObject {
        val connection = open(token, type, action, extras)
        return try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Stalker HTTP ${connection.responseCode} ${connection.responseMessage}"
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        token: String?,
        type: String,
        action: String,
        extras: Map<String, String> = emptyMap(),
    ): HttpURLConnection {
        val parameters = linkedMapOf("type" to type, "action" to action).apply {
            putAll(extras)
            put("JsHttpRequest", "1-xml")
        }
        val query = parameters.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val connection = URL("$endpoint?$query").openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("X-User-Agent", "Model: MAG254; Link: Ethernet")
        connection.setRequestProperty("Cookie", "mac=${encode(macAddress)}; stb_lang=en; timezone=UTC")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        return connection
    }

    private fun JsonReader.scalarString(): String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> { skipValue(); null }
    }

    private data class ChannelItem(
        val id: String,
        val name: String,
        val cmd: String,
        val xmlTvId: String,
        val logo: String,
        val genreId: String,
    )

    private fun responseArray(response: JSONObject): List<JSONObject> {
        val js = response.opt("js")
        val array = when (js) {
            is JSONArray -> js
            is JSONObject -> js.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
        }
    }

    companion object {
        fun normalizeEndpoint(value: String): String {
            val withScheme = value.trim().let {
                if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
            }.trimEnd('/')
            return when {
                withScheme.endsWith("portal.php", true) || withScheme.endsWith("server/load.php", true) -> withScheme
                withScheme.endsWith("/stalker_portal/c", true) ->
                    withScheme.dropLast(2) + "/server/load.php"
                withScheme.endsWith("/c", true) -> withScheme.dropLast(2) + "/portal.php"
                else -> "$withScheme/portal.php"
            }
        }

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) MAG254 stbapp ver: 4 rev: 1812"
    }
}
