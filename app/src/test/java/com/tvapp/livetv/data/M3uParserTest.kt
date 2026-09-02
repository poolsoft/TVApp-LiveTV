package com.tvapp.livetv.data

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesExtInfMetadataAndQuotedComma() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="trt1.tr" tvg-name="TRT 1 HD" tvg-logo="https://img/logo.png" group-title="Ulusal, HD",TRT 1
            https://example.com/live/trt1.m3u8
        """.trimIndent()

        val channel = M3uParser.parse(StringReader(playlist)).single()

        assertEquals("TRT 1", channel.name)
        assertEquals("trt1.tr", channel.tvgId)
        assertEquals("TRT 1 HD", channel.tvgName)
        assertEquals("https://img/logo.png", channel.logoUrl)
        assertEquals("Ulusal, HD", channel.groupTitle)
        assertEquals("https://example.com/live/trt1.m3u8", channel.streamUrl)
        assertEquals("LIVE", channel.contentType)
    }

    @Test
    fun classifiesFiniteDurationAndVodPaths() {
        val playlist = """
            #EXTM3U
            #EXTINF:5400 group-title="Filmler",Uzun Metraj
            https://example.com/archive/item.ts
            #EXTINF:-1,Dizi Bölümü
            https://example.com/series/episode-1.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(StringReader(playlist))

        assertEquals(listOf("VOD", "VOD"), channels.map { it.contentType })
    }

    @Test
    fun parsesVlcAndUrlHeadersWithUrlHeadersTakingPriority() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,SPOR TV
            #EXTGRP:Spor
            #EXTVLCOPT:http-user-agent=Playlist Agent
            #EXTVLCOPT:http-referrer=https://playlist.example/
            #EXTVLCOPT:sub-file=https://example.com/subtitles/tr.vtt
            https://example.com/live.m3u8|User-Agent=Channel%20Agent&Referer=https%3A%2F%2Fchannel.example%2F
        """.trimIndent()

        val channel = M3uParser.parse(StringReader(playlist)).single()

        assertEquals("SPOR TV", channel.name)
        assertEquals("Spor", channel.groupTitle)
        assertEquals("Channel Agent", channel.userAgent)
        assertEquals("https://channel.example/", channel.referrer)
        assertEquals("https://example.com/subtitles/tr.vtt", channel.subtitleUrl)
    }

    @Test
    fun ignoresUnsupportedLocationsAndCreatesFallbackName() {
        val playlist = """
            #EXTM3U
            udp://239.0.0.1:1234
            https://example.com/unnamed.ts
        """.trimIndent()

        val channel = M3uParser.parse(StringReader(playlist)).single()

        assertEquals("Kanal 2", channel.name)
        assertNull(channel.tvgId)
    }
}
