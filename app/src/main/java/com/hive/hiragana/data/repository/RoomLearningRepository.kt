package com.hive.hiragana.data.repository

import com.hive.hiragana.data.local.CharacterProgressDao
import com.hive.hiragana.data.local.CharacterProgressEntity
import com.hive.hiragana.data.local.ReviewCardDao
import com.hive.hiragana.data.local.ReviewCardEntity
import com.hive.hiragana.domain.model.CharacterProgress
import com.hive.hiragana.domain.model.ReviewCard
import com.hive.hiragana.domain.repository.LearningRepository

/**
 * [LearningRepository] backed by Room. Unlike RoomPracticeRepository's
 * `recordAttempt`, failures here are not swallowed — callers decide how to
 * handle them (docs/PHASE3-DESIGN.md §5).
 */
class RoomLearningRepository(
    private val reviewCardDao: ReviewCardDao,
    private val characterProgressDao: CharacterProgressDao,
) : LearningRepository {

    override suspend fun getReviewCards(): List<ReviewCard> =
        reviewCardDao.getAll().map { it.toDomain() }

    override suspend fun upsertReviewCard(card: ReviewCard) {
        reviewCardDao.upsert(card.toEntity())
    }

    override suspend fun getCharacterProgress(): List<CharacterProgress> =
        characterProgressDao.getAll().map { it.toDomain() }

    override suspend fun markIntroduced(characterId: String, atMillis: Long) {
        characterProgressDao.insert(
            CharacterProgressEntity(characterId = characterId, introducedAt = atMillis, totalTraces = 0)
        )
    }

    override suspend fun incrementTraces(characterId: String) {
        characterProgressDao.incrementTraces(characterId)
    }
}

private fun ReviewCard.toEntity() = ReviewCardEntity(
    characterId = characterId,
    repetitions = repetitions,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    nextReviewDate = nextReviewDate,
)

private fun ReviewCardEntity.toDomain() = ReviewCard(
    characterId = characterId,
    repetitions = repetitions,
    intervalDays = intervalDays,
    easeFactor = easeFactor,
    nextReviewDate = nextReviewDate,
)

private fun CharacterProgressEntity.toDomain() = CharacterProgress(
    characterId = characterId,
    introducedAt = introducedAt,
    totalTraces = totalTraces,
)
