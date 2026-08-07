package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SpecialKana] — the editorial "this isn't an ordinary
 * syllable" lookup that the badge/dialog UI keys off. Pins the covered glyphs
 * (both scripts where the mark exists in both), the exact-match rule (no NFD
 * inheritance), and a copy lint matching the [PrintDivergence]/[KanaMnemonics]
 * discipline (no em-dashes in shipped prose).
 */
class SpecialKanaTest {

    @Test
    fun `every special kana carries a note`() {
        listOf(
            "ー",                                    // long-vowel mark
            "っ", "ッ",                              // small tsu (both scripts)
            "ぁ", "ぃ", "ぅ", "ぇ", "ぉ",            // hiragana small vowels
            "ァ", "ィ", "ゥ", "ェ", "ォ",            // katakana small vowels
            "ん", "ン",                              // the single n
            "を", "ヲ",                              // the particle
        ).forEach {
            assertNotNull("$it should have a special-kana note", SpecialKana.noteFor(it))
        }
    }

    @Test
    fun `ordinary syllables carry no note`() {
        listOf("あ", "か", "つ", "ツ", "う", "ウ", "お", "ア", "コ", "ヒ").forEach {
            assertNull("$it should have no note", SpecialKana.noteFor(it))
        }
    }

    @Test
    fun `both scripts of a shared mark point at the same note`() {
        assertEquals(SpecialKana.noteFor("っ"), SpecialKana.noteFor("ッ"))
        assertEquals(SpecialKana.noteFor("ん"), SpecialKana.noteFor("ン"))
        assertEquals(SpecialKana.noteFor("を"), SpecialKana.noteFor("ヲ"))
        assertEquals(SpecialKana.noteFor("ぁ"), SpecialKana.noteFor("ァ"))
    }

    @Test
    fun `lookup is exact-glyph, not NFD — a full tsu is not the small tsu`() {
        // Unlike PrintDivergence there is no base-letter inheritance: the small
        // tsu っ is special, the full つ it decomposes near is not.
        assertNotNull(SpecialKana.noteFor("っ"))
        assertNull(SpecialKana.noteFor("つ"))
        // A voiced kana is an ordinary syllable, never a special mark.
        assertNull(SpecialKana.noteFor("が"))
    }

    @Test
    fun `only the truly soundless marks are silenced for TTS`() {
        // ー and the small tsu have no sound of their own.
        listOf("ー", "っ", "ッ").forEach {
            assertTrue("$it should be a silent mark", SpecialKana.isSilentMark(it))
        }
        // Small vowels carry a bare vowel sound; ん/を read normally; ordinary
        // kana obviously speak — none of these are silenced.
        listOf("ぁ", "ァ", "ん", "ン", "を", "ヲ", "あ", "か", "コーヒー").forEach {
            assertFalse("$it should speak", SpecialKana.isSilentMark(it))
        }
    }

    @Test
    fun `lone small vowels are spoken as their base vowel`() {
        assertEquals("あ", SpecialKana.ttsGlyph("ぁ"))
        assertEquals("い", SpecialKana.ttsGlyph("ぃ"))
        assertEquals("う", SpecialKana.ttsGlyph("ぅ"))
        assertEquals("え", SpecialKana.ttsGlyph("ぇ"))
        assertEquals("お", SpecialKana.ttsGlyph("ぉ"))
        assertEquals("ア", SpecialKana.ttsGlyph("ァ"))
        assertEquals("イ", SpecialKana.ttsGlyph("ィ"))
        assertEquals("ウ", SpecialKana.ttsGlyph("ゥ"))
        assertEquals("エ", SpecialKana.ttsGlyph("ェ"))
        assertEquals("オ", SpecialKana.ttsGlyph("ォ"))
    }

    @Test
    fun `ttsGlyph passes through everything else, including words containing a small vowel`() {
        listOf("あ", "か", "ん", "を", "ー", "っ", "ファ", "ソファ", "コーヒー").forEach {
            assertEquals(it, SpecialKana.ttsGlyph(it))
        }
    }

    @Test
    fun `placeholder-romaji marks get a readable display label`() {
        assertEquals("long vowel", SpecialKana.readingLabel("ー"))
        assertEquals("small tsu", SpecialKana.readingLabel("っ"))
        assertEquals("small tsu", SpecialKana.readingLabel("ッ"))
        assertEquals("small a", SpecialKana.readingLabel("ぁ"))
        assertEquals("small a", SpecialKana.readingLabel("ァ"))
        assertEquals("small o", SpecialKana.readingLabel("ォ"))
        // ん/を already have real, readable romaji, and ordinary kana never need
        // relabelling — so the label falls through to null (use the romaji).
        listOf("ん", "を", "あ", "か", "つ").forEach {
            assertNull("$it needs no label", SpecialKana.readingLabel(it))
        }
    }

    @Test
    fun `note copy is clean and non-empty`() {
        val notes = listOf("ー", "っ", "ぁ", "ん", "を").map { SpecialKana.noteFor(it)!! }
        notes.forEach { note ->
            assertTrue("title should be non-blank", note.title.isNotBlank())
            assertTrue("body should be substantial", note.body.length >= 40)
            // House style: no em-dashes in shipped prose.
            assertFalse("title has an em-dash: ${note.title}", note.title.contains('—'))
            assertFalse("body has an em-dash: ${note.body}", note.body.contains('—'))
        }
    }
}
