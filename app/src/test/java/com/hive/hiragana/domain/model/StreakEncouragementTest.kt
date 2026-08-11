package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StreakEncouragementTest {

    @Test
    fun `tier boundaries map to the right pool`() {
        assertEquals(StreakEncouragement.TIER_1, StreakEncouragement.tierMessages(1))
        assertEquals(StreakEncouragement.TIER_2, StreakEncouragement.tierMessages(2))
        assertEquals(StreakEncouragement.TIER_3, StreakEncouragement.tierMessages(4))
        assertEquals(StreakEncouragement.TIER_4, StreakEncouragement.tierMessages(6))
        assertEquals(StreakEncouragement.TIER_5, StreakEncouragement.tierMessages(10))
        assertEquals(StreakEncouragement.TIER_6, StreakEncouragement.tierMessages(15))
        assertEquals(StreakEncouragement.TIER_7, StreakEncouragement.tierMessages(25))
        assertEquals(StreakEncouragement.TIER_8, StreakEncouragement.tierMessages(50))
    }

    @Test
    fun `milestone lines win over their tier`() {
        assertEquals("5 in a row!", StreakEncouragement.messageFor(5, correct = true, previous = null))
        assertEquals("Double digits!", StreakEncouragement.messageFor(10, correct = true, previous = null))
        assertEquals(
            "20 straight, record territory!",
            StreakEncouragement.messageFor(20, correct = true, previous = null),
        )
        assertEquals("Halfway to 50!", StreakEncouragement.messageFor(25, correct = true, previous = null))
        assertEquals("Fifty. Take a bow.", StreakEncouragement.messageFor(50, correct = true, previous = null))
    }

    @Test
    fun `a wrong answer draws a gentle streak-broken line`() {
        repeat(20) { seed ->
            val m = StreakEncouragement.messageFor(0, correct = false, previous = null, random = Random(seed.toLong()))
            assertTrue("$m not in STREAK_BROKEN", m in StreakEncouragement.STREAK_BROKEN)
        }
    }

    @Test
    fun `a correct answer never repeats the line shown immediately before`() {
        val rnd = Random(1)
        var previous: String? = null
        repeat(300) {
            // streak 2 avoids every milestone, so the 6-line tier 2 pool is used.
            val m = StreakEncouragement.messageFor(2, correct = true, previous = previous, random = rnd)
            assertNotEquals(previous, m)
            assertTrue(m in StreakEncouragement.TIER_2)
            previous = m
        }
    }

    @Test
    fun `a single remaining option allows a repeat instead of failing`() {
        assertEquals("only", StreakEncouragement.pickDistinct(listOf("only"), "only", Random(0)))
    }

    @Test
    fun `no encouragement copy uses an em or en dash`() {
        val all = StreakEncouragement.TIERS.flatMap { it.second } +
            StreakEncouragement.MILESTONES.values +
            StreakEncouragement.STREAK_BROKEN +
            StreakEncouragement.PERFECT_TIERS.flatMap { it.second }
        all.forEach { assertTrue("dash in: $it", '—' !in it && '–' !in it) }
    }

    @Test
    fun `perfect-set tier boundaries map to the right pool`() {
        assertEquals(StreakEncouragement.PERFECT_TIER_1, StreakEncouragement.perfectSetTierMessages(1))
        assertEquals(StreakEncouragement.PERFECT_TIER_2, StreakEncouragement.perfectSetTierMessages(2))
        assertEquals(StreakEncouragement.PERFECT_TIER_3, StreakEncouragement.perfectSetTierMessages(4))
        assertEquals(StreakEncouragement.PERFECT_TIER_4, StreakEncouragement.perfectSetTierMessages(6))
        assertEquals(StreakEncouragement.PERFECT_TIER_5, StreakEncouragement.perfectSetTierMessages(10))
        assertEquals(StreakEncouragement.PERFECT_TIER_6, StreakEncouragement.perfectSetTierMessages(15))
        assertEquals(StreakEncouragement.PERFECT_TIER_7, StreakEncouragement.perfectSetTierMessages(25))
        assertEquals(StreakEncouragement.PERFECT_TIER_8, StreakEncouragement.perfectSetTierMessages(50))
    }

    @Test
    fun `perfectSetMessage stays within the matching tier pool`() {
        repeat(20) { seed ->
            val m = StreakEncouragement.perfectSetMessage(7, random = Random(seed.toLong()))
            assertTrue("$m not in PERFECT_TIER_4", m in StreakEncouragement.PERFECT_TIER_4)
        }
    }
}
