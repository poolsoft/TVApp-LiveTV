package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** XmlTvTime: XMLTV start/stop çözümlemenin sözleşmesini sabitler. */
class XmlTvTimeTest {
    @Test
    fun parse_acceptsUtcSuffixFormat() {
        // 2025-06-01 12:00:00 UTC = 1.748.779.200 sn
        assertEquals(1_748_779_200_000L, XmlTvTime.parse("20250601120000 +0000"))
    }

    @Test
    fun parse_appliesNumericOffset() {
        // 2025-06-01 12:00:00 +03:00 == 09:00 UTC
        assertEquals(1_748_768_400_000L, XmlTvTime.parse("20250601120000 +0300"))
    }

    @Test
    fun parse_acceptsCompactOffsetWithoutSpace() {
        assertEquals(1_748_768_400_000L, XmlTvTime.parse("20250601120000+0300"))
    }

    @Test
    fun parse_treatsMissingOffsetAsUtc() {
        // XMLTV belirtimi offset'i zorunlu kılmaz; eski SimpleDateFormat uygulaması
        // burada cihaz yereline düşüyordu, artık kararlı UTC davranışı var.
        assertEquals(1_748_779_200_000L, XmlTvTime.parse("20250601120000"))
    }

    @Test
    fun parse_returnsZeroForInvalidValues() {
        assertEquals(0L, XmlTvTime.parse(null))
        assertEquals(0L, XmlTvTime.parse(""))
        assertEquals(0L, XmlTvTime.parse("   "))
        assertEquals(0L, XmlTvTime.parse("geçersiz"))
        assertEquals(0L, XmlTvTime.parse("20251301120000 +0000"))
    }
}
