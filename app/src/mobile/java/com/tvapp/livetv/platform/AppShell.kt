package com.tvapp.livetv.platform

import android.app.Activity
import com.tvapp.livetv.MainActivity
import com.tvapp.livetv.MobileMainActivity

/**
 * UI-014: Kabuk yönlendirme hedefleri (mobil/tablet yapılandırması).
 *
 * Aynı FQN (`com.tvapp.livetv.platform.AppShell`) her flavor source-set'inde ayrı yazılır
 * (`src/local`, `src/paid`, `src/mobile`); aynı anda yalnız biri derlenir, bu yüzden hedef
 * sınıf derleme zamanında doğrulanır.
 *
 * Fark burada başlar: mobil flavor'un kendi canlı kabuğu `MobileMainActivity`dir (launcher
 * girişi, dokunma jestleri, side drawer). Yönlendirme öncesi tüm hedefler TV kabuğuydu, bu
 * yüzden "Canlı TV'ye Dön" TV arayüzünü açıyordu. VOD oynatma bilinçli olarak TV kabuğunda
 * kalır (`UI-017` dokunma profilini ekleyecek, `UI-018` mobil-native yüzeyi tartışacak).
 */
object AppShell {

    /** Canlı TV kabuğu: mobil liste + PlayerView. */
    val liveHost: Class<out Activity> = MobileMainActivity::class.java

    /** VOD oynatma yüzeyi: şimdilik TV kabuğu (kullanıcı kararı 2026-11-17). */
    val vodPlaybackHost: Class<out Activity> = MainActivity::class.java
}
