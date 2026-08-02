package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [KanjiUsage.fromJson] decode + labels. */
class KanjiUsageTest {

    @Test
    fun `fromJson decodes the three known values`() {
        assertEquals(KanjiUsage.USUALLY_KANJI, KanjiUsage.fromJson("usually_kanji"))
        assertEquals(KanjiUsage.MIXED, KanjiUsage.fromJson("mixed"))
        assertEquals(KanjiUsage.USUALLY_KANA, KanjiUsage.fromJson("usually_kana"))
    }

    @Test
    fun `fromJson is lenient about blanks and unknowns`() {
        assertNull(KanjiUsage.fromJson(null))
        assertNull(KanjiUsage.fromJson(""))
        assertNull(KanjiUsage.fromJson("sometimes"))
    }

    @Test
    fun `fromJson trims surrounding whitespace`() {
        assertEquals(KanjiUsage.MIXED, KanjiUsage.fromJson("  mixed  "))
    }

    @Test
    fun `each value has a human label`() {
        KanjiUsage.entries.forEach { assert(it.label.isNotBlank()) }
    }
}
