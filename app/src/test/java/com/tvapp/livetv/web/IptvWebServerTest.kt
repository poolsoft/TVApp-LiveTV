package com.tvapp.livetv.web

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class IptvWebServerTest {
    private lateinit var server: IptvWebServer
    private val commands = AtomicInteger()

    @Before fun start() {
        server = IptvWebServer(InetAddress.getByName("127.0.0.1"), "<html>TVApp</html>") { path, _ ->
            if (path == "/command") commands.incrementAndGet()
            WebReply(if (path == "/command") 202 else 200, "{\"sources\":[]}")
        }
        server.start()
    }
    @After fun stop() { server.close() }

    private fun request(path: String, body: String? = null, token: String? = null): Pair<Int, String> {
        val connection = URL(server.url + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 2000
        connection.readTimeout = 2000
        token?.let { connection.setRequestProperty("X-TVApp-Token", it) }
        if (body != null) {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status < 400) connection.inputStream else connection.errorStream
            return status to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally { connection.disconnect() }
    }
    private fun pair(): String {
        val response = request("/pair", JSONObject().put("code", server.pairingCode).toString())
        assertEquals(200, response.first)
        return JSONObject(response.second).getString("token")
    }

    // HttpURLConnection filters Origin/Host; security tests must send the original headers.
    private fun rawStatus(headers: String, body: String = ""): Int {
        val url = URL(server.url)
        return Socket(url.host, url.port).use { socket ->
            socket.soTimeout = 2000
            socket.getOutputStream().write((headers + "\r\n\r\n" + body).toByteArray())
            socket.getInputStream().bufferedReader().readLine().split(' ')[1].toInt()
        }
    }

    @Test fun pageIsPublicButSourceDataNeedsPairing() {
        assertTrue(request("/").second.contains("TVApp"))
        assertEquals(401, request("/state").first)
        assertEquals(200, request("/state", token = pair()).first)
    }
    @Test fun pairingCodeHasExactlyFourDigits() {
        assertTrue(server.pairingCode.matches(Regex("[0-9]{4}")))
        assertEquals(200, request("/state", token = pair()).first)
    }
    @Test fun partialPairingCodeIsRejected() {
        assertEquals(401, request("/pair", JSONObject().put("code", server.pairingCode.dropLast(1)).toString()).first)
    }
    @Test fun commandNeedsValidTokenAndPost() {
        assertEquals(401, request("/command", "{}").first)
        val token = pair()
        assertEquals(404, request("/command", token = token).first)
        assertEquals(202, request("/command", "{}", token).first)
        assertEquals(1, commands.get())
    }
    @Test fun crossOriginCommandIsRejected() {
        assertEquals(403, rawStatus("POST /command HTTP/1.1\r\nHost: ${server.url.removePrefix("http://")}\r\n" +
            "Origin: http://example.org\r\nX-TVApp-Token: ${pair()}\r\nContent-Type: application/json\r\nContent-Length: 2", "{}"))
        assertEquals(0, commands.get())
    }
    @Test fun foreignHostIsRejected() {
        assertEquals(403, rawStatus("GET / HTTP/1.1\r\nHost: example.org"))
    }
    @Test fun oversizedBodyIsRejectedBeforeReadingIt() {
        assertEquals(413, rawStatus("POST /pair HTTP/1.1\r\nHost: ${server.url.removePrefix("http://")}\r\n" +
            "Content-Type: application/json\r\nContent-Length: ${IptvWebServer.MAX_BODY_BYTES + 1}"))
    }
    @Test fun chunkedRequestsAreRejected() {
        assertEquals(403, rawStatus("POST /pair HTTP/1.1\r\nHost: ${server.url.removePrefix("http://")}\r\nTransfer-Encoding: chunked"))
    }
    @Test fun wrongPairingCodesAreRateLimited() {
        repeat(5) { assertEquals(401, request("/pair", "{\"code\":\"wrong\"}").first) }
        assertEquals(429, request("/pair", JSONObject().put("code", server.pairingCode).toString()).first)
    }
    @Test fun malformedJsonDoesNotExecuteCommand() {
        assertEquals(400, request("/command", "broken", pair()).first)
        assertEquals(0, commands.get())
    }
    @Test fun apiFailureDoesNotKillServerOrExposeMessage() {
        server.close()
        server = IptvWebServer(InetAddress.getByName("127.0.0.1"), "page") { _, _ -> error("secret") }
        server.start()
        val response = request("/state", token = pair())
        assertEquals(500, response.first)
        assertFalse(response.second.contains("secret"))
        assertEquals(200, request("/").first)
    }
    @Test fun restartingInvalidatesOldToken() {
        val oldToken = pair()
        server.close()
        start()
        assertEquals(401, request("/state", token = oldToken).first)
    }
}
