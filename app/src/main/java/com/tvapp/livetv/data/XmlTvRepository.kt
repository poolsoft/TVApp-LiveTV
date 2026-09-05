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
import com.tvapp.livetv.model.LiveChannel
import org.json.JSONArray
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class XmlTvRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("xmltv", Context.MODE_PRIVATE)
    private val database = TVAppDatabase.getInstance(appContext)
    private val dao = database.xmlTvDao()
    private val xtreamEpgDao = database.xtreamEpgDao()
    private val legacyCacheFile = appContext.filesDir.resolve("xmltv-programs.json")

    fun sources(): List<XmlTvSourceEntity> = dao.sources()

    fun sourceLabel(): String? = preferences.getString(KEY_SOURCE_SUMMARY, null)
        ?: preferences.getString(KEY_SOURCE, null)

    fun importUrl(url: String): Int {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "TVApp/0.1 AndroidTV")
        return try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "HTTP ${connection.responseCode} ${connection.responseMessage}"
            }
            connection.inputStream.use { importStream(it, url, sourceName(url), KIND_URL) }.also {
                ensurePeriodicRefresh()
            }
        } finally {
            connection.disconnect()
        }
    }

    fun refreshSavedUrls(): Int {
        val urls = sources().filter { it.kind == KIND_URL }
        if (urls.isEmpty()) {
            val legacy = preferences.getString(KEY_SOURCE, null)?.takeIf {
                it.startsWith("http://") || it.startsWith("https://")
            }
            return legacy?.let(::importUrl) ?: 0
        }
        var count = 0
        var firstError: Throwable? = null
        urls.forEach { source ->
            runCatching { importUrl(source.location) }
                .onSuccess { count += it }
                .onFailure { if (firstError == null) firstError = it }
        }
        if (count == 0) firstError?.let { throw it }
        return count
    }

    fun importDocument(uri: Uri): Int = appContext.contentResolver.openInputStream(uri)?.use {
        importStream(it, uri.toString(), documentName(uri), KIND_FILE)
    } ?: error("XMLTV dosyası açılamadı")

    fun deleteSource(sourceId: Long) {
        database.runInTransaction {
            dao.clearPrograms(sourceId)
            dao.deleteSource(sourceId)
        }
        updateSourceSummary()
        if (sources().none { it.kind == KIND_URL }) cancelPeriodicRefresh()
    }

    fun refreshSource(source: XmlTvSourceEntity): Int = when (source.kind) {
        KIND_URL -> importUrl(source.location)
        else -> error("Dosya kaynağı yeniden seçilmelidir")
    }

    fun clear() {
        preferences.edit().clear().apply()
        val sourceIds = sources().map { it.id }
        database.runInTransaction {
            dao.clearPrograms()
            sourceIds.forEach(dao::deleteSource)
        }
        legacyCacheFile.delete()
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
        val programs = programs(channel, now - 6 * 60 * 60_000L, now + 72 * 60 * 60_000L)
        val current = programs.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
        val next = programs.firstOrNull { it.startTimeMillis >= (current?.endTimeMillis ?: now) }
        return NowNextPrograms(current, next)
    }

    fun programs(channel: LiveChannel, start: Long, end: Long): List<ProgramSummary> {
        migrateLegacyCacheIfNeeded()
        val epgId = channel.epgId?.normalize()
        val name = channel.displayName.normalize()
        val xmlTv = dao.programs(epgId.orEmpty(), name, start, end).map {
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
        if (queriedChannels > 0 && successfulQueries == queriedChannels) {
            preferences.edit().putLong(KEY_XTREAM_UPDATED, now).apply()
        }
        return distinctPrograms.size
    }

    private fun importStream(stream: InputStream, location: String, name: String, kind: String): Int {
        val parser = Xml.newPullParser().apply { setInput(stream, null) }
        val channelNames = mutableMapOf<String, String>()
        val programs = mutableListOf<XmlTvProgramEntity>()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        var name = id
                        while (!(parser.eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "channel")) {
                            parser.next()
                            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "display-name") {
                                name = parser.nextText().ifBlank { id }
                            }
                        }
                        channelNames[id] = name
                    }
                    "programme" -> {
                        val channelId = parser.getAttributeValue(null, "channel").orEmpty()
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
                            val channelName = channelNames[channelId] ?: channelId
                            programs += XmlTvProgramEntity(
                                channelId = channelId,
                                channelName = channelName,
                                normalizedChannelId = channelId.normalize(),
                                normalizedChannelName = channelName.normalize(),
                                title = title,
                                description = description,
                                startTimeMillis = start,
                                endTimeMillis = stop,
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        val now = System.currentTimeMillis()
        val existing = dao.sourceByLocation(location)
        val sourceId = existing?.id ?: dao.insertSource(
            XmlTvSourceEntity(name = name, location = location, kind = kind, lastUpdatedAt = now),
        )
        database.runInTransaction {
            dao.updateSource(sourceId, name, now)
            dao.clearPrograms(sourceId)
            programs.asSequence().map { it.copy(sourceId = sourceId) }.chunked(INSERT_BATCH_SIZE)
                .forEach { dao.insertPrograms(it) }
        }
        updateSourceSummary()
        preferences.edit().putLong(KEY_UPDATED, now).remove(KEY_SOURCE).apply()
        legacyCacheFile.delete()
        return programs.size
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

    private fun String.normalize(): String = lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9çğıöşü]+"), "")
        .removeSuffix("hd").removeSuffix("sd").removeSuffix("4k")

    companion object {
        internal const val XTREAM_REFRESH_JOB_ID = 0x545651
        internal const val EXTRA_FORCE_REFRESH = "force-xtream-refresh"
        const val KEY_SOURCE = "source"
        const val KEY_UPDATED = "updated"
        const val KEY_SOURCE_SUMMARY = "source_summary"
        const val KEY_XTREAM_UPDATED = "xtream_updated"
        const val KIND_URL = "URL"
        const val KIND_FILE = "FILE"
        const val INSERT_BATCH_SIZE = 1_000
        const val CURRENT_PROGRAM_QUERY_CHUNK_SIZE = 400
        const val MAX_XTREAM_EPG_CHANNELS_PER_SOURCE = 250
        const val XTREAM_MIN_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val REFRESH_JOB_ID = 0x545650
        const val REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1_000L
        private const val XTREAM_REFRESH_DELAY_MS = 1_000L
    }
}

internal fun mergeProgramSources(
    primary: List<ProgramSummary>,
    fallback: List<ProgramSummary>,
): List<ProgramSummary> = (primary + fallback.filter { candidate ->
    primary.none { preferred ->
        candidate.startTimeMillis < preferred.endTimeMillis &&
            candidate.endTimeMillis > preferred.startTimeMillis
    }
}).sortedBy(ProgramSummary::startTimeMillis)
