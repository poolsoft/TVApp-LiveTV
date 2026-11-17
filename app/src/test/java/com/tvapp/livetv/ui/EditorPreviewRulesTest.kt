package com.tvapp.livetv.ui

import com.tvapp.livetv.playback.IptvPlaybackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF-028: kanal editöründe gezinmeyi ağırlaştıran önizleme kararları.
 *
 * Önizleme (a) tam ekran oynatma kalitesinde açılmamalı, (b) odak başka satıra gidince
 * arka planda akmaya devam etmemeli, (c) aynı kanalda yeniden tune üretmemeli.
 */
class EditorPreviewRulesTest {

    @Test
    fun previewIsNotFullScreenQuality() {
        assertNotEquals(IptvPlaybackProfile.PRIMARY, EditorPreviewRules.previewProfile)
        assertEquals(720, EditorPreviewRules.previewProfile.maximumHeight)
        assertTrue(EditorPreviewRules.previewProfile.maximumBitrate < Int.MAX_VALUE)
    }

    @Test
    fun normalModeFocusChangeSchedulesPreview() {
        assertTrue(
            EditorPreviewRules.schedulesPlayback(
                moveMode = false,
                locked = false,
                previousSourceKey = "tif:1",
                nextSourceKey = "iptv:9",
            ),
        )
        assertTrue(
            EditorPreviewRules.schedulesPlayback(
                moveMode = false,
                locked = false,
                previousSourceKey = null,
                nextSourceKey = "iptv:9",
            ),
        )
    }

    @Test
    fun sameChannelIsNotRetuned() {
        assertFalse(
            EditorPreviewRules.schedulesPlayback(
                moveMode = false,
                locked = false,
                previousSourceKey = "iptv:9",
                nextSourceKey = "iptv:9",
            ),
        )
    }

    @Test
    fun moveModeNeverPreviews() {
        assertFalse(
            EditorPreviewRules.schedulesPlayback(
                moveMode = true,
                locked = false,
                previousSourceKey = "iptv:9",
                nextSourceKey = "iptv:10",
            ),
        )
    }

    @Test
    fun lockedChannelNeverPreviews() {
        assertFalse(
            EditorPreviewRules.schedulesPlayback(
                moveMode = false,
                locked = true,
                previousSourceKey = null,
                nextSourceKey = "iptv:10",
            ),
        )
    }

    @Test
    fun focusMoveCutsRunningPlayback() {
        assertTrue(EditorPreviewRules.stopsRunningPlayback("iptv:9", "iptv:10"))
        // Odak araç düğmesinden aynı satıra döndüğünde oynatma kesilmez.
        assertFalse(EditorPreviewRules.stopsRunningPlayback("iptv:9", "iptv:9"))
        // Çalan bir şey yoksa kesme de yok.
        assertFalse(EditorPreviewRules.stopsRunningPlayback(null, "iptv:10"))
    }
}
