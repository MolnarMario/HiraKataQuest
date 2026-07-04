package com.hive.hiragana.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KanaChartBuilder]: chart placement is derived from romaji +
 * kana classification, so it's fully testable without instrumentation.
 */
class KanaChartTest {

    private fun ch(glyph: String, romaji: String) = HiraganaCharacter(
        character = glyph,
        romaji = romaji,
        frequency = 0,
        svgPath = "",
        strokeImage = "",
        strokes = listOf("M0 0"),
    )

    // The base gojūon, in gojūon order (46 entries incl. ん).
    private val base = listOf(
        ch("あ", "a"), ch("い", "i"), ch("う", "u"), ch("え", "e"), ch("お", "o"),
        ch("か", "ka"), ch("き", "ki"), ch("く", "ku"), ch("け", "ke"), ch("こ", "ko"),
        ch("さ", "sa"), ch("し", "shi"), ch("す", "su"), ch("せ", "se"), ch("そ", "so"),
        ch("た", "ta"), ch("ち", "chi"), ch("つ", "tsu"), ch("て", "te"), ch("と", "to"),
        ch("な", "na"), ch("に", "ni"), ch("ぬ", "nu"), ch("ね", "ne"), ch("の", "no"),
        ch("は", "ha"), ch("ひ", "hi"), ch("ふ", "fu"), ch("へ", "he"), ch("ほ", "ho"),
        ch("ま", "ma"), ch("み", "mi"), ch("む", "mu"), ch("め", "me"), ch("も", "mo"),
        ch("や", "ya"), ch("ゆ", "yu"), ch("よ", "yo"),
        ch("ら", "ra"), ch("り", "ri"), ch("る", "ru"), ch("れ", "re"), ch("ろ", "ro"),
        ch("わ", "wa"), ch("を", "wo"), ch("ん", "n"),
    )

    private fun KanaChart.firstTable(): TableSection =
        sections.filterIsInstance<TableSection>().first()

    private fun TableSection.row(label: String): TableRow = rows.first { it.rowLabel == label }

    @Test
    fun `base table has aligned a i u e o columns`() {
        val table = KanaChartBuilder.build(base).firstTable()
        assertEquals(listOf("a", "i", "u", "e", "o"), table.columnLabels)
        assertEquals("か", table.row("K").cells[0]?.character)
        assertEquals("こ", table.row("K").cells[4]?.character)
    }

    @Test
    fun `irregular romaji land in their cheat-sheet row and column`() {
        val table = KanaChartBuilder.build(base).firstTable()
        assertEquals("し", table.row("S").cells[1]?.character) // shi -> s row, i col
        assertEquals("ち", table.row("T").cells[1]?.character) // chi -> t row, i col
        assertEquals("つ", table.row("T").cells[2]?.character) // tsu -> t row, u col
        assertEquals("ふ", table.row("H").cells[2]?.character) // fu  -> h row, u col
    }

    @Test
    fun `ya row and wa row leave gaps for missing syllables`() {
        val table = KanaChartBuilder.build(base).firstTable()
        val ya = table.row("Y").cells
        assertEquals("や", ya[0]?.character)
        assertNull(ya[1]) // no yi
        assertEquals("ゆ", ya[2]?.character)
        assertNull(ya[3]) // no ye
        assertEquals("よ", ya[4]?.character)

        val wa = table.row("W").cells
        assertEquals("わ", wa[0]?.character)
        assertNull(wa[1])
        assertNull(wa[2])
        assertNull(wa[3])
        assertEquals("を", wa[4]?.character)
    }

    @Test
    fun `n is standalone, not in the CV table`() {
        val chart = KanaChartBuilder.build(base)
        val table = chart.firstTable()
        assertTrue(table.rows.none { r -> r.cells.any { it?.character == "ん" } })
        val flow = chart.sections.filterIsInstance<FlowSection>().flatMap { it.cells }
        assertTrue(flow.any { it.character == "ん" })
    }

    @Test
    fun `voiced section only appears when voiced kana are present`() {
        assertEquals(
            0,
            KanaChartBuilder.build(base).sections.count { it.title == "Voiced ゛゜" },
        )
        val voiced = base + listOf(
            ch("が", "ga"), ch("ざ", "za"), ch("じ", "ji"), ch("だ", "da"),
            ch("ば", "ba"), ch("ぱ", "pa"),
        )
        val table = KanaChartBuilder.build(voiced)
            .sections.filterIsInstance<TableSection>().first { it.title == "Voiced ゛゜" }
        assertEquals("じ", table.row("Z").cells[1]?.character) // ji -> z row, i col
        assertEquals("ぱ", table.row("P").cells[0]?.character) // handakuten folded in
    }

    @Test
    fun `vu gets a v row in the voiced table instead of being orphaned`() {
        val withVu = base + listOf(ch("が", "ga"), ch("ゔ", "vu"))
        val chart = KanaChartBuilder.build(withVu)
        val voiced = chart.sections.filterIsInstance<TableSection>()
            .first { it.title == "Voiced ゛゜" }
        // vu -> v row, u column (index 2)
        assertEquals("ゔ", voiced.row("V").cells[2]?.character)
        // and it is NOT sitting in a standalone flow section
        val flow = chart.sections.filterIsInstance<FlowSection>().flatMap { it.cells }
        assertTrue(flow.none { it.character == "ゔ" })
    }

    @Test
    fun `yoon builds a 3-column ya yu yo table`() {
        val withYoon = base + listOf(
            ch("きゃ", "kya"), ch("きゅ", "kyu"), ch("きょ", "kyo"),
            ch("しゃ", "sha"), ch("しゅ", "shu"), ch("しょ", "sho"),
        )
        val table = KanaChartBuilder.build(withYoon)
            .sections.filterIsInstance<TableSection>().first { it.title == "Combined ゃゅょ" }
        assertEquals(listOf("ya", "yu", "yo"), table.columnLabels)
        assertEquals("きゃ", table.row("KY").cells[0]?.character)
        assertEquals("きょ", table.row("KY").cells[2]?.character)
        assertEquals("しゅ", table.row("SH").cells[1]?.character)
    }

    @Test
    fun `extended kana go to a flow section`() {
        val withExtended = base + listOf(ch("っ", "sokuon"), ch("ファ", "fa"))
        val chart = KanaChartBuilder.build(withExtended)
        val extended = chart.sections.filterIsInstance<FlowSection>()
            .first { it.title == "Extended っ ァ" }
        assertTrue(extended.cells.any { it.character == "ファ" })
        assertTrue(extended.cells.any { it.character == "っ" })
    }
}
