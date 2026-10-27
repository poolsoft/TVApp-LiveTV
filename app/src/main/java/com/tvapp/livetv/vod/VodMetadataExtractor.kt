package com.tvapp.livetv.vod

import java.util.regex.Pattern

/**
 * Sayfa HTML'inden oynatilabilir video adaylarini cikaran site-bagimsiz katman.
 *
 * Sira (ilk bulunan kazanir):
 *  1. JSON-LD VideoObject `contentUrl` (Kanal D ornegi: `.smil/.m3u8`)
 *  2. OpenGraph / Twitter player akis etiketleri (`og:video`, `twitter:player:stream`)
 *  3. Microdata `itemprop=embedURL|contentUrl`
 *  4. Jenerik JS blob desenleri (`"file"`, `"source"`, `"playbackUrl"` ...)
 *  5. Ciplak `.m3u8` / `master.txt` / `.mp4` baglantilari
 *
 * Iframe/embed URL'leri ayri listede doner; cozuc surdurucu (recursion) bunlari
 * ayni cikarmayla tekrar acar (`src` + `data-src` + lazy varyantlari).
 *
 * Saf JVM kodudur; Android bagimliligi yoktur.
 */
object VodMetadataExtractor {

    data class Extraction(
        val streamUrl: String?,
        val embedUrls: List<String>,
    )

    private val JSON_LD_BLOCK = Pattern.compile(
        "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
    )
    private val CONTENT_URL = Pattern.compile(
        "\"contentUrl\"\\s*:\\s*\\[?\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE,
    )
    private val EMBED_URL_JSON = Pattern.compile(
        "\"embedUrl\"\\s*:\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE,
    )
    private val OG_VIDEO = Pattern.compile(
        "<meta[^>]+(?:property|name)=[\"'](?:og:video(?::(?:url|secure_url))?|twitter:player:stream)[\"'][^>]*content=[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE,
    )
    private val OG_VIDEO_REVERSED = Pattern.compile(
        "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*?(?:property|name)=[\"'](?:og:video(?::(?:url|secure_url))?|twitter:player:stream)[\"']",
        Pattern.CASE_INSENSITIVE,
    )
    private val MICRODATA_EMBED = Pattern.compile(
        "<(?:link|meta|iframe)[^>]+itemprop=[\"'](?:embedURL|embedUrl|contentUrl)[\"'][^>]*?(?:href|content|src)=[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE,
    )
    private val MICRODATA_EMBED_REVERSED = Pattern.compile(
        "<(?:link|meta|iframe)[^>]+(?:href|content|src)=[\"']([^\"']+)[\"'][^>]*?itemprop=[\"'](?:embedURL|embedUrl|contentUrl)[\"']",
        Pattern.CASE_INSENSITIVE,
    )
    private val IFRAME_ANY_SRC = Pattern.compile(
        "<iframe[^>]+?(?:src|data-src|data-lazy-src|data-href)\\s*=\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE,
    )
    private val JS_FILE_ATTRS = Pattern.compile(
        "\"(?:file|source|playbackUrl|streamUrl|hlsUrl|videoUrl|contentUrl)\"\\s*:\\s*(?:\\[\\s*)?[\"'](https?:[^\"']+?)[\"']",
        Pattern.CASE_INSENSITIVE,
    )
    private val M3U8_URL = Pattern.compile(
        "https?://[^\\s\"'<>\\\\]+\\.m3u8[^\\s\"'<>\\\\]*",
        Pattern.CASE_INSENSITIVE,
    )
    private val MASQUERADED_HLS_URL = Pattern.compile(
        "https?://[^\\s\"'<>\\\\]+(?:master\\.txt|playlist\\.txt)[^\\s\"'<>\\\\]*",
        Pattern.CASE_INSENSITIVE,
    )
    private val SMIL_HLS_URL = Pattern.compile(
        "https?://[^\\s\"'<>\\\\]+\\.smil/[^\\s\"'<>\\\\]*\\.m3u8[^\\s\"'<>\\\\]*",
        Pattern.CASE_INSENSITIVE,
    )
    private val MP4_URL = Pattern.compile(
        "https?://[^\\s\"'<>\\\\]+\\.mp4[^\\s\"'<>\\\\]*",
        Pattern.CASE_INSENSITIVE,
    )

    fun extract(html: String, pageUrl: String): Extraction {
        if (html.isBlank()) return Extraction(null, emptyList())

        val embeds = linkedSetOf<String>()
        IFRAME_ANY_SRC.matcher(html).let { m ->
            while (m.find()) {
                resolveUrl(pageUrl, cleanToken(m.group(1)))?.let { embeds.add(it) }
                if (embeds.size >= 8) break
            }
        }

        // 1. JSON-LD bloklari
        val jsonLdBlocks = mutableListOf<String>()
        JSON_LD_BLOCK.matcher(html).let { m ->
            while (m.find()) {
                jsonLdBlocks.add(m.group(1).orEmpty())
            }
        }
        for (block in jsonLdBlocks) {
            CONTENT_URL.matcher(block).let { m ->
                while (m.find()) {
                    val cleaned = cleanToken(m.group(1))
                    if (isUsableStream(cleaned)) return Extraction(cleaned, embeds.toList())
                }
            }
            EMBED_URL_JSON.matcher(block).let { m ->
                while (m.find()) {
                    resolveUrl(pageUrl, cleanToken(m.group(1)))?.let { embeds.add(it) }
                }
            }
        }

        // 2. OpenGraph / Twitter
        listOf(OG_VIDEO, OG_VIDEO_REVERSED).forEach { pattern ->
            pattern.matcher(html).let { m ->
                while (m.find()) {
                    val cleaned = cleanToken(m.group(1))
                    if (isUsableStream(cleaned)) return Extraction(cleaned, embeds.toList())
                }
            }
        }

        // 3. Microdata
        listOf(MICRODATA_EMBED, MICRODATA_EMBED_REVERSED).forEach { pattern ->
            pattern.matcher(html).let { m ->
                while (m.find()) {
                    val raw = cleanToken(m.group(1))
                    if (isUsableStream(raw)) return Extraction(raw, embeds.toList())
                    resolveUrl(pageUrl, raw)?.let {
                        if (!isStaticAsset(it)) embeds.add(it)
                    }
                }
            }
        }

        // 4. Jenerik JS blob'lari
        JS_FILE_ATTRS.matcher(html).let { m ->
            while (m.find()) {
                val cleaned = cleanToken(m.group(1))
                if (isUsableStream(cleaned)) return Extraction(cleaned, embeds.toList())
            }
        }

        // 5. Ciplak akis baglantilari (once gizli HLS, sonra m3u8, sonra mp4)
        for (pattern in listOf(SMIL_HLS_URL, MASQUERADED_HLS_URL, M3U8_URL, MP4_URL)) {
            pattern.matcher(html).let { m ->
                while (m.find()) {
                    val cleaned = cleanToken(m.group(0))
                    if (isUsableStream(cleaned)) return Extraction(cleaned, embeds.toList())
                }
            }
        }

        return Extraction(null, embeds.toList())
    }

    private fun isUsableStream(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("http", ignoreCase = true)) return false
        if (url.contains("googletagmanager") || url.contains("google-analytics") ||
            url.contains("doubleclick") || url.contains("facebook.net")
        ) {
            return false
        }
        return VodUrlClassifier.isDirectStream(url)
    }

    private fun isStaticAsset(url: String): Boolean {
        val path = url.substringBefore("?").substringBefore("#").lowercase()
        return path.endsWith(".css") || path.endsWith(".png") || path.endsWith(".jpg") ||
            path.endsWith(".jpeg") || path.endsWith(".webp") || path.endsWith(".svg") ||
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
            path.endsWith(".ico")
    }

    private fun cleanToken(raw: String?): String {
        var s = raw.orEmpty().trim()
        s = s.replace("\\/", "/").replace("\\u0026", "&").replace("\\u0026amp;", "&")
        s = s.replace("&amp;", "&").replace("&#038;", "&")
        return s.trim()
    }

    private fun resolveUrl(pageUrl: String, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) {
            val scheme = try {
                java.net.URI(pageUrl).scheme ?: "https"
            } catch (_: Exception) {
                "https"
            }
            return "$scheme:$trimmed"
        }
        if (trimmed.startsWith("/")) {
            return VodUrlClassifierOrigin.originOf(pageUrl)?.let { "$it$trimmed" }
        }
        return null
    }

    /** Kucuk origin yardimcisi; `WebVideoResolver.originOf` ile ayni kural. */
    private object VodUrlClassifierOrigin {
        fun originOf(url: String): String? {
            val uri = try {
                java.net.URI(url)
            } catch (_: Exception) {
                return null
            }
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            val defaultPort = if (scheme == "https") 443 else 80
            val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
            return "$scheme://$host$port"
        }
    }
}
