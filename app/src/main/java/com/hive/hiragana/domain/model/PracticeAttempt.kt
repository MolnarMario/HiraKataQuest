package com.hive.hiragana.domain.model

/**
 * One graded tracing attempt, persisted to the `practice_history` table.
 *
 * @param source which session step produced the attempt; rows written
 *               before Phase 3 default to [PracticeSource.FREE]
 * @param formScore        handwriting-quality FORM dial (0..100), or null
 *                         when scoring was off/unavailable or the attempt
 *                         was a whole word (words aren't per-kana scored)
 * @param fitScore         handwriting-quality FIT dial (0..100), or null
 * @param recognitionScore ML Kit legibility score (0..100), or null when
 *                         the recognizer didn't run
 * @param headlineScore    the number the learner was actually SHOWN for this
 *                         attempt (the dials combined under the active
 *                         ScoringMode, with any legibility discount applied),
 *                         so the Library reports the same figure as the
 *                         feedback card. Null on rows written before the
 *                         2026-08-02 scoring rework, whose dials are on the
 *                         old scale — readers fall back to [formScore] there
 */
data class PracticeAttempt(
    val id: Long = 0L,
    val character: String,
    val romaji: String,
    val grade: Grade,
    val strokeCount: Int,
    val durationMs: Long,
    val createdAt: Long,
    val source: PracticeSource = PracticeSource.FREE,
    val formScore: Float? = null,
    val fitScore: Float? = null,
    val recognitionScore: Float? = null,
    val headlineScore: Float? = null,
)

/**
 * Session step a [PracticeAttempt] came from (docs/PHASE3-DESIGN.md §5).
 * QUIZ (post-Phase-3) is the library's write-from-memory quiz; like FREE
 * it never touches scheduling, and older builds decode it as FREE.
 */
enum class PracticeSource { REVIEW, NEW, VOCAB, FREE, QUIZ }
