package com.tvapp.livetv.settings

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

private const val KEY_AUDIO = "audio"
private const val KEY_SUBTITLE = "subtitle"
private const val KEY_SUBTITLES_ENABLED = "subtitlesEnabled"

data class ChannelTrackPreference(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitlesEnabled: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        audioLanguage?.let { put(KEY_AUDIO, it) }
        subtitleLanguage?.let { put(KEY_SUBTITLE, it) }
        subtitlesEnabled?.let { put(KEY_SUBTITLES_ENABLED, it) }
    }
}

data class ResolvedTrackPreferences(
    val audioLanguage: String?,
    val subtitlesEnabled: Boolean,
    val subtitleLanguage: String?,
)

fun resolveTrackPreferences(
    channel: ChannelTrackPreference?,
    globalAudioLanguage: String?,
    globalSubtitlesEnabled: Boolean,
    globalSubtitleLanguage: String?,
): ResolvedTrackPreferences = ResolvedTrackPreferences(
    audioLanguage = channel?.audioLanguage ?: globalAudioLanguage,
    subtitlesEnabled = channel?.subtitlesEnabled ?: globalSubtitlesEnabled,
    subtitleLanguage = channel?.subtitleLanguage ?: globalSubtitleLanguage,
)

class ChannelTrackPreferenceStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(sourceKey: String): ChannelTrackPreference? {
        val stored = preferences.getString(sourceKey, null) ?: return null
        return try {
            val json = JSONObject(stored)
            ChannelTrackPreference(
                audioLanguage = json.stringOrNull(KEY_AUDIO),
                subtitleLanguage = json.stringOrNull(KEY_SUBTITLE),
                subtitlesEnabled = if (json.has(KEY_SUBTITLES_ENABLED)) {
                    json.getBoolean(KEY_SUBTITLES_ENABLED)
                } else {
                    null
                },
            )
        } catch (ignored: JSONException) {
            null
        }
    }

    fun save(
        sourceKey: String,
        transform: (ChannelTrackPreference) -> ChannelTrackPreference,
    ) {
        val current = load(sourceKey) ?: ChannelTrackPreference()
        val updated = transform(current)
        preferences.edit().putString(sourceKey, updated.toJson().toString()).apply()
    }

    fun remove(sourceKey: String) {
        preferences.edit().remove(sourceKey).apply()
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private companion object {
        const val PREFERENCES_NAME = "channel-track-preferences"
    }
}
