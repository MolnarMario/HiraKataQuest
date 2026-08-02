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
     * Refines an already-inserted row's headline once the asynchronous
     * recognizer has reported. Rows are inserted synchronously with the
     * geometry-only headline so a slow or offline recognizer can never cost
     * the learner a stored score; this backfills the refinement when (and
     * only when) it arrives.
     */
    @Query("UPDATE practice_history SET headline_score = :headline WHERE id = :id")
    suspend fun updateHeadline(id: Long, headline: Float)

    /**
     * Per-character quality aggregates for the Library history, keyed by the
     * glyph string (safe across scripts — ア and あ are distinct glyphs even
     * though they share an id). Only scored single-character rows count
     * (`form_score IS NOT NULL` excludes words and pre-v3 rows).
     *
     * `avgDisplayed`/`bestDisplayed` are the numbers the learner was actually
     * shown, and are what the Library reports. Rows written before the
     * 2026-08-02 scoring rework have no stored headline, so they COALESCE to
     * their FORM dial: every historic attempt keeps counting toward the
     * average rather than silently dropping out of it the moment one new-scale
     * row lands. (A plain `AVG(headline_score)` would skip those NULLs
     * entirely — it ignores nulls rather than treating them as zero — which
     * would quietly discard a learner's whole practice history.) The two
     * scales do blend for a while; that is the accepted cost of not
     * discarding real practice.
     */
    @Query(
        """
        SELECT character AS character,
               COUNT(*) AS attempts,
               AVG(form_score) AS avgForm,
               MAX(form_score) AS bestForm,
               AVG(fit_score) AS avgFit,
               MAX(fit_score) AS bestFit,
               AVG(COALESCE(headline_score, form_score)) AS avgDisplayed,
               MAX(COALESCE(headline_score, form_score)) AS bestDisplayed
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
    val avgDisplayed: Float?,
    val bestDisplayed: Float?,
)
