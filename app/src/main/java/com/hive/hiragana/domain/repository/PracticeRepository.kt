package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.model.PracticeAttempt
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
import kotlinx.coroutines.flow.Flow

/**
 * Store of graded tracing attempts. The MVP's only implementation is
 * RoomPracticeRepository (local Room database).
 */
interface PracticeRepository {
    /** Stores [attempt] and returns its new row id, for a later [updateHeadline]. */
    suspend fun recordAttempt(attempt: PracticeAttempt): Long

    /**
     * Backfills the headline the learner was shown, once the asynchronous
     * recognizer has reported. Attempts are stored immediately with their
     * geometry-only headline, so a slow or offline recognizer can never cost
     * a stored score; this refines the row when the verdict arrives.
     */
    suspend fun updateHeadline(id: Long, headline: Int)

    /** Newest first. Unused by the MVP UI, ready for a history screen. */
    fun observeAttempts(): Flow<List<PracticeAttempt>>

    /** Per-glyph quality aggregates (avg/best form + fit) for the Library history. */
    fun observeScoreSummaries(): Flow<List<CharacterScoreSummary>>
}
