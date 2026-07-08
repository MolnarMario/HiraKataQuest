package com.hive.hiragana.domain.model

/**
 * How the traced guide ("ghost") glyph is drawn on the canvas.
 *
 * - [PRINTED] — the typographic form, rendered with the system font. This is
 *   the shape learners overwhelmingly meet when *reading* (signs, sites, print),
 *   so it is the default: writing it builds reading fluency fastest.
 * - [HANDWRITTEN] — the kaisho stroke form, rendered from the character's own
 *   KanjiVG strokes (the same geometry the stroke-order demo animates and the
 *   scorer compares against), so handwritten mode is internally consistent:
 *   you trace exactly what is demonstrated and graded.
 *
 * This is a **rendering choice only** — it never changes which characters are
 * studied, the stroke-order demo, or the scorer (both always use the strokes).
 * Persisted via [com.hive.hiragana.domain.repository.SettingsRepository].
 */
enum class CharacterForm(val prefValue: String, val displayLabel: String) {
    PRINTED("printed", "Printed"),
    HANDWRITTEN("handwritten", "Handwritten");

    companion object {
        /** Decodes a stored [prefValue]; unknown/null → [PRINTED] (the default). */
        fun fromPrefValue(value: String?): CharacterForm =
            entries.firstOrNull { it.prefValue == value } ?: PRINTED
    }
}
