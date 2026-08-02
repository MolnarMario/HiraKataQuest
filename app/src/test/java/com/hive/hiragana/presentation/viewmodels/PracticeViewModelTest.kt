package com.hive.hiragana.presentation.viewmodels

import com.hive.hiragana.domain.audio.PronunciationPlayer
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.domain.model.AppearanceMode
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.CUSTOM_MNEMONIC_STORY_MAX
import com.hive.hiragana.domain.model.CUSTOM_MNEMONIC_TITLE_MAX
import com.hive.hiragana.domain.model.Grade
import com.hive.hiragana.domain.model.KanaMnemonic
import com.hive.hiragana.domain.model.ReadingProgress
import com.hive.hiragana.domain.repository.ReadingProgressRepository
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.PracticeAttempt
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.model.StrokeDefinition
import com.hive.hiragana.domain.model.StrokeFrame
import com.hive.hiragana.domain.model.VocabularyProgress
import com.hive.hiragana.domain.model.VocabularyWord
import com.hive.hiragana.domain.model.PracticeSet
import com.hive.hiragana.domain.model.PracticeSetType
import com.hive.hiragana.domain.repository.CharacterRepository
import com.hive.hiragana.domain.repository.HandwritingRecognizer
import com.hive.hiragana.domain.repository.PracticeRepository
import com.hive.hiragana.domain.repository.PracticeSetRepository
import com.hive.hiragana.domain.repository.RecognizerStrokePoint
import com.hive.hiragana.domain.repository.SettingsRepository
import com.hive.hiragana.domain.update.AppUpdater
import com.hive.hiragana.domain.update.UpdateInfo
import com.hive.hiragana.domain.repository.VocabularyRepository
import com.hive.hiragana.domain.scoring.CellRect
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
import com.hive.hiragana.drawing.canvas.BrushEffect
import com.hive.hiragana.domain.scoring.RecognitionResult
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.drawing.stroke.DrawingSession
import com.hive.hiragana.drawing.stroke.StrokePoint
import com.hive.hiragana.drawing.stroke.UserStroke
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The project's first [PracticeViewModel] tests. Focus: the durable-field
 * preservation that funnels through [PracticeUiState.rebuilt], where a code
 * review found bugs that no test caught (e.g. [startNextSession] dropping
 * scoringMode), plus the recognition-guard against a stale async score
 * clobbering the current glyph.
 *
 * These are pure-JVM unit tests: every Android dependency is faked, and
 * [viewModelScope]'s Main dispatcher is swapped for a StandardTestDispatcher
 * so coroutine work is deterministically drained with [advanceUntilIdle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUpMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    // ---- fixtures --------------------------------------------------------

    /** A 2-char deck with valid reference stroke geometry so scoring can run. */
    private fun deck(): List<HiraganaCharacter> = listOf(
        character("a", "あ", frequency = 1),
        character("i", "い", frequency = 2),
    )

    private fun character(id: String, glyph: String, frequency: Int) = HiraganaCharacter(
        id = id,
        character = glyph,
        romaji = id,
        frequency = frequency,
        svgPath = "M0,0 L50,50",
        strokeImage = "$id.png",
        strokes = listOf("M0,0 L50,50"),
        viewBox = 109f,
        strokeAnimations = listOf(
            StrokeDefinition(
                order = 1,
                points = listOf(
                    StrokeFrame(x = 10f, y = 10f, time = 0L),
                    StrokeFrame(x = 90f, y = 90f, time = 100L),
                ),
            ),
        ),
    )

    /** A drawing session with one non-empty stroke, so scoring is enabled. */
    private fun inkSession(): DrawingSession = DrawingSession(
        strokes = mutableListOf(
            UserStroke(
                points = mutableListOf(
                    StrokePoint(x = 12f, y = 12f, pressure = 1f, timestamp = 0L),
                    StrokePoint(x = 88f, y = 88f, pressure = 1f, timestamp = 50L),
                ),
            ),
        ),
    )

    /**
     * A single 1×1 guide cell — the reference frame for FIT scoring, and the
     * signal that ONE glyph was written ONCE. Scoring only runs when this is
     * non-null (see [PracticeViewModel] recordAttempt / CanvasState.lastCell).
     */
    private fun cell(): CellRect = CellRect(left = 0f, top = 0f, size = 109f)

    private val fixedClock: Clock =
        Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC)

    private fun buildViewModel(
        characters: List<HiraganaCharacter> = deck(),
        recognizer: HandwritingRecognizer = FakeRecognizer(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        practiceRepository: FakePracticeRepository = FakePracticeRepository(),
        practiceSetRepository: FakePracticeSetRepository = FakePracticeSetRepository(),
        words: List<VocabularyWord> = emptyList(),
        ttsPlayer: PronunciationPlayer = FakePronunciationPlayer(),
    ) = PracticeViewModel(
        characterRepository = FakeCharacterRepository(characters),
        practiceRepository = practiceRepository,
        vocabularyRepository = FakeVocabularyRepository(words),
        settingsRepository = settings,
        practiceSetRepository = practiceSetRepository,
        readingProgressRepository = FakeReadingProgressRepository(),
        handwritingRecognizer = recognizer,
        ttsPlayer = ttsPlayer,
        appUpdater = FakeAppUpdater(),
        clock = fixedClock,
    )

    // ---- tests -----------------------------------------------------------

    /**
     * A scope-setter rebuild ([setActiveScript]) must preserve scoringMode.
     * Exercises the shared [PracticeUiState.rebuilt] helper.
     */
    @Test
    fun `scoringMode survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setScoringMode(ScoringMode.OFF)
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals(ScoringMode.OFF, vm.uiState.value.scoringMode)
        assertEquals(Script.KATAKANA, vm.uiState.value.activeScript)
    }

    /**
     * scoringMode is preserved when free practice re-scopes (via
     * [practiceCharacter] here it just needs to survive a state change). The
     * durable-field rebuild used by the scope setters must not drop it.
     */
    @Test
    fun `scoringMode survives entering free practice`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setScoringMode(ScoringMode.OFF)
        vm.practiceCharacter(deck().first())
        advanceUntilIdle()
        assertEquals(SessionPhase.FREE, vm.uiState.value.phase)
        assertEquals(ScoringMode.OFF, vm.uiState.value.scoringMode)
    }

    /**
     * The brush effect persists and, because it bakes different stroke
     * geometry, switching it clears the sheet (bumps [canvasClearKey]).
     */
    @Test
    fun `setBrushEffect persists and clears the sheet`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        val before = vm.uiState.value.canvasClearKey
        vm.setBrushEffect(BrushEffect.CALLIGRAPHY)

        assertEquals(BrushEffect.CALLIGRAPHY, vm.uiState.value.brushEffect)
        assertEquals(BrushEffect.CALLIGRAPHY, settings.getBrushEffect())
        assertEquals(before + 1, vm.uiState.value.canvasClearKey)
    }

    /**
     * The TTS voice status is fed once by a status collector and never
     * re-emits (StateFlow conflates equal values), so a scope-setter rebuild
     * MUST carry it — otherwise audio greys out permanently after any
     * script/opt-in change. Regression guard for the rebuilt() drop.
     */
    @Test
    fun `ttsStatus survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel(ttsPlayer = FakePronunciationPlayer(VoiceStatus.READY))
        advanceUntilIdle()
        assertEquals(VoiceStatus.READY, vm.uiState.value.ttsStatus)

        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals(VoiceStatus.READY, vm.uiState.value.ttsStatus)
    }

    /** The persisted brush effect must survive a scope-setter rebuild. */
    @Test
    fun `brushEffect survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setBrushEffect(BrushEffect.MARKER)
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals(BrushEffect.MARKER, vm.uiState.value.brushEffect)
    }

    /** The sets-manager sort order persists and publishes into UI state. */
    @Test
    fun `setSetsSort persists and updates state`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.setsSortKey)
        vm.setSetsSort("NEWEST")

        assertEquals("NEWEST", vm.uiState.value.setsSortKey)
        assertEquals("NEWEST", settings.getSetsSortKey())
    }

    /** The persisted sets sort must survive a scope-setter rebuild. */
    @Test
    fun `setsSortKey survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setSetsSort("SCORE_DESC")
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals("SCORE_DESC", vm.uiState.value.setsSortKey)
    }

    /** The auto-narrate toggle persists and publishes into UI state. */
    @Test
    fun `setAutoNarrate persists and updates state`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.autoNarrateEnabled)
        vm.setAutoNarrate(true)

        assertEquals(true, vm.uiState.value.autoNarrateEnabled)
        assertEquals(true, settings.isAutoNarrateEnabled())
    }

    /** The persisted auto-narrate preference must survive a scope-setter rebuild. */
    @Test
    fun `autoNarrate survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setAutoNarrate(true)
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.autoNarrateEnabled)
    }

    /** The appearance selector persists, publishes, and never clears the sheet. */
    @Test
    fun `setAppearance persists and updates state`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        assertEquals(AppearanceMode.LIGHT, vm.uiState.value.appearance)
        val clearKey = vm.uiState.value.canvasClearKey
        vm.setAppearance(AppearanceMode.DARK)

        assertEquals(AppearanceMode.DARK, vm.uiState.value.appearance)
        assertEquals(AppearanceMode.DARK, settings.getAppearance())
        // A theme flip recolors but never moves the sheet — no clear.
        assertEquals(clearKey, vm.uiState.value.canvasClearKey)
    }

    /** The persisted appearance must survive a scope-setter rebuild. */
    @Test
    fun `appearance survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setAppearance(AppearanceMode.DARK)
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals(AppearanceMode.DARK, vm.uiState.value.appearance)
    }

    /** The dark ink persists and, like the light ink, never clears the sheet. */
    @Test
    fun `setBrushInkColorDark persists and does not clear the sheet`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        assertEquals(0xFFEFE7DA.toInt(), vm.uiState.value.brushInkColorDark)
        val clearKey = vm.uiState.value.canvasClearKey
        vm.setBrushInkColorDark(0xFF112233.toInt())

        assertEquals(0xFF112233.toInt(), vm.uiState.value.brushInkColorDark)
        assertEquals(0xFF112233.toInt(), settings.getBrushInkColorDark())
        assertEquals(clearKey, vm.uiState.value.canvasClearKey)
    }

    /** The persisted dark ink must survive a scope-setter rebuild. */
    @Test
    fun `brushInkColorDark survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setBrushInkColorDark(0xFF112233.toInt())
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals(0xFF112233.toInt(), vm.uiState.value.brushInkColorDark)
    }

    /** The reading answer read-aloud toggle defaults ON, persists, and publishes into UI state. */
    @Test
    fun `setReadingAutoSpeak persists and updates state`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.readingAutoSpeakEnabled)
        vm.setReadingAutoSpeak(false)

        assertEquals(false, vm.uiState.value.readingAutoSpeakEnabled)
        assertEquals(false, settings.isReadingAutoSpeakEnabled())
    }

    /** The persisted reading read-aloud preference must survive a scope-setter rebuild. */
    @Test
    fun `readingAutoSpeak survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setReadingAutoSpeak(false)
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.readingAutoSpeakEnabled)
    }

    /** Beginner mode defaults off, persists, and survives a scope-setter rebuild. */
    @Test
    fun `setBeginnerMode persists and survives a rebuild`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.beginnerMode)
        vm.setBeginnerMode(true)
        assertEquals(true, vm.uiState.value.beginnerMode)
        assertEquals(true, settings.isBeginnerMode())

        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.beginnerMode)
    }

    /**
     * The memory-hints toggle defaults ON, persists, publishes into UI state,
     * and clears the sheet (the chip lives in the paper card's reserved footer
     * strip, so the strip layout changes — same rule as a brush-effect change).
     */
    @Test
    fun `setMnemonicsEnabled persists and clears the sheet`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.mnemonicsEnabled)
        val clearKey = vm.uiState.value.canvasClearKey
        vm.setMnemonicsEnabled(false)

        assertEquals(false, vm.uiState.value.mnemonicsEnabled)
        assertEquals(false, settings.isMnemonicsEnabled())
        assertEquals(clearKey + 1, vm.uiState.value.canvasClearKey)
    }

    /** The persisted memory-hints preference must survive a scope-setter rebuild. */
    @Test
    fun `mnemonicsEnabled survives a scope-setter rebuild`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setMnemonicsEnabled(false)
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.mnemonicsEnabled)
    }

    /**
     * Promoting a mnemonic alternate persists per kana, clears back to the
     * default with null, and survives a scope-setter rebuild.
     */
    @Test
    fun `chooseMnemonic persists per kana and survives a rebuild`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        vm.chooseMnemonic("あ", "ANTENNA")
        assertEquals("ANTENNA", vm.uiState.value.mnemonicChoices["あ"])
        assertEquals("ANTENNA", settings.getMnemonicChoices()["あ"])

        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()
        assertEquals("ANTENNA", vm.uiState.value.mnemonicChoices["あ"])

        vm.chooseMnemonic("あ", null)
        assertEquals(false, vm.uiState.value.mnemonicChoices.containsKey("あ"))
        assertEquals(false, settings.getMnemonicChoices().containsKey("あ"))
    }

    /**
     * Adding a custom mnemonic stores it (title uppercased + clamped), promotes
     * it to the kana's default, survives a rebuild, and deleting it restores
     * the built-in default.
     */
    @Test
    fun `add and delete custom mnemonic`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        vm.addCustomMnemonic("あ", "my apple", "It looks like an apple to me.")
        val saved = vm.uiState.value.customMnemonics["あ"]!!.single()
        assertEquals("MY APPLE", saved.keyword)          // uppercased
        assertEquals("It looks like an apple to me.", saved.story)
        assertEquals("MY APPLE", vm.uiState.value.mnemonicChoices["あ"])  // now default
        assertEquals("MY APPLE", settings.getCustomMnemonics()["あ"]!!.single().keyword)

        // Survives a scope-setter rebuild.
        vm.setActiveScript(Script.KATAKANA)
        advanceUntilIdle()
        assertEquals("MY APPLE", vm.uiState.value.customMnemonics["あ"]!!.single().keyword)

        // Blank input is ignored.
        vm.addCustomMnemonic("あ", "   ", "   ")
        assertEquals(1, vm.uiState.value.customMnemonics["あ"]!!.size)

        // Delete removes it and clears the (now-dangling) default choice.
        vm.deleteCustomMnemonic("あ", "MY APPLE")
        assertEquals(false, vm.uiState.value.customMnemonics.containsKey("あ"))
        assertEquals(false, vm.uiState.value.mnemonicChoices.containsKey("あ"))
        assertEquals(false, settings.getCustomMnemonics().containsKey("あ"))
    }

    /** A custom title/description longer than the limits is clamped when stored. */
    @Test
    fun `custom mnemonic is clamped to the character limits`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.addCustomMnemonic("い", "x".repeat(100), "y".repeat(500))
        val saved = vm.uiState.value.customMnemonics["い"]!!.single()
        assertEquals(CUSTOM_MNEMONIC_TITLE_MAX, saved.keyword.length)
        assertEquals(CUSTOM_MNEMONIC_STORY_MAX, saved.story.length)
    }

    /**
     * A stale recognition result for a previous glyph must not clobber the
     * current glyph's score. Two FREE submits (A then B) each launch a
     * recognition coroutine that suspends on a per-target deferred we control;
     * completing A's deferred AFTER B was submitted must leave lastScore labelled
     * with B, because the guard only refines when `lastScoredCharacter == target`.
     */
    @Test
    fun `stale recognition does not clobber current glyph score`() = runTest(dispatcher) {
        val recognizer = ControllableRecognizer()
        // FORM is fine (default); scoring just has to be enabled so a
        // recognition coroutine is launched at all.
        val vm = buildViewModel(recognizer = recognizer)
        advanceUntilIdle()

        // Enter FREE over the whole deck (あ at index 0).
        vm.practiceCharacter(deck().first())
        advanceUntilIdle()
        assertEquals("あ", vm.uiState.value.currentCharacter?.character)

        // Submit A (あ) — synchronous geometry sets lastScore/-Character to あ,
        // recognition for あ is now suspended. A real 1×1 cell is required for
        // scoring to run at all (the multi-cell gate).
        vm.freeSubmit(inkSession(), cell())
        advanceUntilIdle()
        assertEquals("あ", vm.uiState.value.lastScoredCharacter)

        // Advance to index 1 (い); this clears the feedback, so submit B refreshes it.
        vm.advanceToNextCharacter()
        advanceUntilIdle()
        assertEquals("い", vm.uiState.value.currentCharacter?.character)

        // Submit B (い) — geometry sets lastScore/-Character to い; recognition
        // for い is now suspended too.
        vm.freeSubmit(inkSession(), cell())
        advanceUntilIdle()
        assertEquals("い", vm.uiState.value.lastScoredCharacter)
        val scoreForB = vm.uiState.value.lastScore
        assertNotNull(scoreForB)

        // Now A's recognition finally resolves — the stale result. Because
        // lastScoredCharacter is い (B), the guard must reject the update.
        recognizer.complete("あ", RecognitionResult(recognized = true, rank = 0, topCandidate = "あ"))
        advanceUntilIdle()

        assertEquals("い", vm.uiState.value.lastScoredCharacter)
        // Still B's score object — A's late recognition did not overwrite it.
        assertEquals(scoreForB, vm.uiState.value.lastScore)

        // Let B's recognition drain so the test doesn't leak a suspended coroutine.
        recognizer.complete("い", RecognitionResult.UNAVAILABLE)
        advanceUntilIdle()
    }

    // ---- launch behavior -------------------------------------------------

    /**
     * A cold start drops straight into FREE practice (no graded SRS session) on
     * a character from the scoped deck when the last-used Library section was
     * Characters (the default).
     */
    @Test
    fun `launch enters free practice on a character by default`() = runTest(dispatcher) {
        val vm = buildViewModel() // librarySection defaults to 0 (Characters)
        advanceUntilIdle()
        // Launch now shows the opening screen; picking Writing enters free practice.
        vm.setAppMode(AppMode.WRITING)

        val state = vm.uiState.value
        assertEquals(SessionPhase.FREE, state.phase)
        assertTrue(state.freeCharacters.isNotEmpty())
        assertTrue(state.freeWords.isEmpty())
        assertNull(state.freeWord)
        // The random start index must be a valid position in the free deck.
        assertNotNull(state.currentCharacter)
    }

    /**
     * When the last-used section was Words, a cold start resumes free practice
     * on a random word, with the whole scoped word list loaded so Next cycles.
     */
    @Test
    fun `launch enters free practice on a word when last section was words`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.sectionPref = 1 // Words
        val vm = buildViewModel(settings = settings, words = listOf(aiWord()))
        advanceUntilIdle()
        vm.setAppMode(AppMode.WRITING)

        val state = vm.uiState.value
        assertEquals(SessionPhase.FREE, state.phase)
        assertNotNull(state.freeWord)
        assertEquals("あい", state.freeWord?.kana)
        assertTrue(state.freeWords.isNotEmpty())
        assertTrue(state.freeCharacters.isEmpty())
    }

    /**
     * Words section but no words in scope: launch must not land on a blank
     * sheet — it falls back to a character.
     */
    @Test
    fun `launch falls back to a character when the words section is empty`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.sectionPref = 1 // Words, but the fake vocab repo has none
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()
        vm.setAppMode(AppMode.WRITING)

        val state = vm.uiState.value
        assertEquals(SessionPhase.FREE, state.phase)
        assertTrue(state.freeCharacters.isNotEmpty())
        assertNull(state.freeWord)
    }

    /**
     * The Sets tab (section 2) has no single item to resume, so a cold start
     * with it as the last section falls back to a character — even with words
     * in scope — rather than landing on a blank sheet.
     */
    @Test
    fun `launch falls back to a character when last section was the sets tab`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.sectionPref = 2 // Sets
        val vm = buildViewModel(settings = settings, words = listOf(aiWord()))
        advanceUntilIdle()
        vm.setAppMode(AppMode.WRITING)

        val state = vm.uiState.value
        assertEquals(SessionPhase.FREE, state.phase)
        assertTrue(state.freeCharacters.isNotEmpty())
        assertNull(state.freeWord)
    }

    /** Selecting the Sets tab persists (index 2) and publishes into UI state. */
    @Test
    fun `setLibrarySection persists the sets tab`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        vm.setLibrarySection(2)

        assertEquals(2, vm.uiState.value.librarySection)
        assertEquals(2, settings.getLibrarySection())
    }

    /**
     * Closing a quiz that was launched from a saved set raises the one-shot so
     * the Library reopens on the Sets tab; consuming it clears the shot.
     */
    @Test
    fun `closing a set-launched quiz raises and consumes the sets-tab signal`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.savePracticeSet("MySet", PracticeSetType.CHARACTERS, Script.HIRAGANA, listOf("あ"))
        advanceUntilIdle()
        val id = vm.uiState.value.practiceSets.first { it.name == "MySet" }.id

        vm.openQuiz(id)
        vm.closeQuiz()
        assertEquals(true, vm.uiState.value.pendingOpenSetsManager)

        vm.consumeOpenSetsManager()
        assertEquals(false, vm.uiState.value.pendingOpenSetsManager)
    }

    /**
     * From the reading-quiz summary, [PracticeViewModel.retryWrong] restarts the
     * quiz with only the items not answered correctly this round — the wrong AND
     * the skipped ones — and resets the per-item state. The correctly-answered
     * item is dropped.
     */
    @Test
    fun `retryWrong restarts with only the wrong and skipped items`() = runTest(dispatcher) {
        val vm = buildViewModel(
            characters = listOf(
                character("a", "あ", frequency = 1),
                character("i", "い", frequency = 2),
                character("u", "う", frequency = 3),
                character("e", "え", frequency = 4),
            ),
        )
        advanceUntilIdle()

        vm.openQuiz(kind = QuizKind.READING)
        vm.startQuiz()
        advanceUntilIdle()
        // A full 4-item reading quiz: one correct, one wrong, two skipped.
        assertEquals(4, vm.uiState.value.quiz!!.items.size)

        val correctKey = vm.uiState.value.quiz!!.current!!.key
        vm.readingResult(correct = true)
        vm.quizAdvance()
        vm.readingResult(correct = false)
        vm.quizAdvance()
        vm.quizAdvance() // skip
        vm.quizAdvance() // skip -> SUMMARY
        advanceUntilIdle()
        assertEquals(QuizStage.SUMMARY, vm.uiState.value.quiz!!.stage)

        vm.retryWrong()
        advanceUntilIdle()

        val quiz = vm.uiState.value.quiz!!
        assertEquals(QuizStage.RUNNING, quiz.stage)
        assertEquals(3, quiz.items.size) // 1 wrong + 2 skipped
        assertTrue(quiz.items.none { it.key == correctKey })
        assertEquals(0, quiz.index)
        assertTrue(quiz.results.isEmpty())
        assertTrue(quiz.submittedKeys.isEmpty())
        assertFalse(quiz.revealed)
    }

    /**
     * From the writing-quiz summary, [PracticeViewModel.retryBelowScore] restarts
     * the quiz with only the items whose score was below the given cutoff. Scored
     * items count; genuinely-skipped items (no result) are excluded — they have no
     * score to be "under". A cutoff above every possible score picks all scored
     * items; per-item state resets like [startQuiz].
     */
    @Test
    fun `retryBelowScore restarts with only the scored-below-cutoff items`() = runTest(dispatcher) {
        val vm = buildViewModel(
            characters = listOf(
                character("a", "あ", frequency = 1),
                character("i", "い", frequency = 2),
                character("u", "う", frequency = 3),
                character("e", "え", frequency = 4),
            ),
        )
        advanceUntilIdle()

        vm.openQuiz(kind = QuizKind.WRITING)
        vm.startQuiz()
        advanceUntilIdle()
        assertEquals(4, vm.uiState.value.quiz!!.items.size)

        // Write (and score) the first two items; skip the last two.
        val firstKey = vm.uiState.value.quiz!!.current!!.key
        vm.quizSubmit(inkSession(), cell())
        vm.quizAdvance()
        val secondKey = vm.uiState.value.quiz!!.current!!.key
        vm.quizSubmit(inkSession(), cell())
        vm.quizAdvance()
        vm.quizAdvance() // skip
        vm.quizAdvance() // skip -> SUMMARY
        advanceUntilIdle()
        assertEquals(QuizStage.SUMMARY, vm.uiState.value.quiz!!.stage)
        // Both written items produced a headline; the skips did not.
        assertEquals(2, vm.uiState.value.quiz!!.results.size)

        // A cutoff above the max possible score (100) selects every scored item.
        vm.retryBelowScore(101)
        advanceUntilIdle()

        val quiz = vm.uiState.value.quiz!!
        assertEquals(QuizStage.RUNNING, quiz.stage)
        assertEquals(2, quiz.items.size) // the two scored items only
        assertEquals(setOf(firstKey, secondKey), quiz.items.map { it.key }.toSet())
        assertEquals(0, quiz.index)
        assertTrue(quiz.results.isEmpty())
        assertTrue(quiz.submittedKeys.isEmpty())
        assertFalse(quiz.revealed)
    }

    /**
     * A cutoff below every score selects nothing, so [retryBelowScore] is a no-op
     * and the quiz stays on the summary.
     */
    @Test
    fun `retryBelowScore with an unreachable cutoff is a no-op`() = runTest(dispatcher) {
        val vm = buildViewModel(
            characters = listOf(
                character("a", "あ", frequency = 1),
                character("i", "い", frequency = 2),
            ),
        )
        advanceUntilIdle()

        vm.openQuiz(kind = QuizKind.WRITING)
        vm.startQuiz()
        advanceUntilIdle()
        vm.quizSubmit(inkSession(), cell())
        vm.quizAdvance()
        vm.quizSubmit(inkSession(), cell())
        vm.quizAdvance() // -> SUMMARY
        advanceUntilIdle()
        assertEquals(QuizStage.SUMMARY, vm.uiState.value.quiz!!.stage)

        // No score can be below 1, so nothing qualifies.
        vm.retryBelowScore(1)
        advanceUntilIdle()
        assertEquals(QuizStage.SUMMARY, vm.uiState.value.quiz!!.stage)
    }

    // ---- default practice sets ------------------------------------------

    /**
     * On first launch (the seed latch is off) the built-in default practice
     * sets are seeded into the library and the latch flips on.
     */
    @Test
    fun `default sets seed once on first launch`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository().apply { seededDefaults = false }
        val sets = FakePracticeSetRepository()
        val vm = buildViewModel(settings = settings, practiceSetRepository = sets)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.practiceSets.isNotEmpty())
        assertTrue(settings.seededDefaults)
    }

    /**
     * When the latch is already on (a subsequent launch, or a user who deleted
     * a default), nothing is re-seeded.
     */
    @Test
    fun `default sets do not re-seed when already seeded`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository().apply { seededDefaults = true }
        val sets = FakePracticeSetRepository()
        val vm = buildViewModel(settings = settings, practiceSetRepository = sets)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.practiceSets.isEmpty())
    }

    /**
     * Multi-copy 2×2/3×3 layouts are repetition worksheets, not scored
     * attempts. In FREE, [freeSubmit] is guarded off for a multi-cell character
     * (the UI shows Clear·Next there, never Submit): no score is produced and
     * the writing↔reviewing flag never flips.
     */
    @Test
    fun `multi-cell layout suppresses free-practice scoring`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setPracticeLayout(PracticeLayout.LARGE)
        val vm = buildViewModel(settings = settings)
        advanceUntilIdle()

        vm.practiceCharacter(deck().first())
        advanceUntilIdle()
        assertEquals(PracticeLayout.LARGE, vm.uiState.value.practiceLayout)
        assertEquals(SessionPhase.FREE, vm.uiState.value.phase)

        // A cell is passed, but the multi-cell guard short-circuits regardless.
        vm.freeSubmit(inkSession(), cell())
        advanceUntilIdle()
        assertNull(vm.uiState.value.lastScore)
        assertFalse(vm.uiState.value.freeSubmitted)
    }

    /**
     * The counterpart: a single character in the SINGLE layout with a real 1×1
     * cell still scores on Submit and enters the reviewing sub-state.
     */
    @Test
    fun `single-cell layout scores on submit`() = runTest(dispatcher) {
        val vm = buildViewModel() // default layout SINGLE
        advanceUntilIdle()

        vm.practiceCharacter(deck().first())
        advanceUntilIdle()

        vm.freeSubmit(inkSession(), cell())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.freeSubmitted)
        assertNotNull(vm.uiState.value.lastScore)
    }

    /**
     * Changing the practice layout after a FREE submit must clear the pending
     * feedback (the submitted flag + score cards) along with the sheet — the
     * ghost cells move, so stale "Again·Next" chrome and a score card would
     * otherwise float over a freshly-blanked canvas.
     */
    @Test
    fun `changing layout clears pending free-practice feedback`() = runTest(dispatcher) {
        val vm = buildViewModel() // default layout SINGLE
        advanceUntilIdle()

        vm.practiceCharacter(deck().first())
        advanceUntilIdle()

        vm.freeSubmit(inkSession(), cell())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.freeSubmitted)
        assertNotNull(vm.uiState.value.lastScore)

        vm.setPracticeLayout(PracticeLayout.LARGE)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.freeSubmitted)
        assertNull(vm.uiState.value.lastScore)
        assertNull(vm.uiState.value.lastScoredCharacter)
        assertNull(vm.uiState.value.lastWordScores)
        assertNull(vm.uiState.value.lastWordAverage)
    }

    /**
     * With scoring OFF, a FREE submit produces no headline and records no
     * attempt — the geometry/persistence path is skipped entirely, so nothing
     * lands in the practice repository.
     */
    @Test
    fun `free submit with scoring off records no attempt`() = runTest(dispatcher) {
        val practice = FakePracticeRepository()
        val settings = FakeSettingsRepository()
        settings.setScoringMode(ScoringMode.OFF)
        val vm = buildViewModel(settings = settings, practiceRepository = practice)
        advanceUntilIdle()

        vm.practiceCharacter(deck().first())
        advanceUntilIdle()

        vm.freeSubmit(inkSession(), cell())
        advanceUntilIdle()

        assertTrue(practice.recorded.isEmpty())
    }

    // ---- word (per-cell) scoring ----------------------------------------

    /** The word "あい" — both kana are in the deck, so it can be per-kana scored. */
    private fun aiWord(): VocabularyWord =
        VocabularyWord.fromKana(kana = "あい", meaning = "love")

    /** Two 109-wide guide cells side by side (one per kana of "あい"). */
    private fun twoCells(): List<CellRect> = listOf(
        CellRect(left = 0f, top = 0f, size = 109f),   // center (54.5, 54.5)
        CellRect(left = 120f, top = 0f, size = 109f), // center (174.5, 54.5)
    )

    /** A session with one stroke inside cell 0 and one inside cell 1. */
    private fun wordSession(): DrawingSession = DrawingSession(
        strokes = mutableListOf(
            UserStroke(
                points = mutableListOf(
                    StrokePoint(x = 12f, y = 12f, pressure = 1f, timestamp = 0L),
                    StrokePoint(x = 88f, y = 88f, pressure = 1f, timestamp = 50L),
                ),
            ),
            UserStroke(
                points = mutableListOf(
                    StrokePoint(x = 132f, y = 12f, pressure = 1f, timestamp = 100L),
                    StrokePoint(x = 208f, y = 88f, pressure = 1f, timestamp = 150L),
                ),
            ),
        ),
    )

    /**
     * Free WORD practice with one guide cell per kana (`cells.size == units.size`)
     * scores each kana independently: [PracticeUiState.lastWordScores] carries one
     * [WordUnitScore] per kana and [lastWordAverage] the 0..100 headline average.
     */
    @Test
    fun `free word practice scores each kana per cell`() = runTest(dispatcher) {
        val vm = buildViewModel(words = listOf(aiWord()))
        advanceUntilIdle()

        vm.practiceWord(aiWord())
        advanceUntilIdle()
        assertEquals(SessionPhase.FREE, vm.uiState.value.phase)
        assertEquals("あい", vm.uiState.value.freeWord?.kana)

        val cells = twoCells()
        vm.freeSubmit(wordSession(), cells.first(), cells)
        advanceUntilIdle()

        val scores = vm.uiState.value.lastWordScores
        assertNotNull(scores)
        assertEquals(2, scores!!.size)
        assertEquals("あ", scores[0].glyph)
        assertEquals("い", scores[1].glyph)

        val avg = vm.uiState.value.lastWordAverage
        assertNotNull(avg)
        assertTrue("word average $avg out of range", avg!! in 0..100)
    }

    /**
     * A library word tap loads the whole active-script word list (positioned at
     * the tapped word) so **Next** cycles the words, mirroring [practiceCharacter]
     * on the character deck. [advanceToNextCharacter] then steps to the next word.
     */
    @Test
    fun `practiceWord loads the word list so Next cycles words`() = runTest(dispatcher) {
        val second = VocabularyWord.fromKana(kana = "うえ", meaning = "up")
        val vm = buildViewModel(words = listOf(aiWord(), second))
        advanceUntilIdle()

        vm.practiceWord(aiWord())
        advanceUntilIdle()
        assertEquals("あい", vm.uiState.value.freeWord?.kana)
        assertEquals(2, vm.uiState.value.freeWords.size)

        vm.advanceToNextCharacter()
        advanceUntilIdle()
        assertEquals("うえ", vm.uiState.value.freeWord?.kana)
    }

    /**
     * Changing the scoring mode must clear the per-kana word chips too — the
     * "reset wherever lastScore is" invariant. Regression for a round-2 review
     * finding where [setScoringMode] cleared only [lastScore], leaving stale
     * word chips lingering over the sheet after a Library mode change.
     */
    @Test
    fun `setScoringMode clears lingering word-score chips`() = runTest(dispatcher) {
        val vm = buildViewModel(words = listOf(aiWord()))
        advanceUntilIdle()

        vm.practiceWord(aiWord())
        advanceUntilIdle()
        val cells = twoCells()
        vm.freeSubmit(wordSession(), cells.first(), cells)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.lastWordScores)

        // Default is FORM; switching to BOTH must wipe the chips.
        vm.setScoringMode(ScoringMode.BOTH)
        advanceUntilIdle()
        assertNull(vm.uiState.value.lastWordScores)
        assertNull(vm.uiState.value.lastWordAverage)
    }

    /**
     * Per-kana scoring runs only when there is exactly one guide cell per reading
     * unit. With no cells (multi-copy sheet / fit meaningless), [lastWordScores]
     * stays null and a single plain word row is recorded instead.
     */
    @Test
    fun `free word practice without matching cells leaves word scores null`() = runTest(dispatcher) {
        val vm = buildViewModel(words = listOf(aiWord()))
        advanceUntilIdle()

        vm.practiceWord(aiWord())
        advanceUntilIdle()

        // No cells → cells.size (0) != units.size (2): the per-cell gate is off.
        vm.freeSubmit(wordSession(), null, emptyList())
        advanceUntilIdle()

        assertNull(vm.uiState.value.lastWordScores)
        assertNull(vm.uiState.value.lastWordAverage)
        assertTrue(vm.uiState.value.freeSubmitted)
    }

    /**
     * On a scored word submit, each kana persists its own scored [PracticeAttempt]
     * (character = the unit glyph, form score set) plus a word-level row keyed by
     * the whole word string carrying the average form.
     */
    @Test
    fun `free word practice persists per-kana and word-level attempts`() = runTest(dispatcher) {
        val practice = FakePracticeRepository()
        val vm = buildViewModel(practiceRepository = practice, words = listOf(aiWord()))
        advanceUntilIdle()

        vm.practiceWord(aiWord())
        advanceUntilIdle()

        val cells = twoCells()
        vm.freeSubmit(wordSession(), cells.first(), cells)
        advanceUntilIdle()

        // Per-kana rows: "あ" and "い" with a non-null form score.
        val aRow = practice.recorded.firstOrNull { it.character == "あ" }
        val iRow = practice.recorded.firstOrNull { it.character == "い" }
        assertNotNull("no per-kana row for あ", aRow)
        assertNotNull("no per-kana row for い", iRow)
        assertNotNull(aRow!!.formScore)
        assertNotNull(iRow!!.formScore)

        // Word-level row keyed by the full word.
        val wordRow = practice.recorded.firstOrNull { it.character == "あい" }
        assertNotNull("no word-level row for あい", wordRow)
        assertNotNull(wordRow!!.formScore)
    }

    // ---- fakes -----------------------------------------------------------

    private class FakeCharacterRepository(
        private val characters: List<HiraganaCharacter>,
    ) : CharacterRepository {
        override suspend fun getCharacters(): List<HiraganaCharacter> = characters
    }

    private class FakePracticeRepository(
        /** Every recorded attempt is appended here so tests can assert on persistence. */
        val recorded: MutableList<PracticeAttempt> = mutableListOf(),
    ) : PracticeRepository {
        /** Ids are 1-based row indices, mirroring Room's autoGenerate. */
        override suspend fun recordAttempt(attempt: PracticeAttempt): Long {
            recorded.add(attempt)
            return recorded.size.toLong()
        }

        /** Applies the refinement in place, so tests see the stored headline. */
        override suspend fun updateHeadline(id: Long, headline: Int) {
            val index = (id - 1).toInt()
            recorded.getOrNull(index)?.let {
                recorded[index] = it.copy(headlineScore = headline.toFloat())
            }
        }

        override fun observeAttempts(): Flow<List<PracticeAttempt>> = emptyFlow()
        override fun observeScoreSummaries(): Flow<List<CharacterScoreSummary>> = emptyFlow()
    }

    private class FakeVocabularyRepository(
        private val words: List<VocabularyWord> = emptyList(),
    ) : VocabularyRepository {
        override suspend fun getWords(): List<VocabularyWord> = words
        override suspend fun getProgress(): List<VocabularyProgress> = emptyList()
        override suspend fun unlock(kana: String, atMillis: Long) = Unit
        override suspend fun recordPractice(kana: String, atMillis: Long) = Unit
    }

    /** Returns UNAVAILABLE immediately — the common no-op recognizer. */
    private class FakeRecognizer : HandwritingRecognizer {
        override suspend fun recognize(
            strokes: List<List<RecognizerStrokePoint>>,
            target: String,
        ): RecognitionResult = RecognitionResult.UNAVAILABLE
    }

    /** No-op pronunciation engine — the VM only forwards calls to it. */
    private class FakePronunciationPlayer(
        initial: VoiceStatus = VoiceStatus.UNAVAILABLE,
    ) : PronunciationPlayer {
        override val status = MutableStateFlow(initial)
        override fun play(glyph: String) {}
        override fun refresh() {}
        override fun release() {}
    }

    /**
     * Suspends each [recognize] on a per-target [CompletableDeferred] so the
     * test controls exactly when (and in what order) results resolve.
     */
    private class ControllableRecognizer : HandwritingRecognizer {
        private val gates = mutableMapOf<String, CompletableDeferred<RecognitionResult>>()

        override suspend fun recognize(
            strokes: List<List<RecognizerStrokePoint>>,
            target: String,
        ): RecognitionResult {
            val gate = gates.getOrPut(target) { CompletableDeferred() }
            return gate.await()
        }

        fun complete(target: String, result: RecognitionResult) {
            gates.getOrPut(target) { CompletableDeferred() }.complete(result)
        }
    }

    /**
     * In-memory settings: stores whatever is set, returns the documented
     * defaults otherwise (scoringMode FORM, activeScript HIRAGANA, all opt-ins
     * false, grid/ghost true, layout SINGLE, library-grid false).
     */
    private class FakeSettingsRepository : SettingsRepository {
        private var beginner = false
        private var grid = true
        private var ghost = true
        private var layout = PracticeLayout.SINGLE
        private var script = Script.HIRAGANA
        private var diacritics = false
        private var yoon = false
        private var extended = false
        private var scoringMode = ScoringMode.FORM
        private var libraryGridView = false
        var sectionPref = 0
        private var setsSortPref: String? = null
        // Defaults to true so unrelated tests don't seed the built-in sets; the
        // seeding tests set it explicitly.
        var seededDefaults = true
        private var brushInkColor = 0xFF20222E.toInt()
        private var brushInkColorDark = 0xFFEFE7DA.toInt()
        private var appearance = AppearanceMode.LIGHT
        private var brushEffect = BrushEffect.BRUSH
        private var autoNarrate = false
        private var readingAutoSpeak = true
        private var readingMeaningPractice = false
        private var characterForm = CharacterForm.PRINTED

        override fun isBeginnerMode(): Boolean = beginner
        override fun setBeginnerMode(enabled: Boolean) { beginner = enabled }
        override fun isGridEnabled(): Boolean = grid
        override fun setGridEnabled(enabled: Boolean) { grid = enabled }
        override fun isGhostEnabled(): Boolean = ghost
        override fun setGhostEnabled(enabled: Boolean) { ghost = enabled }
        override fun getPracticeLayout(): PracticeLayout = layout
        override fun setPracticeLayout(layout: PracticeLayout) { this.layout = layout }
        override fun getActiveScript(): Script = script
        override fun setActiveScript(script: Script) { this.script = script }
        override fun isDiacriticsIncluded(): Boolean = diacritics
        override fun setDiacriticsIncluded(included: Boolean) { diacritics = included }
        override fun isYoonIncluded(): Boolean = yoon
        override fun setYoonIncluded(included: Boolean) { yoon = included }
        override fun isExtendedIncluded(): Boolean = extended
        override fun setExtendedIncluded(included: Boolean) { extended = included }
        override fun getScoringMode(): ScoringMode = scoringMode
        override fun setScoringMode(mode: ScoringMode) { scoringMode = mode }
        override fun isLibraryGridView(): Boolean = libraryGridView
        override fun setLibraryGridView(gridView: Boolean) { libraryGridView = gridView }
        override fun getLibrarySection(): Int = sectionPref
        override fun setLibrarySection(index: Int) { sectionPref = index }
        override fun getSetsSortKey(): String? = setsSortPref
        override fun setSetsSortKey(key: String) { setsSortPref = key }
        override fun hasSeededDefaultSets(): Boolean = seededDefaults
        override fun setDefaultSetsSeeded() { seededDefaults = true }
        override fun getBrushInkColor(): Int = brushInkColor
        override fun setBrushInkColor(color: Int) { brushInkColor = color }
        override fun getBrushInkColorDark(): Int = brushInkColorDark
        override fun setBrushInkColorDark(color: Int) { brushInkColorDark = color }
        override fun getBrushEffect(): BrushEffect = brushEffect
        override fun setBrushEffect(effect: BrushEffect) { brushEffect = effect }
        override fun isAutoNarrateEnabled(): Boolean = autoNarrate
        override fun setAutoNarrateEnabled(enabled: Boolean) { autoNarrate = enabled }
        override fun isReadingAutoSpeakEnabled(): Boolean = readingAutoSpeak
        override fun setReadingAutoSpeakEnabled(enabled: Boolean) { readingAutoSpeak = enabled }
        override fun isReadingMeaningPractice(): Boolean = readingMeaningPractice
        override fun setReadingMeaningPractice(enabled: Boolean) { readingMeaningPractice = enabled }
        private var mnemonics = true
        override fun isMnemonicsEnabled(): Boolean = mnemonics
        override fun setMnemonicsEnabled(enabled: Boolean) { mnemonics = enabled }
        private var mnemonicChoices = mapOf<String, String>()
        override fun getMnemonicChoices(): Map<String, String> = mnemonicChoices
        override fun setMnemonicChoices(choices: Map<String, String>) {
            mnemonicChoices = choices
        }
        private var customMnemonics = mapOf<String, List<KanaMnemonic>>()
        override fun getCustomMnemonics(): Map<String, List<KanaMnemonic>> = customMnemonics
        override fun setCustomMnemonics(customs: Map<String, List<KanaMnemonic>>) {
            customMnemonics = customs
        }
        override fun getCharacterForm(): CharacterForm = characterForm
        override fun setCharacterForm(form: CharacterForm) { characterForm = form }
        private var appModeValue: AppMode? = null
        override fun getAppMode(): AppMode? = appModeValue
        override fun setAppMode(mode: AppMode) { appModeValue = mode }
        override fun getAppearance(): AppearanceMode = appearance
        override fun setAppearance(mode: AppearanceMode) { appearance = mode }
        private var skippedUpdate: String? = null
        override fun getSkippedUpdateVersion(): String? = skippedUpdate
        override fun setSkippedUpdateVersion(versionName: String) { skippedUpdate = versionName }
    }

    /** No-op updater: the launch check finds nothing, so no dialog is offered. */
    private class FakeAppUpdater : AppUpdater {
        override suspend fun check(): UpdateInfo? = null
        override suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit) =
            java.io.File("unused")
        override fun canInstall(): Boolean = true
        override fun requestInstallPermission() {}
        override fun install(apk: java.io.File) {}
    }

    /** In-memory practice sets, backing a [MutableStateFlow] for observation. */
    private class FakeReadingProgressRepository : ReadingProgressRepository {
        override fun observe(): Flow<List<ReadingProgress>> = MutableStateFlow(emptyList())
        override suspend fun record(itemKey: String, typed: Boolean, correct: Boolean) {}
    }

    private class FakePracticeSetRepository : PracticeSetRepository {
        private val sets = MutableStateFlow<List<PracticeSet>>(emptyList())
        private var nextId = 1L

        override fun observeSets(): Flow<List<PracticeSet>> = sets

        override suspend fun createSet(
            name: String,
            type: PracticeSetType,
            script: Script,
            members: List<String>,
        ): Long {
            val id = nextId++
            sets.value = sets.value + PracticeSet(id, name, type, script, members)
            return id
        }

        override suspend fun renameSet(id: Long, name: String) {
            sets.value = sets.value.map { if (it.id == id) it.copy(name = name) else it }
        }

        override suspend fun deleteSet(id: Long) {
            sets.value = sets.value.filterNot { it.id == id }
        }

        override suspend fun updateMembers(id: Long, members: List<String>) {
            sets.value = sets.value.map { if (it.id == id) it.copy(members = members) else it }
        }
    }
}
