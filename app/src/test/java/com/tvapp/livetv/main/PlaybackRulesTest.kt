package com.tvapp.livetv.main

import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRulesTest {

    private fun iptvChannel(
        key: String = "iptv:1",
        contentType: String? = "LIVE",
        uri: String = "https://cdn/live.m3u8",
    ) = LiveChannel(
        id = 1L,
        sourceKey = key,
        inputId = "iptv",
        displayNumber = "1",
        displayName = "Kanal",
        uri = uri,
        iptvContentType = contentType,
        source = LiveChannel.Source.IPTV,
    )

    @Test
    fun vodModeBlocksLiveChannels() {
        assertTrue(PlaybackRules.blockedByVodMode(true, iptvChannel(contentType = "LIVE")))
        assertFalse(PlaybackRules.blockedByVodMode(true, iptvChannel(contentType = "VOD")))
        assertFalse(PlaybackRules.blockedByVodMode(false, iptvChannel(contentType = "LIVE")))
    }

    @Test
    fun vodModeBlocksTifChannels() {
        val tif = iptvChannel().copy(source = LiveChannel.Source.TIF)
        assertTrue(PlaybackRules.blockedByVodMode(true, tif))
    }

    @Test
    fun repeatSelectIgnoredWhilePreparingOrBuffering() {
        val channel = iptvChannel()
        assertTrue(
            PlaybackRules.ignoreRepeatSelect(channel, "iptv:1", IptvPlaybackPhase.PREPARING),
        )
        assertTrue(
            PlaybackRules.ignoreRepeatSelect(channel, "iptv:1", IptvPlaybackPhase.BUFFERING),
        )
    }

    @Test
    fun repeatSelectAllowedWhenReadyOrOtherChannel() {
        val channel = iptvChannel()
        assertFalse(PlaybackRules.ignoreRepeatSelect(channel, "iptv:1", IptvPlaybackPhase.READY))
        assertFalse(PlaybackRules.ignoreRepeatSelect(channel, "iptv:1", IptvPlaybackPhase.FAILED))
        assertFalse(PlaybackRules.ignoreRepeatSelect(channel, "iptv:2", IptvPlaybackPhase.PREPARING))
        assertFalse(PlaybackRules.ignoreRepeatSelect(channel, null, IptvPlaybackPhase.PREPARING))
    }

    @Test
    fun vodResumePositionReadsStore() {
        val channel = iptvChannel(contentType = "VOD")
        assertEquals(42_000L, PlaybackRules.resumePosition(channel) { 42_000L })
    }

    @Test
    fun liveResumePositionIsZeroAndDoesNotReadStore() {
        var read = false
        val position = PlaybackRules.resumePosition(iptvChannel(contentType = "LIVE")) {
            read = true
            99_000L
        }
        assertEquals(0L, position)
        assertFalse(read)
    }

    @Test
    fun alternativeStartPositionKeepsVodOffsetOnly() {
        assertEquals(12_000L, PlaybackRules.alternativeStartPosition(iptvChannel(contentType = "VOD"), 12_000L))
        assertEquals(0L, PlaybackRules.alternativeStartPosition(iptvChannel(contentType = "LIVE"), 12_000L))
    }

    @Test
    fun cachedAlternativesUsedOnlyForSameChannel() {
        val cached = listOf(iptvChannel(key = "alt"))
        assertSame(
            cached,
            PlaybackRules.cachedAlternatives("iptv:1", "iptv:1", cached),
        )
        assertNull(PlaybackRules.cachedAlternatives("iptv:2", "iptv:1", cached))
        assertNull(PlaybackRules.cachedAlternatives("iptv:1", "iptv:1", emptyList()))
        assertNull(PlaybackRules.cachedAlternatives(null, "iptv:1", cached))
    }

    @Test
    fun nextAlternativeAdvancesIndex() {
        val first = iptvChannel(key = "alt1")
        val second = iptvChannel(key = "alt2")
        val alternatives = listOf(first, second)

        val choice = PlaybackRules.nextAlternative(alternatives, 0)
        assertEquals(first, choice?.channel)
        assertEquals(1, choice?.nextIndex)

        assertEquals(second, PlaybackRules.nextAlternative(alternatives, 1)?.channel)
        assertNull(PlaybackRules.nextAlternative(alternatives, 2))
    }

    @Test
    fun applyAlternativeKeepsIdentityAndTakesPlaybackFields() {
        val channel = iptvChannel(key = "iptv:1", contentType = "VOD")
        val alternative = iptvChannel(
            key = "alt",
            contentType = "VOD",
            uri = "https://cdn/alt.m3u8",
        ).copy(
            inputId = "iptv-alt",
            userAgent = "UA",
            referrer = "https://ref/",
            origin = "https://origin/",
            subtitleUrl = "https://sub.vtt",
        )

        val result = PlaybackRules.applyAlternative(channel, alternative)

        assertEquals("iptv:1", result.sourceKey)
        assertEquals("Kanal", result.displayName)
        assertEquals("VOD", result.iptvContentType)
        assertEquals("https://cdn/alt.m3u8", result.uri)
        assertEquals("iptv-alt", result.inputId)
        assertEquals("UA", result.userAgent)
        assertEquals("https://ref/", result.referrer)
        assertEquals("https://origin/", result.origin)
        assertEquals("https://sub.vtt", result.subtitleUrl)
    }
}
