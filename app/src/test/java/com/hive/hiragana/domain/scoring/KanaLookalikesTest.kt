package com.hive.hiragana.domain.scoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KanaLookalikes] — the shape-twin equivalence the
 * recognition legibility gate matches with, so a correctly-written small
 * kana (which ML Kit inevitably reads as its full-size twin) is not capped
 * as "unrecognized".
 */
class KanaLookalikesTest {

    @Test
    fun `small kana match their regular twins, both scripts`() {
        listOf(
            "ぁ" to "あ", "ぃ" to "い", "ぅ" to "う", "ぇ" to "え", "ぉ" to "お",
            "っ" to "つ", "ゃ" to "や", "ゅ" to "ゆ", "ょ" to "よ",
            "ァ" to "ア", "ッ" to "ツ", "ャ" to "ヤ", "ュ" to "ユ", "ョ" to "ヨ",
        ).forEach { (small, regular) ->
            assertTrue("$small should match $regular", KanaLookalikes.matches(small, regular))
            assertTrue("$regular should match $small", KanaLookalikes.matches(regular, small))
        }
    }

    @Test
    fun `shape-identical cross-script pairs match`() {
        assertTrue(KanaLookalikes.matches("へ", "ヘ"))
        assertTrue(KanaLookalikes.matches("リ", "り"))
    }

    @Test
    fun `distinct kana do not match`() {
        assertFalse(KanaLookalikes.matches("あ", "お"))
        assertFalse(KanaLookalikes.matches("つ", "う"))
        assertFalse(KanaLookalikes.matches("シ", "ツ"))
        assertFalse(KanaLookalikes.matches("ソ", "ン"))
        assertFalse(KanaLookalikes.matches("り", "い"))
    }

    @Test
    fun `identical strings trivially match and canonicalize is stable`() {
        assertTrue(KanaLookalikes.matches("き", "き"))
        assertTrue(KanaLookalikes.matches("きゃ", "きや")) // per-char canonicalization
        assertFalse(KanaLookalikes.matches("きゃ", "き"))
    }
}
