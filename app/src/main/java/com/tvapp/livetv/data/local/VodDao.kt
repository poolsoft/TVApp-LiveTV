package com.tvapp.livetv.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface VodDao {
    @RawQuery suspend fun page(query: SupportSQLiteQuery): List<VodCatalogItem>
    @RawQuery suspend fun count(query: SupportSQLiteQuery): Int
    @RawQuery suspend fun categories(query: SupportSQLiteQuery): List<String>
    @Query("SELECT * FROM vod_metadata WHERE sourceKey = :key")
    suspend fun metadata(key: String): VodMetadataEntity?
    @Query("SELECT DISTINCT seasonNumber FROM vod_metadata WHERE parentKey = :key AND seasonNumber IS NOT NULL ORDER BY seasonNumber")
    suspend fun seasons(key: String): List<Int>
    @Query("SELECT MIN(updatedAt) FROM vod_metadata WHERE parentKey = :key")
    suspend fun episodesUpdatedAt(key: String): Long?
    @Query("DELETE FROM vod_metadata WHERE sourceId = :sourceId AND kind = 'SERIES' AND updatedAt != :updatedAt")
    suspend fun pruneSeries(sourceId: Long, updatedAt: Long)
    @Query("DELETE FROM vod_metadata WHERE parentKey = :parent AND updatedAt != :updatedAt")
    suspend fun pruneEpisodes(parent: String, updatedAt: Long)
    @Upsert suspend fun upsert(items: List<VodMetadataEntity>)
}
