package com.tvapp.livetv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI-005: OSD punto alt siniri. Eski degerler (7sp tarih, 8sp kalite rozeti,
 * 9f renk ipuclari) 10-foot mesafede okunmuyordu; kural tek yerde tutulur.
 */
class OsdTextScaleTest {

    @Test
    fun valuesBelowFloorAreRaisedToFloor() {
        assertEquals(OsdTextScale.MIN_TEXT_SP, OsdTextScale.textSp(7f), 0f)
        assertEquals(OsdTextScale.MIN_TEXT_SP, OsdTextScale.textSp(9f), 0f)
        assertEquals(OsdTextScale.MIN_TEXT_SP, OsdTextScale.textSp(11.5f), 0f)
    }

    @Test
    fun valuesAtOrAboveFloorAreUntouched() {
        assertEquals(12f, OsdTextScale.textSp(12f), 0f)
        assertEquals(13f, OsdTextScale.textSp(13f), 0f)
        assertEquals(21f, OsdTextScale.textSp(21f), 0f)
    }

    @Test
    fun publishedOsdSizesRespectFloor() {
        assertTrue(OsdTextScale.HINT_TEXT_SP >= OsdTextScale.MIN_TEXT_SP)
        assertTrue(OsdTextScale.BADGE_TEXT_SP >= OsdTextScale.MIN_TEXT_SP)
        // Ipucu ve rozet puntolari alt sinirin ustune cikmaz: infobar teknik
        // satiri ve kanal satiri yuksekligi sabit kalmali.
        assertEquals(OsdTextScale.MIN_TEXT_SP, OsdTextScale.HINT_TEXT_SP, 0f)
        assertEquals(OsdTextScale.MIN_TEXT_SP, OsdTextScale.BADGE_TEXT_SP, 0f)
    }

    @Test
    fun floorIsTwelveSpMatchingLayoutValues() {
        // item_channel.xml / item_editor_channel.xml rozetleri ve
        // activity_main.xml clock_date autoSize alt siniri bu degerle ayni.
        assertEquals(12f, OsdTextScale.MIN_TEXT_SP, 0f)
    }
}
