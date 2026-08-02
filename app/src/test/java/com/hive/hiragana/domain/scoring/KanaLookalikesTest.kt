package com.hive.hiragana.domain.scoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KanaLookalikes] — the shape-twin equivalence the
 * recognition legibility gate matches with, so a correctly-written glyph
 * that ML Kit reads as its homograph (a small kana's full-size twin, or the
 * kanji a katakana was carved from) is not discounted as "unrecognized".
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

    /**
     * The device-found regression: katakana エ written cleanly (form 88 /
     * fit 85) displayed 65 because ML Kit top-ranked the kanji 工.
     */
    @Test
    fun `katakana match the kanji they are homographic with`() {
        listOf(
            "工" to "エ", "力" to "カ", "口" to "ロ", "囗" to "ロ", "二" to "ニ",
            "八" to "ハ", "夕" to "タ", "卜" to "ト", "厶" to "ム", "千" to "チ",
            "丿" to "ノ", "亻" to "イ", "匕" to "ヒ", "儿" to "ル", "彡" to "ミ",
            "三" to "ミ", "才" to "オ", "又" to "ヌ", "冖" to "ワ", "宀" to "ウ",
            "勹" to "ク", "乂" to "メ", "彐" to "ヨ", "乚" to "レ", "乜" to "セ",
            "廾" to "サ", "木" to "ホ", "毛" to "モ", "井" to "ヰ",
        ).forEach { (kanji, kana) ->
            assertTrue("$kanji should match $kana", KanaLookalikes.matches(kanji, kana))
            assertTrue("$kana should match $kanji", KanaLookalikes.matches(kana, kanji))
        }
    }

    /** 刂 resolves through リ to the り/リ class, so a class is reachable from any member. */
    @Test
    fun `kanji resolve through to the cross-script representative`() {
        assertTrue(KanaLookalikes.matches("刂", "リ"))
        assertTrue(KanaLookalikes.matches("刂", "り"))
    }

    @Test
    fun `distinct kana do not match`() {
        assertFalse(KanaLookalikes.matches("あ", "お"))
        assertFalse(KanaLookalikes.matches("つ", "う"))
        assertFalse(KanaLookalikes.matches("シ", "ツ"))
        assertFalse(KanaLookalikes.matches("ソ", "ン"))
        assertFalse(KanaLookalikes.matches("り", "い"))
    }

    /**
     * The invariant that keeps the homoglyph table safe: adding kanji must
     * never make two DIFFERENT kana equivalent, or the gate would accept the
     * wrong character. These are the pairs a learner actually confuses.
     */
    @Test
    fun `homoglyph table never merges two distinct kana`() {
        listOf(
            "ス" to "ヌ", // both are near 又; only ヌ may claim it
            "ク" to "タ", // differ by one stroke
            "ウ" to "ワ", // 宀 vs 冖
            "シ" to "ツ",
            "ソ" to "ン",
            "コ" to "ユ",
            "ホ" to "モ", // 木 vs 毛
            "エ" to "ニ", // 工 vs 二
            "オ" to "ケ",
        ).forEach { (a, b) ->
            assertFalse("$a must not match $b", KanaLookalikes.matches(a, b))
        }
    }

    @Test
    fun `homoglyph keys are not kana and values are`() {
        KanaLookalikes.kanjiHomoglyphs.forEach { (kanji, kana) ->
            assertFalse("$kanji is a key and must not itself be kana", kanji.isKana())
            assertTrue("$kana is a value and must be kana", kana.isKana())
        }
    }

    @Test
    fun `identical strings trivially match and canonicalize is stable`() {
        assertTrue(KanaLookalikes.matches("き", "き"))
        assertTrue(KanaLookalikes.matches("きゃ", "きや")) // per-char canonicalization
        assertFalse(KanaLookalikes.matches("きゃ", "き"))
    }

    private fun Char.isKana(): Boolean = this in 'ぁ'..'ゟ' || this in '゠'..'ヿ'
}
