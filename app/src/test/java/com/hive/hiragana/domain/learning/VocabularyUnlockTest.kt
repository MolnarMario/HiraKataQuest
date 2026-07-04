package com.hive.hiragana.domain.learning

import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.ReviewCard
import com.hive.hiragana.domain.model.VocabularyWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LessonGenerator]'s vocabulary-unlock and reinforcement
 * selection (docs/PHASE3-DESIGN.md §3–§4 rule 4), including the id-vs-glyph
 * mapping between [ReviewCard.characterId] (asset ids, e.g. "a") and
 * [VocabularyWord.requiredCharacters] (rendered glyphs, e.g. "あ").
 */
class VocabularyUnlockTest {

    companion object {
        private const val TODAY = 1_800_000_000_000L
    }

    private fun character(id: String, glyph: String, frequency: Int) = HiraganaCharacter(
        id = id,
        character = glyph,
        romaji = id,
        frequency = frequency,
        svgPath = "M0,0",
        strokeImage = "$id.png",
        strokes = listOf("M0,0"),
    )

    private fun card(characterId: String, repetitions: Int) = ReviewCard(
        characterId = characterId,
        repetitions = repetitions,
        intervalDays = 1,
        easeFactor = 1.7f,
        nextReviewDate = TODAY + 10_000L, // far from due; irrelevant to vocab selection
    )

    private fun word(kana: String, meaning: String, requiredCharacters: Set<String>) =
        VocabularyWord(kana = kana, meaning = meaning, requiredCharacters = requiredCharacters)

    /** a/i/u/e/o deck, asset ids "a".."o" mapped to glyphs あ/い/う/え/お. */
    private val deck = listOf(
        character("a", "あ", 0),
        character("i", "い", 1),
        character("u", "う", 2),
        character("e", "え", 3),
        character("o", "お", 4),
    )

    private val generator = LessonGenerator()

    // ---- unlock requires ALL glyphs learned, repetitions >= 1 -------------

    @Test
    fun `word unlocks only when every required glyph is learned`() {
        val cards = listOf(card("a", repetitions = 1), card("i", repetitions = 1))
        val words = listOf(word("あい", "love", setOf("あ", "い")))
        val session = generator.generate(
            allCharacters = deck,
            cards = cards,
            allWords = words,
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("あい"), session.vocabulary.map { it.kana })
    }

    @Test
    fun `a repetitions-0 card does not count as learned so the word stays locked`() {
        val cards = listOf(card("a", repetitions = 1), card("i", repetitions = 0))
        val words = listOf(word("あい", "love", setOf("あ", "い")))
        val session = generator.generate(
            allCharacters = deck,
            cards = cards,
            allWords = words,
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertTrue(session.vocabulary.isEmpty())
    }

    @Test
    fun `word requiring a glyph outside the deck stays locked even if all deck chars are learned`() {
        val cards = deck.map { card(it.id, repetitions = 1) } // every deck character learned
        val words = listOf(word("した", "under", setOf("し", "た"))) // し/た not in the deck at all
        val session = generator.generate(
            allCharacters = deck,
            cards = cards,
            allWords = words,
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertTrue(session.vocabulary.isEmpty())
    }

    // ---- id-vs-glyph mapping -------------------------------------------------

    @Test
    fun `cards keyed by asset id correctly unlock words keyed by glyph`() {
        // Cards use "a"/"i" (asset ids); the word requires "あ"/"い" (glyphs). If the
        // generator naively compared characterId to requiredCharacters this would
        // never unlock.
        val cards = listOf(card("a", repetitions = 1), card("i", repetitions = 2))
        val words = listOf(word("あい", "love", setOf("あ", "い")))
        val session = generator.generate(
            allCharacters = deck,
            cards = cards,
            allWords = words,
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("あい"), session.vocabulary.map { it.kana })
    }

    // ---- newly-unlocked vs already-unlocked separation ------------------------

    @Test
    fun `already-unlocked words are never duplicated into the newly-unlocked section`() {
        val cards = listOf(card("a", repetitions = 1), card("i", repetitions = 1))
        val words = listOf(word("あい", "love", setOf("あ", "い")))
        val session = generator.generate(
            allCharacters = deck,
            cards = cards,
            allWords = words,
            unlockedWordKana = setOf("あい"), // already unlocked previously
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        // Appears once, as a reinforcement pick, not twice.
        assertEquals(listOf("あい"), session.vocabulary.map { it.kana })
    }

    @Test
    fun `newly unlocked words are listed before reinforcement words`() {
        val cards = listOf(
            card("a", repetitions = 1),
            card("i", repetitions = 1),
            card("u", repetitions = 1),
        )
        val words = listOf(
            word("いえ", "house", setOf("い", "え")), // え not learned: stays locked, absent
            word("あい", "love", setOf("あ", "い")),   // newly unlockable
            word("あう", "meet", setOf("あ", "う")),   // already unlocked -> reinforcement
        )
        val session = generator.generate(
            allCharacters = deck,
            cards = cards,
            allWords = words,
            unlockedWordKana = setOf("あう"),
            wordLastPracticedAt = mapOf("あう" to 100L),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("あい", "あう"), session.vocabulary.map { it.kana })
    }

    // ---- reinforcement: most-stale first, cap, ignores locked words -----------

    @Test
    fun `reinforcement picks the most-stale word first`() {
        val words = listOf(
            word("あい", "love", setOf("あ", "い")),
            word("あう", "meet", setOf("あ", "う")),
        )
        val session = generator.generate(
            allCharacters = deck,
            cards = emptyList(),
            allWords = words,
            unlockedWordKana = setOf("あい", "あう"),
            wordLastPracticedAt = mapOf("あい" to 500L, "あう" to 100L), // あう is more stale
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("あう", "あい"), session.vocabulary.map { it.kana })
    }

    @Test
    fun `reinforcement treats an absent lastPracticedAt as maximally stale`() {
        val words = listOf(
            word("あい", "love", setOf("あ", "い")),
            word("あう", "meet", setOf("あ", "う")),
        )
        val session = generator.generate(
            allCharacters = deck,
            cards = emptyList(),
            allWords = words,
            unlockedWordKana = setOf("あい", "あう"),
            wordLastPracticedAt = mapOf("あい" to 500L), // あう absent -> treated as 0, most stale
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("あう", "あい"), session.vocabulary.map { it.kana })
    }

    @Test
    fun `reinforcement ties on lastPracticedAt break by kana`() {
        val words = listOf(
            word("うお", "fish", setOf("う", "お")),
            word("あい", "love", setOf("あ", "い")),
        )
        val session = generator.generate(
            allCharacters = deck,
            cards = emptyList(),
            allWords = words,
            unlockedWordKana = setOf("あい", "うお"),
            wordLastPracticedAt = emptyMap(), // both absent -> tie -> break by kana
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("あい", "うお"), session.vocabulary.map { it.kana })
    }

    @Test
    fun `reinforcement respects the configured cap`() {
        val words = listOf(
            word("あい", "love", setOf("あ", "い")),
            word("あう", "meet", setOf("あ", "う")),
            word("いえ", "house", setOf("い", "え")),
        )
        val limitedGenerator = LessonGenerator(vocabReinforcementCount = 1)
        val session = limitedGenerator.generate(
            allCharacters = deck,
            cards = emptyList(),
            allWords = words,
            unlockedWordKana = setOf("あい", "あう", "いえ"),
            wordLastPracticedAt = mapOf("あい" to 300L, "あう" to 100L, "いえ" to 200L),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("あう"), session.vocabulary.map { it.kana })
    }

    @Test
    fun `reinforcement ignores words that are still locked`() {
        // A word could theoretically be in unlockedWordKana's complement and still be
        // "unlockable" this session -- but a locked word not yet in unlockedWordKana
        // and not newly-unlockable must never appear as a reinforcement pick.
        val cards = listOf(card("a", repetitions = 1)) // only "a" learned
        val words = listOf(
            word("あい", "love", setOf("あ", "い")), // い not learned: locked, not unlockable
        )
        val session = generator.generate(
            allCharacters = deck,
            cards = cards,
            allWords = words,
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertFalse(session.vocabulary.map { it.kana }.contains("あい"))
        assertTrue(session.vocabulary.isEmpty())
    }

    // ---- requiredCharacters derivation (VocabularyWord.fromKana) --------------

    @Test
    fun `fromKana derives distinct required characters from repeated glyphs`() {
        // いいえ has three kana but only two distinct characters.
        val word = VocabularyWord.fromKana(kana = "いいえ", meaning = "no")
        assertEquals(setOf("い", "え"), word.requiredCharacters)
        assertEquals("いいえ", word.kana)
        assertEquals("no", word.meaning)
    }

    @Test
    fun `fromKana of a single-character word requires exactly that character`() {
        assertEquals(setOf("あ"), VocabularyWord.fromKana(kana = "あ", meaning = "ah").requiredCharacters)
    }
}
