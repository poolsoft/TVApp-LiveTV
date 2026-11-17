package com.tvapp.livetv.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI-015: girdi UX profilinin saf kararı.
 *
 * Asıl kilit TV tarafıdır: flavor dokunmatik kabuk değilse donanım dokunmatik olsa ve
 * D-pad taşısa bile sonuç D_PAD'dir; böylece dokunma işleri TV arayüzünü taşıyamaz.
 */
class UiInputProfileTest {

    @Test
    fun tvFlavorAlwaysUsesDpadEvenOnTouchHardware() {
        assertEquals(UiInputProfile.D_PAD, UiInputRules.resolve(mobileUiEnabled = false, hasTouchScreen = false, hasDpadInput = true))
        assertEquals(UiInputProfile.D_PAD, UiInputRules.resolve(mobileUiEnabled = false, hasTouchScreen = true, hasDpadInput = false))
        assertEquals(UiInputProfile.D_PAD, UiInputRules.resolve(mobileUiEnabled = false, hasTouchScreen = true, hasDpadInput = true))
    }

    @Test
    fun mobileShellOnTouchlessDeviceFallsBackToDpad() {
        // Mobil APK yan yuklenen dokunmatik olmayan bir kutuda D-pad UX'ini alir.
        assertEquals(UiInputProfile.D_PAD, UiInputRules.resolve(mobileUiEnabled = true, hasTouchScreen = false, hasDpadInput = true))
        assertEquals(UiInputProfile.D_PAD, UiInputRules.resolve(mobileUiEnabled = true, hasTouchScreen = false, hasDpadInput = false))
    }

    @Test
    fun phoneWithoutDpadIsTouch() {
        assertEquals(UiInputProfile.TOUCH, UiInputRules.resolve(mobileUiEnabled = true, hasTouchScreen = true, hasDpadInput = false))
    }

    @Test
    fun tabletWithDpadRemoteIsHybrid() {
        assertEquals(UiInputProfile.HYBRID, UiInputRules.resolve(mobileUiEnabled = true, hasTouchScreen = true, hasDpadInput = true))
    }

    @Test
    fun dPadProfileKeepsRemoteAffordances() {
        assertFalse(UiInputProfile.D_PAD.usesTouchTargets)
        assertTrue(UiInputProfile.D_PAD.hasDpadInput)
        assertTrue(UiInputProfile.D_PAD.hasColorKeys)
    }

    @Test
    fun touchProfileDropsRemoteAffordances() {
        assertTrue(UiInputProfile.TOUCH.usesTouchTargets)
        assertFalse(UiInputProfile.TOUCH.hasDpadInput)
        assertFalse(UiInputProfile.TOUCH.hasColorKeys)
    }

    @Test
    fun hybridProfileKeepsBoth() {
        assertTrue(UiInputProfile.HYBRID.usesTouchTargets)
        assertTrue(UiInputProfile.HYBRID.hasDpadInput)
        // Renk tusu varsayilmaz: eylemler dokunulabilir kalmali, ipucu tek basina yetmez.
        assertFalse(UiInputProfile.HYBRID.hasColorKeys)
    }
}
