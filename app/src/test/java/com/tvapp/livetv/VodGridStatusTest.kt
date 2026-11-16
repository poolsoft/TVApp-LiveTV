package com.tvapp.livetv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VOD ana ekranindaki durum katman karari (UI-002). Kabul olcutu buradan dogrulanir:
 * hata, yukleme ve bos katalog birbiriyle karismaz; icerik varken katman gorunmez.
 */
class VodGridStatusTest {

    @Test
    fun testEmptyGridWhileLoadingShowsSpinner() {
        assertEquals(
            VodGridStatus.Loading,
            decideVodGridStatus(itemsEmpty = true, isLoading = true, loadFailed = false)
        )
    }

    @Test
    fun testErrorIsNotConfusedWithEmptyCatalog() {
        assertEquals(
            VodGridStatus.EmptyCatalog,
            decideVodGridStatus(itemsEmpty = true, isLoading = false, loadFailed = false)
        )
        assertEquals(
            VodGridStatus.Error,
            decideVodGridStatus(itemsEmpty = true, isLoading = false, loadFailed = true)
        )
    }

    @Test
    fun testErrorWinsOverLoading() {
        // Yukleme bitmeden hata dusen istek: spinner degil yeniden dene karti gorunur.
        assertEquals(
            VodGridStatus.Error,
            decideVodGridStatus(itemsEmpty = true, isLoading = true, loadFailed = true)
        )
    }

    @Test
    fun testContentOnScreenNeverGetsOverlay() {
        listOf(false to false, false to true, true to false, true to true).forEach { (loading, failed) ->
            assertEquals(
                "itemsEmpty=false, isLoading=$loading, loadFailed=$failed",
                VodGridStatus.Hidden,
                decideVodGridStatus(itemsEmpty = false, isLoading = loading, loadFailed = failed)
            )
        }
    }
}
