package com.hive.hiragana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data access for the vocabulary_progress table. Only used through
 * RoomVocabularyRepository, which maps entities to domain models.
 */
@Dao
interface VocabularyProgressDao {

    @Query("SELECT * FROM vocabulary_progress")
    suspend fun getAll(): List<VocabularyProgressEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: VocabularyProgressEntity)

    @Query("UPDATE vocabulary_progress SET timesPracticed = timesPracticed + 1, lastPracticedAt = :atMillis WHERE kana = :kana")
    suspend fun recordPractice(kana: String, atMillis: Long)
}
