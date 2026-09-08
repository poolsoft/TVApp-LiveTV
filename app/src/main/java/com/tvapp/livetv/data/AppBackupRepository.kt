package com.tvapp.livetv.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import com.tvapp.livetv.data.local.ChannelGroupEntity
import com.tvapp.livetv.data.local.IptvChannelEntity
import com.tvapp.livetv.data.local.IptvSourceEntity
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.UserChannelEntity
import com.tvapp.livetv.settings.ChannelListFilterPreferences
import com.tvapp.livetv.settings.ChannelListFilterStore
import com.tvapp.livetv.settings.ChannelPanelSide
import com.tvapp.livetv.settings.ChannelSourceFilter
import com.tvapp.livetv.settings.ChannelSourceFilterSnapshot
import com.tvapp.livetv.settings.ChannelSourceFilterStore
import com.tvapp.livetv.settings.DisplayPreferences
import com.tvapp.livetv.settings.DisplayPreferencesStore
import com.tvapp.livetv.settings.InfoBarPosition
import com.tvapp.livetv.settings.SleepTimerStore
import com.tvapp.livetv.settings.ParentalControlSnapshot
import com.tvapp.livetv.settings.ParentalControlStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.InputStreamReader

data class BackupSummary(
    val channelCount: Int,
    val iptvSourceCount: Int,
    val iptvChannelCount: Int,
)

data class BackupExportResult(
    val summary: BackupSummary,
    val fallbackLocation: String? = null,
)

class AppBackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = TVAppDatabase.getInstance(appContext)
    private val displayStore = DisplayPreferencesStore(appContext)
    private val listFilterStore = ChannelListFilterStore(appContext)
    private val sourceFilterStore = ChannelSourceFilterStore(appContext)
    private val sleepTimerStore = SleepTimerStore(appContext)
    private val parentalControlStore = ParentalControlStore(appContext)

    suspend fun exportTo(uri: Uri, fallbackFileName: String): BackupExportResult {
        val metadata = BackupMetadata(
            groups = database.channelDao().getGroups(),
            channels = database.channelDao().getOrderedChannels(),
            iptvSources = database.iptvDao().getSources(),
            display = displayStore.load(),
            listFilter = listFilterStore.load(),
            sourceFilter = sourceFilterStore.snapshot(),
            sleepTimerEndAt = sleepTimerStore.endAtMillis(),
            parentalControl = parentalControlStore.snapshot(),
        )
        val summary = BackupSummary(
            channelCount = metadata.channels.size,
            iptvSourceCount = metadata.iptvSources.size,
            iptvChannelCount = metadata.iptvSources.sumOf { source ->
                database.iptvDao().channelCount(source.id)
            },
        )
        val primaryFailure = runCatching {
            writeDocument(uri) { output -> writeBackup(output, metadata) }
        }.exceptionOrNull()
        if (primaryFailure == null) return BackupExportResult(summary)
        runCatching { appContext.contentResolver.delete(uri, null, null) }

        val fallbackLocation = runCatching {
            writeToDownloads(fallbackFileName) { output -> writeBackup(output, metadata) }
        }.getOrElse { fallbackFailure ->
            fallbackFailure.addSuppressed(primaryFailure)
            throw fallbackFailure
        }
        return BackupExportResult(summary, fallbackLocation)
    }

    private inline fun writeDocument(uri: Uri, write: (OutputStream) -> Unit) {
        val descriptor = appContext.contentResolver.openFileDescriptor(uri, "rwt")
            ?: error("Yedek dosyası açılamadı")
        descriptor.use {
            FileOutputStream(it.fileDescriptor).use { output ->
                write(output)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private inline fun writeToDownloads(
        fileName: String,
        write: (OutputStream) -> Unit,
    ): String {
        val resolver = appContext.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/TVApp"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("İndirilenler klasöründe yedek dosyası oluşturulamadı")
        try {
            writeDocument(uri, write)
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        return "$relativePath/$fileName"
    }

    private suspend fun writeBackup(output: OutputStream, metadata: BackupMetadata) {
        val writer = JsonWriter(BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)))
        writer.setIndent("  ")
        writer.beginObject()
        writer.name("format").value(FORMAT)
        writer.name("version").value(VERSION.toLong())
        writer.name("createdAt").value(System.currentTimeMillis())
        writer.name("groups").beginArray()
        metadata.groups.forEach { writer.write(it) }
        writer.endArray()
        writer.name("channels").beginArray()
        metadata.channels.forEach { writer.write(it) }
        writer.endArray()
        writer.name("iptvSources").beginArray()
        metadata.iptvSources.forEach { source ->
            writer.beginObject()
            writer.writeFields(source)
            writer.name("channels").beginArray()
            writeIptvChannels(writer, source.id)
            writer.endArray()
            writer.endObject()
        }
        writer.endArray()
        writer.name("display").write(metadata.display)
        writer.name("channelListFilter").beginObject()
        writer.name("source").value(metadata.listFilter.source.name)
        writer.name("favoritesOnly").value(metadata.listFilter.favoritesOnly)
        writer.endObject()
        writer.name("tifInputFilter").beginObject()
        writer.name("configured").value(metadata.sourceFilter.configured)
        writer.name("enabledInputIds").beginArray()
        metadata.sourceFilter.enabledInputIds.forEach { writer.value(it) }
        writer.endArray()
        writer.endObject()
        writer.name("sleepTimerEndAt").value(metadata.sleepTimerEndAt)
        writer.name("parentalControl").beginObject()
        writer.name("pinHash").nullableValue(metadata.parentalControl.pinHash)
        writer.name("lockedSourceKeys").beginArray()
        metadata.parentalControl.lockedSourceKeys.forEach { writer.value(it) }
        writer.endArray()
        writer.endObject()
        writer.endObject()
        writer.flush()
    }

    private suspend fun writeIptvChannels(writer: JsonWriter, sourceId: Long) {
        var anchorIndex = -1
        var anchorKey = ""
        while (true) {
            val page = database.iptvDao().getBackupPageAfter(
                sourceId,
                anchorIndex,
                anchorKey,
                BACKUP_PAGE_SIZE,
            )
            if (page.isEmpty()) return
            page.forEach { writer.write(it) }
            val last = page.last()
            anchorIndex = last.originalIndex
            anchorKey = last.sourceKey
        }
    }

    private data class BackupMetadata(
        val groups: List<ChannelGroupEntity>,
        val channels: List<UserChannelEntity>,
        val iptvSources: List<IptvSourceEntity>,
        val display: DisplayPreferences,
        val listFilter: ChannelListFilterPreferences,
        val sourceFilter: ChannelSourceFilterSnapshot,
        val sleepTimerEndAt: Long,
        val parentalControl: ParentalControlSnapshot,
    )

    suspend fun importFrom(uri: Uri): BackupSummary {
        val tempFile = File.createTempFile("tvapp-restore-", ".json", appContext.cacheDir)
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().buffered().use(input::copyTo)
            } ?: error("Yedek dosyası okunamadı")
            val metadata = validateBackup(tempFile)
            val summary = importDatabaseRows(tempFile)
            displayStore.save(metadata.display)
            listFilterStore.save(metadata.listFilter.source, metadata.listFilter.favoritesOnly)
            sourceFilterStore.restore(metadata.sourceFilter)
            val remainingMinutes = (
                (metadata.sleepTimerEndAt - System.currentTimeMillis() + 59_999L) / 60_000L
                ).toInt().coerceAtLeast(0)
            sleepTimerStore.schedule(remainingMinutes)
            parentalControlStore.restore(metadata.parentalControl)
            return summary
        } finally {
            tempFile.delete()
        }
    }

    private fun validateBackup(file: File): RestoreMetadata {
        var format: String? = null
        var version = -1
        var display: DisplayPreferences? = null
        var listFilter: ChannelListFilterPreferences? = null
        var sourceFilter: ChannelSourceFilterSnapshot? = null
        var sleepTimerEndAt = 0L
        var parentalControl = ParentalControlSnapshot(null, emptySet())
        file.jsonReader().use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "format" -> format = reader.nextString()
                    "version" -> version = reader.nextInt()
                    "display" -> display = reader.readObject().toDisplayPreferences()
                    "channelListFilter" -> {
                        val json = reader.readObject()
                        listFilter = ChannelListFilterPreferences(
                            source = enumValue(
                                json.requireString("source"),
                                ChannelSourceFilter.ALL,
                            ),
                            favoritesOnly = json.optBoolean("favoritesOnly", false),
                        )
                    }
                    "tifInputFilter" -> {
                        val json = reader.readObject()
                        sourceFilter = ChannelSourceFilterSnapshot(
                            configured = json.optBoolean("configured", false),
                            enabledInputIds = json.requireArray("enabledInputIds")
                                .strings().toSet(),
                        )
                    }
                    "sleepTimerEndAt" -> sleepTimerEndAt = reader.nextLong()
                    "parentalControl" -> {
                        val json = reader.readObject()
                        parentalControl = ParentalControlSnapshot(
                            pinHash = json.nullableString("pinHash"),
                            lockedSourceKeys = json.optJSONArray("lockedSourceKeys")
                                ?.strings()?.toSet().orEmpty(),
                        )
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        require(format == FORMAT) { "Bu dosya TVApp yedeği değil" }
        require(version in 1..VERSION) { "Desteklenmeyen yedek sürümü: $version" }
        return RestoreMetadata(
            display = requireNotNull(display) { "Eksik alan: display" },
            listFilter = requireNotNull(listFilter) { "Eksik alan: channelListFilter" },
            sourceFilter = requireNotNull(sourceFilter) { "Eksik alan: tifInputFilter" },
            sleepTimerEndAt = sleepTimerEndAt,
            parentalControl = parentalControl,
        )
    }

    private suspend fun importDatabaseRows(file: File): BackupSummary = database.withTransaction {
        val channelDao = database.channelDao()
        val iptvDao = database.iptvDao()
        channelDao.deleteAllChannels()
        channelDao.deleteAllGroups()
        iptvDao.deleteAllSources()
        val groupIds = hashSetOf<Long>()
        var channelCount = 0
        var sourceCount = 0
        var iptvChannelCount = 0
        file.jsonReader().use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "groups" -> reader.readObjectArrayInBatches(BACKUP_PAGE_SIZE) { objects ->
                        val groups = objects.map { it.toGroup() }
                        channelDao.upsertGroups(groups)
                        groups.forEach { groupIds += it.id }
                    }
                    "channels" -> reader.readObjectArrayInBatches(BACKUP_PAGE_SIZE) { objects ->
                        val channels = objects.map { json ->
                            json.toUserChannel().let { channel ->
                                if (channel.groupId == null || channel.groupId in groupIds) channel
                                else channel.copy(groupId = null)
                            }
                        }
                        channelDao.upsertChannels(channels)
                        channelCount += channels.size
                    }
                    "iptvSources" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            iptvChannelCount += reader.importIptvSource(iptvDao)
                            sourceCount++
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        BackupSummary(channelCount, sourceCount, iptvChannelCount)
    }

    private suspend fun JsonReader.importIptvSource(
        iptvDao: com.tvapp.livetv.data.local.IptvDao,
    ): Int {
        val sourceJson = JSONObject()
        var source: IptvSourceEntity? = null
        var channelCount = 0
        beginObject()
        while (hasNext()) {
            val name = nextName()
            if (name == "channels") {
                val resolvedSource = sourceJson.toIptvSource()
                source = resolvedSource
                iptvDao.upsertSources(listOf(resolvedSource))
                readObjectArrayInBatches(BACKUP_PAGE_SIZE) { objects ->
                    val channels = objects.map { it.toIptvChannel(resolvedSource.id) }
                    iptvDao.upsertChannels(channels)
                    channelCount += channels.size
                }
            } else {
                sourceJson.put(name, readJsonValue())
            }
        }
        endObject()
        if (source == null) iptvDao.upsertSources(listOf(sourceJson.toIptvSource()))
        return channelCount
    }

    private suspend fun JsonReader.readObjectArrayInBatches(
        batchSize: Int,
        write: suspend (List<JSONObject>) -> Unit,
    ) {
        beginArray()
        val batch = ArrayList<JSONObject>(batchSize)
        while (hasNext()) {
            batch += readObject()
            if (batch.size >= batchSize) {
                write(batch.toList())
                batch.clear()
            }
        }
        endArray()
        if (batch.isNotEmpty()) write(batch)
    }

    private fun File.jsonReader() = JsonReader(
        InputStreamReader(FileInputStream(this), Charsets.UTF_8),
    )

    private fun JsonReader.readObject(): JSONObject {
        val result = JSONObject()
        beginObject()
        while (hasNext()) {
            result.put(nextName(), readJsonValue())
        }
        endObject()
        return result
    }

    private fun JsonReader.readJsonValue(): Any = when (peek()) {
        JsonToken.BEGIN_OBJECT -> readObject()
        JsonToken.BEGIN_ARRAY -> JSONArray().also { array ->
            beginArray()
            while (hasNext()) array.put(readJsonValue())
            endArray()
        }
        JsonToken.STRING -> nextString()
        JsonToken.NUMBER -> nextString().let { number ->
            number.toLongOrNull() ?: number.toDouble()
        }
        JsonToken.BOOLEAN -> nextBoolean()
        JsonToken.NULL -> {
            nextNull()
            JSONObject.NULL
        }
        else -> error("Geçersiz JSON değeri: ${peek()}")
    }

    private data class RestoreMetadata(
        val display: DisplayPreferences,
        val listFilter: ChannelListFilterPreferences,
        val sourceFilter: ChannelSourceFilterSnapshot,
        val sleepTimerEndAt: Long,
        val parentalControl: ParentalControlSnapshot,
    )

    private companion object {
        const val FORMAT = "TVApp-backup"
        const val VERSION = 1
        const val BACKUP_PAGE_SIZE = 500

        fun JsonWriter.nullableValue(value: String?) = apply {
            if (value == null) nullValue() else value(value)
        }

        fun JsonWriter.write(group: ChannelGroupEntity) = apply {
            beginObject()
            name("id").value(group.id)
            name("name").value(group.name)
            name("sortOrder").value(group.sortOrder.toLong())
            endObject()
        }

        fun JsonWriter.write(channel: UserChannelEntity) = apply {
            beginObject()
            name("sourceKey").value(channel.sourceKey)
            name("sourceType").value(channel.sourceType)
            name("originalDisplayNumber").value(channel.originalDisplayNumber)
            name("lastKnownName").value(channel.lastKnownName)
            name("customNumber").apply {
                channel.customNumber?.let { value(it.toLong()) } ?: nullValue()
            }
            name("customName").nullableValue(channel.customName)
            name("sortOrder").value(channel.sortOrder.toLong())
            name("favorite").value(channel.favorite)
            name("hidden").value(channel.hidden)
            name("groupId").apply { channel.groupId?.let(::value) ?: nullValue() }
            name("epgIdOverride").nullableValue(channel.epgIdOverride)
            name("epgSourceIdOverride").apply {
                channel.epgSourceIdOverride?.let(::value) ?: nullValue()
            }
            name("lastSeenAt").value(channel.lastSeenAt)
            endObject()
        }

        fun JsonWriter.writeFields(source: IptvSourceEntity) = apply {
            name("id").value(source.id)
            name("name").value(source.name)
            name("location").value(source.location)
            name("kind").value(source.kind)
            name("enabled").value(source.enabled)
            name("lastUpdatedAt").value(source.lastUpdatedAt)
            name("serverUrl").nullableValue(source.serverUrl)
            name("username").nullableValue(source.username)
            name("password").nullableValue(source.password)
            name("macAddress").nullableValue(source.macAddress)
        }

        fun JsonWriter.write(channel: IptvChannelEntity) = apply {
            beginObject()
            name("sourceKey").value(channel.sourceKey)
            name("tvgId").nullableValue(channel.tvgId)
            name("tvgName").nullableValue(channel.tvgName)
            name("displayName").value(channel.displayName)
            name("streamUrl").value(channel.streamUrl)
            name("logoUrl").nullableValue(channel.logoUrl)
            name("groupTitle").nullableValue(channel.groupTitle)
            name("userAgent").nullableValue(channel.userAgent)
            name("referrer").nullableValue(channel.referrer)
            name("subtitleUrl").nullableValue(channel.subtitleUrl)
            name("originalIndex").value(channel.originalIndex.toLong())
            name("contentType").value(channel.contentType)
            name("selected").value(channel.selected)
            name("lastSeenAt").value(channel.lastSeenAt)
            name("matchKey").value(channel.matchKey)
            endObject()
        }

        fun JsonWriter.write(display: DisplayPreferences) = apply {
            beginObject()
            name("infoBarPosition").value(display.infoBarPosition.name)
            name("showCurrentProgram").value(display.showCurrentProgram)
            name("showNextProgram").value(display.showNextProgram)
            name("showChannelLogo").value(display.showChannelLogo)
            name("showChannelProgram").value(display.showChannelProgram)
            name("showChannelProgress").value(display.showChannelProgress)
            name("showChannelSourceBadge").value(display.showChannelSourceBadge)
            name("channelPanelSide").value(display.channelPanelSide.name)
            name("infoBarOpacityPercent").value(display.infoBarOpacityPercent.toLong())
            name("channelPanelOpacityPercent").value(display.channelPanelOpacityPercent.toLong())
            name("infoBarDurationSeconds").value(display.infoBarDurationSeconds.toLong())
            name("subtitlesEnabled").value(display.subtitlesEnabled)
            name("channelFocusAutoTune").value(display.channelFocusAutoTune)
            name("channelFocusTuneDelayMillis").value(display.channelFocusTuneDelayMillis.toLong())
            name("launchOnBoot").value(display.launchOnBoot)
            name("preferredAudioLanguage").nullableValue(display.preferredAudioLanguage)
            name("preferredSubtitleLanguage").nullableValue(display.preferredSubtitleLanguage)
            endObject()
        }

        fun JSONObject.toGroup() = ChannelGroupEntity(
            id = requireLong("id"), name = requireString("name"), sortOrder = requireInt("sortOrder"),
        )

        fun JSONObject.toUserChannel() = UserChannelEntity(
            sourceKey = requireString("sourceKey"), sourceType = requireString("sourceType"),
            originalDisplayNumber = requireString("originalDisplayNumber"),
            lastKnownName = requireString("lastKnownName"), customNumber = nullableInt("customNumber"),
            customName = nullableString("customName"), sortOrder = requireInt("sortOrder"),
            favorite = optBoolean("favorite", false), hidden = optBoolean("hidden", false),
            groupId = nullableLong("groupId"),
            epgIdOverride = nullableString("epgIdOverride"),
            epgSourceIdOverride = nullableLong("epgSourceIdOverride"),
            lastSeenAt = optLong("lastSeenAt", 0L),
        )

        fun JSONObject.toIptvSource() = IptvSourceEntity(
            id = requireLong("id"), name = requireString("name"), location = requireString("location"),
            kind = requireString("kind"), enabled = optBoolean("enabled", true),
            lastUpdatedAt = optLong("lastUpdatedAt", 0L),
            serverUrl = nullableString("serverUrl"), username = nullableString("username"),
            password = nullableString("password"), macAddress = nullableString("macAddress"),
        )

        fun JSONObject.toIptvChannel(sourceId: Long) = IptvChannelEntity(
            sourceKey = requireString("sourceKey"), sourceId = sourceId, tvgId = nullableString("tvgId"),
            tvgName = nullableString("tvgName"), displayName = requireString("displayName"),
            streamUrl = requireString("streamUrl"), logoUrl = nullableString("logoUrl"),
            groupTitle = nullableString("groupTitle"), userAgent = nullableString("userAgent"),
            referrer = nullableString("referrer"), subtitleUrl = nullableString("subtitleUrl"),
            originalIndex = requireInt("originalIndex"),
            contentType = optString("contentType", "LIVE"),
            selected = optBoolean("selected", false), lastSeenAt = optLong("lastSeenAt", 0L),
            matchKey = optString("matchKey", ""),
        )

        fun JSONObject.toDisplayPreferences() = DisplayPreferences(
            infoBarPosition = enumValue(requireString("infoBarPosition"), InfoBarPosition.TOP),
            showCurrentProgram = optBoolean("showCurrentProgram", true),
            showNextProgram = optBoolean("showNextProgram", true),
            showChannelLogo = optBoolean("showChannelLogo", true),
            showChannelProgram = optBoolean("showChannelProgram", true),
            showChannelProgress = optBoolean("showChannelProgress", true),
            showChannelSourceBadge = optBoolean("showChannelSourceBadge", false),
            channelPanelSide = enumValue(requireString("channelPanelSide"), ChannelPanelSide.LEFT),
            infoBarOpacityPercent = optInt("infoBarOpacityPercent", 90).coerceIn(30, 100),
            channelPanelOpacityPercent = optInt("channelPanelOpacityPercent", 90).coerceIn(30, 100),
            infoBarDurationSeconds = optInt("infoBarDurationSeconds", 6).coerceIn(0, 15),
            channelFocusAutoTune = optBoolean("channelFocusAutoTune", true),
            channelFocusTuneDelayMillis = optInt("channelFocusTuneDelayMillis", 1_500)
                .coerceIn(500, 5_000),
            subtitlesEnabled = optBoolean("subtitlesEnabled", false),
            launchOnBoot = optBoolean("launchOnBoot", false),
            preferredAudioLanguage = nullableString("preferredAudioLanguage"),
            preferredSubtitleLanguage = nullableString("preferredSubtitleLanguage"),
        )

        fun JSONObject.requireArray(key: String): JSONArray = optJSONArray(key)
            ?: error("Eksik alan: $key")
        fun JSONObject.requireString(key: String): String = optString(key).takeIf { it.isNotBlank() }
            ?: error("Eksik alan: $key")
        fun JSONObject.requireInt(key: String): Int = if (has(key)) getInt(key) else error("Eksik alan: $key")
        fun JSONObject.requireLong(key: String): Long = if (has(key)) getLong(key) else error("Eksik alan: $key")
        fun JSONObject.nullableString(key: String): String? =
            if (!has(key) || isNull(key)) null else getString(key)
        fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)
        fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
        fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
        inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == value } ?: fallback
    }
}
