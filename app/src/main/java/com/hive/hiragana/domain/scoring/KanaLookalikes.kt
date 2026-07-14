package com.hive.hiragana.domain.scoring

/**
 * Kana that are indistinguishable BY SHAPE ALONE, for the recognition
 * legibility gate.
 *
 * ML Kit sees one glyph with no surrounding text, so it cannot tell:
 *  - a SMALL kana from its regular twin (ぁ/あ, っ/つ, ャ/ヤ …) — size is
 *    relative, and the learner traces every character full-cell anyway; a
 *    lone small ぁ IS a full あ, pixel for pixel;
 *  - the shape-identical cross-script pairs (へ/ヘ, り/リ), which differ
 *    only by which script the surrounding text is in.
 *
 * Without this, ML Kit top-ranks the regular/other-script twin, the target
 * lands at rank 2, and [CharacterScore.combine]'s not-recognized cap fires —
 * every small-kana attempt was hard-capped at 65 no matter how well it was
 * written (found on device: forms of 82–91 all displayed as 65). Treating a
 * twin match as recognized keeps the gate for what it is meant to catch —
 * genuinely illegible ink — and leaves the score to the geometry dials
 * (form + stroke count/order/direction).
 */
object KanaLookalikes {

    /** Each shape-twin group collapsed onto one canonical representative. */
    private val CANONICAL: Map<Char, Char> = buildMap {
        // Small kana → regular, hiragana.
        put('ぁ', 'あ'); put('ぃ', 'い'); put('ぅ', 'う'); put('ぇ', 'え'); put('ぉ', 'お')
        put('っ', 'つ'); put('ゃ', 'や'); put('ゅ', 'ゆ'); put('ょ', 'よ'); put('ゎ', 'わ')
        // Small kana → regular, katakana.
        put('ァ', 'ア'); put('ィ', 'イ'); put('ゥ', 'ウ'); put('ェ', 'エ'); put('ォ', 'オ')
        put('ッ', 'ツ'); put('ャ', 'ヤ'); put('ュ', 'ユ'); put('ョ', 'ヨ'); put('ヮ', 'ワ')
        // Shape-identical across scripts.
        put('ヘ', 'へ'); put('リ', 'り')
    }

    /** [text] with every shape-twin replaced by its canonical representative. */
    fun canonicalize(text: String): String =
        buildString(text.length) { text.forEach { append(CANONICAL[it] ?: it) } }

    /** True when [a] and [b] cannot be told apart by shape alone. */
    fun matches(a: String, b: String): Boolean = canonicalize(a) == canonicalize(b)
}
