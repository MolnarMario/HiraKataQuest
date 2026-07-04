package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the NFD-based voiced/semi-voiced classifier [KanaKind.of] and
 * its two derived consumers, [HiraganaCharacter.kind] and
 * [VocabularyWord.usesDiacritics]. This is the rule the voiced-kana opt-in
 * gate keys off, so it is pinned directly.
 */
class KanaKindTest {

    @Test
    fun `base kana classify as BASE`() {
        listOf("あ", "か", "ん", "ア", "カ", "ン", "ー").forEach {
            assertEquals("$it should be BASE", KanaKind.BASE, KanaKind.of(it))
        }
    }

    @Test
    fun `dakuten kana classify as DAKUTEN, both scripts`() {
        listOf("が", "ざ", "だ", "ば", "ぢ", "づ", "ガ", "ザ", "ド", "ブ").forEach {
            assertEquals("$it should be DAKUTEN", KanaKind.DAKUTEN, KanaKind.of(it))
        }
    }

    @Test
    fun `handakuten kana classify as HANDAKUTEN, both scripts`() {
        listOf("ぱ", "ぴ", "ぷ", "ぺ", "ぽ", "パ", "ポ").forEach {
            assertEquals("$it should be HANDAKUTEN", KanaKind.HANDAKUTEN, KanaKind.of(it))
        }
    }

    @Test
    fun `HiraganaCharacter derives its kind from the glyph`() {
        fun char(glyph: String) = HiraganaCharacter(
            id = "x", character = glyph, romaji = "x", frequency = 1,
            svgPath = "M0,0", strokeImage = "x.png", strokes = listOf("M0,0"),
        )
        assertEquals(KanaKind.BASE, char("か").kind)
        assertEquals(KanaKind.DAKUTEN, char("が").kind)
        assertEquals(KanaKind.HANDAKUTEN, char("ぱ").kind)
    }

    @Test
    fun `VocabularyWord flags words that use voiced kana`() {
        assertFalse(VocabularyWord.fromKana("かお", "face").usesDiacritics)      // base only
        assertTrue(VocabularyWord.fromKana("かぎ", "key").usesDiacritics)        // ぎ dakuten
        assertTrue(VocabularyWord.fromKana("めがね", "glasses").usesDiacritics)  // が dakuten
        assertTrue(VocabularyWord.fromKana("ゲーム", "game").usesDiacritics)     // ゲ katakana dakuten
        assertFalse(VocabularyWord.fromKana("コーヒー", "coffee").usesDiacritics) // katakana but no voicing
    }
}
