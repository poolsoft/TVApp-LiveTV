package com.tvapp.livetv.settings

import android.content.Context

class SleepTimerStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sleep-timer",
        Context.MODE_PRIVATE,
    )

    fun endAtMillis(): Long = preferences.getLong(KEY_END_AT, 0L)

    fun remainingMinutes(now: Long = System.currentTimeMillis()): Int {
        val remaining = endAtMillis() - now
        return if (remaining <= 0L) 0 else ((remaining + 59_999L) / 60_000L).toInt()
    }

    fun schedule(minutes: Int, now: Long = System.currentTimeMillis()) {
        val endAt = if (minutes <= 0) 0L else now + minutes * 60_000L
        preferences.edit().putLong(KEY_END_AT, endAt).apply()
    }

    fun clear() = schedule(0)

    private companion object {
        const val KEY_END_AT = "end-at"
    }
}
