package com.hive.hiragana.domain.model

/**
 * Editorial notes for the handful of kana that are NOT ordinary syllables and
 * quietly confuse learners who meet them in a deck as if they read like every
 * other card:
 *
 *  - the katakana long-vowel mark ー, which has no sound of its own and only
 *    stretches the vowel before it;
 *  - the small tsu っ/ッ (sokuon), which also has no sound and instead doubles
 *    the following consonant;
 *  - the small vowels ぁぃぅぇぉ / ァィゥェォ, which are almost never written
 *    alone and exist to build combined sounds;
 *  - the single ん/ン, the one kana with no vowel;
 *  - the particle を/ヲ, which sounds like お but is only ever a grammar marker.
 *
 * Like [PrintDivergence] this is editorial knowledge that cannot be derived
 * from Unicode or the asset data, so the map below is the one source of truth,
 * and it is purely informative: nothing here touches scoring, reading tallies,
 * or scheduling. Unlike [PrintDivergence] the lookup is EXACT-glyph (a plain
 * map, no NFD) because none of these marks have voiced or composite variants
 * that should inherit the note.
 */
object SpecialKana {

    /**
     * A short note about one special kana: a [title] for the chip and dialog
     * heading, and a [body] that explains why the character behaves unlike an
     * ordinary syllable. Kept free of em-dashes and long enough to teach the
     * rule with a worked example.
     */
    data class Note(val title: String, val body: String)

    private val LONG_VOWEL = Note(
        title = "Long-vowel mark",
        body = "This mark has no sound of its own. It simply stretches the " +
            "vowel of the kana before it. コーヒー is read koohii, ラーメン is " +
            "raamen. You only meet it in katakana.",
    )

    private val SOKUON = Note(
        title = "Small tsu",
        body = "The small tsu has no sound of its own. It doubles the " +
            "consonant that follows, heard as a brief pause before it. きって " +
            "is kitte, ベッド is beddo. It is written smaller than a full つ/ツ.",
    )

    private val SMALL_VOWEL = Note(
        title = "Small vowel",
        body = "Small vowels are almost never written on their own. They join " +
            "the kana before them to spell sounds the basic set cannot, mostly " +
            "in foreign words: ファ (fa), ティ (ti), ウェ (we).",
    )

    private val MORAIC_N = Note(
        title = "The single n",
        body = "ん is the only kana with no vowel. It is a nasal \"n\" that " +
            "closes a syllable and never starts one, and it takes a full beat " +
            "of its own: にほん is read ni ho n.",
    )

    private val WO_PARTICLE = Note(
        title = "The particle を",
        body = "を is pronounced just like お (o), but it is used only as a " +
            "grammar particle that marks the object of a verb. Type it as " +
            "\"wo\". You will rarely see it inside a word.",
    )

    /** One note per special glyph, keyed on the exact character (both scripts). */
    private val NOTES: Map<String, Note> = buildMap {
        put("ー", LONG_VOWEL)
        put("っ", SOKUON)
        put("ッ", SOKUON)
        listOf("ぁ", "ぃ", "ぅ", "ぇ", "ぉ", "ァ", "ィ", "ゥ", "ェ", "ォ")
            .forEach { put(it, SMALL_VOWEL) }
        put("ん", MORAIC_N)
        put("ン", MORAIC_N)
        put("を", WO_PARTICLE)
        put("ヲ", WO_PARTICLE)
    }

    /**
     * The note for one reading unit's [glyph] (e.g. "ー", "っ", "ん"), or null
     * when the glyph is an ordinary syllable with nothing unusual to explain.
     */
    fun noteFor(glyph: String): Note? = NOTES[glyph]

    /**
     * Marks that make no sound at all on their own, so a text-to-speech engine
     * has nothing sensible to say for them in isolation: the long-vowel mark ー
     * and the small tsu っ/ッ. Callers use this to hide the pronunciation
     * button and skip auto-narration for a lone mark (a WORD that merely
     * contains one still speaks normally — the whole word has a sound).
     *
     * The small vowels ぁぃぅぇぉ still carry their bare vowel sound, and ん/を
     * read normally, so those are deliberately not silenced.
     */
    fun isSilentMark(glyph: String): Boolean =
        glyph == "ー" || glyph == "っ" || glyph == "ッ"

    /**
     * A short, human-readable reading label for a lone mark whose stored romaji
     * is a placeholder that only makes sense inside a word (e.g. "ー" for the
     * long-vowel mark, "xtsu"/"xa" for the small kana). Shown in place of that
     * placeholder on browse and study surfaces so the mark never looks like a
     * fake syllable. Null for ordinary kana (and for ん/を, whose romaji is
     * already a real, readable value), so callers fall back to the romaji.
     *
     * This is a DISPLAY label only. It is never used as the answer that a typed
     * romaji is matched against, so the in-word reading logic is untouched.
     */
    fun readingLabel(glyph: String): String? = when (glyph) {
        "ー" -> "long vowel"
        "っ", "ッ" -> "small tsu"
        "ぁ", "ァ" -> "small a"
        "ぃ", "ィ" -> "small i"
        "ぅ", "ゥ" -> "small u"
        "ぇ", "ェ" -> "small e"
        "ぉ", "ォ" -> "small o"
        else -> null
    }
}
