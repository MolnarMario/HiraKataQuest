package com.hive.hiragana.presentation.viewmodels

import com.hive.hiragana.domain.model.CharacterProgress
import com.hive.hiragana.domain.model.Grade
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.PracticeAttempt
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.ReviewCard
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.model.StrokeDefinition
import com.hive.hiragana.domain.model.StrokeFrame
import com.hive.hiragana.domain.model.VocabularyProgress
import com.hive.hiragana.domain.model.VocabularyWord
import com.hive.hiragana.domain.repository.CharacterRepository
import com.hive.hiragana.domain.repository.HandwritingRecognizer
import com.hive.hiragana.domain.repository.LearningRepository
import com.hive.hiragana.domain.repository.PracticeRepository
import com.hive.hiragana.domain.repository.RecognizerStrokePoint
import com.hive.hiragana.domain.repository.SettingsRepository
import com.hive.hiragana.domain.repository.VocabularyRepository
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private val fixedClock: Clock =
        Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC)

    private fun buildViewModel(
        characters: List<HiraganaCharacter> = deck(),
        recognizer: HandwritingRecognizer = FakeRecognizer(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = PracticeViewModel(
        characterRepository = FakeCharacterRepository(characters),
        practiceRepository = FakePracticeRepository(),
        learningRepository = FakeLearningRepository(),
        vocabularyRepository = FakeVocabularyRepository(),
        settingsRepository = settings,
        handwritingRecognizer = recognizer,
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
     * The actual reported bug: [startNextSession] used to drop scoringMode,
     * reverting scoring to the FORM default. Reaching FREE via
     * [practiceCharacter] is the shortest route past the COMPLETE/FREE guard.
     */
    @Test
    fun `scoringMode survives startNextSession`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.practiceCharacter(deck().first())
        advanceUntilIdle()
        assertEquals(SessionPhase.FREE, vm.uiState.value.phase)

        vm.setScoringMode(ScoringMode.OFF)
        vm.startNextSession()
        advanceUntilIdle()

        // Would be FORM (the default) under the pre-fix startNextSession that
        // omitted scoringMode from its rebuild.
        assertEquals(ScoringMode.OFF, vm.uiState.value.scoringMode)
    }

    /**
     * A stale recognition result for a previous glyph must not clobber the
     * current glyph's score. Two FREE grades (A then B) each launch a
     * recognition coroutine that suspends on a per-target deferred we control;
     * completing A's deferred AFTER B was graded must leave lastScore labelled
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

        // Grade A (あ) — synchronous geometry sets lastScore/-Character to あ,
        // recognition for あ is now suspended.
        vm.gradeCurrentCharacter(Grade.GOOD, inkSession())
        advanceUntilIdle()
        assertEquals("あ", vm.uiState.value.lastScoredCharacter)
        // FREE advanced to index 1 (い).
        assertEquals("い", vm.uiState.value.currentCharacter?.character)

        // Grade B (い) — geometry sets lastScore/-Character to い; recognition
        // for い is now suspended too.
        vm.gradeCurrentCharacter(Grade.GOOD, inkSession())
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

    // ---- fakes -----------------------------------------------------------

    private class FakeCharacterRepository(
        private val characters: List<HiraganaCharacter>,
    ) : CharacterRepository {
        override suspend fun getCharacters(): List<HiraganaCharacter> = characters
    }

    private class FakePracticeRepository : PracticeRepository {
        override suspend fun recordAttempt(attempt: PracticeAttempt) = Unit
        override fun observeAttempts(): Flow<List<PracticeAttempt>> = emptyFlow()
        override fun observeScoreSummaries(): Flow<List<CharacterScoreSummary>> = emptyFlow()
    }

    private class FakeLearningRepository : LearningRepository {
        override suspend fun getReviewCards(): List<ReviewCard> = emptyList()
        override suspend fun upsertReviewCard(card: ReviewCard) = Unit
        override suspend fun getCharacterProgress(): List<CharacterProgress> = emptyList()
        override suspend fun markIntroduced(characterId: String, atMillis: Long) = Unit
        override suspend fun incrementTraces(characterId: String) = Unit
    }

    private class FakeVocabularyRepository : VocabularyRepository {
        override suspend fun getWords(): List<VocabularyWord> = emptyList()
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
        private var grid = true
        private var ghost = true
        private var layout = PracticeLayout.SINGLE
        private var script = Script.HIRAGANA
        private var diacritics = false
        private var yoon = false
        private var extended = false
        private var scoringMode = ScoringMode.FORM
        private var libraryGridView = false
        private var brushInkColor = 0xFF20222E.toInt()

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
        override fun getBrushInkColor(): Int = brushInkColor
        override fun setBrushInkColor(color: Int) { brushInkColor = color }
    }
}
