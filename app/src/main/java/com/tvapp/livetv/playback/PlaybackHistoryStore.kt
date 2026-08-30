package com.tvapp.livetv.playback

import android.content.Context
import org.json.JSONArray

class PlaybackHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "playback-history",
        Context.MODE_PRIVATE,
    )

    fun record(sourceKey: String) {
        val updated = buildList {
            add(sourceKey)
            addAll(keys().filter { it != sourceKey }.take(MAX_ITEMS - 1))
        }
        preferences.edit().putString(KEY_HISTORY, JSONArray(updated).toString()).apply()
    }

    fun keys(): List<String> = runCatching {
        val array = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        buildList {
            for (index in 0 until array.length()) add(array.getString(index))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_HISTORY = "source-keys"
        const val MAX_ITEMS = 50
    }
}
