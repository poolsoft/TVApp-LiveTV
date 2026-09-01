package com.tvapp.livetv.settings

import android.content.Context

data class LogoCachePreferences(
    val enabled: Boolean = true,
    val maximumMegabytes: Int = 128,
)

class LogoCachePreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load() = LogoCachePreferences(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        maximumMegabytes = preferences.getInt(KEY_MAXIMUM_MEGABYTES, 128),
    )

    fun save(value: LogoCachePreferences) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putInt(KEY_MAXIMUM_MEGABYTES, value.maximumMegabytes)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "channel-logo-cache"
        const val KEY_ENABLED = "enabled"
        const val KEY_MAXIMUM_MEGABYTES = "maximum-megabytes"
    }
}
