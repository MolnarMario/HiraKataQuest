package com.hive.hiragana.domain.model

/**
 * One hiragana character as defined in assets/hiragana/<romaji>.json.
 *
 * @param id               stable identifier: the asset file basename from
 *                         index.json ("a", "i", …). Primary key for the
 *                         Phase 3 progress tables (review_cards,
 *                         character_progress)
 * @param svgPath          all strokes concatenated into a single SVG path
 *                         string (redundant with [strokes]; nothing in the
 *                         MVP reads it)
 * @param strokeImage      file name of an optional raster stroke-order image
 *                         (reserved for later phases; the MVP renders
 *                         [strokes], and no such PNG is actually bundled in
 *                         assets yet)
 * @param strokes          one SVG path string per stroke, in stroke order
 * @param viewBox          side length of the square coordinate space the SVG
 *                         paths are authored in
 * @param strokeAnimations timed pen positions for the animated stroke-order
 *                         demonstration, one [StrokeDefinition] per entry in
 *                         [strokes], sorted by [StrokeDefinition.order].
 *                         Coordinates share the [viewBox] space of [strokes].
 *                         Derived data produced offline by
 *                         tools/generate_stroke_animations.py from the SVG
 *                         paths — never hand-edited. Empty when the asset
 *                         JSON carries no (valid) `strokeAnimations` field,
 *                         in which case the UI falls back to the static
 *                         stroke-order diagram (docs/PHASE2-DESIGN.md).
 * @param script           which writing system this character belongs to.
 *                         Assigned by the asset loader from the directory the
 *                         file was read from (`assets/<script.key>/`), never
 *                         stored in the JSON — the directory *is* the script.
 */
data class HiraganaCharacter(
    val id: String = "",
    val character: String,
    val romaji: String,
    val frequency: Int,
    val svgPath: String,
    val strokeImage: String,
    val strokes: List<String>,
    val viewBox: Float = 100f,
    val strokeAnimations: List<StrokeDefinition> = emptyList(),
    val script: Script = Script.HIRAGANA,
) {
    val strokeCount: Int get() = strokes.size

    /**
     * Base syllable vs voiced (dakuten) / semi-voiced (handakuten) variant,
     * derived from [character]. Voiced kana are opt-in in the learning flow
     * (see [KanaKind] / [ScriptScope]).
     */
    val kind: KanaKind get() = KanaKind.of(character)

    /**
     * True when [character] is a yōon (a contracted syllable like きょ, whose
     * glyph is a base kana + a small ゃ/ゅ/ょ). Yōon are opt-in in the learning
     * flow, gated independently of [kind] (see [ScriptScope]).
     */
    val isYoon: Boolean get() = KanaUnits.containsYoon(character)

    /**
     * True when [character] is an "extended" kana: a small tsu (っ/ッ), a small
     * vowel or foreign-sound combo (ぁ, ファ, ティ), or an obsolete kana (ゐ/ヰ).
     * Its own opt-in, gated ahead of [isYoon]/[kind] (see [ScriptScope]).
     */
    val isExtended: Boolean get() = KanaUnits.isExtended(character)
}
