package com.tvapp.livetv.settings

import android.content.Context

class MultiViewPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun forceFourStreams(): Boolean = preferences.getBoolean(KEY_FORCE_FOUR_STREAMS, false)

    fun setForceFourStreams(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FORCE_FOUR_STREAMS, enabled).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "multiview-preferences"
        const val KEY_FORCE_FOUR_STREAMS = "force_four_streams"
    }
}
