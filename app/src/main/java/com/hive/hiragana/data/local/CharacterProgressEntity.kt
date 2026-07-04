package com.hive.hiragana.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one character's introduction/practice stats — the domain
 * CharacterProgress flattened for storage (`character_progress` table,
 * docs/PHASE3-DESIGN.md §5).
 */
@Entity(tableName = "character_progress")
data class CharacterProgressEntity(
    @PrimaryKey val characterId: String,
    val introducedAt: Long,
    val totalTraces: Int,
)
