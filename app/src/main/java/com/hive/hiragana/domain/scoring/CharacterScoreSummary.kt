package com.hive.hiragana.domain.scoring

/**
 * Rolled-up handwriting-quality history for one glyph, for the Library's
 * per-character progress readout. Keyed by the glyph [character] string —
 * script-safe (ア and あ are distinct glyphs even though they share an id).
 * Dials are rounded 0..100 averages/bests; null when no scored attempt of
 * that dial exists.
 *
 * [avgDisplayed]/[bestDisplayed] are the numbers the learner was actually
 * shown, and are what the Library reports. Attempts recorded before the
 * 2026-08-02 scoring rework have no stored headline and contribute their FORM
 * dial instead, so historic practice keeps counting rather than vanishing
 * from the average.
 */
data class CharacterScoreSummary(
    val character: String,
    val attempts: Int,
    val avgForm: Int?,
    val bestForm: Int?,
    val avgFit: Int?,
    val bestFit: Int?,
    val avgDisplayed: Int? = null,
    val bestDisplayed: Int? = null,
) {
    /** The average to show: what was displayed, else the raw dials. */
    val displayAverage: Int? get() = avgDisplayed ?: avgForm ?: avgFit

    /** The best to show: what was displayed, else the raw dials. */
    val displayBest: Int? get() = bestDisplayed ?: bestForm ?: bestFit
}
