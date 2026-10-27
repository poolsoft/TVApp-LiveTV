package com.tvapp.livetv.browser

/**
 * Yakalanan yayin adayinin turu.
 */
enum class MediaKind {
    MANIFEST_HLS,
    MANIFEST_DASH,
    PROGRESSIVE,
    SUBTITLE,
    SEGMENT,
    OTHER
}

/**
 * Sayfada tespit edilen medya istegi bilgisi.
 */
data class StreamCandidate(
    val url: String,
    val kind: MediaKind,
    val seq: Int,
    val headers: Map<String, String> = emptyMap()
)

data class SubtitleTrack(
    val url: String,
    val mimeType: String,
    val language: String,
    val label: String
)

data class PlaybackRequest(
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val title: String = ""
)

/**
 * URL yapisina gore medya turunu siniflandirir.
 */
object MediaUrlClassifier {

    private val AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "googletagmanager.com", "googleadservices.com", "adservice.google.com",
        "imasdk.googleapis.com", "amazon-adsystem.com", "adnxs.com", "scorecardresearch.com"
    )

    private val PROGRESSIVE_EXTENSIONS = listOf(".mp4", ".m4v", ".webm", ".mkv")
    private val SEGMENT_WORDS = setOf("init", "dashinit", "seg", "segment", "chunk", "frag", "fragment")
    private val SEGMENT_PREFIX = Regex("^(init|seg|segment|chunk|frag|fragment)\\d+$")

    fun classify(url: String): MediaKind {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val lowerUrl = url.lowercase()
        return when {
            com.tvapp.livetv.vod.VodUrlClassifier.isHlsUrl(url) -> MediaKind.MANIFEST_HLS
            com.tvapp.livetv.vod.VodUrlClassifier.isDashUrl(url) -> MediaKind.MANIFEST_DASH
            path.endsWith(".ts") || path.endsWith(".m4s") -> MediaKind.SEGMENT
            path.endsWith(".vtt") || path.endsWith(".srt") || lowerUrl.contains(".vtt") -> MediaKind.SUBTITLE
            path.endsWith(".m3u8") || lowerUrl.contains(".m3u8") || lowerUrl.contains("playlist.m3u8") || lowerUrl.contains("manifest.m3u8") -> MediaKind.MANIFEST_HLS
            path.endsWith(".mpd") || lowerUrl.contains(".mpd") -> MediaKind.MANIFEST_DASH
            PROGRESSIVE_EXTENSIONS.any { path.endsWith(it) || lowerUrl.contains(it) } ->
                if (isSegmentLike(path)) MediaKind.SEGMENT else MediaKind.PROGRESSIVE
            lowerUrl.contains("/hls/") && !isSegmentLike(path) -> MediaKind.MANIFEST_HLS
            lowerUrl.contains("/dash/") && !isSegmentLike(path) -> MediaKind.MANIFEST_DASH
            else -> MediaKind.OTHER
        }
    }

    private fun isSegmentLike(path: String): Boolean {
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        if (name.isEmpty()) return false
        if (name.all { it.isDigit() }) return true
        val words = name.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (words.any { it in SEGMENT_WORDS || SEGMENT_PREFIX.matches(it) }) return true
        val last = words.lastOrNull() ?: return false
        return words.size > 1 && last.length > 1 && last.startsWith("0") && last.all { it.isDigit() }
    }

    fun isAdHost(url: String, denylist: Set<String> = AD_HOSTS): Boolean {
        val host = UrlHost.of(url) ?: return false
        return denylist.any { host == it || host.endsWith(".$it") }
    }
}

/**
 * Toplanan medya adaylari arasindan en uygun olani secer.
 */
object StreamCandidateSelector {
    fun selectBest(candidates: List<StreamCandidate>): StreamCandidate? {
        val playable = candidates.filter { !MediaUrlClassifier.isAdHost(it.url) }
        val manifests = playable.filter { it.kind == MediaKind.MANIFEST_HLS }
        val masters = manifests.filter { it.url.substringBefore('?').lowercase().contains("master") }
        return masters.maxByOrNull { it.seq }
            ?: manifests.minByOrNull { it.seq }
            ?: playable.filter { it.kind == MediaKind.MANIFEST_DASH }.minByOrNull { it.seq }
            ?: playable.filter { it.kind == MediaKind.PROGRESSIVE }.minByOrNull { it.seq }
    }
}

/**
 * Oynatici icin HTTP basliklarini hazirlar.
 */
object PlaybackHeaders {
    fun build(
        streamUrl: String,
        pageUrl: String,
        userAgent: String,
        sniffed: Map<String, String>
    ): Map<String, String> {
        val sniffedReferer = sniffed.header("Referer")
        val origin = sniffed.header("Origin")
            ?: sniffedReferer?.let(::originOf)?.takeIf { it != originOf(streamUrl) }
        val referer = sniffedReferer ?: pageUrl.ifEmpty { null }
        return buildMap {
            put("User-Agent", userAgent)
            referer?.let { put("Referer", it) }
            origin?.let { put("Origin", it) }
        }
    }

    fun originOf(url: String): String? {
        val uri = try { java.net.URI(url) } catch (e: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    private fun Map<String, String>.header(name: String): String? =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.ifEmpty { null }
}
