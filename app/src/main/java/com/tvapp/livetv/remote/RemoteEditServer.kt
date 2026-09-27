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

        /** Keyset-paged channel list (cursor = last sourceKey); JSON array. */
        fun channels(afterSourceKey: String?, limit: Int, query: String?): JSONArray

        fun groups(): JSONArray

        fun sources(): JSONArray
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
                method == Method.GET && (uri.isEmpty() || uri == "/" || uri == API_PING) ->
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
            method == Method.GET && uri == API_GROUPS ->
                json(StatusCode.OK, dataProvider.groups())

            method == Method.GET && uri == API_SOURCES ->
                json(StatusCode.OK, dataProvider.sources())

            else -> json(
                StatusCode.NOT_FOUND,
                error("not_found", "Bu sürümde bulunmayan uç nokta."),
            )
        }
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

    private fun handleChannels(session: IHTTPSession): Response {
        val parameters = session.parameters
        val after = parameters["after"]?.firstOrNull()?.trim()?.take(CURSOR_MAX_LENGTH)
            ?.takeIf(String::isNotEmpty)
        val query = parameters["q"]?.firstOrNull()?.trim()?.take(QUERY_MAX_LENGTH)
            ?.takeIf(String::isNotEmpty)
        val limit = (parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT)
            .coerceIn(1, MAX_PAGE_LIMIT)
        return json(
            StatusCode.OK,
            JSONObject()
                .put("channels", dataProvider.channels(after, limit, query))
                .put("limit", limit)
                .put("after", after ?: JSONObject.NULL),
        )
    }

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
        private const val API_GROUPS = "$API_ROOT/groups"
        private const val API_SOURCES = "$API_ROOT/sources"
        private const val DEFAULT_PAGE_LIMIT = 100
        private const val MAX_PAGE_LIMIT = 500
        private const val QUERY_MAX_LENGTH = 64
        private const val CURSOR_MAX_LENGTH = 128
        private const val DEVICE_NAME_MAX_LENGTH = 64

        /** Routes exposed for tests without starting a socket. */
        internal val ROUTES = listOf(
            EndpointProbe(Method.GET, API_PING),
            EndpointProbe(Method.POST, API_PAIR),
            EndpointProbe(Method.GET, API_CHANNELS),
            EndpointProbe(Method.GET, API_GROUPS),
            EndpointProbe(Method.GET, API_SOURCES),
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
