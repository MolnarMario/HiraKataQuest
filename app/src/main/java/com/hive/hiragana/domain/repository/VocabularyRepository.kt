package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.model.VocabularyProgress
import com.hive.hiragana.domain.model.VocabularyWord

/**
 * Vocabulary words (from assets/hiragana/vocabulary.json) and their
 * unlock/practice state (table `vocabulary_progress`). Implemented by
 * data/repository/RoomVocabularyRepository.
 */
interface VocabularyRepository {

    /** Every word in the vocabulary asset, locked or not. */
    suspend fun getWords(): List<VocabularyWord>

    /** Progress rows for unlocked words only. */
    suspend fun getProgress(): List<VocabularyProgress>

    /** Persist that the word [kana] unlocked at [atMillis] (idempotent). */
    suspend fun unlock(kana: String, atMillis: Long)

    /** Bump practice count and set last-practiced time for [kana]. */
    suspend fun recordPractice(kana: String, atMillis: Long)
}
