package com.hive.hiragana.domain.scoring

/**
 * Glyphs that are indistinguishable BY SHAPE ALONE, for the recognition
 * legibility gate.
 *
 * ML Kit sees one glyph with no surrounding text, so it cannot tell:
 *  - a SMALL kana from its regular twin (ぁ/あ, っ/つ, ャ/ヤ …) — size is
 *    relative, and the learner traces every character full-cell anyway; a
 *    lone small ぁ IS a full あ, pixel for pixel;
 *  - the shape-identical cross-script pairs (へ/ヘ, り/リ), which differ
 *    only by which script the surrounding text is in;
 *  - a katakana from the KANJI or radical it is homographic with (エ/工,
 *    カ/力, ロ/口, ニ/二 …). Katakana were carved out of kanji, so the
 *    simplest ones are byte-for-byte the same drawing as an ancient
 *    radical. Nothing in the ink can distinguish them.
 *
 * Without this, ML Kit top-ranks the twin, the target lands at rank 1+, and
 * [CharacterScore.combine]'s legibility factor discounts a perfectly good
 * attempt. Two device-found cases: every small-kana attempt was hard-capped
 * at 65 no matter how well it was written (forms of 82–91 all displayed as
 * 65), and katakana エ written cleanly (form 88 / fit 85) displayed 65 with
 * "Looks more like 工" — the kanji for "work", which is the same shape.
 *
 * Treating a twin match as recognized keeps the gate for what it is meant to
 * catch — genuinely illegible ink — and leaves the quality judgement to the
 * geometry dials, which compare against the character's ACTUAL reference
 * strokes and so can tell apart what a general-purpose recognizer cannot.
 */
object KanaLookalikes {

    /**
     * Kana that collapse onto another KANA: small forms onto their regular
     * twin, and the two cross-script pairs that are the same drawing.
     */
    private val KANA_TWINS: Map<Char, Char> = buildMap {
        // Small kana → regular, hiragana.
        put('ぁ', 'あ'); put('ぃ', 'い'); put('ぅ', 'う'); put('ぇ', 'え'); put('ぉ', 'お')
        put('っ', 'つ'); put('ゃ', 'や'); put('ゅ', 'ゆ'); put('ょ', 'よ'); put('ゎ', 'わ')
        // Small kana → regular, katakana.
        put('ァ', 'ア'); put('ィ', 'イ'); put('ゥ', 'ウ'); put('ェ', 'エ'); put('ォ', 'オ')
        put('ッ', 'ツ'); put('ャ', 'ヤ'); put('ュ', 'ユ'); put('ョ', 'ヨ'); put('ヮ', 'ワ')
        // Shape-identical across scripts.
        put('ヘ', 'へ'); put('リ', 'り')
    }

    /**
     * Kanji and CJK radicals that are homographic with a katakana, mapped
     * onto that kana.
     *
     * INVARIANT: each entry's value is a kana, and no two DIFFERENT kana may
     * become equivalent through this table — otherwise the gate would accept
     * the wrong character. That is why 又 maps to ヌ only (ス keeps its own
     * class), and why the classic confusions ク/タ, ウ/ワ, シ/ツ, ソ/ン stay
     * in separate classes. Pinned by KanaLookalikesTest.
     *
     * Erring toward accepting is deliberate: a false accept here costs
     * nothing (the geometry dials still judge how well the shape was drawn),
     * whereas a false reject hands the learner an unearned low score.
     */
    private val KANJI_HOMOGLYPHS: Map<Char, Char> = buildMap {
        put('工', 'エ')   // U+5DE5 "work"      — the device-found エ case
        put('力', 'カ')   // U+529B "power"
        put('口', 'ロ')   // U+53E3 "mouth"
        put('囗', 'ロ')   // U+56D7 enclosure radical
        put('二', 'ニ')   // U+4E8C "two"
        put('八', 'ハ')   // U+516B "eight"
        put('夕', 'タ')   // U+5915 "evening"
        put('卜', 'ト')   // U+535C divination radical
        put('厶', 'ム')   // U+53B6 "private" radical
        put('千', 'チ')   // U+5343 "thousand"
        put('丿', 'ノ')   // U+4E3F slash radical
        put('亻', 'イ')   // U+4EBB person radical
        put('匕', 'ヒ')   // U+5315 spoon radical
        put('儿', 'ル')   // U+513F legs radical
        put('彡', 'ミ')   // U+5F61 bristle radical
        put('三', 'ミ')   // U+4E09 "three"
        put('才', 'オ')   // U+624D "talent"
        put('又', 'ヌ')   // U+53C8 "again" — ヌ only; ス keeps its own class
        put('冖', 'ワ')   // U+5196 cover radical
        put('宀', 'ウ')   // U+5B80 roof radical
        put('勹', 'ク')   // U+52F9 wrap radical
        put('乂', 'メ')   // U+4E42 "mow"
        put('彐', 'ヨ')   // U+5F50 snout radical
        put('刂', 'リ')   // U+5202 knife radical (joins the り/リ class)
        put('乚', 'レ')   // U+4E5A hidden radical
        put('乜', 'セ')   // U+4E5C
        put('廾', 'サ')   // U+5EFE twenty radical
        put('木', 'ホ')   // U+6728 "tree"
        put('毛', 'モ')   // U+6BDB "hair"
        put('井', 'ヰ')   // U+4E95 "well" — obsolete ヰ, extended scope only
    }

    /**
     * Each shape-twin group collapsed onto one canonical representative.
     * Kanji resolve through to the kana twin's representative (刂 → リ → り),
     * so a class is reached the same way from any of its members.
     */
    private val CANONICAL: Map<Char, Char> =
        KANJI_HOMOGLYPHS.mapValues { (_, kana) -> KANA_TWINS[kana] ?: kana } + KANA_TWINS

    /** [text] with every shape-twin replaced by its canonical representative. */
    fun canonicalize(text: String): String =
        buildString(text.length) { text.forEach { append(CANONICAL[it] ?: it) } }

    /** True when [a] and [b] cannot be told apart by shape alone. */
    fun matches(a: String, b: String): Boolean = canonicalize(a) == canonicalize(b)

    /** The kanji→kana homoglyph table, exposed so tests can check its invariants. */
    internal val kanjiHomoglyphs: Map<Char, Char> get() = KANJI_HOMOGLYPHS
}
