package com.tvapp.livetv.platform

import com.tvapp.livetv.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * UI-014: kabuk hedeflerinin flavor bazında bağlı olduğu.
 *
 * `AppShell` her flavor source-set'inde ayrı yazılır; bu test o dosyanın gerçekten doğru
 * kabuğu gösterdiğini kilitler. TV yapılandırmalarında (`local`, `paid`) hedef
 * `MainActivity`dir, yani yönlendirme değişikliği TV davranışını taşımaz.
 * `MOBILE_UI_ENABLED` yalnız `mobile` flavor'ında true olduğundan beklenti varyantla birlikte
 * değişir ve üç flavor'ın birim testinde de doğru çalışır.
 */
class AppShellTest {

    @Test
    fun liveHostMatchesFlavorShell() {
        val expected = if (BuildConfig.MOBILE_UI_ENABLED) "MobileMainActivity" else "MainActivity"
        assertEquals(expected, AppShell.liveHost.simpleName)
    }

    @Test
    fun vodPlaybackStaysOnTvShellInEveryFlavor() {
        // Kullanıcı kararı (2026-11-17): VOD oynatma UI-017'ye kadar TV kabuğunda kalır.
        assertEquals("MainActivity", AppShell.vodPlaybackHost.simpleName)
    }
}
