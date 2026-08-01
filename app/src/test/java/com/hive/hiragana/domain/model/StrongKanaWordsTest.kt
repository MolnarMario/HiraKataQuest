package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StrongKanaWords.select] — the pure rule behind the "generate
 * a set from my strong kana" action.
 */
class StrongKanaWordsTest {

    private fun word(kana: String) = VocabularyWord.fromKana(kana, kana)

    @Test
    fun `keeps only words whose every unit is mastered`() {
        val words = listOf(word("ねこ"), word("いぬ"), word("とり"))
        // Master ね, こ, と, り — but not い or ぬ.
        val mastered = setOf("ね", "こ", "と", "り")
        val result = StrongKanaWords.select(words, mastered).map { it.kana }
        assertEquals(listOf("ねこ", "とり"), result)
    }

    @Test
    fun `excludes a word with a single un-mastered kana`() {
        val words = listOf(word("さかな"))
        val mastered = setOf("さ", "か") // missing な
        assertEquals(emptyList<VocabularyWord>(), StrongKanaWords.select(words, mastered))
    }

    @Test
    fun `treats a yoon as one unit`() {
        // きょう splits into きょ + う, so BOTH must be mastered.
        val words = listOf(word("きょう"))
        assertEquals(
            emptyList<VocabularyWord>(),
            StrongKanaWords.select(words, setOf("き", "ょ", "う")), // き/ょ separately is not the unit
        )
        assertEquals(
            listOf("きょう"),
            StrongKanaWords.select(words, setOf("きょ", "う")).map { it.kana },
        )
    }

    @Test
    fun `empty mastered set selects nothing`() {
        assertEquals(
            emptyList<VocabularyWord>(),
            StrongKanaWords.select(listOf(word("ねこ")), emptySet()),
        )
    }

    @Test
    fun `preserves input order`() {
        val words = listOf(word("こ"), word("ね"), word("こね"))
        val result = StrongKanaWords.select(words, setOf("こ", "ね")).map { it.kana }
        assertEquals(listOf("こ", "ね", "こね"), result)
    }
}
