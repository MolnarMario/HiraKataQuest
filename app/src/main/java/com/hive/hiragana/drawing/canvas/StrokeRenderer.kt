package com.hive.hiragana.drawing.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.hive.hiragana.drawing.stroke.StrokePoint
import kotlin.math.hypot

/**
 * Turns raw stroke points into render-ready Compose [Path]s.
 *
 * The output is a filled **fixed-width ribbon**: a constant-width band around
 * the smoothed centerline, closed with round end caps. The width is set purely
 * by the caller's base width and does NOT vary with pen pressure, finger
 * contact size, or pen speed — so a chubby finger and a thin stylus draw an
 * identically thick stroke, and the ink matches the uniform-width stroke-order
 * demo. (It used to swell with pressure and thin on fast flicks; that made the
 * thickness depend on how the screen was touched, which we deliberately removed.)
 *
 * The centerline and both ribbon edges use the same midpoint quadratic-bezier
 * smoothing the line renderer used (and that `StrokeFramePath` still uses for
 * the demo ink), so the ink keeps its jitter-free character.
 *
 * All coordinates are canvas-local pixels; the caller supplies the width in
 * pixels (see [CanvasState.brushBaseWidthPx]).
 */
object StrokeRenderer {

    /** Input points closer than this (px) are dropped — stabilises edge normals. */
    private const val MIN_POINT_STEP = 1.5f

    /** Half-width floor (px) so degenerate input still leaves visible ink. */
    private const val MIN_HALF_WIDTH = 0.75f

    /**
     * Builds the filled fixed-width ribbon for one stroke.
     *
     * @param points     raw samples in canvas pixels, in draw order
     * @param baseWidthPx the ink width (px) — identical at every point,
     *   independent of pressure, contact size and speed
     */
    fun buildBrushPath(points: List<StrokePoint>, baseWidthPx: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path

        val halfWidth = (baseWidthPx / 2f).coerceAtLeast(MIN_HALF_WIDTH)
        val sampled = decimate(points)
        if (sampled.size < 2) {
            // A tap: a single round blot at the touch point.
            val point = sampled.first()
            path.addOval(Rect(center = Offset(point.x, point.y), radius = halfWidth))
            return path
        }

        val center = smoothCenterline(sampled)

        // Offset both ribbon edges a fixed half-width from the centerline along
        // its normals — no per-point width variation.
        val count = center.size
        val left = ArrayList<Offset>(count)
        val right = ArrayList<Offset>(count)
        for (i in 0 until count) {
            val previous = center[maxOf(0, i - 1)]
            val next = center[minOf(count - 1, i + 1)]
            var dx = next.x - previous.x
            var dy = next.y - previous.y
            val length = hypot(dx, dy)
            if (length < 1e-4f) {
                dx = 1f
                dy = 0f
            } else {
                dx /= length
                dy /= length
            }
            val normal = Offset(-dy, dx)
            left.add(center[i] + normal * halfWidth)
            right.add(center[i] - normal * halfWidth)
        }

        // Outline: down the left edge, back up the right, both midpoint-smoothed.
        path.moveTo(left.first().x, left.first().y)
        appendSmoothed(path, left, forward = true)
        path.lineTo(right.last().x, right.last().y)
        appendSmoothed(path, right, forward = false)
        path.close()

        // Round caps: half-width discs at both ends (non-zero fill unions them).
        path.addOval(Rect(center = center.first(), radius = halfWidth))
        path.addOval(Rect(center = center.last(), radius = halfWidth))
        return path
    }

    /** Drops points closer than [MIN_POINT_STEP] to their kept predecessor. */
    private fun decimate(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 2) return points
        val kept = ArrayList<StrokePoint>(points.size)
        kept.add(points.first())
        for (i in 1 until points.size) {
            val point = points[i]
            val last = kept.last()
            if (hypot(point.x - last.x, point.y - last.y) >= MIN_POINT_STEP) {
                kept.add(point)
            }
        }
        // The stroke's true endpoint must survive decimation so the ink
        // reaches exactly where the pen stopped.
        if (kept.size < 2 || kept.last() !== points.last()) {
            val last = points.last()
            if (kept.size >= 2 && hypot(last.x - kept[kept.size - 2].x, last.y - kept[kept.size - 2].y) >= MIN_POINT_STEP) {
                kept[kept.size - 1] = last
            } else {
                kept.add(last)
            }
        }
        return kept
    }

    /** One binomial pass ((p-1) + 2p + (p+1)) / 4; endpoints stay pinned. */
    private fun smoothCenterline(points: List<StrokePoint>): List<Offset> {
        val smoothed = ArrayList<Offset>(points.size)
        for (i in points.indices) {
            if (i == 0 || i == points.lastIndex) {
                smoothed.add(Offset(points[i].x, points[i].y))
            } else {
                smoothed.add(
                    Offset(
                        (points[i - 1].x + 2f * points[i].x + points[i + 1].x) / 4f,
                        (points[i - 1].y + 2f * points[i].y + points[i + 1].y) / 4f,
                    ),
                )
            }
        }
        return smoothed
    }

    /**
     * Appends a midpoint-quadratic-smoothed polyline to [path], which must
     * already be positioned at the run's first point ([Path.moveTo] for the
     * left edge, the joining [Path.lineTo] for the right edge).
     */
    private fun appendSmoothed(path: Path, points: List<Offset>, forward: Boolean) {
        val indices = if (forward) 1 until points.size else points.size - 2 downTo 0
        var previous = if (forward) points.first() else points.last()
        for (i in indices) {
            val current = points[i]
            val midX = (previous.x + current.x) / 2f
            val midY = (previous.y + current.y) / 2f
            path.quadraticTo(previous.x, previous.y, midX, midY)
            previous = current
        }
        path.lineTo(previous.x, previous.y)
    }
}
