package com.hive.hiragana.domain.learning

import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.ReviewCard
import com.hive.hiragana.domain.model.StudySession
import com.hive.hiragana.domain.model.VocabularyWord

/**
 * Builds one [StudySession] from persisted learning state (docs/PHASE3-DESIGN.md
 * §4). Pure and clock-free: every input is passed in, nothing is read from I/O
 * or `System.currentTimeMillis()`, which makes the whole pipeline deterministic
 * and trivially unit-testable.
 *
 * The generator never mutates its inputs or writes anything — the ViewModel
 * session machine persists each step's outcome (rescheduled card, unlock row,
 * practice_history row) as it happens, so re-generating after process death
 * naturally yields exactly the remaining work.
 *
 * @param maxReviewsPerSession   cap on due cards resolved into [StudySession.reviews]
 *                               per session; overflow simply stays due for the
 *                               next session
 * @param newCharBacklogLimit    the due-backlog ceiling for the mastery gate
 *                               (docs/PHASE3-DESIGN.md §4 rule 2a)
 * @param practiceRepetitions    traces required of a newly introduced character;
 *                               surfaced as [StudySession.practiceRepetitions]
 * @param vocabReinforcementCount cap on already-unlocked words resurfaced per
 *                               session for reinforcement practice
 */
class LessonGenerator(
    private val maxReviewsPerSession: Int = 10,
    private val newCharBacklogLimit: Int = 5,
    private val practiceRepetitions: Int = 3,
    private val vocabReinforcementCount: Int = 2,
) {

    /**
     * Generates the next [StudySession] from the current learning state.
     *
     * @param allCharacters       the full deck, already sorted by
     *                            [HiraganaCharacter.frequency] ascending by the
     *                            repository — this function trusts that order
     *                            and does not re-sort
     * @param cards               every persisted [ReviewCard], any order.
     *                            Orphaned cards — whose characterId matches no
     *                            entry in [allCharacters] — are ignored
     *                            entirely: they can never be reviewed, so they
     *                            must not consume review slots, inflate the
     *                            gate's backlog count, or hold gate clause (b)
     *                            closed forever
     * @param allWords            the full vocabulary list, in asset order
     * @param unlockedWordKana    kana of words already unlocked (persisted in
     *                            `vocabulary_progress`)
     * @param wordLastPracticedAt kana -> last-practiced epoch millis; a kana
     *                            absent from the map has never been practiced
     * @param todayStartMillis    epoch millis of the local start-of-day
     */
    fun generate(
        allCharacters: List<HiraganaCharacter>,
        cards: List<ReviewCard>,
        allWords: List<VocabularyWord>,
        unlockedWordKana: Set<String>,
        wordLastPracticedAt: Map<String, Long>,
        todayStartMillis: Long,
    ): StudySession {
        val charactersById = allCharacters.associateBy { it.id }
        val liveCards = cards.filter { it.characterId in charactersById }

        val dueCards = liveCards
            .filter { it.nextReviewDate < todayStartMillis + DAY }
            .sortedWith(compareBy({ it.nextReviewDate }, { it.characterId }))
        val dueBacklogCount = dueCards.size

        val reviews = dueCards
            .take(maxReviewsPerSession)
            .mapNotNull { charactersById[it.characterId] }

        val newCharacter = selectNewCharacter(
            allCharacters = allCharacters,
            cards = liveCards,
            dueBacklogCount = dueBacklogCount,
        )
        val repetitionsForSession = if (newCharacter != null) practiceRepetitions else 0

        val vocabulary = selectVocabulary(
            allCharacters = allCharacters,
            cards = liveCards,
            allWords = allWords,
            unlockedWordKana = unlockedWordKana,
            wordLastPracticedAt = wordLastPracticedAt,
        )

        return StudySession(
            reviews = reviews,
            newCharacter = newCharacter,
            practiceRepetitions = repetitionsForSession,
            vocabulary = vocabulary,
        )
    }

    /**
     * The "prevent advancing before mastery" gate (docs/PHASE3-DESIGN.md §4
     * rule 2). Picks the first (lowest-frequency, since [allCharacters] is
     * already frequency-sorted) character with no [ReviewCard], but only when
     * BOTH hold:
     *  - (a) [dueBacklogCount] — the due count *before* capping — is at most
     *    [newCharBacklogLimit];
     *  - (b) every existing card has `repetitions >= 1`, i.e. the last
     *    introduced character has been successfully recalled at least once.
     *
     * Returns null when the gate is closed or the deck is already fully
     * carded (nothing left to introduce).
     */
    private fun selectNewCharacter(
        allCharacters: List<HiraganaCharacter>,
        cards: List<ReviewCard>,
        dueBacklogCount: Int,
    ): HiraganaCharacter? {
        if (dueBacklogCount > newCharBacklogLimit) return null
        if (cards.any { it.repetitions < 1 }) return null

        val cardedIds = cards.map { it.characterId }.toSet()
        return allCharacters.firstOrNull { it.id !in cardedIds }
    }

    /**
     * Vocabulary selection (docs/PHASE3-DESIGN.md §4 rule 4). Note the
     * id-vs-glyph mapping: [ReviewCard.characterId] is the asset id ("a",
     * "i", …) while [VocabularyWord.requiredCharacters] holds the rendered
     * glyphs ("あ", "い", …), so "learned" is computed by joining cards to
     * characters on id and reading back the glyph.
     *
     * Output is newly-unlocked words (in [allWords] order) followed by up to
     * [vocabReinforcementCount] reinforcement picks from already-unlocked
     * words, most-stale first (absent [wordLastPracticedAt] entries treated
     * as epoch 0, i.e. maximally stale), tie-broken by kana. The two groups
     * are disjoint by construction, so there are never duplicates.
     */
    private fun selectVocabulary(
        allCharacters: List<HiraganaCharacter>,
        cards: List<ReviewCard>,
        allWords: List<VocabularyWord>,
        unlockedWordKana: Set<String>,
        wordLastPracticedAt: Map<String, Long>,
    ): List<VocabularyWord> {
        val learnedCharacterIds = cards.filter { it.repetitions >= 1 }.map { it.characterId }.toSet()
        val learnedGlyphs = allCharacters
            .filter { it.id in learnedCharacterIds }
            .map { it.character }
            .toSet()

        val newlyUnlocked = allWords.filter { word ->
            word.kana !in unlockedWordKana && learnedGlyphs.containsAll(word.requiredCharacters)
        }

        val reinforcement = allWords
            .filter { it.kana in unlockedWordKana }
            .sortedWith(
                compareBy(
                    { wordLastPracticedAt[it.kana] ?: 0L },
                    { it.kana },
                ),
            )
            .take(vocabReinforcementCount)

        return newlyUnlocked + reinforcement
    }

    private companion object {
        /** Millis in one day; matches [SrsScheduler]'s definition. */
        const val DAY = 86_400_000L
    }
}
