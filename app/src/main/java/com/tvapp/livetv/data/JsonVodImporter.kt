package com.tvapp.livetv.data

import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.VodMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Imports JSON formatted VOD libraries (series, seasons, episodes and movies)
 * such as dizitime.json directly into TVApp's vod_metadata database.
 */
object JsonVodImporter {

    suspend fun importJsonVod(
        sourceId: Long,
        jsonText: String,
        database: TVAppDatabase,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val array = JSONArray(jsonText)
        val total = array.length()
        val now = System.currentTimeMillis()
        val metadataBatch = mutableListOf<VodMetadataEntity>()
        var count = 0

        for (i in 0 until total) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim().ifEmpty {
                item.optString("original_title").trim()
            }
            if (title.isEmpty()) continue

            val type = item.optString("type").lowercase()
            val poster = item.optString("poster").trim().ifEmpty {
                item.optString("banner").trim()
            }.takeIf { it.isNotEmpty() }
            val description = item.optString("description").trim().takeIf { it.isNotEmpty() }
            val category = item.optString("category").trim().ifEmpty {
                val genres = item.optJSONArray("genres")
                if (genres != null && genres.length() > 0) genres.optString(0).trim()
                else item.optString("platform").trim()
            }.takeIf { it.isNotEmpty() } ?: "VOD"

            val episodesArray = item.optJSONArray("episodes")
            val isSeries = type in listOf("dizi", "series", "tv", "show") || (episodesArray != null && episodesArray.length() > 0)

            if (isSeries) {
                val seriesSlug = hashString("$sourceId:series:$title")
                val seriesKey = "iptv:$sourceId:series:$seriesSlug"
                val episodesRaw = episodesArray?.toString()

                // Add Series Parent Entity with episodes JSON stored in streamUrl
                metadataBatch.add(
                    VodMetadataEntity(
                        sourceKey = seriesKey,
                        sourceId = sourceId,
                        kind = "SERIES",
                        providerId = "json_series_$i",
                        parentKey = null,
                        seasonNumber = null,
                        episodeNumber = null,
                        name = title,
                        logoUrl = poster,
                        category = category,
                        description = description,
                        durationMillis = null,
                        streamUrl = episodesRaw,
                        updatedAt = now,
                    )
                )
                count++
            } else {
                // Standalone Movie
                val videoUrl = item.optString("videoUrl").trim().ifEmpty {
                    item.optString("url").trim()
                }
                if (videoUrl.isNotEmpty()) {
                    val movieSlug = hashString("$sourceId:movie:$title")
                    val movieKey = "iptv:$sourceId:movie:$movieSlug"

                    metadataBatch.add(
                        VodMetadataEntity(
                            sourceKey = movieKey,
                            sourceId = sourceId,
                            kind = "MOVIE",
                            providerId = "json_movie_$i",
                            parentKey = null,
                            seasonNumber = null,
                            episodeNumber = null,
                            name = title,
                            logoUrl = poster,
                            category = category,
                            description = description,
                            durationMillis = null,
                            streamUrl = videoUrl,
                            updatedAt = now,
                        )
                    )
                    count++
                }
            }

            // Save in batches of 400 items to avoid memory & SQLite statement limits
            if (metadataBatch.size >= 400) {
                database.vodDao().upsert(metadataBatch)
                metadataBatch.clear()
                onProgress(i + 1, total)
            }
        }

        if (metadataBatch.isNotEmpty()) {
            database.vodDao().upsert(metadataBatch)
            metadataBatch.clear()
            onProgress(total, total)
        }

        count
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
