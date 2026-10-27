package com.tvapp.livetv.main

import com.tvapp.livetv.data.local.VodMetadataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodLibraryResolverTest {

    private fun meta(
        sourceKey: String = "series1",
        kind: String = "SERIES",
        parentKey: String? = null,
        streamUrl: String? = "[{\"episode\":2,\"videoUrl\":\"https://cdn/f2.mp4\"}]",
    ) = VodMetadataEntity(
        sourceKey = sourceKey,
        sourceId = 1L,
        kind = kind,
        providerId = "p1",
        parentKey = parentKey,
        seasonNumber = 1,
        episodeNumber = 2,
        name = "Dizi",
        streamUrl = streamUrl,
        updatedAt = 0L,
    )

    @Test
    fun resolvesDynamicEpisode() {
        val parent = meta()
        val resolved = VodLibraryResolver.resolveDynamicEpisode("series1:ep:0_123", null, parent)
        assertEquals("1. Sezon 2. Bolum", resolved?.dynamicEpTitle)
        assertEquals("https://cdn/f2.mp4", resolved?.meta?.streamUrl)
        assertTrue(resolved?.hasEpisodes == true)
    }

    @Test
    fun ignoresNonEpisodeKeys() {
        assertNull(VodLibraryResolver.resolveDynamicEpisode("series1", null, meta()))
        assertNull(VodLibraryResolver.resolveDynamicEpisode("series1:ep:0_1", meta(), null))
        assertNull(VodLibraryResolver.resolveDynamicEpisode("x:ep:9_1", null, meta(streamUrl = "[{}]")))
    }

    @Test
    fun hasEpisodesRule() {
        assertTrue(VodLibraryResolver.hasEpisodes(meta(kind = "SERIES"), "series1"))
        assertTrue(VodLibraryResolver.hasEpisodes(meta(kind = "MOVIE"), "m:ep:0_1"))
        assertFalse(VodLibraryResolver.hasEpisodes(meta(kind = "MOVIE", streamUrl = "https://cdn/f.mp4"), "m1"))
        assertFalse(VodLibraryResolver.hasEpisodes(null, "m1"))
    }

    @Test
    fun titlesPreferParentAndDynamicEpisode() {
        val (main, sub) = VodLibraryResolver.titles(meta(), "Ana Dizi", "1. Sezon 2. Bolum") { s, e -> "$s/$e" }
        assertEquals("Ana Dizi", main)
        assertEquals("1. Sezon 2. Bolum", sub)
    }

    @Test
    fun titlesFormatSeasonEpisode() {
        val (main, sub) = VodLibraryResolver.titles(meta(), "Ana Dizi", null) { s, e -> "$s. Sezon $e. Bolum" }
        assertEquals("Ana Dizi", main)
        assertEquals("1. Sezon 2. Bolum", sub)
    }

    @Test
    fun titlesWithoutParentHaveNoSubtitle() {
        val (main, sub) = VodLibraryResolver.titles(meta(kind = "MOVIE"), null, null) { s, e -> "$s/$e" }
        assertEquals("Dizi", main)
        assertNull(sub)
    }

    @Test
    fun buildsChannelWithVodFields() {
        val channel = VodLibraryResolver.buildChannel(meta(sourceKey = "k1"), "Ana", "Alt")
        assertEquals("k1", channel.sourceKey)
        assertEquals("iptv:1", channel.inputId)
        assertEquals("VOD", channel.displayNumber)
        assertEquals("Ana", channel.displayName)
        assertEquals("Alt", channel.episodeTitle)
        assertEquals("VOD", channel.iptvContentType)
    }
}
