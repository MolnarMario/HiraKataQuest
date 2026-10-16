package com.hive.hiragana.drawing.canvas

import androidx.compose.ui.geometry.Rect
import com.hive.hiragana.drawing.stroke.StrokePoint

/**
 * Pure geometry for carrying ink across a sheet relayout (see
 * [CanvasState.fitToSheet]). Ink is stored in canvas pixels, so when the
 * writing cells move or resize (a font-size change, a fold, a window resize)
 * every stroke is mapped from its old cell to the cell with the same index.
 * Cells are square, so the map is a uniform scale plus a shift and a stroke
 * keeps its shape.
 */

/** The cell a stroke starting at ([x], [y]) belongs to: the one containing
 *  the point, else the nearest by edge distance (strokes may start up to the
 *  canvas's touch slop outside a cell). -1 only when [cells] is empty. */
internal fun nearestCellIndex(x: Float, y: Float, cells: List<Rect>): Int {
    var best = -1
    var bestDistance = Float.MAX_VALUE
    cells.forEachIndexed { index, cell ->
        val dx = maxOf(cell.left - x, 0f, x - cell.right)
        val dy = maxOf(cell.top - y, 0f, y - cell.bottom)
        val distance = dx * dx + dy * dy
        if (distance < bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best
}

/** [point] moved from [from] to [to], keeping its position relative to the
 *  cell's top-left corner in units of the cell's width. */
internal fun remapPoint(point: StrokePoint, from: Rect, to: Rect): StrokePoint {
    val scale = if (from.width > 0f) to.width / from.width else 1f
    return point.copy(
        x = to.left + (point.x - from.left) * scale,
        y = to.top + (point.y - from.top) * scale,
    )
}
