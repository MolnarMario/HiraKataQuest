package com.hive.hiragana.domain.model

/**
 * One vocabulary word from a script's `assets/<script>/vocabulary.json`.
 *
 * @param kana               the word in kana (also its stable id) — hiragana
 *                           for hiragana words, katakana (incl. the long-vowel
 *                           mark ー) for katakana words
 * @param meaning            short English gloss shown in the guide panel
 * @param requiredCharacters distinct characters the word is written with —
 *                           derived by the parser from [kana], never stored
 *                           in the JSON. The word unlocks once every one of
 *                           them is learned (docs/PHASE3-DESIGN.md §3)
 */
data class VocabularyWord(
    val kana: String,
    val meaning: String,
    val requiredCharacters: Set<String>,
) {
    /**
     * Which writing system this word belongs to, derived from [kana]: any
     * character in the Unicode Katakana block (U+30A0–U+30FF, which includes
     * the long-vowel mark ー at U+30FC) marks the word katakana; otherwise
     * hiragana. The single place the word→script rule lives. The shipped
     * decks keep each word entirely within one block, so this never has to
     * arbitrate a mixed-script word.
     */
    val script: Script
        get() = if (kana.any { it in '゠'..'ヿ' }) Script.KATAKANA else Script.HIRAGANA

    /**
     * True when any character in [kana] is a voiced (dakuten) or semi-voiced
     * (handakuten) kana — e.g. かぎ, めがね, ゲーム. Such words are opt-in in
     * the learning flow: they stay locked until the learner enables voiced
     * kana (see [ScriptScope]). Derived from [kana] via [KanaKind], the single
     * place the rule lives. (Note: small ゃ/ゅ/ょ are NOT dakuten — those are
     * classified by [usesYoon], an independent opt-in.)
     */
    val usesDiacritics: Boolean
        get() = kana.any { KanaKind.of(it.toString()) != KanaKind.BASE }

    /**
     * True when [kana] contains a yōon (a small ゃ/ゅ/ょ forming a contracted
     * syllable like きょ) — e.g. きょう, しゅくだい. Yōon words are their own
     * opt-in, gated independently of [usesDiacritics] (see [ScriptScope]).
     */
    val usesYoon: Boolean
        get() = KanaUnits.containsYoon(kana)

    /**
     * True when [kana] uses an "extended" kana — a small tsu (きって), a
     * small-vowel foreign combo (ソファ), or an obsolete kana. Its own opt-in,
     * gated ahead of [usesYoon]/[usesDiacritics] (see [ScriptScope]).
     */
    val usesExtended: Boolean
        get() = KanaUnits.isExtended(kana)

    companion object {
        /**
         * Builds a word with [requiredCharacters] derived from [kana]'s reading
         * units — the single place the derivation rule lives, so the parser and
         * tests cannot drift apart. Splitting via [KanaUnits] groups a yōon
         * (base + small ゃ/ゅ/ょ) into one unit so it matches a yōon deck
         * character (whose glyph is the 2-code-point "きょ"); plain kana each
         * become their own unit, unchanged.
         */
        fun fromKana(kana: String, meaning: String) = VocabularyWord(
            kana = kana,
            meaning = meaning,
            requiredCharacters = KanaUnits.split(kana).toSet(),
        )
    }
}
