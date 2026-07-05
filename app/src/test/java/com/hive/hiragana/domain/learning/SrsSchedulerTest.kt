package com.hive.hiragana.domain.learning

import com.hive.hiragana.domain.model.Grade
import com.hive.hiragana.domain.model.ReviewCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SrsScheduler] against the transition table in
 * docs/PHASE3-DESIGN.md §2. All dates are computed off a single fixed
 * [TODAY] constant — the scheduler never reads the system clock.
 */
class SrsSchedulerTest {

    companion object {
        /** Arbitrary fixed local start-of-day used as "today" throughout. Never [System.currentTimeMillis]. */
        private const val TODAY = 1_800_000_000_000L
        private const val DAY = 86_400_000L
        private const val DELTA = 0.0001f

        /** Mirror of SrsScheduler's private interval cap (~100 years). */
        private const val MAX_INTERVAL_DAYS = 36_500
    }

    private fun card(
        characterId: String = "a",
        repetitions: Int = 0,
        intervalDays: Int = 0,
        easeFactor: Float = 1.7f,
        nextReviewDate: Long = TODAY,
    ) = ReviewCard(characterId, repetitions, intervalDays, easeFactor, nextReviewDate)

    // ---- newCard ----------------------------------------------------------

    @Test
    fun `newCard defaults repetitions interval and ease, due today`() {
        val result = SrsScheduler.newCard("a", TODAY)

        assertEquals("a", result.characterId)
        assertEquals(0, result.repetitions)
        assertEquals(0, result.intervalDays)
        assertEquals(1.7f, result.easeFactor, DELTA)
        assertEquals(TODAY, result.nextReviewDate)
    }

    // ---- AGAIN --------------------------------------------------------------

    @Test
    fun `AGAIN resets repetitions and interval to zero, preserves ease, dues today`() {
        val input = card(repetitions = 5, intervalDays = 21, easeFactor = 2.1f)

        val result = SrsScheduler.next(input, Grade.AGAIN, TODAY)

        assertEquals(0, result.repetitions)
        assertEquals(0, result.intervalDays)
        assertEquals(2.1f, result.easeFactor, DELTA)
        assertEquals(TODAY, result.nextReviewDate)
    }

    @Test
    fun `AGAIN on a fresh card stays at zero and preserves default ease`() {
        val input = SrsScheduler.newCard("a", TODAY)

        val result = SrsScheduler.next(input, Grade.AGAIN, TODAY)

        assertEquals(0, result.repetitions)
        assertEquals(0, result.intervalDays)
        assertEquals(1.7f, result.easeFactor, DELTA)
        assertEquals(TODAY, result.nextReviewDate)
    }

    // ---- HARD ---------------------------------------------------------------

    @Test
    fun `HARD collapses a mature 30-day card to a flat 1 day interval`() {
        val input = card(repetitions = 6, intervalDays = 30, easeFactor = 1.7f)

        val result = SrsScheduler.next(input, Grade.HARD, TODAY)

        assertEquals(6, result.repetitions)
        assertEquals(1, result.intervalDays)
        assertEquals(TODAY + DAY, result.nextReviewDate)
    }

    @Test
    fun `HARD leaves repetitions unchanged and lowers ease by 0point15`() {
        val input = card(repetitions = 3, intervalDays = 4, easeFactor = 1.7f)

        val result = SrsScheduler.next(input, Grade.HARD, TODAY)

        assertEquals(3, result.repetitions)
        assertEquals(1.55f, result.easeFactor, DELTA)
    }

    @Test
    fun `repeated HARD floors ease at 1point3 and stays there`() {
        var current = card(easeFactor = 1.7f)

        repeat(6) {
            current = SrsScheduler.next(current, Grade.HARD, TODAY)
        }

        assertEquals(1.3f, current.easeFactor, 0f)
    }

    // ---- GOOD -----------------------------------------------------------------

    @Test
    fun `GOOD from interval zero gives a 1 day interval`() {
        val input = card(repetitions = 0, intervalDays = 0, easeFactor = 1.7f)

        val result = SrsScheduler.next(input, Grade.GOOD, TODAY)

        assertEquals(1, result.repetitions)
        assertEquals(1, result.intervalDays)
        assertEquals(1.7f, result.easeFactor, DELTA)
        assertEquals(TODAY + DAY, result.nextReviewDate)
    }

    @Test
    fun `GOOD increments repetitions and leaves ease unchanged`() {
        val input = card(repetitions = 2, intervalDays = 4, easeFactor = 2.0f)

        val result = SrsScheduler.next(input, Grade.GOOD, TODAY)

        assertEquals(3, result.repetitions)
        assertEquals(2.0f, result.easeFactor, DELTA)
        // ceil(4 * 2.0) = 8
        assertEquals(8, result.intervalDays)
        assertEquals(TODAY + 8 * DAY, result.nextReviewDate)
    }

    @Test
    fun `GOOD uses the cards own ease factor, not a fixed constant`() {
        val input = card(intervalDays = 10, easeFactor = 2.5f)

        val result = SrsScheduler.next(input, Grade.GOOD, TODAY)

        // ceil(10 * 2.5) = 25, would be 17 at the default 1.7 ease
        assertEquals(25, result.intervalDays)
    }

    @Test
    fun `default ease GOOD growth sequence matches 0 1 2 4 7 12 21 36`() {
        var current = card(repetitions = 0, intervalDays = 0, easeFactor = 1.7f)
        val expected = listOf(1, 2, 4, 7, 12, 21, 36)

        val actual = expected.map {
            current = SrsScheduler.next(current, Grade.GOOD, TODAY)
            current.intervalDays
        }

        assertEquals(expected, actual)
        assertEquals(1.7f, current.easeFactor, DELTA)
        assertEquals(7, current.repetitions)
    }

    // ---- EASY -----------------------------------------------------------------

    @Test
    fun `EASY from interval zero gives a 2 day interval`() {
        val input = card(repetitions = 0, intervalDays = 0, easeFactor = 1.7f)

        val result = SrsScheduler.next(input, Grade.EASY, TODAY)

        assertEquals(1, result.repetitions)
        assertEquals(2, result.intervalDays)
        assertEquals(TODAY + 2 * DAY, result.nextReviewDate)
    }

    @Test
    fun `EASY increments repetitions, raises ease by 0point15, and uses the 2point5 multiplier`() {
        val input = card(repetitions = 1, intervalDays = 4, easeFactor = 1.7f)

        val result = SrsScheduler.next(input, Grade.EASY, TODAY)

        assertEquals(2, result.repetitions)
        assertEquals(1.85f, result.easeFactor, DELTA)
        // ceil(4 * 2.5) = 10, independent of the card's own 1.7 ease
        assertEquals(10, result.intervalDays)
    }

    @Test
    fun `repeated EASY caps ease at 2point5 and stays there`() {
        var current = card(easeFactor = 1.7f)

        repeat(7) {
            current = SrsScheduler.next(current, Grade.EASY, TODAY)
        }

        assertEquals(2.5f, current.easeFactor, 0f)
    }

    // ---- nextReviewDate arithmetic ---------------------------------------------

    @Test
    fun `nextReviewDate is exactly todayStart plus interval days times DAY`() {
        val input = card(intervalDays = 5, easeFactor = 2.0f)

        val result = SrsScheduler.next(input, Grade.GOOD, TODAY)

        // ceil(5 * 2.0) = 10
        assertEquals(10, result.intervalDays)
        assertEquals(TODAY + 10L * DAY, result.nextReviewDate)
    }

    @Test
    fun `AGAIN dues the card immediately today with zero offset`() {
        val input = card(intervalDays = 15, easeFactor = 1.9f)

        val result = SrsScheduler.next(input, Grade.AGAIN, TODAY)

        assertEquals(TODAY, result.nextReviewDate)
    }

    // ---- interval overflow clamp -----------------------------------------------

    @Test
    fun `long run of GOOD never exceeds the cap and keeps the due date positive`() {
        var current = card(repetitions = 0, intervalDays = 0, easeFactor = 2.5f)

        repeat(100) {
            current = SrsScheduler.next(current, Grade.GOOD, TODAY)
            assertTrue(
                "intervalDays ${current.intervalDays} exceeded cap $MAX_INTERVAL_DAYS",
                current.intervalDays <= MAX_INTERVAL_DAYS,
            )
            assertTrue(
                "nextReviewDate ${current.nextReviewDate} not after today $TODAY",
                current.nextReviewDate > TODAY,
            )
        }
        // After 100 GOOD grades at max ease the interval has long since saturated.
        assertEquals(MAX_INTERVAL_DAYS, current.intervalDays)
        assertEquals(TODAY + MAX_INTERVAL_DAYS.toLong() * DAY, current.nextReviewDate)
    }

    @Test
    fun `long run of EASY never exceeds the cap and keeps the due date positive`() {
        var current = card(repetitions = 0, intervalDays = 0, easeFactor = 1.7f)

        repeat(100) {
            current = SrsScheduler.next(current, Grade.EASY, TODAY)
            assertTrue(
                "intervalDays ${current.intervalDays} exceeded cap $MAX_INTERVAL_DAYS",
                current.intervalDays <= MAX_INTERVAL_DAYS,
            )
            assertTrue(
                "nextReviewDate ${current.nextReviewDate} not after today $TODAY",
                current.nextReviewDate > TODAY,
            )
        }
        assertEquals(MAX_INTERVAL_DAYS, current.intervalDays)
    }
}
