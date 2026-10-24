package com.tvapp.livetv.browser

import java.net.URLEncoder

/**
 * URL host cozumleme yardimcisi.
 */
object UrlHost {
    fun of(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']').removePrefix("[")
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase().trimEnd('.').ifEmpty { null }
    }
}

/**
 * Kullanicinin girdigi metni gecerli bir URL'ye veya Google arama sorgusuna donusturur.
 */
object UrlNormalizer {
    private val SCHEME = Regex("(?i)^[a-z][a-z0-9+.-]*://")

    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"

        // Eger bosluk iceriyorsa veya nokta barindirmiyorsa arama olarak degerlendir
        if (trimmed.contains(' ') || (!trimmed.contains('.') && !SCHEME.containsMatchIn(trimmed))) {
            val query = runCatching { URLEncoder.encode(trimmed, "UTF-8") }.getOrDefault(trimmed)
            return "https://www.google.com/search?q=$query"
        }

        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val host = UrlHost.of(withScheme)
        return if (host != null && (host == "localhost" || host.contains('.'))) {
            withScheme
        } else {
            val query = runCatching { URLEncoder.encode(trimmed, "UTF-8") }.getOrDefault(trimmed)
            "https://www.google.com/search?q=$query"
        }
    }
}
