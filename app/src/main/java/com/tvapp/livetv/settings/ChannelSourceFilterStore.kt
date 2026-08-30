package com.tvapp.livetv.settings

import android.content.Context

data class ChannelSourceFilterSnapshot(
    val configured: Boolean,
    val enabledInputIds: Set<String>,
)

class ChannelSourceFilterStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "channel-source-filter",
        Context.MODE_PRIVATE,
    )

    fun enabledInputIds(availableInputIds: Set<String>): Set<String> {
        if (!preferences.getBoolean(KEY_CONFIGURED, false)) return availableInputIds
        return preferences.getStringSet(KEY_ENABLED_INPUTS, emptySet()).orEmpty()
            .intersect(availableInputIds)
    }

    fun save(enabledInputIds: Set<String>) {
        preferences.edit()
            .putBoolean(KEY_CONFIGURED, true)
            .putStringSet(KEY_ENABLED_INPUTS, enabledInputIds)
            .apply()
    }

    fun snapshot(): ChannelSourceFilterSnapshot = ChannelSourceFilterSnapshot(
        configured = preferences.getBoolean(KEY_CONFIGURED, false),
        enabledInputIds = preferences.getStringSet(KEY_ENABLED_INPUTS, emptySet()).orEmpty(),
    )

    fun restore(snapshot: ChannelSourceFilterSnapshot) {
        preferences.edit()
            .putBoolean(KEY_CONFIGURED, snapshot.configured)
            .putStringSet(KEY_ENABLED_INPUTS, snapshot.enabledInputIds)
            .apply()
    }

    private companion object {
        const val KEY_CONFIGURED = "configured"
        const val KEY_ENABLED_INPUTS = "enabled-inputs"
    }
}
