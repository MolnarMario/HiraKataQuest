package com.hive.hiragana.drawing.canvas

import com.hive.hiragana.domain.model.PracticeLayout
import kotlin.math.roundToInt

/**
 * One planned practice grid: [cols] × [rows] whole copies of the glyph row,
 * each cell [side] px square. Always a complete grid — no ragged last row.
 */
data class SheetGrid(val cols: Int, val rows: Int, val side: Float) {
    val copies: Int get() = cols * rows
}

/**
 * Plans the practice sheet's grid for a [PracticeLayout] — pure geometry, no
 * Compose/Android dependencies (same testability pattern as GeometryScorer).
 *
 * XL ([PracticeLayout.SINGLE]) is exactly one copy, sized to the sheet — the
 * only layout with well-defined scoring cells. The repetition sizes are
 * computed as a CHAIN L → M → S, each derived from the previous size's actual
 * result, so three invariants hold for every item shape (1-cell characters,
 * 2–6-cell words) and every canvas proportion — cell-size targets alone can't
 * guarantee them, because cells quantize to whole grids and two targets can
 * land on the same grid (device-found: single-char M and S with equal square
 * counts, 2-kana L and M both at 10 copies):
 *
 * 1. **Visibly smaller cells** — each size's target side is
 *    `min(availWidth / divisor, SIZE_STEP × previous side)`. The absolute
 *    anchors (width / 2 / 2.5 / 3) keep lone characters on the classic sizes;
 *    the chain cap guarantees a ≥25% drop for words, whose XL cells already
 *    span a fraction of the width.
 * 2. **Strictly more copies** — after packing, rows are added (shrinking the
 *    cells) until the count strictly exceeds the previous size's.
 * 3. **Never unusably small** — targets clamp to [cellFloor]; when the floor
 *    makes a strict increase impossible (very long words), the ladder
 *    saturates there instead of producing unwritable cells.
 *
 * A "copy" is the full unit being practiced: one cell for a character, the
 * whole row of [cellCount] kana cells for a word. [cellGap] separates cells
 * within a copy, [slotGap] separates copies.
 */
fun planSheetGrid(
    layout: PracticeLayout,
    cellCount: Int,
    availWidth: Float,
    availHeight: Float,
    cellGap: Float,
    slotGap: Float,
    cellFloor: Float,
): SheetGrid {
    val k = cellCount.coerceAtLeast(1)
    // Cell side when `cols` whole copies share the width / `rows` share the height.
    fun widthFit(cols: Int): Float =
        ((availWidth - slotGap * (cols - 1)) / cols - cellGap * (k - 1)) / k
    fun heightFit(rows: Int): Float = (availHeight - slotGap * (rows - 1)) / rows

    val xl = SheetGrid(cols = 1, rows = 1, side = minOf(widthFit(1), availHeight))
    if (layout == PracticeLayout.SINGLE) return xl

    var prev = xl
    for (size in listOf(PracticeLayout.LARGE, PracticeLayout.MEDIUM, PracticeLayout.SMALL)) {
        val target = minOf(availWidth / size.divisor, prev.side * SIZE_STEP)
            .coerceAtLeast(cellFloor)
        // Columns: whole copies across the width at the target size. Rounding
        // UP packs an extra column only while the squeezed cells stay within
        // ~10% of the target (the gaps otherwise cost an exact-fit column).
        val slotWidth = target * k + cellGap * (k - 1)
        val exactCols = (availWidth + slotGap) / (slotWidth + slotGap)
        val roundedCols = exactCols.roundToInt().coerceAtLeast(1)
        val cols = if (widthFit(roundedCols) >= target * SQUEEZE) {
            roundedCols
        } else {
            exactCols.toInt().coerceAtLeast(1)
        }
        val side0 = minOf(target, widthFit(cols), availHeight)
        // Rows: fill the height, with the same ~10% round-up tolerance.
        val exactRows = (availHeight + slotGap) / (side0 + slotGap)
        val roundedRows = exactRows.roundToInt().coerceAtLeast(1)
        var rows = if (heightFit(roundedRows) >= side0 * SQUEEZE) {
            roundedRows
        } else {
            exactRows.toInt().coerceAtLeast(1)
        }
        // A size step must buy strictly more copies; add rows (shrinking the
        // cells) until it does — unless that would sink below the floor, where
        // the ladder saturates.
        while (cols * rows <= prev.copies &&
            minOf(side0, heightFit(rows + 1)) >= cellFloor
        ) {
            rows++
        }
        val grid = SheetGrid(cols, rows, minOf(side0, heightFit(rows)))
        if (size == layout) return grid
        prev = grid
    }
    return prev // unreachable: every non-SINGLE layout is in the chain
}

/** Each size's cells are at most this fraction of the previous size's. */
private const val SIZE_STEP = 0.75f

/** How far below target a squeezed row/column may shrink the cells (~10%). */
private const val SQUEEZE = 0.9f
