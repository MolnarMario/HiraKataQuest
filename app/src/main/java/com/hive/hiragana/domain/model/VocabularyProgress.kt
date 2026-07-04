package com.hive.hiragana.domain.model

/**
 * Unlock/practice state for one vocabulary word (`vocabulary_progress`
 * table). A row exists only for unlocked words.
 */
data class VocabularyProgress(
    val kana: String,
    val unlockedAt: Long,
    val timesPracticed: Int,
    val lastPracticedAt: Long?,
)
