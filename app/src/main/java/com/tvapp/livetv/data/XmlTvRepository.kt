package com.tvapp.livetv.data

import android.content.Context
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.net.Uri
import android.util.Xml
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.XmlTvProgramEntity
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
    private val legacyCacheFile = appContext.filesDir.resolve("xmltv-programs.json")

    fun sourceLabel(): String? = preferences.getString(KEY_SOURCE, null)

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
            connection.inputStream.use { importStream(it, url) }.also {
                scheduleUrlRefresh()
            }
        } finally {
            connection.disconnect()
        }
    }

    fun refreshSavedUrl(): Int {
        val source = sourceLabel()?.takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        } ?: return 0
        return importUrl(source)
    }

    fun importDocument(uri: Uri): Int = appContext.contentResolver.openInputStream(uri)?.use {
        importStream(it, uri.toString())
    } ?: error("XMLTV dosyası açılamadı")

    fun clear() {
        preferences.edit().clear().apply()
        database.runInTransaction { dao.clearPrograms() }
        legacyCacheFile.delete()
        (appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler)
            .cancel(REFRESH_JOB_ID)
    }

    private fun scheduleUrlRefresh() {
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
        return dao.programs(epgId.orEmpty(), name, start, end).map {
            ProgramSummary(it.title, it.startTimeMillis, it.endTimeMillis, it.description)
        }
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
        return buildMap {
            channels.forEach { channel ->
                val epgId = channel.epgId?.normalize()?.takeIf(String::isNotBlank)
                val name = channel.displayName.normalize()
                val match = epgId?.let(byId::get)?.firstOrNull()
                    ?: byName[name]?.firstOrNull()
                    ?: byId[name]?.firstOrNull()
                match?.let {
                    put(
                        channel.sourceKey,
                        ProgramSummary(
                            it.title,
                            it.startTimeMillis,
                            it.endTimeMillis,
                            it.description,
                        ),
                    )
                }
            }
        }
    }

    private fun importStream(stream: InputStream, label: String): Int {
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
        database.runInTransaction {
            dao.clearPrograms()
            programs.chunked(INSERT_BATCH_SIZE).forEach(dao::insertPrograms)
        }
        preferences.edit().putString(KEY_SOURCE, label).putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
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

    private fun String.normalize(): String = lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9çğıöşü]+"), "")
        .removeSuffix("hd").removeSuffix("sd").removeSuffix("4k")

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_UPDATED = "updated"
        const val INSERT_BATCH_SIZE = 1_000
        const val CURRENT_PROGRAM_QUERY_CHUNK_SIZE = 400
        const val REFRESH_JOB_ID = 0x545650
        const val REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1_000L
    }
}
