package com.hive.hiragana.domain.model

/**
 * Whether a [PracticeSet] holds single characters or vocabulary words. A set
 * is built from one Library tab at a time, so it is always one or the other —
 * never mixed. This decides how [PracticeSet.members] are resolved (a glyph vs
 * a whole word's kana) and how the set launches into the quiz / free practice.
 */
enum class PracticeSetType {
    CHARACTERS,
    WORDS;

    companion object {
        /** Decode a persisted [name]; unknown/null falls back to [CHARACTERS]. */
        fun fromKey(key: String?): PracticeSetType =
            entries.firstOrNull { it.name == key } ?: CHARACTERS
    }
}

/**
 * A user-curated, hand-picked list of characters or words, saved so it can be
 * quizzed or practiced later. Deliberately distinct from the declarative Study
 * Scope opt-ins: a set is an explicit membership list, not a filter config.
 *
 * [members] are the app's script-unique keys — the glyph string
 * ([HiraganaCharacter.character]) for a [PracticeSetType.CHARACTERS] set, the
 * word's [VocabularyWord.kana] for a [PracticeSetType.WORDS] set — in the order
 * the user selected them. [script] records which writing system the set was
 * built in (selection is always scoped to one script). Persisted via Room (see
 * PracticeSetRepository), the same structured-user-data pattern as review cards
 * and vocabulary progress.
 */
data class PracticeSet(
    val id: Long = 0L,
    val name: String,
    val type: PracticeSetType,
    val script: Script,
    val members: List<String>,
)
