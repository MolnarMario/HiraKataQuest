package com.hive.hiragana.domain.learning

import com.hive.hiragana.domain.model.Grade
import com.hive.hiragana.domain.model.ReviewCard
import kotlin.math.ceil

/**
 * Simplified SM-2 spaced-repetition transitions (docs/PHASE3-DESIGN.md §2).
 *
 * Pure and clock-free: every date computation takes `todayStartMillis` — the
 * epoch millis of the local start-of-day — as a parameter, so callers own
 * timezone/DST handling in one place and this object stays trivially
 * unit-testable.
 */
object SrsScheduler {

    /** Millis in one day; used to derive [ReviewCard.nextReviewDate] from an interval. */
    private const val DAY = 86_400_000L

    /** Initial ease factor for a freshly introduced character. */
    private const val INITIAL_EASE = 1.7f

    /** Amount [Grade.HARD] subtracts from ease, and [Grade.EASY] adds to it. */
    private const val EASE_STEP = 0.15f

    /** Floor [Grade.HARD] cannot push ease below. */
    private const val EASE_FLOOR = 1.3f

    /** Cap [Grade.EASY] cannot push ease above. */
    private const val EASE_CAP = 2.5f

    /** Multiplier used for [Grade.EASY]'s interval growth, in place of the card's ease. */
    private const val EASY_MULTIPLIER = 2.5f

    /**
     * A brand-new [ReviewCard] for [characterId]: zero repetitions, zero
     * interval, the default ease of 1.7, and due immediately (`nextReviewDate`
     * equal to [todayStartMillis]) so it enters the very next session.
     */
    fun newCard(characterId: String, todayStartMillis: Long): ReviewCard = ReviewCard(
        characterId = characterId,
        repetitions = 0,
        intervalDays = 0,
        easeFactor = INITIAL_EASE,
        nextReviewDate = todayStartMillis,
    )

    /**
     * Applies one grading of [card] and returns the rescheduled card, per the
     * transition table in docs/PHASE3-DESIGN.md §2:
     *
     * - [Grade.AGAIN]: repetitions reset to 0, interval reset to 0, ease
     *   unchanged; due immediately today (requeued within the session).
     * - [Grade.HARD]: repetitions unchanged, interval collapses to a flat 1
     *   day regardless of the card's prior interval, ease drops by 0.15
     *   floored at 1.3.
     * - [Grade.GOOD]: repetitions +1, interval becomes
     *   `max(1, ceil(intervalDays * easeFactor))`, ease unchanged.
     * - [Grade.EASY]: repetitions +1, interval becomes
     *   `max(2, ceil(intervalDays * 2.5))`, ease rises by 0.15 capped at 2.5.
     *
     * In every case `nextReviewDate' = todayStartMillis + intervalDays' * DAY`.
     */
    fun next(card: ReviewCard, grade: Grade, todayStartMillis: Long): ReviewCard {
        val (repetitions, intervalDays, easeFactor) = when (grade) {
            Grade.AGAIN -> Triple(0, 0, card.easeFactor)
            Grade.HARD -> Triple(
                card.repetitions,
                1,
                (card.easeFactor - EASE_STEP).coerceAtLeast(EASE_FLOOR),
            )
            Grade.GOOD -> Triple(
                card.repetitions + 1,
                growInterval(card.intervalDays, card.easeFactor, minimum = 1),
                card.easeFactor,
            )
            Grade.EASY -> Triple(
                card.repetitions + 1,
                growInterval(card.intervalDays, EASY_MULTIPLIER, minimum = 2),
                (card.easeFactor + EASE_STEP).coerceAtMost(EASE_CAP),
            )
        }
        return card.copy(
            repetitions = repetitions,
            intervalDays = intervalDays,
            easeFactor = easeFactor,
            nextReviewDate = todayStartMillis + intervalDays * DAY,
        )
    }

    /** `max(minimum, ceil(intervalDays * multiplier))`, as whole days. */
    private fun growInterval(intervalDays: Int, multiplier: Float, minimum: Int): Int =
        maxOf(minimum, ceil(intervalDays * multiplier).toInt())
}
