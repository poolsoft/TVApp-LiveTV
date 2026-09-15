package com.tvapp.livetv.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiViewFocusResolverTest {

    private val left = KeyEvent.KEYCODE_DPAD_LEFT
    private val right = KeyEvent.KEYCODE_DPAD_RIGHT
    private val up = KeyEvent.KEYCODE_DPAD_UP
    private val down = KeyEvent.KEYCODE_DPAD_DOWN

    @Test
    fun twoCellLayoutMovesOnlyBetweenThePair() {
        assertEquals(1, MultiViewFocusResolver.nextFocus(right, 0, 2))
        assertEquals(0, MultiViewFocusResolver.nextFocus(left, 1, 2))
        // Vertical moves cannot leave the single row.
        assertEquals(0, MultiViewFocusResolver.nextFocus(down, 0, 2))
        assertEquals(1, MultiViewFocusResolver.nextFocus(up, 1, 2))
    }

    @Test
    fun twoCellLayoutNeverLeavesValidRange() {
        (0..1).forEach { index ->
            listOf(left, right, up, down).forEach { key ->
                val next = MultiViewFocusResolver.nextFocus(key, index, 2)
                assertTrue(next in 0..1)
            }
        }
    }

    @Test
    fun threeCellLayoutTallLeftCellAnswersBothRows() {
        // Layout: [0 | 1] / [0 | 2]
        assertEquals(1, MultiViewFocusResolver.nextFocus(right, 0, 3))
        assertEquals(0, MultiViewFocusResolver.nextFocus(left, 1, 3))
        assertEquals(0, MultiViewFocusResolver.nextFocus(left, 2, 3))
        // Right column moves down/up between cells 1 and 2.
        assertEquals(2, MultiViewFocusResolver.nextFocus(down, 1, 3))
        assertEquals(1, MultiViewFocusResolver.nextFocus(up, 2, 3))
    }

    @Test
    fun threeCellLayoutNeverLeavesValidRange() {
        (0..2).forEach { index ->
            listOf(left, right, up, down).forEach { key ->
                val next = MultiViewFocusResolver.nextFocus(key, index, 3)
                assertTrue(next in 0..2)
            }
        }
    }

    @Test
    fun fourCellLayoutMovesRowMajor() {
        assertEquals(1, MultiViewFocusResolver.nextFocus(right, 0, 4))
        assertEquals(0, MultiViewFocusResolver.nextFocus(left, 1, 4))
        assertEquals(3, MultiViewFocusResolver.nextFocus(right, 2, 4))
        assertEquals(2, MultiViewFocusResolver.nextFocus(left, 3, 4))
        assertEquals(2, MultiViewFocusResolver.nextFocus(down, 0, 4))
        assertEquals(3, MultiViewFocusResolver.nextFocus(down, 1, 4))
        assertEquals(0, MultiViewFocusResolver.nextFocus(up, 2, 4))
        assertEquals(1, MultiViewFocusResolver.nextFocus(up, 3, 4))
    }

    @Test
    fun fourCellLayoutWrapsVerticallyWithinItsColumn() {
        // Old table produced index -2 here; wrap lands on the bottom of the column.
        assertEquals(2, MultiViewFocusResolver.nextFocus(up, 0, 4))
        assertEquals(3, MultiViewFocusResolver.nextFocus(up, 1, 4))
        assertEquals(0, MultiViewFocusResolver.nextFocus(down, 2, 4))
        assertEquals(1, MultiViewFocusResolver.nextFocus(down, 3, 4))
    }

    @Test
    fun fourCellLayoutNeverLeavesValidRange() {
        (0..3).forEach { index ->
            listOf(left, right, up, down).forEach { key ->
                val next = MultiViewFocusResolver.nextFocus(key, index, 4)
                assertTrue(next in 0..3)
            }
        }
    }

    @Test
    fun singleCellNeverMoves() {
        listOf(left, right, up, down).forEach { key ->
            assertEquals(0, MultiViewFocusResolver.nextFocus(key, 0, 1))
        }
    }

    @Test
    fun horizontalMovesNeverWrapAcrossRows() {
        // Left edge cannot wrap to the previous row's right cell.
        assertEquals(2, MultiViewFocusResolver.nextFocus(left, 2, 4))
        assertEquals(3, MultiViewFocusResolver.nextFocus(right, 3, 4))
    }

    @Test
    fun unknownKeyKeepsCurrentFocus() {
        assertEquals(2, MultiViewFocusResolver.nextFocus(KeyEvent.KEYCODE_DPAD_CENTER, 2, 4))
    }

    @Test
    fun outOfRangeCurrentIndexIsClampedBeforeMoving() {
        // 99 clamps to cell 3 (bottom-right); down wraps within the right column.
        assertEquals(1, MultiViewFocusResolver.nextFocus(down, 99, 4))
        // -5 clamps to cell 0 (top-left); up wraps within the left column.
        assertEquals(2, MultiViewFocusResolver.nextFocus(up, -5, 4))
    }

    @Test
    fun emptyGridReturnsZero() {
        assertEquals(0, MultiViewFocusResolver.nextFocus(down, 0, 0))
    }
}
