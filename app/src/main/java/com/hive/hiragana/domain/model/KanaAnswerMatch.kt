package com.hive.hiragana.domain.model

import java.text.Normalizer

/**
 * Kana comparison for the REVERSE reading quiz's typed-answer mode, where the
 * learner is prompted with a reading and types the kana it spells.
 *
 * This is the mirror of [RomajiMatch], and deliberately far stricter. Romaji is
 * lenient because several romanization systems spell the same sound; kana is
 * not: the glyph the learner produced either is the answer or is a different
 * character. So the comparison is exact, after only the normalization needed to
 * paper over how a Japanese IME emits text:
 *
 * - NFKC, which folds half-width katakana (ﾈｺ → ネコ) to the full-width forms the
 *   deck stores and composes a half-width voiced mark onto its base (ﾊ+ﾞ → バ),
 * - all whitespace dropped, including the ideographic space U+3000, so a stray
 *   space between the kana of a word doesn't fail an otherwise correct answer,
 * - ASCII "-" and full-width "－" read as the long-vowel mark ー, which sits on a
 *   different IME key than the hyphen a learner reaches for.
 *
 * Two leniencies are deliberately NOT offered:
 *
 * - **hiragana is not folded to katakana.** Typing ねこ when the quiz asked for
 *   ネコ is wrong, because in the reverse direction the script IS the thing being
 *   tested. (The forward direction never has this problem: it asks for romaji.)
 * - **romaji is not accepted as a fallback.** A learner with no Japanese IME
 *   cannot type kana at all, and quietly passing them on "neko" would hide that
 *   instead of surfacing it. The quiz's setup card warns about the keyboard
 *   requirement up front and multiple choice remains available.
 */
object KanaAnswerMatch {

    /** True when [input] is the kana [expected] spells. */
    fun matches(input: String, expected: String): Boolean {
        val typed = canonical(input)
        if (typed.isEmpty()) return false
        return typed == canonical(expected)
    }

    /** The comparison form; also handy for tests. */
    fun canonical(raw: String): String {
        // NFKC (compatibility + composition): half-width katakana become the
        // full-width glyphs the deck stores, and a separately-emitted voiced
        // mark composes onto its base.
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val builder = StringBuilder(normalized.length)
        for (ch in normalized) {
            when {
                ch.isWhitespace() || ch == IDEOGRAPHIC_SPACE -> Unit
                ch in HYPHENS -> builder.append(LONG_VOWEL_MARK)
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    private const val IDEOGRAPHIC_SPACE = '　'
    private const val LONG_VOWEL_MARK = 'ー'

    // NFKC has already folded the full-width hyphen '－' to ASCII '-', but both
    // are listed so canonical() stays correct if the normalization ever changes.
    private val HYPHENS = charArrayOf('-', '－', '‐', '–', '—')
}
