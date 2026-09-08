package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvFtsQueryTest {
    @Test
    fun blankQueryDisablesFtsFilter() {
        assertEquals("", IptvFtsQuery.from("   "))
    }

    @Test
    fun wordsBecomePrefixTerms() {
        assertEquals("TRT* 1* HD*", IptvFtsQuery.from("TRT 1 HD"))
    }

    @Test
    fun punctuationCannotBecomeFtsSyntax() {
        assertEquals("spor* haber*", IptvFtsQuery.from("spor: haber*"))
    }

    @Test
    fun turkishLettersArePreserved() {
        assertEquals("ŞİMDİ* MÜZİK*", IptvFtsQuery.from("ŞİMDİ MÜZİK"))
    }
}
