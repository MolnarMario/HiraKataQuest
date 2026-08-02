package com.hive.hiragana.domain.scoring

import kotlin.math.roundToInt

/**
 * Outcome of the geometry pass: the two dials plus coaching findings, all
 * derived purely from stroke geometry (no recognition). 0..100 each.
 *
 * [strokeCountExpected]/[strokeCountActual] drive the "wrong number of
 * strokes" coaching and the score cap. [fit] is null when no guide-cell
 * rectangle was supplied (e.g. a layout where fit is meaningless).
 */
data class GeometryScore(
    val form: Int,
    val fit: Int?,
    val strokeCountExpected: Int,
    val strokeCountActual: Int,
    val tags: List<String>,
)

/**
 * Result of asking a [com.hive.hiragana.domain.repository.HandwritingRecognizer]
 * whether the ink reads as the intended character.
 *
 * [recognized] is true when the target was the recognizer's top candidate;
 * [rank] is the 0-based position of the target in the candidate list (0 =
 * top, null = absent). [topCandidate] is whatever the recognizer thought
 * was most likely, for the "looks more like X" feedback.
 */
data class RecognitionResult(
    val recognized: Boolean,
    val rank: Int?,
    val topCandidate: String?,
) {
    companion object {
        /** Sentinel for "recognition unavailable" (model still downloading, disabled, or errored). */
        val UNAVAILABLE = RecognitionResult(recognized = false, rank = null, topCandidate = null)
    }
}

/**
 * The final, user-facing quality score for one character attempt, combining
 * the geometry dials with the optional recognition verdict under the active
 * [ScoringMode].
 *
 * [headline] is the single number shown (per mode). [form]/[fit] are kept
 * for persistence and the Library history regardless of mode, so switching
 * modes later doesn't invalidate stored history. [tags] are short coaching
 * lines. [recognitionRan] records whether recognition contributed (false
 * when the model was unavailable and we scored geometry-only).
 */
data class CharacterScore(
    val headline: Int,
    val form: Int,
    val fit: Int?,
    val recognitionScore: Int?,
    val recognitionRan: Boolean,
    val mode: ScoringMode,
    val tags: List<String>,
) {
    companion object {
        /**
         * Fraction of the geometry score kept when the recognizer did not
         * read the ink as the target. A MULTIPLIER, never a cap.
         *
         * The original design clamped the headline to a constant (65 when the
         * target was somewhere in the candidate list, 45 when it was absent).
         * Because a clamp is a maximum, those constants became the score:
         * every well-written character the recognizer merely misranked landed
         * on exactly 65, and every meaningless scribble whose geometry
         * happened to exceed 45 landed on exactly 45. Both were reported from
         * the device as "the score it defaults to", and both were right —
         * the number shown was a constant, not a measurement.
         *
         * Scaling instead of clamping cannot invent a floor, so a low
         * geometry score stays low and a high one is discounted rather than
         * flattened.
         *
         * The discount is deliberately gentle, including for a total miss.
         * Geometry compares the ink against the character's ACTUAL reference
         * strokes; the recognizer is general-purpose and has known blind
         * spots on exactly this deck — homographs (エ read as the kanji 工),
         * and rare glyphs like the yōon composites, extended katakana and
         * obsolete ゐ/ヱ that it may not emit at all. Those failures are
         * systematic, so a harsh factor would quietly punish whole classes of
         * character forever. Meanwhile it buys almost nothing at the bottom:
         * meaningless ink is already near zero from geometry alone, so a
         * severe factor mostly just damages good writing. Recognition
         * therefore expresses doubt, not a verdict.
         */
        private fun legibilityFactor(r: RecognitionResult): Float = when {
            r.recognized -> 1.00f
            r.rank == 1 -> 0.92f
            r.rank == 2 -> 0.86f
            r.rank != null -> 0.80f
            else -> 0.70f // not in the candidate list at all
        }

        /**
         * Form score at or above which the shape gate is fully open. Below
         * it, FIT-mode headlines are scaled down: FIT measures only the ink's
         * bounding box (how much of the cell it fills, how centred it is), so
         * on its own it will happily award ~94 to a single line drawn through
         * the middle of the square. The gate keeps FIT meaning what it claims
         * while denying it the ability to score neatness on ink that isn't a
         * character.
         */
        private const val SHAPE_GATE_FULL = 55f

        /**
         * Blends the geometry pass with an optional recognition verdict.
         *
         * Recognition acts as a legibility discount, not the headline number:
         * if the recognizer ran and did NOT read the ink as the target, the
         * headline is scaled by [legibilityFactor] and a "looks more like X"
         * tag is surfaced. When recognition is unavailable ([recognition]
         * null), we score geometry-only and say nothing about legibility.
         *
         * Mode selection is form-dominant throughout. BOTH is deliberately
         * NOT the average of the two dials: averaging let a shape-blind FIT
         * carry a bad attempt (form 20 + fit 94 reads 57), so FIT modulates
         * the form score within a band instead. Good writing is unaffected
         * (form 88 / fit 85 → 83), bad writing can no longer be rescued by
         * sitting neatly in the box.
         */
        fun combine(
            geometry: GeometryScore,
            recognition: RecognitionResult?,
            mode: ScoringMode,
        ): CharacterScore {
            val form = geometry.form
            val fit = geometry.fit
            val base: Float = when (mode) {
                ScoringMode.FIT ->
                    (fit ?: form) * (form / SHAPE_GATE_FULL).coerceIn(0f, 1f)
                ScoringMode.BOTH ->
                    if (fit != null) form * (0.65f + 0.35f * fit / 100f) else form.toFloat()
                // FORM and OFF (OFF shouldn't reach here, but default safe)
                else -> form.toFloat()
            }

            val tags = geometry.tags.toMutableList()
            var headline = base
            var recognitionScore: Int? = null
            val recognitionRan = recognition != null && recognition !== RecognitionResult.UNAVAILABLE

            if (recognitionRan) {
                val r = recognition!!
                recognitionScore = when {
                    r.recognized -> 100
                    r.rank != null -> (100 - r.rank * 25).coerceAtLeast(40) // in candidate list, ranked
                    else -> 0
                }
                if (!r.recognized) {
                    headline *= legibilityFactor(r)
                    val looksLike = r.topCandidate
                    tags.add(0, if (looksLike != null) "Looks more like $looksLike" else "Hard to read")
                } else if (tags.isEmpty()) {
                    tags.add("Readable ✓")
                }
            }

            return CharacterScore(
                headline = headline.roundToInt().coerceIn(0, 100),
                form = form,
                fit = fit,
                recognitionScore = recognitionScore,
                recognitionRan = recognitionRan,
                mode = mode,
                tags = tags.take(2),
            )
        }
    }
}
