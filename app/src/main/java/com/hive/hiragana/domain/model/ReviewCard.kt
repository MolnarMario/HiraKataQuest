package com.hive.hiragana.domain.model

/**
 * Spaced-repetition state for one character (simplified SM-2,
 * docs/PHASE3-DESIGN.md §2). Persisted in the `review_cards` table.
 *
 * @param characterId    [HiraganaCharacter.id] this card schedules
 * @param repetitions    successful reviews in a row; [Grade.AGAIN] resets to 0
 * @param intervalDays   current inter-review interval in whole days
 * @param easeFactor     the [Grade.GOOD] interval multiplier; starts at 1.7,
 *                       adjusted by HARD (−0.15, floor 1.3) and EASY
 *                       (+0.15, cap 2.5)
 * @param nextReviewDate epoch millis of the local start-of-day the card
 *                       becomes due; due when `nextReviewDate <= now`
 */
data class ReviewCard(
    val characterId: String,
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val nextReviewDate: Long,
)
