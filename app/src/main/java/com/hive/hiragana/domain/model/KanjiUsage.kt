package com.hive.hiragana.domain.model

/**
 * How likely a regular reader is to meet a word written in its **kanji** form
 * rather than kana. Sourced from the JLPT vocabulary CSV's `kanji_usage`
 * column; shown as a colored likelihood tag in the informative Kanji dialog
 * (never affects study or scoring — this app teaches kana only).
 */
enum class KanjiUsage(val label: String) {
    /** Normally written in kanji (e.g. 帽子, 彼女). */
    USUALLY_KANJI("Usually written in kanji"),

    /** Written both ways depending on context (e.g. 事 / こと). */
    MIXED("Written both ways"),

    /** Has a kanji form but is normally written in kana (e.g. 綺麗 → きれい). */
    USUALLY_KANA("Usually written in kana");

    companion object {
        /**
         * Decodes the CSV/JSON string. Unknown or blank values yield null (the
         * tag is simply not shown), the same lenient discipline as the rest of
         * the asset parsing.
         */
        fun fromJson(value: String?): KanjiUsage? = when (value?.trim()) {
            "usually_kanji" -> USUALLY_KANJI
            "mixed" -> MIXED
            "usually_kana" -> USUALLY_KANA
            else -> null
        }
    }
}
