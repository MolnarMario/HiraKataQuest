package com.hive.hiragana.domain.scoring

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
         * Blends the geometry pass with an optional recognition verdict.
         *
         * Recognition acts as a legibility GATE, not the headline number:
         * if the recognizer ran and did NOT read the ink as the target, the
         * headline is capped (you can't score highly on a character that
         * isn't readable as the target) and a "looks more like X" tag is
         * surfaced. When recognition is unavailable ([recognition] null),
         * we score geometry-only and say nothing about legibility.
         */
        fun combine(
            geometry: GeometryScore,
            recognition: RecognitionResult?,
            mode: ScoringMode,
        ): CharacterScore {
            val form = geometry.form
            val fit = geometry.fit
            val base = when (mode) {
                ScoringMode.FIT -> fit ?: form
                ScoringMode.BOTH -> if (fit != null) (form + fit) / 2 else form
                // FORM and OFF (OFF shouldn't reach here, but default safe)
                else -> form
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
                    // Not the top read → cap; the shape may be tidy but it
                    // isn't legible as the intended character.
                    headline = headline.coerceAtMost(if (r.rank != null) 65 else 45)
                    val looksLike = r.topCandidate
                    tags.add(0, if (looksLike != null) "Looks more like $looksLike" else "Hard to read")
                } else if (tags.isEmpty()) {
                    tags.add("Readable ✓")
                }
            }

            return CharacterScore(
                headline = headline.coerceIn(0, 100),
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
