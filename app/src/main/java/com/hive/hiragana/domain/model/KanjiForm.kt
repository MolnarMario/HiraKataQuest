package com.hive.hiragana.domain.model

/**
 * One kanji spelling for a reading, with its meaning and how often that form is
 * seen. Used for the **alternate** kanji of a homophone reading (e.g. はな is
 * primarily 花 "flower" but also 鼻 "nose") — the Kanji dialog lists these under
 * the primary so the learner knows the reading maps to more than one kanji.
 * Informative only; never quizzed or scored.
 */
data class KanjiForm(
    val kanji: String,
    val meaning: String,
    val usage: KanjiUsage? = null,
)
