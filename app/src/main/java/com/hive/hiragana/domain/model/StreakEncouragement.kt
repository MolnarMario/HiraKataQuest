package com.hive.hiragana.domain.model

import kotlin.random.Random

/**
 * Streak-based encouragement for the reading quiz. All copy lives here as public
 * lists/maps so it can be edited without touching the selection logic in
 * [messageFor]. A correct answer picks from the tier matching the current streak
 * length; an exact milestone streak wins over its tier; a wrong answer draws a
 * gentle line from [STREAK_BROKEN]. Randomized within a pool, but never the line
 * shown immediately before (unless excluding it would leave nothing).
 */
object StreakEncouragement {

    val TIER_1 = listOf(
        "Correct!", "Nice start!", "Good job!", "You got it!", "That's right!", "Yep!",
    )
    val TIER_2 = listOf(
        "Way to go!", "Keep it up!", "You're on a roll!", "Great work!", "Nice work!", "Solid!",
    )
    val TIER_3 = listOf(
        "You're doing great!", "Strong streak!", "Nicely done!",
        "Keep that momentum going!", "You've got this!", "Don't stop now!",
    )
    val TIER_4 = listOf(
        "Impressive!", "You really know your stuff!", "That's excellent work!",
        "You're crushing it!", "You're on fire!", "Momentum's building!",
    )
    val TIER_5 = listOf(
        "Amazing streak!", "You're unstoppable!", "Fantastic consistency!",
        "You're absolutely flying!", "Can't be stopped!", "Someone's been studying!",
    )
    val TIER_6 = listOf(
        "Outstanding!", "What a run!", "Your focus is paying off!",
        "You're mastering this!", "Incredible run!", "Flawless so far!",
    )
    val TIER_7 = listOf(
        "Legendary streak!", "Incredible performance!", "You're in the zone!",
        "Seriously impressive!", "Hall of fame material!",
        "This is getting ridiculous (in the best way)!",
    )
    val TIER_8 = listOf(
        "Phenomenal!", "An unbeatable run!", "You're setting the standard!",
        "Absolutely exceptional!", "Are you even human?!", "Perfect run, 50 and counting!",
    )

    /** (streak range -> message pool), lowest range first. */
    val TIERS: List<Pair<IntRange, List<String>>> = listOf(
        1..1 to TIER_1,
        2..3 to TIER_2,
        4..5 to TIER_3,
        6..9 to TIER_4,
        10..14 to TIER_5,
        15..24 to TIER_6,
        25..49 to TIER_7,
        50..Int.MAX_VALUE to TIER_8,
    )

    /** Exact streak values whose line overrides the tier line. */
    val MILESTONES: Map<Int, String> = mapOf(
        5 to "5 in a row!",
        10 to "Double digits!",
        20 to "20 straight, record territory!",
        25 to "Halfway to 50!",
        50 to "Fifty. Take a bow.",
    )

    /** Shown after any wrong answer. Gentle by design, never scolding. */
    val STREAK_BROKEN: List<String> = listOf(
        "Close! Back to it.",
        "No worries, keep going.",
        "Happens to everyone. Next one!",
        "Shake it off.",
    )

    /** The tier pool for [streak] (milestones are not considered here). */
    fun tierMessages(streak: Int): List<String> =
        TIERS.firstOrNull { streak in it.first }?.second ?: TIER_1

    /**
     * The encouragement to show after an answer. [streak] is the value AFTER the
     * answer (a correct answer advances it, a wrong answer resets it to 0).
     * [previous] is the line shown immediately before, excluded so the same line
     * never repeats back to back. Returns non-null for every answer; the caller
     * simply does not call this at the start of a quiz, so streak 0 stays silent.
     */
    fun messageFor(
        streak: Int,
        correct: Boolean,
        previous: String?,
        random: Random = Random.Default,
    ): String {
        if (correct) {
            MILESTONES[streak]?.let { return it }
            return pickDistinct(tierMessages(streak), previous, random)
        }
        return pickDistinct(STREAK_BROKEN, previous, random)
    }

    /**
     * A random line from [pool], excluding [previous] when possible. If dropping
     * [previous] would leave nothing, the repeat is allowed rather than failing.
     */
    internal fun pickDistinct(pool: List<String>, previous: String?, random: Random): String {
        require(pool.isNotEmpty()) { "encouragement pool must not be empty" }
        val choices = pool.filterNot { it == previous }.ifEmpty { pool }
        return choices[random.nextInt(choices.size)]
    }
}
