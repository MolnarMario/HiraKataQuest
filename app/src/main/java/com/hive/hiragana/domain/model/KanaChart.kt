package com.hive.hiragana.domain.model

/**
 * A gojūon-style chart model built from an (already script/opt-in-scoped) list
 * of [HiraganaCharacter]s — the data behind the Library's grid view. Pure and
 * Android-free (same discipline as `SrsScheduler` / `KanaUnits`), so it is
 * unit-tested without instrumentation.
 *
 * Sections mirror the scope the caller already applied: a section is emitted
 * only when the scoped list actually contains its characters, so the Voiced /
 * Combined (yōon) / Extended sections appear exactly when their opt-in is on
 * (the caller filters them out otherwise via `ScriptScope`).
 *
 * Layout is a real cheat-sheet chart, not a uniform grid: base and voiced kana
 * form aligned vowel-column × consonant-row tables (with gaps where a syllable
 * doesn't exist, e.g. the や row has no i/e), yōon is a 3-column ya/yu/yo table,
 * and everything that doesn't fit a consonant×vowel table (ん, the katakana
 * 長音 ー, and the whole extended set) lands in a ragged [FlowSection].
 */
data class KanaChart(val sections: List<KanaSection>)

sealed interface KanaSection {
    val title: String?
}

/**
 * An aligned sub-table: [columnLabels] across the top (vowels), one [TableRow]
 * per consonant family. A cell may be null — a gap the renderer leaves blank
 * so the columns stay aligned (this is what makes it read as a chart).
 */
data class TableSection(
    override val title: String?,
    val columnLabels: List<String>,
    val rows: List<TableRow>,
) : KanaSection

data class TableRow(
    val rowLabel: String,
    val cells: List<HiraganaCharacter?>,
)

/**
 * A ragged run of content-sized cells with no column alignment — for kana that
 * don't fit a consonant×vowel table (ん, ー, and the extended set: small tsu,
 * small vowels, ファ/ティ composites, archaic kana).
 */
data class FlowSection(
    override val title: String?,
    val cells: List<HiraganaCharacter>,
) : KanaSection

/**
 * Builds a [KanaChart] from a scoped character list. All placement is derived
 * from [HiraganaCharacter.romaji] plus [HiraganaCharacter.kind]/`isYoon`/
 * `isExtended`; anything that doesn't map cleanly is never dropped — it falls
 * into a standalone flow row, so the builder can't crash on unexpected data.
 */
object KanaChartBuilder {

    private val AIUEO = listOf("a", "i", "u", "e", "o")
    private val AIUEO_VOWELS = listOf('a', 'i', 'u', 'e', 'o')
    private val YOON_COLS = listOf("ya", "yu", "yo")
    private val YOON_VOWELS = listOf('a', 'u', 'o')

    private val BASE_ROWS = listOf("", "k", "s", "t", "n", "h", "m", "y", "r", "w")
    // Dakuten + handakuten together — they're one opt-in ("Voiced ゛゜").
    // "v" (ゔ/ヴ) is a voiced kana too, so it gets a row rather than orphaning.
    private val VOICED_ROWS = listOf("g", "z", "d", "b", "p", "v")
    private val YOON_ROWS = listOf("ky", "sh", "ch", "ny", "hy", "my", "ry", "gy", "j", "by", "py")

    /**
     * romaji prefix (syllable minus its final vowel) -> canonical chart row for
     * the base and voiced tables, folding the irregular romanizations into the
     * row a cheat sheet uses: shi→s, chi/tsu→t, fu→h, ji→z, di/du→d.
     */
    private val ROW_OF_PREFIX = mapOf(
        "" to "",
        "k" to "k", "g" to "g",
        "s" to "s", "sh" to "s", "z" to "z", "j" to "z",
        "t" to "t", "ch" to "t", "ts" to "t", "d" to "d",
        "n" to "n",
        "h" to "h", "f" to "h", "b" to "b", "p" to "p", "v" to "v",
        "m" to "m", "y" to "y", "r" to "r", "w" to "w",
    )

    fun build(chars: List<HiraganaCharacter>): KanaChart {
        val base = chars.filter { !it.isExtended && !it.isYoon && it.kind == KanaKind.BASE }
        val voiced = chars.filter { !it.isExtended && !it.isYoon && it.kind != KanaKind.BASE }
        val yoon = chars.filter { !it.isExtended && it.isYoon }
        val extended = chars.filter { it.isExtended }

        val sections = mutableListOf<KanaSection>()
        addAligned(
            out = sections, chars = base, rowKeys = BASE_ROWS,
            columnLabels = AIUEO, columnVowels = AIUEO_VOWELS,
            rowOf = { ROW_OF_PREFIX[it] }, title = null,
        )
        addAligned(
            out = sections, chars = voiced, rowKeys = VOICED_ROWS,
            columnLabels = AIUEO, columnVowels = AIUEO_VOWELS,
            rowOf = { ROW_OF_PREFIX[it] }, title = "Voiced ゛゜",
        )
        addAligned(
            out = sections, chars = yoon, rowKeys = YOON_ROWS,
            columnLabels = YOON_COLS, columnVowels = YOON_VOWELS,
            rowOf = { if (it in YOON_ROWS) it else null }, title = "Combined ゃゅょ",
        )
        if (extended.isNotEmpty()) sections.add(FlowSection("Extended っ ァ", extended))
        return KanaChart(sections)
    }

    private fun addAligned(
        out: MutableList<KanaSection>,
        chars: List<HiraganaCharacter>,
        rowKeys: List<String>,
        columnLabels: List<String>,
        columnVowels: List<Char>,
        rowOf: (String) -> String?,
        title: String?,
    ) {
        if (chars.isEmpty()) return
        val grid = HashMap<String, Array<HiraganaCharacter?>>()
        val standalone = mutableListOf<HiraganaCharacter>()

        for (c in chars) {
            val romaji = c.romaji.lowercase()
            val vowelIndex = romaji.lastOrNull()?.let { columnVowels.indexOf(it) } ?: -1
            val prefix = if (vowelIndex >= 0) romaji.dropLast(1) else romaji
            val row = if (vowelIndex >= 0) rowOf(prefix) else null
            if (row == null || row !in rowKeys) {
                standalone.add(c)
                continue
            }
            grid.getOrPut(row) { arrayOfNulls(columnLabels.size) }[vowelIndex] = c
        }

        val rows = rowKeys.mapNotNull { key ->
            grid[key]?.let { TableRow(rowLabel = key.uppercase(), cells = it.toList()) }
        }
        if (rows.isNotEmpty()) out.add(TableSection(title, columnLabels, rows))
        // ん, ー, and any unmapped kana ride along under their section as a flow row.
        if (standalone.isNotEmpty()) out.add(FlowSection(null, standalone))
    }
}
