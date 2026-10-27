package com.tvapp.livetv.main

import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.vod.VodPageResolver
import com.tvapp.livetv.vod.WebVideoResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodHostTest {

    private val callbacks = object : VodHostCallbacks {
        override fun isVodMode(): Boolean = true
        override fun currentGeneration(): Int = 1
    }
    private val host = VodHost(callbacks)

    @Test
    fun classifiesLiveMode() {
        val result = host.classifyDeepLink(
            DeepLinkInput(liveMode = true, targetSource = "iptv:1"),
        )
        assertTrue(result is VodDeepLink.LiveMode)
        assertEquals("iptv:1", (result as VodDeepLink.LiveMode).targetSource)
    }

    @Test
    fun classifiesDirectStream() {
        val result = host.classifyDeepLink(
            DeepLinkInput(sourceKey = "k1", directStreamUrl = "https://cdn/x.mp4"),
        )
        assertTrue(result is VodDeepLink.DirectStream)
    }

    @Test
    fun classifiesMissingAsNone() {
        assertTrue(host.classifyDeepLink(DeepLinkInput()) is VodDeepLink.None)
        assertTrue(host.classifyDeepLink(null) is VodDeepLink.None)
    }

    @Test
    fun directStreamsSkipWebResolve() {
        assertFalse(host.needsWebResolve("https://cdn/x/movie.mp4"))
        assertFalse(host.needsWebResolve("https://cdn/x/master.m3u8"))
        assertTrue(host.needsWebResolve("https://example.com/watch/123"))
    }

    @Test
    fun emptyCandidatesPlayBase() {
        val base = testChannel("https://example.com/watch/1")
        val resolved = VodPageResolver.Resolved(emptyList())
        val decision = host.decideCandidates(base, resolved)
        assertTrue(decision is VodCandidateDecision.PlayBase)
    }

    @Test
    fun singleCandidatePlaysAppliedChannel() {
        val base = testChannel("https://example.com/watch/1")
        val resolved = VodPageResolver.Resolved(
            listOf(WebVideoResolver.ResolvedStream("https://cdn/final.mp4", referrer = "https://example.com/")),
        )
        val decision = host.decideCandidates(base, resolved)
        assertTrue(decision is VodCandidateDecision.PlayBase)
        val applied = (decision as VodCandidateDecision.PlayBase).channel
        assertEquals("https://cdn/final.mp4", applied.uri)
        assertEquals("https://example.com/", applied.referrer)
    }

    @Test
    fun multipleCandidatesShowChoice() {
        val base = testChannel("https://example.com/watch/1")
        val resolved = VodPageResolver.Resolved(
            listOf(
                WebVideoResolver.ResolvedStream("https://cdn/a.mp4"),
                WebVideoResolver.ResolvedStream("https://cdn/b.mp4"),
            ),
        )
        val decision = host.decideCandidates(base, resolved)
        assertTrue(decision is VodCandidateDecision.ShowChoice)
    }

    private fun testChannel(uri: String) = LiveChannel(
        id = 1L,
        sourceKey = "k1",
        inputId = "iptv:1",
        displayNumber = "VOD",
        displayName = "Test",
        source = LiveChannel.Source.IPTV,
        uri = uri,
        iptvContentType = "VOD",
    )
}
