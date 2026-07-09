package com.hive.hiragana.domain.model

import kotlin.math.roundToInt

/**
 * A learner's reading-quiz accuracy for one glyph/word, split by answer style.
 *
 * Reading is pass/fail, so this is about *how often* you get it right, not a
 * quality score — there is no "best". [mcqPercent] is the success rate on
 * multiple-choice questions, [typePercent] on typed-romaji ones; each is null
 * until that style has been attempted at least once.
 */
data class ReadingProgress(
    val itemKey: String,
    val mcqAttempts: Int,
    val mcqCorrect: Int,
    val typeAttempts: Int,
    val typeCorrect: Int,
) {
    val mcqPercent: Int? get() = percent(mcqCorrect, mcqAttempts)
    val typePercent: Int? get() = percent(typeCorrect, typeAttempts)

    /**
     * The single figure that colors a Library cell: the average of whichever
     * styles have been attempted — both if the learner did both, otherwise the
     * one they did. Null when neither has been attempted (no tint).
     */
    val colorPercent: Int? get() {
        val parts = listOfNotNull(mcqPercent, typePercent)
        return if (parts.isEmpty()) null else parts.average().roundToInt()
    }

    private fun percent(correct: Int, attempts: Int): Int? =
        if (attempts > 0) (correct * 100.0 / attempts).roundToInt() else null
}
