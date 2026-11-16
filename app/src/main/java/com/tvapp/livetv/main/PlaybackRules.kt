package com.tvapp.livetv.main

import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackPhase

/**
 * Faz 2: MainActivity'deki tune / alternatif akis zincirinin saf karar kurallari.
 *
 * Davranis degisikligi yok: buradaki kurallar MainActivity'deki mevcut ifadelerin
 * birebir karsiligidir. Yan etkili oynatma kodu (Media3, TIF, UI) Activity'de kalir;
 * burada yalnizca "ne yapilmali" sorusu cevaplanir.
 */
object PlaybackRules {

    /** VOD modunda VOD olmayan bir kanal secilirken ne olacagi. */
    enum class VodModeGate {
        /** Secim dogrudan oynatilir. */
        Play,

        /**
         * Kullanici canli kanali kendisi secerse (OK, CH+, rakam): VOD modu kapanir, sonra
         * secim oynatilir. Eskiden bu secim sessizce engelleniyor ve uygulama "VOD modunda
         * takildi" hissi veriyordu (siyah ekran + OK ile VOD oynatici katmani).
         */
        ExitVodThenPlay,

        /** Odak kaynakli gecikmeli otomatik ayarlama VOD oynatmasini bozamaz. */
        Block,
    }

    /**
     * VOD modunda kanal seciminin kapisi.
     *
     * Engelleme yalnizca listede gezinirken tetiklenen otomatik ayarlamada korunur
     * (AGENTS.md: listede gezinmek/uzun basma oynatmayi degistirmemeli). OK, CH+/CH-,
     * rakam girisi gibi acik secimler artik VOD modundan cikarip kanali acar.
     */
    fun vodModeGate(vodMode: Boolean, channel: LiveChannel, autoTune: Boolean): VodModeGate = when {
        !vodMode || channel.isVodContent() -> VodModeGate.Play
        autoTune -> VodModeGate.Block
        else -> VodModeGate.ExitVodThenPlay
    }

    /**
     * VOD arayuzunun (VOD oynatici katmani, VOD zaman cizgisi, bilgi barinin kapanmasi)
     * etkin olup olmadigi; `MainActivity.isVodPlaybackActive` ifadesinin karsiligidir.
     *
     * Kritik ayrim: [contentKindIsVod] oynaticinin son IPTV icerosu icin bildirdigi
     * bayraktir. Canli kanala geciste sifirlanmazsa arayuz VOD'da kalmaya devam eder;
     * canli kanalda bilgi bar yerine VOD oynatici katmani acar.
     */
    fun vodInterfaceActive(
        vodMode: Boolean,
        contentKindIsVod: Boolean,
        vodTimeline: Boolean,
        channelIsVod: Boolean,
        channelIsIptv: Boolean,
    ): Boolean = (vodMode || contentKindIsVod || vodTimeline || channelIsVod) && channelIsIptv

    /**
     * Ayni kanal uzerinde PREPARING/BUFFERING iken tekrar OK basimi oynatmayi
     * yeniden baslatmamali (retry/alternatif dongusu sifirlanmasin).
     */
    fun ignoreRepeatSelect(
        channel: LiveChannel,
        currentSourceKey: String?,
        phase: IptvPlaybackPhase,
    ): Boolean = channel.sourceKey == currentSourceKey &&
        (phase == IptvPlaybackPhase.PREPARING || phase == IptvPlaybackPhase.BUFFERING)

    /**
     * VOD icerikte kayitli konumdan devam edilir; canli yayinda 0.
     * Konum yalnizca gerektiginde okunur (lambda), boylece canli yayinda
     * gereksiz depolama sorgusu yapilmaz.
     */
    fun resumePosition(channel: LiveChannel, storedPosition: () -> Long): Long =
        if (channel.isVodContent() || channel.iptvContentType.equals("VOD", ignoreCase = true)) {
            storedPosition()
        } else {
            0L
        }

    /** Hata sonrasi alternatif akisa gecerken korunacak konum. */
    fun alternativeStartPosition(channel: LiveChannel, snapshotPositionMillis: Long): Long =
        if (channel.isVodContent() || channel.iptvContentType.equals("VOD", ignoreCase = true)) {
            snapshotPositionMillis
        } else {
            0L
        }

    /**
     * Onbellekteki alternatif listesi bu kanal icin kullanilabilir mi?
     * Kullanilamiyorsa null doner ve cagiran taraf kaynaktan okur.
     */
    fun cachedAlternatives(
        cachedKey: String?,
        channelSourceKey: String,
        cached: List<LiveChannel>,
    ): List<LiveChannel>? =
        cached.takeIf { cachedKey == channelSourceKey && it.isNotEmpty() }

    /** Siradaki alternatif akis ve guncellenmis imlec. */
    data class AlternativeChoice(val channel: LiveChannel, val nextIndex: Int)

    /** Imlecteki alternatifi secer; liste bittiyse null (tune tukendi). */
    fun nextAlternative(alternatives: List<LiveChannel>, index: Int): AlternativeChoice? {
        val alternative = alternatives.getOrNull(index) ?: return null
        return AlternativeChoice(alternative, index + 1)
    }

    /**
     * Alternatif akisin baglanti bilgilerini mevcut kanala uygular.
     * Kimlik, baslik ve icerik turu korunur; yalnizca oynatma parametreleri degisir.
     */
    fun applyAlternative(channel: LiveChannel, alternative: LiveChannel): LiveChannel =
        channel.copy(
            uri = alternative.uri,
            inputId = alternative.inputId,
            userAgent = alternative.userAgent,
            referrer = alternative.referrer,
            origin = alternative.origin,
            subtitleUrl = alternative.subtitleUrl,
        )
}
