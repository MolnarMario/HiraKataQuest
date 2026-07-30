package com.hive.hiragana.data.assets

import android.content.res.AssetManager
import android.util.Log
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.model.StrokeDefinition
import com.hive.hiragana.domain.model.StrokeFrame
import com.hive.hiragana.domain.model.VocabularyWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads character and vocabulary definitions from one `assets/<script>/`
 * directory per [Script] (`assets/hiragana/`, `assets/katakana/`) and
 * concatenates them into a single deck.
 *
 * Each directory's index.json lists its JSON file names in study order; each
 * listed file describes one character (glyph, romaji, frequency rank, and SVG
 * stroke paths in a square viewBox), plus an optional generated
 * `strokeAnimations` array of timed per-stroke pen positions that drives the
 * animated stroke-order demonstration (schema: docs/PHASE2-DESIGN.md §1). The
 * file's basename in index.json becomes [HiraganaCharacter.id]; the directory
 * it was read from sets [HiraganaCharacter.script] (never stored in the JSON).
 * vocabulary.json is a separate, independent per-directory asset (schema:
 * docs/PHASE3-DESIGN.md §3).
 *
 * Loading is resilient **per directory AND per file**: each script's read is
 * wrapped so a missing or unreadable directory (e.g. no `assets/katakana/` yet)
 * is skipped with a warning and contributes nothing, and within a directory a
 * single malformed character JSON is skipped rather than dropping every
 * character of that script. The result is always the union of whatever loaded
 * successfully.
 */
class HiraganaAssetDataSource(private val assetManager: AssetManager) {

    suspend fun loadCharacters(): List<HiraganaCharacter> = withContext(Dispatchers.IO) {
        Script.entries.flatMap { script -> loadCharactersForScript(script) }
    }

    private fun loadCharactersForScript(script: Script): List<HiraganaCharacter> =
        runCatching {
            val index = JSONObject(readAsset("${script.key}/index.json"))
            val files = index.getJSONArray("characters")
            buildList {
                for (i in 0 until files.length()) {
                    val name = files.getString(i)
                    // Resilient per FILE too: one malformed character JSON skips
                    // only itself, not the whole script (dropping all 100+
                    // characters over one bad file was the old behaviour).
                    runCatching {
                        parseCharacter(
                            JSONObject(readAsset("${script.key}/$name.json")),
                            id = name,
                            script = script,
                        )
                    }.onSuccess { add(it) }
                        .onFailure { Log.w(TAG, "Skipping malformed character '$name' in '${script.key}'", it) }
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Could not load characters for '${script.key}'; skipping that script", error)
            emptyList()
        }

    /**
     * Reads every script's `assets/<script>/vocabulary.json` (schema:
     * docs/PHASE3-DESIGN.md §3) and concatenates them. Each directory is read
     * independently: a missing or malformed vocabulary file for one script is
     * logged and skipped (contributing no words) without affecting the other
     * script's words or the character load path. The caller
     * (RoomVocabularyRepository) still wraps the whole call as a last resort.
     */
    suspend fun loadVocabulary(): List<VocabularyWord> = withContext(Dispatchers.IO) {
        Script.entries.flatMap { script -> loadVocabularyForScript(script) }
    }

    private fun loadVocabularyForScript(script: Script): List<VocabularyWord> =
        runCatching {
            val json = JSONObject(readAsset("${script.key}/vocabulary.json"))
            val wordsJson = json.getJSONArray("words")
            buildList {
                for (i in 0 until wordsJson.length()) {
                    add(parseVocabularyWord(wordsJson.getJSONObject(i)))
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Could not load vocabulary for '${script.key}'; skipping that script", error)
            emptyList()
        }

    private fun parseVocabularyWord(json: JSONObject): VocabularyWord =
        VocabularyWord.fromKana(
            kana = json.getString("kana"),
            meaning = json.getString("meaning"),
        )

    private fun readAsset(path: String): String =
        assetManager.open(path).bufferedReader().use { it.readText() }

    private fun parseCharacter(json: JSONObject, id: String, script: Script): HiraganaCharacter {
        val character = json.getString("character")
        val strokesJson = json.getJSONArray("strokes")
        val strokes = buildList {
            for (i in 0 until strokesJson.length()) add(strokesJson.getString(i))
        }
        return HiraganaCharacter(
            id = id,
            character = character,
            romaji = json.getString("romaji"),
            frequency = json.getInt("frequency"),
            svgPath = json.optString("svgPath", strokes.joinToString(" ")),
            strokeImage = json.optString("strokeImage"),
            strokes = strokes,
            viewBox = json.optDouble("viewBox", 100.0).toFloat(),
            strokeAnimations = parseStrokeAnimations(json, character, strokes.size),
            script = script,
        )
    }

    /**
     * Parses the optional `strokeAnimations` field into timed stroke frames.
     *
     * The field is derived data written by tools/generate_stroke_animations.py
     * (docs/PHASE2-DESIGN.md §1); a file may legitimately not have it yet, so
     * absence is not an error — it just yields an empty list and the UI falls
     * back to the static stroke-order diagram. Parsing is all-or-nothing per
     * character: ANY malformed entry (missing key, wrong type, non-object
     * element) voids the whole list, because a partially animated character
     * would demonstrate a wrong stroke count, which is worse than no
     * animation at all. The same rule covers structurally valid data that is
     * inconsistent with the character's strokes[]: the list is voided unless
     * it has exactly one entry per stroke, orders run exactly 1..N, and every
     * entry has at least one point — schema-valid JSON from a buggy generator
     * run must not demonstrate a wrong stroke count either. For the same
     * reason this never throws — bad animation data must not take down the
     * asset load that Phase 1 depends on. Voiding is logged (the app's only
     * warning log) so a silent regression to the static diagram is
     * diagnosable from logcat.
     */
    private fun parseStrokeAnimations(
        json: JSONObject,
        character: String,
        strokeCount: Int,
    ): List<StrokeDefinition> {
        val animationsJson = json.optJSONArray("strokeAnimations") ?: return emptyList()
        val definitions = runCatching { parseStrokeDefinitions(animationsJson) }
            .getOrElse { error ->
                Log.w(TAG, "Malformed strokeAnimations for '$character'; using the static diagram", error)
                return emptyList()
            }
        val consistent = definitions.size == strokeCount &&
            definitions.withIndex().all { (index, definition) ->
                definition.order == index + 1 && definition.points.isNotEmpty()
            }
        if (!consistent) {
            Log.w(
                TAG,
                "strokeAnimations for '$character' don't match its $strokeCount strokes; " +
                    "using the static diagram",
            )
            return emptyList()
        }
        return definitions
    }

    /** Strict mapping: any schema violation throws, failing the whole list. */
    private fun parseStrokeDefinitions(animationsJson: JSONArray): List<StrokeDefinition> =
        buildList {
            for (i in 0 until animationsJson.length()) {
                val strokeJson = animationsJson.getJSONObject(i)
                add(
                    StrokeDefinition(
                        order = strokeJson.getInt("order"),
                        points = parseStrokeFrames(strokeJson.getJSONArray("points")),
                    )
                )
            }
        }.sortedBy { it.order }

    private fun parseStrokeFrames(pointsJson: JSONArray): List<StrokeFrame> = buildList {
        for (i in 0 until pointsJson.length()) {
            val pointJson = pointsJson.getJSONObject(i)
            add(
                StrokeFrame(
                    x = pointJson.getDouble("x").toFloat(),
                    y = pointJson.getDouble("y").toFloat(),
                    time = pointJson.getLong("time"),
                )
            )
        }
    }

    private companion object {
        const val TAG = "HiraganaAssets"
    }
}
