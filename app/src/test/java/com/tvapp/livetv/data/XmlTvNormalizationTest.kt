package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** normalizeEpgKey'in sözleşmesini korur; kalıplar artık önceden derlenir. */
class XmlTvNormalizationTest {
    @Test
    fun normalizeEpgKey_stripsQualityAndCountrySuffixes() {
        assertEquals("trt1", "TRT-1 HD.tr".normalizeEpgKey())
        assertEquals("trt1", "trt 1.fhd".normalizeEpgKey())
        assertEquals("startv", "Star TV UHD".normalizeEpgKey())
        assertEquals("kanald", "Kanal D_SD".normalizeEpgKey())
        assertEquals("atv", "ATV 4K".normalizeEpgKey())
    }

    @Test
    fun normalizeEpgKey_stripsSeparatorsAndCase() {
        assertEquals("beinsports1", "Bein Sports 1".normalizeEpgKey())
        assertEquals("fox", "FOX!".normalizeEpgKey())
        assertEquals("tv8", "  TV8\t".normalizeEpgKey())
    }

    @Test
    fun normalizeExactEpgKey_keepsQualityMarkers() {
        assertEquals("trt1hd", "TRT 1 HD".normalizeExactEpgKey())
        assertEquals("trt1hd", "TRT1 HD.tr".normalizeExactEpgKey())
    }

    @Test
    fun normalizeEpgKey_isIdempotent() {
        val once = "Show TV HD.tr".normalizeEpgKey()
        assertEquals(once, once.normalizeEpgKey())
    }
}
