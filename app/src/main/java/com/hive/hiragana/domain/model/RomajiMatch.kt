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
 *   chi=ti, tsu=tu, fu=hu, ji=zi (and their yōon sha/sya, cha/tya, ja/zya …),
 *   plus the d-row homophones du=zu, di=zi and the wāpuro jya/jyu/jyo, and a
 *   final ん typed "nn" folded to a single n.
 *
 * Long-vowel spelling (ou vs oo vs ō) is intentionally NOT reconciled beyond
 * macron stripping — the reading is now built by [KanaReading], which spells a
 * long vowel out (コーヒー → "koohii"), so the learner's straight romaji matches
 * and typed mode only needs to be forgiving of romanization-system differences,
 * not a full romaji parser.
 */
object RomajiMatch {

    /** True when [input] is an acceptable romaji for [expected]. */
    fun matches(input: String, expected: String): Boolean {
        val typed = canonical(input)
        if (typed.isEmpty()) return false
        val want = canonical(expected)
        if (typed == want) return true
        // を: the deck stores its spelling "wo", but the particle is
        // PRONOUNCED plain "o" (and the mnemonic copy teaches that), so a
        // learner typing what they hear must pass. One-directional — "wo"
        // typed for お stays wrong (お is never spelled wo).
        return want == "wo" && typed == "o"
    }

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

    // Longer keys first so e.g. "sha" is folded before a bare "sh" could form,
    // and the yōon jya/jyu/jyo before the plain ja/ju/jo.
    private val DIGRAPHS: List<Pair<String, String>> = listOf(
        "shi" to "si",
        "sha" to "sya", "shu" to "syu", "sho" to "syo",
        "chi" to "ti",
        "cha" to "tya", "chu" to "tyu", "cho" to "tyo",
        "tsu" to "tu",
        "fu" to "hu",
        "jya" to "zya", "jyu" to "zyu", "jyo" to "zyo",
        "ja" to "zya", "ju" to "zyu", "jo" to "zyo",
        "ji" to "zi",
        // A word-final ん is habitually typed "nn" (every IME requires it).
        // (づ/ぢ are deliberately NOT folded to ず/じ: the same "du"/"di" spells
        // the distinct katakana foreign sounds ディ/ドゥ, which must stay literal.)
        "nn" to "n",
    )
}
