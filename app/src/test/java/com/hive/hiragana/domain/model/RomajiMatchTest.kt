package com.hive.hiragana.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiMatchTest {

    @Test
    fun `exact match`() {
        assertTrue(RomajiMatch.matches("ka", "ka"))
    }

    @Test
    fun `case and whitespace insensitive`() {
        assertTrue(RomajiMatch.matches("  KA ", "ka"))
        // the deck stores word readings space-joined per kana
        assertTrue(RomajiMatch.matches("kasa", "ka sa"))
        assertTrue(RomajiMatch.matches("ka sa", "ka sa"))
    }

    @Test
    fun `hepburn and kunrei spellings are equivalent`() {
        assertTrue(RomajiMatch.matches("shi", "si"))
        assertTrue(RomajiMatch.matches("si", "shi"))
        assertTrue(RomajiMatch.matches("chi", "ti"))
        assertTrue(RomajiMatch.matches("tsu", "tu"))
        assertTrue(RomajiMatch.matches("fu", "hu"))
        assertTrue(RomajiMatch.matches("ji", "zi"))
    }

    @Test
    fun `yoon digraphs fold consistently`() {
        assertTrue(RomajiMatch.matches("sha", "sya"))
        assertTrue(RomajiMatch.matches("cha", "tya"))
        assertTrue(RomajiMatch.matches("ju", "zyu"))
    }

    @Test
    fun `macrons are stripped to base vowels`() {
        assertTrue(RomajiMatch.matches("tōkyō", "toukyou".let { "tokyo" })) // tōkyō vs tokyo
        assertTrue(RomajiMatch.matches("rāmen", "ramen"))
    }

    @Test
    fun `wrong reading does not match`() {
        assertFalse(RomajiMatch.matches("ka", "sa"))
        assertFalse(RomajiMatch.matches("neko", "inu"))
    }

    @Test
    fun `blank input never matches`() {
        assertFalse(RomajiMatch.matches("", "ka"))
        assertFalse(RomajiMatch.matches("   ", "ka"))
    }

    @Test
    fun `wapuro jya jyu jyo fold to the ja row`() {
        assertTrue(RomajiMatch.matches("jya", "zya"))
        assertTrue(RomajiMatch.matches("jyu", "zyu"))
        assertTrue(RomajiMatch.matches("jyo", "zyo"))
        // and the already-supported spellings still agree with it
        assertTrue(RomajiMatch.matches("jya", "ja"))
    }

    @Test
    fun `word-final n typed nn still matches`() {
        // みかん reading built by KanaReading is "mi ka n"
        assertTrue(RomajiMatch.matches("mikann", "mi ka n"))
        assertTrue(RomajiMatch.matches("mikan", "mi ka n"))
    }

    @Test
    fun `geminated and long-vowel readings match their natural romaji`() {
        // The readings below are what KanaReading now produces (see KanaReadingTest).
        assertTrue(RomajiMatch.matches("kitte", "ki tte"))
        assertTrue(RomajiMatch.matches("gakkou", "ga kko u"))
        assertTrue(RomajiMatch.matches("koohii", "koo hii"))
        // the short, long-vowel-dropping spelling is (correctly) not accepted
        assertFalse(RomajiMatch.matches("kohi", "koo hii"))
    }

    @Test
    fun `d-row foreign sounds stay literal (di is not folded to zi)`() {
        // ディ / ドゥ katakana keep "di"/"du" — a blanket d→z fold would break them.
        assertTrue(RomajiMatch.matches("di", "di"))
        assertFalse(RomajiMatch.matches("zi", "di"))
        assertFalse(RomajiMatch.matches("zu", "du"))
    }

    @Test
    fun `wo accepts its spoken reading o, one-directional`() {
        // を is spelled "wo" in the deck but pronounced plain "o" (the
        // mnemonic copy teaches exactly that), so typing what you hear passes.
        assertTrue(RomajiMatch.matches("o", "wo"))
        assertTrue(RomajiMatch.matches("wo", "wo"))
        // The reverse stays wrong: お is never spelled wo.
        assertFalse(RomajiMatch.matches("wo", "o"))
    }
}
