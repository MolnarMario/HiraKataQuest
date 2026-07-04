package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.model.PracticeAttempt
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
import kotlinx.coroutines.flow.Flow

/**
 * Store of graded tracing attempts. The MVP's only implementation is
 * RoomPracticeRepository (local Room database).
 */
interface PracticeRepository {
    suspend fun recordAttempt(attempt: PracticeAttempt)

    /** Newest first. Unused by the MVP UI, ready for a history screen. */
    fun observeAttempts(): Flow<List<PracticeAttempt>>

    /** Per-glyph quality aggregates (avg/best form + fit) for the Library history. */
    fun observeScoreSummaries(): Flow<List<CharacterScoreSummary>>
}
