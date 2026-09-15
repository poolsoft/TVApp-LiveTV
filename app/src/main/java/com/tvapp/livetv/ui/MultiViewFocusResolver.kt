package com.tvapp.livetv.ui

import android.view.KeyEvent

/**
 * Pure focus navigation for the Multi-View grid.
 *
 * The resolver mirrors the real cell arrangements produced by
 * `applyIptvGridLayout`:
 *  - 2 cells: `[0 | 1]` (single row)
 *  - 3 cells: `[0 | 1] / [0 | 2]` where cell 0 is the tall left cell spanning
 *    both rows of column 0
 *  - 4 cells: `[0 | 1] / [2 | 3]`
 *
 * Movement is geometric on that layout. Horizontal moves target the cell in
 * the neighboring column of the same row and never wrap; empty slots are not
 * focusable. Vertical moves stay in the column and wrap between the first and
 * last row of that column, which also removes the out-of-range index the
 * previous hand-written table could produce on the 4-cell grid.
 */
object MultiViewFocusResolver {

    private const val GRID_COLUMNS = 2
    private const val GRID_ROWS = 2

    /**
     * Returns the next focused cell index for [keyCode], or the (coerced)
     * current index when the key cannot move focus. The result is always a
     * valid cell index for a grid holding [cellCount] cells.
     */
    fun nextFocus(keyCode: Int, currentIndex: Int, cellCount: Int): Int {
        if (cellCount <= 0) return 0
        val current = currentIndex.coerceIn(0, cellCount - 1)
        val column = columnOf(current, cellCount)
        val row = rowOf(current, cellCount)
        val targetColumn: Int
        val targetRow: Int
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                targetColumn = column - 1
                targetRow = row
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                targetColumn = column + 1
                targetRow = row
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                targetColumn = column
                targetRow = row - 1
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                targetColumn = column
                targetRow = row + 1
            }
            else -> return current
        }
        val target = cellIndexAt(targetColumn, targetRow, cellCount)
        return (target?.takeIf { it != current } ?: current).coerceIn(0, cellCount - 1)
    }

    /**
     * Grid position of a cell in the real layout. The 3-cell arrangement is
     * mapped explicitly: the tall left cell (index 0) anchors column 0, while
     * cells 1 and 2 occupy column 1 of the top and bottom rows. Larger grids
     * are row-major.
     */
    private fun positionOf(index: Int, cellCount: Int): Pair<Int, Int> = when {
        cellCount == 3 && index == 0 -> 0 to 0
        cellCount == 3 -> 1 to (index - 1)
        else -> (index % GRID_COLUMNS) to (index / GRID_COLUMNS)
    }

    private fun columnOf(index: Int, cellCount: Int): Int = positionOf(index, cellCount).first

    private fun rowOf(index: Int, cellCount: Int): Int = positionOf(index, cellCount).second

    /**
     * Cell occupying ([column], [row]) for a grid of [cellCount] cells, or
     * null when the slot is empty. The 3-cell layout anchors the tall left
     * cell to column 0 of both rows and keeps cells 1 and 2 in column 1.
     */
    private fun cellIndexAt(column: Int, row: Int, cellCount: Int): Int? {
        if (column !in 0 until GRID_COLUMNS) return null
        val wrappedRow = Math.floorMod(row, GRID_ROWS)
        if (wrappedRow !in 0 until GRID_ROWS) return null
        val index = when {
            cellCount == 3 && column == 0 -> 0
            cellCount == 3 -> wrappedRow + 1
            else -> wrappedRow * GRID_COLUMNS + column
        }
        return index.takeIf { it < cellCount }
    }
}
