package com.hive.hiragana.domain.model

import java.text.Normalizer

/**
 * Lenient English-meaning comparison for the reading flashcards' typed-meaning
 * step (Typing answer style). Typed English can never be matched as strictly as
 * romaji — a word usually has several valid glosses and phrasings — so this is a
 * best-effort, forgiving check, NOT an exact string compare:
 *
 * - the expected gloss is split on `;` `,` `/` into separate accepted senses,
 *   so "up; above" accepts "up" OR "above",
 * - each sense and the input are normalized: lower-cased, accents stripped,
 *   parenthetical notes "(…)" dropped, a leading "to " (verbs) or article
 *   ("a"/"an"/"the") removed, and everything but letters/spaces collapsed,
 * - the input matches when it equals ANY normalized sense.
 *
 * Matching is whole-sense equality (not substring), so "house" doesn't match
 * "warehouse". Synonyms the gloss doesn't list ("home" for "house") won't
 * match — the accepted cost of typing meaning, which is why it's opt-in.
 */
object MeaningMatch {

    /** True when [input] is an acceptable English meaning for [expected]. */
    fun matches(input: String, expected: String): Boolean {
        val normalizedInput = normalize(input)
        if (normalizedInput.isEmpty()) return false
        return senses(expected).any { it == normalizedInput }
    }

    /** The accepted senses of [expected], normalized; exposed for tests. */
    fun senses(expected: String): List<String> =
        expected.split(';', ',', '/').map(::normalize).filter { it.isNotEmpty() }

    private fun normalize(raw: String): String {
        // Strip accents to base letters (NFD → drop combining marks).
        val deAccented = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        var t = deAccented.lowercase()
        // Drop parenthetical notes, e.g. "hey! (calling out)".
        t = t.replace(Regex("\\(.*?\\)"), " ")
        // Letters and spaces only, single-spaced.
        t = t.replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()
        // Drop a leading infinitive "to" or article so "buy" == "to buy".
        for (prefix in LEADING) {
            if (t == prefix.trim()) break // a bare "to"/"a" is the whole word — keep it
            if (t.startsWith(prefix)) {
                t = t.removePrefix(prefix).trim()
                break
            }
        }
        return t
    }

    private val LEADING = listOf("to ", "the ", "an ", "a ")
}
