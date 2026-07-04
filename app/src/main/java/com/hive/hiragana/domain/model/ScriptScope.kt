package com.hive.hiragana.domain.model

/**
 * Pure filtering that scopes a merged multi-script deck down to what the
 * learner is currently studying: one [Script], and — unless they have opted
 * in — only the base gojūon, excluding voiced (dakuten) / semi-voiced
 * (handakuten) kana.
 *
 * This is the single behaviour-defining rule behind the app's script
 * "sections" and the voiced-kana opt-in: the ViewModel keeps the FULL merged
 * deck loaded (so glyph→romaji lookups always resolve across scripts) and
 * calls these functions to derive the scoped lists it feeds into session
 * generation, the quiz pool, free practice, and the Library lists. Kept here —
 * pure, Android-free — so the scoping behaviour is unit-tested directly rather
 * than only through the ViewModel.
 *
 * Three independent opt-ins narrow it further, each off by default:
 * @param includeDiacritics when false, dakuten/handakuten kana (が/ざ/ぱ …) and
 * words that use them are excluded.
 * @param includeYoon when false, yōon (contracted syllables like きょ) and words
 * that use them are excluded.
 * @param includeExtended when false, extended kana (small tsu っ, small vowels /
 * foreign combos like ファ/ティ, obsolete kana) and words using them are excluded.
 *
 * A character/word is classified into at most one advanced bucket for gating,
 * in priority order **extended → yōon → voiced**: e.g. a voiced-base yōon like
 * ぎゃ is gated by [includeYoon] alone, and a foreign combo like ヴァ (voiced
 * base + small vowel) by [includeExtended] alone, so the opt-ins stay
 * independent.
 */
object ScriptScope {

    /** The subset of [all] characters in [script], gated by the three opt-ins. */
    fun scopedCharacters(
        all: List<HiraganaCharacter>,
        script: Script,
        includeDiacritics: Boolean,
        includeYoon: Boolean,
        includeExtended: Boolean,
    ): List<HiraganaCharacter> =
        all.filter { it.script == script && it.inScope(includeDiacritics, includeYoon, includeExtended) }

    /** The subset of [all] words in [script], gated by the three opt-ins. */
    fun scopedWords(
        all: List<VocabularyWord>,
        script: Script,
        includeDiacritics: Boolean,
        includeYoon: Boolean,
        includeExtended: Boolean,
    ): List<VocabularyWord> =
        all.filter { it.script == script && it.inScope(includeDiacritics, includeYoon, includeExtended) }

    private fun HiraganaCharacter.inScope(
        includeDiacritics: Boolean,
        includeYoon: Boolean,
        includeExtended: Boolean,
    ): Boolean = unitInScope(character, includeDiacritics, includeYoon, includeExtended)

    /**
     * A word is in scope only when EVERY reading unit it is written with is in
     * scope — the same per-unit rule as characters, applied to each of
     * [VocabularyWord.requiredCharacters]. This is stricter (and more correct)
     * than gating on the word's coarse per-character `uses…` flags: a word that
     * mixes a standalone dakuten kana with a yōon — e.g. でんしゃ = で + ん + しゃ
     * — needs BOTH [includeDiacritics] and [includeYoon], which is exactly what
     * it takes to learn (and therefore unlock) it. So a word is shown iff it is
     * actually unlockable. A voiced-base yōon unit like ぎゅ is still classified
     * as yōon (the per-unit priority puts yōon ahead of voiced), so ぎゅうにゅう
     * stays gated by [includeYoon] alone.
     */
    private fun VocabularyWord.inScope(
        includeDiacritics: Boolean,
        includeYoon: Boolean,
        includeExtended: Boolean,
    ): Boolean =
        requiredCharacters.all { unitInScope(it, includeDiacritics, includeYoon, includeExtended) }

    /**
     * Per reading-unit gate, priority **extended → yōon → voiced** (a unit is in
     * at most one advanced bucket). Shared by character and word scoping so the
     * two can never disagree.
     */
    private fun unitInScope(
        unit: String,
        includeDiacritics: Boolean,
        includeYoon: Boolean,
        includeExtended: Boolean,
    ): Boolean =
        when {
            KanaUnits.isExtended(unit) -> includeExtended
            KanaUnits.containsYoon(unit) -> includeYoon
            KanaKind.of(unit) != KanaKind.BASE -> includeDiacritics
            else -> true
        }
}
