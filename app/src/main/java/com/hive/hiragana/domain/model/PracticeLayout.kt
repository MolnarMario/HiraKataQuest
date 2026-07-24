package com.hive.hiragana.domain.model

/**
 * The practice sheet's cell size, picked as XL / L / M / S in the guide panel.
 * [SINGLE] (XL) is one big copy of the character/word — the default, the only
 * layout that scores (one well-defined guide cell per glyph), and the size the
 * quiz always uses. The repetition sizes are planned by
 * `drawing/canvas/SheetGrid.planSheetGrid` as a CHAIN L → M → S in which every
 * step down guarantees BOTH visibly smaller cells (≤75% of the previous
 * size's actual cells; [divisor] is the absolute anchor — sheet width / 2 /
 * 2.5 / 3 — that keeps lone characters on the classic sizes) AND strictly
 * more copies, filling the sheet with complete rows down to a 40dp
 * writability floor. Cell-size targets alone could not guarantee that:
 * cells quantize to whole grids, so two targets can land on the same grid —
 * same square count, just more whitespace (device-found twice). A "copy" is
 * the full unit being practiced: a single cell for a character, the whole
 * row of kana cells for a word (which is why the old "2×2"/"3×3" grid names
 * stopped meaning anything for words — a word sheet stacks word-rows, never
 * a square grid).
 *
 * [prefKey] is the persisted pref value (the pre-rename span ints, so an
 * existing install keeps its size: old 2×2 → L, old 3×3 → S; M is new and
 * takes the previously unused 4). [sizeLabel] is the segmented picker's text.
 */
enum class PracticeLayout(val prefKey: Int, val divisor: Float, val sizeLabel: String) {
    SINGLE(1, 1f, "XL"),
    LARGE(2, 2f, "L"),
    MEDIUM(4, 2.5f, "M"),
    SMALL(3, 3f, "S");

    companion object {
        /** Decodes a persisted [prefKey]; unknown values fall back to [SINGLE]. */
        fun fromPrefKey(key: Int): PracticeLayout =
            entries.firstOrNull { it.prefKey == key } ?: SINGLE
    }
}
