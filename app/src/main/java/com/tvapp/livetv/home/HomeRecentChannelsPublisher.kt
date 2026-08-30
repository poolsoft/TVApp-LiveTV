package com.tvapp.livetv.home

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.tvapp.livetv.R
import com.tvapp.livetv.model.LiveChannel
import org.json.JSONArray
import org.json.JSONObject

class HomeRecentChannelsPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val helper = PreviewChannelHelper(appContext)
    private val preferences = appContext.getSharedPreferences("home-recents", Context.MODE_PRIVATE)

    // These builders are the documented TvProvider API despite 1.0.0's restrictive annotations.
    @SuppressLint("RestrictedApi")
    fun publish(channels: List<LiveChannel>, historyKeys: List<String>) {
        val recent = historyKeys.mapNotNull { key -> channels.firstOrNull { it.sourceKey == key } }
            .take(MAX_PROGRAMS)
        if (recent.isEmpty()) return
        // Google TV uses Watch Next. Publishing a legacy preview channel without a bitmap logo
        // creates an unusable provider row and retries on every channel change.
        publishWatchNext(recent)
    }

    @SuppressLint("RestrictedApi")
    private fun publishWatchNext(recent: List<LiveChannel>) {
        val desired = recent.take(MAX_WATCH_NEXT_PROGRAMS)
            .filterNot { it.sourceKey in suppressedSourceKeys() }
        val desiredKeys = desired.mapTo(mutableSetOf()) { it.sourceKey }
        val ids = watchNextIds()

        ids.keys.filterNot { it in desiredKeys }.forEach { sourceKey ->
            ids.remove(sourceKey)?.let { id ->
                runCatching {
                    appContext.contentResolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(id),
                        null,
                        null,
                    )
                }
            }
        }

        val now = System.currentTimeMillis()
        desired.forEachIndexed { index, channel ->
            val program = WatchNextProgram.Builder()
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(now - index)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(channel.displayName)
                .setDescription(
                    appContext.getString(
                        R.string.home_watch_next_description,
                        channel.displayNumber,
                    ),
                )
                .setPosterArtUri(posterUri(channel))
                .setIntentUri(channelIntentUri(channel.sourceKey))
                .setInternalProviderId(channel.sourceKey)
                .setContentId(channel.sourceKey)
                .setLive(true)
                .build()
            val savedId = ids[channel.sourceKey]
            val existing = savedId?.let {
                runCatching { helper.getWatchNextProgram(it) }.getOrNull()
            }
            val id = if (savedId != null && existing != null) {
                runCatching { helper.updateWatchNextProgram(program, savedId) }
                savedId
            } else {
                runCatching { helper.publishWatchNextProgram(program) }.getOrNull()
            }
            if (id != null && id > 0L) ids[channel.sourceKey] = id
        }
        saveWatchNextIds(ids)
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
        .build()

    private fun watchNextIds(): MutableMap<String, Long> = readWatchNextIds(preferences)

    private fun saveWatchNextIds(ids: Map<String, Long>) {
        preferences.edit().putString(KEY_WATCH_NEXT_IDS, ids.toJson().toString()).apply()
    }

    private fun suppressedSourceKeys(): Set<String> = runCatching {
        val array = JSONArray(preferences.getString(KEY_SUPPRESSED_SOURCE_KEYS, "[]"))
        buildSet { for (index in 0 until array.length()) add(array.getString(index)) }
    }.getOrDefault(emptySet())

    companion object {
        fun suppressWatchNext(context: Context, programId: Long) {
            val preferences = context.applicationContext.getSharedPreferences(
                "home-recents",
                Context.MODE_PRIVATE,
            )
            val ids = readWatchNextIds(preferences)
            val removedKey = ids.entries.firstOrNull { it.value == programId }?.key ?: return
            ids.remove(removedKey)
            val suppressed = runCatching {
                val array = JSONArray(preferences.getString(KEY_SUPPRESSED_SOURCE_KEYS, "[]"))
                buildSet { for (index in 0 until array.length()) add(array.getString(index)) }
            }.getOrDefault(emptySet()) + removedKey
            preferences.edit()
                .putString(KEY_WATCH_NEXT_IDS, ids.toJson().toString())
                .putString(KEY_SUPPRESSED_SOURCE_KEYS, JSONArray(suppressed.toList()).toString())
                .apply()
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
    }
}
