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
        assertEquals(XmlTvMatcher.MatchType.EXACT_ID, result?.type)
    }

    @Test
    fun matching_usesRelaxedQualityMatchWhenThereIsOnlyOneCandidate() {
        val result = XmlTvMatcher.automaticResolution(
            channel("TRT-1 HD", null),
            listOf(option("trt.one", "TRT 1")),
        )

        assertEquals(XmlTvMatcher.MatchType.NORMALIZED_NAME, result?.type)
    }

    @Test
    fun matching_doesNotCollapseDistinctQualityVariants() {
        val result = XmlTvMatcher.automaticResolution(
            channel("TRT 1", null),
            listOf(
                option("trt1", "TRT 1"),
                option("trt1hd", "TRT 1 HD"),
            ),
        )

        assertEquals("trt1", result?.option?.channelId)
    }

    @Test
    fun relaxedMatchingRejectsAmbiguousQualityVariants() {
        val result = XmlTvMatcher.automaticResolution(
            channel("TRT 1 UHD", null),
            listOf(
                option("trt1", "TRT 1"),
                option("trt1hd", "TRT 1 HD"),
            ),
        )

        assertEquals(null, result)
    }

    @Test
    fun matching_ignoresCountrySuffixAfterQualityMarker() {
        val result = XmlTvMatcher.automaticResolution(
            channel("TRT 1 HD", null),
            listOf(option("TRT1 HD.tr", "TRT1 HD.tr")),
        )

        assertEquals(XmlTvMatcher.MatchType.NORMALIZED_NAME, result?.type)
        assertEquals("trt1", "TRT1 HD.tr".normalizeEpgKey())
        assertEquals("trt1hd", "TRT1 HD.tr".normalizeExactEpgKey())
    }

    @Test
    fun matching_reportsExactNameSeparatelyFromNormalizedName() {
        val result = XmlTvMatcher.automaticResolution(
            channel("TRT 1 HD", null),
            listOf(option("unrelated", "trt 1 hd")),
        )

        assertEquals(XmlTvMatcher.MatchType.EXACT_NAME, result?.type)
    }

    @Test
    fun exactMatchingRejectsDuplicateCandidates() {
        val result = XmlTvMatcher.automaticResolution(
            channel("TRT 1 HD", null),
            listOf(
                XmlTvChannelOption(1, "EPG 1", "one", "TRT 1 HD", 10),
                XmlTvChannelOption(2, "EPG 2", "two", "TRT 1 HD", 10),
            ),
        )

        assertEquals(null, result)
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
