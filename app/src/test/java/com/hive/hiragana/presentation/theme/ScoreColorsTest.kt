package com.hive.hiragana.presentation.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM checks for the dark-appearance score spectrum. Compose [Color]
 * and [luminance] are self-contained Kotlin math (no android.jar), so these
 * run under `testDebugUnitTest` without Robolectric.
 */
class ScoreColorsTest {

    private val scores = listOf(0, 40, 60, 80, 100)

    @Test
    fun `default is the light spectrum`() {
        for (s in scores) {
            assertEquals(scoreSpectrumColor(s), scoreSpectrumColor(s, dark = false))
        }
    }

    @Test
    fun `dark differs from light at every anchor`() {
        for (s in scores) {
            assertNotEquals(scoreSpectrumColor(s, dark = false), scoreSpectrumColor(s, dark = true))
        }
    }

    @Test
    fun `score clamps outside 0 to 100`() {
        for (dark in listOf(false, true)) {
            assertEquals(scoreSpectrumColor(0, dark), scoreSpectrumColor(-5, dark))
            assertEquals(scoreSpectrumColor(100, dark), scoreSpectrumColor(150, dark))
        }
    }

    /**
     * The whole point of the dark twin: its tints are dark enough that the dark
     * scheme's LIGHT on-color text stays legible, while the light tints stay
     * light for dark text. Assert both the absolute band and the ordering.
     */
    @Test
    fun `dark tints are low-luminance and lighter tints are high-luminance`() {
        for (s in scores) {
            val darkLum = scoreSpectrumColor(s, dark = true).luminance()
            val lightLum = scoreSpectrumColor(s, dark = false).luminance()
            assertTrue("dark tint at $s should be dim (was $darkLum)", darkLum < 0.25f)
            assertTrue("light tint at $s should be bright (was $lightLum)", lightLum > 0.4f)
            assertTrue("dark < light at $s", darkLum < lightLum)
        }
    }
}
