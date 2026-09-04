package com.tvapp.livetv.reminder

import org.json.JSONArray
import org.json.JSONObject

/**
 * A scheduled reminder that fires a notification when an EPG program starts.
 * Identifiers are stable per (channel sourceKey, program start) pair so the
 * same program can be toggled deterministically.
 */
data class ProgramReminder(
    val id: String,
    val sourceKey: String,
    val channelName: String,
    val programTitle: String,
    val startTimeMillis: Long,
) {
    companion object {
        fun idFor(sourceKey: String, startTimeMillis: Long): String = "$sourceKey|$startTimeMillis"

        fun of(
            sourceKey: String,
            channelName: String,
            programTitle: String,
            startTimeMillis: Long,
        ): ProgramReminder = ProgramReminder(
            id = idFor(sourceKey, startTimeMillis),
            sourceKey = sourceKey,
            channelName = channelName,
            programTitle = programTitle,
            startTimeMillis = startTimeMillis,
        )

        internal fun toJson(reminders: List<ProgramReminder>): String {
            val array = JSONArray()
            reminders.forEach { reminder ->
                array.put(
                    JSONObject()
                        .put("id", reminder.id)
                        .put("sourceKey", reminder.sourceKey)
                        .put("channelName", reminder.channelName)
                        .put("programTitle", reminder.programTitle)
                        .put("startTimeMillis", reminder.startTimeMillis),
                )
            }
            return array.toString()
        }

        internal fun fromJson(raw: String?): List<ProgramReminder> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val id = item.optString("id")
                        val sourceKey = item.optString("sourceKey")
                        val start = item.optLong("startTimeMillis")
                        if (id.isBlank() || sourceKey.isBlank() || start <= 0L) continue
                        add(
                            ProgramReminder(
                                id = id,
                                sourceKey = sourceKey,
                                channelName = item.optString("channelName"),
                                programTitle = item.optString("programTitle"),
                                startTimeMillis = start,
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * Drops reminders whose programs already ended; keeps only future reminders.
 * Shared by the store (on every read) and the unit tests.
 */
fun List<ProgramReminder>.pruneExpired(now: Long): List<ProgramReminder> =
    filter { it.startTimeMillis > now }
