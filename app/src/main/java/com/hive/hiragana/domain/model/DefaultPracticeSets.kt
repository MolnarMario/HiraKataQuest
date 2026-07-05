package com.hive.hiragana.domain.model

/**
 * A single default practice set to seed, resolved against the loaded deck.
 * [members] are glyph strings (for [PracticeSetType.CHARACTERS]) or kana
 * strings (for [PracticeSetType.WORDS]).
 */
data class DefaultSetSpec(
    val name: String,
    val type: PracticeSetType,
    val script: Script,
    val members: List<String>,
)

/**
 * The built-in default practice sets seeded once into every user's library
 * (see `PracticeViewModel.seedDefaultSetsIfNeeded`). They replace the removed
 * guided-study flow with a structured, ready-made set of things to practice.
 *
 * For each script we seed:
 *  - the 10 gojūon **rows** (Vowels, K, S … W+ん) — hardcoded glyph lists;
 *  - three **advanced categories** (Voiced, Combined, Extended) — derived from
 *    the loaded deck by the same classifiers [ScriptScope] uses, so they always
 *    match the data and never need re-typing ~130 composite glyphs;
 *  - a few **starter word sets** — curated kana lists, filtered to the words
 *    actually present in that script's `vocabulary.json`.
 *
 * Seeded sets are ordinary editable sets afterwards (rename/delete/edit).
 */
object DefaultPracticeSets {

    private val HIRAGANA_ROWS: List<Pair<String, String>> = listOf(
        "Vowels" to "あいうえお",
        "K-row (か)" to "かきくけこ",
        "S-row (さ)" to "さしすせそ",
        "T-row (た)" to "たちつてと",
        "N-row (な)" to "なにぬねの",
        "H-row (は)" to "はひふへほ",
        "M-row (ま)" to "まみむめも",
        "Y-row (や)" to "やゆよ",
        "R-row (ら)" to "らりるれろ",
        "W-row (わ)" to "わをん",
    )

    private val KATAKANA_ROWS: List<Pair<String, String>> = listOf(
        "Vowels" to "アイウエオ",
        "K-row (カ)" to "カキクケコ",
        "S-row (サ)" to "サシスセソ",
        "T-row (タ)" to "タチツテト",
        "N-row (ナ)" to "ナニヌネノ",
        "H-row (ハ)" to "ハヒフヘホ",
        "M-row (マ)" to "マミムメモ",
        "Y-row (ヤ)" to "ヤユヨ",
        "R-row (ラ)" to "ラリルレロ",
        "W-row (ワ)" to "ワヲン",
    )

    private val HIRAGANA_WORDS: List<Pair<String, List<String>>> = listOf(
        "Colors" to listOf("あか", "あお", "しろ", "くろ"),
        "Nature" to listOf("やま", "かわ", "そら", "あめ", "はな", "さかな"),
        "Seasons" to listOf("はる", "なつ", "あき", "ふゆ"),
    )

    private val KATAKANA_WORDS: List<Pair<String, List<String>>> = listOf(
        "Food & drink" to listOf("コーヒー", "ケーキ", "ラーメン", "カレー", "パン", "ミルク"),
        "Countries" to listOf("アメリカ", "イタリア", "フランス"),
        "Everyday items" to listOf("カメラ", "ノート", "テレビ", "ペン", "ホテル"),
    )

    /**
     * Builds the concrete set specs to seed, resolving row/word members against
     * the loaded [deck]/[words] (dropping anything that doesn't resolve, and
     * skipping a set that ends up empty). Category members are derived from the
     * deck by classifier with the same priority as [ScriptScope]
     * (extended → yōon → voiced) so the three groups partition cleanly.
     */
    fun build(
        deck: List<HiraganaCharacter>,
        words: List<VocabularyWord>,
    ): List<DefaultSetSpec> {
        val specs = mutableListOf<DefaultSetSpec>()
        for (script in Script.entries) {
            val scriptChars = deck.filter { it.script == script }
            val glyphs = scriptChars.map { it.character }.toHashSet()
            val rows = if (script == Script.HIRAGANA) HIRAGANA_ROWS else KATAKANA_ROWS
            val wordSets = if (script == Script.HIRAGANA) HIRAGANA_WORDS else KATAKANA_WORDS

            rows.forEach { (name, glyphString) ->
                val members = glyphString.map { it.toString() }.filter { it in glyphs }
                if (members.isNotEmpty()) {
                    specs += DefaultSetSpec(name, PracticeSetType.CHARACTERS, script, members)
                }
            }

            // Advanced categories, derived and partitioned by priority.
            fun category(name: String, predicate: (HiraganaCharacter) -> Boolean) {
                val members = scriptChars.filter(predicate).map { it.character }
                if (members.isNotEmpty()) {
                    specs += DefaultSetSpec(name, PracticeSetType.CHARACTERS, script, members)
                }
            }
            category("Extended (っ ァ)") { it.isExtended }
            category("Combined (ゃゅょ)") { it.isYoon && !it.isExtended }
            category("Voiced (゛゜)") {
                it.kind != KanaKind.BASE && !it.isYoon && !it.isExtended
            }

            val wordKana = words.filter { it.script == script }.map { it.kana }.toHashSet()
            wordSets.forEach { (name, kana) ->
                val members = kana.filter { it in wordKana }
                if (members.isNotEmpty()) {
                    specs += DefaultSetSpec(name, PracticeSetType.WORDS, script, members)
                }
            }
        }
        return specs
    }
}
