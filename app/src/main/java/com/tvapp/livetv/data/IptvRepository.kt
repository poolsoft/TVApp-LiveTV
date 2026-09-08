package com.tvapp.livetv.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tvapp.livetv.data.local.IptvChannelEntity
import com.tvapp.livetv.data.local.IptvChannelListProjection
import com.tvapp.livetv.data.local.IptvChannelStagingEntity
import com.tvapp.livetv.data.local.IptvSourceEntity
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.tifinput.IptvInputSyncScheduler
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

data class IptvStatistics(
    val sourceCount: Int,
    val channelCount: Int,
    val selectedChannelCount: Int,
)

enum class IptvPageDirection { FIRST, NEXT, PREVIOUS, LAST, AT_INDEX }

data class IptvPageAnchor(val originalIndex: Int, val sourceKey: String)

data class IptvLibraryPage(
    val channels: List<LiveChannel>,
    val firstAnchor: IptvPageAnchor?,
    val lastAnchor: IptvPageAnchor?,
)

class IptvRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = TVAppDatabase.getInstance(appContext)
    private val dao = database.iptvDao()
    private val xmlTvRepository by lazy { XmlTvRepository(appContext) }

    suspend fun sources(): List<IptvSourceSummary> = dao.getSources().map { source ->
        IptvSourceSummary(
            source,
            dao.channelCount(source.id),
            dao.selectedChannelCount(source.id),
        )
    }

    suspend fun statistics(): IptvStatistics {
        val sources = sources()
        return IptvStatistics(
            sourceCount = sources.size,
            channelCount = sources.sumOf(IptvSourceSummary::channelCount),
            selectedChannelCount = sources.sumOf(IptvSourceSummary::selectedChannelCount),
        )
    }

    suspend fun rename(source: IptvSourceEntity, name: String) {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "Liste adi bos olamaz." }
        dao.updateSource(source.copy(name = normalized))
        notifySharedChannelsChanged()
    }

    suspend fun sourceCategories(sourceId: Long): List<String> =
        dao.getCategoriesForSource(sourceId)

    suspend fun selectionPage(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<IptvChannelListProjection> = dao.getSelectionPage(
        sourceId,
        category,
        IptvFtsQuery.from(query),
        selectedOnly,
        limit,
        offset,
    )

    suspend fun selectionWindow(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
        limit: Int,
        direction: IptvPageDirection,
        anchor: IptvPageAnchor? = null,
        targetIndex: Int = 0,
    ): List<IptvChannelListProjection> {
        val ftsQuery = IptvFtsQuery.from(query)
        return when (direction) {
            IptvPageDirection.FIRST -> dao.getSelectionPage(
                sourceId, category, ftsQuery, selectedOnly, limit, 0,
            )
            IptvPageDirection.NEXT -> requireNotNull(anchor).let {
                dao.getSelectionPageAfter(
                    sourceId, category, ftsQuery, selectedOnly,
                    it.originalIndex, it.sourceKey, limit,
                )
            }
            IptvPageDirection.PREVIOUS -> requireNotNull(anchor).let {
                dao.getSelectionPageBefore(
                    sourceId, category, ftsQuery, selectedOnly,
                    it.originalIndex, it.sourceKey, limit,
                ).asReversed()
            }
            IptvPageDirection.LAST -> dao.getSelectionLastPage(
                sourceId, category, ftsQuery, selectedOnly, limit,
            ).asReversed()
            IptvPageDirection.AT_INDEX -> dao.getSelectionPageAtOrAfter(
                sourceId, category, ftsQuery, selectedOnly, targetIndex, limit,
            )
        }
    }

    suspend fun selectionCount(
        sourceId: Long,
        category: String?,
        query: String,
        selectedOnly: Boolean,
    ): Int = dao.selectionCount(
        sourceId,
        category,
        IptvFtsQuery.from(query),
        selectedOnly,
    )

    suspend fun selectedChannelCount(sourceId: Long): Int = dao.selectedChannelCount(sourceId)

    suspend fun setFilteredChannelsSelected(
        sourceId: Long,
        category: String?,
        query: String,
        selected: Boolean,
    ): Int {
        val changed = dao.setFilteredChannelsSelected(
            sourceId,
            category,
            IptvFtsQuery.from(query),
            selected,
        )
        if (changed > 0) {
            notifySharedChannelsChanged()
            if (selected) xmlTvRepository.requestXtreamRefresh(force = true)
        }
        return changed
    }

    suspend fun isChannelSelected(sourceKey: String): Boolean =
        dao.getChannel(sourceKey)?.selected == true

    suspend fun setChannelSelected(sourceKey: String, selected: Boolean) {
        dao.setChannelSelected(sourceKey, selected)
        notifySharedChannelsChanged()
        if (selected) xmlTvRepository.requestXtreamRefresh(force = true)
    }

    suspend fun setChannelsSelected(sourceKeys: List<String>, selected: Boolean) {
        sourceKeys.chunked(SELECTION_UPDATE_CHUNK_SIZE).forEach { chunk ->
            if (chunk.isNotEmpty()) dao.setChannelsSelected(chunk, selected)
        }
        if (sourceKeys.isNotEmpty()) {
            notifySharedChannelsChanged()
            if (selected) xmlTvRepository.requestXtreamRefresh(force = true)
        }
    }

    suspend fun libraryLiveChannelsPage(
        sourceId: Long,
        category: String?,
        contentType: String,
        limit: Int,
        offset: Int,
    ): List<LiveChannel> = dao.getLibraryPage(sourceId, category, contentType, limit, offset)
        .map { it.toLiveChannel() }

    suspend fun libraryLiveChannelsWindow(
        sourceId: Long,
        category: String?,
        contentType: String,
        limit: Int,
        direction: IptvPageDirection,
        anchor: IptvPageAnchor? = null,
        targetIndex: Int = 0,
    ): IptvLibraryPage {
        val entities = when (direction) {
            IptvPageDirection.FIRST -> dao.getLibraryPage(
                sourceId, category, contentType, limit, 0,
            )
            IptvPageDirection.NEXT -> requireNotNull(anchor).let {
                dao.getLibraryPageAfter(
                    sourceId, category, contentType,
                    it.originalIndex, it.sourceKey, limit,
                )
            }
            IptvPageDirection.PREVIOUS -> requireNotNull(anchor).let {
                dao.getLibraryPageBefore(
                    sourceId, category, contentType,
                    it.originalIndex, it.sourceKey, limit,
                ).asReversed()
            }
            IptvPageDirection.LAST -> dao.getLibraryLastPage(
                sourceId, category, contentType, limit,
            ).asReversed()
            IptvPageDirection.AT_INDEX -> dao.getLibraryPageAtOrAfter(
                sourceId, category, contentType, targetIndex, limit,
            )
        }
        return IptvLibraryPage(
            channels = entities.map { it.toLiveChannel() },
            firstAnchor = entities.firstOrNull()?.pageAnchor(),
            lastAnchor = entities.lastOrNull()?.pageAnchor(),
        )
    }

    suspend fun libraryChannelCount(
        sourceId: Long,
        category: String?,
        contentType: String,
    ): Int = dao.libraryCount(sourceId, category, contentType)

    suspend fun channel(sourceKey: String): LiveChannel? = dao.getChannel(sourceKey)?.toLiveChannel()

    suspend fun alternativeStreams(sourceKey: String): List<LiveChannel> =
        dao.getAlternativeChannels(sourceKey)
            .distinctBy(IptvChannelEntity::streamUrl)
            .map { it.toLiveChannel() }

    suspend fun setSelectedChannels(sourceId: Long, sourceKeys: Set<String>) {
        database.withTransaction {
            dao.clearSelectedChannels(sourceId)
            sourceKeys.chunked(SELECTION_UPDATE_CHUNK_SIZE).forEach { chunk ->
                if (chunk.isNotEmpty()) dao.selectChannels(chunk)
            }
        }
        notifySharedChannelsChanged()
        if (sourceKeys.isNotEmpty()) xmlTvRepository.requestXtreamRefresh(force = true)
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
            epgId = tvgId?.takeIf(String::isNotBlank)
                ?: tvgName?.takeIf(String::isNotBlank),
            userAgent = userAgent,
            referrer = referrer,
            subtitleUrl = subtitleUrl,
            iptvContentType = contentType,
            inMainList = selected,
        )

    private fun IptvChannelListProjection.toLiveChannel() =
        LiveChannel(
            id = stableLongId(sourceKey),
            sourceKey = sourceKey,
            inputId = "iptv:$sourceId",
            displayNumber = (originalIndex + 1).toString(),
            displayName = displayName,
            uri = "",
            source = LiveChannel.Source.IPTV,
            logoUrl = logoUrl,
            groupTitle = groupTitle,
            epgId = tvgId?.takeIf(String::isNotBlank)
                ?: tvgName?.takeIf(String::isNotBlank),
            iptvContentType = contentType,
            inMainList = selected,
        )

    private fun IptvChannelEntity.pageAnchor() = IptvPageAnchor(originalIndex, sourceKey)

    private fun IptvChannelListProjection.pageAnchor() = IptvPageAnchor(originalIndex, sourceKey)

    suspend fun importUrl(location: String, nameOverride: String? = null): IptvImportResult {
        return importUrlInternal(location, nameOverride, null)
    }

    suspend fun updateUrl(source: IptvSourceEntity, location: String): IptvImportResult {
        require(source.kind == KIND_URL) { "Yalniz URL kaynaklari duzenlenebilir." }
        return importUrlInternal(location, source.name, source)
    }

    private suspend fun importUrlInternal(
        location: String,
        nameOverride: String?,
        replacementSource: IptvSourceEntity?,
    ): IptvImportResult {
        val normalized = location.trim()
        require(normalized.startsWith("http://") || normalized.startsWith("https://"))
        val conflicting = dao.getSourceByLocation(normalized)
        require(conflicting == null || conflicting.id == replacementSource?.id) {
            "Bu adres zaten baska bir IPTV listesinde kayitli."
        }
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
            return input.use {
                importStream(normalized, KIND_URL, name, it, replacementSource)
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun importDocument(
        uri: Uri,
        name: String,
        replacementSource: IptvSourceEntity? = null,
    ): IptvImportResult {
        val input = checkNotNull(appContext.contentResolver.openInputStream(uri))
        val compressed = uri.lastPathSegment?.endsWith(".gz", ignoreCase = true) == true
        val decoded = if (compressed) GZIPInputStream(input) else input
        return decoded.use {
            importStream(uri.toString(), KIND_DOCUMENT, name, it, replacementSource)
        }
    }

    suspend fun importXtream(
        serverUrl: String,
        username: String,
        password: String,
        name: String,
        replacementSource: IptvSourceEntity? = null,
    ): IptvImportResult {
        require(username.isNotBlank() && password.isNotBlank()) {
            "Xtream kullanıcı adı ve parola gereklidir."
        }
        val client = XtreamClient(serverUrl, username.trim(), password)
        client.verifyAccount()
        val result = importGenerated(
            location = "xtream:${client.baseUrl}|${username.trim()}",
            kind = KIND_XTREAM,
            name = name,
            serverUrl = client.baseUrl,
            username = username.trim(),
            password = password,
            replacementSource = replacementSource,
        ) { client.channels().asIterable() }
        xmlTvRepository.ensurePeriodicRefresh()
        xmlTvRepository.requestXtreamRefresh(force = true)
        return result
    }

    suspend fun importStalker(
        portalUrl: String,
        macAddress: String,
        name: String,
        replacementSource: IptvSourceEntity? = null,
    ): IptvImportResult {
        val normalizedMac = normalizeMac(macAddress)
        val client = StalkerClient(portalUrl, normalizedMac)
        return importGenerated(
            location = "stalker:${client.endpoint}|$normalizedMac",
            kind = KIND_STALKER,
            name = name,
            serverUrl = client.endpoint,
            macAddress = normalizedMac,
            replacementSource = replacementSource,
        ) { client.channels().asIterable() }
    }

    suspend fun refresh(source: IptvSourceEntity): IptvImportResult = when (source.kind) {
        KIND_URL -> importUrlInternal(source.location, source.name, source)
        KIND_DOCUMENT -> importDocument(Uri.parse(source.location), source.name, source)
        KIND_XTREAM -> importXtream(
            checkNotNull(source.serverUrl),
            checkNotNull(source.username),
            checkNotNull(source.password),
            source.name,
            source,
        )
        KIND_STALKER -> importStalker(
            checkNotNull(source.serverUrl),
            checkNotNull(source.macAddress),
            source.name,
            source,
        )
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
        replacementSource: IptvSourceEntity? = null,
    ): IptvImportResult = importGenerated(location, kind, name, replacementSource = replacementSource) {
        M3uParser.sequence(InputStreamReader(input, Charsets.UTF_8)).asIterable()
    }

    private suspend fun importGenerated(
        location: String,
        kind: String,
        name: String,
        serverUrl: String? = null,
        username: String? = null,
        password: String? = null,
        macAddress: String? = null,
        replacementSource: IptvSourceEntity? = null,
        produce: () -> Iterable<ParsedIptvChannel>,
    ): IptvImportResult {
        val result = IMPORT_MUTEX.withLock {
            val sessionId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            try {
                dao.deleteStaleStaging(now - STAGING_MAX_AGE_MS)
                val batch = ArrayList<IptvChannelStagingEntity>(IMPORT_BATCH_SIZE)
                var channelCount = 0
                for (item in produce()) {
                    coroutineContext.ensureActive()
                    val tvgId = item.tvgId?.trim()?.takeIf(String::isNotBlank)
                    val matchKey = selectionName(item.name, item.groupTitle)
                    val identity = tvgId?.let { "id:$it" } ?: listOf(
                        "name:$matchKey",
                        item.streamUrl.substringBefore('?'),
                    ).joinToString("|")
                    batch += IptvChannelStagingEntity(
                        sessionId = sessionId,
                        originalIndex = channelCount,
                        identityHash = sha256(identity).take(24),
                        tvgId = tvgId,
                        tvgName = item.tvgName,
                        displayName = item.name,
                        streamUrl = item.streamUrl,
                        logoUrl = item.logoUrl,
                        groupTitle = item.groupTitle,
                        userAgent = item.userAgent,
                        referrer = item.referrer,
                        subtitleUrl = item.subtitleUrl,
                        contentType = item.contentType,
                        matchKey = matchKey,
                        createdAt = now,
                    )
                    channelCount++
                    if (batch.size >= IMPORT_BATCH_SIZE) {
                        dao.insertStagedChannels(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) dao.insertStagedChannels(batch)
                require(channelCount > 0 && dao.stagingCount(sessionId) > 0) {
                    "Listede oynatılabilir IPTV kanalı bulunamadı."
                }

                database.withTransaction {
                    val existing = replacementSource ?: dao.getSourceByLocation(location)
                    val sourceId = existing?.id ?: dao.insertSource(
                        IptvSourceEntity(
                            name = name,
                            location = location,
                            kind = kind,
                            serverUrl = serverUrl,
                            username = username,
                            password = password,
                            macAddress = macAddress,
                        ),
                    )
                    check(sourceId > 0) { "IPTV kaynağı kaydedilemedi." }
                    val source = (existing ?: IptvSourceEntity(
                        id = sourceId,
                        name = name,
                        location = location,
                        kind = kind,
                    )).copy(
                        name = name,
                        location = location,
                        kind = kind,
                        enabled = true,
                        lastUpdatedAt = now,
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        macAddress = macAddress,
                    )
                    dao.updateSource(source)
                    dao.resolveStagedSourceKeys(sessionId, sourceId)
                    dao.discardSupersededStagedDuplicates(sessionId)
                    dao.updateChangedStagedChannels(sessionId, sourceId)
                    dao.touchStagedChannels(sessionId, sourceId, now)
                    dao.insertNewStagedChannels(sessionId, sourceId, now)
                    dao.deleteChannelsMissingFromStaging(sourceId, sessionId)
                    IptvImportResult(sourceId, source.name, dao.channelCount(sourceId))
                }
            } finally {
                withContext(NonCancellable) {
                    runCatching { dao.deleteStagingSession(sessionId) }
                }
            }
        }
        notifySharedChannelsChanged()
        return result
    }

    private fun normalizeMac(value: String): String {
        val hex = value.filter(Char::isLetterOrDigit).uppercase()
        require(hex.length == 12 && hex.all { it.isDigit() || it in 'A'..'F' }) {
            "Geçerli bir MAC adresi girin."
        }
        return hex.chunked(2).joinToString(":")
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
        "${group.orEmpty().trim().lowercase(Locale.ROOT)}|" +
            name.trim().lowercase(Locale.ROOT)

    companion object {
        const val KIND_URL = "URL"
        const val KIND_DOCUMENT = "DOCUMENT"
        const val KIND_XTREAM = "XTREAM"
        const val KIND_STALKER = "STALKER"
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val DEFAULT_USER_AGENT = "TVApp/0.1 AndroidTV"
        private const val SELECTION_UPDATE_CHUNK_SIZE = 500
        private const val IMPORT_BATCH_SIZE = 500
        private const val STAGING_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
        private val IMPORT_MUTEX = Mutex()
    }
}
