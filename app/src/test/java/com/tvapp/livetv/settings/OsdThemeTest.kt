package com.tvapp.livetv.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class OsdThemeTest {
    @Test fun freshInstallKeepsCurrentAppearance() {
        assertEquals(OsdTheme.DEFAULT, DisplayPreferences().osdTheme)
        assertEquals(OsdTheme.DEFAULT, OsdTheme.fromName(null))
    }

    @Test fun unknownSavedThemeFallsBackToCurrentAppearance() {
        assertEquals(OsdTheme.DEFAULT, OsdTheme.fromName("unsupported"))
        assertEquals(OsdTheme.DEFAULT, OsdTheme.fromName(""))
    }

    @Test fun allThemesCanBeRestoredByStableName() {
        OsdTheme.entries.forEach { assertEquals(it, OsdTheme.fromName(it.name)) }
    }
}
