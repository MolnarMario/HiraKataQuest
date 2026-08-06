package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaAnswerMatchTest {

    @Test
    fun `exact kana matches`() {
        assertTrue(KanaAnswerMatch.matches("あ", "あ"))
        assertTrue(KanaAnswerMatch.matches("ネコ", "ネコ"))
        assertTrue(KanaAnswerMatch.matches("きゃ", "きゃ"))
    }

    @Test
    fun `a different kana does not match`() {
        assertFalse(KanaAnswerMatch.matches("い", "あ"))
        // the voiced twin is a different character, not a typo
        assertFalse(KanaAnswerMatch.matches("き", "ぎ"))
        assertFalse(KanaAnswerMatch.matches("ぎ", "き"))
        // and so is the small form
        assertFalse(KanaAnswerMatch.matches("つ", "っ"))
    }

    @Test
    fun `half-width katakana is folded to full width`() {
        // What a half-width IME emits for ネコ / パン.
        assertTrue(KanaAnswerMatch.matches("ﾈｺ", "ネコ"))
        assertTrue(KanaAnswerMatch.matches("ﾊﾟﾝ", "パン"))
    }

    @Test
    fun `whitespace is ignored`() {
        assertTrue(KanaAnswerMatch.matches("  ねこ ", "ねこ"))
        assertTrue(KanaAnswerMatch.matches("ね こ", "ねこ"))
        // the ideographic space a Japanese IME produces on its space bar
        assertTrue(KanaAnswerMatch.matches("ね　こ", "ねこ"))
    }

    @Test
    fun `a hyphen is accepted for the long vowel mark`() {
        assertTrue(KanaAnswerMatch.matches("コ-ヒ-", "コーヒー"))
        assertTrue(KanaAnswerMatch.matches("コーヒー", "コーヒー"))
    }

    @Test
    fun `hiragana does not match katakana`() {
        // The whole point of the reverse quiz is which SCRIPT the kana is in.
        assertFalse(KanaAnswerMatch.matches("ねこ", "ネコ"))
        assertFalse(KanaAnswerMatch.matches("ア", "あ"))
    }

    @Test
    fun `romaji is never accepted`() {
        assertFalse(KanaAnswerMatch.matches("neko", "ねこ"))
        assertFalse(KanaAnswerMatch.matches("a", "あ"))
    }

    @Test
    fun `blank input never matches`() {
        assertFalse(KanaAnswerMatch.matches("", "あ"))
        assertFalse(KanaAnswerMatch.matches("   ", "あ"))
        // even against a blank expectation — there is nothing to be right about
        assertFalse(KanaAnswerMatch.matches("", ""))
    }

    @Test
    fun `canonical is stable for already-clean kana`() {
        assertEquals("ねこ", KanaAnswerMatch.canonical("ねこ"))
        assertEquals("ネコ", KanaAnswerMatch.canonical("ﾈｺ"))
        assertEquals("ー", KanaAnswerMatch.canonical("-"))
    }
}
