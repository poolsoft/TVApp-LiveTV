package com.tvapp.livetv.data

internal object IptvFtsQuery {
    private val termPattern = Regex("[\\p{L}\\p{N}]+")

    fun from(rawQuery: String): String {
        if (rawQuery.isBlank()) return ""
        val terms = termPattern.findAll(rawQuery)
            .map(MatchResult::value)
            .distinct()
            .toList()
        if (terms.isEmpty()) return NO_MATCH_QUERY
        return terms.joinToString(" ") { term -> "$term*" }
    }

    private const val NO_MATCH_QUERY = "tvappnomatchtoken*"
}
