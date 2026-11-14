package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class HttpBodyReaderTest {
    @Test
    fun readsWholeBodyWithinLimit() {
        val body = """{"user_info":{"auth":1}}"""

        val text = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
            .readLimitedText(64, "Xtream hesap")

        assertEquals(body, text)
    }

    @Test
    fun keepsMultiByteCharactersIntact() {
        val body = "Şifre doğrulandı — Türkçe karakterler korunur"
        val bytes = body.toByteArray(Charsets.UTF_8)

        val text = ByteArrayInputStream(bytes).readLimitedText(bytes.size, "Stalker portal")

        assertEquals(body, text)
    }

    @Test
    fun returnsEmptyStringForEmptyResponse() {
        assertEquals("", ByteArrayInputStream(ByteArray(0)).readLimitedText(64, "Manifest"))
    }

    @Test
    fun failsWithNamedLimitInsteadOfReturningTruncatedBody() {
        val body = ByteArray(4096) { 'a'.code.toByte() }

        val failure = runCatching {
            ByteArrayInputStream(body).readLimitedText(1024, "Kaynak manifesti")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        val message = failure?.message.orEmpty()
        assertTrue(message, message.contains("Kaynak manifesti"))
        assertTrue(message, message.contains("1 KB"))
    }

    @Test
    fun stopsReadingAtLimitWithoutBufferingTheWholeBody() {
        val huge = ByteArray(8 * 1024 * 1024) { 'b'.code.toByte() }
        var consumed = 0
        val counting = object : java.io.FilterInputStream(ByteArrayInputStream(huge)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = super.read(b, off, len)
                if (count > 0) consumed += count
                return count
            }
        }

        val failure = runCatching {
            counting.readLimitedText(64 * 1024, "Kaynak manifesti")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(
            "consumed $consumed bytes for a 65536 byte limit",
            consumed <= 64 * 1024 + DEFAULT_BUFFER_SIZE,
        )
    }
}
