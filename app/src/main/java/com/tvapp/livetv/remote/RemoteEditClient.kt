package com.tvapp.livetv.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REMOTEEDIT-003: phone-side HTTP client for the TV management API. Thin and
 * synchronous — callers run it on Dispatchers.IO. Uses the same OkHttp stack
 * the app already ships.
 */
class RemoteEditClient(
    private val store: RemoteEditClientStore,
    private val http: OkHttpClient = defaultHttp(),
) {
    data class ChannelRow(
        val sourceKey: String,
        val displayName: String,
        val displayNumber: String,
        val source: String,
        val favorite: Boolean,
        val hidden: Boolean,
    )

    sealed class PairResult {
        data class Paired(val token: String) : PairResult()
        data class Refused(val message: String) : PairResult()
    }

    sealed class PatchResult {
        data class Applied(val newRevision: Long) : PatchResult()
        data class Conflict(val currentRevision: Long) : PatchResult()
        data class Failed(val message: String) : PatchResult()
    }

    fun ping(address: String): JSONObject? = runCatching {
        val request = Request.Builder()
            .url(normalizeAddress(address) + "/api/v1/ping")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            JSONObject(response.body?.string().orEmpty())
        }
    }.getOrNull()

    fun pair(address: String, code: String, deviceName: String): PairResult = runCatching {
        val body = JSONObject()
            .put("code", code.trim())
            .put("deviceName", deviceName.ifBlank { "Telefon" })
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(normalizeAddress(address) + "/api/v1/pair")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (response.isSuccessful && json.has("token")) {
                PairResult.Paired(json.getString("token"))
            } else {
                PairResult.Refused(json.optString("message", "Eşleştirme reddedildi."))
            }
        }
    }.getOrElse { error -> PairResult.Refused(error.message ?: "Bağlantı hatası.") }

    fun channels(after: String?, limit: Int = 100, query: String? = null): Result<List<ChannelRow>> =
        runCatching {
            val url = buildString {
                append(normalizeAddress(store.address()))
                append("/api/v1/channels?limit=")
                append(limit)
                if (!after.isNullOrBlank()) append("&after=").append(urlEncode(after))
                if (!query.isNullOrBlank()) append("&q=").append(urlEncode(query))
            }
            val request = authorizedRequest(url)
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val payload = JSONObject(response.body?.string().orEmpty())
                val array = payload.optJSONArray("channels") ?: JSONArray()
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    ChannelRow(
                        sourceKey = item.getString("sourceKey"),
                        displayName = item.optString("displayName"),
                        displayNumber = item.optString("displayNumber"),
                        source = item.optString("source"),
                        favorite = item.optBoolean("favorite", false),
                        hidden = item.optBoolean("hidden", false),
                    )
                }
            }
        }

    /** Applies one patch; when offline the caller enqueues it instead. */
    fun patch(sourceKey: String, revision: Long, patch: JSONObject): PatchResult = runCatching {
        val payload = JSONObject(patch.toString()).put("revision", revision)
        val request = authorizedRequest(
            normalizeAddress(store.address()) + "/api/v1/channels/" + urlEncode(sourceKey),
        ).newBuilder()
            .patch(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            when {
                response.code == 409 ->
                    PatchResult.Conflict(json.optLong("currentRevision", -1L))

                response.isSuccessful && json.has("revision") ->
                    PatchResult.Applied(json.getLong("revision"))

                else -> PatchResult.Failed(json.optString("message", "HTTP ${response.code}"))
            }
        }
    }.getOrElse { error -> PatchResult.Failed(error.message ?: "Bağlantı hatası.") }

    private fun authorizedRequest(url: String): Request = Request.Builder()
        .url(url)
        .get()
        .header("Authorization", "Bearer ${store.token()}")
        .build()

    private fun normalizeAddress(address: String): String {
        val trimmed = address.trim().trimEnd('/')
        return if (trimmed.startsWith("http")) trimmed else "http://$trimmed"
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
