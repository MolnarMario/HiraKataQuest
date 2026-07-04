package com.hive.hiragana.domain.model

/**
 * One generated study session (docs/PHASE3-DESIGN.md §4) — the output of
 * LessonGenerator, consumed by the session state machine in the ViewModel.
 *
 * The session itself is never persisted: every completed step writes its
 * own durable state (rescheduled card, unlock row, practice_history row),
 * so regenerating after process death yields exactly the remaining work.
 *
 * @param reviews             due characters, most overdue first, capped
 * @param newCharacter        next character to introduce, or null when the
 *                            mastery gate is closed or the deck is exhausted
 * @param practiceRepetitions traces required of [newCharacter]
 * @param vocabulary          words to trace: newly unlocked first, then
 *                            least-recently-practiced reinforcement picks
 */
data class StudySession(
    val reviews: List<HiraganaCharacter>,
    val newCharacter: HiraganaCharacter?,
    val practiceRepetitions: Int,
    val vocabulary: List<VocabularyWord>,
) {
    val isEmpty: Boolean
        get() = reviews.isEmpty() && newCharacter == null && vocabulary.isEmpty()
}
