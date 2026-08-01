package com.hive.hiragana.domain.model

/**
 * A writing system the app teaches as its own study "section".
 *
 * [key] does triple duty as the single source of truth for the script: it
 * is the assets subdirectory name (`assets/<key>/`), the persisted
 * SharedPreferences value, and the decode key — one string, one place.
 * A character's script is assigned by the asset loader from the directory
 * it was read from (never stored in the JSON); a word's script is derived
 * from its kana (see [VocabularyWord.script]).
 */
enum class Script(val key: String, val displayName: String) {
    HIRAGANA("hiragana", "Hiragana"),
    KATAKANA("katakana", "Katakana");

    companion object {
        /** Decodes a persisted [key]; unknown or null values fall back to [HIRAGANA]. */
        fun fromKey(key: String?): Script = entries.firstOrNull { it.key == key } ?: HIRAGANA
    }
}
