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

    @Test
    fun filterByAlphabetRenumbersVisibleRows() {
        val channels = listOf(
            channel("Alpha", number = "5"),
            channel("Beta", number = "6"),
            channel("Akasya", number = "7"),
        )
        val filtered = ChannelListRules.filterByAlphabet(channels, "A")
        assertEquals(listOf("Alpha", "Akasya"), filtered.map { it.displayName })
        assertEquals(listOf("1", "2"), filtered.map { it.displayNumber })

        val all = ChannelListRules.filterByAlphabet(channels, null)
        assertEquals(listOf("1", "2", "3"), all.map { it.displayNumber })
    }

    @Test
    fun alphabetRailPlanPutsAllEntryFirst() {
        val plan = ChannelListRules.alphabetRailPlan(listOf("B", "A", "#"), allLabel = "Tümü")

        assertEquals(listOf("A", "B", "#"), plan.letters.map { it.letter })
        assertEquals(listOf("Tümü", "A", "B", "#"), plan.targets.map { it.letter })
        assertEquals(-1, plan.targets.first().position)
        // Harf hedefleri kendi sira numarasini tasir; ray geri cagrisi bu degeri verir.
        assertEquals(listOf(0, 1, 2), plan.targets.drop(1).map { it.position })
    }

    @Test
    fun alphabetRailJumpResolvesLetterFromPlan() {
        val plan = ChannelListRules.alphabetRailPlan(listOf("A", "B"), allLabel = "Tümü")
        // "Tümü" secimi -1 dondurur -> harf filtresi kapanir.
        assertEquals(null, plan.letters.getOrNull(plan.targets.first().position)?.letter)
        // Harf secimi 0..n-1 dondurur -> ilgili harf uygulanir.
        assertEquals("A", plan.letters.getOrNull(plan.targets[1].position)?.letter)
        assertEquals("B", plan.letters.getOrNull(plan.targets[2].position)?.letter)
    }

    @Test
    fun continueChannelsFilterSourceVodAndSearch() {
        val channels = listOf(
            channel("Dizi 1", key = "c1", source = LiveChannel.Source.IPTV, contentType = "VOD")
                .copy(inputId = "iptv:7"),
            channel("Dizi 2", key = "c2", source = LiveChannel.Source.IPTV, contentType = "VOD")
                .copy(inputId = "iptv:7"),
            // Baska kaynak
            channel("Dizi 3", key = "c3", source = LiveChannel.Source.IPTV, contentType = "VOD")
                .copy(inputId = "iptv:9"),
            // Canli yayin
            channel("Canli", key = "c4", source = LiveChannel.Source.IPTV, contentType = "LIVE")
                .copy(inputId = "iptv:7"),
        )

        val continued = ChannelListRules.continueChannels(channels, sourceId = 7)
        assertEquals(listOf("Dizi 1", "Dizi 2"), continued.map { it.displayName })
        assertEquals(listOf("1", "2"), continued.map { it.displayNumber })

        val searched = ChannelListRules.continueChannels(
            channels,
            sourceId = 7,
            normalizedQuery = ChannelListRules.normalizeSearch("dizi 2"),
        )
        assertEquals(listOf("Dizi 2"), searched.map { it.displayName })
        assertEquals(listOf("1"), searched.map { it.displayNumber })
    }
}
