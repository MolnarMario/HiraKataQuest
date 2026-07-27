package com.hive.hiragana.domain.model

/**
 * Builds the *spoken* romaji reading of a kana string from its per-unit
 * readings — the string shown to a learner and compared against their typed
 * answer in the Reading track.
 *
 * The per-unit romaji stored on the deck characters is fine for ordinary kana
 * but wrong for two marks that only make sense in context, and both used to
 * leak straight into the UI and the answer key:
 *
 *  - the **sokuon** っ/ッ is stored as the IME escape `"xtsu"`, but it actually
 *    *geminates* the following consonant (きって → "kitte", not "ki xtsu te");
 *  - the **long-vowel mark** ー is stored as the glyph itself, but it *lengthens*
 *    the previous vowel (コーヒー → "koohii", not "ko ー hi ー").
 *
 * This resolves both from the surrounding units, so [PracticeViewModel.pronunciationOf]
 * produces a reading a learner can actually type. It is the single source of
 * that rule, mirroring [KanaUnits]'s role for the unit split it depends on.
 */
object KanaReading {

    /** Small tsu / sokuon (U+3063, U+30C3). */
    private val SOKUON = setOf('っ', 'ッ')

    /** Long-vowel mark ー (U+30FC), only meaningful after another kana. */
    private const val CHOONPU = 'ー'

    private const val VOWELS = "aeiou"

    /**
     * Space-separated romaji for [kana], resolving sokuon gemination and the
     * long-vowel mark. [lookup] returns the stored romaji for one reading unit
     * (a base kana or a composite like きょ/ファ), or null when the unit isn't in
     * the deck — then the glyph itself is used, matching the old behaviour.
     */
    fun spacedRomaji(kana: String, lookup: (String) -> String?): String {
        val units = KanaUnits.split(kana)
        val out = mutableListOf<String>()
        var i = 0
        while (i < units.size) {
            val unit = units[i]
            val single = unit.singleOrNull()
            when {
                // Sokuon: swallow it and prefix the geminating consonant onto
                // the NEXT unit's reading (っこう → "kkou", って → "tte",
                // っち → "tchi"). A trailing/pre-vowel sokuon just vanishes.
                single in SOKUON -> {
                    val next = units.getOrNull(i + 1)?.let { lookup(it) ?: it }.orEmpty()
                    val geminated = geminate(next)
                    if (geminated.isNotEmpty()) {
                        out.add(geminated)
                        i += 2
                    } else {
                        i += 1
                    }
                }
                // Long-vowel mark: lengthen the previous unit by repeating its
                // final vowel (コ + ー → "koo"); nothing precedes → drop it.
                single == CHOONPU -> {
                    val prev = out.lastOrNull()
                    val v = prev?.lastOrNull { it in VOWELS }
                    if (prev != null && v != null) out[out.lastIndex] = prev + v
                    i += 1
                }
                else -> {
                    out.add(lookup(unit) ?: unit)
                    i += 1
                }
            }
        }
        return out.joinToString(" ")
    }

    /**
     * The consonant a sokuon prepends to [nextRomaji]: normally its leading
     * consonant doubled (か→"k"→"kka"), but "ch…" geminates to "t" (Hepburn
     * っち = "tchi"). Empty when the next sound starts with a vowel or is absent.
     */
    private fun geminate(nextRomaji: String): String = when {
        nextRomaji.isEmpty() -> ""
        nextRomaji.startsWith("ch") -> "t" + nextRomaji
        nextRomaji[0] in VOWELS -> nextRomaji
        else -> nextRomaji[0] + nextRomaji
    }
}
