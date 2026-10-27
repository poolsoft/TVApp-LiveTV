package com.tvapp.livetv.vod

/**
 * Web sayfasi, embed ve sniffer katmanlarinin ortak VOD URL dili.
 *
 * Klasik uzantilarin (m3u8/mpd/mp4) yaninda kilitya girmis HLS dagitimlarini da tanir:
 * ornek `.../master.txt`, `.../playlist.txt`, `.smil/.m3u8`.
 *
 * Saf Kotlin'dir; Android bagimliligi yoktur, birim testlerde dogrudan calisir.
 */
object VodUrlClassifier {

    fun isDirectStream(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty()) return false
        val lower = clean.lowercase()

        // Kiliga girmis HLS dagitimlari
        if (lower.contains("master.txt") || lower.contains("playlist.txt")) return true
        if (lower.contains(".smil") && lower.contains(".m3u8")) return true

        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("stream.php") ||
            lower.contains("/hls/") || lower.contains("playlist.m3u8") ||
            lower.contains("/movie/") || lower.contains("/series/") || lower.contains("/live/")
        ) {
            return true
        }
        if (lower.contains("/dash/") && (lower.contains("manifest") || lower.contains(".mpd"))) {
            return true
        }
        val noQuery = clean.substringBefore("?").substringBefore("#").lowercase()
        if (noQuery.endsWith(".m3u8") || noQuery.endsWith(".m3u") || noQuery.endsWith(".mpd") ||
            noQuery.endsWith(".mp4") || noQuery.endsWith(".m4v") || noQuery.endsWith(".mkv") ||
            noQuery.endsWith(".ts") || noQuery.endsWith(".avi") || noQuery.endsWith(".webm") ||
            noQuery.endsWith(".flv") || noQuery.endsWith("master.txt") || noQuery.endsWith("playlist.txt")
        ) {
            return true
        }
        return false
    }

    fun isHlsUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        if (lower.isEmpty()) return false
        if (lower.contains(".m3u8")) return true
        if (lower.contains("master.txt") || lower.contains("playlist.txt")) return true
        if (lower.contains(".smil") && lower.contains(".m3u8")) return true
        if (lower.contains("/hls/") && (lower.contains("master") || lower.contains("playlist"))) return true
        return false
    }

    fun isDashUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        if (lower.isEmpty()) return false
        if (lower.contains(".mpd")) return true
        if (lower.contains("/dash/") && lower.contains("manifest")) return true
        return false
    }

    /**
     * ExoPlayer `MediaItem.setMimeType` icin mime karsiligi.
     * Uzantidan tur cikarilamayan (`master.txt` gibi) HLS dagitimlarinda zorunludur.
     */
    fun mimeType(url: String): String? = when {
        isHlsUrl(url) -> "application/x-mpegURL"
        isDashUrl(url) -> "application/dash+xml"
        else -> null
    }
}
