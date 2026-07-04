package com.hive.hiragana.domain.model

/**
 * One timed sample of the reference pen position while writing a stroke.
 *
 * Coordinates are in the character's square [HiraganaCharacter.viewBox]
 * space (0..viewBox, not normalized), so frames share a coordinate system
 * with the SVG paths in [HiraganaCharacter.strokes]. [time] is milliseconds
 * since the start of this stroke — each stroke has its own clock starting
 * at 0, which is what lets the animation reproduce the original writing
 * rhythm and the pauses between pen lifts.
 */
data class StrokeFrame(
    val x: Float,
    val y: Float,
    val time: Long,
)

/**
 * The timed reference recording of a single stroke, consumed by the
 * stroke-order animation engine (drawing/animation).
 *
 * [order] is 1-based and matches the stroke's position in
 * [HiraganaCharacter.strokes]; [points] are ordered by strictly increasing
 * [StrokeFrame.time], starting at 0.
 *
 * This is derived data: generated from the SVG stroke paths by
 * `tools/generate_stroke_animations.py`. Re-run the generator instead of
 * hand-editing the `strokeAnimations` arrays in the asset JSON.
 * See docs/PHASE2-DESIGN.md for the full schema contract.
 */
data class StrokeDefinition(
    val order: Int,
    val points: List<StrokeFrame>,
)
