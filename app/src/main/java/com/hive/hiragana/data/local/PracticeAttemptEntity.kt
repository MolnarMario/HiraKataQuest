package com.hive.hiragana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one graded tracing attempt — the domain PracticeAttempt
 * flattened for storage. [grade] holds the Grade enum's name as plain text.
 * Table renamed from `practice_attempts` in the Phase 3 v1->v2 migration
 * (docs/PHASE3-DESIGN.md §5); [source] holds the PracticeSource enum's name
 * as plain text, defaulting to "FREE" for rows written before the rename.
 * The three dial columns were added in the v2->v3 migration (handwriting
 * quality scoring); they are null for word attempts, for pre-v3 rows, and
 * whenever scoring was off/unavailable. [headlineScore] followed in v6->v7:
 * it stores the number the learner was actually SHOWN, so the Library can
 * report the same figure as the feedback card did. A null there means the
 * row predates the honest-scoring rework and its dials are on the old
 * (pre-2026-08-02) scale — that null is the cutover marker.
 */
@Entity(tableName = "practice_history")
data class PracticeAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val character: String,
    val romaji: String,
    val grade: String,
    @ColumnInfo(name = "stroke_count") val strokeCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(defaultValue = "FREE") val source: String = "FREE",
    @ColumnInfo(name = "form_score") val formScore: Float? = null,
    @ColumnInfo(name = "fit_score") val fitScore: Float? = null,
    @ColumnInfo(name = "recognition_score") val recognitionScore: Float? = null,
    @ColumnInfo(name = "headline_score") val headlineScore: Float? = null,
)
