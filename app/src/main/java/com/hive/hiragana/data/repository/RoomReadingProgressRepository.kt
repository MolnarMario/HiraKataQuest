package com.hive.hiragana.data.repository

import com.hive.hiragana.data.local.ReadingProgressDao
import com.hive.hiragana.data.local.ReadingProgressEntity
import com.hive.hiragana.domain.model.ReadingProgress
import com.hive.hiragana.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [ReadingProgressRepository]. Recording is an ensure-then-increment
 * pair (portable across all supported SQLite versions — no UPSERT syntax, which
 * needs SQLite 3.24 / API 30+): [ReadingProgressDao.ensure] guarantees a zeroed
 * row, then [ReadingProgressDao.increment] bumps the relevant style's counters.
 */
class RoomReadingProgressRepository(
    private val dao: ReadingProgressDao,
) : ReadingProgressRepository {

    override fun observe(): Flow<List<ReadingProgress>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun record(itemKey: String, typed: Boolean, correct: Boolean) {
        dao.ensure(ReadingProgressEntity(itemKey = itemKey))
        dao.increment(
            key = itemKey,
            mcqAttempts = if (!typed) 1 else 0,
            mcqCorrect = if (!typed && correct) 1 else 0,
            typeAttempts = if (typed) 1 else 0,
            typeCorrect = if (typed && correct) 1 else 0,
        )
    }

    private fun toDomain(e: ReadingProgressEntity) = ReadingProgress(
        itemKey = e.itemKey,
        mcqAttempts = e.mcqAttempts,
        mcqCorrect = e.mcqCorrect,
        typeAttempts = e.typeAttempts,
        typeCorrect = e.typeCorrect,
    )
}
