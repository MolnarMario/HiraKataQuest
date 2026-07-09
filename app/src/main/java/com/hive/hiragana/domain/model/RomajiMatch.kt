package com.hive.hiragana.domain.model

import java.text.Normalizer

/**
 * Lenient romaji comparison for the reading quiz's typed-answer mode.
 *
 * A learner typing the reading of a kana/word shouldn't be marked wrong over
 * romanization-system differences. Both the typed input and the expected
 * reading are reduced to a canonical form before comparison:
 *
 * - case-folded, macrons/accents stripped (ō → o, ū → u),
 * - everything but letters dropped (so spaces, the deck's space-joined
 *   syllables "ka sa", hyphens and apostrophes don't matter),
 * - Hepburn digraphs folded to a single kunrei-style spelling, so shi=si,
 *   chi=ti, tsu=tu, fu=hu, ji=zi (and their yōon sha/sya, cha/tya, ja/zya …).
 *
 * Long-vowel spelling (ou vs oo vs ō) is intentionally NOT reconciled beyond
 * macron stripping — the deck stores plain vowels ("ko u" → "kou"), and the
 * default answer style is multiple-choice, so typed mode only needs to be
 * forgiving of system differences, not a full romaji parser.
 */
object RomajiMatch {

    /** True when [input] is an acceptable romaji for [expected]. */
    fun matches(input: String, expected: String): Boolean =
        canonical(input).isNotEmpty() && canonical(input) == canonical(expected)

    /** The comparison form; also handy for tests. */
    fun canonical(raw: String): String {
        // Strip macrons/accents to their base letter (NFD → drop combining marks).
        val deAccented = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        // Letters only, lower-cased: drops spaces, hyphens, apostrophes, digits.
        var t = deAccented.lowercase().filter { it in 'a'..'z' }
        for ((from, to) in DIGRAPHS) t = t.replace(from, to)
        return t
    }

    // Longer keys first so e.g. "sha" is folded before a bare "sh" could form.
    private val DIGRAPHS: List<Pair<String, String>> = listOf(
        "shi" to "si",
        "sha" to "sya", "shu" to "syu", "sho" to "syo",
        "chi" to "ti",
        "cha" to "tya", "chu" to "tyu", "cho" to "tyo",
        "tsu" to "tu",
        "fu" to "hu",
        "ja" to "zya", "ju" to "zyu", "jo" to "zyo",
        "ji" to "zi",
    )
}
