package com.hive.hiragana.drawing.animation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.hive.hiragana.domain.model.StrokeFrame

/**
 * Pure geometry: turns a timed [StrokeFrame] recording into render-ready
 * Compose [Path]s and an interpolated pen-tip position.
 *
 * All inputs and outputs are in the character's viewBox space (0..viewBox),
 * the shared coordinate system of `strokeAnimations`, the `strokes[]` SVG
 * paths, and `StrokeOrderDiagram` (see docs/PHASE2-DESIGN.md §1.1). Callers
 * scale to pixels at draw time.
 *
 * The frame-to-path conversion uses the same midpoint quadratic-bezier
 * smoothing (and degenerate-segment dot for a single point) that
 * `StrokeRenderer` applies to the tracing canvas's brush ribbon edges, so
 * the demonstration's ink has the same jitter-free character as the user's
 * own strokes. It stays a thin uniform line rather than a brush ribbon —
 * right for the small preview — and a *partial* path additionally ends in a
 * pen tip linearly interpolated between the last visible frame and the next
 * one (contract §2.1).
 *
 * Robustness contract: never throws for degenerate-but-well-formed data —
 * empty frame lists yield an empty path / null tip, non-increasing times
 * degrade to clamped interpolation, and times before the first frame clamp to
 * the stroke start.
 */
internal object StrokeFramePath {

    /**
     * A lone point becomes a tiny segment so a round-capped stroke renders
     * it as a visible dot (the brush renderer uses a disc for the same case).
     */
    private const val DOT_SEGMENT_LENGTH = 0.01f

    /**
     * The finished path of a whole stroke — every recorded frame, smoothed.
     * A single-frame recording yields a dot; an empty one yields an empty path.
     */
    fun fullPath(frames: List<StrokeFrame>): Path =
        buildSmoothPath(frames, frames.size, tipX = 0f, tipY = 0f, includeTip = false)

    /**
     * The partial path at stroke time [timeMs]: all frames with
     * `time <= timeMs`, smoothed, plus a final segment to the interpolated
     * pen tip (see [penPositionAt]). At `timeMs >= duration` this is
     * geometrically identical to [fullPath], so swapping the active path for
     * the committed one at stroke completion causes no visible pop.
     */
    fun partialPath(frames: List<StrokeFrame>, timeMs: Float): Path {
        if (frames.isEmpty()) return Path()
        val visibleCount = visibleFrameCount(frames, timeMs)
        val tip = interpolatedTip(frames, visibleCount, timeMs)
        val lastVisible = frames[visibleCount - 1]
        val includeTip = tip.x != lastVisible.x || tip.y != lastVisible.y
        return buildSmoothPath(frames, visibleCount, tip.x, tip.y, includeTip)
    }

    /**
     * Where the pen tip is at stroke time [timeMs]: the last frame with
     * `time <= timeMs`, moved linearly toward the next frame in proportion to
     * the time elapsed between them. Clamps to the stroke's first/last frame
     * outside the recorded range; null only for an empty recording.
     */
    fun penPositionAt(frames: List<StrokeFrame>, timeMs: Float): Offset? {
        if (frames.isEmpty()) return null
        return interpolatedTip(frames, visibleFrameCount(frames, timeMs), timeMs)
    }

    /**
     * How many leading frames have `time <= timeMs`, never less than 1: the
     * pen is on the first frame from the moment the stroke segment starts,
     * even for degenerate data whose first frame time is > 0.
     */
    private fun visibleFrameCount(frames: List<StrokeFrame>, timeMs: Float): Int {
        var count = 0
        for (frame in frames) {
            if (frame.time <= timeMs) count++ else break
        }
        return count.coerceAtLeast(1)
    }

    private fun interpolatedTip(
        frames: List<StrokeFrame>,
        visibleCount: Int,
        timeMs: Float,
    ): Offset {
        val previous = frames[visibleCount - 1]
        val next = frames.getOrNull(visibleCount)
            ?: return Offset(previous.x, previous.y)
        val deltaMs = (next.time - previous.time).toFloat()
        // deltaMs <= 0 would be malformed (times must strictly increase);
        // degrade to the next frame instead of dividing by zero.
        val fraction =
            if (deltaMs <= 0f) 1f
            else ((timeMs - previous.time) / deltaMs).coerceIn(0f, 1f)
        return Offset(
            previous.x + (next.x - previous.x) * fraction,
            previous.y + (next.y - previous.y) * fraction,
        )
    }

    /**
     * Midpoint quadratic-bezier smoothing over `frames[0 until visibleCount]`,
     * optionally extended by one linear point (the pen tip). Indexed accessors
     * instead of a merged list keep this allocation-free besides the [Path]
     * itself, which matters because the engine rebuilds the partial path on
     * every animation frame (same rebuild-per-update model as `CanvasState`).
     */
    private fun buildSmoothPath(
        frames: List<StrokeFrame>,
        visibleCount: Int,
        tipX: Float,
        tipY: Float,
        includeTip: Boolean,
    ): Path {
        val path = Path()
        val pointCount = visibleCount + if (includeTip) 1 else 0
        if (pointCount <= 0) return path

        fun xAt(index: Int): Float = if (index < visibleCount) frames[index].x else tipX
        fun yAt(index: Int): Float = if (index < visibleCount) frames[index].y else tipY

        path.moveTo(xAt(0), yAt(0))
        if (pointCount == 1) {
            path.lineTo(xAt(0) + DOT_SEGMENT_LENGTH, yAt(0))
            return path
        }

        for (i in 1 until pointCount) {
            val previousX = xAt(i - 1)
            val previousY = yAt(i - 1)
            val midX = (previousX + xAt(i)) / 2f
            val midY = (previousY + yAt(i)) / 2f
            path.quadraticTo(previousX, previousY, midX, midY)
        }

        path.lineTo(xAt(pointCount - 1), yAt(pointCount - 1))
        return path
    }
}
