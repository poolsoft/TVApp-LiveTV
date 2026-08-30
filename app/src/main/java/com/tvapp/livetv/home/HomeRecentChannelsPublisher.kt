package com.tvapp.livetv.home

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.tvapp.livetv.R
import com.tvapp.livetv.model.LiveChannel
import org.json.JSONArray

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
        val channelId = ensurePreviewChannel()
        programIds().forEach { id -> runCatching { helper.deletePreviewProgram(id) } }
        val published = recent.mapNotNull { channel ->
            runCatching {
                helper.publishPreviewProgram(
                    PreviewProgram.Builder()
                        .setChannelId(channelId)
                        .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                        .setTitle(channel.displayName)
                        .setDescription(channel.displayNumber)
                        .setPosterArtUri(posterUri(channel))
                        .setIntentUri(channelIntentUri(channel.sourceKey))
                        .setInternalProviderId(channel.sourceKey)
                        .build(),
                )
            }.getOrNull()
        }
        preferences.edit().putString(KEY_PROGRAM_IDS, JSONArray(published).toString()).apply()
    }

    private fun ensurePreviewChannel(): Long {
        val saved = preferences.getLong(KEY_CHANNEL_ID, 0L)
        if (saved > 0L) return saved
        val channel = PreviewChannel.Builder()
            .setDisplayName(appContext.getString(R.string.home_recent_channels))
            .setAppLinkIntentUri(Uri.parse("tvapp://channel/recent"))
            .build()
        return helper.publishDefaultChannel(channel).also { id ->
            preferences.edit().putLong(KEY_CHANNEL_ID, id).apply()
        }
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

    private fun programIds(): List<Long> = runCatching {
        val array = JSONArray(preferences.getString(KEY_PROGRAM_IDS, "[]"))
        (0 until array.length()).map { array.getLong(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_PROGRAMS = 12
        const val KEY_CHANNEL_ID = "channel-id"
        const val KEY_PROGRAM_IDS = "program-ids"
    }
}
