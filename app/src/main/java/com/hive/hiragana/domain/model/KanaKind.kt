package com.hive.hiragana.domain.model

import java.text.Normalizer

/**
 * Whether a kana is a plain (base) syllable or a voiced/semi-voiced variant.
 *
 * Dakuten (゛, "voiced") and handakuten (゜, "semi-voiced") kana — が, ざ, だ,
 * ば, ぱ and their katakana equivalents — are held OUT of the default learning
 * flow so a beginner isn't confronted with them before the base gojūon; the
 * learner opts them in (see the "voiced kana" toggle wired through
 * [ScriptScope] and the settings). This enum is the classifier that gate keys
 * off.
 *
 * The kind is DERIVED from the glyph, not stored: a voiced kana canonically
 * decomposes (Unicode NFD) into its base letter plus a combining mark —
 * U+3099 (dakuten) or U+309A (handakuten) — so [of] just looks for that mark.
 * One source of truth, shared by [HiraganaCharacter.kind] and
 * [VocabularyWord.usesDiacritics]; no `kind` field ever appears in the asset
 * JSON.
 */
enum class KanaKind {
    BASE,
    DAKUTEN,
    HANDAKUTEN;

    companion object {
        // Combining marks appended by NFD decomposition (referenced by code
        // point, not as literal combining chars — those merge with adjacent
        // glyphs in source and confuse editors/tooling).
        private const val COMBINING_DAKUTEN = 0x3099
        private const val COMBINING_HANDAKUTEN = 0x309A

        /** Classifies a single kana [glyph] by its NFD decomposition. */
        fun of(glyph: String): KanaKind {
            val marks = Normalizer.normalize(glyph, Normalizer.Form.NFD).map { it.code }
            return when {
                COMBINING_HANDAKUTEN in marks -> HANDAKUTEN
                COMBINING_DAKUTEN in marks -> DAKUTEN
                else -> BASE
            }
        }
    }
}
