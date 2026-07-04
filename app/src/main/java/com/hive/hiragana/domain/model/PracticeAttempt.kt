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
)

/**
 * Session step a [PracticeAttempt] came from (docs/PHASE3-DESIGN.md §5).
 * QUIZ (post-Phase-3) is the library's write-from-memory quiz; like FREE
 * it never touches scheduling, and older builds decode it as FREE.
 */
enum class PracticeSource { REVIEW, NEW, VOCAB, FREE, QUIZ }
