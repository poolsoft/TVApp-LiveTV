package com.tvapp.livetv.remote

import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
/**
 * REMOTEEDIT-001: the embedded, LAN-only management server. Serves the v1
 * REST API over plain HTTP on the Wi-Fi interface. Every endpoint except
 * `ping` requires a valid bearer token (REMOTEEDIT-002). Writes are NOT part
 * of this sprint: only read endpoints and pairing exist; `PATCH`/`batch`
 * arrive with REMOTEEDIT-004 and are rejected here with 404 so clients can
 * detect the capability.
 *
 * Security: the listener binds to the Wi-Fi IPv4 address only (loopback as
 * fallback when Wi-Fi is absent); no token is ever logged; error responses
 * never echo submitted secrets.
 */
class RemoteEditServer(
    private val preferencesStore: RemoteEditPreferencesStore,
    private val pairing: PairingAuthorizer,
    private val dataProvider: DataProvider,
) : NanoHTTPD(hostnameForBinding(), preferencesStore.port()) {

    /** Narrow authorization surface so the server does not depend on the
     *  Android-bound PairingStore directly; tests can stub it. */
    fun interface PairingAuthorizer {
        fun authorize(token: String): Boolean
    }

    /** Read-only view over TVApp data; implemented against repositories. */
    interface DataProvider {
        fun serverPing(): JSONObject

        /** Keyset-paged channel list (cursor = last sourceKey); JSON array.
         *  sourceId filters IPTV catalog rows to one source (channel picker). */
        fun channels(afterSourceKey: String?, limit: Int, query: String?, sourceId: Long?): JSONArray

        /** Single channel with full editable fields, or null. */
        fun channel(sourceKey: String): JSONObject?

        fun groups(): JSONArray

        fun sources(): JSONArray

        /** XMLTV EPG channel catalog (sourceId, channelId, channelName). */
        fun xmltvCatalog(): JSONArray

        /** Applies a full/delta selection change for an IPTV source.
         *  false = source unknown or payload empty. */
        fun applySelection(sourceId: Long, body: JSONObject): Boolean
    }

    @Throws(IOException::class)
    fun startServer() {
        start(SOCKET_READ_TIMEOUT, true)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.orEmpty().trimEnd('/')
        val method = session.method
        return try {
            when {
                // REMOTEEDIT-008: static web panel served from APK assets.
                method == Method.GET && (uri.isEmpty() || uri == "/" || uri.startsWith(WEB_ROOT)) ->
                    serveWebAsset(uri)

                method == Method.GET && uri == API_PING ->
                    json(StatusCode.OK, dataProvider.serverPing())

                method == Method.POST && uri == API_PAIR ->
                    handlePair(session)

                method == Method.OPTIONS ->
                    json(StatusCode.OK, JSONObject())

                uri.startsWith(API_ROOT) -> handleAuthorized(session, uri, method)

                else -> json(StatusCode.NOT_FOUND, error("not_found", "Bilinmeyen yol."))
            }
        } catch (error: BadRequestException) {
            json(StatusCode.BAD_REQUEST, error("bad_request", error.message ?: "Geçersiz istek."))
        } catch (error: JSONException) {
            json(StatusCode.BAD_REQUEST, error("bad_request", "JSON çözülemedi."))
        } catch (error: Exception) {
            json(StatusCode.INTERNAL_ERROR, error("internal", "Sunucu hatası."))
        }
    }

    /** REMOTEEDIT-008: serves assets/webpanel files; / → index.html. */
    private fun serveWebAsset(uri: String): Response {
        val relative = when {
            uri.isEmpty() || uri == "/" -> "index.html"
            uri.startsWith(WEB_ROOT) -> uri.removePrefix(WEB_ROOT).trimStart('/')
            else -> return json(StatusCode.NOT_FOUND, error("not_found", "Bilinmeyen yol."))
        }
        val safe = relative.takeWhile { it != '?' }
        if (safe.contains("..") || safe.isBlank()) {
            return json(StatusCode.NOT_FOUND, error("not_found", "Bilinmeyen yol."))
        }
        val mime = when (safe.substringAfterLast('.', "")) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
        val bytes = webPanelLoader?.invoke(safe) ?: return json(
            StatusCode.NOT_FOUND,
            error("not_found", "Panosu dosyası yok: $safe"),
        )
        return newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong())
            .apply { addHeader("Cache-Control", "no-cache") }
    }

    private fun handleAuthorized(
        session: IHTTPSession,
        uri: String,
        method: Method,
    ): Response {
        val token = bearerToken(session)
            ?: return json(StatusCode.UNAUTHORIZED, error("unauthorized", "Jeton gerekli."))
        if (!pairing.authorize(token)) {
            return json(StatusCode.UNAUTHORIZED, error("unauthorized", "Jeton geçersiz."))
        }
        return when {
            method == Method.GET && uri == API_CHANNELS -> handleChannels(session)
            method == Method.GET && uri.startsWith(API_CHANNELS + "/") ->
                dataProvider.channel(uri.removePrefix(API_CHANNELS + "/"))?.let {
                    json(StatusCode.OK, it)
                } ?: json(StatusCode.NOT_FOUND, error("not_found", "Kanal bulunamadı."))

            method == Method.GET && uri == API_EVENTS -> handleEvents(session)

            method == Method.GET && uri == API_GROUPS ->
                json(StatusCode.OK, dataProvider.groups())

            method == Method.GET && uri == API_SOURCES ->
                json(StatusCode.OK, dataProvider.sources())

            method == Method.PATCH && uri.startsWith(API_CHANNELS + "/") ->
                handlePatch(session, uri.removePrefix(API_CHANNELS + "/"))

            method == Method.POST && uri == API_CHANNELS_BATCH ->
                handleBatch(session)

            method == Method.POST && uri == API_IMPORTS ->
                handleImportRequest(session)

            method == Method.GET && uri == API_IMPORTS ->
                handleImportList()

            method == Method.GET && uri.startsWith(API_IMPORTS + "/") ->
                handleImportStatus(uri.removePrefix(API_IMPORTS + "/"))

            method == Method.GET && uri == API_XMLTV_CATALOG ->
                json(StatusCode.OK, JSONObject().put("catalog", dataProvider.xmltvCatalog()))

            method == Method.POST && uri.startsWith(API_SOURCES + "/selection/") ->
                handleSelection(session, uri.removePrefix(API_SOURCES + "/selection/"))

            method == Method.POST && uri == API_SOURCES + "/delete" ->
                handleSourceMutation(session, OP_DELETE)

            method == Method.POST && uri == API_SOURCES + "/refresh" ->
                handleSourceMutation(session, OP_REFRESH)

            else -> json(
                StatusCode.NOT_FOUND,
                error("not_found", "Bu sürümde bulunmayan uç nokta."),
            )
        }
    }

    /** REMOTEEDIT-004: revision-guarded field patch on one channel. */
    private fun handlePatch(session: IHTTPSession, sourceKey: String): Response {
        if (sourceKey.isBlank()) {
            throw BadRequestException("Kanal anahtarı gerekli.")
        }
        val body = readBody(session)
        val expectedRevision = body.optLong("revision", Long.MIN_VALUE)
        if (expectedRevision == Long.MIN_VALUE) {
            throw BadRequestException("revision alanı gerekli.")
        }
        val patch = parsePatch(body)
        val result = writeHandler?.invoke(sourceKey, expectedRevision, patch)
            ?: return json(
                StatusCode.NOT_FOUND,
                error("writes_unsupported", "Bu sunucu yazma kabul etmiyor."),
            )
        return when (result) {
            is WriteOutcome.Applied -> {
                versionCounter.incrementAndGet()
                json(
                    StatusCode.OK,
                    JSONObject()
                        .put("sourceKey", sourceKey)
                        .put("revision", result.newRevision),
                )
            }

            is WriteOutcome.Rejected -> json(
                StatusCode.CONFLICT,
                JSONObject()
                    .put("error", "revision_conflict")
                    .put("message", "Kanal TV tarafında değişmiş.")
                    .put("currentRevision", result.currentRevision),
            )

            WriteOutcome.NotFound -> json(
                StatusCode.NOT_FOUND,
                error("not_found", "Kanal bulunamadı."),
            )
        }
    }

    /** REMOTEEDIT-004: bounded batch of the same revision-guarded patches. */
    private fun handleBatch(session: IHTTPSession): Response {
        val body = readBody(session)
        val ops = body.optJSONArray("ops")
            ?: throw BadRequestException("ops dizisi gerekli.")
        if (ops.length() > MAX_BATCH_OPS) {
            return json(
                StatusCode.PAYLOAD_TOO_LARGE,
                error("batch_too_large", "En fazla $MAX_BATCH_OPS işlem/istek."),
            )
        }
        val results = JSONArray()
        for (index in 0 until ops.length()) {
            val op = ops.optJSONObject(index) ?: throw BadRequestException("ops[$index] nesne olmalı.")
            val sourceKey = op.optString("sourceKey")
            val expectedRevision = op.optLong("revision", Long.MIN_VALUE)
            if (sourceKey.isBlank() || expectedRevision == Long.MIN_VALUE) {
                throw BadRequestException("ops[$index]: sourceKey ve revision gerekli.")
            }
            val patch = parsePatch(op)
            val outcome = writeHandler?.invoke(sourceKey, expectedRevision, patch)
            results.put(
                when (outcome) {
                    is WriteOutcome.Applied -> JSONObject()
                        .put("sourceKey", sourceKey)
                        .put("status", "applied")
                        .put("revision", outcome.newRevision)

                    is WriteOutcome.Rejected -> JSONObject()
                        .put("sourceKey", sourceKey)
                        .put("status", "conflict")
                        .put("currentRevision", outcome.currentRevision)

                    WriteOutcome.NotFound, null -> JSONObject()
                        .put("sourceKey", sourceKey)
                        .put("status", "not_found")
                },
            )
        }
        return json(StatusCode.OK, JSONObject().put("results", results))
    }

    private fun parsePatch(body: JSONObject): ChannelPatch = ChannelPatch(
        favorite = body.optBooleanOrNull("favorite"),
        hidden = body.optBooleanOrNull("hidden"),
        customName = body.optStringOrNull("customName"),
        clearCustomName = body.optBoolean("clearCustomName", false),
        customNumber = body.optIntOrNull("customNumber"),
        clearCustomNumber = body.optBoolean("clearCustomNumber", false),
        groupId = body.optLongOrNull("groupId"),
        clearGroupId = body.optBoolean("clearGroupId", false),
        sortOrder = body.optIntOrNull("sortOrder"),
    )

    /** One channel's editable fields; null means "leave unchanged". */
    data class ChannelPatch(
        val favorite: Boolean?,
        val hidden: Boolean?,
        val customName: String?,
        val clearCustomName: Boolean,
        val customNumber: Int?,
        val clearCustomNumber: Boolean,
        val groupId: Long?,
        val clearGroupId: Boolean,
        val sortOrder: Int?,
    )

    sealed class WriteOutcome {
        data class Applied(val newRevision: Long) : WriteOutcome()
        data class Rejected(val currentRevision: Long) : WriteOutcome()
        data object NotFound : WriteOutcome()
    }

    private fun handlePair(session: IHTTPSession): Response {
        val body = readBody(session)
        val code = body.optString("code")
        val deviceName = body.optString("deviceName").trim()
            .ifBlank { "Cihaz" }
            .take(DEVICE_NAME_MAX_LENGTH)
        if (code.isBlank()) {
            throw BadRequestException("Eşleştirme kodu gerekli.")
        }
        val paired = pairingHandler?.invoke(code, deviceName)
            ?: return json(
                StatusCode.UNAUTHORIZED,
                error("pairing_failed", "Kod geçersiz, süresi doldu veya kilit devrede."),
            )
        return json(
            StatusCode.OK,
            JSONObject()
                .put("deviceId", paired.first)
                .put("deviceName", paired.second)
                .put("token", paired.third),
        )
    }

    /** Optional pairing handler; when absent (or when it returns null) the pair
     *  endpoint refuses with 401 — the code was wrong/expired/locked out. */
    var pairingHandler: ((code: String, deviceName: String) -> Triple<Long, String, String>?)? = null

    /** REMOTEEDIT-004: revision-guarded write path; absent = writes disabled. */
    var writeHandler: (
        (sourceKey: String, expectedRevision: Long, patch: ChannelPatch) -> WriteOutcome?
    )? = null

    /** REMOTEEDIT-005: monotonic data version; every applied write bumps it. */
    private val versionCounter = java.util.concurrent.atomic.AtomicLong(1L)

    fun bumpDataVersion(): Long = versionCounter.incrementAndGet()

    /** REMOTEEDIT-006: queue a remote source import (IPTV playlist or XMLTV
     *  EPG); executes via the TV app's existing pipelines. URL/credentials are
     *  never echoed or logged. */
    private fun handleImportRequest(session: IHTTPSession): Response {
        val body = readBody(session)
        val url = body.optString("url").trim()
        val name = body.optString("name").trim()
        val kind = when (body.optString("kind").trim().lowercase(java.util.Locale.ROOT)) {
            "xmltv", "epg" -> RemoteImportQueue.Request.Kind.XMLTV
            else -> RemoteImportQueue.Request.Kind.IPTV
        }
        if (url.isBlank()) {
            throw BadRequestException("url gerekli.")
        }
        val importQueue = importQueueHandler ?: return json(
            StatusCode.NOT_FOUND,
            error("imports_unsupported", "Bu sunucu kaynak aktarımı kabul etmiyor."),
        )
        val id = try {
            importQueue.invoke(name, url, kind)
        } catch (error: IllegalArgumentException) {
            throw BadRequestException(error.message ?: "Geçersiz adres.")
        }
        return json(
            StatusCode.OK,
            JSONObject()
                .put("id", id)
                .put("status", "queued"),
        )
    }

    private fun handleImportList(): Response {
        val queue = importQueueListHandler
            ?: return json(
                StatusCode.NOT_FOUND,
                error("imports_unsupported", "Bu sunucu kaynak aktarımı kabul etmiyor."),
            )
        val requests = JSONArray()
        queue().forEach { request ->
            requests.put(importJson(request))
        }
        return json(StatusCode.OK, JSONObject().put("imports", requests))
    }

    private fun handleImportStatus(idText: String): Response {
        val id = idText.toLongOrNull() ?: return json(
            StatusCode.BAD_REQUEST,
            error("bad_request", "Geçersiz istek kimliği."),
        )
        val queue = importQueueListHandler
            ?: return json(
                StatusCode.NOT_FOUND,
                error("imports_unsupported", "Bu sunucu kaynak aktarımı kabul etmiyor."),
            )
        val request = queue().firstOrNull { it.id == id }
            ?: return json(StatusCode.NOT_FOUND, error("not_found", "İstek bulunamadı."))
        return json(StatusCode.OK, importJson(request))
    }

    private fun importJson(request: RemoteImportQueue.Request): JSONObject = JSONObject()
        .put("id", request.id)
        .put("name", request.name)
        .put("kind", request.kind.name.lowercase(java.util.Locale.ROOT))
        .put("status", request.status.name.lowercase())
        .put("importedChannels", request.importedChannels)
        .put("error", request.error ?: JSONObject.NULL)

    /** Source delete/refresh; body {kind, id}. A missing handler or unknown
     *  source answers 404; the outcome never echoes URLs. */
    private fun handleSourceMutation(session: IHTTPSession, operation: String): Response {
        val body = readBody(session)
        val kind = body.optString("kind").trim()
        val id = body.optLong("id", -1L)
        if (kind.isBlank() || id <= 0) {
            throw BadRequestException("kind ve id gerekli.")
        }
        val handler = sourceMutationHandler
            ?: return json(
                StatusCode.NOT_FOUND,
                error("sources_unsupported", "Bu sunucu kaynak yönetimini desteklemiyor."),
            )
        val updated = try {
            handler(kind, id, operation)
        } catch (error: IllegalArgumentException) {
            throw BadRequestException(error.message ?: "Geçersiz kaynak isteği.")
        }
        return if (updated) {
            versionCounter.incrementAndGet()
            json(StatusCode.OK, JSONObject().put("status", "ok"))
        } else {
            json(StatusCode.NOT_FOUND, error("not_found", "Kaynak bulunamadı veya işlem uygulanamaz."))
        }
    }

    /** IPTV selection change; path /sources/selection/{id}, body {add[], remove[]}
     *  or {selected[]}. Responds ok or 404 when the source is unknown. */
    private fun handleSelection(session: IHTTPSession, idText: String): Response {
        val id = idText.toLongOrNull() ?: return json(
            StatusCode.BAD_REQUEST,
            error("bad_request", "Geçersiz kaynak kimliği."),
        )
        val body = readBody(session)
        val applied = try {
            dataProvider.applySelection(id, body)
        } catch (error: JSONException) {
            throw BadRequestException("JSON gövdesi hatalı.")
        } catch (error: IllegalArgumentException) {
            throw BadRequestException(error.message ?: "Geçersiz seçim isteği.")
        }
        return if (applied) {
            versionCounter.incrementAndGet()
            json(StatusCode.OK, JSONObject().put("status", "ok"))
        } else {
            json(StatusCode.NOT_FOUND, error("not_found", "Kaynak bulunamadı veya seçim boş."))
        }
    }

    /** REMOTEEDIT-006: import queue hooks; absent = imports disabled. */
    var importQueueHandler: ((name: String, url: String, kind: RemoteImportQueue.Request.Kind) -> Long)? = null
    var importQueueListHandler: (() -> List<RemoteImportQueue.Request>)? = null

    /** Source delete/refresh hook: (kind, id, operation) -> Boolean. */
    var sourceMutationHandler: ((kind: String, id: Long, operation: String) -> Boolean)? = null

    /** REMOTEEDIT-005: long-poll until the data version moves or the wait
     *  elapses. NanoHTTPD worker threads tolerate the blocking sleep; clients
     *  treat any changed response as "refetch your list". */
    private fun handleEvents(session: IHTTPSession): Response {
        val since = session.parameters["since"]?.firstOrNull()?.toLongOrNull()
            ?: return json(
                StatusCode.BAD_REQUEST,
                error("bad_request", "since parametresi gerekli."),
            )
        val deadline = System.currentTimeMillis() + EVENTS_MAX_WAIT_MILLIS
        var current = versionCounter.get()
        while (current <= since && System.currentTimeMillis() < deadline) {
            Thread.sleep(EVENTS_POLL_STEP_MILLIS)
            current = versionCounter.get()
        }
        return json(
            StatusCode.OK,
            JSONObject()
                .put("version", current)
                .put("changed", current > since),
        )
    }

    /** REMOTEEDIT-008: asset loader for the web panel; wired to APK assets. */
    var webPanelLoader: ((path: String) -> ByteArray?)? = null

    private fun handleChannels(session: IHTTPSession): Response {
        val parameters = session.parameters
        val after = parameters["after"]?.firstOrNull()?.trim()?.take(CURSOR_MAX_LENGTH)
            ?.takeIf(String::isNotEmpty)
        val query = parameters["q"]?.firstOrNull()?.trim()?.take(QUERY_MAX_LENGTH)
            ?.takeIf(String::isNotEmpty)
        val sourceId = parameters["sourceId"]?.firstOrNull()?.toLongOrNull()
        val limit = (parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT)
            .coerceIn(1, MAX_PAGE_LIMIT)
        return json(
            StatusCode.OK,
            JSONObject()
                .put("channels", dataProvider.channels(after, limit, query, sourceId))
                .put("limit", limit)
                .put("after", after ?: JSONObject.NULL),
        )
    }

    private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
        if (has(name) && !isNull(name)) optBoolean(name) else null

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    private fun bearerToken(session: IHTTPSession): String? {
        val header = session.headers["authorization"] ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        return header.substring(7).trim().takeIf { it.isNotEmpty() }
    }

    private fun readBody(session: IHTTPSession): JSONObject {
        val map = HashMap<String, String>()
        session.parseBody(map)
        val raw = map["postData"] ?: throw BadRequestException("JSON gövde gerekli.")
        return JSONObject(raw)
    }

    private fun json(status: StatusCode, payload: JSONObject): Response =
        newFixedLengthResponse(status.status, "application/json", payload.toString())
            .apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
                addHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS")
            }

    private fun json(status: StatusCode, payload: JSONArray): Response =
        newFixedLengthResponse(status.status, "application/json", payload.toString())
            .apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
                addHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS")
            }

    internal enum class StatusCode(val status: Response.Status) {
        OK(Response.Status.OK),
        BAD_REQUEST(Response.Status.BAD_REQUEST),
        UNAUTHORIZED(Response.Status.UNAUTHORIZED),
        NOT_FOUND(Response.Status.NOT_FOUND),
        CONFLICT(Response.Status.CONFLICT),
        PAYLOAD_TOO_LARGE(Response.Status.PAYLOAD_TOO_LARGE),
        INTERNAL_ERROR(Response.Status.INTERNAL_ERROR),
    }

    internal class BadRequestException(message: String) : Exception(message)

    data class EndpointProbe(val method: Method, val path: String)

    companion object {
        private const val SOCKET_READ_TIMEOUT = 5_000
        private const val API_ROOT = "/api/v1"
        private const val API_PING = "$API_ROOT/ping"
        private const val API_PAIR = "$API_ROOT/pair"
        private const val API_CHANNELS = "$API_ROOT/channels"
        private const val API_CHANNELS_BATCH = "$API_ROOT/channels/batch"
        private const val API_EVENTS = "$API_ROOT/events"
        private const val API_IMPORTS = "$API_ROOT/imports"
        private const val API_GROUPS = "$API_ROOT/groups"
        private const val API_SOURCES = "$API_ROOT/sources"
        private const val API_XMLTV_CATALOG = "$API_ROOT/xmltv/catalog"
        private const val WEB_ROOT = "/assets/webpanel"
        private const val EVENTS_MAX_WAIT_MILLIS = 25_000L
        private const val EVENTS_POLL_STEP_MILLIS = 500L
        private const val MAX_BATCH_OPS = 100
        private const val DEFAULT_PAGE_LIMIT = 100
        private const val MAX_PAGE_LIMIT = 500
        private const val QUERY_MAX_LENGTH = 64
        private const val CURSOR_MAX_LENGTH = 128
        private const val DEVICE_NAME_MAX_LENGTH = 64
        internal const val OP_DELETE = "delete"
        internal const val OP_REFRESH = "refresh"

        /** Routes exposed for tests without starting a socket. */
        internal val ROUTES = listOf(
            EndpointProbe(Method.GET, "/"),
            EndpointProbe(Method.GET, API_PING),
            EndpointProbe(Method.POST, API_PAIR),
            EndpointProbe(Method.GET, API_CHANNELS),
            EndpointProbe(Method.GET, "$API_CHANNELS/{sourceKey}"),
            EndpointProbe(Method.PATCH, "$API_CHANNELS/{sourceKey}"),
            EndpointProbe(Method.POST, API_CHANNELS_BATCH),
            EndpointProbe(Method.GET, API_EVENTS),
            EndpointProbe(Method.POST, API_IMPORTS),
            EndpointProbe(Method.GET, API_IMPORTS),
            EndpointProbe(Method.GET, "$API_IMPORTS/{id}"),
            EndpointProbe(Method.GET, API_GROUPS),
            EndpointProbe(Method.GET, API_SOURCES),
            EndpointProbe(Method.POST, "$API_SOURCES/delete"),
            EndpointProbe(Method.POST, "$API_SOURCES/refresh"),
            EndpointProbe(Method.POST, "$API_SOURCES/selection/{id}"),
            EndpointProbe(Method.GET, API_XMLTV_CATALOG),
        )

        internal fun error(code: String, message: String): JSONObject =
            JSONObject().put("error", code).put("message", message)

        /** Binds to the Wi-Fi IPv4 address when present, loopback otherwise. */
        internal fun hostnameForBinding(): String = wifiIpv4HostAddress() ?: "127.0.0.1"

        internal fun wifiIpv4HostAddress(): String? = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .filter { it.name.startsWith("wlan") || it.displayName?.startsWith("wlan") == true }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<java.net.Inet4Address>()
                .filterNot { it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
                .firstOrNull()
        }.getOrNull()
    }
}
