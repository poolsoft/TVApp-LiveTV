package com.tvapp.livetv.main

import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.settings.ChannelSourceFilter
import com.tvapp.livetv.ui.AlphabetJump
import com.tvapp.livetv.ui.isRadioChannel
import java.text.Normalizer
import java.util.Locale

/**
 * Faz 3: normal kanal panelinin filtre/arama kurallari.
 *
 * Davranis degisikligi yok: MainActivity'deki `buildNormalPanelChannels`,
 * `matchesChannelSearch` ve `normalizeSearchText` govdelerinin birebir
 * karsiligidir. UI'a ait isler (adapter, binding, focus) Activity'de kalir.
 */
object ChannelListRules {

    private val SEARCH_COMBINING_MARKS = Regex("\\p{M}+")

    /** Aksan/isaret duyarsiz, kucuk harfli arama metni. */
    fun normalizeSearch(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(SEARCH_COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

    /** Kanal numarasi, adi veya grup adi normalize edilmis sorguyu iceriyor mu? */
    fun matchesSearch(channel: LiveChannel, normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        return listOf(channel.displayNumber, channel.displayName, channel.groupTitle.orEmpty())
            .any { normalizeSearch(it).contains(normalizedQuery) }
    }

    /** Kaynak filtresi kurali (Tumu / Uydu / Radyo / IPTV / Favoriler). */
    fun matchesSource(channel: LiveChannel, sourceFilter: ChannelSourceFilter): Boolean =
        when (sourceFilter) {
            ChannelSourceFilter.ALL -> true
            ChannelSourceFilter.SATELLITE ->
                channel.source == LiveChannel.Source.TIF && !channel.isRadioChannel()
            ChannelSourceFilter.RADIO -> channel.isRadioChannel()
            ChannelSourceFilter.IPTV -> channel.source == LiveChannel.Source.IPTV
            ChannelSourceFilter.FAVORITES -> channel.favorite
        }

    /**
     * Normal panel listesi: VOD disi, kaynak/favori/arama/alfabe filtreleri ve
     * gorunen siraya gore yeniden numaralandirma.
     *
     * @param includeAlphabet false ise alfabe filtresi uygulanmaz (harf atlama
     *   listesi kurulurken kullanilir).
     */
    fun normalPanelChannels(
        channels: List<LiveChannel>,
        sourceFilter: ChannelSourceFilter,
        favoriteFilter: Boolean,
        normalizedQuery: String,
        alphabetLetter: String?,
        includeAlphabet: Boolean = true,
    ): List<LiveChannel> = channels.asSequence()
        .filterNot { it.isVodContent() }
        .filter { matchesSource(it, sourceFilter) }
        .filter { channel -> !favoriteFilter || channel.favorite }
        .filter { channel -> matchesSearch(channel, normalizedQuery) }
        .filter { channel ->
            !includeAlphabet || alphabetLetter == null ||
                AlphabetJump.letter(channel.displayName) == alphabetLetter
        }
        .mapIndexed { index, channel -> channel.copy(displayNumber = (index + 1).toString()) }
        .toList()
}
