package com.hive.hiragana.drawing.canvas

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/**
 * Draws the 2x2 Japanese practice grid: a solid outer square with a
 * dashed centre cross dividing it into four quadrants, like a
 * genkouyoushi practice sheet cell.
 */
fun DrawScope.drawPracticeGrid(cell: Rect, borderColor: Color, guideColor: Color) {
    drawRoundRect(
        color = borderColor,
        topLeft = cell.topLeft,
        size = cell.size,
        cornerRadius = CornerRadius(16f, 16f),
        style = Stroke(width = 2.dp.toPx()),
    )

    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
    val guideWidth = 1.5.dp.toPx()
    val inset = 6.dp.toPx()

    drawLine(
        color = guideColor,
        start = Offset(cell.left + inset, cell.center.y),
        end = Offset(cell.right - inset, cell.center.y),
        strokeWidth = guideWidth,
        pathEffect = dash,
    )
    drawLine(
        color = guideColor,
        start = Offset(cell.center.x, cell.top + inset),
        end = Offset(cell.center.x, cell.bottom - inset),
        strokeWidth = guideWidth,
        pathEffect = dash,
    )
}

/** Ghost glyph height as a fraction of the (square) cell side. */
private const val GHOST_HEIGHT_FRACTION = 0.78f

/** Max glyph width as a fraction of the cell side before it is shrunk to fit. */
private const val GHOST_MAX_WIDTH_FRACTION = 0.86f

/**
 * How much a glyph must shrink to fit the cell width at the ghost text size,
 * as a fraction of [cellSide] (1 = no shrink). A lone kana never trips this,
 * but a two-code-point glyph like "きゃ"/"ファ" is ~1.5–2× as wide and shrinks
 * to ~0.6. Multiplying [cellSide] by this gives the glyph's *effective* size —
 * used both to size the ghost and to scale the brush (so a composite doesn't
 * get an over-thick stroke; see TracingCanvas).
 *
 * [paint]'s textSize is left set to the fitted size as a side effect.
 */
fun ghostFitScale(paint: Paint, glyph: String, cellSide: Float): Float {
    if (glyph.isEmpty()) return 1f
    paint.textSize = cellSide * GHOST_HEIGHT_FRACTION
    val maxWidth = cellSide * GHOST_MAX_WIDTH_FRACTION
    val measuredWidth = paint.measureText(glyph)
    val fit = if (measuredWidth > maxWidth) maxWidth / measuredWidth else 1f
    if (fit < 1f) paint.textSize *= fit
    return fit
}

/**
 * Draws the character as a large light-grey glyph centred in the cell —
 * the "ghost" the learner traces over. Rendered with the system font via
 * the native canvas, which guarantees a typographically correct shape.
 *
 * Sized to 78% of the cell height, then shrunk if needed so the glyph also
 * fits the cell width — a single kana never trips this, but a two-code-point
 * yōon like "きゃ" is ~1.5× as wide and would otherwise overflow and clip.
 */
fun DrawScope.drawGhostCharacter(character: String, cell: Rect, paint: Paint) {
    if (character.isEmpty()) return
    ghostFitScale(paint, character, cell.height) // sets paint.textSize to the fitted size
    val metrics = paint.fontMetrics
    val baseline = cell.center.y - (metrics.ascent + metrics.descent) / 2f
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(character, cell.center.x, baseline, paint)
    }
}

/** Handwritten-ghost stroke width as a fraction of the KanjiVG viewBox. */
private const val GHOST_STROKE_WIDTH_FRACTION = 0.1f

/**
 * Draws the character's KanjiVG strokes as a light-grey round-capped brush,
 * scaled uniformly into the (square) [cell] — the HANDWRITTEN-form ghost, an
 * alternative to the font-rendered [drawGhostCharacter]. It uses the very same
 * geometry as the stroke-order demo and the scorer, so tracing it lines up with
 * both.
 *
 * [paths] are pre-parsed Compose paths in viewBox space (the caller caches them
 * so nothing is parsed per frame); [viewBox] is the KanjiVG box (109). No-op if
 * there are no paths — the caller then falls back to the font ghost.
 */
fun DrawScope.drawGhostStrokes(paths: List<Path>, cell: Rect, viewBox: Float, color: Color) {
    if (paths.isEmpty() || viewBox <= 0f) return
    val scale = cell.width / viewBox
    val style = Stroke(
        width = viewBox * GHOST_STROKE_WIDTH_FRACTION,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    withTransform({
        translate(cell.left, cell.top)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        paths.forEach { drawPath(path = it, color = color, style = style) }
    }
}
