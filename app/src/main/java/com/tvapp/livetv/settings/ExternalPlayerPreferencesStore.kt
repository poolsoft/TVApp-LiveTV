package com.tvapp.livetv.settings

import android.content.Context

enum class ExternalPlayerPreference { SYSTEM, VLC, MX_PLAYER }

class ExternalPlayerPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): ExternalPlayerPreference = runCatching {
        ExternalPlayerPreference.valueOf(
            preferences.getString(KEY_PLAYER, ExternalPlayerPreference.SYSTEM.name).orEmpty(),
        )
    }.getOrDefault(ExternalPlayerPreference.SYSTEM)

    fun save(value: ExternalPlayerPreference) {
        preferences.edit().putString(KEY_PLAYER, value.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "external-player"
        const val KEY_PLAYER = "preferred-player"
    }
}
