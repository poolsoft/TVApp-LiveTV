package com.tvapp.livetv.settings

import android.content.Context

enum class ChannelSourceFilter { ALL, SATELLITE, IPTV }

data class ChannelListFilterPreferences(
    val source: ChannelSourceFilter = ChannelSourceFilter.ALL,
    val favoritesOnly: Boolean = false,
)

class ChannelListFilterStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): ChannelListFilterPreferences = ChannelListFilterPreferences(
        source = runCatching {
            ChannelSourceFilter.valueOf(
                preferences.getString(KEY_SOURCE, ChannelSourceFilter.ALL.name).orEmpty(),
            )
        }.getOrDefault(ChannelSourceFilter.ALL),
        favoritesOnly = preferences.getBoolean(KEY_FAVORITES, false),
    )

    fun save(source: ChannelSourceFilter, favoritesOnly: Boolean) {
        preferences.edit()
            .putString(KEY_SOURCE, source.name)
            .putBoolean(KEY_FAVORITES, favoritesOnly)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "channel_list_filter"
        private const val KEY_SOURCE = "source"
        private const val KEY_FAVORITES = "favorites_only"
    }
}
