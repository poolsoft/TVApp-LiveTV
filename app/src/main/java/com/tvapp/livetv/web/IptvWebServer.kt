package com.tvapp.livetv.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections

/** Screen-scoped LAN server. No credentials or user data are served before pairing. */
class IptvWebServer(
    address: InetAddress,
    private val page: String,
    private val onPaired: () -> Unit = {},
    private val api: suspend (String, JSONObject) -> WebReply,
) : Closeable {
    private val random = SecureRandom()
    val pairingCode = String.format(java.util.Locale.ROOT, "%04d", random.nextInt(10_000))
    private val token = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    private val server = bindServerSocket(address)
    val url = "http://${address.hostAddress}:${server.localPort}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val slots = Semaphore(2)
    private var badCodes = 0
    private var blockedUntil = 0L

    fun start() {
        scope.launch {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (_: IOException) { break }
                if (!slots.tryAcquire()) { client.close(); continue }
                clients.add(client)
                scope.launch {
                    try { client.use { handle(it) } } catch (_: IOException) {
                        // Disconnects and malformed requests do not expose request contents in logs.
                    } finally { clients.remove(client); slots.release() }
                }
            }
        }
    }

    private suspend fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        var headerBytes = 0
        fun line(): String {
            val bytes = ArrayList<Byte>()
            while (true) {
                val next = input.read()
                if (next < 0) throw IOException("Truncated request")
                if (++headerBytes > 16_384) throw IOException("Headers too large")
                if (next == 10) return bytes.toByteArray().toString(Charsets.US_ASCII).removeSuffix("\r")
                bytes.add(next.toByte())
            }
        }
        val request = line().split(' ')
        if (request.size != 3) return respond(socket, WebReply(400))
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = line()
            if (line.isEmpty()) break
            val key = line.substringBefore(':').lowercase(java.util.Locale.ROOT)
            if (!line.contains(':') || headers.put(key, line.substringAfter(':').trim()) != null) {
                return respond(socket, WebReply(400))
            }
        }
        if (headers["host"] != url.removePrefix("http://") ||
            headers["origin"]?.let { it != url } == true || headers.containsKey("transfer-encoding")) {
            return respond(socket, WebReply(403))
        }
        val method = request[0]
        val path = request[1]
        if (method == "GET" && path == "/") {
            return respond(socket, WebReply(200, page, "text/html; charset=utf-8"))
        }
        if (path != "/pair" && !MessageDigest.isEqual(
                headers["x-tvapp-token"].orEmpty().toByteArray(), token.toByteArray(),
            )) return respond(socket, WebReply(401))
        if (method == "GET" && path == "/state") return respond(socket, safeApi(path, JSONObject()))
        if (method != "POST" || path !in listOf("/pair", "/command")) return respond(socket, WebReply(404))
        if (headers["content-type"]?.substringBefore(';') != "application/json") return respond(socket, WebReply(415))
        val length = headers["content-length"]?.toIntOrNull()
        val limit = if (path == "/pair") 256 else MAX_BODY_BYTES
        if (length == null || length !in 1..limit) return respond(socket, WebReply(413))
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) throw IOException("Truncated body")
            read += count
        }
        val json = try { JSONObject(body.toString(Charsets.UTF_8)) } catch (_: Exception) {
            return respond(socket, WebReply(400))
        }
        if (path == "/pair") {
            val reply = synchronized(this) {
                val now = System.nanoTime()
                when {
                    now < blockedUntil -> WebReply(429)
                    !MessageDigest.isEqual(json.optString("code").toByteArray(), pairingCode.toByteArray()) -> {
                        if (++badCodes >= 5) { badCodes = 0; blockedUntil = now + 60_000_000_000L }
                        WebReply(401)
                    }
                    else -> {
                        badCodes = 0
                        onPaired()
                        WebReply(200, JSONObject().put("token", token).toString())
                    }
                }
            }
            return respond(socket, reply)
        }
        respond(socket, safeApi(path, json))
    }

    private suspend fun safeApi(path: String, data: JSONObject): WebReply = try { api(path, data)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
    } catch (_: Exception) { WebReply(500) }

    private fun respond(socket: Socket, reply: WebReply) {
        val bytes = reply.body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 ${reply.status} Response\r\nContent-Type: ${reply.contentType}\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n" +
            "Content-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }

    override fun close() {
        server.close()
        synchronized(clients) { clients.forEach { runCatching { it.close() } }; clients.clear() }
        scope.cancel()
    }

    companion object {
        const val MAX_BODY_BYTES = 16 * 1024 * 1024
        private val PREFERRED_PORTS = intArrayOf(8050, 8060, 8070)

        private fun bindServerSocket(address: InetAddress): ServerSocket {
            for (port in PREFERRED_PORTS) {
                try {
                    return ServerSocket(port, 8, address)
                } catch (_: IOException) {
                    // Port already in use, try next
                }
            }
            return ServerSocket(0, 8, address)
        }
    }
}

data class WebReply(val status: Int, val body: String = "{}", val contentType: String = "application/json; charset=utf-8")
