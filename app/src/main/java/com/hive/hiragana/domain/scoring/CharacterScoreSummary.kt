package com.hive.hiragana.domain.scoring

/**
 * Rolled-up handwriting-quality history for one glyph, for the Library's
 * per-character progress readout. Keyed by the glyph [character] string —
 * script-safe (ア and あ are distinct glyphs even though they share an id).
 * Dials are rounded 0..100 averages/bests; null when no scored attempt of
 * that dial exists.
 */
data class CharacterScoreSummary(
    val character: String,
    val attempts: Int,
    val avgForm: Int?,
    val bestForm: Int?,
    val avgFit: Int?,
    val bestFit: Int?,
)
