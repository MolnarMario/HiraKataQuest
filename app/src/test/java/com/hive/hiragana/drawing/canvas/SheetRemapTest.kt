package com.hive.hiragana.drawing.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.hive.hiragana.drawing.stroke.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetRemapTest {

    private fun cell(left: Float, top: Float, side: Float) =
        Rect(Offset(left, top), Size(side, side))

    @Test
    fun `a point keeps its place in the cell when the cell moves and grows`() {
        val from = cell(100f, 200f, 400f)
        val to = cell(50f, 80f, 600f)
        // Three quarters across, a quarter down.
        val moved = remapPoint(StrokePoint(400f, 300f, 0.7f, 123L), from, to)
        assertEquals(50f + 0.75f * 600f, moved.x, 0.001f)
        assertEquals(80f + 0.25f * 600f, moved.y, 0.001f)
    }

    @Test
    fun `remapping keeps pressure and timestamp`() {
        val moved = remapPoint(StrokePoint(1f, 2f, 1.4f, 999L), cell(0f, 0f, 10f), cell(5f, 5f, 20f))
        assertEquals(1.4f, moved.pressure, 0f)
        assertEquals(999L, moved.timestamp)
    }

    @Test
    fun `a point just outside a cell maps relative to that cell, not clamped`() {
        // Strokes may start inside the canvas touch slop, a few px outside.
        val moved = remapPoint(StrokePoint(-6f, 50f, 1f, 0L), cell(0f, 0f, 100f), cell(0f, 0f, 200f))
        assertEquals(-12f, moved.x, 0.001f)
        assertEquals(100f, moved.y, 0.001f)
    }

    @Test
    fun `nearest cell is the containing one`() {
        val cells = listOf(cell(0f, 0f, 100f), cell(110f, 0f, 100f), cell(220f, 0f, 100f))
        assertEquals(1, nearestCellIndex(150f, 50f, cells))
        assertEquals(2, nearestCellIndex(319f, 99f, cells))
    }

    @Test
    fun `a start in the gap between cells goes to the closer cell`() {
        val cells = listOf(cell(0f, 0f, 100f), cell(110f, 0f, 100f))
        assertEquals(0, nearestCellIndex(103f, 50f, cells))
        assertEquals(1, nearestCellIndex(108f, 50f, cells))
    }

    @Test
    fun `no cells gives no index`() {
        assertEquals(-1, nearestCellIndex(0f, 0f, emptyList()))
    }
}
