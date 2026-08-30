package com.tvapp.livetv.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tvapp.livetv.model.LiveChannel
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class XmlTvRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("xmltv", Context.MODE_PRIVATE)
    private val cacheFile = appContext.filesDir.resolve("xmltv-programs.json")

    fun sourceLabel(): String? = preferences.getString(KEY_SOURCE, null)

    fun importUrl(url: String): Int {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        return connection.inputStream.use { importStream(it, url) }
    }

    fun importDocument(uri: Uri): Int = appContext.contentResolver.openInputStream(uri)?.use {
        importStream(it, uri.toString())
    } ?: error("XMLTV dosyası açılamadı")

    fun clear() {
        preferences.edit().clear().apply()
        cacheFile.delete()
    }

    fun nowAndNext(channel: LiveChannel, now: Long = System.currentTimeMillis()): NowNextPrograms {
        val programs = programs(channel, now - 6 * 60 * 60_000L, now + 72 * 60 * 60_000L)
        val current = programs.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
        val next = programs.firstOrNull { it.startTimeMillis >= (current?.endTimeMillis ?: now) }
        return NowNextPrograms(current, next)
    }

    fun programs(channel: LiveChannel, start: Long, end: Long): List<ProgramSummary> {
        val epgId = channel.epgId?.normalize()
        val name = channel.displayName.normalize()
        return readPrograms().asSequence()
            .filter { program ->
                program.end > start && program.start < end &&
                    ((epgId != null && program.channelId.normalize() == epgId) ||
                        program.channelName.normalize() == name || program.channelId.normalize() == name)
            }
            .sortedBy { it.start }
            .map { ProgramSummary(it.title, it.start, it.end) }
            .toList()
    }

    private fun importStream(stream: InputStream, label: String): Int {
        val parser = Xml.newPullParser().apply { setInput(stream, null) }
        val channelNames = mutableMapOf<String, String>()
        val programs = mutableListOf<XmlProgram>()
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
                        while (!(parser.eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "programme")) {
                            parser.next()
                            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "title") {
                                title = parser.nextText()
                            }
                        }
                        if (channelId.isNotBlank() && start > 0 && stop > start) {
                            programs += XmlProgram(channelId, channelNames[channelId] ?: channelId, title, start, stop)
                        }
                    }
                }
            }
            event = parser.next()
        }
        val json = JSONArray().apply { programs.forEach { put(it.toJson()) } }
        cacheFile.writeText(json.toString())
        preferences.edit().putString(KEY_SOURCE, label).putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
        return programs.size
    }

    private fun readPrograms(): List<XmlProgram> = runCatching {
        val array = JSONArray(cacheFile.readText())
        (0 until array.length()).map { index ->
            array.getJSONObject(index).let {
                XmlProgram(it.getString("id"), it.getString("name"), it.getString("title"), it.getLong("start"), it.getLong("end"))
            }
        }
    }.getOrDefault(emptyList())

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

    private data class XmlProgram(val channelId: String, val channelName: String, val title: String, val start: Long, val end: Long) {
        fun toJson() = JSONObject().apply {
            put("id", channelId); put("name", channelName); put("title", title); put("start", start); put("end", end)
        }
    }

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_UPDATED = "updated"
    }
}
