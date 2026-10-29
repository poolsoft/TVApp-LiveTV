package com.tvapp.livetv.playback

import java.util.Locale

/**
 * Yayin CDN'lerinin HTTP 403 Forbidden donmesini onlemek icin
 * URL kaliplarina gore otomatik Referer ve Origin basliklarini cozumler.
 */
object StreamHeaderResolver {

    data class ResolvedHeaders(
        val referrer: String?,
        val origin: String?,
    )

    fun resolve(
        url: String?,
        existingReferrer: String? = null,
        existingOrigin: String? = null,
    ): ResolvedHeaders {
        var finalReferrer = existingReferrer?.trim()?.takeIf { it.isNotEmpty() }
        var finalOrigin = existingOrigin?.trim()?.takeIf { it.isNotEmpty() }

        // Eger her iki baslik da zaten tanimliysa gereksiz tarama yapma
        if (finalReferrer != null && finalOrigin != null) {
            return ResolvedHeaders(finalReferrer, finalOrigin)
        }

        val rawUrl = url?.trim() ?: return ResolvedHeaders(finalReferrer, finalOrigin)
        val lower = rawUrl.lowercase(Locale.US)

        var detectedReferrer: String? = null
        var detectedOrigin: String? = null

        when {
            // Turkuvaz Medya Grubu (ATV, A Haber, A Spor, A Para vb.)
            lower.contains("trkvz") || lower.contains("atv") || lower.contains("ahaber") || lower.contains("aspor") -> {
                detectedReferrer = "https://www.atv.com.tr/"
                detectedOrigin = "https://www.atv.com.tr"
            }
            // Demiroren Medya Grubu (Kanal D, CNN Turk, Dream TV vb.)
            lower.contains("kanald") || lower.contains("duhnet") || lower.contains("demiroren") || lower.contains("cnnturk") -> {
                detectedReferrer = "https://www.kanald.com.tr/"
                detectedOrigin = "https://www.kanald.com.tr"
            }
            // Ciner Medya Grubu (Show TV, Haberturk, Bloomberg HT vb.)
            lower.contains("showtv") || lower.contains("ciner") || lower.contains("haberturk") || lower.contains("bloomberght") -> {
                detectedReferrer = "https://www.showtv.com.tr/"
                detectedOrigin = "https://www.showtv.com.tr"
            }
            // Dogus Yayin Grubu (Star TV, NTV, Kral vb.)
            lower.contains("startv") || lower.contains("ntv") || lower.contains("dogus") || lower.contains("kralmuzik") -> {
                detectedReferrer = "https://www.startv.com.tr/"
                detectedOrigin = "https://www.startv.com.tr"
            }
            // Acun Medya (TV8, TV8.5)
            lower.contains("tv8") -> {
                detectedReferrer = "https://www.tv8.com.tr/"
                detectedOrigin = "https://www.tv8.com.tr"
            }
            // Now TV (eski Fox TV)
            lower.contains("nowtv") || lower.contains("fox.com.tr") -> {
                detectedReferrer = "https://www.nowtv.com.tr/"
                detectedOrigin = "https://www.nowtv.com.tr"
            }
            // Halk TV
            lower.contains("halktv") -> {
                detectedReferrer = "https://halktv.com.tr/"
                detectedOrigin = "https://halktv.com.tr"
            }
            // TRT & Tabii
            lower.contains("trt.com.tr") || lower.contains("tabii.com") || lower.contains("trtworld") -> {
                detectedReferrer = "https://www.tabii.com/"
                detectedOrigin = "https://www.tabii.com"
            }
            // Sozcu TV (SZC)
            lower.contains("szctv") || lower.contains("sozcu") -> {
                detectedReferrer = "https://www.szctv.com.tr/"
                detectedOrigin = "https://www.szctv.com.tr"
            }
            // Tele 1
            lower.contains("tele1") -> {
                detectedReferrer = "https://tele1.com.tr/"
                detectedOrigin = "https://tele1.com.tr"
            }
            // Beyaz TV
            lower.contains("beyaztv") -> {
                detectedReferrer = "https://www.beyaztv.com.tr/"
                detectedOrigin = "https://www.beyaztv.com.tr"
            }
            // TGRT Haber
            lower.contains("tgrthaber") || lower.contains("tgrt") -> {
                detectedReferrer = "https://www.tgrthaber.com.tr/"
                detectedOrigin = "https://www.tgrthaber.com.tr"
            }
        }

        if (finalReferrer == null) finalReferrer = detectedReferrer
        if (finalOrigin == null) finalOrigin = detectedOrigin

        return ResolvedHeaders(finalReferrer, finalOrigin)
    }
}

