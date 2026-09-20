package com.tvapp.livetv.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.tvapp.livetv.R
import com.tvapp.livetv.model.LiveChannel
import org.json.JSONArray
import org.json.JSONObject

class HomeRecentChannelsPublisher(
    context: Context,
    private val onOperationFailed: (op: String, sourceKey: String, error: Throwable) -> Unit = { _, _, _ -> },
) {
    private val appContext = context.applicationContext
    private val helper = PreviewChannelHelper(appContext)
    private val preferences = appContext.getSharedPreferences("home-recents", Context.MODE_PRIVATE)

    @SuppressLint("RestrictedApi")
    fun publishVod(channel: LiveChannel, positionMillis: Long, durationMillis: Long) {
        if (channel.sourceKey in suppressedSourceKeys()) return
        if (
            positionMillis < MINIMUM_RESUME_POSITION_MS ||
            durationMillis <= 0L ||
            positionMillis >= durationMillis - FINISHED_MARGIN_MS
        ) {
            removeVod(channel.sourceKey)
            return
        }
        val ids = watchNextIds()
        val now = System.currentTimeMillis()
        val program = WatchNextProgram.Builder()
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(now)
            .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
            .setTitle(channel.displayName)
            .setDescription(
                channel.groupTitle?.takeIf(String::isNotBlank)
                    ?: appContext.getString(R.string.iptv_library_vod),
            )
            .setPosterArtUri(posterUri(channel))
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            .setInteractionType(TvContractCompat.PreviewProgramColumns.INTERACTION_TYPE_VIEWS)
            .setDurationMillis(durationMillis.coerceAtLeast(0L).toInt())
            .setLastPlaybackPositionMillis(positionMillis.coerceAtLeast(0L).toInt())
            .setIntentUri(channelIntentUri(channel.sourceKey))
            .setInternalProviderId(channel.sourceKey)
            .setContentId(channel.sourceKey)
            .build()

        val savedId = ids[channel.sourceKey]
        val existing = savedId?.let {
            runCatching { helper.getWatchNextProgram(it) }.getOrNull()
        }
        val id = if (savedId != null && existing != null) {
            runCatching { helper.updateWatchNextProgram(program, savedId) }
                .onFailure { error -> onOperationFailed("update", channel.sourceKey, error) }
            savedId
        } else {
            runCatching { helper.publishWatchNextProgram(program) }
                .onFailure { error -> onOperationFailed("publish", channel.sourceKey, error) }
                .getOrNull()
        }
        if (id != null && id > 0L) {
            ids[channel.sourceKey] = id
            saveWatchNextIds(ids)
        }
    }

    suspend fun syncResumeVods(
        iptvRepository: com.tvapp.livetv.data.IptvRepository,
        resumeStore: com.tvapp.livetv.playback.IptvResumeStore,
    ) {
        val entries = resumeStore.entries().take(MAX_WATCH_NEXT_PROGRAMS)
        entries.forEach { entry ->
            val channel = iptvRepository.channel(entry.sourceKey)
            if (channel != null) {
                val duration = if (entry.durationMillis > 0L) entry.durationMillis else resumeStore.duration(entry.sourceKey)
                publishVod(channel, entry.positionMillis, duration)
            }
        }
    }

    fun removeVod(sourceKey: String) {
        val ids = watchNextIds()
        val id = ids.remove(sourceKey) ?: return
        saveWatchNextIds(ids)
        runCatching {
            appContext.contentResolver.delete(
                TvContractCompat.buildWatchNextProgramUri(id),
                null,
                null,
            )
        }.onFailure { error -> onOperationFailed("removeVod", sourceKey, error) }
    }

    @SuppressLint("RestrictedApi")
    fun cleanupLegacyLiveChannels() {
        runCatching {
            val ids = watchNextIds()
            val toRemove = mutableListOf<String>()
            ids.forEach { (sourceKey, id) ->
                val program = runCatching { helper.getWatchNextProgram(id) }.getOrNull()
                if (program != null && program.type == TvContractCompat.PreviewPrograms.TYPE_CHANNEL) {
                    toRemove.add(sourceKey)
                    appContext.contentResolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(id),
                        null,
                        null,
                    )
                }
            }
            if (toRemove.isNotEmpty()) {
                toRemove.forEach { ids.remove(it) }
                saveWatchNextIds(ids)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    fun ensurePreviewChannel(channels: List<LiveChannel> = emptyList()) {
        runCatching {
            val existingChannels = helper.allChannels
            val channelId = if (existingChannels.isNotEmpty()) {
                existingChannels.first().id
            } else {
                val logoBitmap = BitmapFactory.decodeResource(appContext.resources, R.drawable.app_banner)
                val previewChannel = PreviewChannel.Builder()
                    .setDisplayName(appContext.getString(R.string.app_name))
                    .setDescription(appContext.getString(R.string.iptv_library_continue))
                    .setAppLinkIntentUri(channelIntentUri("home"))
                    .setLogo(logoBitmap)
                    .build()
                val id = runCatching { helper.publishDefaultChannel(previewChannel) }.getOrDefault(0L)
                val finalId = if (id <= 0L) runCatching { helper.publishChannel(previewChannel) }.getOrDefault(0L) else id
                if (finalId > 0L) {
                    TvContractCompat.requestChannelBrowsable(appContext, finalId)
                }
                finalId
            }
            if (channelId > 0L && channels.isNotEmpty()) {
                updatePreviewPrograms(channelId, channels)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun updatePreviewPrograms(channelId: Long, channels: List<LiveChannel>) {
        runCatching {
            val targetChannels = channels.take(10)
            if (targetChannels.isEmpty()) return
            appContext.contentResolver.delete(
                TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                null,
                null,
            )
            targetChannels.forEachIndexed { index, channel ->
                val program = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                    .setTitle(channel.displayName)
                    .setDescription(
                        channel.groupTitle?.takeIf(String::isNotBlank)
                            ?: channel.displayNumber,
                    )
                    .setPosterArtUri(posterUri(channel))
                    .setIntentUri(channelIntentUri(channel.sourceKey))
                    .setInternalProviderId(channel.sourceKey)
                    .setContentId(channel.sourceKey)
                    .setWeight(100 - index)
                    .setLive(true)
                    .build()
                runCatching { helper.publishPreviewProgram(program) }
            }
        }
    }

    // Retained for backward compatibility
    @SuppressLint("RestrictedApi")
    fun publish(channels: List<LiveChannel>, historyKeys: List<String>) {
        cleanupLegacyLiveChannels()
        ensurePreviewChannel(channels)
    }

    private fun posterUri(channel: LiveChannel): Uri = channel.logoUrl
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
        ?: Uri.parse("android.resource://${appContext.packageName}/${R.drawable.app_banner}")

    private fun channelIntentUri(sourceKey: String): Uri = Uri.Builder()
        .scheme("tvapp")
        .authority("channel")
        .appendPath("open")
        .appendQueryParameter("sourceKey", sourceKey)
        .appendQueryParameter("resume", "true")
        .build()

    private fun watchNextIds(): MutableMap<String, Long> = readWatchNextIds(preferences)

    private fun saveWatchNextIds(ids: Map<String, Long>) {
        preferences.edit().putString(KEY_WATCH_NEXT_IDS, ids.toJson().toString()).apply()
    }

    private fun suppressedSourceKeys(): Set<String> = runCatching {
        val array = JSONArray(preferences.getString(KEY_SUPPRESSED_SOURCE_KEYS, "[]"))
        buildSet { for (index in 0 until array.length()) add(array.getString(index)) }
    }.getOrDefault(emptySet()).take(MAX_SUPPRESSED_KEYS).toSet()

    companion object {
        fun suppressWatchNext(context: Context, programId: Long): String? {
            val preferences = context.applicationContext.getSharedPreferences(
                "home-recents",
                Context.MODE_PRIVATE,
            )
            val ids = readWatchNextIds(preferences)
            val removedKey = ids.entries.firstOrNull { it.value == programId }?.key ?: return null
            ids.remove(removedKey)
            val suppressed = runCatching {
                val array = JSONArray(preferences.getString(KEY_SUPPRESSED_SOURCE_KEYS, "[]"))
                buildSet { for (index in 0 until array.length()) add(array.getString(index)) }
            }.getOrDefault(emptySet()) + removedKey
            preferences.edit()
                .putString(KEY_WATCH_NEXT_IDS, ids.toJson().toString())
                .putString(KEY_SUPPRESSED_SOURCE_KEYS, JSONArray(suppressed.toList()).toString())
                .apply()
            return removedKey
        }

        private fun readWatchNextIds(preferences: android.content.SharedPreferences): MutableMap<String, Long> =
            runCatching {
                val json = JSONObject(preferences.getString(KEY_WATCH_NEXT_IDS, "{}") ?: "{}")
                buildMap {
                    json.keys().forEach { key -> put(key, json.getLong(key)) }
                }.toMutableMap()
            }.getOrDefault(mutableMapOf())

        private fun Map<String, Long>.toJson(): JSONObject = JSONObject().also { json ->
            forEach { (key, id) -> json.put(key, id) }
        }

        const val MAX_PROGRAMS = 12
        private const val MAX_WATCH_NEXT_PROGRAMS = 6
        const val KEY_CHANNEL_ID = "channel-id"
        const val KEY_PROGRAM_IDS = "program-ids"
        private const val KEY_WATCH_NEXT_IDS = "watch-next-ids"
        private const val KEY_SUPPRESSED_SOURCE_KEYS = "suppressed-watch-next-source-keys"
        private const val MINIMUM_RESUME_POSITION_MS = 30_000L
        private const val FINISHED_MARGIN_MS = 60_000L
        private const val MAX_SUPPRESSED_KEYS = 64
    }
}
