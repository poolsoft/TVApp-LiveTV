package com.tvapp.livetv.data

import android.content.Context
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.net.Uri
import android.os.PersistableBundle
import android.util.Xml
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.XtreamEpgProgramEntity
import com.tvapp.livetv.data.local.XmlTvProgramEntity
import com.tvapp.livetv.data.local.XmlTvSourceEntity
import com.tvapp.livetv.data.local.XmlTvChannelCatalogRow
import com.tvapp.livetv.model.LiveChannel
import org.json.JSONArray
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class XmlTvRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("xmltv", Context.MODE_PRIVATE)
    private val database = TVAppDatabase.getInstance(appContext)
    private val dao = database.xmlTvDao()
    private val xtreamEpgDao = database.xtreamEpgDao()
    private val legacyCacheFile = appContext.filesDir.resolve("xmltv-programs.json")

    fun sources(): List<XmlTvSourceEntity> = dao.sources()

    fun sourceSummaries(): List<XmlTvSourceSummary> = sources().map { source ->
        XmlTvSourceSummary(
            source = source,
            channelCount = dao.sourceChannelCount(source.id),
            programCount = dao.sourceProgramCount(source.id),
        )
    }

    fun channelCatalog(): List<XmlTvChannelOption> {
        ensureCurrentNormalization()
        val sourceNames = sources().associate { it.id to it.name }
        return dao.channelCatalog().map { row ->
            XmlTvChannelOption(
                sourceId = row.sourceId,
                sourceName = sourceNames[row.sourceId] ?: "XMLTV",
                channelId = row.channelId,
                channelName = row.channelName,
                programCount = row.programCount,
            )
        }
    }

    fun sourceLabel(): String? = preferences.getString(KEY_SOURCE_SUMMARY, null)
        ?: preferences.getString(KEY_SOURCE, null)

    fun shouldAutoRefresh(): Boolean {
        val urls = sources().filter { it.kind == KIND_URL && it.enabled }
        if (urls.isEmpty()) return false
        val lastUpdated = preferences.getLong(KEY_UPDATED, 0L)
        val now = System.currentTimeMillis()
        return (now - lastUpdated) >= AUTO_REFRESH_THRESHOLD_MS
    }

    fun activeChannelKeys(): Set<String> = runCatching {
        runBlocking(Dispatchers.IO) {
            val userChannels = database.channelDao().getAllChannels()
            val iptvChannels = database.iptvDao().getEnabledChannels()
            buildSet {
                userChannels.forEach { channel ->
                    channel.lastKnownName?.normalize()?.takeIf(String::isNotBlank)?.let(::add)
                    channel.customName?.normalize()?.takeIf(String::isNotBlank)?.let(::add)
                    channel.epgIdOverride?.normalize()?.takeIf(String::isNotBlank)?.let(::add)
                }
                iptvChannels.forEach { channel ->
                    channel.displayName.normalize().takeIf(String::isNotBlank)?.let(::add)
                    channel.tvgName?.normalize()?.takeIf(String::isNotBlank)?.let(::add)
                    channel.tvgId?.normalize()?.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
    }.getOrDefault(emptySet())

    fun importUrl(url: String, nameOverride: String? = null, targetKeys: Set<String>? = null): Int =
        importUrlInternal(url, nameOverride, null, targetKeys)

    fun updateUrl(source: XmlTvSourceEntity, url: String): Int {
        require(source.kind == KIND_URL) { "Yalniz URL kaynaklari duzenlenebilir." }
        return runCatching { importUrlInternal(url, source.name, source) }
            .onFailure { error ->
                dao.setSourceError(
                    source.id,
                    error.message?.take(240) ?: error.javaClass.simpleName,
                )
            }
            .getOrThrow()
    }

    private fun importUrlInternal(
        url: String,
        nameOverride: String?,
        replacementSource: XmlTvSourceEntity?,
        targetKeys: Set<String>? = null,
    ): Int {
        val normalizedUrl = url.trim()
        require(normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://"))
        val conflicting = dao.sourceByLocation(normalizedUrl)
        require(conflicting == null || conflicting.id == replacementSource?.id) {
            "Bu adres zaten baska bir XMLTV kaynaginda kayitli."
        }
        val connection = URL(normalizedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "TVApp/0.1 AndroidTV")
        return try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "HTTP ${connection.responseCode} ${connection.responseMessage}"
            }
            val name = nameOverride?.trim()?.takeIf(String::isNotBlank) ?: sourceName(normalizedUrl)
            connection.inputStream.use {
                importStream(it, normalizedUrl, name, KIND_URL, replacementSource, targetKeys ?: activeChannelKeys())
            }.also {
                ensurePeriodicRefresh()
            }
        } finally {
            connection.disconnect()
        }
    }

    fun refreshSavedUrls(targetKeys: Set<String>? = null): Int {
        val urls = sources().filter { it.kind == KIND_URL && it.enabled }
        val effectiveKeys = targetKeys ?: activeChannelKeys()
        if (urls.isEmpty()) {
            val legacy = preferences.getString(KEY_SOURCE, null)?.takeIf {
                it.startsWith("http://") || it.startsWith("https://")
            }
            return legacy?.let { importUrl(it, targetKeys = effectiveKeys) } ?: 0
        }
        var count = 0
        var firstError: Throwable? = null
        urls.forEach { source ->
            runCatching { refreshSource(source, effectiveKeys) }
                .onSuccess { count += it }
                .onFailure { if (firstError == null) firstError = it }
        }
        if (count == 0 && firstError != null) throw firstError!!
        return count
    }

    fun importDocument(uri: Uri, targetKeys: Set<String>? = null): Int = appContext.contentResolver.openInputStream(uri)?.use {
        importStream(it, uri.toString(), documentName(uri), KIND_FILE, targetChannelKeys = targetKeys ?: activeChannelKeys())
    } ?: error("XMLTV dosyası açılamadı")

    fun deleteSource(sourceId: Long) {
        database.runInTransaction {
            dao.clearPrograms(sourceId)
            dao.deleteSource(sourceId)
        }
        updateSourceSummary()
        EpgSnapshotCache.clear()
        if (sources().none { it.kind == KIND_URL }) cancelPeriodicRefresh()
    }

    fun refreshSource(source: XmlTvSourceEntity, targetKeys: Set<String>? = null): Int = runCatching {
        when (source.kind) {
            KIND_URL -> importUrlInternal(source.location, source.name, source, targetKeys ?: activeChannelKeys())
            else -> error("Dosya kaynağı yeniden seçilmelidir")
        }
    }.onFailure { error ->
        dao.setSourceError(source.id, error.message?.take(240) ?: error.javaClass.simpleName)
    }.getOrThrow()

    fun setSourceEnabled(sourceId: Long, enabled: Boolean) {
        dao.setSourceEnabled(sourceId, enabled)
        EpgSnapshotCache.clear()
        updateSourceSummary()
    }

    fun renameSource(sourceId: Long, name: String) {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "XMLTV kaynak adi bos olamaz." }
        dao.renameSource(sourceId, normalized)
        updateSourceSummary()
    }

    fun clear() {
        preferences.edit().clear().apply()
        val sourceIds = sources().map { it.id }
        database.runInTransaction {
            dao.clearPrograms()
            sourceIds.forEach(dao::deleteSource)
        }
        legacyCacheFile.delete()
        EpgSnapshotCache.clear()
        cancelPeriodicRefresh()
    }

    fun ensurePeriodicRefresh() {
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.schedule(
            JobInfo.Builder(
                REFRESH_JOB_ID,
                ComponentName(appContext, XmlTvRefreshJobService::class.java),
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(REFRESH_INTERVAL_MS)
                .build(),
        )
    }

    fun requestXtreamRefresh(force: Boolean = false) {
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val pendingForce = scheduler.getPendingJob(XTREAM_REFRESH_JOB_ID)
            ?.extras
            ?.getBoolean(EXTRA_FORCE_REFRESH, false) == true
        scheduler.schedule(
            JobInfo.Builder(
                XTREAM_REFRESH_JOB_ID,
                ComponentName(appContext, XmlTvRefreshJobService::class.java),
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(XTREAM_REFRESH_DELAY_MS)
                .setExtras(PersistableBundle().apply {
                    putBoolean(EXTRA_FORCE_REFRESH, force || pendingForce)
                })
                .build(),
        )
    }

    fun nowAndNext(channel: LiveChannel, now: Long = System.currentTimeMillis()): NowNextPrograms {
        val epgId = channel.epgId?.normalize().orEmpty()
        val name = channel.displayName.normalize()
        val xmlTvEntities = dao.nowAndNextPrograms(epgId, name, channel.epgSourceId, now)
        val xtreamEntities = if (xmlTvEntities.size < 2) {
            xtreamEpgDao.nowAndNextPrograms(epgId, name, now)
        } else {
            emptyList()
        }
        val xmlTv = xmlTvEntities.map {
            ProgramSummary(it.title, it.startTimeMillis, it.endTimeMillis, it.description)
        }
        val xtream = xtreamEntities.map {
            ProgramSummary(it.title, it.startTimeMillis, it.endTimeMillis, it.description)
        }
        val programs = mergeProgramSources(xmlTv, xtream)
        val current = programs.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
        val next = programs.firstOrNull { it.startTimeMillis >= (current?.endTimeMillis ?: now) }
        return NowNextPrograms(current, next)
    }

    fun purgeExpiredPrograms(cutoffMillis: Long = System.currentTimeMillis() - EXPIRED_EPG_CUTOFF_MS) {
        runCatching {
            val xmlTvDeleted = dao.deleteExpiredPrograms(cutoffMillis)
            val xtreamDeleted = xtreamEpgDao.deleteExpiredPrograms(cutoffMillis)
            if (xmlTvDeleted > 0 || xtreamDeleted > 0) {
                EpgSnapshotCache.clear()
            }
        }
    }

    fun programs(channel: LiveChannel, start: Long, end: Long): List<ProgramSummary> {
        migrateLegacyCacheIfNeeded()
        ensureCurrentNormalization()
        val epgId = channel.epgId?.normalize()
        val name = channel.displayName.normalize()
        val xmlTv = dao.programs(epgId.orEmpty(), name, channel.epgSourceId, start, end).map {
            ProgramSummary(it.title, it.startTimeMillis, it.endTimeMillis, it.description)
        }
        val xtream = xtreamEpgDao.programs(epgId.orEmpty(), name, start, end).map {
            ProgramSummary(it.title, it.startTimeMillis, it.endTimeMillis, it.description)
        }
        return mergeProgramSources(xmlTv, xtream)
    }

    fun currentPrograms(
        channels: List<LiveChannel>,
        now: Long = System.currentTimeMillis(),
    ): Map<String, ProgramSummary> {
        if (channels.isEmpty()) return emptyMap()
        migrateLegacyCacheIfNeeded()
        ensureCurrentNormalization()
        val channelKeys = channels.flatMap { channel ->
            listOfNotNull(
                channel.epgId?.normalize()?.takeIf(String::isNotBlank),
                channel.displayName.normalize().takeIf(String::isNotBlank),
            )
        }.distinct()
        if (channelKeys.isEmpty()) return emptyMap()
        val programs = channelKeys.chunked(CURRENT_PROGRAM_QUERY_CHUNK_SIZE)
            .flatMap { dao.currentPrograms(it, now) }
        val byId = programs.groupBy(XmlTvProgramEntity::normalizedChannelId)
        val byName = programs.groupBy(XmlTvProgramEntity::normalizedChannelName)
        val result = mutableMapOf<String, ProgramSummary>()
        channels.forEach { channel ->
            val epgId = channel.epgId?.normalize()?.takeIf(String::isNotBlank)
            val name = channel.displayName.normalize()
            val match = sequenceOf(
                epgId?.let(byId::get),
                byName[name],
                byId[name],
            ).filterNotNull().flatten().firstOrNull { candidate ->
                channel.epgSourceId == null || candidate.sourceId == channel.epgSourceId
            }
            match?.let {
                result[channel.sourceKey] = ProgramSummary(
                    it.title,
                    it.startTimeMillis,
                    it.endTimeMillis,
                    it.description,
                )
            }
        }
        fillMissingFromXtreamEpg(result, channels, now)
        return result
    }

    private fun fillMissingFromXtreamEpg(
        result: MutableMap<String, ProgramSummary>,
        channels: List<LiveChannel>,
        now: Long,
    ) {
        val missing = channels.filter { it.sourceKey !in result }
        if (missing.isEmpty()) return
        val channelKeys = missing.flatMap { channel ->
            listOfNotNull(
                channel.epgId?.normalize()?.takeIf(String::isNotBlank),
                channel.displayName.normalize().takeIf(String::isNotBlank),
            )
        }.distinct()
        if (channelKeys.isEmpty()) return
        val programs = channelKeys.chunked(CURRENT_PROGRAM_QUERY_CHUNK_SIZE)
            .flatMap { xtreamEpgDao.currentPrograms(it, now) }
        val byId = programs.groupBy(XtreamEpgProgramEntity::normalizedChannelId)
        val byName = programs.groupBy(XtreamEpgProgramEntity::normalizedChannelName)
        missing.forEach { channel ->
            val epgId = channel.epgId?.normalize()?.takeIf(String::isNotBlank)
            val name = channel.displayName.normalize()
            val match = epgId?.let(byId::get)?.firstOrNull()
                ?: byName[name]?.firstOrNull()
                ?: byId[name]?.firstOrNull()
            match?.let {
                result[channel.sourceKey] = ProgramSummary(
                    it.title,
                    it.startTimeMillis,
                    it.endTimeMillis,
                    it.description,
                )
            }
        }
    }

    suspend fun refreshXtreamShortEpg(force: Boolean = false): Int {
        val now = System.currentTimeMillis()
        if (!force && now - preferences.getLong(KEY_XTREAM_UPDATED, 0L) < XTREAM_MIN_INTERVAL_MS) {
            return 0
        }
        val iptvDao = database.iptvDao()
        val sources = iptvDao.getSources().filter { source ->
            source.kind == IptvRepository.KIND_XTREAM && source.enabled &&
                !source.serverUrl.isNullOrBlank() &&
                !source.username.isNullOrBlank() &&
                source.password != null
        }
        if (sources.isEmpty()) {
            if (xtreamEpgDao.programCount() > 0) xtreamEpgDao.clearPrograms()
            preferences.edit().putLong(KEY_XTREAM_UPDATED, now).apply()
            return 0
        }
        val programs = ArrayList<XtreamEpgProgramEntity>()
        var queriedChannels = 0
        var successfulQueries = 0
        val failedChannels = linkedSetOf<Pair<String, String>>()
        for (source in sources) {
            val channels = iptvDao.getSelectedChannelsForSource(source.id)
                .asSequence()
                .filter { it.contentType.equals("LIVE", ignoreCase = true) }
                .mapNotNull { channel ->
                    XtreamClient.streamIdFromHttpUrl(channel.streamUrl)?.let { channel to it }
                }
                .take(MAX_XTREAM_EPG_CHANNELS_PER_SOURCE)
                .toList()
            if (channels.isEmpty()) continue
            val client = XtreamClient(
                source.serverUrl.orEmpty(),
                source.username.orEmpty(),
                source.password.orEmpty(),
            )
            for ((channel, streamId) in channels) {
                val channelId = channel.tvgId?.trim()?.takeIf(String::isNotBlank) ?: streamId
                val channelName = channel.displayName.trim().ifBlank { channelId }
                val listings = try {
                    client.shortEpg(streamId).also { successfulQueries++ }
                } catch (ignored: Exception) {
                    failedChannels += channelId.normalize() to channelName.normalize()
                    emptyList()
                }
                queriedChannels++
                if (listings.isEmpty()) continue
                for (listing in listings) {
                    programs += XtreamEpgProgramEntity(
                        channelId = channelId,
                        channelName = channelName,
                        normalizedChannelId = channelId.normalize(),
                        normalizedChannelName = channelName.normalize(),
                        title = listing.title,
                        description = listing.description,
                        startTimeMillis = listing.startTimeMillis,
                        endTimeMillis = listing.endTimeMillis,
                    )
                }
            }
        }
        failedChannels.forEach { (channelId, channelName) ->
            programs += xtreamEpgDao.programs(
                channelId,
                channelName,
                Long.MIN_VALUE,
                Long.MAX_VALUE,
            )
        }
        val distinctPrograms = programs.distinctBy {
            listOf(
                it.normalizedChannelId,
                it.normalizedChannelName,
                it.startTimeMillis,
                it.endTimeMillis,
                it.title,
            )
        }
        database.runInTransaction {
            xtreamEpgDao.clearPrograms()
            distinctPrograms.chunked(INSERT_BATCH_SIZE).forEach(xtreamEpgDao::insertPrograms)
        }
        EpgSnapshotCache.clear()
        purgeExpiredPrograms()
        if (queriedChannels > 0 && successfulQueries == queriedChannels) {
            preferences.edit().putLong(KEY_XTREAM_UPDATED, now).apply()
        }
        return distinctPrograms.size
    }

    private fun skipTag(parser: org.xmlpull.v1.XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                org.xmlpull.v1.XmlPullParser.END_TAG -> depth--
                org.xmlpull.v1.XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun importStream(
        stream: InputStream,
        location: String,
        name: String,
        kind: String,
        replacementSource: XmlTvSourceEntity? = null,
        targetChannelKeys: Set<String>? = null,
    ): Int {
        val parser = Xml.newPullParser().apply { setInput(stream.openXmlTvContent(), null) }
        val channelNames = mutableMapOf<String, String>()
        val matchedChannelIds = mutableSetOf<String>()
        val filterEnabled = !targetChannelKeys.isNullOrEmpty()

        val now = System.currentTimeMillis()
        val existing = replacementSource ?: dao.sourceByLocation(location)
        val sourceId = existing?.id ?: dao.insertSource(
            XmlTvSourceEntity(name = name, location = location, kind = kind, lastUpdatedAt = now),
        )

        val batch = ArrayList<XmlTvProgramEntity>(INSERT_BATCH_SIZE)
        var totalImported = 0

        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        var displayName = id
                        while (!(parser.eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "channel")) {
                            parser.next()
                            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "display-name") {
                                displayName = parser.nextText().ifBlank { id }
                            }
                        }
                        channelNames[id] = displayName
                        if (filterEnabled) {
                            val idNormalized = id.normalize()
                            val nameNormalized = displayName.normalize()
                            if (idNormalized in targetChannelKeys!! || nameNormalized in targetChannelKeys) {
                                matchedChannelIds += id
                            }
                        }
                    }
                    "programme" -> {
                        val channelId = parser.getAttributeValue(null, "channel").orEmpty()
                        val channelName = channelNames[channelId] ?: channelId

                        if (filterEnabled && matchedChannelIds.isNotEmpty() && channelId !in matchedChannelIds) {
                            val idNormalized = channelId.normalize()
                            val nameNormalized = channelName.normalize()
                            if (idNormalized !in targetChannelKeys!! && nameNormalized !in targetChannelKeys) {
                                skipTag(parser)
                                event = parser.eventType
                                continue
                            } else {
                                matchedChannelIds += channelId
                            }
                        }

                        val start = parseTime(parser.getAttributeValue(null, "start"))
                        val stop = parseTime(parser.getAttributeValue(null, "stop"))
                        var title = ""
                        var description = ""
                        while (!(parser.eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "programme")) {
                            parser.next()
                            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "title" -> title = parser.nextText()
                                    "desc" -> description = parser.nextText()
                                }
                            }
                        }
                        if (channelId.isNotBlank() && start > 0 && stop > start) {
                            batch += XmlTvProgramEntity(
                                channelId = channelId,
                                channelName = channelName,
                                normalizedChannelId = channelId.normalize(),
                                normalizedChannelName = channelName.normalize(),
                                title = title,
                                description = description,
                                startTimeMillis = start,
                                endTimeMillis = stop,
                                sourceId = sourceId,
                            )
                            totalImported++
                            if (batch.size >= INSERT_BATCH_SIZE) {
                                if (totalImported == batch.size) {
                                    database.runInTransaction {
                                        dao.updateSource(sourceId, name, location, kind, now)
                                        dao.clearPrograms(sourceId)
                                        dao.insertPrograms(batch)
                                    }
                                } else {
                                    database.runInTransaction {
                                        dao.insertPrograms(batch)
                                    }
                                }
                                batch.clear()
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (batch.isNotEmpty()) {
            if (totalImported == batch.size) {
                database.runInTransaction {
                    dao.updateSource(sourceId, name, location, kind, now)
                    dao.clearPrograms(sourceId)
                    dao.insertPrograms(batch)
                }
            } else {
                database.runInTransaction {
                    dao.insertPrograms(batch)
                }
            }
            batch.clear()
        } else if (totalImported == 0) {
            database.runInTransaction {
                dao.updateSource(sourceId, name, location, kind, now)
                dao.clearPrograms(sourceId)
            }
        }

        EpgSnapshotCache.clear()
        purgeExpiredPrograms()
        updateSourceSummary()
        preferences.edit().putLong(KEY_UPDATED, now).remove(KEY_SOURCE).apply()
        legacyCacheFile.delete()
        return totalImported
    }

    private fun migrateLegacyCacheIfNeeded() {
        if (!legacyCacheFile.exists()) return
        if (dao.programCount() > 0) {
            legacyCacheFile.delete()
            return
        }
        val legacyPrograms = runCatching {
            val array = JSONArray(legacyCacheFile.readText())
            (0 until array.length()).map { index ->
                array.getJSONObject(index).let { item ->
                    val channelId = item.getString("id")
                    val channelName = item.getString("name")
                    XmlTvProgramEntity(
                        channelId = channelId,
                        channelName = channelName,
                        normalizedChannelId = channelId.normalize(),
                        normalizedChannelName = channelName.normalize(),
                        title = item.getString("title"),
                        description = item.optString("description"),
                        startTimeMillis = item.getLong("start"),
                        endTimeMillis = item.getLong("end"),
                    )
                }
            }
        }.getOrDefault(emptyList())
        if (legacyPrograms.isNotEmpty()) {
            database.runInTransaction {
                legacyPrograms.chunked(INSERT_BATCH_SIZE).forEach(dao::insertPrograms)
            }
        }
        legacyCacheFile.delete()
    }

    private fun parseTime(value: String?): Long {
        val text = value.orEmpty().trim()
        val formats = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmssZ", "yyyyMMddHHmmss")
        return formats.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(text)?.time }.getOrNull()
        } ?: 0L
    }

    private fun sourceName(url: String): String = runCatching {
        URL(url).path.substringAfterLast('/').takeIf(String::isNotBlank)
            ?: URL(url).host
    }.getOrDefault("XMLTV")

    private fun documentName(uri: Uri): String = appContext.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        cursor.takeIf { it.moveToFirst() }?.getString(0)
    }?.takeIf(String::isNotBlank) ?: "XMLTV dosyası"

    private fun cancelPeriodicRefresh() {
        (appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler)
            .cancel(REFRESH_JOB_ID)
    }

    private fun updateSourceSummary() {
        val summary = sources().joinToString(limit = 2, truncated = "…") { it.name }
        preferences.edit().apply {
            if (summary.isBlank()) remove(KEY_SOURCE_SUMMARY) else putString(KEY_SOURCE_SUMMARY, summary)
        }.apply()
    }

    private fun ensureCurrentNormalization() {
        if (preferences.getInt(KEY_NORMALIZATION_VERSION, 0) >= NORMALIZATION_VERSION) return
        val programs = dao.allPrograms()
        database.runInTransaction {
            programs.asSequence().map { program ->
                program.copy(
                    normalizedChannelId = program.channelId.normalizeEpgKey(),
                    normalizedChannelName = program.channelName.normalizeEpgKey(),
                )
            }.chunked(INSERT_BATCH_SIZE).forEach(dao::updatePrograms)
        }
        preferences.edit().putInt(KEY_NORMALIZATION_VERSION, NORMALIZATION_VERSION).apply()
    }

    private fun String.normalize(): String = normalizeEpgKey()

    companion object {
        internal const val XTREAM_REFRESH_JOB_ID = 0x545651
        internal const val EXTRA_FORCE_REFRESH = "force-xtream-refresh"
        const val KEY_SOURCE = "source"
        const val KEY_UPDATED = "updated"
        const val KEY_SOURCE_SUMMARY = "source_summary"
        const val KEY_XTREAM_UPDATED = "xtream_updated"
        const val KEY_NORMALIZATION_VERSION = "normalization_version"
        const val KIND_URL = "URL"
        const val KIND_FILE = "FILE"
        const val INSERT_BATCH_SIZE = 1_000
        const val CURRENT_PROGRAM_QUERY_CHUNK_SIZE = 400
        const val MAX_XTREAM_EPG_CHANNELS_PER_SOURCE = 250
        const val XTREAM_MIN_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val REFRESH_JOB_ID = 0x545650
        const val REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1_000L
        const val AUTO_REFRESH_THRESHOLD_MS = 12 * 60 * 60 * 1_000L
        private const val XTREAM_REFRESH_DELAY_MS = 1_000L
        private const val NORMALIZATION_VERSION = 2
        const val EXPIRED_EPG_CUTOFF_MS = 24 * 60 * 60 * 1_000L
    }
}

data class XmlTvChannelOption(
    val sourceId: Long,
    val sourceName: String,
    val channelId: String,
    val channelName: String,
    val programCount: Int,
)

data class XmlTvSourceSummary(
    val source: XmlTvSourceEntity,
    val channelCount: Int,
    val programCount: Int,
)

internal fun mergeProgramSources(
    primary: List<ProgramSummary>,
    fallback: List<ProgramSummary>,
): List<ProgramSummary> = (primary + fallback.filter { candidate ->
    primary.none { preferred ->
        candidate.startTimeMillis < preferred.endTimeMillis &&
            candidate.endTimeMillis > preferred.startTimeMillis
    }
}).sortedBy(ProgramSummary::startTimeMillis)
