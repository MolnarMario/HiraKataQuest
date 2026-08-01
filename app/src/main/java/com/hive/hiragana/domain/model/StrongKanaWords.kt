package com.hive.hiragana.domain.model

/**
 * Pure selection rule for the "generate a set from my strong kana" action: pick
 * the vocabulary words whose every reading unit is a kana the learner has
 * already mastered.
 *
 * A word's reading units come from [KanaUnits.split] (a yōon like きょ counts as
 * one unit, so きょう needs both きょ AND う mastered). The mastery bar itself
 * (e.g. 85+ in both writing and reading) is decided by the caller and passed in
 * as [masteredUnits]; this function only does the set membership so it stays
 * trivially testable and free of any scoring/Android dependency.
 */
object StrongKanaWords {

    /**
     * The subset of [words] whose reading units are all contained in
     * [masteredUnits]. Words with no units are excluded (nothing to be strong
     * at). Order is preserved.
     */
    fun select(words: List<VocabularyWord>, masteredUnits: Set<String>): List<VocabularyWord> =
        words.filter { word ->
            val units = KanaUnits.split(word.kana)
            units.isNotEmpty() && units.all { it in masteredUnits }
        }
}
