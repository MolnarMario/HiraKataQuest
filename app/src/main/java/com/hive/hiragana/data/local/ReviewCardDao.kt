package com.hive.hiragana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data access for the review_cards table. Only used through
 * RoomLearningRepository, which maps entities to domain models.
 */
@Dao
interface ReviewCardDao {

    @Query("SELECT * FROM review_cards")
    suspend fun getAll(): List<ReviewCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReviewCardEntity)
}
