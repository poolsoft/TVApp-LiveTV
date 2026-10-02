package com.tvapp.livetv.data

import androidx.sqlite.db.SimpleSQLiteQuery

data class VodFilter(
    val category: String? = null,
    val query: String = "",
    val section: String = "MOVIE",
    val view: String = "ALL",
    val order: String = "SOURCE",
    val parentKey: String? = null,
    val season: Int? = null,
)

/** Queries stay bounded; favorites live in user_channels, not in duplicated VOD flags. */
object VodCatalogQuery {
    private const val CATALOG = """
        SELECT c.sourceKey, c.sourceId, COALESCE(u.customName,c.displayName) AS name,
          COALESCE(m.logoUrl,c.logoUrl) AS logoUrl, c.groupTitle AS category,
          CASE WHEN INSTR(LOWER(c.streamUrl),'/series/') > 0 THEN 'EPISODE' ELSE 'MOVIE' END AS kind,
          m.providerId, NULL AS parentKey, NULL AS seasonNumber, NULL AS episodeNumber,
          m.description, m.durationMillis, c.originalIndex AS ordinal, COALESCE(u.favorite,0) AS favorite
        FROM iptv_channels c LEFT JOIN user_channels u ON u.sourceKey=c.sourceKey
        LEFT JOIN vod_metadata m ON m.sourceKey=c.sourceKey
        WHERE c.contentType='VOD'
        UNION ALL
        SELECT m.sourceKey,m.sourceId,COALESCE(u.customName,m.name),m.logoUrl,m.category,
          m.kind,m.providerId,m.parentKey,m.seasonNumber,m.episodeNumber,m.description,m.durationMillis,
          COALESCE(m.episodeNumber,0),COALESCE(u.favorite,0)
        FROM vod_metadata m LEFT JOIN user_channels u ON u.sourceKey=m.sourceKey
        WHERE m.kind IN ('SERIES','EPISODE')
    """

    fun item(key: String) = SimpleSQLiteQuery("SELECT * FROM ($CATALOG) WHERE sourceKey=? LIMIT 1", arrayOf(key))

    fun build(sourceId: Long, filter: VodFilter, keys: List<String> = emptyList(),
        offset: Int = 0, limit: Int = 60, count: Boolean = false, categories: Boolean = false): SimpleSQLiteQuery {
        val args = mutableListOf<Any>(sourceId)
        val where = StringBuilder("sourceId=?")
        if (filter.parentKey != null) {
            where.append(" AND parentKey=?"); args += filter.parentKey
            filter.season?.let { where.append(" AND seasonNumber=?"); args += it }
        } else {
            where.append(if (filter.section == "SERIES") " AND kind IN ('SERIES','EPISODE')" else " AND kind='MOVIE'")
            if (filter.view == "ALL") where.append(" AND (kind!='EPISODE' OR parentKey IS NULL)")
        }
        if (!categories && filter.category != null) { where.append(" AND category=?"); args += filter.category }
        if (filter.query.isNotBlank() && !categories) {
            where.append(" AND name LIKE ? ESCAPE '\\'")
            args += "%${filter.query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        }
        if (!categories && filter.view == "FAVORITES") where.append(" AND favorite=1")
        if (!categories && filter.view in listOf("RECENT", "CONTINUE")) {
            if (keys.isEmpty()) where.append(" AND 0") else {
                where.append(" AND sourceKey IN (${keys.joinToString(",") { "?" }})"); args.addAll(keys)
            }
        }
        val select = when { count -> "COUNT(*)"; categories -> "DISTINCT category"; else -> "*" }
        val sql = StringBuilder("SELECT $select FROM ($CATALOG) WHERE $where")
        if (categories) sql.append(" AND category IS NOT NULL AND TRIM(category)!='' ORDER BY category COLLATE NOCASE")
        else if (!count) {
            sql.append(" ORDER BY ")
            if (filter.view in listOf("RECENT", "CONTINUE") && keys.isNotEmpty()) {
                sql.append("CASE sourceKey ")
                keys.forEachIndexed { i, key -> sql.append("WHEN ? THEN $i "); args += key }
                sql.append("ELSE ${keys.size} END,")
            }
            sql.append(if (filter.order == "NAME") "name COLLATE NOCASE,sourceKey" else "ordinal,sourceKey")
            sql.append(" LIMIT ? OFFSET ?"); args += limit.coerceIn(1, 60); args += offset.coerceAtLeast(0)
        }
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
