package com.tvapp.livetv.vod

import java.util.regex.Pattern

data class CleanedVodTitle(
    val cleanTitle: String,
    val year: String? = null,
)

object VodTitleCleaner {
    private val YEAR_PATTERN = Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)")

    // Gürültü terimleri: Çözünürlük, ses, dil, ripper/grup etiketleri
    private val NOISE_PATTERNS = listOf(
        // Çözünürlük ve Format
        Pattern.compile("(?i)\\b(4k|2160p|1080p|1080i|720p|576p|480p|uhd|fhd|hd|sd)\\b"),
        Pattern.compile("(?i)\\b(bluray|blu-ray|bdrip|brrip|web-dl|webdl|webrip|dvdrip|hdtv)\\b"),
        Pattern.compile("(?i)\\b(x264|x265|h264|h265|hevc|avc|10bit|remux)\\b"),
        // Ses ve Dil
        Pattern.compile("(?i)\\b(tr\\s*dublaj|turkce\\s*dublaj|türkçe\\s*dublaj|dublaj)\\b"),
        Pattern.compile("(?i)\\b(tr\\s*altyazi|turkce\\s*altyazi|türkçe\\s*altyazı|altyazi|altyazılı)\\b"),
        Pattern.compile("(?i)\\b(dual|multi|aac|ac3|dts|ddp5\\.1|dd5\\.1|atmos)\\b"),
        // Diğer genel etiketler
        Pattern.compile("(?i)\\b(sinema\\s*cekimi|camrip|ts|hdts|extended|unrated|director'?s?\\s*cut)\\b"),
        // Emojiler ve semboller
        Pattern.compile("[⚡🎬🔥⭐✨💥🔴🔵🟢\\[\\](){}|_]"),
    )

    fun clean(rawTitle: String): CleanedVodTitle {
        var text = rawTitle.trim()

        // 1. Önce yılı yakala (varsa)
        var detectedYear: String? = null
        val yearMatcher = YEAR_PATTERN.matcher(text)
        // Başlığın sonuna yakın veya parantez içindeki yılı tercih et
        while (yearMatcher.find()) {
            val potentialYear = yearMatcher.group(1)
            // Yıl başlığın ilk kelimesi değilse (örn. "2001: A Space Odyssey" hariç)
            if (yearMatcher.start() > 3) {
                detectedYear = potentialYear
            }
        }

        // 2. Parantez ve koseli parantez icindeki aciklamalari (oyuncu, tur, etiket) temizle
        val textWithoutBrackets = text
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .trim()
        if (textWithoutBrackets.length >= 2) {
            text = textWithoutBrackets
        }

        // 3. Yil bilgisini temizle (bulunduysa)
        if (detectedYear != null) {
            text = text.replace(detectedYear, " ")
        }

        // 3. Bilinen tum gurultu kelimelerini boslukla degistir
        for (pattern in NOISE_PATTERNS) {
            text = pattern.matcher(text).replaceAll(" ")
        }

        // 4. Nokta, alt çizgi ve fazla tire işaretlerini temizle
        text = text.replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(':', ' ')

        // 5. Çoklu boşlukları teke indir ve kenarları kırp
        val cleaned = text.replace("\\s+".toRegex(), " ").trim()

        return CleanedVodTitle(
            cleanTitle = if (cleaned.isNotEmpty()) cleaned else rawTitle.trim(),
            year = detectedYear,
        )
    }
}
