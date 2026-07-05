package com.hive.hiragana.domain.scoring

/**
 * One kana unit's handwriting score within a scored whole word — the glyph it
 * was written for, plus its [CharacterScore]. Ordered as the word reads.
 */
data class WordUnitScore(val glyph: String, val score: CharacterScore)

/**
 * Pure geometry that splits a flat list of user strokes into per-cell groups,
 * so each kana of a whole word (traced on one sheet, one cell per
 * [com.hive.hiragana.domain.model.KanaUnits] unit) can be scored independently.
 *
 * The canvas captures all ink in one flat list regardless of cell; a stroke is
 * attributed to the cell whose CENTER is nearest the stroke's centroid. Nearest-
 * center (rather than strict point-in-rect) means a stroke landing in the small
 * gap between cells still counts toward the closer kana instead of being
 * dropped. Android-free and deterministic, so it is unit-tested like the rest of
 * the scoring domain.
 */
object StrokeAttribution {

    /**
     * Groups [strokes] by nearest [cells] entry, preserving stroke order within
     * each group. Returns one list per cell (same size/order as [cells]); empty
     * strokes are ignored, and an empty [cells] yields an empty result.
     */
    fun groupByCell(
        strokes: List<List<Pt>>,
        cells: List<CellRect>,
    ): List<List<List<Pt>>> {
        if (cells.isEmpty()) return emptyList()
        val groups = List(cells.size) { mutableListOf<List<Pt>>() }
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            var cx = 0f
            var cy = 0f
            for (p in stroke) {
                cx += p.x
                cy += p.y
            }
            cx /= stroke.size
            cy /= stroke.size

            var best = 0
            var bestDist = Float.MAX_VALUE
            for (i in cells.indices) {
                val dx = cx - cells[i].centerX
                val dy = cy - cells[i].centerY
                val d = dx * dx + dy * dy
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            groups[best].add(stroke)
        }
        return groups
    }
}
