package com.tvapp.livetv.settings

import android.content.Context

/**
 * DEBUGLOG-001: ayiklama gunlugunun acma/kapama tercihi.
 *
 * Ayiklama gunlugü (`.log/TVApp-debug-*.log`) uygulamadaki her onemli olay icin asenkron bir
 * satir yazdiriyor. Kullanici, bu satirlarin farkini gorebilmek icin gunlugü ayarlardan
 * kapatilabilir hale getirmeyi istedi.
 *
 * Varsayilani `false`tir: ayiklama satirlari artik yalnizca kullanici acikca isterse yazilir.
 * Bu tercih yalnizca olay/izleme satirlarini kapsar; istisna ve cokme raporlari tercih ne
 * olursa olsun kaydedilir (karar: [com.tvapp.livetv.diagnostics.DebugLogPolicy]).
 */
class DebugLogStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "debug-log"
        const val KEY_ENABLED = "log-enabled"
    }
}
