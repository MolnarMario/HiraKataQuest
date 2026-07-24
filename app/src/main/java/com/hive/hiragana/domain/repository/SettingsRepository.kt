package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.drawing.canvas.BrushEffect

/**
 * User-facing app settings that survive process death. Synchronous by
 * design: the backing store is SharedPreferences, whose reads are
 * in-memory after the first load and whose writes are async (`apply`),
 * so none of this warrants suspend plumbing.
 */
interface SettingsRepository {

    /**
     * Whether "Beginner mode" is on (default false). When on, the app shows the
     * kana-concept ⓘ help icons + the "New to kana?" overview; when off they are
     * hidden so the screens stay uncluttered for learners who know the terms.
     */
    fun isBeginnerMode(): Boolean

    fun setBeginnerMode(enabled: Boolean)

    /** Whether the tracing canvas draws the practice-grid helper lines. */
    fun isGridEnabled(): Boolean

    fun setGridEnabled(enabled: Boolean)

    /** Whether the tracing canvas draws the light-grey ghost glyph to trace over. */
    fun isGhostEnabled(): Boolean

    fun setGhostEnabled(enabled: Boolean)

    /** How many repetitions of the current glyph the canvas tiles per sheet. */
    fun getPracticeLayout(): PracticeLayout

    fun setPracticeLayout(layout: PracticeLayout)

    /** Which writing system the learner is currently studying (default hiragana). */
    fun getActiveScript(): Script

    fun setActiveScript(script: Script)

    /**
     * Whether voiced (dakuten) / semi-voiced (handakuten) kana are folded into
     * the learning flow. Default false so beginners start on the base gojūon.
     */
    fun isDiacriticsIncluded(): Boolean

    fun setDiacriticsIncluded(included: Boolean)

    /**
     * Whether yōon (contracted syllables like きょ/しゅ) are folded into the
     * learning flow. Default false — the most advanced kana, opted in last.
     */
    fun isYoonIncluded(): Boolean

    fun setYoonIncluded(included: Boolean)

    /**
     * Whether extended kana are folded into the learning flow: the small tsu
     * (っ/ッ), small vowels and foreign-sound combos (ファ, ティ …), and obsolete
     * kana. Default false — the last group a learner adds.
     */
    fun isExtendedIncluded(): Boolean

    fun setExtendedIncluded(included: Boolean)

    /**
     * What handwriting-quality scoring judges (form / fit / both / off).
     * Default [ScoringMode.FORM] — the forgiving dial that rewards legible
     * shape without demanding the writing hug the guide strokes.
     */
    fun getScoringMode(): ScoringMode

    fun setScoringMode(mode: ScoringMode)

    /**
     * Whether the Library shows its characters/words as a chart/grid (true) or
     * the vertical list (false, default). A view preference, persisted so it
     * survives restarts like the other display toggles.
     */
    fun isLibraryGridView(): Boolean

    fun setLibraryGridView(gridView: Boolean)

    /**
     * The Library section (tab) the learner last had open: 0 = Characters
     * (default), 1 = Words. Persisted so a cold start can resume free practice
     * in the same area the learner was studying.
     */
    fun getLibrarySection(): Int

    fun setLibrarySection(index: Int)

    /**
     * The saved-sets manager's last-used sort order, stored as the UI sort
     * enum's name (e.g. "NAME_ASC"); null until the learner first picks one.
     * Persisted so the list keeps the learner's chosen order across edit
     * flows, quiz round-trips, and restarts — it only changes when they pick
     * another order. A raw string because the enum is presentation-local;
     * unknown values fall back to the default order at the decode site.
     */
    fun getSetsSortKey(): String?

    fun setSetsSortKey(key: String)

    /**
     * Whether the built-in default practice sets have already been seeded into
     * this install's library. One-time latch: false on a fresh install (and on
     * existing installs upgrading from before defaults existed) so they seed
     * once, true afterwards so a set the user deleted never reappears.
     */
    fun hasSeededDefaultSets(): Boolean

    fun setDefaultSetsSeeded()

    /**
     * The brush ink color, as a packed ARGB Int (`Color.toArgb()`). Purely a
     * visual customization — it never affects stroke geometry or scoring.
     * Default is the app's `InkBlack` charcoal.
     */
    fun getBrushInkColor(): Int

    fun setBrushInkColor(color: Int)

    /**
     * The artistic brush effect strokes are drawn with ([BrushEffect]). Purely
     * visual — it never affects stroke geometry as seen by the scorer (which
     * reads raw points). Default [BrushEffect.BRUSH] (the original ribbon look).
     */
    fun getBrushEffect(): BrushEffect

    fun setBrushEffect(effect: BrushEffect)

    /**
     * Whether a newly shown character/word is spoken aloud automatically by the
     * device TTS the moment it appears — in Writing free practice and the
     * writing quiz. Default false: the learner otherwise taps the speaker to
     * hear the reading. Never applies in the Reading track (hearing the answer
     * would defeat the recognition practice) — Reading has its own
     * after-the-answer setting, [isReadingAutoSpeakEnabled].
     */
    fun isAutoNarrateEnabled(): Boolean

    fun setAutoNarrateEnabled(enabled: Boolean)

    /**
     * Whether the Reading track speaks the answer aloud once it is revealed —
     * after a flashcard Check / choice tap, and after a reading-quiz answer.
     * The Reading counterpart of [isAutoNarrateEnabled]: there the glyph is
     * spoken the moment it APPEARS; here only AFTER the learner has answered,
     * so the audio never gives the recognition answer away. Default true.
     */
    fun isReadingAutoSpeakEnabled(): Boolean

    fun setReadingAutoSpeakEnabled(enabled: Boolean)

    /**
     * Whether the Reading flashcards also test the word's meaning after the
     * reading — Typing → type the meaning, Multiple choice → tap it. Words only
     * (characters have no meaning). Default false. Independent of the reading
     * quiz's own meaning toggle.
     */
    fun isReadingMeaningPractice(): Boolean

    fun setReadingMeaningPractice(enabled: Boolean)

    /**
     * Which character form the traced ghost shows: [CharacterForm.PRINTED]
     * (typographic — the form learners read in the wild, the default) or
     * [CharacterForm.HANDWRITTEN] (the kaisho stroke shape). A rendering choice
     * only — the stroke-order demo and scorer always use the strokes.
     */
    fun getCharacterForm(): CharacterForm

    fun setCharacterForm(form: CharacterForm)

    /**
     * The top-level track the learner last chose: [AppMode.READING] or
     * [AppMode.WRITING]. `null` means not chosen yet → the opening screen is
     * shown so the learner can pick.
     */
    fun getAppMode(): AppMode?

    fun setAppMode(mode: AppMode)

    /**
     * The app version the user chose to skip in the in-app updater ("Skip"), or
     * null if none. The updater won't re-offer a matching [UpdateInfo.versionName]
     * (a newer one still prompts).
     */
    fun getSkippedUpdateVersion(): String?

    fun setSkippedUpdateVersion(versionName: String)
}
