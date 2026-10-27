package com.tvapp.livetv.main

import com.tvapp.livetv.data.local.VodMetadataEntity
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.vod.VodPageResolver
import com.tvapp.livetv.vod.WebVideoResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodHostTest {

    /** Cagrilari kaydeden sahte ortam; gercek Activity'ye dokunmaz. */
    private class FakeEnv : VodHostEnv {
        val events = mutableListOf<String>()
        var vodMode = true
        var generation = 1
        var currentKey: String? = null
        var tunedReady = false
        var selected: LiveChannel? = null
        var hasEpisodes: Boolean? = null
        var channelNotFound = false
        var choiceShown = false
        var bufferingHidden = false
        var resolvingHidden = false
        var lastVodCleared = false
        var libraryChannel: LiveChannel? = null
        var metadata: VodMetadataEntity? = null
        var resolved: VodPageResolver.Resolved = VodPageResolver.Resolved(emptyList())

        override fun isVodMode() = vodMode
        override fun currentGeneration() = generation
        override fun beginGeneration(): Int = ++generation
        override fun enterVodMode() { events += "enterVod" }
        override fun switchToLiveMode(targetSource: String?) { events += "live:$targetSource" }
        override fun showVodPlayerControls(autoHide: Boolean) { events += "controls:$autoHide" }
        override fun saveResumePosition() { events += "saveResume" }
        override fun stopIptvPlayback() { events += "stop" }
        override fun resumeIptvPlayback() { events += "resume" }
        override fun clearResume(sourceKey: String) { events += "clear:$sourceKey" }
        override fun setVodHasEpisodes(hasEpisodes: Boolean) { this.hasEpisodes = hasEpisodes }
        override fun currentChannelKey(): String? = currentKey
        override fun isTunedAndReady(sourceKey: String) = tunedReady
        override fun selectChannel(channel: LiveChannel) { selected = channel }
        override fun showResolvingFeedback() { events += "resolving" }
        override fun hideResolvingFeedback() { resolvingHidden = true }
        override fun showBufferingPreparing() { events += "buffering" }
        override fun hideBuffering() { bufferingHidden = true }
        override fun string(resId: Int) = "str:$resId"
        override fun seasonEpisodeLabel(season: Int, episode: Int) = "$season. Sezon $episode. Bolum"
        override fun showStreamChoice(resolved: VodPageResolver.Resolved, baseChannel: LiveChannel) {
            choiceShown = true
        }
        override fun showChannelNotFound() { channelNotFound = true }
        override fun clearLastVodKey() { lastVodCleared = true }
        override fun log(message: String) { events += "log:$message" }
        override fun findChannelInList(sourceKey: String): LiveChannel? = null
        override suspend fun loadIptvChannel(sourceKey: String): LiveChannel? = null
        override suspend fun loadMetadata(sourceKey: String): VodMetadataEntity? = metadata
        override suspend fun loadParentName(parentKey: String): String? = null
        override suspend fun resolvePageWithContext(url: String): VodPageResolver.Resolved = resolved
    }

    private fun newHost(env: FakeEnv) =
        VodHost(CoroutineScope(Dispatchers.Unconfined + Job()), env)

    private fun testChannel(uri: String, key: String = "k1") = LiveChannel(
        id = 1L,
        sourceKey = key,
        inputId = "iptv:1",
        displayNumber = "VOD",
        displayName = "Test",
        source = LiveChannel.Source.IPTV,
        uri = uri,
        iptvContentType = "VOD",
    )

    @Test
    fun classifiesLiveMode() {
        val host = newHost(FakeEnv())
        val result = host.classifyDeepLink(VodDeepLinkPayload(liveMode = true, targetSource = "iptv:1"))
        assertTrue(result is VodDeepLink.LiveMode)
        assertEquals("iptv:1", (result as VodDeepLink.LiveMode).targetSource)
    }

    @Test
    fun classifiesDirectStream() {
        val host = newHost(FakeEnv())
        val result = host.classifyDeepLink(
            VodDeepLinkPayload(sourceKey = "k1", streamUrl = "https://cdn/x.mp4"),
        )
        assertTrue(result is VodDeepLink.DirectStream)
    }

    @Test
    fun classifiesMissingAsNone() {
        val host = newHost(FakeEnv())
        assertTrue(host.classifyDeepLink(VodDeepLinkPayload()) is VodDeepLink.None)
        assertTrue(host.classifyDeepLink(null) is VodDeepLink.None)
    }

    @Test
    fun directStreamsSkipWebResolve() {
        val host = newHost(FakeEnv())
        assertFalse(host.needsWebResolve("https://cdn/x/movie.mp4"))
        assertFalse(host.needsWebResolve("https://cdn/x/master.m3u8"))
        assertTrue(host.needsWebResolve("https://example.com/watch/123"))
    }

    @Test
    fun emptyCandidatesPlayBase() {
        val host = newHost(FakeEnv())
        val decision = host.decideCandidates(
            testChannel("https://example.com/watch/1"),
            VodPageResolver.Resolved(emptyList()),
        )
        assertTrue(decision is VodCandidateDecision.PlayBase)
    }

    @Test
    fun singleCandidatePlaysAppliedChannel() {
        val host = newHost(FakeEnv())
        val resolved = VodPageResolver.Resolved(
            listOf(WebVideoResolver.ResolvedStream("https://cdn/final.mp4", referrer = "https://example.com/")),
        )
        val decision = host.decideCandidates(testChannel("https://example.com/watch/1"), resolved)
        assertTrue(decision is VodCandidateDecision.PlayBase)
        val applied = (decision as VodCandidateDecision.PlayBase).channel
        assertEquals("https://cdn/final.mp4", applied.uri)
        assertEquals("https://example.com/", applied.referrer)
    }

    @Test
    fun multipleCandidatesShowChoice() {
        val host = newHost(FakeEnv())
        val resolved = VodPageResolver.Resolved(
            listOf(
                WebVideoResolver.ResolvedStream("https://cdn/a.mp4"),
                WebVideoResolver.ResolvedStream("https://cdn/b.mp4"),
            ),
        )
        assertTrue(host.decideCandidates(testChannel("https://x/1"), resolved) is VodCandidateDecision.ShowChoice)
    }

    @Test
    fun libraryPathWithoutMetadataShowsNotFound() {
        val env = FakeEnv()
        val host = newHost(env)
        runBlocking {
            host.handlePayload(VodDeepLinkPayload(sourceKey = "k1"))
            host.awaitResolve()
        }
        assertTrue(env.events.contains("enterVod"))
        assertTrue(env.events.contains("buffering"))
        assertTrue(env.bufferingHidden)
        assertTrue(env.lastVodCleared)
        assertTrue(env.channelNotFound)
        assertNull(env.selected)
    }

    @Test
    fun libraryPathWithMetadataSelectsChannel() {
        val env = FakeEnv()
        env.metadata = VodMetadataEntity(
            sourceKey = "k1",
            sourceId = 2L,
            kind = "MOVIE",
            providerId = "p",
            name = "Film",
            streamUrl = "https://cdn/film.mp4",
            updatedAt = 0L,
        )
        val host = newHost(env)
        runBlocking {
            host.handlePayload(VodDeepLinkPayload(sourceKey = "k1"))
            host.awaitResolve()
        }
        assertEquals("Film", env.selected?.displayName)
        assertEquals("https://cdn/film.mp4", env.selected?.uri)
        assertEquals(false, env.hasEpisodes)
    }

    @Test
    fun cancelResolveIsSafeWithoutJob() {
        val host = newHost(FakeEnv())
        host.cancelResolve()
        host.cancelResolve()
    }
}
