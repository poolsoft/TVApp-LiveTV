package com.tvapp.livetv.vod

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TmdbItem(
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double,
    val releaseDate: String?,
    val releaseYear: String?,
    val genres: List<String>,
)

object TmdbApiClient {
    // TMDB V3 API anahtari (kullanici ayarlarindan degistirilebilir veya varsayilan kullanilir)
    // Standart acik gelistirici erisim anahtari
    private const val DEFAULT_API_KEY = "84143a5323863412353347c6a953e5dc"
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE_POSTER = "https://image.tmdb.org/t/p/w500"
    private const val IMAGE_BASE_BACKDROP = "https://image.tmdb.org/t/p/w1280"

    private val GENRE_MAP_TR = mapOf(
        28 to "Aksiyon",
        12 to "Macera",
        16 to "Animasyon",
        35 to "Komedi",
        80 to "Suç",
        99 to "Belgesel",
        18 to "Dram",
        10751 to "Aile",
        14 to "Fantastik",
        36 to "Tarih",
        27 to "Korku",
        10402 to "Müzik",
        9648 to "Gizem",
        10749 to "Romantik",
        878 to "Bilim Kurgu",
        10770 to "TV Filmi",
        53 to "Gerilim",
        10752 to "Savaş",
        37 to "Vahşi Batı",
        // TV Türleri
        10759 to "Aksiyon & Macera",
        10762 to "Çocuk",
        10765 to "Bilim Kurgu & Fantazi",
    )

    private fun getEffectiveApiKey(paramKey: String?): String {
        return if (!paramKey.isNullOrBlank()) paramKey
        else if (com.tvapp.livetv.BuildConfig.TMDB_API_KEY.isNotBlank()) com.tvapp.livetv.BuildConfig.TMDB_API_KEY
        else DEFAULT_API_KEY
    }

    suspend fun searchMovie(query: String, year: String? = null, apiKey: String? = null): TmdbItem? = withContext(Dispatchers.IO) {
        val key = getEffectiveApiKey(apiKey)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        var endpoint = "$BASE_URL/search/movie?api_key=$key&query=$encodedQuery&language=tr-TR&include_adult=false"
        if (!year.isNullOrBlank()) {
            endpoint += "&year=$year"
        }

        val json = fetchJson(endpoint) ?: return@withContext null
        parseFirstResult(json, isMovie = true)
    }

    suspend fun searchTvShow(query: String, year: String? = null, apiKey: String? = null): TmdbItem? = withContext(Dispatchers.IO) {
        val key = getEffectiveApiKey(apiKey)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        var endpoint = "$BASE_URL/search/tv?api_key=$key&query=$encodedQuery&language=tr-TR&include_adult=false"
        if (!year.isNullOrBlank()) {
            endpoint += "&first_air_date_year=$year"
        }

        val json = fetchJson(endpoint) ?: return@withContext null
        parseFirstResult(json, isMovie = false)
    }

    suspend fun getTrendingMovies(apiKey: String? = null): List<TmdbItem> = withContext(Dispatchers.IO) {
        val key = getEffectiveApiKey(apiKey)
        val endpoint = "$BASE_URL/trending/movie/week?api_key=$key&language=tr-TR"
        val json = fetchJson(endpoint) ?: return@withContext emptyList()
        parseListResults(json, isMovie = true)
    }

    private fun fetchJson(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFirstResult(jsonString: String, isMovie: Boolean): TmdbItem? {
        return try {
            val root = JSONObject(jsonString)
            val results = root.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            parseItem(results.getJSONObject(0), isMovie)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseListResults(jsonString: String, isMovie: Boolean): List<TmdbItem> {
        val list = mutableListOf<TmdbItem>()
        try {
            val root = JSONObject(jsonString)
            val results = root.optJSONArray("results") ?: return emptyList()
            for (i in 0 until results.length()) {
                val item = parseItem(results.getJSONObject(i), isMovie)
                if (item != null) list.add(item)
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return list
    }

    private fun parseItem(obj: JSONObject, isMovie: Boolean): TmdbItem? {
        val id = obj.optInt("id", 0)
        if (id == 0) return null

        val title = if (isMovie) obj.optString("title", "") else obj.optString("name", "")
        if (title.isBlank()) return null

        val originalTitle = if (isMovie) obj.optString("original_title") else obj.optString("original_name")
        val overview = obj.optString("overview", "")
        val posterPath = obj.optString("poster_path", "")
        val backdropPath = obj.optString("backdrop_path", "")
        val voteAverage = obj.optDouble("vote_average", 0.0)
        val releaseDate = if (isMovie) obj.optString("release_date", "") else obj.optString("first_air_date", "")
        val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else null

        val genreIds = obj.optJSONArray("genre_ids")
        val genres = mutableListOf<String>()
        if (genreIds != null) {
            for (i in 0 until genreIds.length()) {
                val gid = genreIds.optInt(i)
                GENRE_MAP_TR[gid]?.let { genres.add(it) }
            }
        }

        return TmdbItem(
            id = id,
            title = title,
            originalTitle = if (originalTitle.isNotBlank()) originalTitle else null,
            overview = if (overview.isNotBlank()) overview else null,
            posterUrl = if (posterPath.isNotBlank()) "$IMAGE_BASE_POSTER$posterPath" else null,
            backdropUrl = if (backdropPath.isNotBlank()) "$IMAGE_BASE_BACKDROP$backdropPath" else null,
            rating = Math.round(voteAverage * 10.0) / 10.0,
            releaseDate = if (releaseDate.isNotBlank()) releaseDate else null,
            releaseYear = year,
            genres = genres,
        )
    }
}
