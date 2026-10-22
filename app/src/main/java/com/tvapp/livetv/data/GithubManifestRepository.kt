package com.tvapp.livetv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class GithubManifestItem(
    val name: String,
    val url: String,
    val poster: String? = null,
    val description: String? = null,
    val sourceFormat: String? = null,
)

class GithubManifestRepository {

    suspend fun fetchManifest(
        username: String,
        fileName: String = DEFAULT_FILE,
    ): Result<List<GithubManifestItem>> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().ifEmpty { DEFAULT_USER }
        val cleanFile = fileName.trim().ifEmpty { DEFAULT_FILE }

        val primaryUrl = "https://raw.githubusercontent.com/$cleanUser/gunestv/refs/heads/main/$cleanFile"
        val fallbackUrl = "https://raw.githubusercontent.com/$cleanUser/gunestv/main/$cleanFile"

        val jsonText = downloadText(primaryUrl).getOrElse {
            downloadText(fallbackUrl).getOrElse { error ->
                return@withContext Result.failure(error)
            }
        }

        runCatching {
            parseManifest(jsonText)
        }
    }

    fun parseManifest(jsonText: String): List<GithubManifestItem> {
        val array = JSONArray(jsonText)
        val items = ArrayList<GithubManifestItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val url = obj.optString("url").trim()
            if (name.isEmpty() || url.isEmpty()) continue

            val poster = obj.optString("poster").trim().takeIf { it.isNotEmpty() }
            val description = obj.optString("description").trim().takeIf { it.isNotEmpty() }
            val format = obj.optString("kaynak").trim().takeIf { it.isNotEmpty() }

            items.add(
                GithubManifestItem(
                    name = name,
                    url = url,
                    poster = poster,
                    description = description,
                    sourceFormat = format,
                )
            )
        }
        return items
    }

    private fun downloadText(urlString: String): Result<String> = runCatching {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TVApp/1.0 (Android TV)")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            instanceFollowRedirects = true
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
            conn.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_USER = "GeceHub"
        const val DEFAULT_FILE = "live2.json"
        const val VOD_FILE = "vod2.json"
    }
}
