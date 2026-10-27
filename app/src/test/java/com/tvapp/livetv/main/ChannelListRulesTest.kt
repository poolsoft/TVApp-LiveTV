package com.tvapp.livetv.main

import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.settings.ChannelSourceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelListRulesTest {

    private fun channel(
        name: String,
        number: String = "1",
        key: String = name,
        source: LiveChannel.Source = LiveChannel.Source.TIF,
        contentType: String? = "LIVE",
        favorite: Boolean = false,
        groupTitle: String? = null,
    ) = LiveChannel(
        id = key.hashCode().toLong(),
        sourceKey = key,
        inputId = "in",
        displayNumber = number,
        displayName = name,
        uri = "https://cdn/$key",
        iptvContentType = contentType,
        source = source,
        favorite = favorite,
        groupTitle = groupTitle,
    )

    @Test
    fun normalizeSearchStripsDiacriticsAndCase() {
        assertEquals("sine5", ChannelListRules.normalizeSearch("ŞİNE5"))
        assertEquals("cagri", ChannelListRules.normalizeSearch("ÇAĞRI"))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(ChannelListRules.matchesSearch(channel("TRT 1"), ""))
        assertTrue(ChannelListRules.matchesSearch(channel("TRT 1"), "   "))
    }

    @Test
    fun searchMatchesNumberNameAndGroup() {
        val trt = channel("TRT 1", number = "7", groupTitle = "Ulusal")
        assertTrue(ChannelListRules.matchesSearch(trt, "trt"))
        assertTrue(ChannelListRules.matchesSearch(trt, "7"))
        assertTrue(ChannelListRules.matchesSearch(trt, "ulusal"))
        assertFalse(ChannelListRules.matchesSearch(trt, "haber"))
    }

    @Test
    fun satelliteFilterExcludesRadioAndIptv() {
        val sat = channel("Uydu", source = LiveChannel.Source.TIF)
        val radio = channel("Radyo", source = LiveChannel.Source.TIF, groupTitle = "Radyo")
        val iptv = channel("IPTV", source = LiveChannel.Source.IPTV)

        assertTrue(ChannelListRules.matchesSource(sat, ChannelSourceFilter.SATELLITE))
        assertFalse(ChannelListRules.matchesSource(radio, ChannelSourceFilter.SATELLITE))
        assertFalse(ChannelListRules.matchesSource(iptv, ChannelSourceFilter.SATELLITE))
        assertTrue(ChannelListRules.matchesSource(radio, ChannelSourceFilter.RADIO))
        assertTrue(ChannelListRules.matchesSource(iptv, ChannelSourceFilter.IPTV))
        assertTrue(ChannelListRules.matchesSource(iptv, ChannelSourceFilter.ALL))
    }

    @Test
    fun favoritesFilterRequiresFavoriteFlag() {
        val fav = channel("Favori", favorite = true)
        val other = channel("Diger")
        assertTrue(ChannelListRules.matchesSource(fav, ChannelSourceFilter.FAVORITES))
        assertFalse(ChannelListRules.matchesSource(other, ChannelSourceFilter.FAVORITES))
    }

    @Test
    fun panelExcludesVodAndRenumbers() {
        val live = channel("Bir", number = "9")
        val vod = channel("Film", number = "10", contentType = "VOD", source = LiveChannel.Source.IPTV)
        val result = ChannelListRules.normalPanelChannels(
            channels = listOf(vod, live),
            sourceFilter = ChannelSourceFilter.ALL,
            favoriteFilter = false,
            normalizedQuery = "",
            alphabetLetter = null,
        )
        assertEquals(listOf("Bir"), result.map { it.displayName })
        assertEquals("1", result.single().displayNumber)
    }

    @Test
    fun alphabetFilterAndSkipWhenDisabled() {
        val a = channel("Alpha")
        val b = channel("Beta")
        val filtered = ChannelListRules.normalPanelChannels(
            channels = listOf(a, b),
            sourceFilter = ChannelSourceFilter.ALL,
            favoriteFilter = false,
            normalizedQuery = "",
            alphabetLetter = "A",
        )
        assertEquals(listOf("Alpha"), filtered.map { it.displayName })

        val withoutAlphabet = ChannelListRules.normalPanelChannels(
            channels = listOf(a, b),
            sourceFilter = ChannelSourceFilter.ALL,
            favoriteFilter = false,
            normalizedQuery = "",
            alphabetLetter = "A",
            includeAlphabet = false,
        )
        assertEquals(listOf("Alpha", "Beta"), withoutAlphabet.map { it.displayName })
        assertEquals(listOf("1", "2"), withoutAlphabet.map { it.displayNumber })
    }

    @Test
    fun favoriteFilterFlagAndSearchCombine() {
        val fav = channel("Haber Favori", favorite = true)
        val other = channel("Haber Diger")
        val result = ChannelListRules.normalPanelChannels(
            channels = listOf(fav, other),
            sourceFilter = ChannelSourceFilter.ALL,
            favoriteFilter = true,
            normalizedQuery = ChannelListRules.normalizeSearch("haber"),
            alphabetLetter = null,
        )
        assertEquals(listOf("Haber Favori"), result.map { it.displayName })
    }

    @Test
    fun searchFoldsAccentsButKeepsDotlessI() {
        val cagri = channel("Çağrı TV")
        assertTrue(ChannelListRules.matchesSearch(cagri, ChannelListRules.normalizeSearch("cagr")))
        // Noktasiz i (U+0131) NFD'de ayrismadigi icin 'i' ile eslesmez.
        // Bu, tasima oncesi MainActivity davranisinin aynisidir; degistirilmedi.
        assertFalse(ChannelListRules.matchesSearch(cagri, ChannelListRules.normalizeSearch("cagri")))
    }

    @Test
    fun hybridCycleWalksAllSources() {
        fun next(filter: ChannelSourceFilter) =
            ChannelListRules.nextListMode(inIptvLibrary = false, sourceFilter = filter, hybrid = true)

        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.SATELLITE),
            next(ChannelSourceFilter.ALL),
        )
        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.RADIO),
            next(ChannelSourceFilter.SATELLITE),
        )
        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.IPTV),
            next(ChannelSourceFilter.RADIO),
        )
        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.FAVORITES),
            next(ChannelSourceFilter.IPTV),
        )
        assertEquals(ChannelListModeAction.OpenIptvLibrary, next(ChannelSourceFilter.FAVORITES))
    }

    @Test
    fun simpleModeCycleSkipsSatelliteAndRadio() {
        fun next(filter: ChannelSourceFilter) =
            ChannelListRules.nextListMode(inIptvLibrary = false, sourceFilter = filter, hybrid = false)

        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.FAVORITES),
            next(ChannelSourceFilter.ALL),
        )
        assertEquals(ChannelListModeAction.OpenIptvLibrary, next(ChannelSourceFilter.FAVORITES))
        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.ALL),
            next(ChannelSourceFilter.SATELLITE),
        )
        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.ALL),
            next(ChannelSourceFilter.IPTV),
        )
    }

    @Test
    fun iptvLibraryAlwaysReturnsToAllChannels() {
        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.ALL),
            ChannelListRules.nextListMode(true, ChannelSourceFilter.FAVORITES, hybrid = true),
        )
        assertEquals(
            ChannelListModeAction.ApplyFilter(ChannelSourceFilter.ALL),
            ChannelListRules.nextListMode(true, ChannelSourceFilter.IPTV, hybrid = false),
        )
    }

    @Test
    fun cycleNeverEnablesFavoriteFilter() {
        ChannelSourceFilter.entries.forEach { filter ->
            listOf(true, false).forEach { hybrid ->
                val action = ChannelListRules.nextListMode(false, filter, hybrid)
                if (action is ChannelListModeAction.ApplyFilter) {
                    assertFalse(action.showFavorites)
                }
            }
        }
    }
}
