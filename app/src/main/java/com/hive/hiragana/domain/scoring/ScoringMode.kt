package com.hive.hiragana.domain.scoring

/**
 * What a handwriting quality score is judged on — a user-facing setting,
 * persisted like the grid/ghost toggles.
 *
 * The scorer always computes BOTH the [FORM] and [FIT] dials; this setting
 * only decides which becomes the headline number shown to the learner:
 *
 * - [FORM] — shape, stroke order, direction and proportion, with the
 *   writing normalized by its own bounding box so its size and position in
 *   the cell are ignored. This is the forgiving dial, and how Japanese
 *   handwriting (書き方) is actually graded: legible, correctly-formed
 *   writing scores high even if it doesn't hug the guide strokes.
 * - [FIT] — how well the writing fills and centers within the guide cell.
 *   The strict "neatness" dial, for learners who want to chase tidiness.
 * - [BOTH] — the average of the two.
 * - [OFF] — scoring disabled entirely (no computation, no feedback).
 */
enum class ScoringMode(val key: String) {
    OFF("off"),
    FORM("form"),
    FIT("fit"),
    BOTH("both");

    companion object {
        /** Decodes a persisted [key]; unknown or null values fall back to [FORM]. */
        fun fromKey(key: String?): ScoringMode = entries.firstOrNull { it.key == key } ?: FORM
    }
}
