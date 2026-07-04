package com.hive.hiragana.presentation.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.scoring.CellRect
import com.hive.hiragana.drawing.canvas.CanvasState
import com.hive.hiragana.drawing.canvas.drawGhostCharacter
import com.hive.hiragana.drawing.canvas.drawPracticeGrid
import com.hive.hiragana.drawing.canvas.ghostFitScale
import com.hive.hiragana.drawing.stroke.StrokeCapture
import com.hive.hiragana.presentation.theme.GhostGrey
import com.hive.hiragana.presentation.theme.GridBorder
import com.hive.hiragana.presentation.theme.GridGuide
import com.hive.hiragana.presentation.theme.InkBlack
import com.hive.hiragana.presentation.theme.PaperWhite

/** Gap between adjacent word cells within one copy of the glyph row. */
private val CellGap = 10.dp

/** Gap between repetition slots — wider than [CellGap] so each copy of a word reads as a group. */
private val SlotGap = 18.dp

/**
 * Full brush width at neutral pressure/speed in the reference cell — the
 * square a single character gets on the 2×2 sheet.
 */
private val BrushBaseWidth = 20.dp

/**
 * Ink stays proportional to the cell it is traced in: below the reference
 * cell the brush thins linearly, so smaller cells — denser layouts AND the
 * shrunken per-kana cells of a word row — get proportionally finer ink
 * automatically. Growth above the reference is halved: the single 1×1
 * cell is 2× the reference but the brush only grows ~1.5×, because a pen
 * shouldn't double just because the paper did.
 */
private fun brushScale(cellSide: Float, referenceSide: Float): Float {
    val ratio = cellSide / referenceSide
    return if (ratio < 1f) ratio else 1f + (ratio - 1f) / 2f
}

/**
 * The main drawing surface. Renders, in order:
 *   1. one 2x2 practice grid per glyph (a single square for a lone
 *      character; a horizontal row of smaller cells for a whole word),
 *      repeated [PracticeLayout.span]² times — every slot holds one full
 *      copy of the glyph row, arranged into however many slot columns
 *      give the biggest cells (single characters keep the classic
 *      span×span square; wide word slots stack as fewer, fuller rows
 *      down the sheet), so the learner can trace the same character/word
 *      several times on one sheet
 *   2. the light-grey ghost glyph centred in each cell
 *   3. the user's completed strokes
 *   4. the stroke currently being drawn
 *
 * Ink is one shared sheet: strokes may land anywhere, including across
 * cell boundaries — the cells are guides, not input regions.
 *
 * Input arrives as raw [android.view.MotionEvent]s via pointerInteropFilter
 * so stylus pressure and batched historical samples are captured.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TracingCanvas(
    canvasState: CanvasState,
    characters: List<String>,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    showGhost: Boolean = true,
    layout: PracticeLayout = PracticeLayout.SINGLE,
    inkColor: Color = InkBlack,
) {
    val strokeCapture = remember(canvasState) { StrokeCapture(canvasState) }
    val ghostPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = GhostGrey.toArgb()
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = PaperWhite,
        shadowElevation = 2.dp,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInteropFilter { event -> strokeCapture.onMotionEvent(event) },
        ) {
            val repetitions = layout.span * layout.span
            val cellCount = characters.size.coerceAtLeast(1)
            val margin = 16.dp.toPx()
            val cellGap = if (cellCount > 1) CellGap.toPx() else 0f
            val slotGap = SlotGap.toPx()
            val availWidth = size.width - margin
            val availHeight = size.height - margin

            // A slot is one full copy of the glyph row. The picker fixes how
            // many copies (span²) but NOT their arrangement: forcing span
            // columns crams a word's wide slots into a thin band of tiny
            // cells centred in leftover vertical space. Instead, try every
            // column count and keep the one whose square cell comes out
            // biggest — single characters keep the classic span×span square,
            // words stack as fewer, fuller rows that use the sheet's height.
            var cols = 1
            var side = 0f
            for (candidate in 1..repetitions) {
                val candidateRows = (repetitions + candidate - 1) / candidate
                val widthSide =
                    ((availWidth - slotGap * (candidate - 1)) / candidate -
                        cellGap * (cellCount - 1)) / cellCount
                val heightSide = (availHeight - slotGap * (candidateRows - 1)) / candidateRows
                val candidateSide = minOf(widthSide, heightSide)
                if (candidateSide > side) {
                    side = candidateSide
                    cols = candidate
                }
            }
            val rows = (repetitions + cols - 1) / cols

            val slotContentWidth = side * cellCount + cellGap * (cellCount - 1)
            val gridWidth = slotContentWidth * cols + slotGap * (cols - 1)
            val gridHeight = side * rows + slotGap * (rows - 1)
            val left = (size.width - gridWidth) / 2f
            val top = (size.height - gridHeight) / 2f

            // 1 + 2. one practice grid + ghost glyph per cell, repeated per
            // slot; when the copies don't divide evenly the last row stays
            // left-aligned, worksheet-style. The grid and ghost toggles each
            // skip their own drawing only — cell layout is unchanged either
            // way, so ink and glyph positions stay identical.
            for (copy in 0 until repetitions) {
                val slotLeft = left + (copy % cols) * (slotContentWidth + slotGap)
                val slotTop = top + (copy / cols) * (side + slotGap)
                for (index in 0 until cellCount) {
                    val cell = Rect(
                        Offset(slotLeft + index * (side + cellGap), slotTop),
                        Size(side, side),
                    )
                    if (showGrid) {
                        drawPracticeGrid(cell, borderColor = GridBorder, guideColor = GridGuide)
                    }
                    if (showGhost) {
                        characters.getOrNull(index)?.let { glyph ->
                            drawGhostCharacter(glyph, cell, ghostPaint)
                        }
                    }
                }
            }

            // Publish the single-character cell for FIT scoring. Only a lone
            // character in a 1×1 layout has a well-defined guide cell; word
            // sheets and multi-copy layouts leave it null (fit is skipped).
            canvasState.lastCell = if (repetitions == 1 && cellCount == 1) {
                CellRect(left = left, top = top, size = side)
            } else {
                null
            }

            // Brush geometry is built in pixels at input time, so the ink
            // width must be published before the next stroke starts — every
            // draw pass refreshes it (cheap plain-field write). Anything
            // that changes the cell size (advance, layout switch) also
            // clears the sheet via canvasClearKey, so committed ink never
            // outlives the scale it was built with.
            //
            // The brush tracks the *effective glyph size*, not the raw cell:
            // a composite glyph like "ファ" is drawn shrunk to fit the cell
            // width (ghostFitScale), so its strokes are ~0.6× a lone kana's —
            // scaling the brush by the smallest such fit keeps composites from
            // getting an over-thick stroke, the same width-fit the ghost uses.
            val referenceSide =
                (minOf(availWidth, availHeight) - slotGap).coerceAtLeast(1f) / 2f
            val glyphFit = characters.filter { it.isNotEmpty() }
                .minOfOrNull { ghostFitScale(ghostPaint, it, side) } ?: 1f
            canvasState.brushBaseWidthPx =
                BrushBaseWidth.toPx() * brushScale(side * glyphFit, referenceSide)

            // 3. completed strokes — filled brush ribbons, width baked in
            canvasState.completedPaths.forEach { path ->
                drawPath(path = path, color = inkColor)
            }

            // 4. active stroke
            canvasState.activePath?.let { path ->
                drawPath(path = path, color = inkColor)
            }
        }
    }
}
