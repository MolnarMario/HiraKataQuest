package com.hive.hiragana.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.domain.model.CharacterForm
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
        PracticeLayout.fromSpan(prefs.getInt(KEY_PRACTICE_LAYOUT_SPAN, 1))

    override fun setPracticeLayout(layout: PracticeLayout) {
        prefs.edit().putInt(KEY_PRACTICE_LAYOUT_SPAN, layout.span).apply()
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

    override fun hasSeededDefaultSets(): Boolean =
        prefs.getBoolean(KEY_DEFAULT_SETS_SEEDED, false)

    override fun setDefaultSetsSeeded() {
        prefs.edit().putBoolean(KEY_DEFAULT_SETS_SEEDED, true).apply()
    }

    override fun getBrushInkColor(): Int = prefs.getInt(KEY_BRUSH_INK_COLOR, DEFAULT_INK_ARGB)

    override fun setBrushInkColor(color: Int) {
        prefs.edit().putInt(KEY_BRUSH_INK_COLOR, color).apply()
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
        const val KEY_DEFAULT_SETS_SEEDED = "default_sets_seeded"
        const val KEY_BRUSH_INK_COLOR = "brush_ink_color"
        const val KEY_BRUSH_EFFECT = "brush_effect"
        const val KEY_AUTO_NARRATE = "auto_narrate_enabled"
        const val KEY_CHARACTER_FORM = "character_form"
        const val KEY_APP_MODE = "app_mode"
        const val KEY_SKIPPED_UPDATE_VERSION = "skipped_update_version"

        // Default ARGB = presentation.theme.InkBlack (0xFF20222E); hardcoded so
        // the data layer needn't depend on a Compose Color.
        const val DEFAULT_INK_ARGB: Int = 0xFF20222E.toInt()
    }
}
