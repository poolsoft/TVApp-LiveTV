package com.tvapp.livetv.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `UI-006`: VOD ana ekranı telefonda (landscape, kısa gövde) listeyi eziyordu.
 * Kırpma hesabı saf kuralla verildiği burada doğrulanır; TV ölçülerinin
 * değişmediği de aynı testle kilitlenir.
 */
class VodLayoutRulesTest {

    private val shortBodyRange = 200..(VodLayoutRules.TV_BODY_HEIGHT_DP - 1)

    @Test
    fun tvKeepsLayoutConstantsUntouched() {
        val standard = VodLayoutRules.standard()
        (VodLayoutRules.TV_BODY_HEIGHT_DP..900 step 37).forEach { body ->
            assertEquals("govde=$body", standard, VodLayoutRules.geometry(body))
        }
        // Olcu hic yoksa (ilk layout oncesi) duzen sabitlerine donulur: gizli degisiklik yok.
        assertEquals(standard, VodLayoutRules.geometry(0))
        assertEquals(VodLayoutRules.TV_RAIL_HEIGHT_DP, standard.railHeightDp)
        assertEquals(VodLayoutRules.TV_POSTER_HEIGHT_DP, standard.gridPosterHeightDp)
        assertTrue(standard.railLabelVisible)
        assertTrue(standard.metaRowVisible)
    }

    @Test
    fun phoneLandscapeLeavesGridOneFullRow() {
        // Ornek: telefon yatay ~640x360dp -> govde ~279dp (ust/alt bar ve dolgular dusulunce).
        val plan = VodLayoutRules.geometry(279)
        val gridViewport = 279 - plan.railHeightDp - VodLayoutRules.GRID_HEADER_BLOCK_DP
        val gridRowHeight = plan.gridPosterHeightDp + VodLayoutRules.CARD_CHROME_DP

        assertTrue("izgara gorunur alani one tam satir vermez: $gridViewport", gridViewport >= gridRowHeight)
        assertTrue("ray sabit 175dp olarak kalmis: ${plan.railHeightDp}", plan.railHeightDp < VodLayoutRules.TV_RAIL_HEIGHT_DP)
        assertTrue("telefon basliginda baslik satiri gorunmez kalmali", !plan.railLabelVisible)
    }

    @Test
    fun railNeverCropsItsOwnCards() {
        shortBodyRange.forEach { body ->
            val plan = VodLayoutRules.geometry(body)
            val railContent = plan.railPosterHeightDp +
                VodLayoutRules.CARD_CHROME_DP +
                VodLayoutRules.RAIL_CONTENT_PADDING_DP
            assertTrue("govde=$body ray icerigi tasiyor: $railContent > ${plan.railHeightDp}", railContent <= plan.railHeightDp)
        }
    }

    @Test
    fun gridCardIsNotCroppedExceptBelowPosterFloor() {
        shortBodyRange.forEach { body ->
            val plan = VodLayoutRules.geometry(body)
            val gridViewport = body - plan.railHeightDp - VodLayoutRules.GRID_HEADER_BLOCK_DP
            val gridRowHeight = plan.gridPosterHeightDp + VodLayoutRules.CARD_CHROME_DP
            assertTrue(
                "govde=$body izgara satiri $gridRowHeight > alan $gridViewport",
                gridRowHeight <= gridViewport || plan.gridPosterHeightDp == VodLayoutRules.MIN_GRID_POSTER_HEIGHT_DP,
            )
        }
    }

    @Test
    fun gridAlwaysKeepsMinimumViewportAndPoster() {
        shortBodyRange.forEach { body ->
            val plan = VodLayoutRules.geometry(body)
            val gridViewport = body - plan.railHeightDp - VodLayoutRules.GRID_HEADER_BLOCK_DP
            val budget = body - VodLayoutRules.GRID_HEADER_BLOCK_DP - VodLayoutRules.MIN_GRID_VIEWPORT_DP
            if (budget >= VodLayoutRules.MIN_RAIL_HEIGHT_DP) {
                assertTrue("govde=$body izgara alani $gridViewport kuculttu", gridViewport >= VodLayoutRules.MIN_GRID_VIEWPORT_DP)
            } else {
                // Alan yetmiyor: ray tabanina iner, kirpilan pay listeye kalir (liste kazanir).
                assertEquals("govde=$body ray tabanda olmali", VodLayoutRules.MIN_RAIL_HEIGHT_DP, plan.railHeightDp)
            }
            assertTrue(
                "govde=$body afis ${plan.gridPosterHeightDp} cok kucuk",
                plan.gridPosterHeightDp >= VodLayoutRules.MIN_GRID_POSTER_HEIGHT_DP,
            )
            assertTrue(
                "govde=$body ray yuksekligi mantiksiz",
                plan.railHeightDp in VodLayoutRules.MIN_RAIL_HEIGHT_DP..VodLayoutRules.TV_RAIL_HEIGHT_DP,
            )
        }
    }

    @Test
    fun tallerScreenNeverGivesGridSmallerPoster() {
        var previous = 0
        (160..900).forEach { body ->
            val plan = VodLayoutRules.geometry(body)
            assertTrue("govde=$body afis kuculdu", plan.gridPosterHeightDp >= previous)
            previous = plan.gridPosterHeightDp
        }
    }

    @Test
    fun gridSpanKeepsFiveColumnsOnTvWidths() {
        // TV: 1080p tvdpi'de izgara alani ~739dp, 720p'de ~1059dp -> bes sutun korunur.
        listOf(739, 1_059, 1_219, 1_600).forEach { widthDp ->
            assertEquals(widthDp.toString(), 5, VodLayoutRules.gridSpan(widthDp, 110))
        }
    }

    @Test
    fun gridSpanStopsCrushingCardsOnPhoneWidths() {
        // Telefon yatay 640dp: izgara alani ~419dp -> uc, 411dp telefon portre ~190dp -> bir sutun.
        assertEquals(3, VodLayoutRules.gridSpan(419, 110))
        assertEquals(1, VodLayoutRules.gridSpan(190, 110))
        assertEquals(1, VodLayoutRules.gridSpan(109, 110))
        assertEquals(1, VodLayoutRules.gridSpan(0, 110))
        assertEquals(1, VodLayoutRules.gridSpan(500, 0))
    }
}
