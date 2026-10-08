package com.tvapp.livetv.data

import android.content.Context
import androidx.room.withTransaction
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.VodCatalogItem
import com.tvapp.livetv.data.local.VodMetadataEntity
import com.tvapp.livetv.model.LiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class VodRepository(context: Context, private val database: TVAppDatabase = TVAppDatabase.getInstance(context)) {
    private val dao = database.vodDao()
    private val preferences = context.getSharedPreferences("vod-cache", Context.MODE_PRIVATE)

    suspend fun page(sourceId: Long, filter: VodFilter, keys: List<String>, page: Int): List<VodCatalogItem> =
        dao.page(VodCatalogQuery.build(sourceId, filter, keys, page * PAGE_SIZE, PAGE_SIZE))
    suspend fun count(sourceId: Long, filter: VodFilter, keys: List<String>): Int =
        dao.count(VodCatalogQuery.build(sourceId, filter, keys, count = true))
    suspend fun categories(sourceId: Long, section: String): List<String> =
        dao.categories(VodCatalogQuery.build(sourceId, VodFilter(section = section), categories = true))
    suspend fun item(key: String): VodCatalogItem? {
        // Resolve a single item without loading its source catalog.
        return dao.page(VodCatalogQuery.item(key)).firstOrNull()
    }

    suspend fun refreshSeries(sourceId: Long, force: Boolean = false) {
        val source = database.iptvDao().getSource(sourceId) ?: return
        if (source.kind != IptvRepository.KIND_XTREAM) return
        val now = System.currentTimeMillis()
        if (!force && now - preferences.getLong("series:$sourceId", 0) in 0 until CACHE_TTL) return
        val client = client(sourceId) ?: return
        val batch = ArrayList<VodMetadataEntity>(100)
        for (row in client.series(sourceId, now)) {
            currentCoroutineContext().ensureActive()
            batch += row
            if (batch.size == 100) { dao.upsert(batch.toList()); batch.clear() }
        }
        if (batch.isNotEmpty()) dao.upsert(batch)
        dao.pruneSeries(sourceId, now)
        preferences.edit().putLong("series:$sourceId", now).apply()
    }

    suspend fun refreshEpisodes(key: String) {
        val series = dao.metadata(key) ?: return
        val now = System.currentTimeMillis()
        if (now - (dao.episodesUpdatedAt(key) ?: 0) in 0 until CACHE_TTL) return
        val client = client(series.sourceId) ?: return
        val batch = ArrayList<VodMetadataEntity>(100)
        for (row in client.episodes(series, now)) {
            currentCoroutineContext().ensureActive()
            batch += row
            if (batch.size == 100) { dao.upsert(batch.toList()); batch.clear() }
        }
        if (batch.isNotEmpty()) dao.upsert(batch)
        dao.pruneEpisodes(key, now)
    }

    suspend fun seasons(key: String): List<Int> = dao.seasons(key)
    suspend fun nextEpisode(key: String): VodCatalogItem? = dao.nextEpisode(key)?.let { item(it.sourceKey) }

    suspend fun continueItems(sourceId: Long, section: String, keys: List<String>): List<VodCatalogItem> {
        val rows = dao.page(VodCatalogQuery.resumeItems(keys)).associateBy { it.sourceKey }
        return keys.take(100).mapNotNull(rows::get).filter {
            it.sourceId == sourceId && if (section == "SERIES") it.kind == "EPISODE" else it.kind == "MOVIE"
        }.distinctBy { it.parentKey ?: it.sourceKey }
    }

    suspend fun details(item: VodCatalogItem): VodCatalogItem {
        if (item.kind != "MOVIE") return item
        val cached = dao.metadata(item.sourceKey)
        if (cached == null || System.currentTimeMillis() - cached.updatedAt > CACHE_TTL) {
            val channel = database.iptvDao().getChannel(item.sourceKey) ?: return item
            val streamId = android.net.Uri.parse(channel.streamUrl).lastPathSegment?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return item
            client(item.sourceId)?.movieDetails(item.sourceKey, item.sourceId, streamId, System.currentTimeMillis())
                ?.let { dao.upsert(listOf(it)) }
        }
        return this.item(item.sourceKey) ?: item
    }

    suspend fun setFavorite(item: VodCatalogItem, favorite: Boolean) = database.withTransaction {
        val userDao = database.channelDao()
        val row = userDao.getChannel(item.sourceKey) ?: com.tvapp.livetv.data.local.UserChannelEntity(
            sourceKey = item.sourceKey, sourceType = "IPTV", originalDisplayNumber = "",
            lastKnownName = item.name, sortOrder = (userDao.maxSortOrder() ?: -1) + 1,
            lastSeenAt = System.currentTimeMillis())
        userDao.upsertChannels(listOf(row.copy(favorite = favorite)))
    }

    private suspend fun client(sourceId: Long): XtreamClient? {
        val source = database.iptvDao().getSource(sourceId) ?: return null
        if (source.kind != IptvRepository.KIND_XTREAM) return null
        return XtreamClient(source.serverUrl ?: return null, source.username ?: return null, source.password ?: return null)
    }

    companion object {
        const val PAGE_SIZE = 60
        private const val CACHE_TTL = 6 * 60 * 60 * 1000L
        fun playbackChannel(metadata: VodMetadataEntity): LiveChannel? {
            val uri = metadata.streamUrl?.takeIf(String::isNotBlank) ?: return null
            if (metadata.kind != "EPISODE") return null
            return LiveChannel(id = metadata.sourceKey.fold(1125899906842597L) { hash, ch -> 31 * hash + ch.code },
                sourceKey = metadata.sourceKey, inputId = "iptv:${metadata.sourceId}", displayNumber = "",
                displayName = metadata.name, uri = uri, source = LiveChannel.Source.IPTV,
                logoUrl = metadata.logoUrl, groupTitle = metadata.category, iptvContentType = "VOD")
        }
    }
}
