package com.tvapp.livetv.settings

import android.content.Context

/**
 * PERF-028 eki: kanal editörünün önizleme tercihi.
 *
 * Kullanıcı raporunda editörde ok tuşlarıyla gezinme "kanal önizlemesini bekliyor" kadar
 * ağırlaşıyordu. Önizleme tuner veya ağ bedeli ödeten tek bileşen olduğu için, kapatmanın
 * ayarlar ekranında dolaşmadan doğrudan editörün sol çubuğundan yapılabilmesi istendi.
 * Tercih kalıcıdır: TV'de bir kez kapatıldıktan sonra sonraki açılışlarda da kapalı kalır.
 *
 * Varsayılan `true`dur; yani bu kayıt hiçbir davranışı tek başına değiştirmez.
 */
class EditorPreviewStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "editor-preview"
        const val KEY_ENABLED = "preview-enabled"
    }
}
