package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the informative-only [VocabularyWord.kanji] field (JLPT N5
 * vocabulary import): it is optional, defaults to null, and rides through
 * [VocabularyWord.fromKana] alongside the derived reading units. It never
 * changes [VocabularyWord.requiredCharacters] (the app studies kana only).
 */
class VocabularyWordTest {

    @Test
    fun `fromKana carries the kanji spelling when given`() {
        val word = VocabularyWord.fromKana("かのじょ", "she", "彼女")
        assertEquals("彼女", word.kanji)
        assertEquals("she", word.meaning)
    }

    @Test
    fun `fromKana defaults kanji to null`() {
        val word = VocabularyWord.fromKana("あそこ", "over there")
        assertNull(word.kanji)
    }

    @Test
    fun `fromKana carries category and kanji usage`() {
        val word = VocabularyWord.fromKana(
            "かのじょ", "she", "彼女",
            category = "pronouns",
            kanjiUsage = KanjiUsage.USUALLY_KANJI,
        )
        assertEquals("pronouns", word.category)
        assertEquals(KanjiUsage.USUALLY_KANJI, word.kanjiUsage)
    }

    @Test
    fun `fromKana defaults category and kanji usage to null`() {
        val word = VocabularyWord.fromKana("あそこ", "over there")
        assertNull(word.category)
        assertNull(word.kanjiUsage)
    }

    @Test
    fun `fromKana carries homophone alternate kanji forms`() {
        val word = VocabularyWord.fromKana(
            "はな", "flower", "花",
            kanjiUsage = KanjiUsage.USUALLY_KANJI,
            otherKanjiForms = listOf(KanjiForm("鼻", "nose", KanjiUsage.USUALLY_KANJI)),
        )
        assertEquals("花", word.kanji)
        assertEquals(1, word.otherKanjiForms.size)
        assertEquals("鼻", word.otherKanjiForms.first().kanji)
        assertEquals("nose", word.otherKanjiForms.first().meaning)
    }

    @Test
    fun `fromKana defaults other kanji forms to empty`() {
        val word = VocabularyWord.fromKana("ねこ", "cat")
        assertEquals(emptyList<KanjiForm>(), word.otherKanjiForms)
    }

    @Test
    fun `kanji does not affect the derived reading units`() {
        val withKanji = VocabularyWord.fromKana("きょう", "today", "今日")
        val withoutKanji = VocabularyWord.fromKana("きょう", "today")
        // きょう splits into the yōon unit きょ + う regardless of the kanji.
        assertEquals(setOf("きょ", "う"), withKanji.requiredCharacters)
        assertEquals(withoutKanji.requiredCharacters, withKanji.requiredCharacters)
    }
}
