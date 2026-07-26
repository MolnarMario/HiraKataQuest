package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeTest {

    /** LIGHT (not SYSTEM) is the fallback — existing installs stay light. */
    @Test
    fun `unknown or null prefValue falls back to LIGHT`() {
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromPrefValue(null))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromPrefValue("nonsense"))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromPrefValue(""))
    }

    @Test
    fun `every mode round-trips through its prefValue`() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromPrefValue(mode.prefValue))
        }
    }

    @Test
    fun `prefValues are unique and non-blank`() {
        val values = AppearanceMode.entries.map { it.prefValue }
        assertEquals(values.size, values.toSet().size)
        assertTrue(values.all { it.isNotBlank() })
    }

    /** The segmented control iterates entries, so the order is load-bearing. */
    @Test
    fun `segmented-control order is System, Light, Dark`() {
        assertEquals(
            listOf(AppearanceMode.SYSTEM, AppearanceMode.LIGHT, AppearanceMode.DARK),
            AppearanceMode.entries.toList(),
        )
    }
}
