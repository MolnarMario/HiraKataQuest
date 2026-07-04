package com.hive.hiragana.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hive.hiragana.HiraganaApplication
import com.hive.hiragana.domain.learning.LessonGenerator
import com.hive.hiragana.domain.learning.SrsScheduler
import com.hive.hiragana.domain.model.Grade
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.PracticeAttempt
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.KanaUnits
import com.hive.hiragana.domain.model.PracticeSource
import com.hive.hiragana.domain.model.ReviewCard
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.model.ScriptScope
import com.hive.hiragana.domain.model.StudySession
import com.hive.hiragana.domain.model.VocabularyWord
import com.hive.hiragana.domain.repository.CharacterRepository
import com.hive.hiragana.domain.repository.HandwritingRecognizer
import com.hive.hiragana.domain.repository.LearningRepository
import com.hive.hiragana.domain.repository.PracticeRepository
import com.hive.hiragana.domain.repository.RecognizerStrokePoint
import com.hive.hiragana.domain.repository.SettingsRepository
import com.hive.hiragana.domain.repository.VocabularyRepository
import com.hive.hiragana.domain.scoring.CellRect
import com.hive.hiragana.domain.scoring.CharacterScore
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
import com.hive.hiragana.domain.scoring.GeometryScorer
import com.hive.hiragana.domain.scoring.Pt
import com.hive.hiragana.domain.scoring.RecognitionResult
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.drawing.stroke.DrawingSession
import com.hive.hiragana.drawing.stroke.durationMillis
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "HiraganaSession"

/**
 * Step of the Phase 3 session state machine (docs/PHASE3-DESIGN.md §6).
 *
 * NEW_CHARACTER and PRACTICE are deliberately collapsed into one phase:
 * the Phase 2 animated preview auto-plays as soon as the new character is
 * shown, and its first trace *is* practice repetition 1 — there is no
 * separate "introduction" UI to build. [PracticeUiState.repetitionIndex]
 * distinguishes "Practice 1/3" from "Practice 2/3" within this phase.
 */
enum class SessionPhase {
    LOADING,
    REVIEWS,
    NEW_CHARACTER,
    PRACTICE,
    VOCABULARY,
    COMPLETE,
    FREE,
}

/** Stage of the library-launched "Quiz me!" overlay. */
enum class QuizStage { SETUP, RUNNING, SUMMARY }

/**
 * State of the "Quiz me!" overlay: write [characters] one at a time from
 * their romaji prompt alone. The ghost tracing guide stays off while
 * writing ([revealed] shows it, along with the kana, for self-checking),
 * while [showGrid] (the crosshair practice cell) is a per-quiz choice
 * that never touches the persisted grid setting. **Next submits** the
 * current character — it is auto-scored (no self-grade) and recorded — and
 * advances; an empty canvas + Next is a genuine skip. [results] (by
 * character id) holds the auto quality-score headline for each SUBMITTED
 * character; a character absent from the map was skipped. Back re-visits an
 * earlier character (blank canvas — re-writing re-submits, overwriting its
 * result). Overlays whatever phase is active, exactly like the library;
 * closing it restores that phase.
 */
data class QuizState(
    val stage: QuizStage = QuizStage.SETUP,
    val requestedCount: Int = 5,
    val showGrid: Boolean = true,
    val characters: List<HiraganaCharacter> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    // Two-step per character: false = writing (ghost off, no feedback, button
    // "Submit"); true = the char has been submitted and is being reviewed
    // (ghost revealed, feedback shown, button "Next"). Reset on advance/back.
    val submitted: Boolean = false,
    val results: Map<String, Int> = emptyMap(),
) {
    val current: HiraganaCharacter? get() = characters.getOrNull(index)

    /** True on the quiz's last character — Next becomes Finish. */
    val isLast: Boolean get() = index >= characters.size - 1
}

/**
 * Session/deck state for the practice screen. Drawing state is deliberately
 * kept out — it lives in the UI-owned CanvasState.
 *
 * [currentCharacter] derives from whichever phase is active: the head of
 * the review queue, the new character being introduced/practiced, or
 * (FREE only) [currentIndex] over [freeCharacters]. Word steps have no
 * single current character — the whole word is traced on one sheet; the
 * canvas renders [canvasGlyphs], which is the word's kana in word modes
 * and the single current character otherwise. [canvasClearKey] increments
 * on every advance (each review, each practice rep, each word, each phase
 * change) so PracticeScreen can re-key its clearing LaunchedEffect off a
 * single monotonic counter instead of tracking every phase's own position
 * field.
 */
data class PracticeUiState(
    val isLoading: Boolean = true,
    val phase: SessionPhase = SessionPhase.LOADING,
    val characters: List<HiraganaCharacter> = emptyList(),
    val errorMessage: String? = null,
    val canvasClearKey: Int = 0,
    // REVIEWS
    val reviewQueue: List<HiraganaCharacter> = emptyList(),
    val reviewsTotal: Int = 0,
    val reviewsDone: Int = 0,
    // NEW_CHARACTER / PRACTICE
    val newCharacter: HiraganaCharacter? = null,
    val repetitionIndex: Int = 0,
    val repetitionsTotal: Int = 0,
    // VOCABULARY
    val vocabularyQueue: List<VocabularyWord> = emptyList(),
    val currentWord: VocabularyWord? = null,
    val wordsTotal: Int = 0,
    val wordsPracticed: Int = 0,
    val newlyUnlockedKana: Set<String> = emptySet(),
    // COMPLETE
    val allCaughtUp: Boolean = false,
    val newCharacterLearned: String? = null,
    val wordsUnlocked: List<String> = emptyList(),
    val hasLearnedCharacters: Boolean = false,
    // FREE (post-session endless loop; via the library it can hold ANY
    // characters, not just learned ones, and optionally a word context)
    val freeCharacters: List<HiraganaCharacter> = emptyList(),
    val currentIndex: Int = 0,
    val gradedCount: Int = 0,
    val freeWord: VocabularyWord? = null,
    // LIBRARY (browse everything; overlays whatever phase is active)
    val isLibraryOpen: Boolean = false,
    val allWords: List<VocabularyWord> = emptyList(),
    val unlockedWordKana: Set<String> = emptySet(),
    val learnedCharacterIds: Set<String> = emptySet(),
    // QUIZ (write-from-memory overlay, launched from the library; renders
    // above the library so closing it drops back into browsing)
    val quiz: QuizState? = null,
    // SETTINGS (persisted via SettingsRepository)
    val isGridEnabled: Boolean = true,
    val isGhostEnabled: Boolean = true,
    val isLibraryGridView: Boolean = false,
    val practiceLayout: PracticeLayout = PracticeLayout.SINGLE,
    // SCRIPT (which writing system is being studied; persisted). [characters]
    // and [allWords] stay the FULL merged deck so cross-script glyph→romaji
    // lookups always resolve; [activeScriptCharacters] is the scoped view that
    // feeds session generation, the quiz pool, and free practice.
    val activeScript: Script = Script.HIRAGANA,
    // Whether voiced (dakuten) / semi-voiced (handakuten) kana are part of the
    // study flow (persisted; default false so beginners start on base kana).
    val includeDiacritics: Boolean = false,
    // Whether yōon (contracted syllables like きょ) are part of the study flow
    // (persisted; default false — the most advanced kana, opted in last).
    val includeYoon: Boolean = false,
    // Whether extended kana (small tsu, small-vowel/foreign combos, obsolete
    // kana) are part of the study flow (persisted; default false).
    val includeExtended: Boolean = false,
    // What handwriting-quality scoring judges (persisted; default FORM).
    val scoringMode: ScoringMode = ScoringMode.FORM,
    // Brush ink color as a packed ARGB Int (persisted; purely visual — feeds
    // TracingCanvas.inkColor, never affects geometry or scoring). Default InkBlack.
    val brushInkColor: Int = 0xFF20222E.toInt(),
    // Latest computed quality score, shown as advisory feedback after a graded
    // single character. Null when scoring is off/unavailable or on word steps.
    // Purely transient UI state — never persisted.
    val lastScore: CharacterScore? = null,
    // The glyph [lastScore] was computed for. The canvas advances on grade, so
    // the card can outlive its character — labelling it with the scored glyph
    // stops the score reading as if it judged the character now on screen.
    val lastScoredCharacter: String? = null,
    // Per-glyph quality history (avg/best), keyed by glyph string, for the
    // Library rows. Sourced from the DB, carried across session resets.
    val scoreStats: Map<String, CharacterScoreSummary> = emptyMap(),
) {
    /**
     * The scoped study deck: the active script, minus voiced kana / yōon /
     * extended kana unless their opt-ins are on. Feeds session generation, the
     * quiz pool, FREE practice, and the Library lists.
     */
    val activeScriptCharacters: List<HiraganaCharacter>
        get() = ScriptScope.scopedCharacters(
            characters, activeScript, includeDiacritics, includeYoon, includeExtended,
        )

    val currentCharacter: HiraganaCharacter?
        get() = when (phase) {
            SessionPhase.REVIEWS -> reviewQueue.firstOrNull()
            SessionPhase.NEW_CHARACTER, SessionPhase.PRACTICE -> newCharacter
            // Words are traced whole — no single current character.
            SessionPhase.VOCABULARY -> null
            SessionPhase.FREE -> if (freeWord != null) null else freeCharacters.getOrNull(currentIndex)
            SessionPhase.LOADING, SessionPhase.COMPLETE -> null
        }

    /**
     * Glyphs the tracing canvas renders, one practice cell each: the whole
     * word's kana in word modes (split into reading units so a yōon like きょ
     * shares one cell instead of orphaning the small ょ), otherwise the single
     * current character.
     */
    val canvasGlyphs: List<String>
        get() {
            val word = when {
                phase == SessionPhase.VOCABULARY -> currentWord
                phase == SessionPhase.FREE -> freeWord
                else -> null
            }
            return word?.kana?.let { KanaUnits.split(it) }
                ?: currentCharacter?.let { listOf(it.character) }
                ?: emptyList()
        }

    /** Human-readable progress line shown by CharacterGuidePanel; null outside a session. */
    val sessionProgress: String?
        get() = when (phase) {
            SessionPhase.REVIEWS -> "Review ${reviewsDone + 1}/$reviewsTotal"
            SessionPhase.NEW_CHARACTER, SessionPhase.PRACTICE ->
                "Practice ${repetitionIndex + 1}/$repetitionsTotal"
            SessionPhase.VOCABULARY -> {
                val word = currentWord
                if (word == null) null else "Word ${wordsPracticed + 1}/$wordsTotal · ${word.kana}"
            }
            else -> null
        }

    val total: Int get() = characters.size

    /** True when the word being traced was unlocked by this session — drives the "New word!" badge. */
    val isCurrentWordNew: Boolean
        get() = currentWord?.kana?.let { it in newlyUnlockedKana } == true

    /**
     * Space-separated per-unit romaji for [word] — the pronunciation shown
     * next to its meaning in the guide panel (かう -> "ka u", きょう -> "kyo u").
     * Splitting into reading units means a yōon resolves as one sound (きょ ->
     * "kyo"); a unit with no deck entry falls back to the glyph(s) themselves.
     */
    fun pronunciationOf(word: VocabularyWord): String =
        KanaUnits.split(word.kana).joinToString(" ") { unit ->
            characters.firstOrNull { it.character == unit }?.romaji ?: unit
        }
}

/**
 * Drives the Phase 3 session flow: on launch it generates today's
 * [StudySession] (reviews due, at most one new character, vocabulary) and
 * walks the learner through REVIEWS -> NEW_CHARACTER/PRACTICE -> VOCABULARY
 * -> COMPLETE, persisting SRS/progress/vocabulary state as each step is
 * graded. COMPLETE offers an escape hatch into FREE, the old endless loop
 * over already-learned characters, which does not touch scheduling.
 *
 * All repository writes are fire-and-forget from [viewModelScope] but,
 * unlike the Phase 1 attempt-recording wart, log failures instead of
 * swallowing them silently.
 */
class PracticeViewModel(
    private val characterRepository: CharacterRepository,
    private val practiceRepository: PracticeRepository,
    private val learningRepository: LearningRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository,
    private val handwritingRecognizer: HandwritingRecognizer,
    private val lessonGenerator: LessonGenerator = LessonGenerator(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PracticeUiState(
            isGridEnabled = settingsRepository.isGridEnabled(),
            isGhostEnabled = settingsRepository.isGhostEnabled(),
            isLibraryGridView = settingsRepository.isLibraryGridView(),
            practiceLayout = settingsRepository.getPracticeLayout(),
            activeScript = settingsRepository.getActiveScript(),
            includeDiacritics = settingsRepository.isDiacriticsIncluded(),
            includeYoon = settingsRepository.isYoonIncluded(),
            includeExtended = settingsRepository.isExtendedIncluded(),
            scoringMode = settingsRepository.getScoringMode(),
            brushInkColor = settingsRepository.getBrushInkColor(),
        ),
    )
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    /** All cards indexed by characterId, kept in sync as reviews/practice are graded. */
    private var cardsById: MutableMap<String, ReviewCard> = mutableMapOf()

    /**
     * The [DrawingSession] consumed by the most recent grade. Grade buttons
     * are only disabled once the canvas clears on the NEXT frame, so two
     * near-simultaneous taps (e.g. two fingers on adjacent grade buttons)
     * can both fire against the same ink; the second would otherwise grade
     * the already-advanced step. [CanvasState.clear] replaces the session
     * object, so identity comparison exactly detects that race.
     */
    private var lastGradedSession: DrawingSession? = null

    /**
     * The guide cell captured from the canvas at the moment of the current
     * grade tap, used for the FIT score. Set by the grade entry points
     * ([gradeCurrentCharacter]/[gradeQuizCharacter]) before dispatch and read
     * by [recordAttempt]; null when the layout has no single well-defined cell.
     */
    private var pendingCellRect: CellRect? = null

    init {
        startSession()
        // Long-lived: keeps the Library's per-glyph score history live as
        // attempts are recorded. Independent of session resets.
        viewModelScope.launch {
            practiceRepository.observeScoreSummaries().collect { summaries ->
                _uiState.update { it.copy(scoreStats = summaries.associateBy { s -> s.character }) }
            }
        }
    }

    private fun todayStartMillis(): Long =
        LocalDate.now(clock).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Recovery path for the error state: back to LOADING, then a fresh
     * session load. Without it a failed [startSession] leaves the user on a
     * dead-end error screen whose only exit is killing the app.
     */
    fun retrySession() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        startSession()
    }

    private fun startSession() {
        viewModelScope.launch {
            runCatching {
                val characters = characterRepository.getCharacters()
                val cards = learningRepository.getReviewCards()
                val words = vocabularyRepository.getWords()
                val progress = vocabularyRepository.getProgress()
                val unlockedWordKana = progress.map { it.kana }.toSet()
                val wordLastPracticedAt = progress
                    .mapNotNull { p -> p.lastPracticedAt?.let { p.kana to it } }
                    .toMap()
                val today = todayStartMillis()

                cardsById = cards.associateBy { it.characterId }.toMutableMap()

                // Scope the deck to the active script BEFORE generation: the
                // full merged deck stays in UiState for lookups, but the
                // session (reviews, new character, vocabulary) is built from
                // the active script only. Other-script cards become orphaned
                // and are dropped by LessonGenerator's up-front liveCards
                // filter — no spurious reviews, backlog, gating, or unlocks.
                val script = _uiState.value.activeScript
                val includeDiacritics = _uiState.value.includeDiacritics
                val includeYoon = _uiState.value.includeYoon
                val includeExtended = _uiState.value.includeExtended
                val session = lessonGenerator.generate(
                    allCharacters = ScriptScope.scopedCharacters(
                        characters, script, includeDiacritics, includeYoon, includeExtended,
                    ),
                    cards = cards,
                    allWords = ScriptScope.scopedWords(
                        words, script, includeDiacritics, includeYoon, includeExtended,
                    ),
                    unlockedWordKana = unlockedWordKana,
                    wordLastPracticedAt = wordLastPracticedAt,
                    todayStartMillis = today,
                )

                val newlyUnlocked = session.vocabulary.filter { it.kana !in unlockedWordKana }
                newlyUnlocked.forEach { word ->
                    runCatching { vocabularyRepository.unlock(word.kana, today) }
                        .onFailure { Log.w(TAG, "Failed to persist unlock for ${word.kana}", it) }
                }

                SessionLoad(
                    characters = characters,
                    session = session,
                    newlyUnlockedKana = newlyUnlocked.map { it.kana }.toSet(),
                    allWords = words,
                    unlockedWordKana = unlockedWordKana + newlyUnlocked.map { it.kana },
                )
            }.onSuccess { load ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        characters = load.characters,
                        allWords = load.allWords,
                        unlockedWordKana = load.unlockedWordKana,
                        learnedCharacterIds = learnedIds(),
                        errorMessage = if (load.characters.isEmpty()) {
                            "No characters found."
                        } else {
                            null
                        },
                    )
                }
                if (load.characters.isNotEmpty()) enterSession(load.session, load.newlyUnlockedKana)
            }.onFailure { error ->
                Log.w(TAG, "Failed to start session", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load characters: ${error.message}",
                    )
                }
            }
        }
    }

    /**
     * Enters REVIEWS, or skips forward through whichever steps are empty.
     * [newlyUnlockedKana] distinguishes fresh unlocks from reinforcement
     * picks: only the former appear in the COMPLETE summary's
     * [PracticeUiState.wordsUnlocked] and get the "New word!" badge.
     */
    private fun enterSession(session: StudySession, newlyUnlockedKana: Set<String>) {
        if (session.isEmpty) {
            _uiState.update {
                it.copy(
                    phase = SessionPhase.COMPLETE,
                    allCaughtUp = true,
                    hasLearnedCharacters = anyLearnedCharacters(),
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                reviewQueue = session.reviews,
                reviewsTotal = session.reviews.size,
                reviewsDone = 0,
                newCharacter = session.newCharacter,
                repetitionsTotal = session.practiceRepetitions,
                vocabularyQueue = session.vocabulary,
                wordsTotal = session.vocabulary.size,
                newlyUnlockedKana = newlyUnlockedKana,
                wordsUnlocked = session.vocabulary.map { it.kana }.filter { it in newlyUnlockedKana },
            )
        }
        if (session.reviews.isNotEmpty()) {
            advanceToPhase(SessionPhase.REVIEWS)
        } else {
            afterReviews(session)
        }
    }

    /**
     * Easy/Okay/Hard/Again button. Behavior depends on the active phase;
     * the canvas session is always snapshotted synchronously first (the
     * canvas is cleared as soon as [PracticeUiState.canvasClearKey] moves).
     */
    fun gradeCurrentCharacter(grade: Grade, session: DrawingSession, cellRect: CellRect? = null) {
        if (session === lastGradedSession) return
        lastGradedSession = session
        pendingCellRect = cellRect
        when (_uiState.value.phase) {
            SessionPhase.REVIEWS -> gradeReview(grade, session)
            SessionPhase.NEW_CHARACTER, SessionPhase.PRACTICE -> gradePractice(grade, session)
            SessionPhase.VOCABULARY -> gradeVocabulary(grade, session)
            SessionPhase.FREE -> gradeFree(grade, session)
            SessionPhase.LOADING, SessionPhase.COMPLETE -> Unit
        }
    }

    /** "Next" button — only visible in FREE. Skips ahead without grading. */
    fun advanceToNextCharacter() {
        _uiState.update { state ->
            if (state.phase != SessionPhase.FREE || state.freeCharacters.isEmpty()) {
                state
            } else {
                state.copy(
                    currentIndex = (state.currentIndex + 1) % state.freeCharacters.size,
                    canvasClearKey = state.canvasClearKey + 1,
                )
            }
        }
    }

    /**
     * "Keep practicing" button on the COMPLETE summary. Only characters
     * that are actually learned (docs/PHASE3-DESIGN.md §3: a card with
     * `repetitions >= 1`) enter the free deck; with nothing learned yet
     * this is a no-op (the screen hides the button in that case too).
     */
    fun startFreePractice() {
        val learned = _uiState.value.activeScriptCharacters.filter {
            (cardsById[it.id]?.repetitions ?: 0) >= 1
        }
        if (learned.isEmpty()) return
        _uiState.update {
            it.copy(
                phase = SessionPhase.FREE,
                freeCharacters = learned,
                currentIndex = 0,
                gradedCount = 0,
                freeWord = null,
                canvasClearKey = it.canvasClearKey + 1,
            )
        }
    }

    /**
     * A fresh [PracticeUiState] for a session rebuild: every DURABLE field —
     * the loaded deck, learned/unlocked progress, and ALL persisted settings
     * (grid/ghost/layout/ink color/library view, script + the three opt-ins,
     * AND scoringMode/scoreStats) — is carried over from the current state,
     * session/phase fields reset to their defaults, and [canvasClearKey]
     * bumped. Callers override only what differs. Every rebuild path funnels
     * through here so no path can silently drop a durable field.
     */
    private fun PracticeUiState.rebuilt(
        isLoading: Boolean,
        isLibraryOpen: Boolean = this.isLibraryOpen,
        activeScript: Script = this.activeScript,
        includeDiacritics: Boolean = this.includeDiacritics,
        includeYoon: Boolean = this.includeYoon,
        includeExtended: Boolean = this.includeExtended,
    ): PracticeUiState = PracticeUiState(
        isLoading = isLoading,
        isLibraryOpen = isLibraryOpen,
        characters = characters,
        allWords = allWords,
        unlockedWordKana = unlockedWordKana,
        learnedCharacterIds = learnedCharacterIds,
        isGridEnabled = isGridEnabled,
        isGhostEnabled = isGhostEnabled,
        isLibraryGridView = isLibraryGridView,
        brushInkColor = brushInkColor,
        practiceLayout = practiceLayout,
        scoringMode = scoringMode,
        scoreStats = scoreStats,
        activeScript = activeScript,
        includeDiacritics = includeDiacritics,
        includeYoon = includeYoon,
        includeExtended = includeExtended,
        canvasClearKey = canvasClearKey + 1,
    )

    /**
     * "Start next session" button on the COMPLETE summary. Regenerates a
     * fresh [StudySession] from persisted state — since grading updated the
     * cards as the finished session went along, the mastery gate usually
     * opens and the new session introduces the next character. Restarting
     * the app is no longer the only way to keep learning.
     */
    fun startNextSession() {
        val phase = _uiState.value.phase
        if (phase != SessionPhase.COMPLETE && phase != SessionPhase.FREE) return
        // Every durable field (deck, progress, all settings incl. scoringMode)
        // is preserved by the shared rebuild helper; only isLoading differs.
        _uiState.update { it.rebuilt(isLoading = true) }
        startSession()
    }

    /**
     * Switches the studied writing system (the Library's Hiragana/Katakana
     * selector). Persists the choice, then rebuilds the session scoped to the
     * new script. Unlike [startNextSession] this must NOT be gated on the
     * current phase — the selector lives in the library, which overlays ANY
     * phase, so it force-resets to a fresh LOADING state regardless of where
     * the abandoned session was. [lastGradedSession] is cleared so a stray
     * in-flight grade tap can't land against the discarded session, and the
     * full [characters]/[allWords] deck is preserved across the reset. The
     * library stays open ([PracticeUiState.isLibraryOpen] preserved) so the
     * selector reads as an in-place filter — the rebuilt session waits behind
     * it and is ready when the user closes the overlay.
     */
    fun setActiveScript(script: Script) {
        if (script == _uiState.value.activeScript) return
        settingsRepository.setActiveScript(script)
        lastGradedSession = null
        // Not isLoading: the library overlay stays mounted and re-scopes in
        // place; the rebuilt session waits behind it.
        _uiState.update { it.rebuilt(isLoading = false, activeScript = script) }
        startSession()
    }

    /**
     * Voiced-kana opt-in (the Library's "voiced kana" toggle). When enabled,
     * dakuten/handakuten characters and their words fold into the active
     * script's flow (after the base gojūon, by frequency); when disabled they
     * vanish from sessions, the quiz, unlocks, and the Library lists. Persisted
     * and — like [setActiveScript] — a phase-independent rebuild, since the
     * toggle lives in the library which overlays any phase. Any progress made
     * on voiced kana while enabled is preserved (their cards simply go inert
     * as off-scope orphans, resurfacing when re-enabled).
     */
    fun setIncludeDiacritics(included: Boolean) {
        if (included == _uiState.value.includeDiacritics) return
        settingsRepository.setDiacriticsIncluded(included)
        lastGradedSession = null
        _uiState.update { it.rebuilt(isLoading = false, includeDiacritics = included) }
        startSession()
    }

    /**
     * Yōon opt-in (the Library's "combined kana" toggle). When enabled, yōon
     * characters (きょ, しゅ …) and their words fold into the active script's
     * flow (after the base and voiced kana, by frequency); when disabled they
     * vanish from sessions, the quiz, unlocks, and the Library lists.
     * Independent of the voiced-kana opt-in and, like it, a persisted
     * phase-independent rebuild that keeps the library open. Progress on yōon
     * is preserved when toggled off (cards go inert as off-scope orphans).
     */
    fun setIncludeYoon(included: Boolean) {
        if (included == _uiState.value.includeYoon) return
        settingsRepository.setYoonIncluded(included)
        lastGradedSession = null
        _uiState.update { it.rebuilt(isLoading = false, includeYoon = included) }
        startSession()
    }

    /**
     * Extended-kana opt-in (the Library's "extended kana" toggle). When
     * enabled, the small tsu (っ/ッ), small-vowel foreign combos (ファ, ティ …),
     * and obsolete kana — plus their words — fold into the active script's
     * flow; when disabled they vanish from sessions, the quiz, unlocks, and the
     * Library lists. Independent of the other opt-ins and, like them, a
     * persisted phase-independent rebuild that keeps the library open. Progress
     * is preserved when toggled off (cards go inert as off-scope orphans).
     */
    fun setIncludeExtended(included: Boolean) {
        if (included == _uiState.value.includeExtended) return
        settingsRepository.setExtendedIncluded(included)
        lastGradedSession = null
        _uiState.update { it.rebuilt(isLoading = false, includeExtended = included) }
        startSession()
    }

    /**
     * Grid-free mode toggle: hides/shows the practice-grid helper lines on
     * the tracing canvas (the ghost glyph stays). Persisted, so the choice
     * survives app restarts.
     */
    fun toggleGrid() {
        _uiState.update { state ->
            val enabled = !state.isGridEnabled
            settingsRepository.setGridEnabled(enabled)
            state.copy(isGridEnabled = enabled)
        }
    }

    /**
     * Ghost-free mode toggle: hides/shows the light-grey ghost glyph on the
     * tracing canvas (the grid stays), for writing from memory once tracing
     * feels easy. Persisted, so the choice survives app restarts.
     */
    fun toggleGhost() {
        _uiState.update { state ->
            val enabled = !state.isGhostEnabled
            settingsRepository.setGhostEnabled(enabled)
            state.copy(isGhostEnabled = enabled)
        }
    }

    /**
     * Library list ⇄ chart(grid) view. A view preference only — no effect on
     * study/scope. Persisted so it survives restarts like the canvas toggles.
     */
    fun setLibraryGridView(gridView: Boolean) {
        settingsRepository.setLibraryGridView(gridView)
        _uiState.update { it.copy(isLibraryGridView = gridView) }
    }

    /**
     * Handwriting-quality scoring mode (form / fit / both / off). Persisted.
     * Only changes what the headline score reports and whether feedback shows;
     * both dials are always computed and stored, so switching modes never
     * invalidates the recorded score history. Clears any stale feedback.
     */
    fun setScoringMode(mode: ScoringMode) {
        _uiState.update { state ->
            if (state.scoringMode == mode) return@update state
            settingsRepository.setScoringMode(mode)
            state.copy(scoringMode = mode, lastScore = null)
        }
    }

    /**
     * Brush ink color picker (persisted ARGB Int). Purely visual — it recolors
     * the ink via [PracticeUiState.brushInkColor] → TracingCanvas, and leaves
     * stroke geometry, scoring, and scheduling untouched (no session rebuild).
     */
    fun setBrushInkColor(color: Int) {
        _uiState.update { state ->
            if (state.brushInkColor == color) return@update state
            settingsRepository.setBrushInkColor(color)
            state.copy(brushInkColor = color)
        }
    }

    /**
     * Repetition layout: tiles the canvas with span×span copies of the
     * current character/word so several attempts fit on one sheet.
     * Persisted like the grid toggle; unlike it, changing layout clears
     * the sheet (the ghost cells move, so stale ink would misalign).
     */
    fun setPracticeLayout(layout: PracticeLayout) {
        _uiState.update { state ->
            if (state.practiceLayout == layout) return@update state
            settingsRepository.setPracticeLayout(layout)
            state.copy(
                practiceLayout = layout,
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    // ---- LIBRARY --------------------------------------------------------

    /** Opens the character/word library overlay (any phase; purely visual). */
    fun openLibrary() {
        _uiState.update { it.copy(isLibraryOpen = true, learnedCharacterIds = learnedIds()) }
    }

    /** Back/close from the library without changing the underlying phase. */
    fun closeLibrary() {
        _uiState.update { it.copy(isLibraryOpen = false) }
    }

    /**
     * Library pick: free practice starting at [character], with the WHOLE
     * deck (learned or not — the library is the explicit escape hatch from
     * the SRS gating) available via Next. Abandons any in-progress session
     * steps; a new session can be started from the COMPLETE summary later.
     */
    fun practiceCharacter(character: HiraganaCharacter) {
        _uiState.update { state ->
            // The library only lists the active script (it is a section
            // switch), so the free deck for a tapped character is that same
            // active-script subset — Next cycles within the current script.
            val deck = state.activeScriptCharacters
            if (deck.isEmpty()) return@update state
            state.copy(
                isLibraryOpen = false,
                phase = SessionPhase.FREE,
                freeCharacters = deck,
                currentIndex = deck.indexOfFirst { it.id == character.id }.coerceAtLeast(0),
                gradedCount = 0,
                freeWord = null,
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    /**
     * Library pick: free practice of [word] traced whole — one sheet with
     * a cell per kana, graded once per attempt, repeating until the user
     * leaves. Same FREE semantics as [practiceCharacter]: attempts are
     * recorded with [PracticeSource.FREE], no unlock or scheduling side
     * effects.
     */
    fun practiceWord(word: VocabularyWord) {
        _uiState.update { state ->
            state.copy(
                isLibraryOpen = false,
                phase = SessionPhase.FREE,
                freeCharacters = emptyList(),
                currentIndex = 0,
                gradedCount = 0,
                freeWord = word,
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    // ---- QUIZ ------------------------------------------------------------

    /** "Quiz me!" in the library: opens the quiz setup overlay (scoped to the active script). */
    fun openQuiz() {
        _uiState.update { state ->
            val deckSize = state.activeScriptCharacters.size
            if (deckSize == 0) return@update state
            state.copy(
                quiz = QuizState(
                    requestedCount = 5.coerceAtMost(deckSize),
                    showGrid = state.isGridEnabled,
                ),
            )
        }
    }

    /** Back/close from any quiz stage; the library (and phase) underneath resume untouched. */
    fun closeQuiz() {
        _uiState.update {
            if (it.quiz == null) {
                it
            } else {
                it.copy(
                    quiz = null,
                    // Drop any lingering feedback (incl. a late async recognition
                    // update) so it can't flash on the screen underneath.
                    lastScore = null,
                    lastScoredCharacter = null,
                    canvasClearKey = it.canvasClearKey + 1,
                )
            }
        }
    }

    /**
     * Setup-screen count: preset chips, the +/− stepper, and the free-typed
     * number all land here; clamped to what the deck can actually serve.
     */
    fun setQuizCount(count: Int) {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(
                quiz = quiz.copy(
                    requestedCount = count.coerceIn(1, state.activeScriptCharacters.size.coerceAtLeast(1)),
                ),
            )
        }
    }

    /**
     * Quiz-local crosshair-grid toggle (setup switch, changeable mid-quiz
     * too). Deliberately NOT persisted: the quiz's blank-page preference
     * shouldn't clobber the user's normal practice setting.
     */
    fun toggleQuizGrid() {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(showGrid = !quiz.showGrid))
        }
    }

    /**
     * Builds the quiz deck and starts writing (also "Quiz again" on the
     * summary): learned characters first — they are the ones worth testing
     * — topped up with unlearned picks when the requested count exceeds
     * them; both groups shuffled.
     */
    fun startQuiz() {
        val learned = learnedIds()
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            val deck = state.activeScriptCharacters
            if (deck.isEmpty()) return@update state
            val pool = deck.filter { it.id in learned }.shuffled() +
                deck.filterNot { it.id in learned }.shuffled()
            state.copy(
                quiz = quiz.copy(
                    stage = QuizStage.RUNNING,
                    characters = pool.take(quiz.requestedCount.coerceIn(1, pool.size)),
                    index = 0,
                    revealed = false,
                    submitted = false,
                    results = emptyMap(),
                ),
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    /**
     * "Submit" — step one of the two-step quiz flow. Scores the ink the learner
     * wrote (records a [PracticeSource.QUIZ] attempt — never touches scheduling;
     * the grade is a neutral placeholder since the Library reads form/fit), then
     * enters the review sub-state: the score headline lands in
     * [QuizState.results], the answer/ghost is revealed, and the feedback card
     * shows. Does NOT advance and does NOT clear the canvas — the user compares
     * their ink to the revealed guide strokes, then taps Next. No-op without ink
     * (the UI calls [quizAdvance] to skip an empty canvas) or if already
     * submitted; double-tap guarded like the other grade paths.
     */
    fun quizSubmit(session: DrawingSession, cellRect: CellRect? = null) {
        val quiz = _uiState.value.quiz ?: return
        if (quiz.stage != QuizStage.RUNNING || quiz.submitted) return
        val character = quiz.current ?: return
        if (session.strokes.none { it.points.isNotEmpty() }) return
        if (session === lastGradedSession) return

        lastGradedSession = session
        pendingCellRect = cellRect
        val score = recordAttempt(character, Grade.GOOD, session, PracticeSource.QUIZ)

        _uiState.update { state ->
            val live = state.quiz ?: return@update state
            state.copy(
                quiz = live.copy(
                    submitted = true,
                    revealed = true,
                    results = if (score != null) live.results + (character.id to score.headline) else live.results,
                ),
                // No canvasClearKey bump — keep the ink on screen for review.
            )
        }
    }

    /**
     * "Next"/"Finish"/"Skip" — step two (and the empty-canvas skip). Advances to
     * the next character (or SUMMARY on the last), returns to the writing
     * sub-state, and clears the feedback so it doesn't distract on the next
     * character.
     */
    fun quizAdvance() {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            if (quiz.stage != QuizStage.RUNNING) return@update state
            val finished = quiz.index + 1 >= quiz.characters.size
            state.copy(
                quiz = quiz.copy(
                    stage = if (finished) QuizStage.SUMMARY else QuizStage.RUNNING,
                    index = if (finished) quiz.index else quiz.index + 1,
                    revealed = false,
                    submitted = false,
                ),
                lastScore = null,
                lastScoredCharacter = null,
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    /** "Back" in a quiz: returns to the previous character (blank canvas, writing sub-state). */
    fun quizBack() {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            if (quiz.stage != QuizStage.RUNNING || quiz.index == 0) return@update state
            state.copy(
                quiz = quiz.copy(index = quiz.index - 1, revealed = false, submitted = false),
                lastScore = null,
                lastScoredCharacter = null,
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    // ---- REVIEWS ----------------------------------------------------

    private fun gradeReview(grade: Grade, session: DrawingSession) {
        val state = _uiState.value
        val character = state.reviewQueue.firstOrNull() ?: return
        recordAttempt(character, grade, session, PracticeSource.REVIEW)

        val today = todayStartMillis()
        val currentCard = cardsById[character.id] ?: SrsScheduler.newCard(character.id, today)
        val updatedCard = SrsScheduler.next(currentCard, grade, today)
        cardsById[character.id] = updatedCard
        persistCard(updatedCard)

        val remainingQueue = state.reviewQueue.drop(1)
        val nextQueue = if (grade == Grade.AGAIN) remainingQueue + character else remainingQueue
        val reviewsDone = if (grade == Grade.AGAIN) state.reviewsDone else state.reviewsDone + 1

        _uiState.update {
            it.copy(
                reviewQueue = nextQueue,
                reviewsDone = reviewsDone,
                learnedCharacterIds = learnedIds(),
                canvasClearKey = it.canvasClearKey + 1,
            )
        }

        if (nextQueue.isEmpty()) afterReviews(sessionSnapshot())
    }

    private fun afterReviews(session: StudySession) {
        when {
            session.newCharacter != null -> advanceToPhase(SessionPhase.NEW_CHARACTER)
            session.vocabulary.isNotEmpty() -> enterVocabulary()
            else -> finishSession()
        }
    }

    // ---- NEW_CHARACTER / PRACTICE ------------------------------------

    private fun gradePractice(grade: Grade, session: DrawingSession) {
        val state = _uiState.value
        val character = state.newCharacter ?: return
        recordAttempt(character, grade, session, PracticeSource.NEW)

        if (state.phase == SessionPhase.NEW_CHARACTER) {
            // First rep: introduce (INSERT-OR-IGNORE) THEN increment, in ONE
            // ordered coroutine, so the trace UPDATE can't race ahead of the
            // INSERT and hit zero rows (losing the first trace).
            markIntroducedThenIncrement(character.id)
        } else {
            // Later reps: the row already exists, so a bare increment is safe.
            incrementTraces(character.id)
        }

        val isFinalRep = state.repetitionIndex + 1 >= state.repetitionsTotal
        if (isFinalRep) {
            val today = todayStartMillis()
            val seeded = SrsScheduler.next(SrsScheduler.newCard(character.id, today), grade, today)
            cardsById[character.id] = seeded
            persistCard(seeded)

            _uiState.update {
                it.copy(
                    // AGAIN seeds a repetitions-0 card: introduced, but not
                    // "learned" — the summary must not claim otherwise.
                    newCharacterLearned = if (seeded.repetitions >= 1) character.character else null,
                    learnedCharacterIds = learnedIds(),
                    canvasClearKey = it.canvasClearKey + 1,
                )
            }
            if (_uiState.value.vocabularyQueue.isNotEmpty()) {
                enterVocabulary()
            } else {
                finishSession()
            }
        } else {
            _uiState.update {
                it.copy(
                    phase = SessionPhase.PRACTICE,
                    repetitionIndex = it.repetitionIndex + 1,
                    canvasClearKey = it.canvasClearKey + 1,
                )
            }
        }
    }

    // ---- VOCABULARY ---------------------------------------------------

    private fun enterVocabulary() {
        val queue = _uiState.value.vocabularyQueue
        val firstWord = queue.firstOrNull()
        if (firstWord == null) {
            finishSession()
            return
        }
        _uiState.update {
            it.copy(
                phase = SessionPhase.VOCABULARY,
                currentWord = firstWord,
                canvasClearKey = it.canvasClearKey + 1,
            )
        }
    }

    /**
     * One grade covers the WHOLE word — it is traced on a single sheet
     * with one cell per kana, so there is no per-kana step to grade.
     */
    private fun gradeVocabulary(grade: Grade, session: DrawingSession) {
        val state = _uiState.value
        val word = state.currentWord ?: return
        recordWordAttempt(word, grade, session, PracticeSource.VOCAB)

        val now = clock.millis()
        viewModelScope.launch {
            runCatching { vocabularyRepository.recordPractice(word.kana, now) }
                .onFailure { Log.w(TAG, "Failed to record practice for ${word.kana}", it) }
        }

        val remainingWords = state.vocabularyQueue.drop(1)
        val nextWord = remainingWords.firstOrNull()
        _uiState.update {
            it.copy(
                vocabularyQueue = remainingWords,
                currentWord = nextWord,
                wordsPracticed = it.wordsPracticed + 1,
                canvasClearKey = it.canvasClearKey + 1,
            )
        }

        if (nextWord == null) finishSession()
    }

    // ---- COMPLETE / FREE ---------------------------------------------

    private fun finishSession() {
        _uiState.update {
            it.copy(
                phase = SessionPhase.COMPLETE,
                allCaughtUp = false,
                hasLearnedCharacters = anyLearnedCharacters(),
            )
        }
    }

    private fun anyLearnedCharacters(): Boolean = cardsById.values.any { it.repetitions >= 1 }

    /** Ids of characters with a recalled-at-least-once card — the library's "learned" badges. */
    private fun learnedIds(): Set<String> =
        cardsById.filterValues { it.repetitions >= 1 }.keys.toSet()

    private fun gradeFree(grade: Grade, session: DrawingSession) {
        val state = _uiState.value
        val word = state.freeWord
        if (word != null) {
            // Library word practice: one grade per whole-word sheet, then a
            // clean sheet for another go. No unlock/staleness side effects —
            // the library must never touch scheduling state.
            recordWordAttempt(word, grade, session, PracticeSource.FREE)
            _uiState.update {
                it.copy(
                    gradedCount = it.gradedCount + 1,
                    canvasClearKey = it.canvasClearKey + 1,
                )
            }
            return
        }
        val character = state.freeCharacters.getOrNull(state.currentIndex) ?: return
        recordAttempt(character, grade, session, PracticeSource.FREE)
        _uiState.update { it.copy(gradedCount = it.gradedCount + 1) }
        advanceToNextCharacter()
    }

    // ---- shared helpers -------------------------------------------------

    private fun advanceToPhase(phase: SessionPhase) {
        _uiState.update {
            it.copy(
                phase = phase,
                repetitionIndex = 0,
                canvasClearKey = it.canvasClearKey + 1,
            )
        }
    }

    /** Rebuilds a [StudySession]-shaped view of the current UiState for post-step routing. */
    private fun sessionSnapshot(): StudySession {
        val state = _uiState.value
        return StudySession(
            reviews = state.reviewQueue,
            newCharacter = state.newCharacter,
            practiceRepetitions = state.repetitionsTotal,
            vocabulary = state.vocabularyQueue,
        )
    }

    /**
     * First-trace path: mark the character introduced (INSERT-OR-IGNORE into
     * character_progress) and then bump its trace count, **sequentially in one
     * coroutine**. Kept in a single launch on purpose — running the two as
     * separate fire-and-forget coroutines let the trace UPDATE occasionally
     * land before the INSERT and silently drop the first trace.
     */
    private fun markIntroducedThenIncrement(characterId: String) {
        val now = clock.millis()
        viewModelScope.launch {
            runCatching {
                learningRepository.markIntroduced(characterId, now)
                learningRepository.incrementTraces(characterId)
            }.onFailure { Log.w(TAG, "Failed to record introduction/trace for $characterId", it) }
        }
    }

    private fun incrementTraces(characterId: String) {
        viewModelScope.launch {
            runCatching { learningRepository.incrementTraces(characterId) }
                .onFailure { Log.w(TAG, "Failed to increment traces for $characterId", it) }
        }
    }

    private fun persistCard(card: ReviewCard) {
        viewModelScope.launch {
            runCatching { learningRepository.upsertReviewCard(card) }
                .onFailure { Log.w(TAG, "Failed to persist review card for ${card.characterId}", it) }
        }
    }

    /**
     * Persists one whole-word attempt as a single row: `character` holds
     * the full kana (e.g. "あお") and `romaji` the joined per-character
     * romaji ("ao"), since strokes can't be attributed to individual kana
     * on a shared sheet.
     */
    private fun recordWordAttempt(
        word: VocabularyWord,
        grade: Grade,
        session: DrawingSession,
        source: PracticeSource,
    ) {
        val characters = _uiState.value.characters
        val romaji = KanaUnits.split(word.kana).joinToString("") { unit ->
            characters.firstOrNull { it.character == unit }?.romaji ?: unit
        }
        // Words aren't per-kana scored; clear any lingering single-character
        // feedback so a word step doesn't show a stale glyph score.
        _uiState.update { it.copy(lastScore = null, lastScoredCharacter = null) }
        val attempt = PracticeAttempt(
            character = word.kana,
            romaji = romaji,
            grade = grade,
            strokeCount = session.strokes.size,
            durationMs = session.durationMillis(),
            createdAt = clock.millis(),
            source = source,
        )
        viewModelScope.launch {
            runCatching { practiceRepository.recordAttempt(attempt) }
                .onFailure { Log.w(TAG, "Failed to record word attempt for ${word.kana}", it) }
        }
    }

    /**
     * Persists one graded single-character attempt, and — unless scoring is
     * off — computes its handwriting-quality score. Geometry is computed
     * synchronously (instant, so [PracticeUiState.lastScore] updates
     * immediately for advisory feedback); recognition is awaited in a
     * fire-and-forget coroutine, after which the score is finalised and the
     * row is inserted with all three sub-scores. Everything is snapshotted
     * synchronously from the caller before the canvas clears; the Room insert
     * never blocks the session (failures logged).
     *
     * Scoring never touches scheduling (advisory only) — the caller's SRS
     * logic runs independently on the same grade.
     */
    private fun recordAttempt(
        character: HiraganaCharacter,
        grade: Grade,
        session: DrawingSession,
        source: PracticeSource,
    ): CharacterScore? {
        val createdAt = clock.millis()
        val strokeCount = session.strokes.size
        val durationMs = session.durationMillis()
        val cell = pendingCellRect

        val mode = _uiState.value.scoringMode
        val reference = character.strokeAnimations
            .sortedBy { it.order }
            .map { def -> def.points.map { Pt(it.x, it.y) } }
            .filter { it.isNotEmpty() }
        val userStrokes = session.strokes
            .map { stroke -> stroke.points.map { Pt(it.x, it.y) } }
            .filter { it.isNotEmpty() }
        val scoreEnabled = mode != ScoringMode.OFF &&
            reference.isNotEmpty() &&
            userStrokes.isNotEmpty()

        fun persist(attempt: PracticeAttempt) {
            viewModelScope.launch {
                runCatching { practiceRepository.recordAttempt(attempt) }
                    .onFailure { Log.w(TAG, "Failed to record attempt for ${character.id}", it) }
            }
        }

        val base = PracticeAttempt(
            character = character.character,
            romaji = character.romaji,
            grade = grade,
            strokeCount = strokeCount,
            durationMs = durationMs,
            createdAt = createdAt,
            source = source,
        )

        if (!scoreEnabled) {
            _uiState.update { it.copy(lastScore = null, lastScoredCharacter = null) }
            persist(base)
            return null
        }

        // Instant geometry feedback, then persist IMMEDIATELY with the
        // geometry dials — persistence must never be gated on recognition,
        // which may be slow or (offline) never resolve. Recognition is a
        // best-effort refinement of the on-screen headline only.
        val geometry = GeometryScorer.score(userStrokes, reference, cell)
        val score = CharacterScore.combine(geometry, null, mode)
        _uiState.update {
            it.copy(lastScore = score, lastScoredCharacter = character.character)
        }
        persist(
            base.copy(
                formScore = geometry.form.toFloat(),
                fitScore = geometry.fit?.toFloat(),
            ),
        )

        val recognizerStrokes = session.strokes
            .map { stroke -> stroke.points.map { RecognizerStrokePoint(it.x, it.y, it.timestamp) } }
            .filter { it.isNotEmpty() }
        val target = character.character
        viewModelScope.launch {
            val recognition = runCatching { handwritingRecognizer.recognize(recognizerStrokes, target) }
                .getOrElse {
                    Log.w(TAG, "Recognition failed for $target", it)
                    RecognitionResult.UNAVAILABLE
                }
            // Only refine the headline if the glyph we scored is STILL the one
            // on screen — a stale recognition from a previous character must not
            // clobber the current score (nor flash onto the phase under a just-
            // closed quiz, which clears lastScoredCharacter).
            _uiState.update { s ->
                if (s.lastScoredCharacter == target) {
                    s.copy(lastScore = CharacterScore.combine(geometry, recognition, mode))
                } else {
                    s
                }
            }
        }
        // The synchronous geometry headline (recognition refines the live
        // card asynchronously but the caller/summary uses this value).
        return score
    }

    /** Everything [startSession] loads, bundled for the success path. */
    private data class SessionLoad(
        val characters: List<HiraganaCharacter>,
        val session: StudySession,
        val newlyUnlockedKana: Set<String>,
        val allWords: List<VocabularyWord>,
        val unlockedWordKana: Set<String>,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HiraganaApplication
                PracticeViewModel(
                    characterRepository = application.container.characterRepository,
                    practiceRepository = application.container.practiceRepository,
                    learningRepository = application.container.learningRepository,
                    vocabularyRepository = application.container.vocabularyRepository,
                    settingsRepository = application.container.settingsRepository,
                    handwritingRecognizer = application.container.handwritingRecognizer,
                )
            }
        }
    }
}
