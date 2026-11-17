package com.tvapp.livetv.platform

import com.tvapp.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * UI-014: "Canlı TV'ye dön" isteğinin kanala çevrilmesi.
 *
 * Kuralın asla kendi kendine kanal uydurmaması beklenir: eşleşme yoksa `null` döner ve
 * çağıran mevcut oynatmasını korur.
 */
class LiveLaunchRulesTest {

    private fun channel(
        key: String,
        source: LiveChannel.Source,
        contentType: String? = "LIVE",
    ) = LiveChannel(
        id = key.hashCode().toLong(),
        sourceKey = key,
        inputId = "in",
        displayNumber = key,
        displayName = key,
        uri = "https://cdn/$key",
        iptvContentType = contentType,
        source = source,
    )

    private val tif = channel("tif-1", LiveChannel.Source.TIF)
    private val iptvLive = channel("iptv-1", LiveChannel.Source.IPTV)
    private val iptvVod = channel("iptv-vod", LiveChannel.Source.IPTV, contentType = "VOD")
    private val mix = listOf(iptvVod, tif, iptvLive)

    @Test
    fun emptyTargetSelectsNothing() {
        assertNull(LiveLaunchRules.resolve(mix, null))
        assertNull(LiveLaunchRules.resolve(mix, LiveLaunchTarget()))
    }

    @Test
    fun tokenSelectsFirstLiveChannelOfThatSourceKind() {
        assertEquals(tif, LiveLaunchRules.resolve(mix, LiveLaunchTarget(token = LiveLaunchRules.TARGET_TIF)))
        assertEquals(
            iptvLive,
            LiveLaunchRules.resolve(mix, LiveLaunchTarget(token = LiveLaunchRules.TARGET_IPTV)),
        )
    }

    @Test
    fun vodRowsAreNotLiveTargets() {
        val onlyVod = listOf(iptvVod)
        assertNull(LiveLaunchRules.resolve(onlyVod, LiveLaunchTarget(token = LiveLaunchRules.TARGET_IPTV)))
    }

    @Test
    fun mobileShellWithoutTifRowsIgnoresTifRequest() {
        // Mobil kabuk channels(includeTif = false) ile çalışır: TIF satırı yoktur ve
        // istek karşılıksız kalır; mevcut oynatma korunur.
        val iptvOnly = listOf(iptvLive, iptvVod)
        assertNull(LiveLaunchRules.resolve(iptvOnly, LiveLaunchTarget(token = LiveLaunchRules.TARGET_TIF)))
    }

    @Test
    fun unknownTokenSelectsNothingInsteadOfGuessing() {
        assertNull(LiveLaunchRules.resolve(mix, LiveLaunchTarget(token = "satellite")))
    }

    @Test
    fun deepLinkSourceKeyWinsWhenTokenHasNoMatch() {
        val target = LiveLaunchTarget(token = LiveLaunchRules.TARGET_TIF, sourceKey = "iptv-1")
        assertEquals(tif, LiveLaunchRules.resolve(mix, target))

        val iptvOnly = listOf(iptvLive)
        assertEquals(iptvLive, LiveLaunchRules.resolve(iptvOnly, target))
    }

    @Test
    fun unknownSourceKeySelectsNothing() {
        assertNull(LiveLaunchRules.resolve(mix, LiveLaunchTarget(sourceKey = "olmayan-kanal")))
    }
}
