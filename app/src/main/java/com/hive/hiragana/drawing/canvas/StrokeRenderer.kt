package com.hive.hiragana.drawing.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.hive.hiragana.drawing.stroke.StrokePoint
import kotlin.math.hypot

/**
 * Turns raw stroke points into render-ready Compose [Path]s.
 *
 * Since the brush rework the output is a **filled variable-width ribbon**,
 * not a uniform-width polyline: every sampled point gets its own ink width
 * from pen pressure and pen speed, the ribbon's two edges are offset from
 * the smoothed centerline along its normals, and round caps close the ends.
 * The result reads like a felt brush — fast flicks thin out, firm slow
 * strokes swell — instead of a ballpoint line of constant width.
 *
 * The centerline and both ribbon edges use the same midpoint
 * quadratic-bezier smoothing the old line renderer used (and that
 * `StrokeFramePath` still uses for the demo ink), so the ink keeps its
 * jitter-free character.
 *
 * All coordinates are canvas-local pixels; the caller supplies the base
 * width in pixels (see [CanvasState.brushBaseWidthPx]).
 */
object StrokeRenderer {

    /** Pressure -> width multiplier clamp, same bounds as the Phase 1 line ink. */
    private const val MIN_PRESSURE_SCALE = 0.55f
    private const val MAX_PRESSURE_SCALE = 1.6f

    /** Input points closer than this (px) are dropped — stabilises edge normals. */
    private const val MIN_POINT_STEP = 1.5f

    /** Pen speed (px/ms) at or below which speed causes no thinning. */
    private const val SPEED_FULL_WIDTH = 0.3f

    /** Width multiplier lost per px/ms of speed above [SPEED_FULL_WIDTH]. */
    private const val SPEED_THINNING = 0.12f

    /** Floor for the speed multiplier — fast flicks thin out but never vanish. */
    private const val MIN_SPEED_SCALE = 0.55f

    /** Moving-average half-window (in points) applied to the width profile. */
    private const val WIDTH_SMOOTHING_RADIUS = 2

    /** Arc length (px) of the entry taper and its starting width fraction. */
    private const val HEAD_TAPER_PX = 12f
    private const val HEAD_START_SCALE = 0.7f

    /** Arc length (px) of the exit taper and its final width fraction. */
    private const val TAIL_TAPER_PX = 28f
    private const val TAIL_END_SCALE = 0.35f

    /** Half-width floor (px) so degenerate input still leaves visible ink. */
    private const val MIN_HALF_WIDTH = 0.75f

    /**
     * Builds the filled brush ribbon for one stroke.
     *
     * @param points     raw samples in canvas pixels, in draw order
     * @param baseWidthPx full ink width (px) at neutral pressure and speed
     * @param taperTail  true for a committed stroke (the end thins into a
     *   brush flick); false for the live stroke, whose "tail" is the moving
     *   pen tip and must stay at full width while drawing continues
     */
    fun buildBrushPath(
        points: List<StrokePoint>,
        baseWidthPx: Float,
        taperTail: Boolean,
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path

        val halfBase = (baseWidthPx / 2f).coerceAtLeast(MIN_HALF_WIDTH)
        val sampled = decimate(points)
        if (sampled.size < 2) {
            // A tap: a single round blot at the touch point.
            val point = sampled.first()
            val radius = halfBase * pressureScale(point.pressure)
            path.addOval(Rect(center = Offset(point.x, point.y), radius = radius))
            return path
        }

        val center = smoothCenterline(sampled)
        val arcLength = cumulativeArcLength(center)
        val halfWidths = widthProfile(sampled, arcLength, halfBase, taperTail)

        // Offset both ribbon edges from the centerline along its normals.
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
            left.add(center[i] + normal * halfWidths[i])
            right.add(center[i] - normal * halfWidths[i])
        }

        // Outline: down the left edge, back up the right, both midpoint-smoothed.
        path.moveTo(left.first().x, left.first().y)
        appendSmoothed(path, left, forward = true)
        path.lineTo(right.last().x, right.last().y)
        appendSmoothed(path, right, forward = false)
        path.close()

        // Round caps: local-width discs at both ends (non-zero fill unions them).
        path.addOval(Rect(center = center.first(), radius = halfWidths.first()))
        path.addOval(Rect(center = center.last(), radius = halfWidths.last()))
        return path
    }

    /** Pressure -> width multiplier (same curve and clamps as Phase 1). */
    private fun pressureScale(pressure: Float): Float =
        (0.55f + 0.9f * pressure).coerceIn(MIN_PRESSURE_SCALE, MAX_PRESSURE_SCALE)

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

    private fun cumulativeArcLength(points: List<Offset>): FloatArray {
        val arc = FloatArray(points.size)
        for (i in 1 until points.size) {
            arc[i] = arc[i - 1] + hypot(
                points[i].x - points[i - 1].x,
                points[i].y - points[i - 1].y,
            )
        }
        return arc
    }

    /**
     * Per-point ribbon half-widths: pressure x speed, moving-average
     * smoothed so neither jittery pressure sensors nor batched timestamps
     * make the edges wobble, then end-tapered.
     */
    private fun widthProfile(
        points: List<StrokePoint>,
        arcLength: FloatArray,
        halfBase: Float,
        taperTail: Boolean,
    ): FloatArray {
        val count = points.size
        val raw = FloatArray(count)
        var lastSpeedScale = 1f
        for (i in 0 until count) {
            val speedScale = if (i == 0) {
                1f
            } else {
                val dt = (points[i].timestamp - points[i - 1].timestamp).toFloat()
                if (dt <= 0f) {
                    lastSpeedScale // batched samples share a timestamp; reuse
                } else {
                    val speed = (arcLength[i] - arcLength[i - 1]) / dt
                    (1f - (speed - SPEED_FULL_WIDTH).coerceAtLeast(0f) * SPEED_THINNING)
                        .coerceIn(MIN_SPEED_SCALE, 1f)
                }
            }
            lastSpeedScale = speedScale
            raw[i] = halfBase * pressureScale(points[i].pressure) * speedScale
        }

        val smoothed = FloatArray(count)
        for (i in 0 until count) {
            var sum = 0f
            var n = 0
            for (j in maxOf(0, i - WIDTH_SMOOTHING_RADIUS)..minOf(count - 1, i + WIDTH_SMOOTHING_RADIUS)) {
                sum += raw[j]
                n++
            }
            smoothed[i] = sum / n
        }

        val total = arcLength.last()
        for (i in 0 until count) {
            val fromStart = arcLength[i]
            if (fromStart < HEAD_TAPER_PX) {
                val t = fromStart / HEAD_TAPER_PX
                smoothed[i] *= HEAD_START_SCALE + (1f - HEAD_START_SCALE) * t
            }
            if (taperTail) {
                val fromEnd = total - arcLength[i]
                if (fromEnd < TAIL_TAPER_PX) {
                    val t = fromEnd / TAIL_TAPER_PX
                    smoothed[i] *= TAIL_END_SCALE + (1f - TAIL_END_SCALE) * t
                }
            }
            smoothed[i] = smoothed[i].coerceAtLeast(MIN_HALF_WIDTH)
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
