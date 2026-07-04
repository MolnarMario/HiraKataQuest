package com.hive.hiragana.domain.learning

import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.ReviewCard
import com.hive.hiragana.domain.model.VocabularyWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LessonGenerator]'s review selection and mastery-gate rules
 * (docs/PHASE3-DESIGN.md §4 rules 1–3). Vocabulary selection is covered
 * separately in [VocabularyUnlockTest].
 *
 * All tests use a fixed [TODAY] instead of a real clock, per the generator's
 * pure/clock-free contract.
 */
class LessonGeneratorTest {

    companion object {
        /** An arbitrary but fixed local-start-of-day, epoch millis. */
        private const val TODAY = 1_800_000_000_000L
        private const val DAY = 86_400_000L
    }

    // ---- fixture helpers -------------------------------------------------

    private fun character(id: String, glyph: String, frequency: Int) = HiraganaCharacter(
        id = id,
        character = glyph,
        romaji = id,
        frequency = frequency,
        svgPath = "M0,0",
        strokeImage = "$id.png",
        strokes = listOf("M0,0"),
    )

    private fun card(
        characterId: String,
        repetitions: Int = 1,
        intervalDays: Int = 1,
        easeFactor: Float = 1.7f,
        nextReviewDate: Long = TODAY,
    ) = ReviewCard(
        characterId = characterId,
        repetitions = repetitions,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        nextReviewDate = nextReviewDate,
    )

    /** Five frequency-sorted characters, a/i/u/e/o, mirroring the real deck. */
    private val deck = listOf(
        character("a", "あ", 0),
        character("i", "い", 1),
        character("u", "う", 2),
        character("e", "え", 3),
        character("o", "お", 4),
    )

    private fun generator(
        maxReviewsPerSession: Int = 10,
        newCharBacklogLimit: Int = 5,
        practiceRepetitions: Int = 3,
        vocabReinforcementCount: Int = 2,
    ) = LessonGenerator(
        maxReviewsPerSession = maxReviewsPerSession,
        newCharBacklogLimit = newCharBacklogLimit,
        practiceRepetitions = practiceRepetitions,
        vocabReinforcementCount = vocabReinforcementCount,
    )

    // ---- due filtering + ordering + cap -----------------------------------

    @Test
    fun `due cards are those with nextReviewDate before today plus one day`() {
        val notYetDue = card("e", nextReviewDate = TODAY + DAY) // exactly today+DAY: NOT due
        val due = card("a", nextReviewDate = TODAY - DAY)
        val session = generator().generate(
            allCharacters = deck,
            cards = listOf(notYetDue, due),
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("a"), session.reviews.map { it.id })
    }

    @Test
    fun `due cards are ordered most-overdue first`() {
        val cards = listOf(
            card("u", nextReviewDate = TODAY - DAY),       // 1 day overdue
            card("a", nextReviewDate = TODAY - 3 * DAY),    // 3 days overdue -- most overdue
            card("i", nextReviewDate = TODAY - 2 * DAY),    // 2 days overdue
        )
        val session = generator().generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("a", "i", "u"), session.reviews.map { it.id })
    }

    @Test
    fun `ties in nextReviewDate break by characterId for determinism`() {
        val cards = listOf(
            card("u", nextReviewDate = TODAY - DAY),
            card("a", nextReviewDate = TODAY - DAY),
            card("i", nextReviewDate = TODAY - DAY),
        )
        val session = generator().generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("a", "i", "u"), session.reviews.map { it.id })
    }

    @Test
    fun `overflow beyond the cap is dropped, keeping the most overdue`() {
        val cards = listOf(
            card("a", nextReviewDate = TODAY - 5 * DAY),
            card("i", nextReviewDate = TODAY - 4 * DAY),
            card("u", nextReviewDate = TODAY - 3 * DAY),
            card("e", nextReviewDate = TODAY - 2 * DAY),
            card("o", nextReviewDate = TODAY - DAY),
        )
        val session = generator(maxReviewsPerSession = 2).generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("a", "i"), session.reviews.map { it.id })
    }

    @Test
    fun `card with unknown characterId is skipped without crashing`() {
        val cards = listOf(
            card("a", nextReviewDate = TODAY - DAY),
            card("ghost", nextReviewDate = TODAY - DAY),
        )
        val session = generator().generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("a"), session.reviews.map { it.id })
    }

    // ---- mastery gate ------------------------------------------------------

    @Test
    fun `gate clause a blocks new character when due backlog exceeds the limit`() {
        // 6 due cards on real deck characters (orphaned cards don't count — see the
        // orphan tests below), limit 5: gate closed even though "ki" remains uncarded.
        val sevenCharDeck = deck + character("ka", "か", 5) + character("ki", "き", 6)
        val cards = listOf("a", "i", "u", "e", "o", "ka").map {
            card(it, repetitions = 1, nextReviewDate = TODAY - DAY)
        }
        val session = generator(newCharBacklogLimit = 5).generate(
            allCharacters = sevenCharDeck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertNull(session.newCharacter)
    }

    @Test
    fun `gate clause b blocks new character when any existing card is unrecalled`() {
        val cards = listOf(
            card("a", repetitions = 1),
            card("i", repetitions = 0), // never successfully recalled
        )
        val session = generator().generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertNull(session.newCharacter)
    }

    @Test
    fun `both gate clauses passing selects the lowest-frequency uncarded character`() {
        val cards = listOf(
            card("a", repetitions = 1, nextReviewDate = TODAY - DAY), // 1 due card, under limit; recalled
        )
        val session = generator(newCharBacklogLimit = 5).generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertNotNull(session.newCharacter)
        assertEquals("i", session.newCharacter!!.id) // next-lowest-frequency after "a"
    }

    @Test
    fun `deck exhausted means every character is carded so newCharacter is null`() {
        val cards = deck.map { card(it.id, repetitions = 1, nextReviewDate = TODAY - DAY) }
        val session = generator().generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertNull(session.newCharacter)
    }

    // ---- practiceRepetitions -----------------------------------------------

    @Test
    fun `practiceRepetitions is the configured value when a new character is chosen`() {
        val session = generator(practiceRepetitions = 3).generate(
            allCharacters = deck,
            cards = emptyList(),
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertNotNull(session.newCharacter)
        assertEquals(3, session.practiceRepetitions)
    }

    @Test
    fun `practiceRepetitions is zero when no new character is chosen`() {
        val cards = deck.map { card(it.id, repetitions = 1, nextReviewDate = TODAY - DAY) }
        val session = generator(practiceRepetitions = 3).generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertNull(session.newCharacter)
        assertEquals(0, session.practiceRepetitions)
    }

    // ---- empty session ------------------------------------------------------

    @Test
    fun `empty everything yields an empty StudySession`() {
        val session = generator().generate(
            allCharacters = emptyList(),
            cards = emptyList(),
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertTrue(session.isEmpty)
    }

    @Test
    fun `vocabulary-only inputs do not spuriously introduce a new character`() {
        // No characters at all means there's nothing to introduce and nothing to review,
        // but this still exercises that allWords/unlockedWordKana alone don't crash generate().
        val words = listOf(VocabularyWord(kana = "あい", meaning = "love", requiredCharacters = setOf("あ", "い")))
        val session = generator().generate(
            allCharacters = emptyList(),
            cards = emptyList(),
            allWords = words,
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertNull(session.newCharacter)
        assertTrue(session.reviews.isEmpty())
    }

    // ---- gate boundaries + backlog-vs-cap ordering ---------------------------

    @Test
    fun `backlog exactly at the limit keeps the gate open`() {
        // 5 due cards, limit 5: <= limit means open (docs/PHASE3-DESIGN.md §4 rule 2a).
        val cards = listOf("a", "i", "u", "e").map {
            card(it, repetitions = 1, nextReviewDate = TODAY - DAY)
        } + card("o", repetitions = 1, nextReviewDate = TODAY - DAY)
        val fullDeck = deck + character("ka", "か", 5)
        val session = generator(newCharBacklogLimit = 5).generate(
            allCharacters = fullDeck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals("ka", session.newCharacter?.id)
    }

    @Test
    fun `gate counts the backlog before the review cap is applied`() {
        // 4 due cards but a cap of 2: an implementation that counted the capped
        // list (2 <= 3) would open the gate; the pre-cap backlog (4 > 3) must close it.
        val cards = listOf("a", "i", "u", "e").map {
            card(it, repetitions = 1, nextReviewDate = TODAY - DAY)
        }
        val session = generator(maxReviewsPerSession = 2, newCharBacklogLimit = 3).generate(
            allCharacters = deck,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(2, session.reviews.size)
        assertNull(session.newCharacter)
    }

    // ---- orphaned cards -------------------------------------------------------

    @Test
    fun `orphaned cards do not consume review slots or count toward the backlog`() {
        // 6 orphans (limit 5) + 1 real due card, cap 1: orphans must neither
        // fill the single review slot nor push the backlog over the gate limit.
        val orphans = (1..6).map {
            card("gone-$it", repetitions = 1, nextReviewDate = TODAY - 10 * DAY)
        }
        val real = card("a", repetitions = 1, nextReviewDate = TODAY - DAY)
        val session = generator(maxReviewsPerSession = 1, newCharBacklogLimit = 5).generate(
            allCharacters = deck,
            cards = orphans + real,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("a"), session.reviews.map { it.id })
        assertEquals("i", session.newCharacter?.id)
    }

    @Test
    fun `an orphaned repetitions-zero card cannot hold the gate closed`() {
        val orphan = card("gone", repetitions = 0, nextReviewDate = TODAY + 30 * DAY)
        val session = generator().generate(
            allCharacters = deck,
            cards = listOf(orphan),
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals("a", session.newCharacter?.id)
    }
}
