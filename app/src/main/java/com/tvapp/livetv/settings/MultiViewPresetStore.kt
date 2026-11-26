package com.tvapp.livetv.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MultiViewPreset(
    val id: String,
    val name: String,
    val channelKeys: List<String>,
)

class MultiViewPresetStore internal constructor(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    fun loadAll(): List<MultiViewPreset> {
        val raw = preferences.getString(KEY_PRESETS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString(FIELD_ID).trim()
                    val name = obj.optString(FIELD_NAME).trim()
                    val keysArray = obj.optJSONArray(FIELD_KEYS) ?: continue
                    val keys = buildList {
                        for (j in 0 until keysArray.length()) {
                            val key = keysArray.optString(j).trim()
                            if (key.isNotEmpty()) add(key)
                        }
                    }.distinct().take(MAX_CHANNELS)
                    if (id.isNotEmpty() && name.isNotEmpty() && keys.isNotEmpty()) {
                        add(MultiViewPreset(id = id, name = name, channelKeys = keys))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(name: String, channelKeys: List<String>): MultiViewPreset? {
        val cleanName = name.trim()
        val cleanKeys = channelKeys.map(String::trim).filter(String::isNotEmpty).distinct().take(MAX_CHANNELS)
        if (cleanName.isEmpty() || cleanKeys.isEmpty()) return null

        val existing = loadAll().toMutableList()
        val matchIndex = existing.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        val saved = if (matchIndex >= 0) {
            existing[matchIndex].copy(name = cleanName, channelKeys = cleanKeys).also {
                existing[matchIndex] = it
            }
        } else {
            MultiViewPreset(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                channelKeys = cleanKeys,
            ).also { existing.add(0, it) }
        }
        persist(existing)
        return saved
    }

    fun delete(id: String) {
        val updated = loadAll().filterNot { it.id == id }
        persist(updated)
    }

    private fun persist(presets: List<MultiViewPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            val keysArray = JSONArray().apply {
                preset.channelKeys.forEach { put(it) }
            }
            val obj = JSONObject().apply {
                put(FIELD_ID, preset.id)
                put(FIELD_NAME, preset.name)
                put(FIELD_KEYS, keysArray)
            }
            array.put(obj)
        }
        preferences.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "multiview-presets"
        private const val KEY_PRESETS = "presets_json"
        private const val FIELD_ID = "id"
        private const val FIELD_NAME = "name"
        private const val FIELD_KEYS = "channel_keys"
        private const val MAX_CHANNELS = 4

        fun suggestName(channelNames: List<String>): String {
            val clean = channelNames.map(String::trim).filter(String::isNotEmpty).distinct()
            return when {
                clean.isEmpty() -> ""
                clean.size <= 2 -> clean.joinToString(" + ")
                else -> "${clean[0]} + ${clean[1]} +${clean.size - 2}"
            }
        }
    }
}
