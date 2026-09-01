package com.tvapp.livetv.data

import android.content.Context
import android.net.Uri
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

data class BackupSummary(
    val channelCount: Int,
    val iptvSourceCount: Int,
    val iptvChannelCount: Int,
)

class AppBackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = TVAppDatabase.getInstance(appContext)
    private val displayStore = DisplayPreferencesStore(appContext)
    private val listFilterStore = ChannelListFilterStore(appContext)
    private val sourceFilterStore = ChannelSourceFilterStore(appContext)
    private val sleepTimerStore = SleepTimerStore(appContext)
    private val parentalControlStore = ParentalControlStore(appContext)

    suspend fun exportTo(uri: Uri): BackupSummary {
        val snapshot = database.withTransaction {
            val sources = database.iptvDao().getSources()
            BackupSnapshot(
                groups = database.channelDao().getGroups(),
                channels = database.channelDao().getOrderedChannels(),
                iptvSources = sources.map { source ->
                    IptvSourceBackup(source, database.iptvDao().getChannelsForSource(source.id))
                },
                display = displayStore.load(),
                listFilter = listFilterStore.load(),
                sourceFilter = sourceFilterStore.snapshot(),
                sleepTimerEndAt = sleepTimerStore.endAtMillis(),
                parentalControl = parentalControlStore.snapshot(),
            )
        }
        val json = snapshot.toJson().toString(2)
        appContext.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
            it.write(json)
        } ?: error("Yedek dosyası açılamadı")
        return snapshot.summary()
    }

    suspend fun importFrom(uri: Uri): BackupSummary {
        val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
            it.readText()
        } ?: error("Yedek dosyası okunamadı")
        val snapshot = BackupSnapshot.fromJson(JSONObject(text))
        database.withTransaction {
            val channelDao = database.channelDao()
            val iptvDao = database.iptvDao()
            channelDao.deleteAllChannels()
            channelDao.deleteAllGroups()
            iptvDao.deleteAllSources()
            if (snapshot.groups.isNotEmpty()) channelDao.upsertGroups(snapshot.groups)
            if (snapshot.iptvSources.isNotEmpty()) {
                iptvDao.upsertSources(snapshot.iptvSources.map { it.source })
                val iptvChannels = snapshot.iptvSources.flatMap { it.channels }
                if (iptvChannels.isNotEmpty()) iptvDao.upsertChannels(iptvChannels)
            }
            if (snapshot.channels.isNotEmpty()) channelDao.upsertChannels(snapshot.channels)
        }
        displayStore.save(snapshot.display)
        listFilterStore.save(snapshot.listFilter.source, snapshot.listFilter.favoritesOnly)
        sourceFilterStore.restore(snapshot.sourceFilter)
        val remainingMinutes = ((snapshot.sleepTimerEndAt - System.currentTimeMillis() + 59_999L) / 60_000L)
            .toInt().coerceAtLeast(0)
        sleepTimerStore.schedule(remainingMinutes)
        parentalControlStore.restore(snapshot.parentalControl)
        return snapshot.summary()
    }

    private data class IptvSourceBackup(
        val source: IptvSourceEntity,
        val channels: List<IptvChannelEntity>,
    )

    private data class BackupSnapshot(
        val groups: List<ChannelGroupEntity>,
        val channels: List<UserChannelEntity>,
        val iptvSources: List<IptvSourceBackup>,
        val display: DisplayPreferences,
        val listFilter: ChannelListFilterPreferences,
        val sourceFilter: ChannelSourceFilterSnapshot,
        val sleepTimerEndAt: Long,
        val parentalControl: ParentalControlSnapshot,
    ) {
        fun summary() = BackupSummary(
            channelCount = channels.size,
            iptvSourceCount = iptvSources.size,
            iptvChannelCount = iptvSources.sumOf { it.channels.size },
        )

        fun toJson(): JSONObject = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("createdAt", System.currentTimeMillis())
            put("groups", JSONArray().apply { groups.forEach { put(it.toJson()) } })
            put("channels", JSONArray().apply { channels.forEach { put(it.toJson()) } })
            put("iptvSources", JSONArray().apply {
                iptvSources.forEach { backup ->
                    put(backup.source.toJson().apply {
                        put("channels", JSONArray().apply {
                            backup.channels.forEach { put(it.toJson()) }
                        })
                    })
                }
            })
            put("display", display.toJson())
            put("channelListFilter", JSONObject().apply {
                put("source", listFilter.source.name)
                put("favoritesOnly", listFilter.favoritesOnly)
            })
            put("tifInputFilter", JSONObject().apply {
                put("configured", sourceFilter.configured)
                put("enabledInputIds", JSONArray(sourceFilter.enabledInputIds.toList()))
            })
            put("sleepTimerEndAt", sleepTimerEndAt)
            put("parentalControl", JSONObject().apply {
                putNullable("pinHash", parentalControl.pinHash)
                put("lockedSourceKeys", JSONArray(parentalControl.lockedSourceKeys.toList()))
            })
        }

        companion object {
            fun fromJson(root: JSONObject): BackupSnapshot {
                require(root.optString("format") == FORMAT) { "Bu dosya TVApp yedeği değil" }
                val version = root.optInt("version", -1)
                require(version in 1..VERSION) { "Desteklenmeyen yedek sürümü: $version" }
                val groups = root.requireArray("groups").objects().map { it.toGroup() }
                val groupIds = groups.mapTo(hashSetOf()) { it.id }
                val channels = root.requireArray("channels").objects().map { json ->
                    json.toUserChannel().let { channel ->
                        if (channel.groupId == null || channel.groupId in groupIds) channel
                        else channel.copy(groupId = null)
                    }
                }
                val sourceBackups = root.requireArray("iptvSources").objects().map { json ->
                    val source = json.toIptvSource()
                    IptvSourceBackup(
                        source = source,
                        channels = json.requireArray("channels").objects().map {
                            it.toIptvChannel(source.id)
                        },
                    )
                }
                val display = root.requireObject("display").toDisplayPreferences()
                val listFilterJson = root.requireObject("channelListFilter")
                val sourceFilterJson = root.requireObject("tifInputFilter")
                val parentalJson = root.optJSONObject("parentalControl") ?: JSONObject()
                return BackupSnapshot(
                    groups = groups,
                    channels = channels,
                    iptvSources = sourceBackups,
                    display = display,
                    listFilter = ChannelListFilterPreferences(
                        source = enumValue(
                            listFilterJson.requireString("source"),
                            ChannelSourceFilter.ALL,
                        ),
                        favoritesOnly = listFilterJson.optBoolean("favoritesOnly", false),
                    ),
                    sourceFilter = ChannelSourceFilterSnapshot(
                        configured = sourceFilterJson.optBoolean("configured", false),
                        enabledInputIds = sourceFilterJson.requireArray("enabledInputIds")
                            .strings().toSet(),
                    ),
                    sleepTimerEndAt = root.optLong("sleepTimerEndAt", 0L),
                    parentalControl = ParentalControlSnapshot(
                        pinHash = parentalJson.nullableString("pinHash"),
                        lockedSourceKeys = parentalJson.optJSONArray("lockedSourceKeys")
                            ?.strings()?.toSet().orEmpty(),
                    ),
                )
            }
        }
    }

    private companion object {
        const val FORMAT = "TVApp-backup"
        const val VERSION = 1

        fun ChannelGroupEntity.toJson() = JSONObject().apply {
            put("id", id); put("name", name); put("sortOrder", sortOrder)
        }

        fun UserChannelEntity.toJson() = JSONObject().apply {
            put("sourceKey", sourceKey); put("sourceType", sourceType)
            put("originalDisplayNumber", originalDisplayNumber); put("lastKnownName", lastKnownName)
            putNullable("customNumber", customNumber); putNullable("customName", customName)
            put("sortOrder", sortOrder); put("favorite", favorite); put("hidden", hidden)
            putNullable("groupId", groupId); put("lastSeenAt", lastSeenAt)
        }

        fun IptvSourceEntity.toJson() = JSONObject().apply {
            put("id", id); put("name", name); put("location", location); put("kind", kind)
            put("enabled", enabled); put("lastUpdatedAt", lastUpdatedAt)
        }

        fun IptvChannelEntity.toJson() = JSONObject().apply {
            put("sourceKey", sourceKey); putNullable("tvgId", tvgId); putNullable("tvgName", tvgName)
            put("displayName", displayName); put("streamUrl", streamUrl); putNullable("logoUrl", logoUrl)
            putNullable("groupTitle", groupTitle); putNullable("userAgent", userAgent)
            putNullable("referrer", referrer); put("originalIndex", originalIndex)
            put("selected", selected); put("lastSeenAt", lastSeenAt)
        }

        fun DisplayPreferences.toJson() = JSONObject().apply {
            put("infoBarPosition", infoBarPosition.name); put("showCurrentProgram", showCurrentProgram)
            put("showNextProgram", showNextProgram); put("showChannelLogo", showChannelLogo)
            put("showChannelProgram", showChannelProgram); put("showChannelProgress", showChannelProgress)
            put("showChannelSourceBadge", showChannelSourceBadge); put("channelPanelSide", channelPanelSide.name)
            put("infoBarOpacityPercent", infoBarOpacityPercent)
            put("channelPanelOpacityPercent", channelPanelOpacityPercent)
            put("infoBarDurationSeconds", infoBarDurationSeconds); put("subtitlesEnabled", subtitlesEnabled)
            put("channelFocusAutoTune", channelFocusAutoTune)
            put("channelFocusTuneDelayMillis", channelFocusTuneDelayMillis)
            put("launchOnBoot", launchOnBoot); putNullable("preferredAudioLanguage", preferredAudioLanguage)
            putNullable("preferredSubtitleLanguage", preferredSubtitleLanguage)
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
            groupId = nullableLong("groupId"), lastSeenAt = optLong("lastSeenAt", 0L),
        )

        fun JSONObject.toIptvSource() = IptvSourceEntity(
            id = requireLong("id"), name = requireString("name"), location = requireString("location"),
            kind = requireString("kind"), enabled = optBoolean("enabled", true),
            lastUpdatedAt = optLong("lastUpdatedAt", 0L),
        )

        fun JSONObject.toIptvChannel(sourceId: Long) = IptvChannelEntity(
            sourceKey = requireString("sourceKey"), sourceId = sourceId, tvgId = nullableString("tvgId"),
            tvgName = nullableString("tvgName"), displayName = requireString("displayName"),
            streamUrl = requireString("streamUrl"), logoUrl = nullableString("logoUrl"),
            groupTitle = nullableString("groupTitle"), userAgent = nullableString("userAgent"),
            referrer = nullableString("referrer"), originalIndex = requireInt("originalIndex"),
            selected = optBoolean("selected", false), lastSeenAt = optLong("lastSeenAt", 0L),
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

        fun JSONObject.putNullable(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)
        fun JSONObject.requireObject(key: String): JSONObject = optJSONObject(key)
            ?: error("Eksik alan: $key")
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
        fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
        fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
        inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == value } ?: fallback
    }
}
