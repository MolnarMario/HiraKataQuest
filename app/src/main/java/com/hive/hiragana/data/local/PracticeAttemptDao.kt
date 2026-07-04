package com.hive.hiragana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access for the practice_history table (renamed from
 * practice_attempts in the Phase 3 v1->v2 migration). Only used through
 * RoomPracticeRepository, which maps entities to domain models.
 */
@Dao
interface PracticeAttemptDao {

    @Insert
    suspend fun insert(attempt: PracticeAttemptEntity): Long

    @Query("SELECT * FROM practice_history ORDER BY created_at DESC")
    fun observeAll(): Flow<List<PracticeAttemptEntity>>

    /**
     * Per-character quality aggregates for the Library history, keyed by the
     * glyph string (safe across scripts — ア and あ are distinct glyphs even
     * though they share an id). Only scored single-character rows count
     * (`form_score IS NOT NULL` excludes words and pre-v3 rows).
     */
    @Query(
        """
        SELECT character AS character,
               COUNT(*) AS attempts,
               AVG(form_score) AS avgForm,
               MAX(form_score) AS bestForm,
               AVG(fit_score) AS avgFit,
               MAX(fit_score) AS bestFit
        FROM practice_history
        WHERE form_score IS NOT NULL
        GROUP BY character
        """
    )
    fun observeScoreStats(): Flow<List<CharacterScoreStats>>
}

/** Aggregate quality stats for one glyph (see [PracticeAttemptDao.observeScoreStats]). */
data class CharacterScoreStats(
    val character: String,
    val attempts: Int,
    val avgForm: Float?,
    val bestForm: Float?,
    val avgFit: Float?,
    val bestFit: Float?,
)
