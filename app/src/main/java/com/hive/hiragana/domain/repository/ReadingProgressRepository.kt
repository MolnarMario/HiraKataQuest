package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

/**
 * Persists and observes per-item reading-quiz accuracy (see [ReadingProgress]).
 */
interface ReadingProgressRepository {

    /** All items with any recorded reading attempts. */
    fun observe(): Flow<List<ReadingProgress>>

    /**
     * Records one reading answer for [itemKey] (the glyph/kana string).
     * [typed] = true for a typed-romaji answer, false for multiple-choice;
     * the count lands in the corresponding style's tally.
     */
    suspend fun record(itemKey: String, typed: Boolean, correct: Boolean)
}
