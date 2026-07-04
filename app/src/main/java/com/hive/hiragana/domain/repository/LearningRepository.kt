package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.model.CharacterProgress
import com.hive.hiragana.domain.model.ReviewCard

/**
 * Spaced-repetition persistence: review cards + per-character progress
 * (tables `review_cards` and `character_progress`). Implemented by
 * data/repository/RoomLearningRepository.
 */
interface LearningRepository {

    /** All review cards, one per introduced character. */
    suspend fun getReviewCards(): List<ReviewCard>

    /** Insert or replace [card] (keyed on [ReviewCard.characterId]). */
    suspend fun upsertReviewCard(card: ReviewCard)

    /** All character progress rows. */
    suspend fun getCharacterProgress(): List<CharacterProgress>

    /** Record that [characterId] was introduced at [atMillis] (idempotent). */
    suspend fun markIntroduced(characterId: String, atMillis: Long)

    /** Bump [CharacterProgress.totalTraces] for [characterId]. */
    suspend fun incrementTraces(characterId: String)
}
