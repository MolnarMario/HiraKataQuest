package com.hive.hiragana.domain.model

/**
 * How many repetitions of the current character/word the tracing canvas
 * tiles onto one sheet: [span] × [span] copies of the same glyph
 * sequence, so the learner can trace several times before clearing or
 * grading. A "copy" is the full unit being practiced — a single cell for
 * a character, the whole row of kana cells for a word.
 */
enum class PracticeLayout(val span: Int) {
    SINGLE(1),
    GRID_2X2(2),
    GRID_3X3(3);

    companion object {
        /** Decodes a persisted span; unknown values fall back to [SINGLE]. */
        fun fromSpan(span: Int): PracticeLayout =
            entries.firstOrNull { it.span == span } ?: SINGLE
    }
}
