package com.hive.hiragana.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Per-item reading-quiz tally, split by answer style: how many multiple-choice
 * vs typed-romaji reading questions the learner has faced for this glyph/word,
 * and how many were correct. Keyed by the printed glyph/kana string — the same
 * key the Library tints cells by.
 *
 * Reading is binary (right or wrong), so — unlike the handwriting scorer — there
 * is no "best"; the meaningful figures are the two success percentages.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val itemKey: String,
    val mcqAttempts: Int = 0,
    val mcqCorrect: Int = 0,
    val typeAttempts: Int = 0,
    val typeCorrect: Int = 0,
)

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress")
    fun observeAll(): Flow<List<ReadingProgressEntity>>

    /** Ensures a zeroed row exists (no-op if already present) before incrementing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensure(row: ReadingProgressEntity)

    @Query(
        "UPDATE reading_progress SET " +
            "mcqAttempts = mcqAttempts + :mcqAttempts, " +
            "mcqCorrect = mcqCorrect + :mcqCorrect, " +
            "typeAttempts = typeAttempts + :typeAttempts, " +
            "typeCorrect = typeCorrect + :typeCorrect " +
            "WHERE itemKey = :key",
    )
    suspend fun increment(
        key: String,
        mcqAttempts: Int,
        mcqCorrect: Int,
        typeAttempts: Int,
        typeCorrect: Int,
    )
}
