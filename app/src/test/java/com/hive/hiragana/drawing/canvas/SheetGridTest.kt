package com.hive.hiragana.drawing.canvas

import com.hive.hiragana.domain.model.PracticeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Property-style checks of the practice-sheet size ladder across the real
 * variation space: 1-cell characters through 6-cell words, on canvas shapes
 * from square to tall to compact. These pin the guarantees the sizes were
 * redesigned around (2026-07-24) after two on-device regressions where
 * adjacent sizes produced the same number of squares.
 */
class SheetGridTest {

    // 10dp / 18dp / 40dp at the Realme's 3x density.
    private val cellGapPx = 30f
    private val slotGapPx = 54f
    private val floorPx = 120f

    private val canvasShapes = listOf(
        1050f to 1050f, // square
        1050f to 1300f, // tall (Pixel-like)
        1050f to 1150f, // the Realme's practice canvas
        1050f to 900f, // short (large-font panel boost eats height)
        900f to 1100f, // compact width
    )

    private val repetitionSizes =
        listOf(PracticeLayout.LARGE, PracticeLayout.MEDIUM, PracticeLayout.SMALL)

    private fun plan(layout: PracticeLayout, k: Int, w: Float, h: Float): SheetGrid =
        planSheetGrid(
            layout = layout,
            cellCount = k,
            availWidth = w,
            availHeight = h,
            cellGap = if (k > 1) cellGapPx else 0f,
            slotGap = slotGapPx,
            cellFloor = floorPx,
        )

    /** A grid saturated at the floor may stop growing; everything else must not. */
    private fun isFloorBound(grid: SheetGrid): Boolean = grid.side <= floorPx / 0.9f

    @Test
    fun `XL is always exactly one copy`() {
        for ((w, h) in canvasShapes) for (k in 1..6) {
            val xl = plan(PracticeLayout.SINGLE, k, w, h)
            assertEquals("cols k=$k ${w}x$h", 1, xl.cols)
            assertEquals("rows k=$k ${w}x$h", 1, xl.rows)
            assertTrue("side k=$k ${w}x$h", xl.side > 0f)
        }
    }

    @Test
    fun `every step down strictly increases copies until the floor`() {
        for ((w, h) in canvasShapes) for (k in 1..6) {
            var prev = plan(PracticeLayout.SINGLE, k, w, h)
            for (size in repetitionSizes) {
                val grid = plan(size, k, w, h)
                if (!isFloorBound(grid)) {
                    assertTrue(
                        "$size k=$k ${w}x$h: ${grid.copies} !> ${prev.copies}",
                        grid.copies > prev.copies,
                    )
                } else {
                    // Saturated ladders may stall, but must never go backwards.
                    assertTrue(
                        "$size k=$k ${w}x$h: ${grid.copies} < ${prev.copies}",
                        grid.copies >= prev.copies,
                    )
                }
                prev = grid
            }
        }
    }

    @Test
    fun `every step down visibly shrinks the cells until the floor`() {
        for ((w, h) in canvasShapes) for (k in 1..6) {
            var prev = plan(PracticeLayout.SINGLE, k, w, h)
            for (size in repetitionSizes) {
                val grid = plan(size, k, w, h)
                if (!isFloorBound(grid)) {
                    assertTrue(
                        "$size k=$k ${w}x$h: side ${grid.side} !<= 0.9*${prev.side}",
                        grid.side <= prev.side * 0.9f + 0.5f,
                    )
                }
                assertTrue("$size k=$k ${w}x$h", grid.side <= prev.side + 0.5f)
                prev = grid
            }
        }
    }

    @Test
    fun `grids always fit the canvas`() {
        for ((w, h) in canvasShapes) for (k in 1..6) {
            for (size in PracticeLayout.entries) {
                val grid = plan(size, k, w, h)
                val gap = if (k > 1) cellGapPx else 0f
                val slotWidth = grid.side * k + gap * (k - 1)
                val gridWidth = slotWidth * grid.cols + slotGapPx * (grid.cols - 1)
                val gridHeight = grid.side * grid.rows + slotGapPx * (grid.rows - 1)
                assertTrue("$size k=$k ${w}x$h width $gridWidth > $w", gridWidth <= w + 0.5f)
                assertTrue("$size k=$k ${w}x$h height $gridHeight > $h", gridHeight <= h + 0.5f)
            }
        }
    }

    @Test
    fun `cells never sink below the writability floor unless XL itself is smaller`() {
        for ((w, h) in canvasShapes) for (k in 1..6) {
            val xl = plan(PracticeLayout.SINGLE, k, w, h)
            for (size in repetitionSizes) {
                val grid = plan(size, k, w, h)
                assertTrue(
                    "$size k=$k ${w}x$h side ${grid.side}",
                    grid.side >= minOf(floorPx * 0.9f, xl.side),
                )
            }
        }
    }

    /** Pinned regression: single-char M and S showed the same square count. */
    @Test
    fun `single character M and S are distinct on the Realme canvas`() {
        val m = plan(PracticeLayout.MEDIUM, 1, 1050f, 1150f)
        val s = plan(PracticeLayout.SMALL, 1, 1050f, 1150f)
        assertTrue("copies ${s.copies} !> ${m.copies}", s.copies > m.copies)
        assertTrue("side ${s.side} !< ${m.side}", s.side < m.side)
    }

    /** Pinned regression: a 2-kana word's L and M both gave 10 copies. */
    @Test
    fun `2-kana word L and M are distinct on the Realme canvas`() {
        val l = plan(PracticeLayout.LARGE, 2, 1050f, 1150f)
        val m = plan(PracticeLayout.MEDIUM, 2, 1050f, 1150f)
        assertTrue("copies ${m.copies} !> ${l.copies}", m.copies > l.copies)
        assertTrue("side ${m.side} !< ${l.side}", m.side < l.side)
    }

    /** L must not be XL-sized for a 2-kana word (the original complaint). */
    @Test
    fun `2-kana word L is visibly smaller than XL`() {
        val xl = plan(PracticeLayout.SINGLE, 2, 1050f, 1150f)
        val l = plan(PracticeLayout.LARGE, 2, 1050f, 1150f)
        assertTrue("side ${l.side} !<= 0.8*${xl.side}", l.side <= xl.side * 0.8f)
        assertTrue(l.copies > 1)
    }
}
