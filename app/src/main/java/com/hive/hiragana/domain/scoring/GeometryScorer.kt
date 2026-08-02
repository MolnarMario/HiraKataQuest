package com.hive.hiragana.domain.scoring

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A plain 2D point. Kept Android-free so the scorer stays pure and unit-testable. */
data class Pt(val x: Float, val y: Float)

/** The square guide cell in the same (canvas-pixel) space as the user ink; used only for FIT. */
data class CellRect(val left: Float, val top: Float, val size: Float) {
    val centerX: Float get() = left + size / 2f
    val centerY: Float get() = top + size / 2f
}

/**
 * Pure geometric handwriting scorer. Compares a user's drawn strokes to a
 * reference glyph's stroke polylines and produces the FORM and FIT dials
 * plus coaching tags. No Android dependencies, no clock, no randomness —
 * deterministic and unit-tested.
 *
 * The design deliberately rewards *correctly formed, legible* writing and
 * forgives *where* on the sheet it lands: the FORM dial normalizes both the
 * user's ink and the reference by their own bounding boxes before comparing,
 * so size and position in the cell don't matter — only shape, stroke order,
 * direction and proportion. This mirrors how Japanese handwriting (書き方)
 * is taught: the genkōyōshi square is a proportion guide, not a trace target.
 * The FIT dial is the only one that measures adherence to the guide cell.
 *
 * Forgiving is not the same as credulous. FORM is gated by a symmetric
 * chamfer [resemblance] term over every point on both drawings, so ink that
 * simply isn't the character cannot collect points from the per-stroke pass
 * (which by construction only inspects the first `min(user, reference)`
 * strokes in draw order). Every dial bottoms out at a real 0 — no additive
 * floors, no constants that a score can come to rest on.
 *
 * Input coordinate spaces may differ (user ink is canvas pixels, reference
 * points are viewBox units) — that's fine, because normalization erases the
 * absolute scale. FIT alone needs a common space, so it takes the guide
 * [CellRect] in the user ink's (canvas-pixel) space.
 */
object GeometryScorer {

    /** Points each stroke is resampled to before comparison (arc-length uniform). */
    private const val RESAMPLE_N = 32

    /**
     * Mean normalized point distance (unit-square units) at which a stroke's
     * shape score reaches 0. Generous on purpose: legible-but-loose writing
     * should still score well. ~0.33 means "on average a third of the glyph
     * off" before the shape score bottoms out.
     */
    private const val SHAPE_TOLERANCE = 0.33f

    /** Blend of shape vs direction within a single stroke's score. */
    private const val SHAPE_WEIGHT = 0.72f
    private const val DIRECTION_WEIGHT = 0.28f

    /** Blend of per-stroke average vs whole-glyph proportion within FORM. */
    private const val STROKE_WEIGHT = 0.82f
    private const val PROPORTION_WEIGHT = 0.18f

    /** FIT: fraction of the cell the ink's longer side ideally spans. */
    private const val IDEAL_FILL = 0.82f

    /**
     * RESEMBLANCE gate — distance (unit-square units) at which a point counts
     * as "nowhere near" the other drawing. Generous, like [SHAPE_TOLERANCE]:
     * this term exists to catch ink that isn't the character AT ALL, not to
     * demand tracing accuracy (that's what the per-stroke shape score is for).
     */
    private const val COVERAGE_TOLERANCE = 0.30f

    /**
     * Resemblance at or above which the gate is fully open (no penalty).
     * Ordinary correct writing sits comfortably above this, so the gate is
     * invisible to real practice and only bites on ink that doesn't resemble
     * the target. Calibrated against GeometryScorerCalibrationTest.
     */
    private const val GATE_FULL = 0.55f

    /**
     * Stroke-count penalties. Deliberately gentle and asymmetric: our KanjiVG
     * reference is only ONE stroke-count convention, and a complete character
     * written with an EXTRA stroke (き/か in 4 strokes rather than 3 — a
     * legitimate handwriting variant) should barely cost, whereas MISSING
     * strokes mean an incomplete glyph and cost in proportion to the fraction
     * missing.
     */
    private const val EXTRA_STROKE_PENALTY = 0.10f
    private const val EXTRA_STROKE_FLOOR = 0.70f
    private const val MISSING_STROKE_PENALTY = 0.5f
    private const val MISSING_STROKE_FLOOR = 0.40f

    /**
     * Cost of drawing strokes backwards, scaled by the fraction affected.
     * Stroke direction (筆順) is a real rule, not a stylistic preference, so
     * it has to cost something even though the finished glyph looks correct —
     * but it is one mistake and should be charged once, which is why the
     * shape term now measures shape alone (see [score]).
     */
    private const val BACKWARDS_PENALTY = 0.30f

    /**
     * Scores one attempt. [userStrokes] are the drawn strokes (canvas px,
     * one point-list per pen-down..up); [referenceStrokes] are the glyph's
     * reference stroke polylines (any consistent space — viewBox is fine),
     * stroke-ordered. [cell] enables the FIT dial (null → fit is null).
     */
    fun score(
        userStrokes: List<List<Pt>>,
        referenceStrokes: List<List<Pt>>,
        cell: CellRect?,
    ): GeometryScore {
        val expected = referenceStrokes.size
        val actual = userStrokes.count { it.isNotEmpty() }
        val user = userStrokes.filter { it.isNotEmpty() }

        val tags = mutableListOf<String>()

        if (user.isEmpty() || expected == 0) {
            return GeometryScore(form = 0, fit = if (cell != null) 0 else null, expected, actual, listOf("No writing detected"))
        }

        // FORM: normalize whole attempt + whole reference into unit squares,
        // then compare stroke-by-stroke in draw order.
        val userNorm = normalizeToUnitSquare(user)
        val refNorm = normalizeToUnitSquare(referenceStrokes)

        val matched = min(userNorm.size, refNorm.size)
        var strokeScoreSum = 0f
        var worstStroke = -1
        var worstScore = Float.MAX_VALUE
        var backwardsStroke = -1
        var backwardsCount = 0

        for (i in 0 until matched) {
            val u = resample(userNorm[i], RESAMPLE_N)
            val r = resample(refNorm[i], RESAMPLE_N)

            val forwardShape = meanPointDistance(u, r)
            val reverseShape = meanPointDistance(u.asReversed(), r)
            // A stroke drawn the right shape but backwards aligns far better
            // reversed.
            val backwards = reverseShape < forwardShape * 0.6f
            if (backwards) {
                backwardsCount++
                if (backwardsStroke < 0) backwardsStroke = i
            }

            // SHAPE is measured on the better of the two alignments, so it
            // measures shape and nothing else. Scoring it on the forward
            // alignment (the original) charged a backwards stroke twice for
            // one mistake — once here and again in the direction dial — and
            // buried a real, nameable finding inside an index-alignment
            // artifact. Direction is charged where it belongs: in [dirScore]
            // and the explicit [BACKWARDS_PENALTY] below.
            val shapeScore =
                (1f - min(forwardShape, reverseShape) / SHAPE_TOLERANCE).coerceIn(0f, 1f)
            val dirScore = directionSimilarity(u, r)
            val strokeScore = SHAPE_WEIGHT * shapeScore + DIRECTION_WEIGHT * dirScore

            strokeScoreSum += strokeScore
            if (strokeScore < worstScore) {
                worstScore = strokeScore
                worstStroke = i
            }
        }
        val perStrokeAvg = strokeScoreSum / matched

        val proportionScore = proportionSimilarity(
            userNorm.take(matched),
            refNorm.take(matched),
        )

        // RESEMBLANCE gate. The per-stroke loop above only ever looks at the
        // first `matched` strokes in draw order, so on its own it cannot see
        // ink that is simply not the character: extra strokes go uninspected,
        // and a single line lands wherever the first reference stroke is. The
        // chamfer term below looks at EVERY point on both sides at once, so
        // "did you draw the whole character" and "did you draw anything that
        // isn't the character" both finally count. Multiplicative, because
        // resembling the target is a precondition for quality, not a bonus.
        val resemblance = resemblance(userNorm, refNorm)
        val gate = (resemblance / GATE_FULL).coerceIn(0f, 1f)

        var form01 = (STROKE_WEIGHT * perStrokeAvg + PROPORTION_WEIGHT * proportionScore) * gate

        // Backwards strokes: charged once, in proportion to how many.
        if (backwardsCount > 0) {
            form01 *= 1f - BACKWARDS_PENALTY * backwardsCount / matched
        }

        // Stroke-count mismatch is coached and penalised, but gently and
        // asymmetrically (see the penalty constants): an extra stroke on a
        // complete glyph is a minor deduction; missing strokes cost in
        // proportion to how much of the character is absent.
        if (actual != expected) {
            val factor = if (actual > expected) {
                (1f - EXTRA_STROKE_PENALTY * (actual - expected)).coerceAtLeast(EXTRA_STROKE_FLOOR)
            } else {
                (1f - MISSING_STROKE_PENALTY * (expected - actual).toFloat() / expected)
                    .coerceAtLeast(MISSING_STROKE_FLOOR)
            }
            form01 *= factor
            tags.add("Use $expected stroke${if (expected == 1) "" else "s"} (you drew $actual)")
        }

        val form = (form01 * 100f).roundToInt().coerceIn(0, 100)

        // Coaching tags (kept short; combine() trims to 2).
        if (backwardsStroke >= 0) {
            tags.add(
                if (backwardsCount > 1) "$backwardsCount strokes drawn backwards"
                else "Stroke ${backwardsStroke + 1} looks drawn backwards",
            )
        }
        if (tags.isEmpty()) {
            when {
                form >= 90 -> tags.add("Great shape")
                worstStroke >= 0 && worstScore < 0.6f -> tags.add("Work on stroke ${worstStroke + 1}")
                form >= 70 -> tags.add("Good, keep it up")
            }
        }

        val fit = cell?.let { fitScore(user, it, tags) }

        return GeometryScore(
            form = form,
            fit = fit,
            strokeCountExpected = expected,
            strokeCountActual = actual,
            tags = tags,
        )
    }

    // --- FIT -----------------------------------------------------------------

    /** How well the ink fills and centers within the guide cell (0..100). */
    private fun fitScore(userStrokes: List<List<Pt>>, cell: CellRect, tags: MutableList<String>): Int {
        val pts = userStrokes.flatten()
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        val span = max(w, h)

        // Fill: longer side vs ideal fraction of the cell. Under- and
        // over-filling both cost, symmetric around IDEAL_FILL.
        val fillRatio = if (cell.size > 0f) span / cell.size else 0f
        val fill01 = (1f - abs(fillRatio - IDEAL_FILL) / IDEAL_FILL).coerceIn(0f, 1f)

        // Centering: ink center offset from cell center, normalized by cell.
        val inkCx = (minX + maxX) / 2f
        val inkCy = (minY + maxY) / 2f
        val offset = hypot(inkCx - cell.centerX, inkCy - cell.centerY)
        val center01 = if (cell.size > 0f) (1f - offset / (cell.size / 2f)).coerceIn(0f, 1f) else 0f

        val fit = ((0.55f * fill01 + 0.45f * center01) * 100f).roundToInt().coerceIn(0, 100)

        if (fit < 70) {
            when {
                fillRatio < IDEAL_FILL * 0.7f -> tags.add("Fill more of the box")
                fillRatio > 1.05f -> tags.add("Keep it inside the box")
                center01 < 0.7f -> tags.add("Center it in the box")
            }
        }
        return fit
    }

    // --- resemblance ---------------------------------------------------------

    /**
     * Symmetric chamfer similarity between two already-normalized drawings,
     * 0..1. Order-independent and stroke-count-independent, so it answers the
     * one question the per-stroke comparison cannot: is this ink the same
     * DRAWING as the reference?
     *
     * - `coverage` averages, over every REFERENCE point, how close the
     *   nearest user point is → "did you draw the whole character?"
     * - `precision` averages, over every USER point, how close the nearest
     *   reference point is → "did you draw anything that isn't the
     *   character?" This is what finally makes surplus strokes cost.
     *
     * Combined as a geometric mean so one side cannot mask the other: ink
     * that covers the glyph but adds a scribble scores low, and so does a
     * single stroke that sits on the glyph but leaves most of it undrawn.
     */
    internal fun resemblance(user: List<List<Pt>>, reference: List<List<Pt>>): Float {
        // Resample so long and short strokes contribute comparably, and so
        // cost stays bounded regardless of how fast the user drew.
        val userPts = user.flatMap { resample(it, RESAMPLE_N) }
        val refPts = reference.flatMap { resample(it, RESAMPLE_N) }
        if (userPts.isEmpty() || refPts.isEmpty()) return 0f

        val coverage = meanNearness(refPts, userPts)
        val precision = meanNearness(userPts, refPts)
        return sqrt(coverage * precision)
    }

    /**
     * Mean over [from] of how near each point is to its closest point in
     * [to], as a 0..1 ramp that reaches 0 at [COVERAGE_TOLERANCE].
     */
    private fun meanNearness(from: List<Pt>, to: List<Pt>): Float {
        var sum = 0f
        for (p in from) {
            var nearest = Float.MAX_VALUE
            for (q in to) {
                val d = dist(p, q)
                if (d < nearest) nearest = d
            }
            sum += (1f - nearest / COVERAGE_TOLERANCE).coerceIn(0f, 1f)
        }
        return sum / from.size
    }

    // --- geometry helpers ----------------------------------------------------

    /**
     * Translates+scales all [strokes] together so their combined bounding box
     * maps into the unit square [0,1]², preserving aspect ratio (the longer
     * side fills [0,1], the shorter is centered). This is what makes FORM
     * ignore absolute size and position.
     */
    internal fun normalizeToUnitSquare(strokes: List<List<Pt>>): List<List<Pt>> {
        val pts = strokes.flatten()
        if (pts.isEmpty()) return strokes
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        val span = max(w, h)
        if (span <= 1e-4f) {
            // Degenerate (a dot): map everything to the center.
            return strokes.map { s -> s.map { Pt(0.5f, 0.5f) } }
        }
        val s = 1f / span
        val offX = (1f - w * s) / 2f
        val offY = (1f - h * s) / 2f
        return strokes.map { stroke ->
            stroke.map { Pt((it.x - minX) * s + offX, (it.y - minY) * s + offY) }
        }
    }

    /** Resamples a polyline to exactly [n] points, uniformly by arc length. */
    internal fun resample(points: List<Pt>, n: Int): List<Pt> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return List(n) { points[0] }
        val total = pathLength(points)
        if (total <= 1e-6f) return List(n) { points[0] }

        val step = total / (n - 1)
        val out = ArrayList<Pt>(n)
        out.add(points.first())
        var segIndex = 0
        var segStart = points[0]
        var segEnd = points[1]
        var segLen = dist(segStart, segEnd)
        var distAlong = 0f
        for (i in 1 until n - 1) {
            val target = i * step
            while (distAlong + segLen < target && segIndex < points.size - 2) {
                distAlong += segLen
                segIndex++
                segStart = points[segIndex]
                segEnd = points[segIndex + 1]
                segLen = dist(segStart, segEnd)
            }
            val remain = target - distAlong
            val t = if (segLen > 1e-6f) (remain / segLen).coerceIn(0f, 1f) else 0f
            out.add(Pt(segStart.x + (segEnd.x - segStart.x) * t, segStart.y + (segEnd.y - segStart.y) * t))
        }
        out.add(points.last())
        return out
    }

    /** Mean Euclidean distance between index-aligned points of two equal-length lists. */
    private fun meanPointDistance(a: List<Pt>, b: List<Pt>): Float {
        val n = min(a.size, b.size)
        if (n == 0) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until n) sum += dist(a[i], b[i])
        return sum / n
    }

    /**
     * Mean cosine similarity of the two polylines' tangent directions,
     * clamped at 0. Captures whether strokes progress the same way.
     *
     * The clamp matters: mapping [-1,1] onto [0,1] instead (the original)
     * scored a PERPENDICULAR stroke 0.5 and only a perfectly reversed one 0,
     * so randomly-angled ink collected half of this dial for free. Clamping
     * costs correct writing almost nothing (a good stroke's cosine is ~0.95
     * either way) and costs wrong directions everything, which is the honest
     * shape for a dial that claims to measure direction.
     */
    private fun directionSimilarity(a: List<Pt>, b: List<Pt>): Float {
        val n = min(a.size, b.size)
        if (n < 2) return 1f
        var sum = 0f
        var count = 0
        for (i in 1 until n) {
            val ua = unit(a[i].x - a[i - 1].x, a[i].y - a[i - 1].y)
            val ub = unit(b[i].x - b[i - 1].x, b[i].y - b[i - 1].y)
            if (ua == null || ub == null) continue
            sum += ua.first * ub.first + ua.second * ub.second
            count++
        }
        if (count == 0) return 1f
        return max(0f, sum / count)
    }

    /**
     * Compares the relative placement of each stroke's centroid within the
     * (already normalized) glyph — rewards correct balance/arrangement
     * without demanding exact overlap.
     */
    private fun proportionSimilarity(user: List<List<Pt>>, ref: List<List<Pt>>): Float {
        val n = min(user.size, ref.size)
        if (n == 0) return 0f
        var sum = 0f
        for (i in 0 until n) {
            sum += (1f - dist(centroid(user[i]), centroid(ref[i])) / SHAPE_TOLERANCE).coerceIn(0f, 1f)
        }
        return sum / n
    }

    private fun centroid(pts: List<Pt>): Pt {
        if (pts.isEmpty()) return Pt(0.5f, 0.5f)
        var sx = 0f
        var sy = 0f
        for (p in pts) {
            sx += p.x
            sy += p.y
        }
        return Pt(sx / pts.size, sy / pts.size)
    }

    private fun pathLength(points: List<Pt>): Float {
        var total = 0f
        for (i in 1 until points.size) total += dist(points[i - 1], points[i])
        return total
    }

    private fun dist(a: Pt, b: Pt): Float = hypot(a.x - b.x, a.y - b.y)

    private fun unit(dx: Float, dy: Float): Pair<Float, Float>? {
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 1e-6f) return null
        return dx / len to dy / len
    }
}
