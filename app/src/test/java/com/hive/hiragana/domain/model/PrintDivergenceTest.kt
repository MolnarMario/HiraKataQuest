package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [PrintDivergence] — the editorial "this kana looks different
 * in print vs handwriting" lookup that the badge/comparison UI keys off. Pins
 * the inheritance rules (voiced + composite via NFD) and the deliberate
 * exclusions (katakana, the chart's milder cases).
 */
class PrintDivergenceTest {

    @Test
    fun `the divergent base kana all carry a note`() {
        listOf("き", "さ", "り").forEach {
            assertNotNull("$it should have a divergence note", PrintDivergence.noteFor(it))
        }
    }

    @Test
    fun `unaffected base kana carry no note`() {
        // The textbook chart's milder cases (ふ, そ, ぬ, め …) are deliberately
        // absent — per user decision their forms are close enough; さ/き/り
        // are the only truly divergent trio.
        listOf("あ", "か", "し", "そ", "ふ", "ぬ", "め", "ん", "を", "ゆ", "ら").forEach {
            assertNull("$it should have no note", PrintDivergence.noteFor(it))
        }
    }

    @Test
    fun `voiced variants inherit the base kana's note`() {
        assertEquals(PrintDivergence.noteFor("き"), PrintDivergence.noteFor("ぎ"))
        assertEquals(PrintDivergence.noteFor("さ"), PrintDivergence.noteFor("ざ"))
        assertNull(PrintDivergence.noteFor("ぶ")) // ふ is not divergent
    }

    @Test
    fun `yoon composites inherit the base kana's note`() {
        assertEquals(PrintDivergence.noteFor("き"), PrintDivergence.noteFor("きゃ"))
        assertEquals(PrintDivergence.noteFor("き"), PrintDivergence.noteFor("ぎょ"))
        assertEquals(PrintDivergence.noteFor("り"), PrintDivergence.noteFor("りゅ"))
        assertNull(PrintDivergence.noteFor("しゅ")) // し is not divergent
    }

    @Test
    fun `katakana never matches — its print and handwritten forms agree`() {
        listOf("キ", "サ", "リ", "ギ", "キャ").forEach {
            assertNull("$it should have no note", PrintDivergence.noteFor(it))
        }
    }

}
