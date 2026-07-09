package com.hive.hiragana.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiMatchTest {

    @Test
    fun `exact match`() {
        assertTrue(RomajiMatch.matches("ka", "ka"))
    }

    @Test
    fun `case and whitespace insensitive`() {
        assertTrue(RomajiMatch.matches("  KA ", "ka"))
        // the deck stores word readings space-joined per kana
        assertTrue(RomajiMatch.matches("kasa", "ka sa"))
        assertTrue(RomajiMatch.matches("ka sa", "ka sa"))
    }

    @Test
    fun `hepburn and kunrei spellings are equivalent`() {
        assertTrue(RomajiMatch.matches("shi", "si"))
        assertTrue(RomajiMatch.matches("si", "shi"))
        assertTrue(RomajiMatch.matches("chi", "ti"))
        assertTrue(RomajiMatch.matches("tsu", "tu"))
        assertTrue(RomajiMatch.matches("fu", "hu"))
        assertTrue(RomajiMatch.matches("ji", "zi"))
    }

    @Test
    fun `yoon digraphs fold consistently`() {
        assertTrue(RomajiMatch.matches("sha", "sya"))
        assertTrue(RomajiMatch.matches("cha", "tya"))
        assertTrue(RomajiMatch.matches("ju", "zyu"))
    }

    @Test
    fun `macrons are stripped to base vowels`() {
        assertTrue(RomajiMatch.matches("tōkyō", "toukyou".let { "tokyo" })) // tōkyō vs tokyo
        assertTrue(RomajiMatch.matches("rāmen", "ramen"))
    }

    @Test
    fun `wrong reading does not match`() {
        assertFalse(RomajiMatch.matches("ka", "sa"))
        assertFalse(RomajiMatch.matches("neko", "inu"))
    }

    @Test
    fun `blank input never matches`() {
        assertFalse(RomajiMatch.matches("", "ka"))
        assertFalse(RomajiMatch.matches("   ", "ka"))
    }
}
