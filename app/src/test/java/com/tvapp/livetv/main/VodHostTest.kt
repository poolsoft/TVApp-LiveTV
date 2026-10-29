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
import org.junit.Assert.assertNotNull
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
        var active: LiveChannel? = null
        var episodes: List<VodMetadataEntity> = emptyList()
        var pickerTitle: String? = null
        var pickerLabels: List<String> = emptyList()
        var pickerIndex: Int = -1
        var pickerPick: ((Int) -> Unit)? = null

        override fun isVodMode() = vodMode
        override fun currentGeneration() = generation
        override fun beginGeneration(): Int = ++generation
        override fun enterVodMode() { events += "enterVod" }
        override fun switchToLiveMode(targetSource: String?) { events += "live:$targetSource" }
        override fun setPendingVodTitle(title: String?, episodeTitle: String?) {
            events += "pendingTitle:$title"
        }
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
        override fun activeChannel(): LiveChannel? = active
        override fun openVodLibrary() { events += "library" }
        override fun playVodKey(sourceKey: String) { events += "play:$sourceKey" }
        override fun playVodDeepLink(payload: VodDeepLinkPayload) { events += "payload:${payload.sourceKey}" }
        override fun showEpisodeChoices(
            title: String,
            labels: List<String>,
            selectedIndex: Int,
            onPick: (Int) -> Unit,
        ) {
            pickerTitle = title
            pickerLabels = labels
            pickerIndex = selectedIndex
            pickerPick = onPick
        }
        var playbackError: Pair<LiveChannel?, String>? = null
        override fun showPlaybackError(channel: LiveChannel?, message: String) {
            playbackError = channel to message
            events += "error:$message"
        }
        override suspend fun loadSeriesEpisodes(seriesKey: String): List<VodMetadataEntity> = episodes
        var fallbackToBrowserEnabled = false
        var openedInBrowserUrl: String? = null
        var promptedFallbackUrl: String? = null
        override fun isVodFallbackToBrowserEnabled(): Boolean = fallbackToBrowserEnabled
        override fun openVodInBrowser(url: String, title: String?) {
            openedInBrowserUrl = url
            events += "browser:$url"
        }
        override fun promptVodBrowserFallback(url: String, title: String?) {
            promptedFallbackUrl = url
            events += "prompt_browser:$url"
        }
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
    fun emptyCandidatesFailsForWebPage() {
        val host = newHost(FakeEnv())
        val decision = host.decideCandidates(
            testChannel("https://example.com/watch/1"),
            VodPageResolver.Resolved(emptyList()),
        )
        assertTrue(decision is VodCandidateDecision.Failure)
    }

    @Test
    fun directStreamWithEmptyCandidatesPlaysBase() {
        val host = newHost(FakeEnv())
        val decision = host.decideCandidates(
            testChannel("https://cdn.example.com/video.mp4"),
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

    @Test
    fun episodePickerWithoutActiveChannelReturnsFalse() {
        val env = FakeEnv()
        assertFalse(newHost(env).showEpisodePicker())
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun episodePickerWithDynamicEpisodeResolvesParentSeries() {
        val env = FakeEnv()
        env.active = testChannel("https://cdn/a.mp4", key = "series1:ep:0_12345")
        env.episodes = listOf(
            metaRecord("e1", kind = "EPISODE", parentKey = "series1", episode = 1, name = "Pilot"),
            metaRecord("e2", kind = "EPISODE", parentKey = "series1", episode = 2, name = "Final"),
        )
        val host = newHost(env)
        runBlocking {
            assertTrue(host.showEpisodePicker())
            host.awaitResolve()
        }
        assertEquals(listOf("1. Sezon 1. Bolum - Pilot", "1. Sezon 2. Bolum - Final"), env.pickerLabels)
        assertFalse(env.events.contains("library"))
    }

    @Test
    fun episodePickerListsSeriesEpisodes() {
        val env = FakeEnv()
        env.active = testChannel("https://cdn/a.mp4", key = "series1")
        env.metadata = metaRecord("series1", kind = "SERIES", parentKey = null)
        env.episodes = listOf(
            metaRecord("e1", kind = "EPISODE", parentKey = "series1", episode = 1, name = "Pilot"),
            metaRecord("e2", kind = "EPISODE", parentKey = "series1", episode = 2, name = "Final"),
        )
        val host = newHost(env)
        runBlocking {
            host.showEpisodePicker()
            host.awaitResolve()
        }
        assertEquals(listOf("1. Sezon 1. Bolum - Pilot", "1. Sezon 2. Bolum - Final"), env.pickerLabels)
        assertEquals(0, env.pickerIndex)
        env.pickerPick?.invoke(1)
        assertTrue(env.events.contains("play:e2"))
    }

    @Test
    fun episodePickerFallsBackToJsonEpisodes() {
        val env = FakeEnv()
        env.active = testChannel("https://cdn/a.mp4", key = "series1")
        env.metadata = metaRecord(
            "series1",
            kind = "SERIES",
            parentKey = null,
            streamUrl = "[{\"episode\":1,\"title\":\"Pilot\",\"videoUrl\":\"https://cdn/e1.mp4\"}]",
        )
        val host = newHost(env)
        runBlocking {
            host.showEpisodePicker()
            host.awaitResolve()
        }
        assertEquals(listOf("Pilot"), env.pickerLabels)
        env.pickerPick?.invoke(0)
        assertTrue(env.events.any { it.startsWith("payload:series1:ep:0_") })
    }

    private fun metaRecord(
        key: String,
        kind: String,
        parentKey: String?,
        episode: Int? = null,
        name: String = "Dizi",
        streamUrl: String? = null,
    ) = VodMetadataEntity(
        sourceKey = key,
        sourceId = 1L,
        kind = kind,
        providerId = "p",
        parentKey = parentKey,
        seasonNumber = 1,
        episodeNumber = episode,
        name = name,
        streamUrl = streamUrl,
        updatedAt = 0L,
    )

    @Test
    fun resolveFailureWithFallbackPromptsUserInsteadOfOpeningDirectly() {
        val env = FakeEnv()
        env.fallbackToBrowserEnabled = true
        val host = newHost(env)
        runBlocking {
            host.handlePayload(
                VodDeepLinkPayload(
                    sourceKey = "web_movie",
                    streamUrl = "https://example.com/watch/999",
                    title = "Web Film",
                ),
            )
            host.awaitResolve()
        }
        assertEquals("https://example.com/watch/999", env.promptedFallbackUrl)
        assertTrue(env.events.contains("prompt_browser:https://example.com/watch/999"))
        assertNull(env.openedInBrowserUrl)
        assertNull(env.playbackError)
    }

    @Test
    fun decideCandidatesWith404YieldsFailureWith404Flag() {
        val env = FakeEnv()
        val host = newHost(env)
        val channel = testChannel("https://example.com/dead_link")
        val resolved = VodPageResolver.Resolved(emptyList(), is404 = true)
        val decision = host.decideCandidates(channel, resolved)
        assertTrue(decision is VodCandidateDecision.Failure)
        assertTrue((decision as VodCandidateDecision.Failure).is404)
    }

    @Test
    fun resolveFailureWithoutFallbackShowsPlaybackError() {
        val env = FakeEnv()
        env.fallbackToBrowserEnabled = false
        val host = newHost(env)
        runBlocking {
            host.handlePayload(
                VodDeepLinkPayload(
                    sourceKey = "web_movie",
                    streamUrl = "https://example.com/watch/999",
                    title = "Web Film",
                ),
            )
            host.awaitResolve()
        }
        assertNull(env.openedInBrowserUrl)
        assertNull(env.promptedFallbackUrl)
        assertNotNull(env.playbackError)
    }
}
