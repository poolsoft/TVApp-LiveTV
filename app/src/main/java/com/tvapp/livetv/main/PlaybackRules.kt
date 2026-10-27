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

    /** VOD modunda VOD olmayan kanal secilemez. */
    fun blockedByVodMode(vodMode: Boolean, channel: LiveChannel): Boolean =
        vodMode && !channel.isVodContent()

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
        if (channel.iptvContentType == "VOD") storedPosition() else 0L

    /** Hata sonrasi alternatif akisa gecerken korunacak konum. */
    fun alternativeStartPosition(channel: LiveChannel, snapshotPositionMillis: Long): Long =
        if (channel.iptvContentType == "VOD") snapshotPositionMillis else 0L

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
