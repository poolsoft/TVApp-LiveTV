package com.tvapp.livetv.reminder

import android.content.Context

/**
 * Persists scheduled program reminders in a small SharedPreferences JSON list.
 * Reminders are transient by nature; expired entries are pruned on every read.
 */
class ProgramReminderStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun reminders(now: Long = System.currentTimeMillis()): List<ProgramReminder> {
        val stored = ProgramReminder.fromJson(preferences.getString(KEY_ITEMS, null))
        val active = stored.pruneExpired(now)
        if (active.size != stored.size) save(active)
        return active
    }

    fun reminderFor(sourceKey: String, startTimeMillis: Long): ProgramReminder? {
        val id = ProgramReminder.idFor(sourceKey, startTimeMillis)
        return reminders().firstOrNull { it.id == id }
    }

    fun put(reminder: ProgramReminder) {
        save(reminders().filterNot { it.id == reminder.id } + reminder)
    }

    fun remove(id: String) {
        save(reminders().filterNot { it.id == id })
    }

    fun remove(sourceKey: String, startTimeMillis: Long) {
        remove(ProgramReminder.idFor(sourceKey, startTimeMillis))
    }

    private fun save(reminders: List<ProgramReminder>) {
        preferences.edit()
            .putString(KEY_ITEMS, ProgramReminder.toJson(reminders))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "program-reminders"
        const val KEY_ITEMS = "items"
    }
}
