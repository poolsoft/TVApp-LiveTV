package com.tvapp.livetv.settings

import android.content.Context

/** XMLTV gece yenileme ayarları: kapalıysa JobScheduler job'u iptal edilir,
 *  açıksa [startHour] yerel saatinde pencere planlanır. Hem TV hem mobil
 *  paketlerinde aynı preference dosyası kullanılır. */
data class NightlyRefreshPreferences(
    val enabled: Boolean = true,
    val startHour: Int = 4,
)

class NightlyRefreshPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load() = NightlyRefreshPreferences(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        startHour = preferences.getInt(KEY_START_HOUR, 4).coerceIn(0, 23),
    )

    fun save(value: NightlyRefreshPreferences) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putInt(KEY_START_HOUR, value.startHour.coerceIn(0, 23))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "nightly-refresh"
        const val KEY_ENABLED = "enabled"
        const val KEY_START_HOUR = "start-hour"
    }
}
