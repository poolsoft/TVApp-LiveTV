package com.tvapp.livetv.data

import com.tvapp.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class XmlTvMatcherTest {
    @Test
    fun matching_prefersTvgIdOverDisplayName() {
        val channel = channel("Completely Different", "trt1.tr")
        val byName = option("other", "Completely Different")
        val byId = option("trt1.tr", "TRT 1")

        val result = XmlTvMatcher.automaticResolution(channel, listOf(byName, byId))

        assertEquals(byId, result?.option)
        assertEquals(XmlTvMatcher.MatchType.ID, result?.type)
    }

    @Test
    fun matching_ignoresQualitySuffixAndPunctuationInName() {
        val result = XmlTvMatcher.automaticResolution(
            channel("TRT-1 HD", null),
            listOf(option("trt.one", "TRT 1")),
        )

        assertEquals(XmlTvMatcher.MatchType.NAME, result?.type)
    }

    @Test
    fun matching_ignoresCountrySuffixAfterQualityMarker() {
        val result = XmlTvMatcher.automaticResolution(
            channel("TRT 1 HD", null),
            listOf(option("TRT1 HD.tr", "TRT1 HD.tr")),
        )

        assertEquals(XmlTvMatcher.MatchType.NAME, result?.type)
        assertEquals("trt1", "TRT1 HD.tr".normalizeEpgKey())
    }

    private fun channel(name: String, epgId: String?) = LiveChannel(
        id = 1,
        sourceKey = "channel",
        inputId = "input",
        displayNumber = "1",
        displayName = name,
        uri = "uri",
        epgId = epgId,
    )

    private fun option(id: String, name: String) = XmlTvChannelOption(1, "EPG", id, name, 10)
}
