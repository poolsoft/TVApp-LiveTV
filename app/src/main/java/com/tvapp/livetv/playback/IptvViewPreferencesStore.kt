package com.tvapp.livetv.playback

import android.content.Context

class IptvViewPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun aspectMode(): IptvAspectMode = runCatching {
        IptvAspectMode.valueOf(
            preferences.getString(KEY_ASPECT_MODE, IptvAspectMode.FIT.name).orEmpty(),
        )
    }.getOrDefault(IptvAspectMode.FIT)

    fun setAspectMode(mode: IptvAspectMode) {
        preferences.edit().putString(KEY_ASPECT_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "iptv-view-preferences"
        const val KEY_ASPECT_MODE = "aspect-mode"
    }
}

enum class IptvAspectMode { FIT, FILL, ZOOM }
