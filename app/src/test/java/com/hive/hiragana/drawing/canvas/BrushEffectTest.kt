package com.hive.hiragana.drawing.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushEffectTest {

    @Test
    fun `unknown or null prefValue falls back to BRUSH`() {
        assertEquals(BrushEffect.BRUSH, BrushEffect.fromPrefValue(null))
        assertEquals(BrushEffect.BRUSH, BrushEffect.fromPrefValue("nonsense"))
        assertEquals(BrushEffect.BRUSH, BrushEffect.fromPrefValue(""))
    }

    @Test
    fun `every effect round-trips through its prefValue`() {
        BrushEffect.entries.forEach { effect ->
            assertEquals(effect, BrushEffect.fromPrefValue(effect.prefValue))
        }
    }

    @Test
    fun `prefValues are unique and non-blank`() {
        val values = BrushEffect.entries.map { it.prefValue }
        assertEquals(values.size, values.toSet().size)
        assertTrue(values.all { it.isNotBlank() })
    }

    @Test
    fun `default BRUSH is an opaque uniform round ribbon`() {
        val spec = BrushEffect.BRUSH.spec
        assertEquals(WidthProfile.Uniform, spec.widthProfile)
        assertEquals(Cap.Round, spec.cap)
        assertEquals(1f, spec.alpha)
    }

    @Test
    fun `calligraphy varies width dynamically and marker is translucent`() {
        assertTrue(BrushEffect.CALLIGRAPHY.spec.widthProfile is WidthProfile.Dynamic)
        assertNotNull(BrushEffect.CALLIGRAPHY.displayLabel)
        assertTrue(BrushEffect.MARKER.spec.alpha < 1f)
        assertEquals(Cap.Square, BrushEffect.MARKER.spec.cap)
    }
}
