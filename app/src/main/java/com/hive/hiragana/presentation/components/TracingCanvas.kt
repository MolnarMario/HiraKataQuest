package com.hive.hiragana.presentation.components

import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.scoring.CellRect
import com.hive.hiragana.drawing.canvas.BrushEffect
import com.hive.hiragana.drawing.canvas.CanvasState
import com.hive.hiragana.drawing.canvas.drawGhostCharacter
import com.hive.hiragana.drawing.canvas.drawGhostStrokes
import com.hive.hiragana.drawing.canvas.drawPracticeGrid
import com.hive.hiragana.drawing.canvas.ghostFitScale
import com.hive.hiragana.drawing.canvas.planSheetGrid
import com.hive.hiragana.drawing.stroke.StrokeCapture
import com.hive.hiragana.presentation.theme.LocalKanaColors

/** KanjiVG viewBox the character stroke paths live in (all deck glyphs use 109). */
private const val KANJIVG_VIEWBOX = 109f

/** Gap between adjacent word cells within one copy of the glyph row. */
private val CellGap = 10.dp

/** Gap between repetition slots — wider than [CellGap] so each copy of a word reads as a group. */
private val SlotGap = 18.dp

/**
 * The main drawing surface. Renders, in order:
 *   1. one 2x2 practice grid per glyph (a single square for a lone
 *      character; a horizontal row of smaller cells for a whole word) —
 *      XL ([PracticeLayout.SINGLE]) draws exactly one copy; the L/M/S
 *      repetition sizes come from [planSheetGrid]'s chain, which guarantees
 *      each step down has visibly smaller cells AND strictly more copies,
 *      filling the sheet with complete rows — so the learner can trace the
 *      same character/word several times on one sheet with no dead corner
 *   2. the light-grey ghost glyph centred in each cell
 *   3. the user's completed strokes
 *   4. the stroke currently being drawn
 *
 * Each stroke is confined to the guide cell it starts in: a stroke that
 * begins outside every cell is rejected, and one that begins inside a cell
 * is clipped to that cell's edges, so ink can never cross into a neighbouring
 * character's box or into the paper margins.
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
    inkColor: Color = LocalKanaColors.current.defaultInk,
    // The artistic brush effect the user's ink is rendered with (uniform brush,
    // calligraphy, fountain pen, marker). Purely visual — baked into new strokes
    // at input time and applied as the fill alpha at draw time; never touches the
    // raw points the scorer reads.
    brushEffect: BrushEffect = BrushEffect.BRUSH,
    // Which form the traced ghost shows. PRINTED (default) draws the system-font
    // glyph; HANDWRITTEN draws [glyphStrokes] as a grey brush (the same strokes
    // the demo and scorer use). Falls back to the font ghost for any cell whose
    // [glyphStrokes] entry is empty.
    characterForm: CharacterForm = CharacterForm.PRINTED,
    // Per-cell KanjiVG stroke paths (viewBox space), aligned with [characters];
    // only consulted in HANDWRITTEN mode. Empty (the default) keeps the font ghost.
    glyphStrokes: List<List<String>> = emptyList(),
    // Reserved feedback strip INSIDE the paper card, above the writing area
    // (the score/chips land here on Submit). Its height is CONSTANT for the
    // sheet whether or not [header] draws anything: ink is stored in canvas
    // pixels, so a strip that only appeared on Submit would move the grid
    // out from under the already-drawn strokes.
    headerHeight: Dp = 0.dp,
    header: (@Composable BoxScope.() -> Unit)? = null,
    // Reserved strip BELOW the writing area (the "Looks different in print"
    // chip / the word-average pill). Same constant-height rule as [header]:
    // the writing square sizes itself around both strips, so footer content
    // can never overlap the square's border.
    footerHeight: Dp = 0.dp,
    footer: (@Composable BoxScope.() -> Unit)? = null,
) {
    val strokeCapture = remember(canvasState) { StrokeCapture(canvasState) }
    // The canvas ("paper") palette for the active appearance (light or dark).
    val kanaColors = LocalKanaColors.current
    // Keyed on the theme's ghost color so a light/dark flip rebuilds the
    // cached paint (an unkeyed remember would keep the old theme's grey).
    val ghostPaint = remember(kanaColors.ghost) {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = kanaColors.ghost.toArgb()
        }
    }
    // Parse the handwritten-ghost stroke paths once per glyph set (not per draw
    // frame). Unparseable strokes drop out; an empty inner list falls back to
    // the font ghost for that cell.
    val ghostStrokePaths: List<List<Path>> = remember(glyphStrokes) {
        glyphStrokes.map { cellStrokes ->
            cellStrokes.mapNotNull { d ->
                runCatching { PathParser().parsePathString(d).toPath() }.getOrNull()
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = kanaColors.paper,
        shadowElevation = 2.dp,
        // In dark mode the elevation shadow is invisible, so a hairline edge
        // keeps the sheet reading as a card against the dark background.
        border = if (kanaColors.isDark) BorderStroke(1.dp, kanaColors.gridGuide) else null,
    ) {
        // The writing cells in canvas px, refreshed each draw pass — the
        // pointer filter rejects strokes that don't START inside one, and
        // the ink render clips to them, so nothing can be drawn outside the
        // bordered writing area (margins, the score strip, between slots).
        val inkBounds = remember(canvasState) { mutableListOf<Rect>() }
        val rejectStroke = remember(canvasState) { booleanArrayOf(false) }
        val touchSlopPx = with(LocalDensity.current) { 6.dp.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
        if (headerHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .padding(horizontal = 12.dp),
                // Centered with NO fixed vertical padding: the strip's slack
                // splits evenly above and below the score card, and the
                // writing square starts at the strip's bottom edge — so the
                // card sits at the SAME distance from the paper's top as
                // from the square's border (user requirement).
                contentAlignment = Alignment.Center,
            ) {
                header?.invoke(this)
            }
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInteropFilter { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val start = Offset(event.x, event.y)
                            val inside = inkBounds.isEmpty() ||
                                inkBounds.any { it.inflate(touchSlopPx).contains(start) }
                            rejectStroke[0] = !inside
                            if (inside) strokeCapture.onMotionEvent(event) else true
                        }
                        else ->
                            if (rejectStroke[0]) true else strokeCapture.onMotionEvent(event)
                    }
                },
        ) {
            val cellCount = characters.size.coerceAtLeast(1)
            val margin = 16.dp.toPx()
            val cellGap = if (cellCount > 1) CellGap.toPx() else 0f
            val slotGap = SlotGap.toPx()
            val availWidth = size.width - margin
            val availHeight = size.height - margin

            // A slot is one full copy of the glyph row. The grid comes from
            // [planSheetGrid] (pure, unit-tested): XL is exactly one copy —
            // the only layout with well-defined scoring cells — and the L/M/S
            // sizes form a chain where each step is guaranteed BOTH visibly
            // smaller cells (≤75% of the previous size's) AND strictly more
            // copies, filling the sheet with complete rows (no ragged
            // half-empty last row), down to a 40dp writability floor.
            val grid = planSheetGrid(
                layout = layout,
                cellCount = cellCount,
                availWidth = availWidth,
                availHeight = availHeight,
                cellGap = cellGap,
                slotGap = slotGap,
                cellFloor = 40.dp.toPx(),
            )
            val cols = grid.cols
            val rows = grid.rows
            val side = grid.side
            val repetitions = grid.copies

            val slotContentWidth = side * cellCount + cellGap * (cellCount - 1)
            val gridWidth = slotContentWidth * cols + slotGap * (cols - 1)
            val gridHeight = side * rows + slotGap * (rows - 1)
            val left = (size.width - gridWidth) / 2f
            // With a score strip above, the grid anchors right below it (the
            // strip's centering provides the symmetric gap) and ALL leftover
            // height becomes bottom margin — which is also what gives the
            // "Looks different in print" chip its guaranteed room. Without a
            // strip, the grid centres as before.
            val top = if (headerHeight > 0.dp) 0f else (size.height - gridHeight) / 2f

            // 1 + 2. one practice grid + ghost glyph per cell, repeated per
            // slot — always a complete cols×rows grid, so the sheet has no
            // ragged half-empty last row. The grid and ghost toggles each
            // skip their own drawing only — cell layout is unchanged either
            // way, so ink and glyph positions stay identical.
            inkBounds.clear()
            val inkClip = Path()
            for (copy in 0 until repetitions) {
                val slotLeft = left + (copy % cols) * (slotContentWidth + slotGap)
                val slotTop = top + (copy / cols) * (side + slotGap)
                for (index in 0 until cellCount) {
                    val cell = Rect(
                        Offset(slotLeft + index * (side + cellGap), slotTop),
                        Size(side, side),
                    )
                    inkBounds.add(cell)
                    inkClip.addRect(cell)
                    if (showGrid) {
                        drawPracticeGrid(cell, borderColor = kanaColors.gridBorder, guideColor = kanaColors.gridGuide)
                    }
                    if (showGhost) {
                        // Handwritten form draws the KanjiVG strokes (grey brush)
                        // when available for this cell; otherwise — and always in
                        // printed form — the system-font glyph.
                        val handwrittenStrokes =
                            if (characterForm == CharacterForm.HANDWRITTEN) {
                                ghostStrokePaths.getOrNull(index)
                            } else {
                                null
                            }
                        if (!handwrittenStrokes.isNullOrEmpty()) {
                            drawGhostStrokes(handwrittenStrokes, cell, KANJIVG_VIEWBOX, kanaColors.ghost)
                        } else {
                            characters.getOrNull(index)?.let { glyph ->
                                drawGhostCharacter(glyph, cell, ghostPaint)
                            }
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

            // Publish the per-kana cells (in glyph order) for per-character
            // scoring of a whole word. Only the single-copy sheet has
            // unambiguous cells — a multi-copy 2×2/3×3 layout repeats the row,
            // so its ink can't be attributed to one kana; leave it empty there.
            canvasState.kanaCellRects = if (repetitions == 1) {
                (0 until cellCount).map { index ->
                    CellRect(left = left + index * (side + cellGap), top = top, size = side)
                }
            } else {
                emptyList()
            }

            // Brush geometry is built in pixels at input time, so the ink
            // width must be published before the next stroke starts — every
            // draw pass refreshes it (cheap plain-field write). Anything
            // that changes the cell size (advance, layout switch) also
            // clears the sheet via canvasClearKey, so committed ink never
            // outlives the scale it was built with.
            //
            // Ink width is the SAME fraction of the glyph the stroke-order demo
            // uses ([BrushWidthFraction]), so the user's brush reads as the same
            // % of the character as the demo — at every size. It scales with the
            // *effective* glyph size (`side` × `glyphFit`), so it thins
            // automatically for words and dense 2×2/3×3 layouts (which shrink
            // `side`) and for composite "multi-symbol" glyphs like きゃ/ファ,
            // which are drawn shrunk to fit the cell width (ghostFitScale < 1).
            val glyphFit = characters.filter { it.isNotEmpty() }
                .minOfOrNull { ghostFitScale(ghostPaint, it, side) } ?: 1f
            canvasState.brushBaseWidthPx = (side * glyphFit * BrushWidthFraction).coerceAtLeast(1f)
            // Publish the current brush effect the same way, before the next
            // stroke starts, so its geometry is baked at the chosen effect.
            canvasState.brushEffect = brushEffect

            // 3 + 4. completed strokes, then the active one — filled brush
            // ribbons, width baked in. Each stroke is clipped to the SINGLE
            // cell it started in (not the union of all cells), so a line drawn
            // inside one box stays within that box's edges and can never spill
            // into a neighbouring character's cell. The effect's fill alpha
            // (e.g. a translucent marker) is applied at draw time.
            val inkAlpha = brushEffect.spec.alpha
            // The cell a stroke starting at (x, y) belongs to — the one whose
            // slop-inflated bounds contains the start (same predicate as the
            // ACTION_DOWN acceptance test, so every accepted stroke matches one).
            fun startCell(x: Float, y: Float): Rect? {
                val p = Offset(x, y)
                return inkBounds.firstOrNull { it.inflate(touchSlopPx).contains(p) }
            }
            canvasState.completedPaths.forEachIndexed { i, path ->
                val start = canvasState.currentSession.strokes.getOrNull(i)?.points?.firstOrNull()
                val cell = start?.let { startCell(it.x, it.y) }
                if (cell != null) {
                    clipRect(cell.left, cell.top, cell.right, cell.bottom) {
                        drawPath(path = path, color = inkColor, alpha = inkAlpha)
                    }
                } else {
                    clipPath(inkClip) { drawPath(path = path, color = inkColor, alpha = inkAlpha) }
                }
            }
            canvasState.activePath?.let { path ->
                val start = canvasState.activeStrokeStart
                val cell = start?.let { startCell(it.x, it.y) }
                if (cell != null) {
                    clipRect(cell.left, cell.top, cell.right, cell.bottom) {
                        drawPath(path = path, color = inkColor, alpha = inkAlpha)
                    }
                } else {
                    clipPath(inkClip) { drawPath(path = path, color = inkColor, alpha = inkAlpha) }
                }
            }
        }
        if (footerHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(footerHeight)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                footer?.invoke(this)
            }
        }
        }
    }
}
