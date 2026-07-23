package com.hive.hiragana.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Leniency contract for the flashcard typed-meaning matcher. */
class MeaningMatchTest {

    @Test
    fun `exact match`() {
        assertTrue(MeaningMatch.matches("house", "house"))
    }

    @Test
    fun `case and surrounding whitespace ignored`() {
        assertTrue(MeaningMatch.matches("  House ", "house"))
    }

    @Test
    fun `any listed sense is accepted`() {
        assertTrue(MeaningMatch.matches("up", "up; above"))
        assertTrue(MeaningMatch.matches("above", "up; above"))
    }

    @Test
    fun `comma-separated senses are accepted`() {
        assertTrue(MeaningMatch.matches("blue", "blue, green"))
        assertTrue(MeaningMatch.matches("green", "blue, green"))
    }

    @Test
    fun `leading to is optional for verbs`() {
        assertTrue(MeaningMatch.matches("buy", "to buy"))
        assertTrue(MeaningMatch.matches("to buy", "to buy"))
    }

    @Test
    fun `leading articles are ignored`() {
        assertTrue(MeaningMatch.matches("house", "a house"))
        assertTrue(MeaningMatch.matches("apple", "an apple"))
    }

    @Test
    fun `parenthetical notes are ignored`() {
        assertTrue(MeaningMatch.matches("hey", "hey! (calling out)"))
    }

    @Test
    fun `wrong meaning is rejected`() {
        assertFalse(MeaningMatch.matches("cat", "house"))
    }

    @Test
    fun `blank never matches`() {
        assertFalse(MeaningMatch.matches("", "house"))
        assertFalse(MeaningMatch.matches("   ", "house"))
    }

    @Test
    fun `a whole sense, not a substring`() {
        assertFalse(MeaningMatch.matches("house", "warehouse"))
    }
}
