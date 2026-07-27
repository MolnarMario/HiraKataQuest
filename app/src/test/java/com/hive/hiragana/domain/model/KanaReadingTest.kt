package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KanaReadingTest {

    // A minimal deck-like lookup covering the kana used below (hiragana +
    // the few katakana the long-vowel/gemination cases need).
    private val romaji = mapOf(
        "か" to "ka", "う" to "u", "こ" to "ko", "き" to "ki", "て" to "te",
        "が" to "ga", "さ" to "sa", "し" to "shi",
        "コ" to "ko", "ヒ" to "hi", "ラ" to "ra", "メ" to "me", "ン" to "n",
        "マ" to "ma", "チ" to "chi",
        "きょ" to "kyo", // composite units resolve as one lookup
    )
    private fun read(kana: String) = KanaReading.spacedRomaji(kana) { romaji[it] }

    @Test
    fun `ordinary word is unchanged`() {
        assertEquals("ka u", read("かう"))
        assertEquals("kyo u", read("きょう"))
    }

    @Test
    fun `sokuon geminates the following consonant`() {
        assertEquals("ki tte", read("きって"))
        assertEquals("ga kko u", read("がっこう"))
        assertEquals("sa sshi", read("さっし"))
    }

    @Test
    fun `sokuon before a ch sound uses t (tchi)`() {
        assertEquals("ma tchi", read("マッチ"))
    }

    @Test
    fun `long-vowel mark lengthens the previous vowel`() {
        assertEquals("koo hii", read("コーヒー"))
    }

    @Test
    fun `long-vowel mark after a kana not in the deck falls back to the glyph`() {
        assertEquals("raa me n", read("ラーメン"))
    }

    @Test
    fun `unknown unit falls back to the glyph itself`() {
        assertEquals("ka ゑ", read("かゑ"))
    }
}
