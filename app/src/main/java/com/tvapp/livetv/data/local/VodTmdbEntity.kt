package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "vod_tmdb_cache")
data class VodTmdbEntity(
    @PrimaryKey val sourceKey: String,
    val tmdbId: Int,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Double = 0.0,
    val releaseYear: String? = null,
    val genres: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface VodTmdbDao {
    @Query("SELECT * FROM vod_tmdb_cache WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getBySourceKey(sourceKey: String): VodTmdbEntity?

    @Query("SELECT * FROM vod_tmdb_cache WHERE sourceKey IN (:sourceKeys)")
    suspend fun getBySourceKeys(sourceKeys: List<String>): List<VodTmdbEntity>

    @Upsert
    suspend fun upsert(item: VodTmdbEntity)

    @Upsert
    suspend fun upsertAll(items: List<VodTmdbEntity>)
}
