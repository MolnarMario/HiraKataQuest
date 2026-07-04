package com.hive.hiragana.domain.model

/**
 * Splits a kana string into the *units* a learner reads and writes as one
 * thing — where a small kana fuses with the base it modifies even though they
 * are separate code points.
 *
 * Two kinds of small kana fuse with the preceding base into one unit:
 *  - a small ゃ/ゅ/ょ forms a **yōon** (きょう → ["きょ", "う"]);
 *  - a small vowel ぁ/ぃ/ぅ/ぇ/ぉ forms an **extended** (foreign-sound) mora
 *    (ソファ → ["ソ", "ファ"], パーティー → ["パ", "ー", "ティ", "ー"]).
 * The small tsu っ/ッ (sokuon) does NOT fuse — it is its own unit (きって →
 * ["き", "っ", "て"]).
 *
 * This is the single source of truth for that grouping, shared by
 * [VocabularyWord.fromKana] (so a composite word's requiredCharacters match a
 * composite deck character whose glyph is the 2-code-point "きょ"/"ファ") and by
 * the tracing canvas's per-cell layout. It also classifies the two "advanced"
 * buckets the study opt-ins gate on: [containsYoon] and [isExtended].
 */
object KanaUnits {

    /** Small ya/yu/yo, hiragana (U+3083/5/7) and katakana (U+30E3/5/7). */
    private val SMALL_YOON = setOf('ゃ', 'ゅ', 'ょ', 'ャ', 'ュ', 'ョ')

    /** Small vowels, hiragana (U+3041/3/5/7/9) and katakana (U+30A1/3/5/7/9). */
    private val SMALL_VOWEL = setOf('ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'ァ', 'ィ', 'ゥ', 'ェ', 'ォ')

    /** Small tsu / sokuon (U+3063, U+30C3) — a gemination mark, its own unit. */
    private val SOKUON = setOf('っ', 'ッ')

    /** Historical, now-obsolete kana wi/we (U+3090/1, U+30F0/1). */
    private val ARCHAIC = setOf('ゐ', 'ゑ', 'ヰ', 'ヱ')

    /** Small kana that fuse with the preceding base into one reading unit. */
    private val FUSING = SMALL_YOON + SMALL_VOWEL

    /** True if [text] contains a small ya/yu/yo — i.e. forms at least one yōon. */
    fun containsYoon(text: String): Boolean = text.any { it in SMALL_YOON }

    /**
     * True if [text] belongs to the "extended" (small-kana / foreign / archaic)
     * bucket: it contains a small vowel (ファ, ぁ), is a small tsu (っ/ッ), or is
     * an obsolete kana (ゐ/ゑ). NOT triggered by small ゃ/ゅ/ょ (that is yōon) or
     * by dakuten (ゔ/ヴ decompose to a combining mark — see [KanaKind]).
     */
    fun isExtended(text: String): Boolean =
        text.any { it in SMALL_VOWEL || it in SOKUON || it in ARCHAIC }

    /**
     * Splits [kana] into reading units: a base kana + a following small
     * ゃ/ゅ/ょ or small vowel fuses into one unit; a small tsu and every other
     * kana is its own unit.
     */
    fun split(kana: String): List<String> {
        val units = mutableListOf<String>()
        var i = 0
        while (i < kana.length) {
            val fusesWithNext = i + 1 < kana.length && kana[i + 1] in FUSING
            if (fusesWithNext) {
                units.add(kana.substring(i, i + 2))
                i += 2
            } else {
                units.add(kana[i].toString())
                i += 1
            }
        }
        return units
    }
}
