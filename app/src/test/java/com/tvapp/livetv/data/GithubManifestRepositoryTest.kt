package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubManifestRepositoryTest {

    @Test
    fun parseManifest_parsesValidItems() {
        val repo = GithubManifestRepository()
        val json = """
            [
              {
                "kaynak": "m3u8",
                "name": "TR Kanallar",
                "url": "https://example.com/live.m3u8",
                "poster": "https://example.com/logo.png",
                "description": "Ulusal kanallar"
              },
              {
                "kaynak": "json",
                "name": "Film Paketi",
                "url": "https://example.com/vod.json",
                "description": "Populer filmler"
              },
              {
                "name": "Eksik Url",
                "url": ""
              }
            ]
        """.trimIndent()

        val items = repo.parseManifest(json)
        assertEquals(2, items.size)

        assertEquals("TR Kanallar", items[0].name)
        assertEquals("https://example.com/live.m3u8", items[0].url)
        assertEquals("https://example.com/logo.png", items[0].poster)
        assertEquals("Ulusal kanallar", items[0].description)
        assertEquals("m3u8", items[0].sourceFormat)

        assertEquals("Film Paketi", items[1].name)
        assertEquals("https://example.com/vod.json", items[1].url)
        assertEquals(null, items[1].poster)
        assertEquals("Populer filmler", items[1].description)
        assertEquals("json", items[1].sourceFormat)
    }

    @Test
    fun parseManifest_emptyArrayReturnsEmptyList() {
        val repo = GithubManifestRepository()
        val items = repo.parseManifest("[]")
        assertTrue(items.isEmpty())
    }
}
