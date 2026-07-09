package com.hive.hiragana.domain.model

/**
 * The two top-level tracks the app is split into.
 *
 * - [READING] — recognizing the **printed (gothic)** forms learners meet on
 *   signs, screens, and online. A recognition skill: see the glyph/word, recall
 *   its reading (and optionally meaning). Checked by multiple-choice or typed
 *   romaji — never handwriting.
 * - [WRITING] — the handwriting practice: trace the **handwritten (kaisho)**
 *   ghost, stroke-order demo, geometry/ML-Kit scoring. This is the original app.
 *
 * The learner picks a track on the opening screen and can flip between them from
 * the Study-scope sheet. Persisted via
 * [com.hive.hiragana.domain.repository.SettingsRepository]; a null stored value
 * means "not chosen yet" → show the opening screen.
 */
enum class AppMode(val prefValue: String, val displayLabel: String) {
    READING("reading", "Reading"),
    WRITING("writing", "Writing");

    companion object {
        /** Decodes a stored [prefValue]; unknown/null → null (opening screen). */
        fun fromPrefValue(value: String?): AppMode? =
            entries.firstOrNull { it.prefValue == value }
    }
}
