package com.tvapp.livetv.settings

import android.content.Context

data class IptvPlaybackPreferences(
    val targetBufferSeconds: Int = DEFAULT_BUFFER_SECONDS,
    val vodPlaybackSpeed: Float = DEFAULT_VOD_SPEED,
    val engineMode: IptvPlaybackEngineMode = IptvPlaybackEngineMode.AUTO_FALLBACK,
) {
    companion object {
        const val AUTO_BUFFER_SECONDS = 0
        const val DEFAULT_BUFFER_SECONDS = 20
        const val DEFAULT_VOD_SPEED = 1f
        val BUFFER_OPTIONS = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}

enum class IptvPlaybackEngineMode {
    MEDIA3,
    AUTO_FALLBACK,
    IJK,
}

class IptvPlaybackPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): IptvPlaybackPreferences = IptvPlaybackPreferences(
        targetBufferSeconds = preferences.getInt(KEY_BUFFER, IptvPlaybackPreferences.DEFAULT_BUFFER_SECONDS)
            .takeIf { it in IptvPlaybackPreferences.BUFFER_OPTIONS }
            ?: IptvPlaybackPreferences.DEFAULT_BUFFER_SECONDS,
        vodPlaybackSpeed = preferences.getFloat(KEY_SPEED, IptvPlaybackPreferences.DEFAULT_VOD_SPEED)
            .takeIf { it in IptvPlaybackPreferences.SPEED_OPTIONS }
            ?: IptvPlaybackPreferences.DEFAULT_VOD_SPEED,
        engineMode = preferences.getString(KEY_ENGINE_MODE, null)
            ?.let { stored -> IptvPlaybackEngineMode.entries.firstOrNull { it.name == stored } }
            ?: IptvPlaybackEngineMode.AUTO_FALLBACK,
    )

    fun saveTargetBufferSeconds(seconds: Int) {
        require(seconds in IptvPlaybackPreferences.BUFFER_OPTIONS)
        preferences.edit().putInt(KEY_BUFFER, seconds).apply()
    }

    fun saveVodPlaybackSpeed(speed: Float) {
        require(speed in IptvPlaybackPreferences.SPEED_OPTIONS)
        preferences.edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun saveEngineMode(mode: IptvPlaybackEngineMode) {
        preferences.edit().putString(KEY_ENGINE_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFS = "iptv_playback"
        const val KEY_BUFFER = "target_buffer_seconds"
        const val KEY_SPEED = "vod_playback_speed"
        const val KEY_ENGINE_MODE = "engine_mode"
    }
}
