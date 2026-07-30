package com.hive.hiragana.data.repository

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.domain.model.AppearanceMode
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.KanaMnemonic
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.repository.SettingsRepository
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.drawing.canvas.BrushEffect

/**
 * [SettingsRepository] backed by a plain [SharedPreferences] file —
 * deliberately not DataStore: a couple of scalar settings do not justify
 * a new dependency (the version catalog stays untouched), and the
 * interface hides the mechanism from the ViewModel either way.
 */
class PrefsSettingsRepository(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isBeginnerMode(): Boolean = prefs.getBoolean(KEY_BEGINNER_MODE, false)

    override fun setBeginnerMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BEGINNER_MODE, enabled).apply()
    }

    override fun isGridEnabled(): Boolean = prefs.getBoolean(KEY_GRID_ENABLED, true)

    override fun setGridEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GRID_ENABLED, enabled).apply()
    }

    override fun isGhostEnabled(): Boolean = prefs.getBoolean(KEY_GHOST_ENABLED, true)

    override fun setGhostEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GHOST_ENABLED, enabled).apply()
    }

    override fun getPracticeLayout(): PracticeLayout =
        PracticeLayout.fromPrefKey(prefs.getInt(KEY_PRACTICE_LAYOUT_SPAN, 1))

    override fun setPracticeLayout(layout: PracticeLayout) {
        prefs.edit().putInt(KEY_PRACTICE_LAYOUT_SPAN, layout.prefKey).apply()
    }

    override fun getActiveScript(): Script =
        Script.fromKey(prefs.getString(KEY_ACTIVE_SCRIPT, null))

    override fun setActiveScript(script: Script) {
        prefs.edit().putString(KEY_ACTIVE_SCRIPT, script.key).apply()
    }

    override fun isDiacriticsIncluded(): Boolean =
        prefs.getBoolean(KEY_DIACRITICS_INCLUDED, false)

    override fun setDiacriticsIncluded(included: Boolean) {
        prefs.edit().putBoolean(KEY_DIACRITICS_INCLUDED, included).apply()
    }

    override fun isYoonIncluded(): Boolean =
        prefs.getBoolean(KEY_YOON_INCLUDED, false)

    override fun setYoonIncluded(included: Boolean) {
        prefs.edit().putBoolean(KEY_YOON_INCLUDED, included).apply()
    }

    override fun isExtendedIncluded(): Boolean =
        prefs.getBoolean(KEY_EXTENDED_INCLUDED, false)

    override fun setExtendedIncluded(included: Boolean) {
        prefs.edit().putBoolean(KEY_EXTENDED_INCLUDED, included).apply()
    }

    override fun getScoringMode(): ScoringMode =
        ScoringMode.fromKey(prefs.getString(KEY_SCORING_MODE, null))

    override fun setScoringMode(mode: ScoringMode) {
        prefs.edit().putString(KEY_SCORING_MODE, mode.key).apply()
    }

    override fun isLibraryGridView(): Boolean = prefs.getBoolean(KEY_LIBRARY_GRID_VIEW, false)

    override fun setLibraryGridView(gridView: Boolean) {
        prefs.edit().putBoolean(KEY_LIBRARY_GRID_VIEW, gridView).apply()
    }

    override fun getLibrarySection(): Int = prefs.getInt(KEY_LIBRARY_SECTION, 0)

    override fun setLibrarySection(index: Int) {
        prefs.edit().putInt(KEY_LIBRARY_SECTION, index).apply()
    }

    override fun getSetsSortKey(): String? = prefs.getString(KEY_SETS_SORT, null)

    override fun setSetsSortKey(key: String) {
        prefs.edit().putString(KEY_SETS_SORT, key).apply()
    }

    override fun hasSeededDefaultSets(): Boolean =
        prefs.getBoolean(KEY_DEFAULT_SETS_SEEDED, false)

    override fun setDefaultSetsSeeded() {
        prefs.edit().putBoolean(KEY_DEFAULT_SETS_SEEDED, true).apply()
    }

    override fun getBrushInkColor(): Int = prefs.getInt(KEY_BRUSH_INK_COLOR, DEFAULT_INK_ARGB)

    override fun setBrushInkColor(color: Int) {
        prefs.edit().putInt(KEY_BRUSH_INK_COLOR, color).apply()
    }

    override fun getBrushInkColorDark(): Int =
        prefs.getInt(KEY_BRUSH_INK_COLOR_DARK, DEFAULT_INK_DARK_ARGB)

    override fun setBrushInkColorDark(color: Int) {
        prefs.edit().putInt(KEY_BRUSH_INK_COLOR_DARK, color).apply()
    }

    override fun getBrushEffect(): BrushEffect =
        BrushEffect.fromPrefValue(prefs.getString(KEY_BRUSH_EFFECT, null))

    override fun setBrushEffect(effect: BrushEffect) {
        prefs.edit().putString(KEY_BRUSH_EFFECT, effect.prefValue).apply()
    }

    override fun isAutoNarrateEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_NARRATE, false)

    override fun setAutoNarrateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_NARRATE, enabled).apply()
    }

    override fun isReadingAutoSpeakEnabled(): Boolean =
        prefs.getBoolean(KEY_READING_AUTO_SPEAK, true)

    override fun setReadingAutoSpeakEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_READING_AUTO_SPEAK, enabled).apply()
    }

    override fun isReadingMeaningPractice(): Boolean =
        prefs.getBoolean(KEY_READING_MEANING_PRACTICE, false)

    override fun setReadingMeaningPractice(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_READING_MEANING_PRACTICE, enabled).apply()
    }

    override fun isMnemonicsEnabled(): Boolean =
        prefs.getBoolean(KEY_MNEMONICS_ENABLED, true)

    override fun setMnemonicsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MNEMONICS_ENABLED, enabled).apply()
    }

    // Encoded as one line per choice, "glyph=KEYWORD" (kana glyphs and the
    // uppercase keywords never contain '=' or a newline) — a whole Room table
    // for at most 46 tiny picks would be overkill.
    override fun getMnemonicChoices(): Map<String, String> =
        prefs.getString(KEY_MNEMONIC_CHOICES, null)
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0 || i == line.length - 1) null
                else line.substring(0, i) to line.substring(i + 1)
            }
            .toMap()

    override fun setMnemonicChoices(choices: Map<String, String>) {
        val encoded = choices.entries.joinToString("\n") { "${it.key}=${it.value}" }
        prefs.edit().putString(KEY_MNEMONIC_CHOICES, encoded).apply()
    }

    // Custom mnemonics carry free-form title/description text (newlines, '=',
    // any character), so the line-based encoding above won't do — store them as
    // JSON via the platform org.json (the same parser the asset loader uses; no
    // new dependency). Shape: { "<glyph>": [ {"t": title, "d": description}, … ] }
    override fun getCustomMnemonics(): Map<String, List<KanaMnemonic>> {
        val raw = prefs.getString(KEY_CUSTOM_MNEMONICS, null) ?: return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        // Parse tolerantly PER ENTRY: skip a corrupt glyph/entry but keep the
        // rest. A single bad entry must not blank the whole map, because the
        // next add/delete rewrites the pref FROM this map — so returning empty
        // on one bad entry would permanently destroy every other custom.
        return buildMap {
            for (glyph in root.keys()) {
                val arr = runCatching { root.getJSONArray(glyph) }.getOrNull() ?: continue
                val list = (0 until arr.length()).mapNotNull { i ->
                    runCatching {
                        val o = arr.getJSONObject(i)
                        KanaMnemonic(keyword = o.getString("t"), story = o.getString("d"))
                    }.getOrNull()
                }
                if (list.isNotEmpty()) put(glyph, list)
            }
        }
    }

    override fun setCustomMnemonics(customs: Map<String, List<KanaMnemonic>>) {
        val root = JSONObject()
        for ((glyph, list) in customs) {
            if (list.isEmpty()) continue
            val arr = JSONArray()
            for (m in list) {
                arr.put(JSONObject().put("t", m.keyword).put("d", m.story))
            }
            root.put(glyph, arr)
        }
        prefs.edit().putString(KEY_CUSTOM_MNEMONICS, root.toString()).apply()
    }

    override fun getCharacterForm(): CharacterForm =
        CharacterForm.fromPrefValue(prefs.getString(KEY_CHARACTER_FORM, null))

    override fun setCharacterForm(form: CharacterForm) {
        prefs.edit().putString(KEY_CHARACTER_FORM, form.prefValue).apply()
    }

    override fun getAppMode(): AppMode? =
        AppMode.fromPrefValue(prefs.getString(KEY_APP_MODE, null))

    override fun setAppMode(mode: AppMode) {
        prefs.edit().putString(KEY_APP_MODE, mode.prefValue).apply()
    }

    override fun getAppearance(): AppearanceMode =
        AppearanceMode.fromPrefValue(prefs.getString(KEY_APPEARANCE, null))

    override fun setAppearance(mode: AppearanceMode) {
        prefs.edit().putString(KEY_APPEARANCE, mode.prefValue).apply()
    }

    override fun getSkippedUpdateVersion(): String? =
        prefs.getString(KEY_SKIPPED_UPDATE_VERSION, null)

    override fun setSkippedUpdateVersion(versionName: String) {
        prefs.edit().putString(KEY_SKIPPED_UPDATE_VERSION, versionName).apply()
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_BEGINNER_MODE = "beginner_mode"
        const val KEY_GRID_ENABLED = "grid_enabled"
        const val KEY_GHOST_ENABLED = "ghost_enabled"
        const val KEY_PRACTICE_LAYOUT_SPAN = "practice_layout_span"
        const val KEY_ACTIVE_SCRIPT = "active_script"
        const val KEY_DIACRITICS_INCLUDED = "diacritics_included"
        const val KEY_YOON_INCLUDED = "yoon_included"
        const val KEY_EXTENDED_INCLUDED = "extended_included"
        const val KEY_SCORING_MODE = "scoring_mode"
        const val KEY_LIBRARY_GRID_VIEW = "library_grid_view"
        const val KEY_LIBRARY_SECTION = "library_section"
        const val KEY_SETS_SORT = "sets_sort"
        const val KEY_DEFAULT_SETS_SEEDED = "default_sets_seeded"
        const val KEY_BRUSH_INK_COLOR = "brush_ink_color"
        const val KEY_BRUSH_INK_COLOR_DARK = "brush_ink_color_dark"
        const val KEY_BRUSH_EFFECT = "brush_effect"
        const val KEY_AUTO_NARRATE = "auto_narrate_enabled"
        const val KEY_READING_AUTO_SPEAK = "reading_auto_speak_enabled"
        const val KEY_READING_MEANING_PRACTICE = "reading_meaning_practice"
        const val KEY_MNEMONICS_ENABLED = "mnemonics_enabled"
        const val KEY_MNEMONIC_CHOICES = "mnemonic_choices"
        const val KEY_CUSTOM_MNEMONICS = "custom_mnemonics"
        const val KEY_CHARACTER_FORM = "character_form"
        const val KEY_APP_MODE = "app_mode"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_SKIPPED_UPDATE_VERSION = "skipped_update_version"

        // Default ARGB = presentation.theme.InkBlack (0xFF20222E); hardcoded so
        // the data layer needn't depend on a Compose Color.
        const val DEFAULT_INK_ARGB: Int = 0xFF20222E.toInt()

        // Dark-appearance default ARGB = presentation.theme.InkChalk
        // (0xFFEFE7DA); hardcoded for the same no-Compose-dep reason.
        const val DEFAULT_INK_DARK_ARGB: Int = 0xFFEFE7DA.toInt()
    }
}
