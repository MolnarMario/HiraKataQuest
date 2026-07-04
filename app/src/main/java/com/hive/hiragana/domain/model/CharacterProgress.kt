package com.hive.hiragana.domain.model

/**
 * Introduction/practice stats for one character (`character_progress`
 * table). Scheduling state lives on [ReviewCard]; this row records that a
 * character has been introduced and how much it has been traced.
 */
data class CharacterProgress(
    val characterId: String,
    val introducedAt: Long,
    val totalTraces: Int,
)
