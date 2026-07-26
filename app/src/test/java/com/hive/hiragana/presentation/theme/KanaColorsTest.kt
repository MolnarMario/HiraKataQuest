package com.hive.hiragana.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the canvas palette so a future edit can't silently regress it. The
 * light instance MUST stay bit-identical to the pre-dark-mode constants
 * (light mode is unchanged); the dark instance must use the night palette.
 */
class KanaColorsTest {

    @Test
    fun `light palette equals the original hardcoded constants`() {
        assertEquals(PaperWhite, LightKanaColors.paper)
        assertEquals(GhostGrey, LightKanaColors.ghost)
        assertEquals(GridBorder, LightKanaColors.gridBorder)
        assertEquals(GridGuide, LightKanaColors.gridGuide)
        assertEquals(DiagramStroke, LightKanaColors.diagramStroke)
        assertEquals(InkBlack, LightKanaColors.defaultInk)
        assertFalse(LightKanaColors.isDark)
    }

    @Test
    fun `dark palette uses the night colors`() {
        assertEquals(NightPaper, DarkKanaColors.paper)
        assertEquals(NightGhost, DarkKanaColors.ghost)
        assertEquals(NightGridBorder, DarkKanaColors.gridBorder)
        assertEquals(NightGridGuide, DarkKanaColors.gridGuide)
        assertEquals(NightDiagramStroke, DarkKanaColors.diagramStroke)
        assertEquals(InkChalk, DarkKanaColors.defaultInk)
        assertTrue(DarkKanaColors.isDark)
    }

    /**
     * The two default-ink constants must match the ARGB literals hardcoded in
     * PrefsSettingsRepository (DEFAULT_INK_ARGB / DEFAULT_INK_DARK_ARGB) and in
     * PracticeUiState, since those are duplicated by value in the data layer.
     */
    @Test
    fun `default ink argb matches the prefs defaults`() {
        assertEquals(0xFF20222E.toInt(), argb(InkBlack))
        assertEquals(0xFFEFE7DA.toInt(), argb(InkChalk))
    }

    // Compose Color -> packed ARGB Int, mirroring Color.toArgb() without the
    // android.jar dependency (pure bit math on the value class).
    private fun argb(c: androidx.compose.ui.graphics.Color): Int {
        val a = (c.alpha * 255f + 0.5f).toInt()
        val r = (c.red * 255f + 0.5f).toInt()
        val g = (c.green * 255f + 0.5f).toInt()
        val b = (c.blue * 255f + 0.5f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
