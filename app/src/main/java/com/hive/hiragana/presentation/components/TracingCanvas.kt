package com.hive.hiragana.presentation.components

import android.graphics.Paint
import android.view.MotionEvent
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
import com.hive.hiragana.drawing.stroke.StrokeCapture
import com.hive.hiragana.presentation.theme.GhostGrey
import com.hive.hiragana.presentation.theme.GridBorder
import com.hive.hiragana.presentation.theme.GridGuide
import com.hive.hiragana.presentation.theme.InkBlack
import com.hive.hiragana.presentation.theme.PaperWhite

/** KanjiVG viewBox the character stroke paths live in (all deck glyphs use 109). */
private const val KANJIVG_VIEWBOX = 109f

/** Gap between adjacent word cells within one copy of the glyph row. */
private val CellGap = 10.dp

/** Gap between repetition slots — wider than [CellGap] so each copy of a word reads as a group. */
private val SlotGap = 18.dp

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
    val ghostPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = GhostGrey.toArgb()
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
        color = PaperWhite,
        shadowElevation = 2.dp,
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
            // With a score strip above, the grid anchors right below it (the
            // strip's centering provides the symmetric gap) and ALL leftover
            // height becomes bottom margin — which is also what gives the
            // "Looks different in print" chip its guaranteed room. Without a
            // strip, the grid centres as before.
            val top = if (headerHeight > 0.dp) 0f else (size.height - gridHeight) / 2f

            // 1 + 2. one practice grid + ghost glyph per cell, repeated per
            // slot; when the copies don't divide evenly the last row stays
            // left-aligned, worksheet-style. The grid and ghost toggles each
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
                        drawPracticeGrid(cell, borderColor = GridBorder, guideColor = GridGuide)
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
                            drawGhostStrokes(handwrittenStrokes, cell, KANJIVG_VIEWBOX, GhostGrey)
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
            // ribbons, width baked in. Clipped to the writing cells so ink
            // can never appear outside the bordered writing area. The effect's
            // fill alpha (e.g. a translucent marker) is applied at draw time.
            val inkAlpha = brushEffect.spec.alpha
            clipPath(inkClip) {
                canvasState.completedPaths.forEach { path ->
                    drawPath(path = path, color = inkColor, alpha = inkAlpha)
                }
                canvasState.activePath?.let { path ->
                    drawPath(path = path, color = inkColor, alpha = inkAlpha)
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
