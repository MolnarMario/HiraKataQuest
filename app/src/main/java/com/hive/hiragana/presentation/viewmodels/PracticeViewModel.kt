package com.hive.hiragana.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hive.hiragana.HiraganaApplication
import com.hive.hiragana.domain.audio.PronunciationPlayer
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.DefaultPracticeSets
import com.hive.hiragana.domain.model.Grade
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.PracticeAttempt
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.KanaUnits
import com.hive.hiragana.domain.model.PracticeSet
import com.hive.hiragana.domain.model.PracticeSetType
import com.hive.hiragana.domain.model.PracticeSource
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.model.ScriptScope
import com.hive.hiragana.domain.model.VocabularyWord
import com.hive.hiragana.domain.repository.CharacterRepository
import com.hive.hiragana.domain.repository.HandwritingRecognizer
import com.hive.hiragana.domain.repository.PracticeRepository
import com.hive.hiragana.domain.repository.PracticeSetRepository
import com.hive.hiragana.domain.repository.RecognizerStrokePoint
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.ReadingProgress
import com.hive.hiragana.domain.repository.ReadingProgressRepository
import com.hive.hiragana.domain.repository.SettingsRepository
import com.hive.hiragana.domain.repository.VocabularyRepository
import com.hive.hiragana.domain.scoring.CellRect
import com.hive.hiragana.domain.scoring.CharacterScore
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
import com.hive.hiragana.domain.scoring.GeometryScorer
import com.hive.hiragana.domain.scoring.Pt
import com.hive.hiragana.domain.scoring.RecognitionResult
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.domain.scoring.StrokeAttribution
import com.hive.hiragana.domain.scoring.WordUnitScore
import com.hive.hiragana.drawing.stroke.DrawingSession
import com.hive.hiragana.drawing.stroke.durationMillis
import java.time.Clock
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "HiraganaSession"

/** Library section (tab) index persisted via [SettingsRepository]. */
private const val SECTION_CHARACTERS = 0
private const val SECTION_WORDS = 1

/**
 * Which surface the practice screen is showing. The app has only free
 * practice now (the guided SRS study session was removed), so this is just
 * [LOADING] while the deck loads, then [FREE] for all practice.
 */
enum class SessionPhase {
    LOADING,
    FREE,
}

/** Stage of the library-launched "Quiz me!" overlay. */
enum class QuizStage { SETUP, RUNNING, SUMMARY }

/** Which track a quiz belongs to — Writing (handwriting) or Reading (recall). */
enum class QuizKind { WRITING, READING }

/** How the learner answers a READING quiz item. */
enum class ReadingAnswerStyle { CHOICE, TYPE }

/** One reading flashcard: the printed glyph/word on the front, reading + meaning on the back. */
data class ReadingCard(val glyph: String, val romaji: String, val meaning: String?)

/**
 * Reading-track flashcard study state: a flip through the scoped deck/words.
 * Self-graded (Got it / Missed) — pure study, so it does NOT feed the reading
 * quiz percentages. Transient (like [QuizState]); not carried across rebuilds.
 */
data class ReadingFlashcardState(
    val cards: List<ReadingCard>,
    val index: Int = 0,
    val revealed: Boolean = false,
) {
    val current: ReadingCard? get() = cards.getOrNull(index)
}

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
/**
 * One item a quiz walks through: either a single character or a whole word.
 * The RUNNING screen only reads the interface ([prompt] shown from memory,
 * [answer] revealed on Submit, [glyphs] laid out on the canvas), so it renders
 * both uniformly; the ViewModel branches on the concrete type only to pick the
 * scoring path (single-character vs per-kana word scoring). [key] identifies
 * the item in [QuizState.results].
 */
sealed interface QuizItem {
    val key: String
    val prompt: String
    val answer: String
    val glyphs: List<String>
    val subtitle: String?
}

data class CharQuizItem(val character: HiraganaCharacter) : QuizItem {
    override val key: String get() = character.id
    override val prompt: String get() = character.romaji
    override val answer: String get() = character.character
    override val glyphs: List<String> get() = listOf(character.character)
    override val subtitle: String? get() = null
}

data class WordQuizItem(
    val word: VocabularyWord,
    override val prompt: String,
) : QuizItem {
    override val key: String get() = word.kana
    override val answer: String get() = word.kana
    override val glyphs: List<String> get() = KanaUnits.split(word.kana)
    override val subtitle: String get() = word.meaning
}

data class QuizState(
    val stage: QuizStage = QuizStage.SETUP,
    val requestedCount: Int = 5,
    val showGrid: Boolean = true,
    val items: List<QuizItem> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    // Two-step per item: false = writing (ghost off, no feedback, button
    // "Submit"); true = the item has been submitted and is being reviewed
    // (ghost revealed, feedback shown, button "Next"). Reset on advance/back.
    val submitted: Boolean = false,
    val results: Map<String, Int> = emptyMap(),
    // Item keys the learner actually wrote+submitted, regardless of whether a
    // score was produced — distinguishes a written-but-unscored item (e.g.
    // ScoringMode.OFF, so no headline lands in [results]) from a genuine skip.
    val submittedKeys: Set<String> = emptySet(),
    // Which source the quiz draws from: null = the active-script character
    // scope (the original behaviour); otherwise a saved practice set's id.
    val selectedSetId: Long? = null,
    // Reading-quiz configuration (ignored by writing quizzes). READING shows the
    // printed glyph and asks for the reading; answered by multiple choice or by
    // typing romaji; words optionally also test the English meaning.
    val kind: QuizKind = QuizKind.WRITING,
    val readingAnswerStyle: ReadingAnswerStyle = ReadingAnswerStyle.CHOICE,
    val readingTestMeaning: Boolean = false,
) {
    val current: QuizItem? get() = items.getOrNull(index)

    /** True on the quiz's last item — Next becomes Finish. */
    val isLast: Boolean get() = index >= items.size - 1
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
    // FREE practice deck: any characters (from the library it can hold the
    // whole scoped deck), and optionally a word context.
    val freeCharacters: List<HiraganaCharacter> = emptyList(),
    val currentIndex: Int = 0,
    val gradedCount: Int = 0,
    val freeWord: VocabularyWord? = null,
    // When free practice is cycling a WORD set, the queue of words; [freeWord]
    // is kept pointing at freeWords[currentIndex] and Next steps through them.
    // Empty for single-word or character free practice.
    val freeWords: List<VocabularyWord> = emptyList(),
    // FREE two-step (mirrors the quiz): false = writing (button "Submit"),
    // true = the attempt has been submitted/scored and the feedback card
    // shows (buttons "Again"/"Next"). Reset on advance, retry, and every FREE
    // entry point.
    val freeSubmitted: Boolean = false,
    // LIBRARY (browse everything; overlays whatever phase is active)
    val isLibraryOpen: Boolean = false,
    val allWords: List<VocabularyWord> = emptyList(),
    // Saved practice sets (user-curated character/word lists), newest first.
    // Observed from the repository; durable across session rebuilds.
    val practiceSets: List<PracticeSet> = emptyList(),
    // QUIZ (write-from-memory overlay, launched from the library; renders
    // above the library so closing it drops back into browsing)
    val quiz: QuizState? = null,
    // READING flashcard study overlay (Reading mode: tap a Library item to flip
    // through the scoped deck/words). Transient, like [quiz].
    val readingFlashcard: ReadingFlashcardState? = null,
    // One-shot: set true when a SET-launched quiz closes, so the library
    // reopens the practice-sets manager (not the browse page) underneath.
    // Consumed by LibraryScreen once acted on. Transient (not in rebuilt()).
    val pendingOpenSetsManager: Boolean = false,
    // SETTINGS (persisted via SettingsRepository)
    val isGridEnabled: Boolean = true,
    val isGhostEnabled: Boolean = true,
    val isLibraryGridView: Boolean = false,
    // Which Library tab the learner last had open (0 = Characters, 1 = Words),
    // persisted. Seeds the Library's initial tab AND decides whether a cold
    // start resumes free practice on a character or a word.
    val librarySection: Int = SECTION_CHARACTERS,
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
    // Which form the traced ghost shows (persisted; default PRINTED). Purely a
    // rendering choice — the stroke-order demo and the scorer always use the
    // KanjiVG strokes, so this switches only the ghost's rendering.
    val characterForm: CharacterForm = CharacterForm.PRINTED,
    // The chosen top-level track (Reading / Writing). null = not chosen yet →
    // the opening screen is shown. Durable across scope rebuilds; reset to null
    // only when the learner goes back to the opening "home".
    val appMode: AppMode? = null,
    // Latest computed quality score, shown as advisory feedback after a graded
    // single character. Null when scoring is off/unavailable or on word steps.
    // Purely transient UI state — never persisted.
    val lastScore: CharacterScore? = null,
    // The glyph [lastScore] was computed for. The canvas advances on grade, so
    // the card can outlive its character — labelling it with the scored glyph
    // stops the score reading as if it judged the character now on screen.
    val lastScoredCharacter: String? = null,
    // Per-kana scores for the most recent WHOLE-WORD attempt (1×1 only), in
    // reading order — shown as chips above the canvas with [lastWordAverage]
    // below. Null outside a scored word step. Transient, like [lastScore];
    // reset wherever [lastScore] is.
    val lastWordScores: List<WordUnitScore>? = null,
    // Average headline across [lastWordScores] (the number shown below the
    // canvas). Null when there are no per-kana word scores.
    val lastWordAverage: Int? = null,
    // Per-glyph quality history (avg/best), keyed by glyph string, for the
    // Library rows in WRITING mode. Sourced from the DB, carried across resets.
    val scoreStats: Map<String, CharacterScoreSummary> = emptyMap(),
    // Per-glyph/word reading-quiz accuracy (MCQ% + typed%), keyed by glyph/kana
    // string, for the Library tint + rows in READING mode. From the DB, carried
    // across resets.
    val readingStats: Map<String, ReadingProgress> = emptyMap(),
    // The device's Japanese TTS voice status — gates the pronunciation button
    // (play / offer download / disabled). Set by the VM's status collector.
    val ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
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

    /** The scoped word list for the active script (same scoping as the deck). */
    val activeScriptWords: List<VocabularyWord>
        get() = ScriptScope.scopedWords(
            allWords, activeScript, includeDiacritics, includeYoon, includeExtended,
        )

    /**
     * "character 12 of 73" / "word 20 of 84" for the item currently in FREE
     * practice — the item's 1-based position in the active script's ordered
     * deck (characters) or word list, and that list's size. Null outside FREE
     * or when the item isn't in the current scope (so nothing misleading shows).
     */
    val practicePositionLabel: String?
        get() {
            if (phase != SessionPhase.FREE) return null
            freeWord?.let { word ->
                val words = activeScriptWords
                val i = words.indexOfFirst { it.kana == word.kana }
                return if (i >= 0) "word ${i + 1} of ${words.size}" else null
            }
            val glyph = currentCharacter?.character ?: return null
            val deck = activeScriptCharacters
            val i = deck.indexOfFirst { it.character == glyph }
            return if (i >= 0) "character ${i + 1} of ${deck.size}" else null
        }

    /** The saved set with [setId], or null for the default (active-script) source. */
    fun practiceSet(setId: Long?): PracticeSet? =
        setId?.let { id -> practiceSets.firstOrNull { it.id == id } }

    /**
     * How many items the quiz would draw from [setId] — a set's member count,
     * or the active-script deck size for the default source. Bounds the count
     * presets/stepper on the quiz setup card.
     */
    fun quizSourceSize(setId: Long?): Int =
        practiceSet(setId)?.members?.size ?: activeScriptCharacters.size

    val currentCharacter: HiraganaCharacter?
        get() = when (phase) {
            // A word is traced whole — no single current character.
            SessionPhase.FREE -> if (freeWord != null) null else freeCharacters.getOrNull(currentIndex)
            SessionPhase.LOADING -> null
        }

    /**
     * Glyphs the tracing canvas renders, one practice cell each: the free
     * word's kana (split into reading units so a yōon like きょ shares one cell
     * instead of orphaning the small ょ), otherwise the single current character.
     */
    val canvasGlyphs: List<String>
        get() {
            val word = if (phase == SessionPhase.FREE) freeWord else null
            return word?.kana?.let { KanaUnits.split(it) }
                ?: currentCharacter?.let { listOf(it.character) }
                ?: emptyList()
        }

    val total: Int get() = characters.size

    /**
     * O(1) glyph -> deck character lookup, built once per state instance. Backs
     * [pronunciationOf]/[wordUnitCharacters] so they don't linear-scan the full
     * ~250-character deck per reading unit — that scan is hot when the Library's
     * Words grid composes cards while fast-scrolling. Not a constructor property,
     * so it stays out of the data class's equals/copy.
     */
    private val characterByGlyph: Map<String, HiraganaCharacter> by lazy(LazyThreadSafetyMode.NONE) {
        characters.associateBy { it.character }
    }

    /**
     * Space-separated per-unit romaji for [word] — the pronunciation shown
     * next to its meaning in the guide panel (かう -> "ka u", きょう -> "kyo u").
     * Splitting into reading units means a yōon resolves as one sound (きょ ->
     * "kyo"); a unit with no deck entry falls back to the glyph(s) themselves.
     */
    fun pronunciationOf(word: VocabularyWord): String =
        KanaUnits.split(word.kana).joinToString(" ") { unit ->
            characterByGlyph[unit]?.romaji ?: unit
        }

    /**
     * The deck [HiraganaCharacter] for each reading unit of [word], in order —
     * feeds the guide panel's per-kana stroke-order previews. Units with no
     * deck entry (unexpected) are dropped; composites (きょ/ファ) resolve to
     * their single composite character with its composed strokes.
     */
    fun wordUnitCharacters(word: VocabularyWord): List<HiraganaCharacter> =
        KanaUnits.split(word.kana).mapNotNull { unit ->
            characterByGlyph[unit]
        }

    /**
     * KanjiVG stroke paths (viewBox space) for each cell of [canvasGlyphs] — the
     * geometry the HANDWRITTEN-form ghost traces. An empty inner list marks a
     * glyph not in the deck; the canvas then falls back to the font ghost there.
     */
    val canvasGhostStrokes: List<List<String>>
        get() = ghostStrokesFor(canvasGlyphs)

    /** Per-glyph KanjiVG stroke paths (viewBox space) for [glyphs]; empty if unknown. */
    fun ghostStrokesFor(glyphs: List<String>): List<List<String>> =
        glyphs.map { characterByGlyph[it]?.strokes ?: emptyList() }
}

/**
 * Drives free practice: on launch it loads the deck and drops into [FREE]
 * practice on a random item from the last-used Library section (scoped by the
 * Study-scope settings). There is no guided study session — whether an attempt
 * is scored is governed solely by [PracticeUiState.scoringMode]. The library's
 * "Quiz me!" overlay and user/default practice sets are the structured modes.
 *
 * All repository writes are fire-and-forget from [viewModelScope] but,
 * unlike the Phase 1 attempt-recording wart, log failures instead of
 * swallowing them silently.
 */
class PracticeViewModel(
    private val characterRepository: CharacterRepository,
    private val practiceRepository: PracticeRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository,
    private val practiceSetRepository: PracticeSetRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val handwritingRecognizer: HandwritingRecognizer,
    // Speaks readings aloud via the device's built-in Japanese TTS.
    private val ttsPlayer: PronunciationPlayer,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PracticeUiState(
            isGridEnabled = settingsRepository.isGridEnabled(),
            isGhostEnabled = settingsRepository.isGhostEnabled(),
            isLibraryGridView = settingsRepository.isLibraryGridView(),
            librarySection = settingsRepository.getLibrarySection(),
            practiceLayout = settingsRepository.getPracticeLayout(),
            activeScript = settingsRepository.getActiveScript(),
            includeDiacritics = settingsRepository.isDiacriticsIncluded(),
            includeYoon = settingsRepository.isYoonIncluded(),
            includeExtended = settingsRepository.isExtendedIncluded(),
            scoringMode = settingsRepository.getScoringMode(),
            brushInkColor = settingsRepository.getBrushInkColor(),
            characterForm = settingsRepository.getCharacterForm(),
        ),
    )
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

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
     * submit tap, used for the FIT score. Set by the submit entry points
     * ([freeSubmit]/[quizSubmit]) before dispatch and read by [recordAttempt];
     * null when the layout has no single well-defined cell.
     */
    private var pendingCellRect: CellRect? = null

    /**
     * The per-kana guide cells captured from the canvas at the moment of the
     * current submit tap, used to score a whole word cell-by-cell. Set by the
     * submit entry points alongside [pendingCellRect] and read by
     * [recordWordAttempt]; empty when the layout has no per-kana cells (multi-
     * copy 2×2/3×3 sheets), which is exactly when a word must not be scored.
     */
    private var pendingCells: List<CellRect> = emptyList()

    init {
        startPractice()
        // Long-lived: keeps the Library's per-glyph score history live as
        // attempts are recorded. Independent of session resets.
        viewModelScope.launch {
            practiceRepository.observeScoreSummaries().collect { summaries ->
                _uiState.update { it.copy(scoreStats = summaries.associateBy { s -> s.character }) }
            }
        }
        // Long-lived: keeps saved practice sets live for the Study Scope sheet,
        // the sets manager, and the Quiz-me source picker. Independent of
        // session resets (carried through rebuilt()).
        viewModelScope.launch {
            practiceSetRepository.observeSets().collect { sets ->
                _uiState.update { it.copy(practiceSets = sets) }
            }
        }
        // Long-lived: keeps per-glyph reading-quiz accuracy live for the
        // Library's Reading-mode tint + rows. Independent of session resets.
        viewModelScope.launch {
            readingProgressRepository.observe().collect { stats ->
                _uiState.update { it.copy(readingStats = stats.associateBy { s -> s.itemKey }) }
            }
        }
        // Long-lived: mirrors the TTS engine's async status into UI state so the
        // pronunciation button can play, offer the voice download, or grey out.
        viewModelScope.launch {
            ttsPlayer.status.collect { status ->
                _uiState.update { it.copy(ttsStatus = status) }
            }
        }
    }

    /** Speak [glyph] via the device's built-in Japanese TTS. */
    fun speakTts(glyph: String) = ttsPlayer.play(glyph)

    /**
     * Re-check the Japanese voice status — called on ON_RESUME so a voice the
     * user just installed flips the button from "download" to "play".
     */
    fun refreshTtsAvailability() = ttsPlayer.refresh()

    // NOTE: the TTS engine is deliberately NOT released here. It's an
    // Application-scoped singleton in AppContainer (like handwritingRecognizer),
    // shared across every ViewModel instance. Releasing it in onCleared() would
    // shut down the shared engine when the Activity finishes (e.g. Back out of
    // the app) even though the process lives on — so the next launch would reuse
    // a dead engine and all audio would silently stop. The OS reclaims the
    // engine when the process dies.

    /**
     * Recovery path for the error state: back to LOADING, then a fresh
     * load. Without it a failed [startPractice] leaves the user on a
     * dead-end error screen whose only exit is killing the app.
     */
    fun retrySession() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        startPractice()
    }

    /**
     * App-launch entry point: loads the deck + word/progress data, then drops
     * straight into FREE practice on a random item from the learner's last-used
     * Library section ([PracticeUiState.librarySection]), scoped by the active
     * script and the Study-scope opt-ins. Replaces the old session-first launch:
     * there is no separate graded SRS session anymore — whether an attempt is
     * scored is governed solely by [PracticeUiState.scoringMode] (Score my
     * writing). The SRS scheduler code stays in place but is no longer entered
     * from launch.
     */
    private fun startPractice() {
        viewModelScope.launch {
            runCatching {
                val characters = characterRepository.getCharacters()
                val words = vocabularyRepository.getWords()
                DeckLoad(characters = characters, allWords = words)
            }.onSuccess { load ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        characters = load.characters,
                        allWords = load.allWords,
                        errorMessage = if (load.characters.isEmpty()) {
                            "No characters found."
                        } else {
                            null
                        },
                    )
                }
                if (load.characters.isNotEmpty()) {
                    seedDefaultSetsIfNeeded(load.characters, load.allWords)
                    // Don't auto-enter a track on launch — the opening screen
                    // (appMode == null) lets the learner pick Reading or Writing.
                    // Only Writing needs its free-practice item set up eagerly;
                    // Reading renders the Library hub via its own screen branch.
                    if (_uiState.value.appMode == AppMode.WRITING) {
                        enterFreePracticeFromLastSection()
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to start practice", error)
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
     * Seeds the built-in [DefaultPracticeSets] into the library the first time
     * ever (guarded by the [SettingsRepository] latch). Runs once per install —
     * fresh installs and existing installs upgrading from before defaults
     * existed both seed on the next launch; a default the user later deletes
     * does not reappear. Fire-and-forget per set; the observe stream picks them
     * up into [PracticeUiState.practiceSets].
     */
    private suspend fun seedDefaultSetsIfNeeded(
        deck: List<HiraganaCharacter>,
        words: List<VocabularyWord>,
    ) {
        if (settingsRepository.hasSeededDefaultSets()) return
        runCatching {
            DefaultPracticeSets.build(deck, words).forEach { spec ->
                practiceSetRepository.createSet(spec.name, spec.type, spec.script, spec.members)
            }
        }.onFailure { Log.w(TAG, "Failed to seed default practice sets", it) }
        settingsRepository.setDefaultSetsSeeded()
    }

    /**
     * Enters FREE practice on a random item drawn from the last-used Library
     * section: a random word (cycling the whole scoped word list) when the
     * learner was last on the Words tab, otherwise a random character (cycling
     * the scoped character deck). Reads the current in-memory deck/words and the
     * active scope from state, so callers that only changed scope (the
     * Study-scope setters) can re-enter without reloading. Falls back from Words
     * to Characters when no words are in scope.
     */
    private fun enterFreePracticeFromLastSection() {
        val state = _uiState.value
        if (state.librarySection == SECTION_WORDS) {
            val words = ScriptScope.scopedWords(
                state.allWords,
                state.activeScript,
                state.includeDiacritics,
                state.includeYoon,
                state.includeExtended,
            )
            if (words.isNotEmpty()) {
                val start = Random.nextInt(words.size)
                _uiState.update {
                    it.copy(
                        phase = SessionPhase.FREE,
                        freeCharacters = emptyList(),
                        freeWords = words,
                        currentIndex = start,
                        freeWord = words[start],
                        gradedCount = 0,
                        freeSubmitted = false,
                        lastScore = null,
                        lastScoredCharacter = null,
                        lastWordScores = null,
                        lastWordAverage = null,
                        canvasClearKey = it.canvasClearKey + 1,
                    )
                }
                return
            }
            // No words in scope — fall through to a character so launch never
            // lands on a blank sheet.
        }
        val deck = state.activeScriptCharacters
        if (deck.isEmpty()) return
        val start = Random.nextInt(deck.size)
        _uiState.update {
            it.copy(
                phase = SessionPhase.FREE,
                freeCharacters = deck,
                freeWords = emptyList(),
                currentIndex = start,
                freeWord = null,
                gradedCount = 0,
                freeSubmitted = false,
                lastScore = null,
                lastScoredCharacter = null,
                lastWordScores = null,
                lastWordAverage = null,
                canvasClearKey = it.canvasClearKey + 1,
            )
        }
    }

    /**
     * "Submit" — step one of the two-step FREE-practice flow (mirrors the
     * quiz). Scores the ink and records a [PracticeSource.FREE] attempt with a
     * neutral placeholder grade (FREE never touches scheduling/unlocks), shows
     * the feedback card, and KEEPS the ink on screen (no [canvasClearKey]
     * bump). No-op outside FREE, without ink, or if already submitted;
     * double-tap guarded via [lastGradedSession] like the other grade paths.
     */
    fun freeSubmit(
        session: DrawingSession,
        cellRect: CellRect? = null,
        cells: List<CellRect> = emptyList(),
    ) {
        val state = _uiState.value
        if (state.phase != SessionPhase.FREE || state.freeSubmitted) return
        // Any multi-copy (2×2/3×3) sheet — character OR word — is a repetition
        // worksheet, not a scored attempt: scoring is a 1×1-only feature (one
        // guide cell per glyph), so the UI shows Clear·Next there with no
        // Submit. Never submit/score outside the SINGLE layout.
        if (state.practiceLayout != PracticeLayout.SINGLE) return
        if (session.strokes.none { it.points.isNotEmpty() }) return
        if (session === lastGradedSession) return

        val word = state.freeWord
        val character = if (word == null) {
            state.freeCharacters.getOrNull(state.currentIndex) ?: return
        } else {
            null
        }

        lastGradedSession = session
        pendingCellRect = cellRect
        pendingCells = cells
        if (word != null) {
            // Words aren't per-kana scored; recordWordAttempt clears any
            // lingering single-character feedback so no stale card shows.
            recordWordAttempt(word, Grade.GOOD, session, PracticeSource.FREE)
        } else if (character != null) {
            recordAttempt(character, Grade.GOOD, session, PracticeSource.FREE)
        }
        _uiState.update { it.copy(freeSubmitted = true, gradedCount = it.gradedCount + 1) }
    }

    /**
     * "Again" — after a submit, discard the ink and let the learner rewrite the
     * SAME character/word (this replaces the old behaviour where a grade tap
     * advanced past it). Clears the feedback and returns to the writing
     * sub-state without changing deck position.
     */
    fun freeRetry() {
        _uiState.update {
            if (it.phase != SessionPhase.FREE) {
                it
            } else {
                it.copy(
                    freeSubmitted = false,
                    lastScore = null,
                    lastScoredCharacter = null,
                    lastWordScores = null,
                    lastWordAverage = null,
                    canvasClearKey = it.canvasClearKey + 1,
                )
            }
        }
    }

    /**
     * "Next"/"Skip" — step two of the FREE flow (and the empty-canvas skip).
     * Advances to the next character in the free deck, returns to the writing
     * sub-state, and clears the feedback so it doesn't distract on the next
     * character. Single-word free practice has no deck to advance (no-op).
     */
    fun advanceToNextCharacter() {
        _uiState.update { state ->
            if (state.phase != SessionPhase.FREE) return@update state
            when {
                // Word-set free practice: step through the word queue, keeping
                // freeWord pointed at the new current word.
                state.freeWords.isNotEmpty() -> {
                    val next = (state.currentIndex + 1) % state.freeWords.size
                    state.copy(
                        currentIndex = next,
                        freeWord = state.freeWords[next],
                        freeSubmitted = false,
                        lastScore = null,
                        lastScoredCharacter = null,
                        lastWordScores = null,
                        lastWordAverage = null,
                        canvasClearKey = state.canvasClearKey + 1,
                    )
                }
                state.freeCharacters.isNotEmpty() -> state.copy(
                    currentIndex = (state.currentIndex + 1) % state.freeCharacters.size,
                    freeSubmitted = false,
                    lastScore = null,
                    lastScoredCharacter = null,
                    lastWordScores = null,
                    lastWordAverage = null,
                    canvasClearKey = state.canvasClearKey + 1,
                )
                else -> state
            }
        }
    }

    /**
     * A fresh [PracticeUiState] for a rebuild after a scope change: every
     * DURABLE field — the loaded deck, all persisted settings (grid/ghost/
     * layout/ink color/library view/section, script + the three opt-ins, AND
     * scoringMode/scoreStats) — is carried over from the current state, the
     * transient practice fields reset to defaults, and [canvasClearKey] bumped.
     * Callers override only what differs. Every rebuild path funnels through
     * here so no path can silently drop a durable field.
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
        practiceSets = practiceSets,
        isGridEnabled = isGridEnabled,
        isGhostEnabled = isGhostEnabled,
        isLibraryGridView = isLibraryGridView,
        librarySection = librarySection,
        brushInkColor = brushInkColor,
        practiceLayout = practiceLayout,
        scoringMode = scoringMode,
        scoreStats = scoreStats,
        readingStats = readingStats,
        activeScript = activeScript,
        includeDiacritics = includeDiacritics,
        includeYoon = includeYoon,
        includeExtended = includeExtended,
        characterForm = characterForm,
        appMode = appMode,
        canvasClearKey = canvasClearKey + 1,
    )

    /**
     * Switches the studied writing system (the Library's Hiragana/Katakana
     * selector). Persists the choice, then re-scopes free practice to the new
     * script in place. The selector lives in the library, which overlays any
     * phase, so it force-resets regardless of where practice was.
     * [lastGradedSession] is cleared so a stray in-flight submit can't land
     * against the discarded session, and the full [characters]/[allWords] deck
     * is preserved across the reset. The library stays open
     * ([PracticeUiState.isLibraryOpen] preserved) so the selector reads as an
     * in-place filter — the re-scoped practice waits behind the overlay.
     */
    fun setActiveScript(script: Script) {
        if (script == _uiState.value.activeScript) return
        settingsRepository.setActiveScript(script)
        lastGradedSession = null
        // Not isLoading: the library overlay stays mounted and re-scopes in
        // place; the rebuilt free practice waits behind it. No reload needed —
        // the full merged deck is already in memory, so re-scoping is enough.
        _uiState.update { it.rebuilt(isLoading = false, activeScript = script) }
        enterFreePracticeFromLastSection()
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
        enterFreePracticeFromLastSection()
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
        enterFreePracticeFromLastSection()
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
        enterFreePracticeFromLastSection()
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
     * Switches the traced ghost between the PRINTED (typographic) and
     * HANDWRITTEN (kaisho stroke) forms. Purely a rendering choice — the
     * stroke-order demo and the scorer always use the KanjiVG strokes — so this
     * just updates state (the canvas redraws the ghost). No rebuild and no
     * [PracticeUiState.canvasClearKey] bump: committed ink and the guide cells
     * are unchanged, so in-progress writing is preserved. Persisted.
     */
    fun setCharacterForm(form: CharacterForm) {
        if (form == _uiState.value.characterForm) return
        settingsRepository.setCharacterForm(form)
        _uiState.update { it.copy(characterForm = form) }
    }

    /**
     * Picks (or switches to) a top-level track. Persisted. Writing sets up its
     * free-practice item eagerly; Reading is rendered as the Library hub by its
     * own screen branch. Called both from the opening screen and from the
     * Study-scope Reading/Writing toggle (which lives inside the Library, so it
     * reads as an in-place switch — the Library stays open across the flip).
     */
    fun setAppMode(mode: AppMode) {
        if (mode == _uiState.value.appMode) return
        settingsRepository.setAppMode(mode)
        _uiState.update { it.copy(appMode = mode) }
        if (mode == AppMode.WRITING) enterFreePracticeFromLastSection()
    }

    /** Returns to the opening screen ("home"); keeps the persisted last mode. */
    fun goToOpening() {
        _uiState.update { it.copy(appMode = null, isLibraryOpen = false) }
    }

    /**
     * Remembers which Library tab (0 = Characters, 1 = Words) the learner last
     * viewed. Persisted so the next cold start resumes free practice in that
     * same area (see [enterFreePracticeFromLastSection]). A view preference
     * only — no effect on the current in-progress practice.
     */
    fun setLibrarySection(index: Int) {
        if (index == _uiState.value.librarySection) return
        settingsRepository.setLibrarySection(index)
        _uiState.update { it.copy(librarySection = index) }
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
            // Clear ALL advisory feedback (single-char AND per-kana word chips),
            // per the "reset wherever lastScore is" invariant — a mode change
            // must not leave stale chips lingering over the sheet.
            state.copy(
                scoringMode = mode,
                lastScore = null,
                lastScoredCharacter = null,
                lastWordScores = null,
                lastWordAverage = null,
            )
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
     * the sheet (the ghost cells move, so stale ink would misalign) — and
     * with it any pending FREE-practice feedback (submitted flag + score
     * cards), which would otherwise float over the freshly-blanked sheet.
     */
    fun setPracticeLayout(layout: PracticeLayout) {
        _uiState.update { state ->
            if (state.practiceLayout == layout) return@update state
            settingsRepository.setPracticeLayout(layout)
            state.copy(
                practiceLayout = layout,
                freeSubmitted = false,
                lastScore = null,
                lastScoredCharacter = null,
                lastWordScores = null,
                lastWordAverage = null,
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    // ---- LIBRARY --------------------------------------------------------

    /** Opens the character/word library overlay (any phase; purely visual). */
    fun openLibrary() {
        _uiState.update { it.copy(isLibraryOpen = true) }
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
                freeWords = emptyList(),
                currentIndex = deck.indexOfFirst { it.id == character.id }.coerceAtLeast(0),
                gradedCount = 0,
                freeWord = null,
                freeSubmitted = false,
                lastScore = null,
                lastScoredCharacter = null,
                lastWordScores = null,
                lastWordAverage = null,
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
                freeWords = emptyList(),
                currentIndex = 0,
                gradedCount = 0,
                freeWord = word,
                freeSubmitted = false,
                lastScore = null,
                lastScoredCharacter = null,
                lastWordScores = null,
                lastWordAverage = null,
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    // ---- PRACTICE SETS ---------------------------------------------------

    /**
     * Free practice of a saved set (no scoring side effects on SRS, same as
     * [practiceCharacter]/[practiceWord]). A character set becomes the free
     * deck (Next cycles the characters); a word set becomes the free word
     * queue (Next cycles the words). Members that no longer resolve are
     * dropped; an empty resolved set is a no-op.
     */
    fun practiceSet(setId: Long) {
        _uiState.update { state ->
            val set = state.practiceSet(setId) ?: return@update state
            when (set.type) {
                PracticeSetType.CHARACTERS -> {
                    val chars = set.members
                        .mapNotNull { glyph -> state.characters.firstOrNull { it.character == glyph } }
                    if (chars.isEmpty()) return@update state
                    state.copy(
                        isLibraryOpen = false,
                        phase = SessionPhase.FREE,
                        freeCharacters = chars,
                        freeWords = emptyList(),
                        currentIndex = 0,
                        gradedCount = 0,
                        freeWord = null,
                        freeSubmitted = false,
                        lastScore = null,
                        lastScoredCharacter = null,
                        lastWordScores = null,
                        lastWordAverage = null,
                        canvasClearKey = state.canvasClearKey + 1,
                    )
                }
                PracticeSetType.WORDS -> {
                    val words = set.members
                        .mapNotNull { kana -> state.allWords.firstOrNull { it.kana == kana } }
                    if (words.isEmpty()) return@update state
                    state.copy(
                        isLibraryOpen = false,
                        phase = SessionPhase.FREE,
                        freeCharacters = emptyList(),
                        freeWords = words,
                        currentIndex = 0,
                        gradedCount = 0,
                        freeWord = words.first(),
                        freeSubmitted = false,
                        lastScore = null,
                        lastScoredCharacter = null,
                        lastWordScores = null,
                        lastWordAverage = null,
                        canvasClearKey = state.canvasClearKey + 1,
                    )
                }
            }
        }
    }

    /** Creates and persists a new practice set. No-op on a blank name / empty members. */
    fun savePracticeSet(
        name: String,
        type: PracticeSetType,
        script: Script,
        members: List<String>,
    ) {
        if (name.isBlank() || members.isEmpty()) return
        viewModelScope.launch {
            runCatching { practiceSetRepository.createSet(name.trim(), type, script, members) }
                .onFailure { Log.w(TAG, "Failed to save practice set '$name'", it) }
        }
    }

    /** Replaces a set's members (add/remove while editing). No-op on empty members. */
    fun updatePracticeSetMembers(id: Long, members: List<String>) {
        if (members.isEmpty()) return
        viewModelScope.launch {
            runCatching { practiceSetRepository.updateMembers(id, members) }
                .onFailure { Log.w(TAG, "Failed to update practice set $id", it) }
        }
    }

    fun renamePracticeSet(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { practiceSetRepository.renameSet(id, name.trim()) }
                .onFailure { Log.w(TAG, "Failed to rename practice set $id", it) }
        }
    }

    fun deletePracticeSet(id: Long) {
        viewModelScope.launch {
            runCatching { practiceSetRepository.deleteSet(id) }
                .onFailure { Log.w(TAG, "Failed to delete practice set $id", it) }
        }
    }

    // ---- QUIZ ------------------------------------------------------------

    /**
     * "Quiz me!" in the library: opens the quiz setup overlay. [setId] is null
     * for the default active-script character quiz, or a saved set's id to
     * preselect that set as the quiz source (the setup card's source picker
     * lets the learner change it either way).
     */
    fun openQuiz(setId: Long? = null, kind: QuizKind = QuizKind.WRITING) {
        _uiState.update { state ->
            // Only preselect a set that still exists; otherwise fall back to
            // the default source so the picker never starts on a dangling id.
            val source = setId?.takeIf { id -> state.practiceSets.any { it.id == id } }
            val poolSize = state.quizSourceSize(source)
            if (poolSize == 0) return@update state
            state.copy(
                quiz = QuizState(
                    requestedCount = 5.coerceAtMost(poolSize),
                    showGrid = state.isGridEnabled,
                    selectedSetId = source,
                    kind = kind,
                ),
            )
        }
    }

    /**
     * Setup-screen source picker: switch between the default active-script
     * character quiz ([setId] null) and any saved set. Re-clamps the requested
     * count to the new source's size.
     */
    fun setQuizSource(setId: Long?) {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            val poolSize = state.quizSourceSize(setId)
            state.copy(
                quiz = quiz.copy(
                    selectedSetId = setId,
                    requestedCount = quiz.requestedCount.coerceIn(1, poolSize.coerceAtLeast(1)),
                ),
            )
        }
    }

    /** Reading-quiz setup: multiple-choice vs typed romaji. */
    fun setReadingAnswerStyle(style: ReadingAnswerStyle) {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(readingAnswerStyle = style))
        }
    }

    /** Reading-quiz setup: also test the English meaning (word sources only). */
    fun setReadingTestMeaning(enabled: Boolean) {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            state.copy(quiz = quiz.copy(readingTestMeaning = enabled))
        }
    }

    /**
     * Records a READING answer for the current item: correct → 100, wrong → 0
     * in [QuizState.results] (the summary reads it), and reveals the item so the
     * running screen shows the answer + audio before Next. Session-only for now;
     * persistent reading stats arrive with the reading_progress table.
     */
    fun readingResult(correct: Boolean) {
        val quiz = _uiState.value.quiz ?: return
        val item = quiz.current ?: return
        val typed = quiz.readingAnswerStyle == ReadingAnswerStyle.TYPE
        _uiState.update { state ->
            val q = state.quiz ?: return@update state
            state.copy(
                quiz = q.copy(
                    revealed = true,
                    submitted = true,
                    results = q.results + (item.key to if (correct) 100 else 0),
                    submittedKeys = q.submittedKeys + item.key,
                ),
            )
        }
        // Persist the pass/fail into the reading tally (keyed by the glyph/word,
        // split by answer style). Fire-and-forget; the Library tint updates via
        // the readingStats collector.
        viewModelScope.launch {
            runCatching { readingProgressRepository.record(item.answer, typed, correct) }
                .onFailure { Log.w(TAG, "Failed to record reading result for ${item.answer}", it) }
        }
    }

    // ---- Reading flashcards (study mode) --------------------------------

    /** Opens the reading flashcard flip over the scoped characters, starting at [character]. */
    fun openReadingFlashcardsForCharacter(character: HiraganaCharacter) {
        val state = _uiState.value
        val chars = state.activeScriptCharacters
        if (chars.isEmpty()) return
        val cards = chars.map { ReadingCard(it.character, it.romaji, null) }
        val start = chars.indexOfFirst { it.id == character.id }.coerceAtLeast(0)
        _uiState.update { it.copy(readingFlashcard = ReadingFlashcardState(cards, start)) }
    }

    /** Opens the reading flashcard flip over the scoped words, starting at [word]. */
    fun openReadingFlashcardsForWord(word: VocabularyWord) {
        val state = _uiState.value
        val words = ScriptScope.scopedWords(
            state.allWords,
            state.activeScript,
            state.includeDiacritics,
            state.includeYoon,
            state.includeExtended,
        )
        if (words.isEmpty()) return
        val cards = words.map { ReadingCard(it.kana, state.pronunciationOf(it), it.meaning) }
        val start = words.indexOfFirst { it.kana == word.kana }.coerceAtLeast(0)
        _uiState.update { it.copy(readingFlashcard = ReadingFlashcardState(cards, start)) }
    }

    /** Flips the current flashcard to reveal its reading + meaning. */
    fun flashcardReveal() {
        _uiState.update { s ->
            val fc = s.readingFlashcard ?: return@update s
            s.copy(readingFlashcard = fc.copy(revealed = true))
        }
    }

    /**
     * Advances to the next flashcard (wraps), face-down again. Self-graded, so
     * "Got it" and "Missed" both simply move on — flashcards are study, not scored.
     */
    fun flashcardNext() {
        _uiState.update { s ->
            val fc = s.readingFlashcard ?: return@update s
            val next = if (fc.cards.isEmpty()) 0 else (fc.index + 1) % fc.cards.size
            s.copy(readingFlashcard = fc.copy(index = next, revealed = false))
        }
    }

    /** Closes the flashcard overlay, back to the reading Library. */
    fun closeReadingFlashcards() {
        _uiState.update { it.copy(readingFlashcard = null) }
    }

    /**
     * Back/close/Done from any quiz stage; the library (and phase) underneath
     * resume untouched. A quiz launched from a saved set ([QuizState.selectedSetId]
     * non-null) flags [PracticeUiState.pendingOpenSetsManager] so the library
     * reopens the sets manager rather than the browse page.
     */
    fun closeQuiz() {
        _uiState.update {
            val quiz = it.quiz ?: return@update it
            it.copy(
                quiz = null,
                pendingOpenSetsManager = quiz.selectedSetId != null,
                // Drop any lingering feedback (incl. a late async recognition
                // update) so it can't flash on the screen underneath.
                lastScore = null,
                lastScoredCharacter = null,
                lastWordScores = null,
                lastWordAverage = null,
                canvasClearKey = it.canvasClearKey + 1,
            )
        }
    }

    /** Clears the [PracticeUiState.pendingOpenSetsManager] one-shot once the library has acted on it. */
    fun consumeOpenSetsManager() {
        if (_uiState.value.pendingOpenSetsManager) {
            _uiState.update { it.copy(pendingOpenSetsManager = false) }
        }
    }

    /**
     * Setup-screen count: preset chips, the +/− stepper, and the free-typed
     * number all land here; clamped to what the deck can actually serve.
     */
    fun setQuizCount(count: Int) {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            val poolSize = state.quizSourceSize(quiz.selectedSetId)
            state.copy(
                quiz = quiz.copy(
                    requestedCount = count.coerceIn(1, poolSize.coerceAtLeast(1)),
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
     * summary). For the default source, the active-script characters are
     * shuffled. For a saved set, the set's members (characters OR words) are the
     * pool, resolved to deck entries and shuffled.
     */
    fun startQuiz() {
        _uiState.update { state ->
            val quiz = state.quiz ?: return@update state
            val pool = buildQuizPool(state, quiz.selectedSetId)
            if (pool.isEmpty()) return@update state
            state.copy(
                quiz = quiz.copy(
                    stage = QuizStage.RUNNING,
                    items = pool.take(quiz.requestedCount.coerceIn(1, pool.size)),
                    index = 0,
                    revealed = false,
                    submitted = false,
                    results = emptyMap(),
                    submittedKeys = emptySet(),
                ),
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    /** Resolves the quiz pool as an ordered list of [QuizItem]s for the source. */
    private fun buildQuizPool(
        state: PracticeUiState,
        setId: Long?,
    ): List<QuizItem> {
        val set = state.practiceSet(setId)
        return when {
            set == null ->
                // Default source: active-script characters, shuffled.
                state.activeScriptCharacters.shuffled().map { CharQuizItem(it) }
            set.type == PracticeSetType.CHARACTERS ->
                set.members
                    .mapNotNull { glyph -> state.characters.firstOrNull { it.character == glyph } }
                    .map { CharQuizItem(it) }
                    .shuffled()
            else ->
                set.members
                    .mapNotNull { kana -> state.allWords.firstOrNull { it.kana == kana } }
                    .map { WordQuizItem(it, prompt = state.pronunciationOf(it)) }
                    .shuffled()
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
     *
     * A word item is scored per-kana via [recordWordAttempt] (the same engine
     * as whole-word practice: each kana against its own reference, averaged);
     * [cells] carries the per-kana guide cells the canvas exposed. A single
     * character goes through [recordAttempt]. Either way the headline (the word
     * average, or the character score) lands in [QuizState.results].
     */
    fun quizSubmit(
        session: DrawingSession,
        cellRect: CellRect? = null,
        cells: List<CellRect> = emptyList(),
    ) {
        val quiz = _uiState.value.quiz ?: return
        if (quiz.stage != QuizStage.RUNNING || quiz.submitted) return
        val item = quiz.current ?: return
        if (session.strokes.none { it.points.isNotEmpty() }) return
        if (session === lastGradedSession) return

        lastGradedSession = session
        pendingCellRect = cellRect
        pendingCells = cells
        val headline: Int? = when (item) {
            is CharQuizItem ->
                recordAttempt(item.character, Grade.GOOD, session, PracticeSource.QUIZ)?.headline
            is WordQuizItem -> {
                // recordWordAttempt updates lastWordScores/lastWordAverage
                // synchronously (persistence is fire-and-forget), so the
                // average is available immediately for the results map.
                recordWordAttempt(item.word, Grade.GOOD, session, PracticeSource.QUIZ)
                _uiState.value.lastWordAverage
            }
        }

        _uiState.update { state ->
            val live = state.quiz ?: return@update state
            state.copy(
                quiz = live.copy(
                    submitted = true,
                    revealed = true,
                    results = if (headline != null) live.results + (item.key to headline) else live.results,
                    submittedKeys = live.submittedKeys + item.key,
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
            val finished = quiz.index + 1 >= quiz.items.size
            state.copy(
                quiz = quiz.copy(
                    stage = if (finished) QuizStage.SUMMARY else QuizStage.RUNNING,
                    index = if (finished) quiz.index else quiz.index + 1,
                    revealed = false,
                    submitted = false,
                ),
                lastScore = null,
                lastScoredCharacter = null,
                lastWordScores = null,
                lastWordAverage = null,
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
                lastWordScores = null,
                lastWordAverage = null,
                canvasClearKey = state.canvasClearKey + 1,
            )
        }
    }

    /**
     * Records a whole-word attempt. When the sheet is a single copy with one
     * guide cell per kana (1×1 layout) and scoring is on, each kana is scored
     * INDEPENDENTLY: its strokes are attributed to its cell
     * ([StrokeAttribution.groupByCell]) and compared to that kana's reference.
     * The per-kana scores drive the chips shown above the canvas and their
     * average the readout below; each kana persists its own scored row (so the
     * Library's per-glyph history includes word practice), plus one word-level
     * row carrying the AVERAGE form/fit — keyed by the whole-word string — for
     * the Library word card's color.
     *
     * **When grading is off (`ScoringMode.OFF`) nothing is recorded** — pure
     * practice. Otherwise, if the sheet has no per-kana cells (multi-copy
     * 2×2/3×3, where a word can't be scored per cell) it falls back to a single
     * unscored row (character = full kana, joined romaji).
     */
    private fun recordWordAttempt(
        word: VocabularyWord,
        grade: Grade,
        session: DrawingSession,
        source: PracticeSource,
    ) {
        val characters = _uiState.value.characters
        val units = KanaUnits.split(word.kana)
        val romaji = units.joinToString("") { unit ->
            characters.firstOrNull { it.character == unit }?.romaji ?: unit
        }
        val createdAt = clock.millis()
        val strokeCount = session.strokes.size
        val durationMs = session.durationMillis()

        fun persist(attempt: PracticeAttempt) {
            viewModelScope.launch {
                runCatching { practiceRepository.recordAttempt(attempt) }
                    .onFailure { Log.w(TAG, "Failed to record word attempt for ${word.kana}", it) }
            }
        }

        val mode = _uiState.value.scoringMode
        // Grading off = pure practice: don't record the word attempt at all
        // (same product decision as recordAttempt). vocab unlock/staleness is
        // tracked separately by the caller and is unaffected.
        if (mode == ScoringMode.OFF) {
            _uiState.update {
                it.copy(
                    lastScore = null,
                    lastScoredCharacter = null,
                    lastWordScores = null,
                    lastWordAverage = null,
                )
            }
            return
        }
        val cells = pendingCells
        val userStrokes = session.strokes
            .map { stroke -> stroke.points.map { Pt(it.x, it.y) } }
            .filter { it.isNotEmpty() }
        // Per-cell scoring only when one guide cell exists per kana (1×1 sheet)
        // and scoring is on; multi-copy 2×2/3×3 leaves pendingCells empty.
        val canScorePerCell = mode != ScoringMode.OFF &&
            cells.size == units.size &&
            userStrokes.isNotEmpty()

        if (canScorePerCell) {
            val perCellStrokes = StrokeAttribution.groupByCell(userStrokes, cells)
            val unitScores = mutableListOf<WordUnitScore>()
            units.forEachIndexed { index, unit ->
                val entry = characters.firstOrNull { it.character == unit }
                val reference = entry?.strokeAnimations
                    ?.sortedBy { it.order }
                    ?.map { def -> def.points.map { Pt(it.x, it.y) } }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                val ink = perCellStrokes.getOrNull(index).orEmpty()
                // Skip units with no reference (e.g. ー) or no ink in their cell.
                if (reference.isEmpty() || ink.isEmpty()) return@forEachIndexed
                val geometry = GeometryScorer.score(ink, reference, cells[index])
                unitScores.add(WordUnitScore(unit, CharacterScore.combine(geometry, null, mode)))
                // Each kana persists its own scored row so the Library's
                // per-glyph avg/best reflects word practice too.
                persist(
                    PracticeAttempt(
                        character = unit,
                        romaji = entry?.romaji ?: unit,
                        grade = grade,
                        strokeCount = ink.size,
                        durationMs = durationMs,
                        createdAt = createdAt,
                        source = source,
                        formScore = geometry.form.toFloat(),
                        fitScore = geometry.fit?.toFloat(),
                    ),
                )
            }

            if (unitScores.isNotEmpty()) {
                val avgHeadline = unitScores.map { it.score.headline }.average().roundToInt()
                val avgForm = unitScores.map { it.score.form }.average().toFloat()
                val fits = unitScores.mapNotNull { it.score.fit }
                val avgFit = if (fits.isEmpty()) null else fits.average().toFloat()
                _uiState.update {
                    it.copy(
                        lastScore = null,
                        lastScoredCharacter = null,
                        lastWordScores = unitScores.toList(),
                        lastWordAverage = avgHeadline,
                    )
                }
                // One word-level row carrying the average (keyed by the whole
                // word string) — colors the Library word card.
                persist(
                    PracticeAttempt(
                        character = word.kana,
                        romaji = romaji,
                        grade = grade,
                        strokeCount = strokeCount,
                        durationMs = durationMs,
                        createdAt = createdAt,
                        source = source,
                        formScore = avgForm,
                        fitScore = avgFit,
                    ),
                )
                return
            }
        }

        // Not scored (scoring off, multi-copy sheet, or nothing attributable):
        // clear any lingering feedback and record one plain word row.
        _uiState.update {
            it.copy(
                lastScore = null,
                lastScoredCharacter = null,
                lastWordScores = null,
                lastWordAverage = null,
            )
        }
        persist(
            PracticeAttempt(
                character = word.kana,
                romaji = romaji,
                grade = grade,
                strokeCount = strokeCount,
                durationMs = durationMs,
                createdAt = createdAt,
                source = source,
            ),
        )
    }

    /**
     * Records one single-character practice attempt. **When grading is off
     * (`ScoringMode.OFF`) nothing is recorded** — it's pure practice with no
     * score to store. Otherwise it persists the attempt and computes its
     * handwriting-quality score. Geometry is computed
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
        // Grading off = pure practice: nothing to score, so don't log the
        // attempt at all (product decision — no point recording ungraded tries).
        // SRS scheduling, done by the caller, is unaffected.
        if (mode == ScoringMode.OFF) {
            _uiState.update {
                it.copy(
                    lastScore = null,
                    lastScoredCharacter = null,
                    lastWordScores = null,
                    lastWordAverage = null,
                )
            }
            return null
        }
        val reference = character.strokeAnimations
            .sortedBy { it.order }
            .map { def -> def.points.map { Pt(it.x, it.y) } }
            .filter { it.isNotEmpty() }
        val userStrokes = session.strokes
            .map { stroke -> stroke.points.map { Pt(it.x, it.y) } }
            .filter { it.isNotEmpty() }
        // Scoring only runs when ONE glyph was written ONCE: [cell] is non-null
        // exactly when the ink was drawn in a single 1×1 guide cell (see
        // CanvasState.lastCell). This excludes the multi-copy 2×2/3×3 layouts —
        // where all copies' strokes flatten into one list and would score as a
        // garbage stroke-count mismatch ("you drew 12 strokes") — and word
        // sheets, while still covering the quiz (it forces PracticeLayout.SINGLE).
        val scoreEnabled = mode != ScoringMode.OFF &&
            reference.isNotEmpty() &&
            userStrokes.isNotEmpty() &&
            cell != null

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
            _uiState.update {
                it.copy(
                    lastScore = null,
                    lastScoredCharacter = null,
                    lastWordScores = null,
                    lastWordAverage = null,
                )
            }
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
            it.copy(
                lastScore = score,
                lastScoredCharacter = character.character,
                lastWordScores = null,
                lastWordAverage = null,
            )
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

    /** Everything [startPractice] loads (deck + words). */
    private data class DeckLoad(
        val characters: List<HiraganaCharacter>,
        val allWords: List<VocabularyWord>,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HiraganaApplication
                PracticeViewModel(
                    characterRepository = application.container.characterRepository,
                    practiceRepository = application.container.practiceRepository,
                    vocabularyRepository = application.container.vocabularyRepository,
                    settingsRepository = application.container.settingsRepository,
                    practiceSetRepository = application.container.practiceSetRepository,
                    readingProgressRepository = application.container.readingProgressRepository,
                    handwritingRecognizer = application.container.handwritingRecognizer,
                    ttsPlayer = application.container.ttsPronunciationPlayer,
                )
            }
        }
    }
}
