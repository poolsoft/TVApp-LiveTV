package com.tvapp.livetv.settings

import android.content.Context

enum class ExperienceModeOverride {
    AUTOMATIC,
    HYBRID,
    IPTV_ONLY,
}

class ExperienceModePreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): ExperienceModeOverride = runCatching {
        ExperienceModeOverride.valueOf(
            preferences.getString(KEY_MODE, ExperienceModeOverride.AUTOMATIC.name).orEmpty(),
        )
    }.getOrDefault(ExperienceModeOverride.AUTOMATIC)

    fun save(mode: ExperienceModeOverride) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "experience-mode"
        const val KEY_MODE = "mode"
    }
}
