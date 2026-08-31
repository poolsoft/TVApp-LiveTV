package com.tvapp.livetv.settings

import android.content.Context

enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    TURKISH("tr"),
    ENGLISH("en"),
}

class AppLanguageStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): AppLanguage = runCatching {
        AppLanguage.valueOf(
            preferences.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name).orEmpty(),
        )
    }.getOrDefault(AppLanguage.SYSTEM)

    fun save(language: AppLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app-language"
        const val KEY_LANGUAGE = "language"
    }
}
