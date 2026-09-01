package com.tvapp.livetv.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tvapp.livetv.data.local.IptvChannelEntity
import com.tvapp.livetv.data.local.IptvSourceEntity
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.tifinput.IptvInputSyncScheduler
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

data class IptvSourceSummary(
    val source: IptvSourceEntity,
    val channelCount: Int,
    val selectedChannelCount: Int,
)

data class IptvImportResult(
    val sourceId: Long,
    val sourceName: String,
    val channelCount: Int,
)

class IptvRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = TVAppDatabase.getInstance(appContext)
    private val dao = database.iptvDao()

    suspend fun sources(): List<IptvSourceSummary> = dao.getSources().map { source ->
        IptvSourceSummary(
            source,
            dao.channelCount(source.id),
            dao.selectedChannelCount(source.id),
        )
    }

    suspend fun sourceChannels(sourceId: Long): List<IptvChannelEntity> =
        dao.getChannelsForSource(sourceId)

    suspend fun sourceCategories(sourceId: Long): List<String> =
        dao.getCategoriesForSource(sourceId)

    suspend fun selectionPage(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<IptvChannelEntity> = dao.getSelectionPage(
        sourceId,
        category,
        query,
        selectedOnly,
        limit,
        offset,
    )

    suspend fun selectionCount(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
    ): Int = dao.selectionCount(sourceId, category, query, selectedOnly)

    suspend fun selectedChannelCount(sourceId: Long): Int = dao.selectedChannelCount(sourceId)

    suspend fun setChannelSelected(sourceKey: String, selected: Boolean) {
        dao.setChannelSelected(sourceKey, selected)
        notifySharedChannelsChanged()
    }

    suspend fun setChannelsSelected(sourceKeys: List<String>, selected: Boolean) {
        sourceKeys.chunked(SELECTION_UPDATE_CHUNK_SIZE).forEach { chunk ->
            if (chunk.isNotEmpty()) dao.setChannelsSelected(chunk, selected)
        }
        if (sourceKeys.isNotEmpty()) notifySharedChannelsChanged()
    }

    suspend fun libraryLiveChannelsPage(
        sourceId: Long,
        category: String?,
        contentType: String,
        limit: Int,
        offset: Int,
    ): List<LiveChannel> = dao.getLibraryPage(sourceId, category, contentType, limit, offset)
        .map { it.toLiveChannel() }

    suspend fun libraryChannelCount(
        sourceId: Long,
        category: String?,
        contentType: String,
    ): Int = dao.libraryCount(sourceId, category, contentType)

    suspend fun libraryChannels(sourceId: Long?): List<IptvChannelEntity> =
        sourceId?.let { dao.getChannelsForSource(it) } ?: dao.getAllEnabledLibraryChannels()

    suspend fun channel(sourceKey: String): LiveChannel? = dao.getChannel(sourceKey)?.toLiveChannel()

    suspend fun libraryLiveChannels(sourceId: Long?, category: String?): List<LiveChannel> =
        libraryChannels(sourceId)
            .asSequence()
            .filter { category == null || it.groupTitle?.trim() == category }
            .map { it.toLiveChannel() }
            .toList()

    suspend fun setSelectedChannels(sourceId: Long, sourceKeys: Set<String>) {
        database.withTransaction {
            dao.clearSelectedChannels(sourceId)
            sourceKeys.chunked(SELECTION_UPDATE_CHUNK_SIZE).forEach { chunk ->
                if (chunk.isNotEmpty()) dao.selectChannels(chunk)
            }
        }
        notifySharedChannelsChanged()
    }

    suspend fun channels(): List<LiveChannel> = dao.getEnabledChannels().map { channel ->
        channel.toLiveChannel()
    }

    private fun IptvChannelEntity.toLiveChannel() =
        LiveChannel(
            id = stableLongId(sourceKey),
            sourceKey = sourceKey,
            inputId = "iptv:$sourceId",
            displayNumber = (originalIndex + 1).toString(),
            displayName = displayName,
            uri = streamUrl,
            source = LiveChannel.Source.IPTV,
            logoUrl = logoUrl,
            groupTitle = groupTitle,
            epgId = tvgId,
            userAgent = userAgent,
            referrer = referrer,
            iptvContentType = contentType,
        )

    suspend fun importUrl(location: String, nameOverride: String? = null): IptvImportResult {
        val normalized = location.trim()
        require(normalized.startsWith("http://") || normalized.startsWith("https://"))
        val connection = URL(normalized).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "HTTP ${connection.responseCode} ${connection.responseMessage}"
            }
            val compressed = connection.contentEncoding.equals("gzip", ignoreCase = true) ||
                normalized.substringBefore('?').endsWith(".gz", ignoreCase = true)
            val input = if (compressed) GZIPInputStream(connection.inputStream) else connection.inputStream
            val derivedName = Uri.parse(normalized).lastPathSegment
                ?.substringBeforeLast('.')
                ?.takeIf(String::isNotBlank)
                ?: Uri.parse(normalized).host
                ?: "IPTV"
            val name = nameOverride?.trim()?.takeIf(String::isNotBlank) ?: derivedName
            return input.use { importStream(normalized, KIND_URL, name, it) }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun importDocument(uri: Uri, name: String): IptvImportResult {
        val input = checkNotNull(appContext.contentResolver.openInputStream(uri))
        val compressed = uri.lastPathSegment?.endsWith(".gz", ignoreCase = true) == true
        val decoded = if (compressed) GZIPInputStream(input) else input
        return decoded.use { importStream(uri.toString(), KIND_DOCUMENT, name, it) }
    }

    suspend fun refresh(source: IptvSourceEntity): IptvImportResult = when (source.kind) {
        KIND_URL -> importUrl(source.location, source.name)
        KIND_DOCUMENT -> importDocument(Uri.parse(source.location), source.name)
        else -> error("Bilinmeyen IPTV kaynak türü: ${source.kind}")
    }

    suspend fun delete(source: IptvSourceEntity) {
        dao.deleteSource(source)
        notifySharedChannelsChanged()
    }

    private suspend fun importStream(
        location: String,
        kind: String,
        name: String,
        input: InputStream,
    ): IptvImportResult {
        val now = System.currentTimeMillis()
        val result = database.withTransaction {
            val existing = dao.getSourceByLocation(location)
            val sourceId = existing?.id ?: dao.insertSource(
                IptvSourceEntity(name = name, location = location, kind = kind),
            )
            check(sourceId > 0) { "IPTV kaynağı kaydedilemedi." }
            val selectedChannels = if (existing == null) {
                emptyList()
            } else {
                dao.getSelectedChannelsForSource(sourceId)
            }
            val selectedKeys = selectedChannels.mapTo(mutableSetOf()) { it.sourceKey }
            val selectedTvgIds = selectedChannels.mapNotNullTo(mutableSetOf()) {
                it.tvgId?.trim()?.takeIf(String::isNotBlank)
            }
            val selectedNames = selectedChannels.asSequence()
                .filter { it.tvgId.isNullOrBlank() }
                .mapTo(mutableSetOf()) { selectionName(it.displayName, it.groupTitle) }
            val source = (existing ?: IptvSourceEntity(
                id = sourceId,
                name = name,
                location = location,
                kind = kind,
            )).copy(name = name, kind = kind, enabled = true, lastUpdatedAt = now)
            dao.updateSource(source)
            dao.deleteChannelsForSource(sourceId)
            val batch = ArrayList<IptvChannelEntity>(IMPORT_BATCH_SIZE)
            var channelCount = 0
            fun entity(index: Int, item: ParsedIptvChannel): IptvChannelEntity {
                val tvgId = item.tvgId?.trim()?.takeIf(String::isNotBlank)
                val identity = tvgId?.let { "id:$it" } ?: listOf(
                    "name:${selectionName(item.name, item.groupTitle)}",
                    item.streamUrl.substringBefore('?'),
                ).joinToString("|")
                val sourceKey = "iptv:$sourceId:${sha256(identity).take(24)}"
                return IptvChannelEntity(
                    sourceKey = sourceKey,
                    sourceId = sourceId,
                    tvgId = item.tvgId,
                    tvgName = item.tvgName,
                    displayName = item.name,
                    streamUrl = item.streamUrl,
                    logoUrl = item.logoUrl,
                    groupTitle = item.groupTitle,
                    userAgent = item.userAgent,
                    referrer = item.referrer,
                    originalIndex = index,
                    contentType = item.contentType,
                    selected = sourceKey in selectedKeys ||
                        tvgId != null && tvgId in selectedTvgIds ||
                        tvgId == null && selectionName(item.name, item.groupTitle) in selectedNames,
                    lastSeenAt = now,
                )
            }
            for (item in M3uParser.sequence(InputStreamReader(input, Charsets.UTF_8))) {
                batch += entity(channelCount, item)
                channelCount++
                if (batch.size >= IMPORT_BATCH_SIZE) {
                    dao.upsertChannels(batch.toList())
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) dao.upsertChannels(batch)
            require(channelCount > 0) { "Listede oynatılabilir IPTV kanalı bulunamadı." }
            IptvImportResult(sourceId, source.name, channelCount)
        }
        notifySharedChannelsChanged()
        return result
    }

    private fun notifySharedChannelsChanged() {
        IptvInputSyncScheduler.scheduleImmediate(appContext)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun stableLongId(value: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        var result = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            result = (result shl 8) or (digest[index].toLong() and 0xff)
        }
        return result or Long.MIN_VALUE
    }

    private fun selectionName(name: String, group: String?): String =
        "${group.orEmpty().trim().lowercase()}|${name.trim().lowercase()}"

    companion object {
        const val KIND_URL = "URL"
        const val KIND_DOCUMENT = "DOCUMENT"
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val DEFAULT_USER_AGENT = "TVApp/0.1 AndroidTV"
        private const val SELECTION_UPDATE_CHUNK_SIZE = 500
        private const val IMPORT_BATCH_SIZE = 500
    }
}
