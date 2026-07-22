package com.hive.hiragana.domain.model

import java.text.Normalizer

/**
 * The three kana whose printed (font) form and handwritten form genuinely
 * differ — not just in style, but in whether strokes connect: さ/き/り, the
 * top of every 活字体-vs-手書き textbook chart. In the printed form the stroke
 * pairs join into one continuous line; the school handwriting form taught by
 * our KanjiVG ghosts keeps them separate strokes. (Other kana on those
 * charts — ふ, ぬ, そ, the katakana rows — differ only in flavor, close
 * enough not to warrant a warning.)
 *
 * The connected printed forms come from a bundled font subset (Kosugi for
 * さ/き, IBM Plex Sans JP for り — merged into one file, see
 * PrintDivergenceInfo.kt and tools/fonts/) because Android's system font
 * draws all three in the detached handwriting style. Per the project owner's
 * decision, the CONNECTED form is what every typed/printed surface shows for
 * these kana, so learners meet both forms constantly.
 *
 * This is editorial knowledge — it cannot be derived from Unicode or the
 * asset data — so the map below is the one source of truth. It is keyed on
 * the BASE hiragana letter and looked up via NFD decomposition, so voiced
 * variants and composites inherit for free (ぎ, きゃ, ぎゃ all match き).
 * Katakana never matches (its print and handwritten forms agree closely),
 * which falls out naturally: a katakana glyph never decomposes to a hiragana
 * base.
 *
 * Purely informative: nothing here touches scoring, reading tallies, or
 * scheduling.
 */
object PrintDivergence {

    /**
     * One note per affected base letter. The wording is direction-neutral
     * (names both forms) because it is shown in both tracks: the Writing
     * track reads it as "here's what print will look like", the Reading
     * track as "here's what handwriting will look like".
     */
    private val NOTES: Map<Char, String> = mapOf(
        'き' to "In books and many print fonts the bottom curve joins the " +
            "stroke above. Handwritten き has 4 strokes: the bottom hook " +
            "is written separately.",
        'さ' to "In books and many print fonts the bottom curve joins the " +
            "stroke above. Handwritten さ has 3 strokes: the bottom is " +
            "written separately.",
        'り' to "In print (as shown here) the two strokes of り flow into one. " +
            "The school handwriting form traces them as two separate strokes.",
    )

    /**
     * The divergence note for one reading unit's [glyph] (e.g. "き", "ぎ",
     * "きゃ"), or null when its print and handwritten forms agree. Matches on
     * the NFD-decomposed characters, so dakuten marks are stripped and the
     * base letter of a composite is seen.
     */
    fun noteFor(glyph: String): String? =
        Normalizer.normalize(glyph, Normalizer.Form.NFD)
            .firstNotNullOfOrNull { NOTES[it] }
}
