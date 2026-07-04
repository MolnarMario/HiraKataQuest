package com.hive.hiragana.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one vocabulary word's unlock/practice state — the domain
 * VocabularyProgress flattened for storage (`vocabulary_progress` table,
 * docs/PHASE3-DESIGN.md §5). A row exists only for unlocked words.
 */
@Entity(tableName = "vocabulary_progress")
data class VocabularyProgressEntity(
    @PrimaryKey val kana: String,
    val unlockedAt: Long,
    val timesPracticed: Int,
    val lastPracticedAt: Long?,
)
