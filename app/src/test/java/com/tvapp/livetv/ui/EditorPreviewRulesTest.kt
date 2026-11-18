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
 * Önizleme (a) tam ekran oynatma kalitesinde açılmamalı, (b) odağın gittiği satır için
 * gezinme sırasında tuner/oynatıcı bedeli ödetmemeli, (c) aynı kanalda yeniden tune üretmemeli.
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
                previewEnabled = true,
                activeSourceKey = "tif:1",
                nextSourceKey = "iptv:9",
            ),
        )
        assertTrue(
            EditorPreviewRules.schedulesPlayback(
                moveMode = false,
                locked = false,
                previewEnabled = true,
                activeSourceKey = null,
                nextSourceKey = "iptv:9",
            ),
        )
    }

    @Test
    fun alreadyPlayingChannelIsNotRetuned() {
        assertFalse(
            EditorPreviewRules.schedulesPlayback(
                moveMode = false,
                locked = false,
                previewEnabled = true,
                activeSourceKey = "iptv:9",
                nextSourceKey = "iptv:9",
            ),
        )
    }

    /**
     * Planlanan kanal ile çalan kanal ayrımı: kullanıcı erteleme süresi içinde bir satır inip
     * geri dönerse önizleme hiç açılamamamıştı. Karar çalan kanala göre verildiği için plan
     * yeniden kurulabilir.
     */
    @Test
    fun returnToChannelThatNeverStartedIsScheduledAgain() {
        assertTrue(
            EditorPreviewRules.schedulesPlayback(
                moveMode = false,
                locked = false,
                previewEnabled = true,
                activeSourceKey = null,
                nextSourceKey = "tif:1",
            ),
        )
    }

    @Test
    fun moveModeNeverPreviews() {
        assertFalse(
            EditorPreviewRules.schedulesPlayback(
                moveMode = true,
                locked = false,
                previewEnabled = true,
                activeSourceKey = "iptv:9",
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
                previewEnabled = true,
                activeSourceKey = null,
                nextSourceKey = "iptv:10",
            ),
        )
    }

    @Test
    fun disabledPreviewNeverPreviews() {
        assertFalse(
            EditorPreviewRules.schedulesPlayback(
                moveMode = false,
                locked = false,
                previewEnabled = false,
                activeSourceKey = null,
                nextSourceKey = "iptv:10",
            ),
        )
    }

    /** Gezinmenin bedeli: çalan bir akış yoksa hiçbir yüzeye dokunulmamalı. */
    @Test
    fun browsingWithoutRunningPlaybackTouchesNothing() {
        assertEquals(
            PreviewMoveAction.NOTHING,
            EditorPreviewRules.moveAction(
                previewEnabled = true,
                locked = false,
                activeSurface = null,
                activeSourceKey = null,
                nextSourceKey = "tif:2",
            ),
        )
    }

    /**
     * TIF yüzeyinde uydu/DVB ayarlaması 1-2 sn sürer; her tuşta oturumu kapatıp açmak şikâyetin
     * asıl kaynağıydı. Yeni ayarlama oturumu devraldığı için yüzey bırakılır.
     */
    @Test
    fun tifSurfaceIsNotReleasedOnEveryFocusMove() {
        assertEquals(
            PreviewMoveAction.NOTHING,
            EditorPreviewRules.moveAction(
                previewEnabled = true,
                locked = false,
                activeSurface = PreviewSurface.TIF,
                activeSourceKey = "tif:1",
                nextSourceKey = "tif:2",
            ),
        )
    }

    /** IPTV akışı ucuzdur ve gezinme boyunca indirme/çözme/bekçi üretmeye devam etmemelidir. */
    @Test
    fun iptvStreamIsCutWhenFocusMoves() {
        assertEquals(
            PreviewMoveAction.STOP_IPTV,
            EditorPreviewRules.moveAction(
                previewEnabled = true,
                locked = false,
                activeSurface = PreviewSurface.IPTV,
                activeSourceKey = "iptv:1",
                nextSourceKey = "iptv:2",
            ),
        )
    }

    @Test
    fun sameRowKeepsItsPlayback() {
        assertEquals(
            PreviewMoveAction.NOTHING,
            EditorPreviewRules.moveAction(
                previewEnabled = true,
                locked = false,
                activeSurface = PreviewSurface.IPTV,
                activeSourceKey = "iptv:1",
                nextSourceKey = "iptv:1",
            ),
        )
    }

    /** Kilitli kanal ve kapalı önizleme kutuyu anında karartır. */
    @Test
    fun lockedOrDisabledPreviewBlanksTheBox() {
        assertEquals(
            PreviewMoveAction.RELEASE_ALL,
            EditorPreviewRules.moveAction(
                previewEnabled = true,
                locked = true,
                activeSurface = PreviewSurface.TIF,
                activeSourceKey = "tif:1",
                nextSourceKey = "tif:2",
            ),
        )
        assertEquals(
            PreviewMoveAction.RELEASE_ALL,
            EditorPreviewRules.moveAction(
                previewEnabled = false,
                locked = false,
                activeSurface = PreviewSurface.IPTV,
                activeSourceKey = "iptv:1",
                nextSourceKey = "iptv:2",
            ),
        )
    }

    /** Tuner yüzeyi yalnız IPTV yüzeyine geçerken serbest bırakılır; boşuna reset yok. */
    @Test
    fun tunerSurfaceReleasedOnlyWhenSwitchingToIptv() {
        assertTrue(
            EditorPreviewRules.releasesTunerSurface(PreviewSurface.IPTV, PreviewSurface.TIF),
        )
        assertFalse(
            EditorPreviewRules.releasesTunerSurface(PreviewSurface.IPTV, PreviewSurface.IPTV),
        )
        assertFalse(EditorPreviewRules.releasesTunerSurface(PreviewSurface.IPTV, null))
        assertFalse(EditorPreviewRules.releasesTunerSurface(PreviewSurface.TIF, PreviewSurface.IPTV))
    }
}
