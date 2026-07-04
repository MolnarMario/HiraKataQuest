package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KanaUnits] — the yōon-aware kana splitter that backs
 * vocabulary unlock and the tracing canvas's per-cell layout. The critical
 * property: a base kana followed by a small ゃ/ゅ/ょ fuses into ONE unit, while
 * a full-size や/ゆ/よ does NOT (a common off-by-one trap).
 */
class KanaUnitsTest {

    @Test
    fun `plain kana each become their own unit`() {
        assertEquals(listOf("か", "う"), KanaUnits.split("かう"))
        assertEquals(listOf("い", "い", "え"), KanaUnits.split("いいえ"))
    }

    @Test
    fun `a base kana plus small ya yu yo fuses into one unit`() {
        assertEquals(listOf("きょ", "う"), KanaUnits.split("きょう"))
        assertEquals(listOf("しゅ", "く", "だ", "い"), KanaUnits.split("しゅくだい"))
        assertEquals(listOf("ぎゅ", "う", "にゅ", "う"), KanaUnits.split("ぎゅうにゅう"))
    }

    @Test
    fun `katakana yoon fuse too`() {
        assertEquals(listOf("チョ", "コ"), KanaUnits.split("チョコ"))
        assertEquals(listOf("ニュ", "ー", "ス"), KanaUnits.split("ニュース"))
    }

    @Test
    fun `full-size ya yu yo do NOT fuse`() {
        // や (U+3084) is a full kana, not a small one — must stay separate.
        assertEquals(listOf("か", "や", "く"), KanaUnits.split("かやく"))
        assertEquals(listOf("き", "や"), KanaUnits.split("きや")) // き + full や, not きゃ
    }

    @Test
    fun `a small vowel fuses into an extended mora, but small tsu does not`() {
        assertEquals(listOf("ソ", "ファ"), KanaUnits.split("ソファ"))        // フ + small ァ
        assertEquals(listOf("パ", "ー", "ティ", "ー"), KanaUnits.split("パーティー")) // テ + small ィ
        assertEquals(listOf("き", "っ", "て"), KanaUnits.split("きって"))     // small tsu stands alone
    }

    @Test
    fun `isExtended flags small vowels, small tsu, and obsolete kana — not yoon or plain`() {
        assertTrue(KanaUnits.isExtended("ファ"))   // small vowel
        assertTrue(KanaUnits.isExtended("きって")) // small tsu
        assertTrue(KanaUnits.isExtended("ゐ"))     // obsolete
        assertFalse(KanaUnits.isExtended("きょう")) // yōon, not extended
        assertFalse(KanaUnits.isExtended("かう"))   // plain
    }

    @Test
    fun `containsYoon detects only small ya yu yo`() {
        assertTrue(KanaUnits.containsYoon("きょう"))
        assertTrue(KanaUnits.containsYoon("ニュース"))
        assertFalse(KanaUnits.containsYoon("かやく")) // full や
        assertFalse(KanaUnits.containsYoon("かう"))
    }

    @Test
    fun `empty string splits to nothing`() {
        assertEquals(emptyList<String>(), KanaUnits.split(""))
    }
}
