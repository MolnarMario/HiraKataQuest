package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the two script-classification rules that back the katakana
 * section: [Script.fromKey] (persisted-value decode, default hiragana) and
 * [VocabularyWord.script] (kana → script, the single word→script rule). These
 * are pure and have no Android dependency.
 */
class ScriptTest {

    // ---- Script.fromKey -------------------------------------------------------

    @Test
    fun `fromKey decodes the two known keys`() {
        assertEquals(Script.HIRAGANA, Script.fromKey("hiragana"))
        assertEquals(Script.KATAKANA, Script.fromKey("katakana"))
    }

    @Test
    fun `fromKey defaults to hiragana for null, blank, or unknown values`() {
        // Covers first-run (no stored value) and any future/garbage pref value.
        assertEquals(Script.HIRAGANA, Script.fromKey(null))
        assertEquals(Script.HIRAGANA, Script.fromKey(""))
        assertEquals(Script.HIRAGANA, Script.fromKey("romaji"))
    }

    @Test
    fun `key round-trips through fromKey`() {
        Script.entries.forEach { script ->
            assertEquals(script, Script.fromKey(script.key))
        }
    }

    // ---- VocabularyWord.script ------------------------------------------------

    @Test
    fun `words written in hiragana classify as hiragana`() {
        assertEquals(Script.HIRAGANA, VocabularyWord.fromKana("かう", "to buy").script)
        assertEquals(Script.HIRAGANA, VocabularyWord.fromKana("おはよう", "good morning").script)
    }

    @Test
    fun `words containing any katakana classify as katakana`() {
        assertEquals(Script.KATAKANA, VocabularyWord.fromKana("コーヒー", "coffee").script)
        assertEquals(Script.KATAKANA, VocabularyWord.fromKana("ケーキ", "cake").script)
        assertEquals(Script.KATAKANA, VocabularyWord.fromKana("ラーメン", "ramen").script)
    }

    @Test
    fun `the long-vowel mark alone counts as katakana`() {
        // ー (U+30FC) lives in the Katakana Unicode block, so a word using it
        // is katakana — this is what lets loanwords like コーヒー unlock once
        // the ー character (kata_choonpu) is learned.
        assertEquals(Script.KATAKANA, VocabularyWord.fromKana("ー", "long vowel").script)
    }

    @Test
    fun `words with no kana at all default to hiragana`() {
        // Degenerate inputs (empty, ASCII) have no katakana-block char, so the
        // rule falls back to hiragana rather than throwing.
        assertEquals(Script.HIRAGANA, VocabularyWord.fromKana("", "empty").script)
        assertEquals(Script.HIRAGANA, VocabularyWord.fromKana("abc", "ascii").script)
    }
}
