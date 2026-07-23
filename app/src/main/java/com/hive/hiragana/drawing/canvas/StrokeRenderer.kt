package com.hive.hiragana.drawing.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.hive.hiragana.drawing.stroke.StrokePoint
import kotlin.math.hypot
import kotlin.math.pow

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
     * Builds the filled ribbon for one stroke.
     *
     * @param points     raw samples in canvas pixels, in draw order
     * @param baseWidthPx the ink width (px) at neutral pressure/speed; the
     *   [profile] may vary the actual width per point around this
     * @param profile    how the half-width is distributed along the centerline
     *   ([WidthProfile.Uniform] reproduces the classic fixed-width ribbon)
     * @param cap        the end-cap shape (round discs / flat square / pointed)
     */
    fun buildBrushPath(
        points: List<StrokePoint>,
        baseWidthPx: Float,
        profile: WidthProfile = WidthProfile.Uniform,
        cap: Cap = Cap.Round,
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path

        val baseHalf = (baseWidthPx / 2f).coerceAtLeast(MIN_HALF_WIDTH)
        val sampled = decimate(points)
        if (sampled.size < 2) {
            // A tap: a single round blot at the touch point.
            val point = sampled.first()
            path.addOval(Rect(center = Offset(point.x, point.y), radius = baseHalf))
            return path
        }

        val center = smoothCenterline(sampled)
        val count = center.size
        // Per-point half-width (Uniform = constant; Tapered/Dynamic vary it).
        val half = halfWidths(sampled, center, baseHalf, profile)

        // Offset both ribbon edges from the centerline along its normals, each
        // by that point's half-width. Unit tangents are kept for square caps.
        val left = ArrayList<Offset>(count)
        val right = ArrayList<Offset>(count)
        val tangents = ArrayList<Offset>(count)
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
            tangents.add(Offset(dx, dy))
            val normal = Offset(-dy, dx)
            left.add(center[i] + normal * half[i])
            right.add(center[i] - normal * half[i])
        }

        // Square caps: push the two end rims outward along the tangent by the
        // end half-width so the ribbon ends flat and squared (no round discs).
        if (cap == Cap.Square) {
            val startShift = tangents.first() * half.first()
            val endShift = tangents.last() * half.last()
            left[0] = left[0] - startShift
            right[0] = right[0] - startShift
            left[count - 1] = left[count - 1] + endShift
            right[count - 1] = right[count - 1] + endShift
        }

        // Outline: down the left edge, back up the right, both midpoint-smoothed.
        path.moveTo(left.first().x, left.first().y)
        appendSmoothed(path, left, forward = true)
        path.lineTo(right.last().x, right.last().y)
        appendSmoothed(path, right, forward = false)
        path.close()

        // Round caps only: half-width discs at both ends (non-zero fill unions
        // them). Square ends are already squared above; Pointed ends taper to a
        // point, so both close naturally with no cap disc.
        if (cap == Cap.Round) {
            path.addOval(Rect(center = center.first(), radius = half.first()))
            path.addOval(Rect(center = center.last(), radius = half.last()))
        }
        return path
    }

    /**
     * The per-point half-width for [profile], index-aligned with [center]
     * (and [sampled], which carries the pressure + timestamps [WidthProfile.Dynamic]
     * needs). All widths are clamped ≥ 0.
     */
    private fun halfWidths(
        sampled: List<StrokePoint>,
        center: List<Offset>,
        baseHalf: Float,
        profile: WidthProfile,
    ): FloatArray {
        val n = center.size
        return when (profile) {
            WidthProfile.Uniform -> FloatArray(n) { baseHalf }

            is WidthProfile.Tapered -> FloatArray(n) { baseHalf }.also {
                applyEndTaper(it, profile.entryExitFraction, floor = 0.35f)
            }

            is WidthProfile.Dynamic -> {
                var half = FloatArray(n) { i ->
                    // Central-difference speed (px/ms); dt guarded ≥ 1ms.
                    val a = sampled[maxOf(0, i - 1)]
                    val b = sampled[minOf(n - 1, i + 1)]
                    val dt = (b.timestamp - a.timestamp).coerceAtLeast(1L).toFloat()
                    val speed = hypot(b.x - a.x, b.y - a.y) / dt
                    val t = (speed / profile.refSpeedPxPerMs).coerceIn(0f, 1f)
                    val speedFactor = lerp(profile.maxFactor, profile.minFactor, t)
                    val pressureFactor = sampled[i].pressure.coerceIn(0.25f, 2f).pow(profile.pressureExp)
                    baseHalf * speedFactor * pressureFactor
                }
                // Speed is noisy — smooth the width so the ribbon doesn't lump.
                repeat(profile.smoothPasses) { half = smoothWidths(half) }
                applyEndTaper(half, profile.taperFraction, floor = 0f)
                val ceiling = baseHalf * profile.maxSwell
                for (i in half.indices) half[i] = half[i].coerceIn(0f, ceiling)
                half
            }
        }
    }

    /**
     * Ramps the first/last [fraction] of [half] from [floor]×→1× (a smoothstep
     * over that many points), leaving the middle untouched. [floor] = 0 gives a
     * pointed taper; a small positive floor keeps a rounded-but-thinner end.
     */
    private fun applyEndTaper(half: FloatArray, fraction: Float, floor: Float) {
        val n = half.size
        val taper = (n * fraction).toInt().coerceIn(1, n / 2)
        for (k in 0 until taper) {
            val ramp = (k + 1).toFloat() / (taper + 1).toFloat()
            val scale = floor + (1f - floor) * smoothstep(ramp)
            half[k] = half[k] * scale
            half[n - 1 - k] = half[n - 1 - k] * scale
        }
    }

    /** One binomial pass over a width array; endpoints stay pinned. */
    private fun smoothWidths(w: FloatArray): FloatArray {
        val n = w.size
        if (n < 3) return w.copyOf()
        val out = FloatArray(n)
        out[0] = w[0]
        out[n - 1] = w[n - 1]
        for (i in 1 until n - 1) out[i] = (w[i - 1] + 2f * w[i] + w[i + 1]) / 4f
        return out
    }

    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

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
