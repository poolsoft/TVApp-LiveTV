package com.tvapp.livetv.diagnostics

import android.view.KeyEvent

/**
 * PERF-028 eki: kanal editöründe tuş izinin nereye yazılacağına karar veren saf kural.
 *
 * Editörde her tuş basımında "son editör olayı" tercihlerine yazılıyordu ve bu yazım
 * `SharedPreferences.commit()` ile **arayüz iş parçacığında senkron disk yazımı** yapıyordu:
 * bir uydu/DVB ayarlaması kadar olmasa da her ok tuşunda flash'a fsync ödemek gezinmeyi
 * belirgin biçimde geciktiriyordu ("aşağı yukarı giderken çok takılıyor").
 *
 * Kalıcı izlemeye gerçekten ihtiyaç duyulan olaylar çökme adli tıbbı için anlamlı olanlardır:
 * renk tuşları, GERİ, rakam ve menü tuşları. Ok tuşlarıyla gezinme ise her olayı zaten
 * asenkron hata günlüğüne yazdırır (CrashReportStore.recordDebug); oradaki tarihçeli satır
 * kaybolmaz, yalnız arayüz iş parçası bloklanmaz.
 */
object EditorKeyTrace {

    /** Liste gezinmesi üreten tuşlar; bunlar için tercih yazımı yapılmaz. */
    private val navigationKeyCodes = setOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_CHANNEL_DOWN,
    )

    fun isNavigationKey(keyCode: Int): Boolean = keyCode in navigationKeyCodes

    /** Bu tuş için "son editör olayı" tercihi güncellenmeli mi? */
    fun persistsToPreferences(keyCode: Int): Boolean = keyCode !in navigationKeyCodes
}
