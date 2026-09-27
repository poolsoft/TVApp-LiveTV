package com.tvapp.livetv.remote

import android.content.Context

/**
 * REMOTEEDIT-001: user-controlled state of the embedded management server.
 * Off by default; when enabled the server binds to the Wi-Fi interface only.
 */
class RemoteEditPreferencesStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun port(): Int = preferences.getInt(KEY_PORT, DEFAULT_PORT)

    fun setPort(port: Int) {
        require(port in 1024..65535)
        preferences.edit().putInt(KEY_PORT, port).apply()
    }

    companion object {
        const val DEFAULT_PORT = 8890
        private const val PREFS = "remote_edit"
        private const val KEY_ENABLED = "server_enabled"
        private const val KEY_PORT = "server_port"
    }
}
