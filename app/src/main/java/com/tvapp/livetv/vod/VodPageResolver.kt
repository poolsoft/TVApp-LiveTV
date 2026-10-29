package com.tvapp.livetv.vod

import com.tvapp.livetv.model.LiveChannel

/**
 * Web sayfasi URL'sini (embed zinciri, sniffer) oynatilabilir VOD kanalina donusturur.
 *
 * MainActivity icindeki tekrar eden cozumleme mantigini tek bir API'ye indirger;
 * boylece MainActivity cozumleme nedeniyle buyumez ve farkli giris yollari
 * (deep link, DB kaydi, IPTV kitapligi kanali) ayni davranisi paylasir.
 *
 * Kurallar:
 *  - Dogrudan medya (mp4/m3u8/mkv) icin ag istegi yapilmaz.
 *  - Cozulmus CDN URL'leri kalici tutulmaz; her oynatma taze cozulenir.
 *  - Birden cok oynatilabilir aday varsa cagiran secim sunar, sessizce ilkini secemez.
 *  - Cozumleme basarisizsa orijinal sayfa URL'si korunur (fallback theater/browser akisi).
 */
object VodPageResolver {

    /** Cozulmus VOD adaylarinin bir kanala uygulanmis hali. */
    data class Resolved(
        val candidates: List<WebVideoResolver.ResolvedStream>,
        val is404: Boolean = false,
    ) {
        val single: WebVideoResolver.ResolvedStream?
            get() = if (candidates.size == 1) candidates.first() else null

        fun playable(): WebVideoResolver.ResolvedStream? = single

        /** Secilen adayi temel kanala uygular (custom UA/referer korunur). */
        fun applyTo(base: LiveChannel, chosen: WebVideoResolver.ResolvedStream): LiveChannel {
            val playableUri = chosen.streamUrl.takeIf { it.isNotBlank() } ?: base.uri
            return base.copy(
                uri = playableUri,
                userAgent = base.userAgent ?: chosen.userAgent,
                referrer = base.referrer ?: chosen.referrer,
                origin = base.origin ?: chosen.origin,
            ).apply {
                cookie = base.cookie ?: chosen.cookie
            }
        }
    }

    suspend fun resolve(
        pageUrl: String,
        context: android.content.Context? = null,
    ): Resolved {
        val target = pageUrl.trim()
        if (target.isEmpty()) return Resolved(emptyList())
        if (WebVideoResolver.isDirectStream(target)) {
            return Resolved(listOf(WebVideoResolver.ResolvedStream(target, userAgent = null, isPlayable = true)))
        }
        if (target.startsWith("http", ignoreCase = true) && WebVideoResolver.checkIsHttp404(target)) {
            return Resolved(emptyList(), is404 = true)
        }
        val candidates = runCatching {
            WebVideoResolver.resolveCandidates(target, context)
        }.getOrElse { emptyList() }
        return Resolved(candidates)
    }
}
