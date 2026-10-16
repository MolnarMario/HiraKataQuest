# Architecture

Deep-dive into how HiraKataQuest is put together. For the project overview see
[README.md](../README.md), for working conventions see [CLAUDE.md](../CLAUDE.md), for the
drawing pipeline internals see [docs/DRAWING.md](DRAWING.md), for asset/Room details see
[docs/DATA.md](DATA.md), and for the build recovery record see [docs/HISTORY.md](HISTORY.md).

Scope note: sections 1–6 describe the **Phase 1** tracing core (originally characters あいうえお; the shipped deck is now the full 46-character basic gojūon, あ〜ん),
complete and verified with a successful `assembleDebug` on 2026-07-03. **Phase 2
(animated stroke-order demonstrations) is also complete** — implemented and built
2026-07-03, adversarially reviewed and review-fixed 2026-07-04. **Phase 3 (Smart
Learning System — SRS scheduling, session generation, vocabulary unlocking) is also
complete** — implemented, built and unit-tested 2026-07-04 (45/45 tests green),
adversarially reviewed the same day (0 P0, review fixes applied). **Phase 3 was
then REMOVED on 2026-07-05** (§8 and §3/§4 note this inline): the SRS scheduler,
lesson generator, `StudySession`, and the session state machine were deleted, so
the app is now free-practice + quiz only with built-in default practice sets.
`SessionPhase` is now just `LOADING`/`FREE`; the Room SRS tables + their
repositories are retained as dormant, unused schema. The Phase-3 descriptions in
the sections below are therefore historical except where a 2026-07-05 note
supersedes them. A **katakana
section** (2026-07-04, device-verified on the Pixel 7) then added a second script
without touching the learning engine or Room schema: a `Script` enum on every
`HiraganaCharacter` (set by the loader from the `assets/<script>/` directory), a
second asset directory, a persisted `activeScript` setting, and a Library selector.
The `PracticeViewModel` keeps the full merged deck loaded (for cross-script romaji
lookups) but derives a script-scoped deck via `ScriptScope.scopedCharacters/Words`
and feeds ONLY that into the quiz pool and FREE practice — katakana rows are
additive (no Room migration). (When this was written the scoped deck also fed
`LessonGenerator.generate()`, but the guided study session was removed 2026-07-05.)
The same `ScriptScope` seam then carries the **dakuten/handakuten opt-in**
(2026-07-04): `KanaKind` classifies each glyph (base vs voiced/semi-voiced, via
Unicode NFD) and the persisted `includeDiacritics` setting (default off) makes
`ScriptScope` drop voiced kana and their words unless enabled — so a beginner
sees only the base gojūon. A **yōon opt-in** (2026-07-04) extends the same seam
with a second flag (`includeYoon`): yōon characters (きゃ …) are the app's only
composite glyphs — two code points with no single KanjiVG file, so their strokes
are composed offline from the base kana + a small ゃ/ゅ/ょ — and `KanaUnits`
centralizes the base+small→one-unit rule so their vocabulary unlocks correctly.
An **extended-kana opt-in** (2026-07-04) adds a third flag (`includeExtended`)
covering the small tsu, small vowels, foreign-sound composites (ファ/ティ — more
composite glyphs, same offline composition), and obsolete kana; `KanaUnits` now
also fuses a base + small vowel into one unit. `ScriptScope` now takes four
flags (script, `includeDiacritics`, `includeYoon`, `includeExtended`), checked
in priority **extended → yōon → voiced**, so each opt-in is independent (a
foreign combo like ヴァ is gated by `includeExtended` alone, and ゔ/ヴ — voiced by
NFD — stay under `includeDiacritics`). `setActiveScript`,
`setIncludeDiacritics`, `setIncludeYoon`, and `setIncludeExtended` all persist
then re-enter FREE practice in place, keeping the library open. With all
three opt-ins on, the deck is the complete kana inventory (both scripts).
A later **practice-sets** feature adds user-curated, hand-picked lists of
characters or words (`domain/model/PracticeSet.kt`) — an *explicit membership
list*, deliberately distinct from the declarative Study-scope opt-ins (a filter
config). Sets persist to two new additive Room tables (DB **v4**,
`MIGRATION_3_4`) and can be launched into FREE practice or the "Quiz me!" mode;
the same feature generalized the quiz to handle **words** as well as characters
(`QuizItem` sealed interface, per-kana feedback) and re-sized the Library's
Study-scope sheet (see §8-scoring below).
A 2026-07-09 **Reading/Writing split** then added a second top-level mode: the app now
opens on a mode chooser (`AppMode {READING, WRITING}`, persisted `app_mode`;
`PracticeUiState.appMode == null` ⇒ the opening screen), from which **Writing** is the
whole handwriting app described here and **Reading** is a recognition track (flashcards
+ a multiple-choice / type-the-romaji / color-the-answer quiz). It is purely additive —
the drawing, scoring, and practice-set machinery are unchanged; it adds an opening
screen + a reading flashcard screen, a `ReadingProgressRepository` (Room **v5**, an
additive migration), lenient romaji matching (`RomajiMatch`), and per-mode Library
tinting. Navigation stays a state-driven `when {}` in `PracticeScreen` (still no nav
library). See §9.
Section 7 covers the Phase 2 design as
built and section 8 covers Phase 3; the full contracts are
[docs/PHASE2-DESIGN.md](PHASE2-DESIGN.md) and [docs/PHASE3-DESIGN.md](PHASE3-DESIGN.md).

All Kotlin sources live under `app/src/main/java/com/hive/hiragana/`. After the
2026-07-05 guided-study removal the `main` source set lost `SrsScheduler`,
`LessonGenerator`, `StudySession`, and `PracticeActionBar` and gained
`DefaultPracticeSets` + a standalone `FreePracticeActionBar`; the test set dropped the
three `domain/learning` files, leaving **84 JUnit tests** (there is still no
`androidTest/`). The test count had grown past Phase 3's original 45 as the
katakana/opt-in seam (`Script`, `KanaKind`,
`KanaUnits`, `ScriptScope`), the Library chart (`KanaChart`), and the
handwriting-scoring layer (`GeometryScorer`, `CharacterScore`, `StrokeAttribution`) each
brought their own pure-domain suites. Paths below are given relative to
`app/src/main/java/com/hive/hiragana/` unless otherwise noted.

Beyond the three phases and the katakana/opt-in work described above, later additive
features layer onto the same architecture without touching the drawing pipeline or the
learning engine: a **handwriting-quality scoring** subsystem (pure `domain/scoring/`
geometry scorer + an ML Kit `data/recognition/` recognizer, advisory-only, §8), a
**write-from-memory quiz** overlay (`QuizScreen` + `QuizState`/`QuizStage` in the
ViewModel, `PracticeSource.QUIZ`), and a **Library chart view** (`domain/model/KanaChart.kt`,
a list⇄grid toggle). These pushed Room to **v3** (a purely additive score-column
migration — see §8 and [docs/DATA.md](DATA.md)). A most-recent scoring-UX batch (§8) then
made FREE practice a **two-step Submit→review flow** (`FreePracticeActionBar`,
`PracticeUiState.freeSubmitted`), gated single-glyph scoring to a lone 1×1 cell, added
**per-kana word scoring** (`domain/scoring/StrokeAttribution.kt` +
`presentation/components/WordScoreDisplay.kt`), made "grading off" skip recording
entirely, added multi-unit **word stroke-order previews** (`WordStrokePreview`), and
tinted the Library by score (`presentation/theme/ScoreColors.kt`).

---

## 1. Layer map

Four packages plus two root files:

| Package | Responsibility | Depends on |
|---|---|---|
| `domain` | Models (`HiraganaCharacter`, `Grade`, `PracticeAttempt`, `StrokeDefinition`/`StrokeFrame`, the Phase 3 additions `ReviewCard`, `VocabularyWord`, `StudySession`, `CharacterProgress`, `VocabularyProgress`, and the later `Script`/`KanaKind`/`KanaUnits`/`ScriptScope`/`PracticeLayout`/`KanaChart`/`PracticeSet` types, plus the 2026-07-09 Reading/Writing-split models `AppMode`, `RomajiMatch`, and `ReadingProgress`), repository interfaces (incl. `SettingsRepository`, `HandwritingRecognizer`, `PracticeSetRepository`, `ReadingProgressRepository`), `domain/learning` (pure SRS scheduler + session generator), and `domain/scoring` — the pure handwriting-quality scorer (`GeometryScorer`, `ScoringMode`, `CharacterScore`, `CharacterScoreSummary`). | **Pure Kotlin + `kotlinx.coroutines`** (`Flow` in `PracticeRepository`). Zero Android imports. `domain/learning` and `domain/scoring` additionally take no clock reads internally — every computation is parameterized (`todayStartMillis: Long`; explicit stroke/cell inputs), which is what makes them unit-testable without instrumentation. |
| `data` | Implements the domain repository interfaces: asset JSON parsing (`data/assets`), Room persistence (`data/local`), repository adapters (`data/repository`), and the ML Kit handwriting recognizer (`data/recognition`). | `domain`, Android (`AssetManager`, Room, ML Kit `digital-ink-recognition`), `org.json`, coroutines. |
| `drawing` | Standalone ink + playback subsystem: raw `MotionEvent` capture, stroke models, path smoothing, canvas snapshot-state, grid/ghost draw helpers, and the Phase 2 stroke-order playback engine (`drawing/animation`). | Compose runtime/UI (`Path`, `DrawScope`, snapshot state, `Animatable`) and `android.view.MotionEvent` / `android.graphics.Paint`. Does not depend on `data` or `presentation`; since Phase 2, `drawing/animation` imports `domain.model.StrokeDefinition`/`StrokeFrame` (timed stroke data) — the one `drawing → domain` edge. Phase 3 did not touch this package. |
| `presentation` | Compose UI: theme, components, the single `PracticeScreen` (plus the `LibraryScreen` overlay it hosts), and `PracticeViewModel` (now a Phase 3 session state machine — see §3/§8). | `domain` + `drawing` + Compose/lifecycle. |
| root (`HiraganaApplication.kt`, `MainActivity.kt`) | Process entry, DI container, activity that hosts the one screen. | everything above. |

Dependency direction:

```
MainActivity ─► presentation ─► domain ◄─ data
                     │             ▲
                     └────────► drawing        (drawing/animation ─► domain.model only;
                                                the rest of drawing imports nothing from
                                                domain/data/presentation)
```

Points worth knowing that a naive reading of "clean architecture" would miss:

- **`drawing` is not Android-free.** It is a self-contained *subsystem*, not a pure
  domain layer: `drawing/stroke/StrokeCapture.kt` consumes `android.view.MotionEvent`
  and `drawing/canvas/*` builds Compose `Path`s and uses `android.graphics.Paint`.
  Its isolation is horizontal (no knowledge of characters, grading, or persistence),
  not vertical. Phase 2 softened this by one edge: `drawing/animation/*` consumes the
  `domain.model` timing types (`StrokeDefinition`, `StrokeFrame`) — it still knows
  nothing about characters, grading, or persistence.
- **`drawing.stroke` and `drawing.canvas` are mutually dependent**: `StrokeCapture`
  drives a `CanvasState` (stroke → canvas), while `CanvasState`/`StrokeRenderer`
  consume `StrokePoint`/`UserStroke`/`DrawingSession` (canvas → stroke). Treat the two
  sub-packages as one unit.
- **One deliberate upward import**: `presentation/viewmodels/PracticeViewModel.kt`
  imports root-level `HiraganaApplication` — only inside the companion `Factory` to
  reach the DI container (see §2).
- The ViewModel also imports `drawing.stroke.DrawingSession` and `durationMillis` —
  presentation is the only consumer that bridges `drawing` and `domain` (it converts a
  `DrawingSession` into a `PracticeAttempt` at grading time).

## 2. App startup & DI wiring

Manual DI, no Hilt/Koin. The choice is documented in-code as deliberate — the KDoc on
`AppContainer` reads: *"Hand-rolled dependency container — deliberately simple for the
MVP; swap for Hilt if the object graph grows."* Phase 3 grew the graph from three lazy
objects to five; the later settings and scoring work took it to seven (adding
`settingsRepository` and `handwritingRecognizer`), the practice-sets feature
to eight (adding `practiceSetRepository`), and the 2026-07-09 Reading/Writing
split to nine (adding `readingProgressRepository`), still all manual.

Wiring chain (all in `HiraganaApplication.kt` and `PracticeViewModel.kt`):

1. `HiraganaApplication.onCreate()` creates `container = AppContainer(this)`.
   `HiraganaApplication` is registered via `android:name=".HiraganaApplication"` in
   `app/src/main/AndroidManifest.xml`.
2. `AppContainer` (same file as the Application class) holds, all `by lazy`:
   - `database: HiraganaDatabase` — `HiraganaDatabase.build(appContext)` (Room v6,
     `hiragana-practice.db`, private).
   - `assetDataSource: HiraganaAssetDataSource` — `HiraganaAssetDataSource(appContext.assets)`
     (private; Phase 3 extracted it to its own lazy so both `characterRepository` and
     `vocabularyRepository` share one instance).
   - `characterRepository: CharacterRepository` = `AssetCharacterRepository(assetDataSource)`.
   - `practiceRepository: PracticeRepository` =
     `RoomPracticeRepository(database.practiceAttemptDao())`.
   - `learningRepository: LearningRepository` (Phase 3) =
     `RoomLearningRepository(database.reviewCardDao(), database.characterProgressDao())`.
   - `vocabularyRepository: VocabularyRepository` (Phase 3) =
     `RoomVocabularyRepository(assetDataSource, database.vocabularyProgressDao())`.
   - `settingsRepository: SettingsRepository` = `PrefsSettingsRepository(appContext)`
     (SharedPreferences-backed; grid/ghost/layout/script/opt-in/scoring/library-view/
     ink-color toggles — see §3).
   - `handwritingRecognizer: HandwritingRecognizer` = `MlKitHandwritingRecognizer()`
     (ML Kit Digital Ink; feeds the §8 scoring pass).
   - `practiceSetRepository: PracticeSetRepository` =
     `RoomPracticeSetRepository(database.practiceSetDao())` (user-curated character/
     word lists — Room v4, see §8-scoring).
   - `readingProgressRepository: ReadingProgressRepository` (2026-07-09) =
     `RoomReadingProgressRepository(database.readingProgressDao())` (per-glyph reading
     accuracy for the Reading mode — Room v5, see §9).
   Nothing is constructed until first use; the Room database is only built when the
   first grade is recorded or the first session is generated (nothing reads it at startup).
3. `MainActivity.onCreate()` → `setContent { HiraganaTheme { PracticeScreen(viewModel = viewModel(factory = PracticeViewModel.Factory)) } }`.
4. `PracticeViewModel.Factory` is a `viewModelFactory { initializer { … } }` that pulls
   the Application out of `CreationExtras` and casts:

   ```kotlin
   val application =
       this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HiraganaApplication
   PracticeViewModel(
       characterRepository = application.container.characterRepository,
       practiceRepository = application.container.practiceRepository,
       learningRepository = application.container.learningRepository,
       vocabularyRepository = application.container.vocabularyRepository,
       settingsRepository = application.container.settingsRepository,
       handwritingRecognizer = application.container.handwritingRecognizer,
       practiceSetRepository = application.container.practiceSetRepository,
       readingProgressRepository = application.container.readingProgressRepository,
   )
   ```

   The ViewModel itself only sees the eight `domain` repository interfaces (plus, as
   defaulted constructor parameters, a `LessonGenerator()` and `Clock.systemDefaultZone()`
   — not part of `AppContainer`, since both are cheap pure-Kotlin objects) — it does not
   know about Room, assets, or the container beyond the factory. `GeometryScorer` /
   `CharacterScore.combine` are called directly (pure `object`/companion, nothing to inject).

## 3. State management — the load-bearing design decision

There are **two separate state channels**, and the split is intentional — Phase 3 grew
channel (a) into a session state machine but did not touch the split itself:

### (a) `PracticeUiState` — session/deck state, owned by the ViewModel

`presentation/viewmodels/PracticeViewModel.kt` exposes
`uiState: StateFlow<PracticeUiState>` (backed by a private `MutableStateFlow`, mutated
only via `_uiState.update { … }`). **As of 2026-07-05 the `SessionPhase` machine is just:**

```
LOADING → FREE
```

The guided-study phases (`REVIEWS`/`NEW_CHARACTER`/`PRACTICE`/`VOCABULARY`/`COMPLETE`)
described in the rest of this section were **removed with the SRS study session** —
`startPractice()` loads the deck and drops straight into `FREE` (see §4). The library
feeds `FREE` directly: `practiceCharacter(char)` free-practices the **whole** deck
starting at the pick, and `practiceWord(word)` free-practices the word's kana in order
with `freeWord` driving the word header in the guide panel. Practice sets launch into
`FREE` too (`practiceSet(id)`). The rest of the field table below is **historical for the
removed phases** (the guided fields — `reviewQueue`, `newCharacter`, `vocabularyQueue`,
`allCaughtUp`, `learnedCharacterIds`, `unlockedWordKana`, etc. — no longer exist); the
`FREE`, SETTINGS, SCORING, LIBRARY-overlay, QUIZ, and practice-set rows still apply. Key
`PracticeUiState` fields, grouped by which phase(s) they served:

| Field | Meaning |
|---|---|
| `isLoading`, `phase: SessionPhase`, `characters`, `errorMessage`, `canvasClearKey: Int` | Always relevant. `canvasClearKey` is a single monotonic counter bumped on every advance (each review, each practice rep, each word, each phase change) — see (b) below. |
| `reviewQueue`, `reviewsTotal`, `reviewsDone` | REVIEWS: the due-card queue (front = current), and progress counters. |
| `newCharacter`, `repetitionIndex`, `repetitionsTotal` | NEW_CHARACTER/PRACTICE: the character being introduced and its rep count (3 by default). |
| `vocabularyQueue`, `currentWord`, `wordCharIndex`, `wordsTotal`, `wordsPracticed`, `newlyUnlockedKana` | VOCABULARY: the word queue, the kana index being traced, and which unlocks are fresh this session (drives the "New word!" badge, not reinforcement picks). |
| `allCaughtUp`, `newCharacterLearned`, `wordsUnlocked`, `hasLearnedCharacters` | COMPLETE: summary stats. `newCharacterLearned` is null if the final practice rep was graded Again (introduced, not learned); `wordsUnlocked` lists only fresh unlocks; `hasLearnedCharacters` gates whether "Keep practicing" renders at all. |
| `freeCharacters`, `currentIndex`, `gradedCount`, `freeWord`, `freeSubmitted` | FREE: the old Phase 1/2 deck-walking fields, now driving a **two-step Submit→review flow**. Via "Keep practicing" the deck is scoped to characters with `repetitions >= 1`; via a library pick it can hold the **whole** deck (`practiceCharacter`) or one word's kana in order (`practiceWord`, with `freeWord` non-null so the guide panel shows the word header). `freeSubmitted` is the writing↔reviewing flag: `false` = writing (Clear · Submit/Skip), `true` = reviewing the just-scored attempt (Again · Next). It is reset at every FREE entry point, on `freeRetry()` (rewrite the same char/word), and on `advanceToNextCharacter()` (Next). See §4. |
| `isLibraryOpen`, `allWords` | LIBRARY: whether the full-screen library overlay is showing (it overlays `FREE` without disturbing it) and every vocabulary word. (The `unlockedWordKana`/`learnedCharacterIds` sets and the "Unlocked/Locked" + "Learned/Not started" row labels they drove were **removed 2026-07-05** — rows now show only the score tint.) |
| `quiz: QuizState?` | QUIZ (write-from-memory overlay launched from the library; renders above it). `QuizState` holds a `QuizStage` (`SETUP`/`RUNNING`/`SUMMARY`), the requested count, a per-quiz `showGrid`, the chosen `items: List<QuizItem>`, a `selectedSetId: Long?` (null = active-script character scope; else a saved practice set), `index`, `revealed`/`submitted` two-step flags, and `results` (auto quality-score headline by item key). `QuizItem` is a sealed interface (`key`/`prompt`/`answer`/`glyphs`/`subtitle`) with `CharQuizItem(character)` and `WordQuizItem(word, prompt)`, so a quiz can now draw **words** as well as characters. **Reading generalization (2026-07-09):** the quiz gained a `QuizKind` (Writing = write-from-memory vs Reading = recognition) and, for Reading, a `ReadingAnswerStyle` (multiple-choice / type-the-romaji / color-the-answer) plus a "test meaning?" flag; `setReadingAnswerStyle(style)` and `setReadingTestMeaning(bool)` configure it and `readingResult` carries the graded reading outcome. A Writing submit auto-scores (no self-grade — `quizSubmit(cells)` branches character → `recordAttempt`, word → `recordWordAttempt` per-kana average) and records a `PracticeSource.QUIZ` attempt; a Reading answer is checked with `RomajiMatch` (see §9) and rolled into `ReadingProgressRepository` — **neither has SRS/unlock side effects**. |
| `appMode: AppMode?` | Always relevant (2026-07-09). `AppMode {READING, WRITING}`; **`null` ⇒ show the opening screen** (no mode chosen). Seeded from + persisted via `SettingsRepository` (key `app_mode`) and preserved through every `rebuilt()`. `setAppMode(mode)` switches, `goToOpening()` resets to `null`. Drives the `PracticeScreen` `when {}` branch order (see §4/§5/§9). |
| `readingFlashcard: ReadingFlashcardState?`, `readingStats` | READING. `ReadingFlashcardState` holds a `List<ReadingCard>` + current index + a `revealed` flag for the reading flashcard drill (`openReadingFlashcardsForCharacters()`/`…ForWords()` and set-scoped variants build it; `flashcardReveal`/`flashcardNext`/`closeReadingFlashcards` drive it). `readingStats: Map<String, ReadingProgress>` mirrors the Writing side's `scoreStats` and tints/sorts the Library in Reading mode. |
| `practiceSets: List<PracticeSet>` | Always relevant. The user's saved practice sets, observed from `PracticeSetRepository.observeSets()` in `init` and carried through the `rebuilt()` helper (durable). Feeds the Library's **Sets tab** (2026-07-27, `SetsTabContent`) and the quiz source picker. |
| `pendingOpenSetsManager: Boolean` | LIBRARY (one-shot). Set true by `closeQuiz()`/`closeReadingFlashcards()` when the closing quiz/flip was launched from a set (`selectedSetId != null` / `fromSet`), so the library reopens on the **Sets tab** instead of the browse page — needed because the quiz overlay decomposes `LibraryScreen` and resets its local `selectedTab`. Consumed by a `LibraryScreen` `LaunchedEffect` that selects + persists tab 2 (`onSelectTab(2)`), then calls `consumeOpenSetsManager()` to clear it. |
| `freeWords: List<VocabularyWord>` | FREE (word-set practice). The FREE word-set queue when a `PracticeSetType.WORDS` set is launched via `practiceSet(id)`; `advanceToNextCharacter()` cycles it (modulo wrap), keeping `freeWord` pointed at the current item. Empty for single-character/word FREE. |
| `isGridEnabled`, `isGhostEnabled`, `isLibraryGridView`, `practiceLayout`, `activeScript`, `includeDiacritics`, `includeYoon`, `includeExtended`, `scoringMode`, `brushInkColor` | SETTINGS: seeded from and persisted via `SettingsRepository` (keys `grid_enabled`, `ghost_enabled`, `library_grid_view`, `practice_layout_span`, `active_script`, `diacritics_included`, `yoon_included`, `extended_included`, `scoring_mode`, `brush_ink_color`). |
| `lastScore`, `lastScoredCharacter`, `lastWordScores`, `lastWordAverage`, `scoreStats` | SCORING (§8): the most recent single-glyph `CharacterScore` and the glyph it was computed for (drives `ScoreFeedbackCard`); `lastWordScores: List<WordUnitScore>?` + `lastWordAverage: Int?` are the transient per-kana word breakdown (drives `WordScoreChips` above the canvas + `WordAverageCard` below); and `scoreStats: Map<String, CharacterScoreSummary>` for the Library's per-character readout. The single-glyph and word fields are mutually exclusive — each is set with the other nulled, and all four are reset wherever `lastScore` is (on every advance/retry/entry point, and when scoring is `OFF`). |
| derived: `currentCharacter`, `activeScriptCharacters` | `currentCharacter` resolves the current FREE glyph (free-deck position, or the current kana of a `freeWord` resolved back to a `HiraganaCharacter`); `activeScriptCharacters` is the script/opt-in-scoped view of the full deck. (The guided `sessionProgress`/`isCurrentWordNew`/`total` derivations were removed 2026-07-05.) |

This is the durable, screen-rotation-safe state (ViewModel-scoped). Collected in
`PracticeScreen` with `collectAsStateWithLifecycle()`.

### (b) `CanvasState` — drawing state, deliberately NOT in the ViewModel

`drawing/canvas/CanvasState.kt` is a `@Stable` snapshot-state holder created with plain
`remember { CanvasState() }` at the top of `PracticeScreen`. It owns everything on the
tracing canvas: `currentSession` (raw `DrawingSession` point data), `completedPaths`
(a `SnapshotStateList<Path>` of filled brush ribbons — the ink width is baked into the
geometry since the brush rework), `activePath` for the in-progress stroke,
`brushBaseWidthPx` (a plain non-snapshot var `TracingCanvas` refreshes from `20.dp`
× a cell-proportional brush scale every draw pass), and the derived `hasInk` flag that enables the Clear/grade buttons.
Every mutation reassigns snapshot state, so composables reading it recompose/redraw
automatically — no flows, no events. (See [docs/DRAWING.md](DRAWING.md) for the brush
pipeline.)

**Why it bypasses the ViewModel:** ink is transient, per-character, high-frequency
state. It is written on every `ACTION_MOVE` (dozens of times per second, including
batched historical samples) and its lifetime is exactly "one character on screen".
Routing that through `StateFlow` + ViewModel would add latency and boilerplate for
state that is intentionally thrown away on every character change. Instead the screen
clears it declaratively:

```kotlin
// PracticeScreen.kt
LaunchedEffect(uiState.canvasClearKey) {
    canvasState.clear()
}
```

Phase 3 replaced the old `LaunchedEffect(uiState.currentIndex, uiState.characters)` key
with `canvasClearKey` — a single `Int` the ViewModel increments on every advance,
regardless of which phase caused it (a review graded, a practice rep completed, a
vocabulary kana advanced, or a phase transition). One counter is simpler than tracking
each phase's own position field as a `LaunchedEffect` key.

**Consequences (accepted trade-offs):**

- Activity recreation loses the ink. Rotation is moot — `MainActivity` is
  portrait-locked (`android:screenOrientation="portrait"` in the manifest) — but other
  recreations (e.g. a system dark-mode toggle, process death) still drop in-progress
  drawing. `remember` is used, not `rememberSaveable`; serialising raw stroke points
  was judged not worth it for scratch ink.
- **Grading must snapshot synchronously.** Because grading bumps `canvasClearKey`, which
  immediately re-runs the `LaunchedEffect` and clears the canvas, the attempt must be
  captured *before* that happens. `PracticeViewModel.gradeCurrentCharacter` builds the
  full `PracticeAttempt` (stroke count, duration, timestamp) on the calling (UI) thread
  before launching any insert coroutine or touching `_uiState`. A second safety property
  backs this up: `CanvasState.clear()` *reassigns* a fresh `DrawingSession` rather than
  mutating the old one, so the session reference handed to the ViewModel stays intact
  even if anything were still reading it.
- **Double-grade guard (Phase 3).** Grade buttons only disable once the canvas clears on
  the next frame, so two near-simultaneous taps (e.g. two fingers on adjacent grade
  buttons) could both fire against the same ink before that happens. `gradeCurrentCharacter`
  guards against this with an identity check against the `DrawingSession` consumed by the
  most recent grade (`if (session === lastGradedSession) return`); since `CanvasState.clear()`
  reassigns rather than mutates, identity comparison exactly detects the race.
- `CanvasState` is **UI-thread-only** by contract (its KDoc: *"Must only be touched
  from the UI thread"*) — see §6.

`TracingCanvas` connects input to state: a `StrokeCapture` (remembered per
`CanvasState`) receives raw `MotionEvent`s via `pointerInteropFilter` and calls
`startStroke` / `extendStroke` / `finishStroke` / `cancelStroke` on the `CanvasState`.
Details of capture, palm rejection, pressure and smoothing are in
[docs/DRAWING.md](DRAWING.md).

## 4. Event flow

The user actions, plus the shared clear loop-back. **As of 2026-07-05 there is no graded
SRS session** — the `GRADE`/`gradeCurrentCharacter` routing and the `START NEXT SESSION`
flow shown below were removed with the study session; the app drives FREE practice through
the SUBMIT/RETRY/NEXT path. The removed flows are kept here (struck through in prose) only
so a reader tracing old commits recognizes them:

```
CLEAR (local-only; ViewModel never involved)
  User taps Clear
    └─► FreePracticeActionBar.onClear
          └─► CanvasState.clear()          resets session/paths → canvas recomposes,
                                           hasInk=false disables Clear + Submit buttons

GRADE — REMOVED 2026-07-05 (was: Again/Hard/Good/Easy for the SRS session phases,
  routing through gradeCurrentCharacter → gradeReview/gradePractice/gradeVocabulary and
  SrsScheduler). No grade bar exists anymore; FREE uses SUBMIT below.

RECORD (recordAttempt / recordWordAttempt — the scoring + persistence step)
  Called by freeSubmit (and the quiz submit):
    ├─ if scoringMode == OFF → clear the last-score fields and RETURN WITHOUT
    │    persisting (no attempt row is written)
    ├─ SYNCHRONOUS snapshot: build PracticeAttempt (character, romaji, grade,
    │    strokeCount, durationMs, clock.millis(), source)
    ├─ single glyph: score ONLY when cell != null — i.e. one glyph in one 1×1
    │    guide cell (true for the quiz, which forces PracticeLayout.SINGLE);
    │    multi-copy 2×2/3×3 never scores (all copies' strokes flatten into one
    │    list). Sets lastScore/lastScoredCharacter.
    ├─ word: recordWordAttempt attributes each stroke to its kana cell via
    │    StrokeAttribution.groupByCell (nearest cell center to the stroke
    │    centroid) using CanvasState.kanaCellRects (threaded through pendingCells),
    │    scores each KanaUnits unit geometry-only, persists one row per kana plus a
    │    word-level average row, and sets lastWordScores/lastWordAverage
    └─ persist via viewModelScope.launch { runCatching { … } }; failures LOGGED
         (Log.w, tag HiraganaSession)

SUBMIT / RETRY / NEXT (FREE two-step, mirrors the quiz)
  User taps Submit (writing sub-state, hasInk)
    └─► FreePracticeActionBar.onSubmit → PracticeViewModel.freeSubmit(session, cellRect, cells)
          scores + records a PracticeSource.FREE attempt with a neutral placeholder
          grade, KEEPS the ink (no canvasClearKey bump), sets freeSubmitted = true
          → feedback card shown. No-op outside FREE, without ink, if already
          submitted, or in a multi-copy repetition worksheet.
  User taps Again (reviewing sub-state)
    └─► FreePracticeActionBar.onRetry → PracticeViewModel.freeRetry()
          clears feedback + freeSubmitted, bumps canvasClearKey → rewrite the SAME
          character/word (deck position unchanged)
  User taps Next (reviewing sub-state; or Skip on an empty canvas while writing)
    └─► FreePracticeActionBar.onNext → PracticeViewModel.advanceToNextCharacter()
          modulo wrap over freeCharacters, resets freeSubmitted + clears feedback,
          bumps canvasClearKey (no-op outside FREE)
  A multi-copy 2×2/3×3 single-character worksheet (or scoring turned Off) can't be
  single-scored, so FreePracticeActionBar collapses the two-step to Clear · Next.

START NEXT SESSION — REMOVED 2026-07-05 (was: the COMPLETE summary's "Start next
  session" button calling startNextSession() to regenerate a StudySession). No
  guided sessions exist anymore.

LIBRARY (available from the guide panel's "Library" button)
  openLibrary()  → isLibraryOpen = true (overlay; FREE underneath untouched).
                   ALSO the FREE screen's hardware/gesture Back target (2026-07-05):
                   the Library is treated as "home", so Back from practice opens it
                   rather than exiting the app.
  goToOpening()  → appMode = null, isLibraryOpen = false. Wired to BOTH the
                   Library's BackHandler and its in-app Back arrow (onClose), in
                   either track, giving the chain Practice → Back → Library →
                   Back → opening screen → Back → exit. (closeLibrary(), which
                   dropped the Writing arrow onto the canvas, was removed
                   2026-10-16.)
  practiceCharacter(char) → FREE over the WHOLE deck, currentIndex at the pick
  practiceWord(word)      → FREE over the word's kana in order, freeWord = word
  (both close the overlay, bump canvasClearKey; record PracticeSource.FREE
  attempts only)

PRACTICE SETS (user-curated lists; CRUD from the Library's Sets tab, SetsTabContent)
  savePracticeSet(name, type, script, members) → RoomPracticeSetRepository.createSet
  updatePracticeSetMembers(id, members)        → replaceItems (edit membership)
  renamePracticeSet(id, name) / deletePracticeSet(id)
  (all fire-and-forget via viewModelScope; observeSets() re-emits into practiceSets)
  practiceSet(id) → FREE over the set's members: a CHARACTERS set walks the glyphs
                    (freeCharacters); a WORDS set walks the words (freeWords, freeWord
                    at the head) — same PracticeSource.FREE, no SRS/unlock side effects
  openQuiz(setId?) / setQuizSource(setId?) → seed/repoint the quiz at a set or the
                    active-script scope; private buildQuizPool resolves the ordered
                    List<QuizItem> for that source
  closeQuiz() → clears quiz; if the closing quiz's selectedSetId != null, raises the
                    pendingOpenSetsManager one-shot so the reopened library lands on the
                    Sets tab (LibraryScreen selects tab 2, then consumeOpenSetsManager)

LOOP-BACK (after any GRADE branch, or NEXT)
  canvasClearKey incremented in PracticeUiState
    └─► StateFlow emission → PracticeScreen recomposes
          └─► LaunchedEffect(uiState.canvasClearKey) re-runs
                └─► CanvasState.clear()    blank sheet for whatever is current now
```

The ordering in the SUBMIT/RECORD path is the whole point: by the time any coroutine write
runs and the canvas clears, the attempt object is already fully built and immutable.
Upstream of all of this is the launch load. **As of 2026-07-05 the guided SRS study session
has been removed** — there is no `startSession`/`startNextSession`/`startStudySession`, and
no `LessonGenerator`/`SrsScheduler`. A cold start calls `startPractice()` (from
`init`/`retrySession`), which loads the deck + words, seeds the built-in default practice
sets once (`seedDefaultSetsIfNeeded`, gated by the persisted `default_sets_seeded` flag),
and drops straight into `SessionPhase.FREE` on a random item from the last-used Library
section (`enterFreePracticeFromLastSection`, scoped by the persisted Study Scope). The four
scope setters (`setActiveScript`/`setIncludeDiacritics`/`setIncludeYoon`/`setIncludeExtended`)
re-enter free practice in place via the same helper. The Room SRS tables and
`LearningRepository`/`RoomLearningRepository` remain in the code as **dormant, unused**
schema (retained to avoid a destructive migration); `AppContainer.learningRepository` is
still constructed but no longer injected into the ViewModel.

## 5. Screen composition tree

Single screen, no navigation library — still a state-driven `when {}` in `PracticeScreen`.
Since the 2026-07-09 Reading/Writing split the branch order is:
**loading → error → OpeningScreen (`appMode == null`) → QuizScreen (`quiz != null`) →
ReadingFlashcardScreen (`readingFlashcard != null`) → LibraryScreen hub
(`appMode == READING || isLibraryOpen`) → PracticeContent (Writing free practice)**.
`LibraryScreen` is the shared hub for both modes: in READING it is the home surface
(browse / quiz / flashcards), in WRITING it is the overlay reached from practice.
Back-nav chain: a sub-screen (quiz, flashcards) → its hub → the opening screen ("home")
→ exit the app, wired with per-branch `BackHandler`s. (The `SessionPhase.COMPLETE`
summary-card branch was removed 2026-07-05 with the guided study session.)

```
MainActivity.setContent
└─ HiraganaTheme                          (presentation/theme/Theme.kt, Material 3)
   └─ PracticeScreen                      (presentation/screens/PracticeScreen.kt)
      ├─ remember { CanvasState() }       + LaunchedEffect(uiState.canvasClearKey) (see §3/§4)
      └─ Surface (fillMaxSize, colorScheme.background)
         ├─ isLoading                  → CenteredBox { CircularProgressIndicator }
         ├─ errorMessage != null       → CenteredBox { error Text + Retry button
         │                               (viewModel.retrySession) }
         ├─ appMode == null            → OpeningScreen (the READING|WRITING mode chooser;
         │                               setAppMode enters a mode. 2026-07-09)
         ├─ quiz != null               → QuizScreen (write-from-memory OR the reading
         │                               recognition quiz, per QuizKind; overlays the hub)
         ├─ readingFlashcard != null   → ReadingFlashcardScreen (reading recognition drill;
         │                               reveal/next/close. 2026-07-09)
         ├─ appMode == READING ||
         │  isLibraryOpen              → LibraryScreen (full-screen hub/overlay:
         │                               LazyColumn (or grid chart) of all characters
         │                               + all words, score-tinted (no status labels
         │                               since 2026-07-05), every row tappable →
         │                               practiceCharacter/practiceWord;
         │                               in-app arrow closes via onClose. NO
         │                               BackHandler — hardware Back exits the app)
         │  (the phase == COMPLETE → SessionCompleteContent branch was REMOVED
         │   2026-07-05 with the guided study session — there is no summary card)
         └─ else → PracticeContent (always FREE)
            (BackHandler { openLibrary } — hardware Back opens the Library instead
             of exiting; the Library is "home", see §4 LIBRARY)
            └─ Column (fillMaxSize)
               ├─ CharacterGuidePanel      weight(0.25f)   ── top 25%
               │  └─ Row
               │     ├─ Column weight(1f): glyph (displayLarge, 76.sp)
               │     │                     + either "romaji · character N of M"
               │     │                       or the word's kana (current glyph bolded)
               │     │                       + meaning line (for a freeWord)
               │     │                     + "Library" TextButton (onOpenLibrary)
               │     │                     (the guided sessionLabel line + "New word!"
               │     │                      badge were removed 2026-07-05)
               │     └─ Column: single char → AnimatedStrokePreview; whole word →
               │                WordStrokePreview (one engine per kana unit sharing
               │                ONE PlaybackControls row; squares sized to fit ALL
               │                units across the strip width). Both fall back to the
               │                static numbered StrokeOrderDiagram without timing data
               │                + "Stroke order · N" label
               ├─ Box                       weight(0.65f)   ── middle 65%
               │  ├─ TracingCanvas
               │  │  └─ Surface (rounded, PaperWhite) → Canvas
               │  │     draw order: practice grid → ghost glyph →
               │  │                 completed strokes → active stroke
               │  │     input: pointerInteropFilter → StrokeCapture
               │  └─ score overlay (advisory, floats over the sheet):
               │        whole word → WordScoreChips (TopCenter) + WordAverageCard
               │                     (BottomCenter), keyed on lastWordScores
               │        single glyph → ScoreFeedbackCard (TopCenter), keyed on lastScore
               └─ action bar               weight(0.10f)   ── bottom 10%
                  └─ FreePracticeActionBar (the only bar since 2026-07-05; two-step,
                        mirrors the quiz)
                        writing:   Clear · Submit  (Skip when canvas empty)
                        reviewing: Again · Next    (freeSubmitted == true)
                        repetition worksheet OR scoring Off → collapses to Clear · Next
                        (single-word practice repeats one sheet → no Next)
```

The 0.25 / 0.65 / 0.10 `Column` weights in `PracticeContent` are the layout contract of
the app; guide, canvas and actions always share the screen in that ratio — Phase 3 did
not touch it, only what's rendered inside each pane.

## 6. Threading model

| Work | Where it runs | Mechanism |
|---|---|---|
| Asset JSON read + parse (characters + vocabulary) | `Dispatchers.IO` | `HiraganaAssetDataSource.loadCharacters()` / `.loadVocabulary()` wrap everything in `withContext(Dispatchers.IO)`; `org.json` parsing happens there too. |
| Character cache / vocabulary word cache | caller's context, serialised | `AssetCharacterRepository` and (Phase 3) `RoomVocabularyRepository.getWords()` each guard an in-memory cache with a `kotlinx.coroutines.sync.Mutex` — concurrent first calls load once. |
| `startPractice()` / freeSubmit / score / persist coroutines | `viewModelScope` (Main dispatcher) | Launched in `PracticeViewModel`; the suspend hops (`withContext(IO)`, Room) move real work off the main thread. (The `startSession()`/`LessonGenerator.generate(...)` guided-session path was removed 2026-07-05.) |
| Room writes (practice_history insert, practice_set create/replace) | Room's own executors | All DAO methods are `suspend fun`; Room dispatches internally. Fire-and-forget inside `viewModelScope.launch { runCatching { … } }` — no result observed, but failures are logged via `Log.w(TAG, ...)` (tag `HiraganaSession`) instead of silently swallowed. (The review_cards/character_progress/vocabulary_progress writes were removed with the study session; those tables are now dormant.) |
| MotionEvent capture, stroke building, path smoothing | UI thread | `pointerInteropFilter` delivers events on the main thread; `StrokeCapture` and `StrokeRenderer` run inline in the handler. |
| `CanvasState` mutations | **UI thread only, by contract** | Documented in its KDoc. Snapshot state is technically thread-safe, but the class assumes single-threaded mutation (e.g. `startStroke`'s defensive commit, `activeStroke` is a plain non-snapshot field). All current call sites honour this: `StrokeCapture` (input), `PracticeScreen` button lambdas and `LaunchedEffect` (composition). |
| Stroke-order playback (Phase 2) | UI thread (main) | `StrokeAnimationEngine.run()` is the single suspend driver, launched from a `LaunchedEffect` in `AnimatedStrokePreview`; controls are non-suspend `onClick` mutations of desired state. Outputs are snapshot state read inside the preview's draw lambda (draw invalidation only, no recomposition per frame). |
| `SrsScheduler` / `LessonGenerator` | — | Removed 2026-07-05. (Were pure, synchronous, clock-injected — no threading requirement.) |

Nothing else in the app creates threads or dispatchers.

## 7. Phase 2: animated stroke-order demonstrations (as built)

Implemented 2026-07-03 as a purely additive extension (contract:
[docs/PHASE2-DESIGN.md](PHASE2-DESIGN.md)); adversarially reviewed and review-fixed
2026-07-04. `PracticeScreen`, `PracticeViewModel`, `CanvasState` and the whole tracing
pipeline were untouched — Phase 2 is confined to the guide area and the data layer.

- **Data**: each asset JSON carries a generated `strokeAnimations` array (timed pen
  positions per stroke, viewBox space, ms clocks per stroke) parsed into
  `domain/model/StrokeDefinition.kt` (`StrokeFrame`/`StrokeDefinition`) and exposed as
  `HiraganaCharacter.strokeAnimations` (default `emptyList()`). Parsing is
  all-or-nothing per character and consistency-checked against `strokes[]` (count
  parity, orders exactly 1..N, no empty point lists); malformed or inconsistent data
  is voided with a `Log.w` and the UI falls back to the static diagram. Timing data is
  derived offline from `strokes[]` by `tools/generate_stroke_animations.py` (~160
  viewBox-units/s, min 180 ms/stroke, ease-in/out) — never hand-edited. The
  hand-authored timed points route was chosen over runtime `PathMeasure` sampling so
  rhythm is data, not code.
- **Engine** (`drawing/animation/StrokeAnimationEngine.kt`): a `@Stable`,
  UI-thread-only snapshot-state holder mirroring the `CanvasState` pattern. Non-suspend
  controls (`play`/`pause`/`replay`/`setSpeed` 0.25–4×/`setAutoRepeat`/`reset`) mutate
  desired state; one `suspend run()` driver (launched from a `LaunchedEffect`) owns all
  suspend work via `Animatable` + LinearEasing, with epoch-guarded output writes so
  stale frames after a replay/reset can never publish. Outputs: `completedPaths`,
  `activePath`, `penPosition` (null = pen lifted), `progress`, `playbackState`.
  `StrokeFramePath` converts timed frames to partial/full `Path`s with the same
  midpoint quadratic-bézier smoothing as `StrokeRenderer`.
- **UI** (`presentation/components/AnimatedStrokePreview.kt`): square preview canvas
  (faint full-character guide → completed → active → pen dot) over a compact controls
  row; auto-plays on character change (engine `remember`ed per glyph); falls back to
  `StrokeOrderDiagram` when `strokeAnimations` is empty. `CharacterGuidePanel` passes a
  height-only constraint — the square lives on the canvas, so the controls row keeps
  its intrinsic width on short screens (2026-07-04 review fix). Nothing here installs
  a pointer handler except the buttons, so tracing below is never blocked.

Remaining seams from Phase 2, now largely closed by Phase 3:

- **`PracticeRepository.observeAttempts()` — still implemented, still no direct UI
  collector.** `HiraganaCharacter.frequency` drives browse/study order; scored attempts
  persist to `practice_history` and roll up into `scoreStats` for the Library tinting.
  (The SRS scheduler that once consumed grade history was removed 2026-07-05; the `Flow`
  remains unread.)
- **`strokeImage` reserved field.** Each character JSON may name a raster stroke-order
  image; the field is parsed into `HiraganaCharacter.strokeImage` but never read, and
  the referenced PNGs **do not exist in assets** (known wart / reserved for later
  phases — neither Phase 2 nor Phase 3 used it). See [docs/DATA.md](DATA.md).

Other honest notes a future session should know (details in the docs referenced):
`svgPath` duplicates `strokes.joinToString(" ")` when absent from JSON (and is now
written as exactly that join by `tools/import_kanjivg.py`); `res/values/themes.xml`
hardcodes light status/nav bars (`#FBF8F4`, `windowLightStatusBar=true`) even though
`HiraganaTheme` defines a dark color scheme — and now also sets
`android:forceDarkAllowed="false"` (`tools:targetApi="q"`), so the light-only paper
design opts out of system force-dark inversion (ignored below API 29, where force-dark
does not exist); the directory is not a git repository.
Phase 3 closed the "zero tests" and `exportSchema = false` warts — see §8 below.

## 8. Phase 3: Smart Learning System (as built)

Implemented, built, and unit-tested 2026-07-04 (contract: [docs/PHASE3-DESIGN.md](PHASE3-DESIGN.md),
including its §11 as-built amendments); adversarially reviewed and review-fixed the same
day (2 agents, 0 P0, both verdicts ship). `drawing/`, `TracingCanvas`, `CanvasState`,
`StrokeAnimationEngine`, and `AnimatedStrokePreview` were **not touched** — Phase 3 adds a
learning layer on top of the existing drawing/animation pipeline and turns the flat deck
walk into a generated session.

- **Domain model extensions**: `Grade` grew from `{EASY, OKAY, HARD}` to
  `{AGAIN, HARD, GOOD, EASY}` (worst-to-best button order); `HiraganaCharacter` gained
  `id: String` (the asset basename — stable primary key for the new progress tables);
  `PracticeAttempt` gained `source: PracticeSource` (`REVIEW`/`NEW`/`VOCAB`/`FREE`,
  defined in the same file). New models: `ReviewCard` (SRS state), `VocabularyWord`
  (+ a `fromKana` companion that derives `requiredCharacters` from `kana` — the single
  place that derivation rule lives), `StudySession` (the generator's output, with a
  derived `isEmpty`), `CharacterProgress`, `VocabularyProgress`.
- **`domain/learning/SrsScheduler.kt`** — a stateless `object` implementing simplified
  SM-2 transitions (docs/PHASE3-DESIGN.md §2): `newCard(characterId, todayStartMillis)`
  and `next(card, grade, todayStartMillis)`. Constants: initial ease `1.7f`, ease step
  `±0.15f` (floor `1.3f`, cap `2.5f`), Easy's fixed interval multiplier `2.5f`. AGAIN
  resets repetitions/interval to 0 (due today, meant to be requeued within the session);
  HARD collapses the interval to a flat 1 day (even for a mature card — an accepted
  literal reading of the simplified spec) and nudges ease down; GOOD grows the interval
  by the card's own ease; EASY grows it by a fixed 2.5× and nudges ease up. No Android
  imports, no clock reads — every date computation takes `todayStartMillis: Long`.
- **`domain/learning/LessonGenerator.kt`** — a pure `class` (constructor defaults:
  `maxReviewsPerSession=10`, `newCharBacklogLimit=5`, `practiceRepetitions=3`,
  `vocabReinforcementCount=2`) whose `generate(...)` builds one `StudySession` from
  four pure computations (docs/PHASE3-DESIGN.md §4): (1) due reviews — cards with
  `nextReviewDate < todayStartMillis + 1 day` (strict, per the §11 amendment), most-overdue
  first, capped; (2) the "prevent advancing before mastery" gate — a new character is
  offered only when the pre-cap due backlog is within `newCharBacklogLimit` AND every
  existing card has `repetitions >= 1`; (3) practice repetitions for that new character;
  (4) vocabulary — newly-unlocked words (by the "every required character has
  `repetitions >= 1`" rule) plus up to `vocabReinforcementCount` stale-first reinforcement
  picks. **Orphaned `ReviewCard`s** (a `characterId` matching no current deck character)
  are filtered out before any of the above run, so they can never consume review slots,
  inflate the gate's backlog count, or hold the gate closed forever. The generator reads
  only its parameters and writes nothing — crash-safe resumability comes for free because
  the ViewModel persists each step's outcome as it happens.
- **Persistence — Room v1 → v2 (and later → v3, → v4, → v5, → v6)**: `HiraganaDatabase` is now
  `version = 6`, `exportSchema = true` (schema history committed at `app/schemas/`).
  `MIGRATION_5_6` additively adds `practice_sets.updatedAt` (2026-07-24, the sets
  manager's newest/oldest sort — seeded from `createdAt`, re-stamped on rename/member edits);
  `MIGRATION_4_5` additively adds the reading-progress table (2026-07-09, §9);
  `MIGRATION_3_4` additively creates the two practice-set tables (§8-scoring). `MIGRATION_1_2` (a real `Migration(1, 2)`, no destructive
  fallback) renames `practice_attempts` → `practice_history`, adds its
  `source TEXT NOT NULL DEFAULT 'FREE'` column, and creates `review_cards` /
  `character_progress` / `vocabulary_progress` (schemas in docs/DATA.md §5); the later
  `MIGRATION_2_3` adds three nullable `REAL` handwriting-score columns
  (`form_score`/`fit_score`/`recognition_score`) to `practice_history` — purely
  additive (§8-scoring below). Three new entity/DAO pairs follow `PracticeAttemptDao`'s
  style. New `LearningRepository` and `VocabularyRepository` interfaces are implemented by
  `RoomLearningRepository` and `RoomVocabularyRepository` (the latter Mutex-caches parsed
  `vocabulary.json` words the same way `AssetCharacterRepository` caches characters, and
  swallows a malformed vocabulary asset into an empty list with a logged warning rather
  than crashing character load). `AssetCharacterRepository` now sorts its cached list by
  `frequency` ascending — study order is the frequency rank, not `index.json`'s listing
  order (today the two coincide). `RoomPracticeRepository`'s legacy-grade fallback moved
  from `Grade.OKAY` to `Grade.GOOD` (stored `"OKAY"` rows decode to `GOOD`); an
  unrecognised stored `source` decodes to `PracticeSource.FREE`.
- **UI — session state machine** *(REMOVED 2026-07-05 — historical)*: Phase 3
  `PracticeViewModel` drove a `SessionPhase` machine
  (`LOADING → REVIEWS → NEW_CHARACTER → PRACTICE → VOCABULARY → COMPLETE → FREE`) with a
  `PracticeActionBar` grade bar (`Clear · Again · Hard · Good · Easy`) and a
  session-labelled `CharacterGuidePanel`. All of that was deleted on 2026-07-05;
  `SessionPhase` is now just `LOADING`/`FREE` and the only bar is `FreePracticeActionBar`
  (see §3/§4). `Color.kt`'s `GradeAgainContainer`/`GradeAgainContent` (the worst-to-best
  grade ramp) remain, unused by the removed grade bar.
- **Tests**: `app/src/test/java/com/hive/hiragana/domain/learning/`
  (`SrsSchedulerTest`, `LessonGeneratorTest`, `VocabularyUnlockTest`) were the project's
  first tests (45 at Phase 3) but were **deleted with the study session on 2026-07-05**;
  `ScriptScopeTest` was trimmed to its pure filter/gating cases and `PracticeViewModelTest`
  lost its session tests and gained default-set-seeding tests. The suite is now
  **84 JUnit4 tests**, all green (`testDebugUnitTest`) — pure-domain suites for the
  katakana/opt-in seam and the scoring/chart work (see §8-scoring) plus
  `PracticeViewModelTest`.

### Handwriting-quality scoring, quiz, and the Library chart (post-Phase-3, additive)

These later features layer on without touching the drawing pipeline or the learning
engine:

- **Handwriting-quality scoring** (advisory). `domain/scoring/` is pure and Android-free
  (same discipline as `SrsScheduler`): `GeometryScorer` compares the user's drawn
  stroke polylines against the reference glyph and produces a `GeometryScore` (FORM +
  optional FIT dials, stroke-count check, coaching tags); `CharacterScore.combine(...)`
  blends that with an optional recognition verdict under the active `ScoringMode`
  (`OFF`/`FORM`/`FIT`/`BOTH`), where recognition acts as a legibility **gate**, not the
  headline; `CharacterScoreSummary` rolls history up per glyph. Recognition itself is
  `domain/repository/HandwritingRecognizer` implemented by
  `data/recognition/MlKitHandwritingRecognizer` (ML Kit Digital Ink, model downloaded on
  demand, degrades to `RecognitionResult.UNAVAILABLE`). The ViewModel calls
  `GeometryScorer.score` + `CharacterScore.combine` synchronously at grade time, exposes
  `lastScore`/`lastScoredCharacter`/`scoreStats` in `PracticeUiState`, renders
  `presentation/components/ScoreFeedbackCard.kt`, and persists the dials onto the
  `practice_history` row via the v3 columns — **advisory only, never feeding SRS**.
- **Scoring-UX batch** (most recent, layered on the scorer above):
  - **Single-glyph scoring gated to a lone 1×1 cell.** `recordAttempt` scores only when
    its `cell != null` — exactly one glyph in one 1×1 guide cell (true for the quiz,
    which forces `PracticeLayout.SINGLE`). Multi-copy 2×2/3×3 flatten every copy's
    strokes into one list and would score as a stroke-count mismatch, so they never score.
  - **Per-cell word scoring.** `recordWordAttempt` attributes each stroke to its kana cell
    via the pure/tested `domain/scoring/StrokeAttribution.groupByCell` (nearest cell
    **center** to the stroke centroid — a stroke in the gap between cells still counts
    toward the closer kana) using `CanvasState.kanaCellRects` (threaded from the screen
    through the new `cells` param on `gradeCurrentCharacter`/`freeSubmit` into the
    ViewModel's `pendingCells`). It scores each `KanaUnits` unit geometry-only, persists
    one `practice_history` row per kana **plus** a word-level average row, and sets the
    transient `PracticeUiState.lastWordScores: List<WordUnitScore>?` / `lastWordAverage:
    Int?` that drive `WordScoreChips` (above the canvas) + `WordAverageCard` (below). Both
    are reset wherever `lastScore` is, and are mutually exclusive with the single-glyph
    `lastScore`/`lastScoredCharacter`.
  - **Grading Off ⇒ no recording.** When `scoringMode == ScoringMode.OFF`, both
    `recordAttempt` and `recordWordAttempt` early-return **without persisting any row**
    (FREE and the SRS session phases alike). SRS scheduling + vocab unlock still run
    because their callers do them independently — only the score history is dropped.
  - **FREE two-step Submit→review flow.** `FreePracticeActionBar` (in `PracticeActionBar.kt`)
    replaces the old grade buttons in FREE: writing → **Submit** (`freeSubmit` scores,
    keeps the ink, no `canvasClearKey` bump, sets `freeSubmitted`) → reviewing → **Again**
    (`freeRetry`, rewrite the same char/word) / **Next** (`advanceToNextCharacter`, resets
    `freeSubmitted` + clears feedback). Empty canvas shows **Skip**; a multi-copy
    worksheet or scoring-Off collapses to Clear · Next; single-word practice has no Next.
    The FREE branch of `gradeCurrentCharacter` is now a no-op.
  - **Word stroke-order previews.** `WordStrokePreview` (in `AnimatedStrokePreview.kt`)
    renders one `StrokeAnimationEngine` per word unit (from `wordUnitCharacters`) sharing
    ONE `PlaybackControls` row — `PlaybackControls` now takes a `List<StrokeAnimationEngine>`
    and broadcasts to all; a shared `StrokeCanvas` composable was extracted. A
    `BoxWithConstraints` sizes every kana square to `min(stripHeight, width/unitCount)`
    so a long word (3+ kana) shows every demo at once instead of clipping trailing kana.
  - **Library score coloring.** `presentation/theme/ScoreColors.kt`'s
    `scoreSpectrumColor(Int)` maps 0..100 onto a red→green tint; `LibraryScreen` tints
    chart squares / word cards / list rows by each glyph's `scoreStats` average
    (`avgForm ?: avgFit`) with the score in the top-right corner, and its app bar was
    reordered (Search leftmost, view toggle persists during search, `BasicTextField`
    search field).
- **Write-from-memory quiz.** `presentation/screens/QuizScreen.kt` + a `QuizState`
  (`QuizStage` `SETUP`/`RUNNING`/`SUMMARY`) held in `PracticeUiState.quiz`, launched from
  the Library, overlaying whatever phase is active. Each submitted item is
  auto-scored (no self-grade) and recorded as `PracticeSource.QUIZ` with **no SRS or
  unlock side effects**. The quiz was later **generalized to words**: `QuizState.characters`
  became `items: List<QuizItem>` plus a `selectedSetId: Long?`; `QuizItem` is a sealed
  interface (`key`/`prompt`/`answer`/`glyphs`/`subtitle`) with `CharQuizItem` (romaji
  prompt → single glyph → `recordAttempt`) and `WordQuizItem` (pronunciation prompt →
  per-kana glyphs → `recordWordAttempt` average). A source-picker dropdown lets the quiz
  draw from a saved practice set instead of the active-script scope (`setQuizSource`,
  `openQuiz(setId?)`, private `buildQuizPool`), and `QuizScreen` renders word prompts with
  the same per-kana `WordScoreChips`/`WordAverageCard` feedback the canvas uses.
- **Practice sets** (user-curated lists). `domain/model/PracticeSet.kt` —
  `PracticeSet(id, name, type: PracticeSetType {CHARACTERS, WORDS}, script: Script,
  members: List<String>)` — is an explicit, hand-picked membership list (glyph strings for
  a CHARACTERS set, word kana for a WORDS set; script-unique keys), **deliberately distinct
  from the declarative Study-scope opt-ins** (a filter config, not a list). It persists to
  two additive Room v4 tables via `MIGRATION_3_4`: `practice_sets` (id PK autoincrement,
  name, type, script, createdAt) and `practice_set_items` (itemId PK, setId FK →
  `practice_sets` `ON DELETE CASCADE`, value, position; index on setId). Entities in
  `data/local/PracticeSetEntity.kt` (`PracticeSetEntity` + `PracticeSetItemEntity` +
  `PracticeSetWithItems` `@Relation`), DAO `data/local/PracticeSetDao.kt` (`observeSetsWithItems`
  Flow, `@Transaction` `createSet`/`replaceItems`, `renameSet`/`deleteSet`), interface
  `domain/repository/PracticeSetRepository.kt`, impl `data/repository/RoomPracticeSetRepository.kt`,
  wired as `AppContainer.practiceSetRepository` (§2). The ViewModel observes `observeSets()`
  into `PracticeUiState.practiceSets` (carried through `rebuilt()`), CRUDs via
  `savePracticeSet`/`updatePracticeSetMembers`/`renamePracticeSet`/`deletePracticeSet`, and
  launches a set into FREE via `practiceSet(id)` (a WORDS set drives the new
  `PracticeUiState.freeWords` queue) or into the quiz via `openQuiz(setId)`. **Sets are a
  first-class Library tab as of 2026-07-27** (`SetsTabContent`, a third tab beside
  Characters and Words — see §9's Library tabs note): script-scoped, driven by the shared
  top-bar search, with an inline header (count + persisted `SetSortMenu` + a "New"
  `DropdownMenu` picking Character/Word set). It replaced the old Study-scope-sheet "Add
  practice set" / "See sets" buttons and the full-screen `SetsManagerContent` overlay. The
  selection mode used to build/edit a set (drag-to-range-select via `detectDragGestures` +
  edge auto-scroll through `LazyListState.scrollBy`) and its name dialog still live in
  `LibraryScreen.kt`. The Study-scope sheet is `skipPartiallyExpanded = true` with its
  content `heightIn(max = 90% screen)` + `verticalScroll` (fit-content with a scroll
  fallback on short screens). Follow-up polish, all still in `LibraryScreen.kt`:
  `exitSelection()` now **always** returns to the Sets tab (save, cancel ✕, or Back
  alike, new or edited set — it selects + persists tab 2); the Sets tab shows each set's average via a
  `setAverage(set, scoreStats)` helper (mean of members' `scoreStats[member].avgScore()`
  = `avgForm ?: avgFit` over scored members, null when none) rendered on `SetManagerRow`
  with the same `scoreSpectrumColor` tint + corner number as char/word cards (no new
  storage — reuses the per-glyph `scoreStats`, so scoring-off runs never contribute); the
  selection screen (`SelectionModeContent`) gained a list⇄grid toggle (extracted
  `SelectableList` + new `SelectableGrid`/`SelectableGridCell`, both keeping tap-toggle
  and drag-range-select — the grid maps pointer (x,y)→item index via
  `LazyGridState.layoutInfo` with edge auto-scroll); and the manager gained a sort menu
  (`SetSortMenu` + `SetSort` enum `{NAME_ASC, NAME_DESC, SCORE_DESC, SCORE_ASC}`, unscored
  sets sorting last, hand-drawn `drawSortGlyph` icon).
- **Library chart view.** `domain/model/KanaChart.kt` (`KanaChartBuilder` +
  `TableSection`/`FlowSection`/`TableRow`) builds a gojūon-style chart from the scoped
  deck; the Library toggles between the vertical list and this grid, persisted as the
  `library_grid_view` setting (`PracticeUiState.isLibraryGridView`).

As-built deviations from the original contract text (docs/PHASE3-DESIGN.md §11, from the
adversarial review): the due-window comparison was documented as `<=` but the code (and
now the doc) is the stricter `<`; orphaned cards are excluded from the gate and review
slots; `PracticeUiState.newlyUnlockedKana` distinguishes fresh unlocks from reinforcement
picks for the "New word!" badge and the COMPLETE summary; a double-grade guard
(`DrawingSession` identity check) was added to `gradeCurrentCharacter`; "learned" is
consistently `repetitions >= 1` everywhere (FREE deck filter, `newCharacterLearned`,
"Keep practicing" visibility); `requiredCharacters` derivation lives in
`VocabularyWord.fromKana`. Accepted quirks (by design, not fixed): Hard's flat 1-day
collapse even for mature cards; practice repetitions restart from rep 1 after process
death mid-introduction. Unlike the original Phase 3 write-up, the app **has since been
run on a device** (the katakana work was device-verified on the Pixel 7 — see the scope
note above); the v2→v3 score migration is likewise additive and low-risk (nullable
columns only).

## 9. Reading & Writing modes (2026-07-09, additive)

The app split into two top-level modes. A cold start opens on a **mode chooser**; from
there the user enters **Writing** (the handwriting practice described in §1–8 — Writing
*is* the pre-split app) or **Reading** (recognition drills). This is a purely additive
layer: the drawing pipeline, scoring, and practice-set machinery are untouched.

- **`AppMode` + the opening screen.** `domain/model/AppMode.kt` defines
  `enum class AppMode { READING, WRITING }`. It rides `PracticeUiState.appMode: AppMode?`
  where **`null` means "show the opening screen"** (no mode chosen yet). It is persisted
  via `SettingsRepository`/`PrefsSettingsRepository` (key `app_mode`) and — like
  `activeScript`/`brushInkColor` — preserved through every `PracticeViewModel.rebuilt()`
  reconstruction. `setAppMode(mode)` persists + switches; `goToOpening()` resets it to
  `null` (back to "home"). New composables: `presentation/screens/OpeningScreen.kt` (the
  chooser) and `presentation/screens/ReadingFlashcardScreen.kt` (the reading drill).

- **Navigation stays a state-driven `when {}`** in `PracticeScreen` — still **no
  navigation library**. Branch order (see §5): loading → error → `OpeningScreen`
  (`appMode == null`) → `QuizScreen` (`quiz != null`) → `ReadingFlashcardScreen`
  (`readingFlashcard != null`) → `LibraryScreen` hub (`appMode == READING || isLibraryOpen`)
  → `PracticeContent` (Writing free practice). `LibraryScreen` is the shared hub: the
  home surface in Reading, the practice overlay in Writing. Back-nav chain: a sub-screen
  (quiz, flashcards) → its hub → the opening screen ("home") → exit the app, via
  per-branch `BackHandler`s.

- **Reading flashcards.** `PracticeUiState.readingFlashcard: ReadingFlashcardState?` holds
  a deck of `ReadingCard`s + the current index + a `revealed` flag.
  `openReadingFlashcardsForCharacters()` / `openReadingFlashcardsForWords()` (and the
  set-scoped variants) build the deck from the active scope; `flashcardReveal()` flips the
  card, `flashcardNext()` advances, `closeReadingFlashcards()` returns to the hub.

- **Reading quiz (generalized quiz).** The existing `QuizState` grew a `QuizKind`
  (Writing vs Reading) and, for Reading, a `ReadingAnswerStyle` (multiple-choice /
  type-the-romaji / color-the-answer) + a "test meaning?" flag. `setReadingAnswerStyle`
  and `setReadingTestMeaning` configure it; `readingResult` carries the graded outcome.
  Reading answers are checked with `RomajiMatch` (below), never handwriting-scored.

- **Reading progress persistence — Room v5.** `domain/repository/ReadingProgressRepository`
  (interface) + `data/repository/RoomReadingProgressRepository` (impl) record reading
  outcomes with an **ensure-then-increment** upsert (insert the row if absent, then bump
  the per-style counters) and expose an `observe…(): Flow` for the Library tint. Wired as
  `AppContainer.readingProgressRepository` (§2) and threaded through
  `PracticeViewModel.Factory`. This pushes `HiraganaDatabase` to **v5** with an additive
  migration adding a reading-progress table (no existing table touched — see
  [docs/DATA.md](DATA.md)). `PracticeUiState.readingStats` mirrors the Writing side's
  `scoreStats`.

- **`ReadingProgress` + `RomajiMatch` models.** `domain/model/ReadingProgress.kt` records
  per-glyph reading mastery with `mcqPercent` / `typePercent` / `colorPercent` (one
  accuracy per answer style). `domain/model/RomajiMatch.kt` centralizes **lenient romaji
  equality** (whitespace- and case-insensitive, common alternate spellings) so a typed
  answer matches its expected reading the same way everywhere — covered by the new
  `RomajiMatchTest`.

- **Writing ghost is now always handwritten.** With the split, the old user-facing
  **Printed / Handwritten** ghost toggle was removed and its Study-scope-sheet slot now
  holds the **Reading | Writing** mode toggle. Writing forces `CharacterForm.HANDWRITTEN`,
  so the ghost is always the KanjiVG stroke-derived glyph (not the font-rendered print
  form) throughout Writing mode.

- **Mode-aware Library.** `LibraryScreen` tints and sorts by the *active mode's* stats —
  `writingScoreStats` (the old `scoreStats`) in Writing, `readingStats` in Reading.
  `ItemSort` gained MC / TYPE variants and a `List.sortedByReading` companion so the
  browse list can order by reading accuracy the way `sortedByScore` orders by handwriting
  score.

- **Keyboard handling.** Because the type-the-romaji reading style needs text input,
  `MainActivity` declares `android:windowSoftInputMode="adjustResize"` (manifest) so the
  soft keyboard **resizes** the window rather than panning it.

## 10. File responsibilities (representative, not exhaustive)

Paths relative to `app/src/main/java/com/hive/hiragana/` unless noted. This table
covers the Phase 1–3 core in full; the later additive files (the katakana/opt-in seam,
handwriting scoring, quiz, Library chart, settings) are listed as a group at the end
rather than exhaustively re-inventoried. **Note (2026-07-05):** the rows below for
`domain/learning/SrsScheduler.kt`, `domain/learning/LessonGenerator.kt`,
`domain/model/StudySession.kt`, `presentation/components/PracticeActionBar.kt`, and the
`domain/learning` test files describe files that **have since been DELETED**;
`FreePracticeActionBar` now lives in its own file and `domain/model/DefaultPracticeSets.kt`
was added. The test suite is now **84 tests** (the `domain/learning` files removed), plus
the new `RomajiMatchTest` from the Reading/Writing split. The 2026-07-09 Reading/Writing
files (`AppMode`/`RomajiMatch`/`ReadingProgress`, the two new screens, and the reading
repository) are listed in the grouped **Later additive files** table at the end rather
than added to the counted `domain`/`data`/`presentation` core tables below.

### Root (2)

| File | Responsibility |
|---|---|
| `HiraganaApplication.kt` | `Application` subclass exposing `container`; also defines `AppContainer` (lazy Room DB + `HiraganaAssetDataSource` + all repositories, incl. `practiceSetRepository`). |
| `MainActivity.kt` | Single `ComponentActivity`; `setContent` → `HiraganaTheme` → `PracticeScreen` with `PracticeViewModel.Factory`. Declared `android:windowSoftInputMode="adjustResize"` (manifest) so the soft keyboard for the type-the-romaji reading answer resizes the window instead of panning it. |

### `domain` (17)

| File | Responsibility |
|---|---|
| `domain/model/HiraganaCharacter.kt` | Immutable character model: `id` (asset basename, Phase 3 progress-table key), glyph, romaji, frequency, `svgPath`, reserved `strokeImage`, per-stroke SVG strings, `viewBox` (default 100f), `strokeAnimations` (default empty), derived `strokeCount`. |
| `domain/model/StrokeDefinition.kt` | Phase 2 timing types: `StrokeFrame(x, y, time)` and `StrokeDefinition(order, points)`. |
| `domain/model/Grade.kt` | `enum class Grade { AGAIN, HARD, GOOD, EASY }` with display `label`, in worst-to-best button order. |
| `domain/model/PracticeAttempt.kt` | One graded attempt: character, romaji, grade, stroke count, duration, timestamp, `source: PracticeSource`. Also defines `enum class PracticeSource { REVIEW, NEW, VOCAB, FREE }`. |
| `domain/model/ReviewCard.kt` | Phase 3 SRS state: `characterId`, `repetitions`, `intervalDays`, `easeFactor`, `nextReviewDate`. |
| `domain/model/VocabularyWord.kt` | Phase 3 vocabulary model: `kana`, `meaning`, `requiredCharacters`; companion `fromKana(kana, meaning)` derives the set from `kana`'s distinct characters. |
| `domain/model/StudySession.kt` | Phase 3 `LessonGenerator` output: `reviews`, `newCharacter`, `practiceRepetitions`, `vocabulary`, derived `isEmpty`. |
| `domain/model/CharacterProgress.kt` | Phase 3 introduction/trace-count stats per character. |
| `domain/model/VocabularyProgress.kt` | Phase 3 unlock/practice stats per word. |
| `domain/repository/CharacterRepository.kt` | `suspend fun getCharacters(): List<HiraganaCharacter>` — deck sorted by frequency ascending. |
| `domain/repository/PracticeRepository.kt` | `suspend recordAttempt(…)` + `observeAttempts(): Flow<…>` (still no direct UI collector). |
| `domain/repository/LearningRepository.kt` | Phase 3: review-card CRUD + character-progress read/write (`getReviewCards`, `upsertReviewCard`, `getCharacterProgress`, `markIntroduced`, `incrementTraces`). |
| `domain/repository/VocabularyRepository.kt` | Phase 3: `getWords`, `getProgress`, `unlock`, `recordPractice`. |
| `domain/model/PracticeSet.kt` | User-curated list model: `PracticeSet(id, name, type: PracticeSetType {CHARACTERS, WORDS}, script, members: List<String>)` — glyph or kana keys; distinct from Study-scope opt-ins. |
| `domain/repository/PracticeSetRepository.kt` | Practice-set persistence iface: `observeSets(): Flow`, `createSet`, `renameSet`, `deleteSet`, `updateMembers`. |
| `domain/learning/SrsScheduler.kt` | Phase 3: pure `object` — SM-2-style grade transitions, clock-injected via `todayStartMillis` parameter. |
| `domain/learning/LessonGenerator.kt` | Phase 3: pure `class` — assembles reviews + gated new character + vocabulary into a `StudySession`; excludes orphaned cards. |

### `data` (17)

| File | Responsibility |
|---|---|
| `data/assets/HiraganaAssetDataSource.kt` | Reads `assets/hiragana/index.json` + per-character JSON, and (Phase 3) `vocabulary.json`, with platform `org.json` on `Dispatchers.IO`. Character parsing voids-and-logs on malformed/inconsistent `strokeAnimations`; `loadVocabulary()` throws on malformed JSON (caller decides fallback behavior). |
| `data/repository/AssetCharacterRepository.kt` | `CharacterRepository` impl; loads once, sorts by `frequency` ascending, caches in memory behind a `Mutex`. |
| `data/repository/RoomPracticeRepository.kt` | `PracticeRepository` impl; entity↔domain mappers (`Grade.valueOf` falls back to `GOOD`; `PracticeSource.valueOf` falls back to `FREE`). |
| `data/repository/RoomLearningRepository.kt` | Phase 3: `LearningRepository` impl over `ReviewCardDao`/`CharacterProgressDao`; failures are not swallowed here — callers decide. |
| `data/repository/RoomVocabularyRepository.kt` | Phase 3: `VocabularyRepository` impl; Mutex-caches `HiraganaAssetDataSource.loadVocabulary()` (malformed asset → logged, empty list) plus `VocabularyProgressDao` reads/writes. |
| `data/local/PracticeAttemptEntity.kt` | Room `@Entity` for table `practice_history` (renamed from `practice_attempts` in v2; grade/source stored as enum-name `String`, `source` defaulting to `"FREE"`; three nullable v3 score columns `form_score`/`fit_score`/`recognition_score`). |
| `data/local/PracticeAttemptDao.kt` | `@Insert suspend insert` + `observeAll()` query, newest first. |
| `data/local/ReviewCardEntity.kt` / `ReviewCardDao.kt` | Phase 3: `review_cards` table (PK `characterId`) + `getAll`/`upsert` (REPLACE). |
| `data/local/CharacterProgressEntity.kt` / `CharacterProgressDao.kt` | Phase 3: `character_progress` table (PK `characterId`) + `getAll`/`insert` (IGNORE)/`incrementTraces`. |
| `data/local/VocabularyProgressEntity.kt` / `VocabularyProgressDao.kt` | Phase 3: `vocabulary_progress` table (PK `kana`) + `getAll`/`insert` (IGNORE)/`recordPractice`. |
| `data/local/HiraganaDatabase.kt` | Room DB v6 (v1 → v2 → v3 → v4 → v5 → v6), `hiragana-practice.db`, `exportSchema = true`; `MIGRATION_1_2` (rename + Phase 3 tables), `MIGRATION_2_3` (nullable score columns), `MIGRATION_3_4` (practice-set tables), `MIGRATION_4_5` (reading-progress table, additive — 2026-07-09), `MIGRATION_5_6` (`practice_sets.updatedAt`, additive — 2026-07-24); `build(context)` factory registering all five. |
| `data/local/PracticeSetEntity.kt` | Room `@Entity`s for `practice_sets` (header, `updatedAt` since v6) + `practice_set_items` (members, `ON DELETE CASCADE`, index on setId) + `PracticeSetWithItems` `@Relation`. |
| `data/local/PracticeSetDao.kt` | `observeSetsWithItems()` Flow + `@Transaction` `createSet`/`replaceItems` (header + ordered members) + `renameSet`/`deleteSet`. |
| `data/repository/RoomPracticeSetRepository.kt` | `PracticeSetRepository` impl over `PracticeSetDao`; entity↔`PracticeSet` mapping (`type`/`script` decoded from stored keys). |

### `drawing` (9) — untouched by Phase 3; `canvas/` rewritten by the post-Phase-3 brush rework

| File | Responsibility |
|---|---|
| `drawing/stroke/StrokePoint.kt` | One sampled point: x, y, pressure, event-time timestamp. |
| `drawing/stroke/UserStroke.kt` | One pen-down→pen-up stroke; `averagePressure()` (no callers since the brush rework). |
| `drawing/stroke/DrawingSession.kt` | All strokes for the current character; `durationMillis()` extension (first→last timestamp). |
| `drawing/stroke/StrokeCapture.kt` | Raw `MotionEvent` → `CanvasState` calls: batched historical samples, pressure coerced 0..2, single-active-pointer palm rejection, stylus + finger. |
| `drawing/canvas/CanvasState.kt` | `@Stable` snapshot-state holder: session, completed brush-ribbon `Path`s, active path, plain-var `brushBaseWidthPx`, plus `lastCell` (the single 1×1 guide cell for single-glyph scoring) and `kanaCellRects` (per-kana cells for word scoring, published by `TracingCanvas`); `clear()`; UI-thread-only. |
| `drawing/canvas/StrokeRenderer.kt` | `buildBrushPath(points, baseWidthPx, taperTail)`: filled variable-width brush ribbon — per-point pressure (0.55..1.6) × speed-thinning widths, moving-average width smoothing, head/tail tapers, midpoint-bézier-smoothed edges (see docs/DRAWING.md §5). |
| `drawing/canvas/CanvasRenderers.kt` | `DrawScope` helpers: 2×2 dashed genkouyoushi grid + font-rendered ghost glyph (native `drawText`). |
| `drawing/animation/StrokeAnimationEngine.kt` | Phase 2 playback engine: `@Stable` snapshot-state holder; desired-state controls + single `suspend run()` driver (`Animatable`, epoch-guarded outputs, inter-stroke pauses, speed 0.25–4×, auto-repeat). |
| `drawing/animation/StrokeFramePath.kt` | Timed frames → geometry: `fullPath`/`partialPath`/`penPositionAt` in viewBox space, midpoint quadratic-bézier smoothing mirroring `StrokeRenderer`. |

### `presentation` (11)

| File | Responsibility |
|---|---|
| `presentation/theme/Color.kt` | Palette: brand vermilion, fixed "paper" canvas colors, grade button colors including Phase 3's `GradeAgainContainer`/`GradeAgainContent`. |
| `presentation/theme/Type.kt` | `HiraganaTypography` (76.sp `displayLarge` for the guide glyph, etc.). |
| `presentation/theme/Theme.kt` | `HiraganaTheme` Material 3 wrapper; light + dark color schemes via `isSystemInDarkTheme()`. |
| `presentation/viewmodels/PracticeViewModel.kt` | Phase 3 session state machine (`SessionPhase`, `PracticeUiState` via `StateFlow`): generates/walks a `StudySession`, routes grading through `SrsScheduler`, persists progress/unlocks, companion `Factory`. Post-Phase-3 API: `startNextSession()`, `openLibrary()`/`goToOpening()`, `practiceCharacter(char)`, `practiceWord(word)`, `retrySession()`. FREE two-step: `freeSubmit()` / `freeRetry()` / `advanceToNextCharacter()`. Private scoring pass `recordAttempt` (single glyph, gated to a 1×1 cell) / `recordWordAttempt` (per-kana via `StrokeAttribution`); both skip persistence when scoring is `OFF`. Practice sets: observes `observeSets()` into `practiceSets`; `savePracticeSet`/`updatePracticeSetMembers`/`renamePracticeSet`/`deletePracticeSet`, `practiceSet(id)` (FREE over a set, WORDS sets driving `freeWords`), `openQuiz(setId?)`/`setQuizSource(setId?)`/`buildQuizPool` (word-or-character `QuizItem` pool); `closeQuiz()` raises the `pendingOpenSetsManager` one-shot for set-launched quizzes, cleared by `consumeOpenSetsManager()`. |
| `presentation/components/TracingCanvas.kt` | Drawing surface: render order grid→ghost→completed→active; publishes `brushBaseWidthPx` (20.dp × cell-proportional `brushScale(cellSide, referenceSide)` — linear below the single-char 2×2 reference cell, half-slope above it) to `CanvasState` every draw pass and fills the brush-ribbon `Path`s plain (width baked into geometry — the old 9dp × `widthScale` stroke math is gone); `pointerInteropFilter` input. |
| `presentation/components/StrokeOrderDiagram.kt` | Static diagram: Compose `PathParser` per stroke, viewBox scaling, numbered badges at each first `MoveTo`. Fallback when a character has no `strokeAnimations`. |
| `presentation/components/AnimatedStrokePreview.kt` | Phase 2 animated demo: shared `StrokeCanvas` (guide → completed → active → pen dot) + `PlaybackControls` (play/pause, replay, speed cycle, auto-repeat; now takes a `List<StrokeAnimationEngine>` and broadcasts); engine per character; static-diagram fallback. Also hosts `WordStrokePreview` — one engine per word unit sharing one controls row. |
| `presentation/components/PracticeActionBar.kt` | `PracticeActionBar` (session phases): Clear · Again · Hard · Good · Easy; ink-gated enabling; grade button colors. Also defines `FreePracticeActionBar` — the FREE two-step (Clear · Submit/Skip → Again · Next), collapsing to Clear · Next for a multi-copy worksheet or when scoring is Off. |
| `presentation/components/CharacterGuidePanel.kt` | Top panel: glyph + (session label or romaji/deck-position or vocabulary word+meaning+"New word!" badge) + animated stroke-order preview + count label; optional `onOpenLibrary` renders a "Library" `TextButton`. |
| `presentation/screens/PracticeScreen.kt` | The single screen: collects `uiState`, remembers `CanvasState`, `LaunchedEffect(canvasClearKey)` clear, branches on library overlay vs the 0.25/0.65/0.10 `Column`, loading/error(+Retry) branches. Back-nav (2026-07-05): FREE has `BackHandler { openLibrary }` (Back → Library); the Library branch has NO `BackHandler` (hardware Back → exit app); the quiz branch keeps `BackHandler { closeQuiz }`. |
| `presentation/screens/LibraryScreen.kt` | Post-Phase-3 full-screen library overlay: `LazyColumn` (or grid chart) of every character ("Learned"/"Not started") and every word ("Unlocked"/"Locked"), all rows tappable into FREE practice (`practiceCharacter`/`practiceWord`); closed by the back arrow or system Back. Tints chart squares / word cards / list rows by each glyph's `scoreStats` average (`avgForm ?: avgFit`) via `scoreSpectrumColor`, score shown top-right; app bar reordered (Search leftmost, `BasicTextField` field, view toggle persists during search). Study-scope `ModalBottomSheet` is now `skipPartiallyExpanded` + `heightIn(max 90%)` + `verticalScroll`. **Practice sets are the third tab** (2026-07-27, `SetsTabContent` — a `ScrollableTabRow` sizes the three tab labels to content so their live counts never clip; script-scoped, top-bar search filters set names, inline header = count + `SetSortMenu`/`SetSort` + a "New" `DropdownMenu` picking Character/Word set): `SetManagerRow` with per-set `setAverage` score tint + `drawSortGlyph`. Building/editing a set still opens the selection mode (`SelectionModeContent` with a list⇄grid toggle: `SelectableList`/`SelectableGrid`/`SelectableGridCell`, both drag-to-range-select via `detectDragGestures` + edge auto-scroll) then a name dialog; `exitSelection()` returns to the Sets tab (selects + persists tab 2). |

### `app/src/test/.../domain/learning` (3, Phase 3)

| File | Responsibility |
|---|---|
| `SrsSchedulerTest.kt` | Every grade transition, ease floor/cap, interval floors, Again-reset, due-date math around start-of-day. |
| `LessonGeneratorTest.kt` | Mastery gate (both clauses + boundary), review cap + overdue ordering, deck-exhausted, backlog-blocked, orphaned-card exclusion, requeue semantics. |
| `VocabularyUnlockTest.kt` | `requiredCharacters` derivation, unlock rule, learned-definition boundary (repetitions 0 vs 1), reinforcement pick order. |
| `domain/model/{ScriptTest, KanaKindTest, KanaUnitsTest, ScriptScopeTest, KanaChartTest, RomajiMatchTest}.kt` | The katakana/opt-in seam, Library chart, and (2026-07-09) reading romaji matching: script classification, base-vs-voiced NFD, base+small unit fusion, four-flag scope filtering, chart section/placement, and `RomajiMatch` lenient equality. |
| `domain/scoring/{GeometryScorerTest, CharacterScoreTest, StrokeAttributionTest}.kt` | Geometry FORM/FIT dials + tags, `CharacterScore.combine` mode blending / recognition gate / headline caps, and `StrokeAttribution.groupByCell` nearest-center attribution. |

### Later additive files (post-Phase-3 — grouped)

Paths relative to `app/src/main/java/com/hive/hiragana/`.

| Group | Files |
|---|---|
| Katakana / opt-in seam (domain) | `domain/model/Script.kt`, `KanaKind.kt`, `KanaUnits.kt`, `ScriptScope.kt`, `PracticeLayout.kt` |
| Settings | `domain/repository/SettingsRepository.kt`, `data/repository/PrefsSettingsRepository.kt` |
| Handwriting scoring | `domain/scoring/{GeometryScorer, ScoringMode, CharacterScore, CharacterScoreSummary, StrokeAttribution}.kt` (`StrokeAttribution.kt` also defines `WordUnitScore` + `groupByCell`), `domain/repository/HandwritingRecognizer.kt`, `data/recognition/MlKitHandwritingRecognizer.kt`, `presentation/components/ScoreFeedbackCard.kt`, `presentation/components/WordScoreDisplay.kt` (`WordScoreChips` + `WordAverageCard`), `presentation/theme/ScoreColors.kt` (`scoreSpectrumColor`) |
| Library chart | `domain/model/KanaChart.kt` |
| Quiz | `presentation/screens/QuizScreen.kt` (state — incl. the `QuizItem`/`selectedSetId` word generalization — in `PracticeViewModel.kt`) |
| Ink color | `presentation/components/BrushColorPicker.kt` |
| Practice sets | `domain/model/PracticeSet.kt`, `domain/repository/PracticeSetRepository.kt`, `data/local/PracticeSetEntity.kt`, `data/local/PracticeSetDao.kt`, `data/repository/RoomPracticeSetRepository.kt` (selection-mode / sets-manager / name-dialog composables live in `LibraryScreen.kt`) |
| Reading/Writing split (2026-07-09) | `domain/model/AppMode.kt`, `domain/model/RomajiMatch.kt`, `domain/model/ReadingProgress.kt`, `domain/repository/ReadingProgressRepository.kt`, `data/repository/RoomReadingProgressRepository.kt` + its Room entity/DAO (`data/local`, backing the v5 reading-progress table), `presentation/screens/OpeningScreen.kt` (mode chooser), `presentation/screens/ReadingFlashcardScreen.kt` (reading drill). Reading quiz state (`QuizKind`/`ReadingAnswerStyle`/`readingResult`) + `readingFlashcard`/`readingStats`/`appMode` live in `PracticeViewModel.kt`; the Reading|Writing toggle + mode-aware tint/sort live in `LibraryScreen.kt`. Test: `RomajiMatchTest`. |
