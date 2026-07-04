package com.hive.hiragana.domain.model

import com.hive.hiragana.domain.learning.LessonGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ScriptScope] — the pure filter that scopes the merged
 * multi-script deck down to the active script — and for the end-to-end
 * guarantee it exists to provide: feeding a script-scoped deck into
 * [LessonGenerator] confines a session to that script while the other
 * script's review cards stay inert.
 *
 * This is the behaviour the ViewModel relies on for the katakana section, so
 * it is tested here directly rather than only through the (untested) ViewModel.
 */
class ScriptScopeTest {

    companion object {
        private const val TODAY = 1_800_000_000_000L
        private const val DAY = 86_400_000L
    }

    private fun character(id: String, glyph: String, frequency: Int, script: Script) =
        HiraganaCharacter(
            id = id,
            character = glyph,
            romaji = id,
            frequency = frequency,
            svgPath = "M0,0",
            strokeImage = "$id.png",
            strokes = listOf("M0,0"),
            script = script,
        )

    private fun card(characterId: String, repetitions: Int, nextReviewDate: Long) = ReviewCard(
        characterId = characterId,
        repetitions = repetitions,
        intervalDays = 1,
        easeFactor = 1.7f,
        nextReviewDate = nextReviewDate,
    )

    /** A merged deck: hiragana あ/い plus katakana ア/イ, as the real app loads it. */
    private val mergedDeck = listOf(
        character("a", "あ", 1, Script.HIRAGANA),
        character("i", "い", 2, Script.HIRAGANA),
        character("kata_a", "ア", 101, Script.KATAKANA),
        character("kata_i", "イ", 102, Script.KATAKANA),
    )

    // ---- pure filtering -------------------------------------------------------

    @Test
    fun `scopedCharacters keeps only the requested script`() {
        assertEquals(
            listOf("a", "i"),
            ScriptScope.scopedCharacters(mergedDeck, Script.HIRAGANA, includeDiacritics = true, includeYoon = true, includeExtended = true).map { it.id },
        )
        assertEquals(
            listOf("kata_a", "kata_i"),
            ScriptScope.scopedCharacters(mergedDeck, Script.KATAKANA, includeDiacritics = true, includeYoon = true, includeExtended = true).map { it.id },
        )
    }

    @Test
    fun `scopedWords keeps only the requested script, using the derived word script`() {
        val words = listOf(
            VocabularyWord.fromKana("あい", "love"),      // hiragana
            VocabularyWord.fromKana("アイ", "eye/love"),  // katakana
            VocabularyWord.fromKana("コーヒー", "coffee"), // katakana (incl. ー)
        )
        assertEquals(
            listOf("あい"),
            ScriptScope.scopedWords(words, Script.HIRAGANA, includeDiacritics = true, includeYoon = true, includeExtended = true).map { it.kana },
        )
        assertEquals(
            listOf("アイ", "コーヒー"),
            ScriptScope.scopedWords(words, Script.KATAKANA, includeDiacritics = true, includeYoon = true, includeExtended = true).map { it.kana },
        )
    }

    // ---- end-to-end scoping through the generator -----------------------------

    @Test
    fun `a katakana-scoped session ignores hiragana cards and introduces katakana`() {
        // Progress exists for BOTH scripts, but with Katakana active the deck
        // is scoped to katakana; the hiragana cards become orphaned and inert
        // (no reviews), and the first katakana character is introduced.
        val cards = listOf(
            card("a", repetitions = 1, nextReviewDate = TODAY - DAY),      // hiragana, due
            card("i", repetitions = 1, nextReviewDate = TODAY - 2 * DAY),  // hiragana, due
        )
        val scoped = ScriptScope.scopedCharacters(mergedDeck, Script.KATAKANA, includeDiacritics = true, includeYoon = true, includeExtended = true)

        val session = LessonGenerator().generate(
            allCharacters = scoped,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )

        assertTrue("hiragana cards must not surface as katakana reviews", session.reviews.isEmpty())
        assertEquals("kata_a", session.newCharacter?.id)
    }

    @Test
    fun `switching scope back to hiragana resurfaces its due reviews unchanged`() {
        // The same cards that were inert under katakana scope are live again
        // under hiragana scope — proving scoping only hides, never mutates.
        val cards = listOf(
            card("a", repetitions = 1, nextReviewDate = TODAY - DAY),
            card("i", repetitions = 1, nextReviewDate = TODAY - 2 * DAY),
        )
        val scoped = ScriptScope.scopedCharacters(mergedDeck, Script.HIRAGANA, includeDiacritics = true, includeYoon = true, includeExtended = true)

        val session = LessonGenerator().generate(
            allCharacters = scoped,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )

        assertEquals(listOf("i", "a"), session.reviews.map { it.id }) // most-overdue first
    }

    // ---- voiced-kana (dakuten/handakuten) opt-in gate -------------------------

    /** Hiragana deck with base か plus voiced が and semi-voiced ぱ. */
    private val diacriticDeck = listOf(
        character("ka", "か", 6, Script.HIRAGANA),
        character("ga", "が", 47, Script.HIRAGANA),   // dakuten
        character("pa", "ぱ", 67, Script.HIRAGANA),   // handakuten
    )

    @Test
    fun `voiced kana are excluded from the study deck by default`() {
        assertEquals(
            listOf("ka"),
            ScriptScope.scopedCharacters(diacriticDeck, Script.HIRAGANA, includeDiacritics = false, includeYoon = false, includeExtended = false)
                .map { it.id },
        )
    }

    @Test
    fun `voiced kana join the study deck when opted in`() {
        assertEquals(
            listOf("ka", "ga", "pa"),
            ScriptScope.scopedCharacters(diacriticDeck, Script.HIRAGANA, includeDiacritics = true, includeYoon = true, includeExtended = true)
                .map { it.id },
        )
    }

    @Test
    fun `words needing voiced kana are gated by the opt-in`() {
        val words = listOf(
            VocabularyWord.fromKana("かお", "face"),   // base only
            VocabularyWord.fromKana("かぎ", "key"),    // needs ぎ (dakuten)
        )
        assertEquals(
            listOf("かお"),
            ScriptScope.scopedWords(words, Script.HIRAGANA, includeDiacritics = false, includeYoon = false, includeExtended = false).map { it.kana },
        )
        assertEquals(
            listOf("かお", "かぎ"),
            ScriptScope.scopedWords(words, Script.HIRAGANA, includeDiacritics = true, includeYoon = true, includeExtended = true).map { it.kana },
        )
    }

    @Test
    fun `a voiced kana studied then hidden goes inert, not lost`() {
        // Opt in, learn が (card exists), then opt back out: the が card
        // becomes an off-scope orphan (no review), exactly like a cross-script
        // card — progress is preserved for when the learner re-enables.
        val cards = listOf(card("ga", repetitions = 1, nextReviewDate = TODAY - DAY))
        val hidden = ScriptScope.scopedCharacters(diacriticDeck, Script.HIRAGANA, includeDiacritics = false, includeYoon = false, includeExtended = false)

        val session = LessonGenerator().generate(
            allCharacters = hidden,
            cards = cards,
            allWords = emptyList(),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )

        assertTrue("hidden voiced-kana card must not surface as a review", session.reviews.isEmpty())
        assertEquals("ka", session.newCharacter?.id) // introduces the base char instead
    }

    // ---- yōon opt-in gate (independent of the voiced gate) --------------------

    /** Hiragana deck: base か, voiced が, and yōon きゃ (base) + ぎゃ (voiced base). */
    private val yoonDeck = listOf(
        character("ka", "か", 6, Script.HIRAGANA),
        character("ga", "が", 47, Script.HIRAGANA),   // dakuten (voiced)
        character("kya", "きゃ", 72, Script.HIRAGANA), // yōon (base consonant)
        character("gya", "ぎゃ", 90, Script.HIRAGANA), // yōon on a voiced base
    )

    @Test
    fun `yoon are excluded by default even when voiced kana are on`() {
        // Enabling voiced kana must NOT pull in yōon — they are independent.
        assertEquals(
            listOf("ka", "ga"),
            ScriptScope.scopedCharacters(yoonDeck, Script.HIRAGANA, includeDiacritics = true, includeYoon = false, includeExtended = false)
                .map { it.id },
        )
    }

    @Test
    fun `enabling yoon includes both plain and voiced-base yoon, regardless of the voiced opt-in`() {
        // includeYoon governs ALL yōon (including ぎゃ) on its own; voiced-kana
        // off must still surface ぎゃ because a yōon is gated only by includeYoon.
        assertEquals(
            listOf("ka", "kya", "gya"),
            ScriptScope.scopedCharacters(yoonDeck, Script.HIRAGANA, includeDiacritics = false, includeYoon = true, includeExtended = true)
                .map { it.id },
        )
    }

    @Test
    fun `yoon words unlock against a yoon deck character and are gated by includeYoon`() {
        // きょう requires the unit "きょ" (not き + ょ) — matches a yōon deck char.
        val deck = listOf(
            character("ki", "き", 7, Script.HIRAGANA),
            character("u", "う", 3, Script.HIRAGANA),
            character("kyo", "きょ", 74, Script.HIRAGANA),
        )
        val word = VocabularyWord.fromKana("きょう", "today")
        assertEquals(setOf("きょ", "う"), word.requiredCharacters)

        // Off: the yōon word is hidden.
        assertTrue(
            ScriptScope.scopedWords(listOf(word), Script.HIRAGANA, includeDiacritics = false, includeYoon = false, includeExtended = false)
                .isEmpty(),
        )
        // On: it's in scope, and unlocks once きょ + う are learned.
        val cards = listOf(
            card("kyo", repetitions = 1, nextReviewDate = TODAY + DAY),
            card("u", repetitions = 1, nextReviewDate = TODAY + DAY),
        )
        val session = LessonGenerator().generate(
            allCharacters = ScriptScope.scopedCharacters(deck, Script.HIRAGANA, includeDiacritics = false, includeYoon = true, includeExtended = true),
            cards = cards,
            allWords = ScriptScope.scopedWords(listOf(word), Script.HIRAGANA, includeDiacritics = false, includeYoon = true, includeExtended = true),
            unlockedWordKana = emptySet(),
            wordLastPracticedAt = emptyMap(),
            todayStartMillis = TODAY,
        )
        assertEquals(listOf("きょう"), session.vocabulary.map { it.kana })
    }

    // ---- extended-kana opt-in gate (independent of voiced + yōon) -------------

    /** Katakana deck: base フ, foreign combo ファ, small tsu ッ, plus ぎゃ (yōon). */
    private val extendedDeck = listOf(
        character("kata_fu", "フ", 128, Script.KATAKANA),
        character("kata_fu_a", "ファ", 215, Script.KATAKANA),  // extended (small vowel)
        character("kata_sokuon", "ッ", 206, Script.KATAKANA),  // extended (small tsu)
        character("kata_gya", "ギャ", 179, Script.KATAKANA),   // yōon
    )

    @Test
    fun `extended kana are excluded even when voiced and yoon are on`() {
        // The three opt-ins are independent: enabling voiced + yōon must NOT
        // pull in extended kana (ファ, ッ).
        assertEquals(
            listOf("kata_fu", "kata_gya"),
            ScriptScope.scopedCharacters(
                extendedDeck, Script.KATAKANA,
                includeDiacritics = true, includeYoon = true, includeExtended = false,
            ).map { it.id },
        )
    }

    @Test
    fun `enabling extended includes small-vowel combos and small tsu`() {
        assertEquals(
            listOf("kata_fu", "kata_fu_a", "kata_sokuon"),
            ScriptScope.scopedCharacters(
                extendedDeck, Script.KATAKANA,
                includeDiacritics = false, includeYoon = false, includeExtended = true,
            ).map { it.id },
        )
    }

    @Test
    fun `an extended word unlocks against composite + small-tsu deck chars`() {
        // ソファ needs the unit "ファ" (フ+small ァ); きって needs the small tsu っ.
        assertEquals(setOf("ソ", "ファ"), VocabularyWord.fromKana("ソファ", "sofa").requiredCharacters)
        val word = VocabularyWord.fromKana("ソファ", "sofa")
        assertTrue(word.usesExtended)
        // Gated off by default, in scope only when extended is enabled.
        assertTrue(
            ScriptScope.scopedWords(
                listOf(word), Script.KATAKANA,
                includeDiacritics = true, includeYoon = true, includeExtended = false,
            ).isEmpty(),
        )
        assertEquals(
            listOf("ソファ"),
            ScriptScope.scopedWords(
                listOf(word), Script.KATAKANA,
                includeDiacritics = false, includeYoon = false, includeExtended = true,
            ).map { it.kana },
        )
    }
}
