package com.hive.hiragana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data access for the character_progress table. Only used through
 * RoomLearningRepository, which maps entities to domain models.
 */
@Dao
interface CharacterProgressDao {

    @Query("SELECT * FROM character_progress")
    suspend fun getAll(): List<CharacterProgressEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CharacterProgressEntity)

    @Query("UPDATE character_progress SET totalTraces = totalTraces + 1 WHERE characterId = :characterId")
    suspend fun incrementTraces(characterId: String)
}
