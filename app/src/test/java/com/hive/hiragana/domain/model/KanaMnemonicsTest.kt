package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KanaMnemonics] — the editorial keyword+story lookup behind
 * the "How to remember" chip, the quiz Hint buttons, and the reveal cards.
 *
 * As of 2026-07-31 the whole kana inventory is covered: the 46 base hiragana,
 * the 66 non-base hiragana ([KanaMnemonics.HIRAGANA_EXTRA]... consulted via
 * [KanaMnemonics.candidatesFor]) and all 134 katakana, plus a separate
 * always-shown technical definition per special glyph ([KanaMnemonics.compositionFor]).
 * Only the suppressed silent marks (long-vowel bar, small tsu) and the
 * standalone small kana that are not deck characters stay empty.
 */
class KanaMnemonicsTest {

    /**
     * The 46 base gojūon glyphs with their romaji, hardcoded here so coverage
     * is asserted independently of the implementation's own table.
     */
    private val gojuon = listOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "wo", "ん" to "n",
    )

    /** Base-46 entries with romaji, for the sound-anchor + coverage tests. */
    private val gojuonEntries: List<Triple<String, String, KanaMnemonic>>
        get() = gojuon.flatMap { (glyph, romaji) ->
            KanaMnemonics.candidatesFor(glyph).map { Triple(glyph, romaji, it) }
        }

    /** EVERY shipped entry across base + extra + katakana. */
    private val allEntries: List<Pair<String, KanaMnemonic>>
        get() = KanaMnemonics.allEntriesForTest()

    @Test
    fun `all 46 base gojuon kana carry a mnemonic`() {
        gojuon.forEach { (glyph, _) ->
            assertTrue("$glyph should have candidates", KanaMnemonics.candidatesFor(glyph).isNotEmpty())
            assertNotNull("$glyph should resolve a default", KanaMnemonics.mnemonicFor(glyph))
        }
    }

    @Test
    fun `every base kana offers at least one alternate for the picker`() {
        gojuon.forEach { (glyph, _) ->
            assertTrue(
                "$glyph should ship alternates alongside its default",
                KanaMnemonics.candidatesFor(glyph).size >= 2,
            )
        }
    }

    @Test
    fun `a chosen keyword wins and unknown choices fall back to the default`() {
        val default = KanaMnemonics.mnemonicFor("あ")!!
        val alternate = KanaMnemonics.candidatesFor("あ")[1]
        assertEquals(default, KanaMnemonics.mnemonicFor("あ", null))
        assertEquals(alternate, KanaMnemonics.mnemonicFor("あ", alternate.keyword))
        assertEquals(default, KanaMnemonics.mnemonicFor("あ", "NO SUCH KEYWORD"))
    }

    @Test
    fun `at least the ten promised starter overlays exist`() {
        val overlays = gojuon.count { (glyph, _) ->
            KanaMnemonics.candidatesFor(glyph).any { it.overlay != null }
        }
        assertTrue("expected >= 10 overlays, found $overlays", overlays >= 10)
    }

    // ---- derived kana now resolve (baked from the owner's picks 2026-07-31) ----

    @Test
    fun `voiced and handakuten kana resolve, default is the owner pick`() {
        assertEquals("GARGOYLE", KanaMnemonics.mnemonicFor("が")!!.keyword)
        assertEquals("PARASOL", KanaMnemonics.mnemonicFor("ぱ")!!.keyword)
        listOf("ぎ", "ざ", "だ", "ば", "ぴ", "ず", "ぶ", "ガ", "ザ", "バ", "パ")
            .forEach { assertNotNull("$it should have a mnemonic", KanaMnemonics.mnemonicFor(it)) }
    }

    @Test
    fun `yoon composites resolve`() {
        assertEquals("KAYAK", KanaMnemonics.mnemonicFor("きゃ")!!.keyword)
        listOf("しゅ", "ちょ", "ぎょ", "ぴゃ", "キャ", "ファ")
            .forEach { assertNotNull("$it should have a mnemonic", KanaMnemonics.mnemonicFor(it)) }
    }

    @Test
    fun `katakana resolves, including base, small, vu and archaic`() {
        assertEquals("KATANA", KanaMnemonics.mnemonicFor("カ")!!.keyword)
        listOf("ア", "シ", "ン", "ヲ", "ァ", "ヴ", "ヰ", "ヱ")
            .forEach { assertNotNull("$it should have a mnemonic", KanaMnemonics.mnemonicFor(it)) }
    }

    @Test
    fun `hiragana small vowels vu and archaic resolve`() {
        listOf("ぁ", "ぃ", "ぅ", "ぇ", "ぉ", "ゔ", "ゐ", "ゑ")
            .forEach { assertNotNull("$it should have a mnemonic", KanaMnemonics.mnemonicFor(it)) }
    }

    @Test
    fun `suppressed silent marks and non-deck small kana stay empty`() {
        listOf("ー", "っ", "ッ", "ゃ", "ゅ", "ょ").forEach {
            assertNull("$it should have no mnemonic", KanaMnemonics.mnemonicFor(it))
            assertTrue("$it should have no candidates", KanaMnemonics.candidatesFor(it).isEmpty())
        }
    }

    // ---- technical definition (always-shown Structure line) ----

    @Test
    fun `special glyphs carry a technical definition, base kana do not`() {
        listOf("が", "ポ", "きゅ", "ファ", "ぁ", "ゔ", "ゐ", "ヴ").forEach {
            assertNotNull("$it needs a composition", KanaMnemonics.compositionFor(it))
        }
        listOf("あ", "ん", "カ", "キ", "サ", "ン").forEach {
            assertNull("$it is a base kana and needs no composition", KanaMnemonics.compositionFor(it))
        }
    }

    @Test
    fun `every technical definition avoids kana glyphs and dashes`() {
        val kana = Regex("[\\u3040-\\u30FF]")
        KanaMnemonics.KANA_COMPOSITION.forEach { (glyph, def) ->
            assertTrue("$glyph definition is blank", def.isNotBlank())
            assertTrue("$glyph definition contains a kana glyph", !kana.containsMatchIn(def))
            assertTrue("$glyph definition contains an em/en dash", '—' !in def && '–' !in def)
        }
    }

    // ---- copy invariants over the FULL shipped set ----

    @Test
    fun `keywords are non-blank uppercase and stories non-blank`() {
        allEntries.forEach { (glyph, m) ->
            assertTrue("$glyph: blank keyword", m.keyword.isNotBlank())
            assertEquals("$glyph: keyword must be uppercase", m.keyword.uppercase(), m.keyword)
            assertTrue("$glyph: blank story", m.story.isNotBlank())
        }
    }

    @Test
    fun `stories respect the 160-character cap`() {
        allEntries.forEach { (glyph, m) ->
            assertTrue(
                "$glyph ${m.keyword}: story is ${m.story.length} chars (cap 160)",
                m.story.length <= 160,
            )
        }
    }

    @Test
    fun `no em or en dashes and no semicolons in any copy`() {
        allEntries.forEach { (glyph, m) ->
            val text = m.keyword + m.story
            assertTrue("$glyph ${m.keyword}: em/en dash in copy", '—' !in text && '–' !in text)
            assertTrue("$glyph ${m.keyword}: semicolon in story", ';' !in m.story)
        }
    }

    @Test
    fun `stories and keywords never contain kana glyphs`() {
        val kana = Regex("[\\u3040-\\u30FF]")
        allEntries.forEach { (glyph, m) ->
            assertTrue(
                "$glyph ${m.keyword}: copy contains a kana glyph",
                !kana.containsMatchIn(m.keyword + m.story),
            )
        }
    }

    @Test
    fun `keywords are unique across every shipped entry`() {
        val keywords = allEntries.map { (_, m) -> m.keyword.uppercase() }
        assertEquals(
            "duplicate keywords: ${keywords.groupBy { it }.filterValues { it.size > 1 }.keys}",
            keywords.distinct().size, keywords.size,
        )
    }

    @Test
    fun `every base story spells out its sound anchor as a standalone word`() {
        gojuonEntries.forEach { (glyph, romaji, m) ->
            val anchor = if (glyph == "を") "o" else romaji
            val bounded = Regex("(?<![a-z])${Regex.escape(anchor)}(?![a-z])")
            assertTrue(
                "$glyph ${m.keyword}: story never says the sound '$anchor' as its own word",
                bounded.containsMatchIn(m.story.lowercase()),
            )
        }
    }

    @Test
    fun `overlay path strings are syntactically valid`() {
        allEntries.forEach { (glyph, m) ->
            val overlay = m.overlay ?: return@forEach
            assertTrue("$glyph: overlay with no paths", overlay.paths.isNotEmpty())
            assertTrue("$glyph: more than 6 overlay paths", overlay.paths.size <= 6)
            overlay.paths.forEachIndexed { i, p ->
                assertValidOverlayPath("$glyph overlay path $i", p.d)
            }
        }
    }

    // ---- pure overlay-path validator (no Compose PathParser on the JVM) ----

    private val arity = mapOf('M' to 2, 'L' to 2, 'C' to 6, 'Q' to 4, 'Z' to 0)

    private val pathToken = Regex("[A-Za-z]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

    private fun assertValidOverlayPath(context: String, d: String) {
        val tokens = pathToken.findAll(d).map { it.value }.toList()
        assertTrue("$context: empty path", tokens.isNotEmpty())
        assertEquals("$context: first command must be M", "M", tokens.first())
        var i = 0
        while (i < tokens.size) {
            val cmd = tokens[i]
            assertTrue(
                "$context: '$cmd' is not an allowed absolute command (M/L/C/Q/Z)",
                cmd.length == 1 && cmd[0] in arity,
            )
            i++
            repeat(arity.getValue(cmd[0])) {
                assertTrue("$context: missing coordinate after $cmd", i < tokens.size)
                val v = tokens[i].toDoubleOrNull()
                assertNotNull("$context: '${tokens[i]}' is not a number", v)
                assertTrue(
                    "$context: coordinate ${tokens[i]} outside the 109 viewBox",
                    v!!.isFinite() && v >= -2.0 && v <= 111.0,
                )
                i++
            }
        }
    }
}
