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
    ): Boolean =
        when {
            isExtended -> includeExtended
            isYoon -> includeYoon
            kind != KanaKind.BASE -> includeDiacritics
            else -> true
        }

    private fun VocabularyWord.inScope(
        includeDiacritics: Boolean,
        includeYoon: Boolean,
        includeExtended: Boolean,
    ): Boolean =
        when {
            usesExtended -> includeExtended
            usesYoon -> includeYoon
            usesDiacritics -> includeDiacritics
            else -> true
        }
}
