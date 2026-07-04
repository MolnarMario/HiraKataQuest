package com.hive.hiragana.data.repository

import com.hive.hiragana.data.local.CharacterScoreStats
import com.hive.hiragana.data.local.PracticeAttemptDao
import com.hive.hiragana.data.local.PracticeAttemptEntity
import com.hive.hiragana.domain.model.Grade
import com.hive.hiragana.domain.model.PracticeAttempt
import com.hive.hiragana.domain.model.PracticeSource
import com.hive.hiragana.domain.repository.PracticeRepository
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/**
 * [PracticeRepository] backed by Room. Grades are persisted as enum name
 * strings; the legacy Phase 1/2 name "OKAY" and any unrecognised name
 * decode to [Grade.GOOD] instead of failing the whole read. [source] is
 * likewise persisted as its enum name, with any unrecognised name (e.g. a
 * pre-Phase-3 row's stored default) decoding to [PracticeSource.FREE].
 */
class RoomPracticeRepository(
    private val dao: PracticeAttemptDao,
) : PracticeRepository {

    override suspend fun recordAttempt(attempt: PracticeAttempt) {
        dao.insert(attempt.toEntity())
    }

    override fun observeAttempts(): Flow<List<PracticeAttempt>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeScoreSummaries(): Flow<List<CharacterScoreSummary>> =
        dao.observeScoreStats().map { rows -> rows.map { it.toSummary() } }
}

private fun CharacterScoreStats.toSummary() = CharacterScoreSummary(
    character = character,
    attempts = attempts,
    avgForm = avgForm?.roundToInt(),
    bestForm = bestForm?.roundToInt(),
    avgFit = avgFit?.roundToInt(),
    bestFit = bestFit?.roundToInt(),
)

private fun PracticeAttempt.toEntity() = PracticeAttemptEntity(
    character = character,
    romaji = romaji,
    grade = grade.name,
    strokeCount = strokeCount,
    durationMs = durationMs,
    createdAt = createdAt,
    source = source.name,
    formScore = formScore,
    fitScore = fitScore,
    recognitionScore = recognitionScore,
)

private fun PracticeAttemptEntity.toDomain() = PracticeAttempt(
    id = id,
    character = character,
    romaji = romaji,
    grade = runCatching { Grade.valueOf(grade) }.getOrDefault(Grade.GOOD),
    strokeCount = strokeCount,
    durationMs = durationMs,
    createdAt = createdAt,
    source = runCatching { PracticeSource.valueOf(source) }.getOrDefault(PracticeSource.FREE),
    formScore = formScore,
    fitScore = fitScore,
    recognitionScore = recognitionScore,
)
