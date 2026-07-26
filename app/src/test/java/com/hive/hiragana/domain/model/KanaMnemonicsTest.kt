package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KanaMnemonics] — the editorial keyword+story lookup behind
 * the "How to remember" chip, the quiz Hint buttons, and the reveal cards.
 * Pins the EXACT-glyph semantics (derived kana return null until real
 * derivation rules land — inheriting a base keyword would anchor the wrong
 * sound), the default-first alternates contract behind the in-app "More ways
 * to remember" picker, and the copy rules every shipped entry must obey
 * (length cap, no em/en dashes, sound anchor present, globally unique
 * keywords, valid overlay paths).
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

    /** Every shipped entry as (glyph, romaji, mnemonic), defaults and alternates. */
    private val allEntries: List<Triple<String, String, KanaMnemonic>>
        get() = gojuon.flatMap { (glyph, romaji) ->
            KanaMnemonics.candidatesFor(glyph).map { Triple(glyph, romaji, it) }
        }

    @Test
    fun `all 46 base gojuon kana carry a mnemonic`() {
        gojuon.forEach { (glyph, _) ->
            assertTrue(
                "$glyph should have candidates",
                KanaMnemonics.candidatesFor(glyph).isNotEmpty(),
            )
            assertNotNull("$glyph should resolve a default", KanaMnemonics.mnemonicFor(glyph))
        }
    }

    @Test
    fun `every kana offers at least one alternate for the picker`() {
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

    @Test
    fun `voiced variants return null — exact match only, no NFD inheritance`() {
        // Unlike PrintDivergence: inheriting き's "ki" keyword onto ぎ (gi)
        // would anchor the wrong sound. Derived kana wait for derive().
        listOf("ぎ", "ざ", "だ", "ば", "ぱ", "ず", "ぶ").forEach {
            assertNull("$it should have no mnemonic yet", KanaMnemonics.mnemonicFor(it))
            assertTrue(KanaMnemonics.candidatesFor(it).isEmpty())
        }
    }

    @Test
    fun `yoon composites return null`() {
        listOf("きゃ", "しゅ", "ちょ", "ぎょ", "ぴゃ").forEach {
            assertNull("$it should have no mnemonic yet", KanaMnemonics.mnemonicFor(it))
        }
    }

    @Test
    fun `katakana returns null`() {
        listOf("ア", "カ", "シ", "ン", "キャ", "ファ").forEach {
            assertNull("$it should have no mnemonic yet", KanaMnemonics.mnemonicFor(it))
        }
    }

    @Test
    fun `small and extended kana return null`() {
        listOf("っ", "ゃ", "ぁ", "ゔ", "ゐ", "ゑ").forEach {
            assertNull("$it should have no mnemonic yet", KanaMnemonics.mnemonicFor(it))
        }
    }

    @Test
    fun `keywords are non-blank uppercase and stories non-blank`() {
        allEntries.forEach { (glyph, _, m) ->
            assertTrue("$glyph: blank keyword", m.keyword.isNotBlank())
            assertEquals(
                "$glyph: keyword must be stored uppercase",
                m.keyword.uppercase(), m.keyword,
            )
            assertTrue("$glyph: blank story", m.story.isNotBlank())
        }
    }

    @Test
    fun `stories respect the 160-character cap`() {
        allEntries.forEach { (glyph, _, m) ->
            assertTrue(
                "$glyph ${m.keyword}: story is ${m.story.length} chars (cap 160)",
                m.story.length <= 160,
            )
        }
    }

    @Test
    fun `no em or en dashes and no semicolons in any copy`() {
        allEntries.forEach { (glyph, _, m) ->
            val text = m.keyword + m.story
            assertTrue("$glyph ${m.keyword}: em/en dash in copy", '—' !in text && '–' !in text)
            assertTrue("$glyph ${m.keyword}: semicolon in story", ';' !in m.story)
        }
    }

    @Test
    fun `every story spells out its sound anchor as a standalone word`() {
        allEntries.forEach { (glyph, romaji, m) ->
            // を anchors the sound "o" (its reading), not the spelling "wo".
            // Word-bounded so "ka" inside "kana" or "no" inside "knot" can't
            // satisfy the rule by accident (single-vowel anchors stay weakly
            // testable — "a" the article matches — but all 40 multi-letter
            // anchors are pinned for real).
            val anchor = if (glyph == "を") "o" else romaji
            val bounded = Regex("(?<![a-z])${Regex.escape(anchor)}(?![a-z])")
            assertTrue(
                "$glyph ${m.keyword}: story never says the sound '$anchor' as its own word",
                bounded.containsMatchIn(m.story.lowercase()),
            )
        }
    }

    @Test
    fun `stories and keywords never contain kana glyphs`() {
        // The writing quiz's pre-answer hint promises "no kana, no specimen"
        // (the shape IS the hidden answer), and the dialog renders stories
        // verbatim — so the copy itself must reference other kana by romaji
        // ("its twin ro"), never by glyph. Caught by review: ん's story
        // originally displayed ん itself in the hint.
        val kana = Regex("[\\u3040-\\u30FF]")
        allEntries.forEach { (glyph, _, m) ->
            assertTrue(
                "$glyph ${m.keyword}: copy contains a kana glyph",
                !kana.containsMatchIn(m.keyword + m.story),
            )
        }
    }

    @Test
    fun `keywords are unique across every shipped entry`() {
        // Globally, not just per kana: the same keyword on two kana would
        // anchor one word to two different sounds.
        val keywords = allEntries.map { (_, _, m) -> m.keyword.uppercase() }
        assertEquals(
            "duplicate keywords: ${keywords.groupBy { it }.filterValues { it.size > 1 }.keys}",
            keywords.distinct().size, keywords.size,
        )
    }

    @Test
    fun `overlay path strings are syntactically valid`() {
        allEntries.forEach { (glyph, _, m) ->
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

    /**
     * Enforces the [OverlayPath] contract: absolute M/L/C/Q/Z only, one
     * command letter per segment with exact arity, finite coordinates within
     * the 109 viewBox (2-unit tolerance). The tokenizer mirrors
     * tools/render_mnemonic_overlays.py's TOKEN_RE exactly, so the two
     * validators can't drift on what counts as a token.
     */
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
