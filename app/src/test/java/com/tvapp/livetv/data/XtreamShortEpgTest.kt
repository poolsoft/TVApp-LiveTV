package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone

class XtreamShortEpgTest {

    private fun base64Of(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    @Test
    fun `decodeListingField decodes utf8 base64`() {
        val title = "Şampiyonlar Ligi Özetleri"
        assertEquals(title, XtreamClient.decodeListingField(base64Of(title)))
    }

    @Test
    fun `decodeListingField keeps raw text when base64 is invalid`() {
        assertEquals("Haber Bülteni", XtreamClient.decodeListingField("Haber Bülteni"))
    }

    @Test
    fun `decodeListingField returns empty for blank input`() {
        assertEquals("", XtreamClient.decodeListingField("   "))
        assertEquals("", XtreamClient.decodeListingField(""))
    }

    @Test
    fun `streamIdFromHttpUrl extracts numeric id from live url`() {
        assertEquals(
            "12345",
            XtreamClient.streamIdFromHttpUrl("http://server:8080/live/user/pass/12345.ts"),
        )
        assertEquals(
            "678",
            XtreamClient.streamIdFromHttpUrl("https://server/live/u/p/678.m3u8"),
        )
        assertEquals(
            "42",
            XtreamClient.streamIdFromHttpUrl("http://server/live/u/p/42.ts?token=abc"),
        )
    }

    @Test
    fun `streamIdFromHttpUrl rejects non live urls`() {
        assertNull(XtreamClient.streamIdFromHttpUrl("http://server/movie/u/p/55.mp4"))
        assertNull(XtreamClient.streamIdFromHttpUrl("http://server/series/u/p/77.mkv"))
        assertNull(XtreamClient.streamIdFromHttpUrl("http://server/playlist.m3u"))
        assertNull(XtreamClient.streamIdFromHttpUrl("http://server/live/u/p/channel-one.ts"))
        assertNull(XtreamClient.streamIdFromHttpUrl("http://server/live/u/p/"))
        assertNull(XtreamClient.streamIdFromHttpUrl(""))
    }

    @Test
    fun `resolveListingTimes uses unix timestamps when present`() {
        val times = XtreamClient.resolveListingTimes(1_700_000_000L, 1_700_003_600L, "", "")
        assertEquals(1_700_000_000_000L, times!!.first)
        assertEquals(1_700_003_600_000L, times.second)
    }

    @Test
    fun `resolveListingTimes falls back to utc date text`() {
        val times = XtreamClient.resolveListingTimes(
            0L,
            0L,
            "2026-09-04 20:00:00",
            "2026-09-04 21:30:00",
        )!!
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        assertEquals(format.parse("2026-09-04 20:00:00")!!.time, times.first)
        assertEquals(format.parse("2026-09-04 21:30:00")!!.time, times.second)
    }

    @Test
    fun `resolveListingTimes rejects invalid ranges`() {
        assertNull(XtreamClient.resolveListingTimes(2_000L, 1_000L, "", ""))
        assertNull(XtreamClient.resolveListingTimes(0L, 0L, "", ""))
        assertNull(XtreamClient.resolveListingTimes(0L, 0L, "gecersiz", "yine gecersiz"))
        assertNull(XtreamClient.resolveListingTimes(1_000L, 0L, "", "metin degil"))
    }

    @Test
    fun `resolveListingTimes mixes timestamp and text`() {
        val times = XtreamClient.resolveListingTimes(
            1_700_000_000L,
            0L,
            "",
            "2023-11-14 22:30:00",
        )
        assertTrue(times != null)
        assertTrue(times!!.second > times.first)
    }
}
