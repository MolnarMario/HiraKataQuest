package com.hive.hiragana.drawing.canvas

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Path
import com.hive.hiragana.domain.scoring.CellRect
import com.hive.hiragana.drawing.stroke.DrawingSession
import com.hive.hiragana.drawing.stroke.StrokePoint
import com.hive.hiragana.drawing.stroke.UserStroke

/**
 * Snapshot-state holder for everything drawn on the tracing canvas.
 *
 * - [activePath]      brush ribbon of the stroke currently being drawn
 * - [completedPaths]  render-ready brush ribbons of all finished strokes
 * - [currentSession]  raw point data ([DrawingSession]) for the character
 *                     currently on the canvas
 *
 * Paths are filled variable-width brush ribbons (see
 * [StrokeRenderer.buildBrushPath]) with the ink width baked into the
 * geometry, so consumers draw them with a plain fill — no stroke style,
 * no width multiplier.
 *
 * All mutations reassign snapshot state, so any composable reading these
 * properties redraws automatically. Must only be touched from the UI thread.
 */
@Stable
class CanvasState {

    /**
     * Full brush width (px) at neutral pressure and speed, baked into every
     * path built from then on. Plain state (not snapshot) — the canvas
     * assigns it from dp on every draw pass, before input can arrive.
     */
    var brushBaseWidthPx: Float = 48f

    /**
     * The single guide cell (px) when the canvas is showing one character in
     * a 1×1 layout — the reference frame for FIT scoring. Null for word
     * sheets and multi-copy layouts, where "fit to the cell" is ill-defined.
     * Plain state, refreshed by the canvas each draw pass like
     * [brushBaseWidthPx].
     */
    var lastCell: CellRect? = null

    /**
     * Per-kana guide cells (px) for the current single-copy sheet, in glyph
     * order — the reference frames for per-character scoring of a whole word
     * (one cell per [com.hive.hiragana.domain.model.KanaUnits] unit). Empty for
     * multi-copy 2×2/3×3 layouts (where a word can't be meaningfully scored per
     * cell). For a lone character this holds the single cell (same rect as
     * [lastCell]). Plain state, refreshed by the canvas each draw pass.
     */
    var kanaCellRects: List<CellRect> = emptyList()

    var currentSession: DrawingSession by mutableStateOf(DrawingSession())
        private set

    val completedPaths: SnapshotStateList<Path> = mutableStateListOf()

    var activePath: Path? by mutableStateOf(null)
        private set

    private var activeStroke: UserStroke? = null

    /** True when there is anything on the canvas (used to enable buttons). */
    val hasInk: Boolean
        get() = completedPaths.isNotEmpty() || activePath != null

    fun startStroke(point: StrokePoint) {
        // Defensive: if a stroke was somehow left open, commit it first.
        activeStroke?.let(::commitStroke)

        val stroke = UserStroke(points = mutableListOf(point))
        activeStroke = stroke
        activePath = buildActivePath(stroke)
    }

    fun extendStroke(points: List<StrokePoint>) {
        val stroke = activeStroke ?: return
        if (points.isEmpty()) return
        stroke.points.addAll(points)
        // Reassigning a fresh Path is what triggers the redraw.
        activePath = buildActivePath(stroke)
    }

    fun finishStroke() {
        val stroke = activeStroke ?: return
        commitStroke(stroke)
    }

    fun cancelStroke() {
        activeStroke = null
        activePath = null
    }

    fun clear() {
        activeStroke = null
        activePath = null
        completedPaths.clear()
        currentSession = DrawingSession()
    }

    /** The live stroke keeps a full-width tip; the flick appears on commit. */
    private fun buildActivePath(stroke: UserStroke): Path =
        StrokeRenderer.buildBrushPath(stroke.points, brushBaseWidthPx, taperTail = false)

    private fun commitStroke(stroke: UserStroke) {
        currentSession.strokes.add(stroke)
        completedPaths.add(
            StrokeRenderer.buildBrushPath(stroke.points, brushBaseWidthPx, taperTail = true),
        )
        activeStroke = null
        activePath = null
    }
}
