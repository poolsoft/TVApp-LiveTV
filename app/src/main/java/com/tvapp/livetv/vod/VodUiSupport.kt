package com.tvapp.livetv.vod

/**
 * VOD cozumleme akisinda kullanilan saf ve Android-bagimsiz yardimcilar.
 *
 * MainActivity'den cikarilmistir; birim testlerde dogrudan dogrulanir.
 * Adres (URL) etiketi ve embed sayfasi icindeki JSON bolum listesi cozumleme
 * mantigi burada toplanir, boylece TV uygulamasi ayni kurallari paylasir.
 */
object VodUiSupport {

    /**
     * Cok kaynakli sayfalarda uzaktan kumandayla gosterilecek aday etiketi.
     *
     * Ornek: `1. HLS [1080p] - hdfilmcehennemi.mobi`
     */
    fun buildVodStreamLabel(index: Int, url: String): String {
        val cleanUrl = url.substringBefore('?')
        val ext = cleanUrl.substringAfterLast('.', "")
        val quality = when {
            url.contains("1080", ignoreCase = true) -> " [1080p]"
            url.contains("720", ignoreCase = true) -> " [720p]"
            url.contains("480", ignoreCase = true) -> " [480p]"
            url.contains("360", ignoreCase = true) -> " [360p]"
            else -> ""
        }
        val type = when {
            VodUrlClassifier.isHlsUrl(url) -> "HLS"
            VodUrlClassifier.isDashUrl(url) -> "DASH"
            url.contains(".mp4", ignoreCase = true) -> "MP4"
            else -> ext.uppercase().take(4)
        }
        val domain = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        return "$index. $type$quality - $domain"
    }

    data class ParsedEpisode(val title: String, val url: String)

    /**
     * Kullaniciya gorunen bolum etiketleri.
     *
     * Android kaynaklarina bagimlilik olusturmamak icin enjekte edilir; varsayilan
     * uygulama eski davranisi korur, TV uygulamasi iki dilli kaynaklari gecer.
     */
    interface EpisodeLabels {
        /** Baslik yok/tekrarli: yalniz `S. Sezon E. Bolum`. */
        fun seasonEpisode(season: Int, episode: Int): String

        /** Saglayicidan gelen gercek baslikla birlikte etiket. */
        fun seasonEpisodeTitle(season: Int, episode: Int, title: String): String

        /** JSON kaydinda ad alani hic yoksa kullanilan numara etiketi. */
        fun untitledEpisode(index: Int): String
    }

    /** Kaynak kullanilmayan cagrilar icin ASCII varsayilanlar (eski davranis). */
    object LegacyEpisodeLabels : EpisodeLabels {
        override fun seasonEpisode(season: Int, episode: Int): String =
            "$season. Sezon $episode. Bolum"

        override fun seasonEpisodeTitle(season: Int, episode: Int, title: String): String =
            "$season. Sezon $episode. Bolum - $title"

        override fun untitledEpisode(index: Int): String = "$index. Bolum"
    }

    /**
     * Seri `streamUrl` icinde gomulu JSON bolum listesini cozumler.
     *
     * Beklenen bicim: `[{"season":1,"episode":1,"title":"...","videoUrl":"..."}]`
     * Gecersiz/boom JSON bos liste doner; exception yaymaz.
     */
    fun parseEpisodesJson(
        raw: String?,
        labels: EpisodeLabels = LegacyEpisodeLabels,
    ): List<ParsedEpisode> {
        if (raw.isNullOrBlank() || !raw.startsWith("[")) return emptyList()
        val result = mutableListOf<ParsedEpisode>()
        try {
            val jsonArray = org.json.JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val epObj = jsonArray.optJSONObject(i) ?: continue
                val epTitle = epObj.optString("title").trim().ifEmpty {
                    epObj.optString("name").trim()
                }.ifEmpty { labels.untitledEpisode(i + 1) }
                val epUrl = epObj.optString("videoUrl").trim().ifEmpty {
                    epObj.optString("url").trim()
                }
                if (epUrl.isNotEmpty()) result.add(ParsedEpisode(epTitle, epUrl))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result
    }

    /**
     * Saglayicinin bolum kayitlarinda ad alani bazen varsayilan degeri tasir
     * ("3. Bölüm" ya da "Bölüm 3"). Boyle tekrarlar gosterilmez. Xtream/M3U verisi
     * uygulama dilinden bagimsiz uretilir; bu yuzden karsilastirma sabit Turkce
     * etiketlerle (eski ASCII kayitlar icin ayrica ASCII bicimiyle) yapilir.
     */
    fun isProviderDefaultEpisodeTitle(
        rawTitle: String,
        episode: Int,
        allowReversed: Boolean = false,
    ): Boolean {
        val candidates = mutableListOf("$episode. Bölüm", "$episode. Bolum")
        if (allowReversed) {
            candidates += "Bölüm $episode"
            candidates += "Bolum $episode"
        }
        return candidates.any { rawTitle.equals(it, ignoreCase = true) }
    }

    /**
     * Gomulu JSON bolum kaydindan (`sourceKey:ep:<index>_<hash>` yolu) baslik ve
     * adres cikarir. Etiketler [labels] ile uretilir; varsayilan [LegacyEpisodeLabels]
     * eski ASCII davranisini korur. Veri yoksa null doner.
     */
    fun dynamicEpisode(
        epObj: org.json.JSONObject?,
        fallbackIndex: Int,
        labels: EpisodeLabels = LegacyEpisodeLabels,
    ): ParsedEpisode? {
        if (epObj == null) return null
        val sNum = epObj.optInt("season", epObj.optInt("season_number", 1)).coerceAtLeast(1)
        val eNum = epObj.optInt("episode", epObj.optInt("episode_number", fallbackIndex + 1)).coerceAtLeast(1)
        val rawTitle = epObj.optString("title").trim().ifEmpty { epObj.optString("name").trim() }
        val title = if (rawTitle.isNotEmpty() && !isProviderDefaultEpisodeTitle(rawTitle, eNum)) {
            labels.seasonEpisodeTitle(sNum, eNum, rawTitle)
        } else {
            labels.seasonEpisode(sNum, eNum)
        }
        val url = epObj.optString("videoUrl").trim().ifEmpty { epObj.optString("url").trim() }
        if (url.isEmpty()) return null
        return ParsedEpisode(title, url)
    }
}
