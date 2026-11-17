package com.tvapp.livetv.platform

import android.app.Activity
import com.tvapp.livetv.MainActivity

/**
 * UI-014: Kabuk yönlendirme hedefleri (ücretli/TV yapılandırması).
 *
 * Aynı FQN (`com.tvapp.livetv.platform.AppShell`) her flavor source-set'inde ayrı yazılır
 * (`src/local`, `src/paid`, `src/mobile`); aynı anda yalnız biri derlenir. Böylece hedef
 * sınıf derleme zamanında doğrulanır, reflection veya sınıf adı yazısı gerekmez.
 *
 * Bu varyant TV'dir: canlı kabuk ve VOD oynatma yüzeyi aynı Activity'dir, yani davranış
 * yönlendirme öncesiyle birebir aynıdır.
 */
object AppShell {

    /** Canlı TV kabuğu: kanal listesi, oynatma, OSD. */
    val liveHost: Class<out Activity> = MainActivity::class.java

    /**
     * VOD oynatma yüzeyi. Üç varyantta da TV kabuğu (kullanıcı kararı 2026-11-17);
     * mobilde dokunma profili `UI-017`, mobil-native yüzey ise `UI-018` kapsamındadır.
     */
    val vodPlaybackHost: Class<out Activity> = MainActivity::class.java
}
