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

    // Perfect-set completion lines for the reading-quiz summary: shown once,
    // at the end, when every answered item in the set was correct (no
    // per-item wrong answers to review). Scales with the set size using the
    // same range boundaries as [TIERS], but phrased for "you cleared the
    // whole set" rather than "you're mid-streak".
    val PERFECT_TIER_1 = listOf(
        "All correct!", "Perfect!", "Nailed it!",
    )
    val PERFECT_TIER_2 = listOf(
        "Clean sweep!", "Not one mistake!", "Flawless!",
    )
    val PERFECT_TIER_3 = listOf(
        "Every single one right!", "Immaculate run!", "You nailed the whole set!",
    )
    val PERFECT_TIER_4 = listOf(
        "Flawless victory!", "Not a single slip!", "That's mastery right there!",
    )
    val PERFECT_TIER_5 = listOf(
        "A perfect set that size is no accident!", "Outstanding, zero mistakes!",
        "You're on another level!",
    )
    val PERFECT_TIER_6 = listOf(
        "Incredible, a flawless run that long!", "Total mastery!",
        "You crushed every single one!",
    )
    val PERFECT_TIER_7 = listOf(
        "Legendary. Not one wrong answer!", "That's a hall of fame set!",
        "Absolutely dominant!",
    )
    val PERFECT_TIER_8 = listOf(
        "Superhuman. Perfect, every time!", "That's an unbelievable perfect run!",
        "Are you even human?!",
    )

    /** (set-size range -> message pool), lowest range first. */
    val PERFECT_TIERS: List<Pair<IntRange, List<String>>> = listOf(
        1..1 to PERFECT_TIER_1,
        2..3 to PERFECT_TIER_2,
        4..5 to PERFECT_TIER_3,
        6..9 to PERFECT_TIER_4,
        10..14 to PERFECT_TIER_5,
        15..24 to PERFECT_TIER_6,
        25..49 to PERFECT_TIER_7,
        50..Int.MAX_VALUE to PERFECT_TIER_8,
    )

    /** The perfect-set pool for [count] correct answers. */
    fun perfectSetTierMessages(count: Int): List<String> =
        PERFECT_TIERS.firstOrNull { count in it.first }?.second ?: PERFECT_TIER_1

    /**
     * A hype line for a reading-quiz summary where every answered item was
     * correct, scaled by [count] (how many were gotten right) the same way a
     * live streak escalates. Random within the matching pool.
     */
    fun perfectSetMessage(count: Int, random: Random = Random.Default): String {
        val pool = perfectSetTierMessages(count)
        return pool[random.nextInt(pool.size)]
    }

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
