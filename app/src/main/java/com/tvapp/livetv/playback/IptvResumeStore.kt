package com.tvapp.livetv.playback

import android.content.Context

class IptvResumeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun position(sourceKey: String): Long {
        val saved = preferences.getLong(positionKey(sourceKey), 0L).takeIf { it > 0L }
            ?: preferences.getLong(sourceKey, 0L)
        return saved.takeIf { it >= MINIMUM_RESUME_POSITION_MS }
            ?.minus(RESUME_REWIND_MS)
            ?.coerceAtLeast(0L)
            ?: 0L
    }

    fun save(sourceKey: String, positionMillis: Long, durationMillis: Long) {
        if (
            positionMillis < MINIMUM_RESUME_POSITION_MS ||
            durationMillis <= 0L ||
            positionMillis >= durationMillis - FINISHED_MARGIN_MS
        ) {
            clear(sourceKey)
            return
        }
        preferences.edit()
            .putLong(positionKey(sourceKey), positionMillis)
            .putLong(updatedKey(sourceKey), System.currentTimeMillis())
            .remove(sourceKey)
            .apply()
    }

    fun clear(sourceKey: String) {
        preferences.edit()
            .remove(positionKey(sourceKey))
            .remove(updatedKey(sourceKey))
            .remove(sourceKey)
            .apply()
    }

    fun entries(): List<IptvResumeEntry> = preferences.all
        .asSequence()
        .mapNotNull { (key, value) ->
            val isCurrent = key.startsWith(POSITION_PREFIX)
            val isLegacy = key.startsWith("iptv:") && value is Long
            if (!isCurrent && !isLegacy) return@mapNotNull null
            val sourceKey = if (isCurrent) key.removePrefix(POSITION_PREFIX) else key
            val position = value as? Long ?: return@mapNotNull null
            if (position < MINIMUM_RESUME_POSITION_MS) null else IptvResumeEntry(
                sourceKey = sourceKey,
                positionMillis = position,
                updatedAt = preferences.getLong(updatedKey(sourceKey), 0L),
            )
        }
        .distinctBy(IptvResumeEntry::sourceKey)
        .sortedByDescending(IptvResumeEntry::updatedAt)
        .toList()

    private fun positionKey(sourceKey: String) = "$POSITION_PREFIX$sourceKey"

    private fun updatedKey(sourceKey: String) = "$UPDATED_PREFIX$sourceKey"

    private companion object {
        const val PREFERENCES_NAME = "iptv-resume"
        const val POSITION_PREFIX = "position:"
        const val UPDATED_PREFIX = "updated:"
        const val MINIMUM_RESUME_POSITION_MS = 30_000L
        const val RESUME_REWIND_MS = 5_000L
        const val FINISHED_MARGIN_MS = 60_000L
    }
}

data class IptvResumeEntry(
    val sourceKey: String,
    val positionMillis: Long,
    val updatedAt: Long,
)
