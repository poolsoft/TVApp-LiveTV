package com.tvapp.livetv.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal class StalkerClient(
    portalUrl: String,
    private val macAddress: String,
) {
    val endpoint = normalizeEndpoint(portalUrl)

    fun channels(): List<ParsedIptvChannel> = buildList {
        val token = handshake()
        runCatching { request(token, "stb", "get_profile") }
        val genres = responseArray(request(token, "itv", "get_genres"))
            .associate { item -> item.optString("id") to item.optString("title") }
        val channels = responseArray(request(token, "itv", "get_all_channels"))
        for ((index, item) in channels.withIndex()) {
            val cmd = item.optString("cmd").removePrefix("ffmpeg ").trim()
            if (cmd.isBlank()) continue
            val id = item.optString("id", index.toString())
            add(
                ParsedIptvChannel(
                    name = item.optString("name").ifBlank { "Kanal $id" },
                    streamUrl = StalkerStreamUri.create(endpoint, macAddress, cmd),
                    tvgId = item.optString("xmltv_id").takeIf(String::isNotBlank),
                    tvgName = item.optString("name").takeIf(String::isNotBlank),
                    logoUrl = item.optString("logo").takeIf(String::isNotBlank),
                    groupTitle = genres[item.optString("tv_genre_id")],
                    contentType = "LIVE",
                ),
            )
        }
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
