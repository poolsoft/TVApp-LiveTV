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
    /** One IPTV/XMLTV source as shown on the phone's management screen. */
    data class SourceRow(
        val kind: String,
        val id: Long,
        val name: String,
        val channelCount: Int,
        val selectedCount: Int?,
        val urlKind: Boolean,
        val error: String?,
    )

    /** One queued import with its observable status. */
    data class ImportRow(
        val id: Long,
        val name: String,
        val kind: String,
        val status: String,
        val importedChannels: Int,
        val error: String?,
    )

    sealed class PairResult {
        data class Paired(val token: String) : PairResult()
        data class Refused(val message: String) : PairResult()
    }

    sealed class MutationResult {
        data object Applied : MutationResult()
        data class Failed(val message: String) : MutationResult()
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

    fun sources(): Result<List<SourceRow>> = runCatching {
        val request = authorizedRequest(normalizeAddress(store.address()) + "/api/v1/sources")
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val payload = JSONObject(response.body?.string().orEmpty())
            val array = payload.optJSONArray("sources") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SourceRow(
                    kind = item.optString("kind"),
                    id = item.optLong("id"),
                    name = item.optString("name"),
                    channelCount = item.optInt("channelCount"),
                    selectedCount = if (item.isNull("selectedCount")) null else item.optInt("selectedCount"),
                    urlKind = item.optBoolean("urlKind", false),
                    error = if (item.isNull("error")) null else item.optString("error"),
                )
            }
        }
    }

    fun imports(): Result<List<ImportRow>> = runCatching {
        val request = authorizedRequest(normalizeAddress(store.address()) + "/api/v1/imports")
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val payload = JSONObject(response.body?.string().orEmpty())
            val array = payload.optJSONArray("imports") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                ImportRow(
                    id = item.optLong("id"),
                    name = item.optString("name"),
                    kind = item.optString("kind", "iptv"),
                    status = item.optString("status"),
                    importedChannels = item.optInt("importedChannels"),
                    error = if (item.isNull("error")) null else item.optString("error"),
                )
            }
        }
    }

    /** Queues an IPTV playlist or XMLTV EPG import on the TV. */
    fun queueImport(name: String, url: String, kind: String): Result<Long> = runCatching {
        val payload = JSONObject()
            .put("url", url)
            .put("name", name)
            .put("kind", kind)
        val request = Request.Builder()
            .url(normalizeAddress(store.address()) + "/api/v1/imports")
            .header("Authorization", "Bearer ${store.token()}")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            JSONObject(response.body?.string().orEmpty()).optLong("id")
        }
    }

    /** Deletes one source together with its channels/programs. */
    fun deleteSource(kind: String, id: Long): MutationResult =
        postSourceMutation("delete", kind, id)

    /** Re-downloads an existing URL-kind source in place. */
    fun refreshSource(kind: String, id: Long): MutationResult =
        postSourceMutation("refresh", kind, id)

    private fun postSourceMutation(operation: String, kind: String, id: Long): MutationResult = runCatching {
        val payload = JSONObject().put("kind", kind).put("id", id)
        val request = Request.Builder()
            .url(normalizeAddress(store.address()) + "/api/v1/sources/$operation")
            .header("Authorization", "Bearer ${store.token()}")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
        }
    }.fold(
        onSuccess = { MutationResult.Applied },
        onFailure = { error -> MutationResult.Failed(error.message ?: "Bağlantı hatası.") },
    )

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
