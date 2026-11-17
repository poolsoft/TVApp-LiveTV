package com.tvapp.livetv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * UI-003: IPTV listeleri ve XMLTV kaynakları ekranlarının boş durum kararı.
 * Kart "yükleniyor" anında "kaynak yok" dememeli; alt ipucu satırı yalnız kart
 * görünürken "nasıl eklenir" yönlendirmesine geçmeli.
 */
class SourceListRulesTest {

    @Test
    fun loadingNeverClaimsEmptyList() {
        assertEquals(SourceListEmptyState.Hidden, SourceListRules.emptyState(loadFinished = false, itemCount = 0))
        assertEquals(SourceListEmptyState.Hidden, SourceListRules.emptyState(loadFinished = false, itemCount = 4))
    }

    @Test
    fun finishedLoadWithoutRowsShowsCard() {
        assertEquals(SourceListEmptyState.Empty, SourceListRules.emptyState(loadFinished = true, itemCount = 0))
    }

    @Test
    fun finishedLoadWithRowsKeepsListOnly() {
        assertEquals(SourceListEmptyState.Hidden, SourceListRules.emptyState(loadFinished = true, itemCount = 1))
        assertEquals(SourceListEmptyState.Hidden, SourceListRules.emptyState(loadFinished = true, itemCount = 12))
    }

    @Test
    fun deletingLastSourceShowsCardOnRefresh() {
        // Son kaynak silindikten sonraki yenileme: okuma tamamlanmış, satır kalmamış.
        val afterDelete = SourceListRules.emptyState(loadFinished = true, itemCount = 0)
        assertEquals(SourceListEmptyState.Empty, afterDelete)
        assertEquals(SourceListHint.AddSource, SourceListRules.hintFor(afterDelete, hasDpadInput = true))
    }

    @Test
    fun rowActionHintRemainsWhileListHasContent() {
        assertEquals(
            SourceListHint.RowActions,
            SourceListRules.hintFor(SourceListEmptyState.Hidden, hasDpadInput = true),
        )
        assertEquals(
            SourceListHint.AddSource,
            SourceListRules.hintFor(SourceListEmptyState.Empty, hasDpadInput = true),
        )
    }

    /** UI-016: D-pad olmayan profilde "OK / CH+/CH-" komutu yerine dokunma jesti anlatılır. */
    @Test
    fun touchProfileDescribesTapInsteadOfKeys() {
        assertEquals(
            SourceListHint.RowActionsTouch,
            SourceListRules.hintFor(SourceListEmptyState.Hidden, hasDpadInput = false),
        )
        // Boş liste yönlendirmesi girdi profiline bağlı değildir: ekleme her yerde panelle.
        assertEquals(
            SourceListHint.AddSource,
            SourceListRules.hintFor(SourceListEmptyState.Empty, hasDpadInput = false),
        )
    }
}
