package com.tvapp.livetv.main

import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.settings.ChannelSourceFilter
import com.tvapp.livetv.ui.AlphabetJump
import com.tvapp.livetv.ui.AlphabetTarget
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

    /**
     * Alfabe harfine gore filtreleyip gorunen siraya gore yeniden numaralandirir.
     * `letter` null ise yalnizca numaralandirma yapilir.
     */
    fun filterByAlphabet(channels: List<LiveChannel>, letter: String?): List<LiveChannel> =
        channels
            .filter { letter == null || AlphabetJump.letter(it.displayName) == letter }
            .mapIndexed { index, channel -> channel.copy(displayNumber = (index + 1).toString()) }

    /**
     * "Devam edenler" listesi: kaynak + VOD filtresi, arama ve gorunen siraya
     * gore yeniden numaralandirma. Bu kural hem kutuphane acilisinda hem arama
     * uygulanirken ayni sekilde kullanilir.
     */
    fun continueChannels(
        channels: List<LiveChannel>,
        sourceId: Long,
        normalizedQuery: String = "",
    ): List<LiveChannel> =
        channels
            .filter { it.inputId == "iptv:$sourceId" && it.iptvContentType == "VOD" }
            .filter { matchesSearch(it, normalizedQuery) }
            .mapIndexed { index, channel -> channel.copy(displayNumber = (index + 1).toString()) }

    /** Alfabe rayi icin harf listesi ve ray hedefleri. */
    data class AlphabetRailPlan(
        /** Sirali harfler; `position` alani harfin kendi sirasidir. */
        val letters: List<AlphabetTarget>,
        /** Raya yerlestirilen hedefler: basa "Tumu" (-1), sonra harfler. */
        val targets: List<AlphabetTarget>,
    )

    /**
     * Alfabe rayi plani: harf listesi + "Tumu" girisi.
     *
     * Ray geri cagrisi `AlphabetTarget.position` degerini verir; "Tumu" -1,
     * harfler ise 0..n-1 tasir. Boylece cagiran taraf `letters.getOrNull(position)`
     * ile harfi bulur (tasima oncesi davranisin aynisi).
     */
    fun alphabetRailPlan(initials: Collection<String>, allLabel: String): AlphabetRailPlan {
        val letters = AlphabetJump.targets(initials.map { it to 0 })
        val targets = listOf(AlphabetTarget(allLabel, -1)) +
            letters.mapIndexed { index, target -> target.copy(position = index) }
        return AlphabetRailPlan(letters, targets)
    }

    /**
     * Liste modu dongusu (Sari kisabasim) icin sonraki adim.
     *
     * Hibrit olmayan deneyimde kaynak filtreleri arasinda gezinilmez:
     * ALL -> FAVORITES -> IPTV kutuphanesi -> ALL.
     * Hibritte ise ALL -> SATELLITE -> RADIO -> IPTV -> FAVORITES -> kutuphane.
     * IPTV kutuphanesi acikken her zaman ALL'a donulur.
     */
    fun nextListMode(
        inIptvLibrary: Boolean,
        sourceFilter: ChannelSourceFilter,
        hybrid: Boolean,
    ): ChannelListModeAction {
        if (inIptvLibrary) return ChannelListModeAction.ApplyFilter(ChannelSourceFilter.ALL)
        return if (!hybrid) {
            when (sourceFilter) {
                ChannelSourceFilter.ALL -> ChannelListModeAction.ApplyFilter(ChannelSourceFilter.FAVORITES)
                ChannelSourceFilter.FAVORITES -> ChannelListModeAction.OpenIptvLibrary
                else -> ChannelListModeAction.ApplyFilter(ChannelSourceFilter.ALL)
            }
        } else {
            when (sourceFilter) {
                ChannelSourceFilter.ALL -> ChannelListModeAction.ApplyFilter(ChannelSourceFilter.SATELLITE)
                ChannelSourceFilter.SATELLITE -> ChannelListModeAction.ApplyFilter(ChannelSourceFilter.RADIO)
                ChannelSourceFilter.RADIO -> ChannelListModeAction.ApplyFilter(ChannelSourceFilter.IPTV)
                ChannelSourceFilter.IPTV -> ChannelListModeAction.ApplyFilter(ChannelSourceFilter.FAVORITES)
                ChannelSourceFilter.FAVORITES -> ChannelListModeAction.OpenIptvLibrary
            }
        }
    }

    /**
     * Bos kanal listesinde ne gosterilecegine karar veren saf kural (`UI-003`).
     *
     * Hata ile bosluk ve "filtre bos" birbirinden ayirt edilir: sorgu veya filtre
     * aktifse tek eylemli temizleme onerilir; hicbiri aktif degilse kullanicinin
     * listeyi kendisinin doldurmasi gerektigi soylenebilir. Kutuphanede (IPTV
     * katalog) Mavi tus filtreleri acar, bu yuzden orada eylem yerine ipucu
     * gosterilir - renk tusu sozlesmesi degistirilmez.
     */
    fun emptyListState(
        listEmpty: Boolean,
        libraryMode: Boolean,
        searchActive: Boolean,
        filterActive: Boolean,
    ): ChannelListEmptyState = when {
        !listEmpty -> ChannelListEmptyState.Hidden
        libraryMode -> ChannelListEmptyState.LibraryEmpty
        searchActive -> ChannelListEmptyState.SearchEmpty
        filterActive -> ChannelListEmptyState.FilterEmpty
        else -> ChannelListEmptyState.NoChannels
    }

    /**
     * Bos liste yonlendirme metninin soylem bicimi (`UI-016`). Metin renk tusunu ya da
     * rozeti anlattigi icin karar `hasColorKeys` ile verilir: renk tusu olmayan profilde
     * ayni rozetler dokunulabilir oldugundan "dokun" denen metin secilir. TV profillerinde
     * `hasColorKeys` her zaman true oldugu icin metin degismez.
     */
    fun emptyHintFor(
        state: ChannelListEmptyState,
        hasColorKeys: Boolean,
    ): ChannelListEmptyHint = when {
        state == ChannelListEmptyState.LibraryEmpty && hasColorKeys ->
            ChannelListEmptyHint.LibraryKeys

        state == ChannelListEmptyState.LibraryEmpty -> ChannelListEmptyHint.LibraryTouch
        hasColorKeys -> ChannelListEmptyHint.NoChannelsKeys
        else -> ChannelListEmptyHint.NoChannelsTouch
    }
}

/** Bos liste yonlendirme metninin soylem bicimi (`UI-016`). */
enum class ChannelListEmptyHint {
    /** Normal liste bos, kumanda var: renk tusu adlariyla anlatilir. */
    NoChannelsKeys,

    /** Normal liste bos, renk tusu yok: rozete dokunma olarak anlatilir. */
    NoChannelsTouch,

    /** IPTV kutuphanesi bos, kumanda var: Mavi tus adiyla anlatilir. */
    LibraryKeys,

    /** IPTV kutuphanesi bos, renk tusu yok: mavi rozete dokunma olarak anlatilir. */
    LibraryTouch,
}

/** Kanal listesi bos durumunun sekilleri (`UI-003`). */
enum class ChannelListEmptyState {
    /** Listede kayit var: bos durum katmani gorunmez. */
    Hidden,

    /** Arama sorgusu var ve eslesen kanal yok: sorgu temizlenebilir. */
    SearchEmpty,

    /** Kaynak/favori/harf filtresi aktif ve eslesen kanal yok: filtreler temizlenebilir. */
    FilterEmpty,

    /** Filtre yok ama liste bos: kullanici yonlendirmesi gerekir. */
    NoChannels,

    /** IPTV kutuphanesi bos: Mavi tus filtreleri acar. */
    LibraryEmpty,
}

/** Liste modu dongusunun urettigi eylem. */
sealed interface ChannelListModeAction {
    /** Verilen kaynak filtresini uygular (favori filtresi her zaman kapanir). */
    data class ApplyFilter(
        val source: ChannelSourceFilter,
        val showFavorites: Boolean = false,
    ) : ChannelListModeAction

    /** Kayitli IPTV kutuphanesi penceresini acar. */
    data object OpenIptvLibrary : ChannelListModeAction
}
