package com.tvapp.livetv.playback

import android.content.Context

class IptvResumeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun position(sourceKey: String): Long = preferences.getLong(sourceKey, 0L)
        .takeIf { it >= MINIMUM_RESUME_POSITION_MS }
        ?.minus(RESUME_REWIND_MS)
        ?.coerceAtLeast(0L)
        ?: 0L

    fun save(sourceKey: String, positionMillis: Long, durationMillis: Long) {
        if (
            positionMillis < MINIMUM_RESUME_POSITION_MS ||
            durationMillis <= 0L ||
            positionMillis >= durationMillis - FINISHED_MARGIN_MS
        ) {
            clear(sourceKey)
            return
        }
        preferences.edit().putLong(sourceKey, positionMillis).apply()
    }

    fun clear(sourceKey: String) {
        preferences.edit().remove(sourceKey).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "iptv-resume"
        const val MINIMUM_RESUME_POSITION_MS = 30_000L
        const val RESUME_REWIND_MS = 5_000L
        const val FINISHED_MARGIN_MS = 60_000L
    }
}
