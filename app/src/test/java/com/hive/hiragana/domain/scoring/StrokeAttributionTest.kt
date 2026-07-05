package com.hive.hiragana.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [StrokeAttribution.groupByCell], the pure geometry that splits a
 * flat list of user strokes into per-cell groups so each kana of a whole word
 * (traced on one sheet, one cell per reading unit) can be scored independently.
 *
 * The rule under test: a stroke is attributed to the cell whose CENTER is
 * nearest the stroke's centroid. Nearest-center (not strict point-in-rect)
 * means a stroke landing in the gap between cells still counts toward the
 * closer kana instead of being dropped. Android-free and deterministic.
 */
class StrokeAttributionTest {

    /** Two 100×100 cells side by side with a 20px gap (left at x=0, right at x=120). */
    private val leftCell = CellRect(left = 0f, top = 0f, size = 100f)   // center (50, 50)
    private val rightCell = CellRect(left = 120f, top = 0f, size = 100f) // center (170, 50)

    @Test
    fun `strokes are grouped into the nearest cell`() {
        // Stroke clearly inside the left cell (centroid ~ (25, 50)).
        val leftStroke = listOf(Pt(20f, 40f), Pt(30f, 60f))
        // Stroke clearly inside the right cell (centroid ~ (145, 50)).
        val rightStroke = listOf(Pt(140f, 40f), Pt(150f, 60f))

        val groups = StrokeAttribution.groupByCell(
            strokes = listOf(leftStroke, rightStroke),
            cells = listOf(leftCell, rightCell),
        )

        // One group per cell, in cell order.
        assertEquals(2, groups.size)
        assertEquals(listOf(leftStroke), groups[0])
        assertEquals(listOf(rightStroke), groups[1])
    }

    @Test
    fun `a stroke in the gap goes to the nearer cell, not dropped`() {
        // Centroid at x = 100 sits in the 20px gap between the cells. The left
        // center is at x=50 (dist 50), the right at x=170 (dist 70) — so it is
        // nearer the LEFT cell and must land there, not be discarded.
        val gapStroke = listOf(Pt(95f, 50f), Pt(105f, 50f)) // centroid (100, 50)

        val groups = StrokeAttribution.groupByCell(
            strokes = listOf(gapStroke),
            cells = listOf(leftCell, rightCell),
        )

        assertEquals(2, groups.size)
        assertEquals(listOf(gapStroke), groups[0]) // attributed to nearer (left)
        assertTrue(groups[1].isEmpty())
        // Nothing dropped: exactly one stroke total across all groups.
        assertEquals(1, groups.sumOf { it.size })
    }

    @Test
    fun `empty strokes are ignored`() {
        val realStroke = listOf(Pt(140f, 50f)) // right cell
        val emptyStroke = emptyList<Pt>()

        val groups = StrokeAttribution.groupByCell(
            strokes = listOf(emptyStroke, realStroke, emptyStroke),
            cells = listOf(leftCell, rightCell),
        )

        assertEquals(2, groups.size)
        assertTrue(groups[0].isEmpty())
        assertEquals(listOf(realStroke), groups[1])
        // Only the one non-empty stroke was attributed.
        assertEquals(1, groups.sumOf { it.size })
    }

    @Test
    fun `empty cells yield an empty result`() {
        val groups = StrokeAttribution.groupByCell(
            strokes = listOf(listOf(Pt(10f, 10f), Pt(20f, 20f))),
            cells = emptyList(),
        )

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `an exact tie is deterministically assigned to the lower-index cell`() {
        // Centroid exactly at x=110, the midpoint between the left center
        // (x=50) and the right center (x=170): squared distances are equal.
        // The code compares with strict `<`, so the first (lower-index) cell
        // wins and nothing is dropped.
        val tieStroke = listOf(Pt(105f, 50f), Pt(115f, 50f)) // centroid (110, 50)

        val groups = StrokeAttribution.groupByCell(
            strokes = listOf(tieStroke),
            cells = listOf(leftCell, rightCell),
        )

        assertEquals(2, groups.size)
        assertEquals(listOf(tieStroke), groups[0]) // tie → leftmost (index 0)
        assertTrue(groups[1].isEmpty())
        // Nothing dropped: exactly one stroke total across all groups.
        assertEquals(1, groups.sumOf { it.size })
    }

    @Test
    fun `multiple strokes per cell preserve their order`() {
        val left1 = listOf(Pt(10f, 10f))
        val left2 = listOf(Pt(40f, 40f))
        val right1 = listOf(Pt(160f, 20f))

        val groups = StrokeAttribution.groupByCell(
            strokes = listOf(left1, right1, left2),
            cells = listOf(leftCell, rightCell),
        )

        assertEquals(2, groups.size)
        assertEquals(listOf(left1, left2), groups[0])
        assertEquals(listOf(right1), groups[1])
    }
}
