package com.hive.hiragana.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one character's spaced-repetition state — the domain
 * ReviewCard flattened for storage (`review_cards` table,
 * docs/PHASE3-DESIGN.md §5).
 */
@Entity(tableName = "review_cards")
data class ReviewCardEntity(
    @PrimaryKey val characterId: String,
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val nextReviewDate: Long,
)
