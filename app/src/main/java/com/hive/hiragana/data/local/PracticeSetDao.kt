package com.hive.hiragana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data access for the practice-set tables (Room v4). Only used through
 * RoomPracticeSetRepository, which maps entities to domain models. Writes are
 * suspend; the set list is observed as a [Flow] so the ViewModel's Study Scope
 * sheet and the Quiz-me source picker stay live as sets are created/edited.
 */
@Dao
interface PracticeSetDao {

    @Transaction
    @Query("SELECT * FROM practice_sets ORDER BY createdAt DESC")
    fun observeSetsWithItems(): Flow<List<PracticeSetWithItems>>

    @Insert
    suspend fun insertSet(set: PracticeSetEntity): Long

    @Insert
    suspend fun insertItems(items: List<PracticeSetItemEntity>)

    @Query("DELETE FROM practice_set_items WHERE setId = :setId")
    suspend fun deleteItems(setId: Long)

    @Query("UPDATE practice_sets SET name = :name WHERE id = :setId")
    suspend fun renameSet(setId: Long, name: String)

    @Query("DELETE FROM practice_sets WHERE id = :setId")
    suspend fun deleteSet(setId: Long)

    /** Insert a set header plus its ordered members in one transaction. */
    @Transaction
    suspend fun createSet(set: PracticeSetEntity, values: List<String>): Long {
        val id = insertSet(set)
        insertItems(values.toItemEntities(id))
        return id
    }

    /** Replace a set's members wholesale (used when editing membership). */
    @Transaction
    suspend fun replaceItems(setId: Long, values: List<String>) {
        deleteItems(setId)
        insertItems(values.toItemEntities(setId))
    }
}

private fun List<String>.toItemEntities(setId: Long): List<PracticeSetItemEntity> =
    mapIndexed { index, value ->
        PracticeSetItemEntity(setId = setId, value = value, position = index)
    }
