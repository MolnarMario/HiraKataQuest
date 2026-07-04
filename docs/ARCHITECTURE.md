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
adversarially reviewed the same day (0 P0, review fixes applied). A **katakana
section** (2026-07-04, device-verified on the Pixel 7) then added a second script
without touching the learning engine or Room schema: a `Script` enum on every
`HiraganaCharacter` (set by the loader from the `assets/<script>/` directory), a
second asset directory, a persisted `activeScript` setting, and a Library selector.
The `PracticeViewModel` keeps the full merged deck loaded (for cross-script romaji
lookups) but derives a script-scoped deck via `ScriptScope.scopedCharacters/Words`
and feeds ONLY that into `LessonGenerator.generate()`, the quiz pool, and FREE
practice — so the pure learning layer, its tests, and the Room tables are
unchanged (off-script `ReviewCard`s are inert orphans; katakana rows are additive).
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
then do a phase-independent session rebuild that keeps the library open;
`startNextSession` preserves all four across its state reconstruction. With all
three opt-ins on, the deck is the complete kana inventory (both scripts).
Section 7 covers the Phase 2 design as
built and section 8 covers Phase 3; the full contracts are
[docs/PHASE2-DESIGN.md](PHASE2-DESIGN.md) and [docs/PHASE3-DESIGN.md](PHASE3-DESIGN.md).

All Kotlin sources live under `app/src/main/java/com/hive/hiragana/`. There are **68
Kotlin files** in the `main` source set, plus **11 files** under `app/src/test/`
(**110 JUnit tests total**; there is still no `androidTest/`). The test count grew
well past Phase 3's original 45 as the katakana/opt-in seam (`Script`, `KanaKind`,
`KanaUnits`, `ScriptScope`), the Library chart (`KanaChart`), and the
handwriting-scoring layer (`GeometryScorer`, `CharacterScore`) each brought their own
pure-domain suites. Paths below are given relative to
`app/src/main/java/com/hive/hiragana/` unless otherwise noted.

Beyond the three phases and the katakana/opt-in work described above, later additive
features layer onto the same architecture without touching the drawing pipeline or the
learning engine: a **handwriting-quality scoring** subsystem (pure `domain/scoring/`
geometry scorer + an ML Kit `data/recognition/` recognizer, advisory-only, §8), a
**write-from-memory quiz** overlay (`QuizScreen` + `QuizState`/`QuizStage` in the
ViewModel, `PracticeSource.QUIZ`), and a **Library chart view** (`domain/model/KanaChart.kt`,
a list⇄grid toggle). These pushed Room to **v3** (a purely additive score-column
migration — see §8 and [docs/DATA.md](DATA.md)).

---

## 1. Layer map

Four packages plus two root files:

| Package | Responsibility | Depends on |
|---|---|---|
| `domain` | Models (`HiraganaCharacter`, `Grade`, `PracticeAttempt`, `StrokeDefinition`/`StrokeFrame`, the Phase 3 additions `ReviewCard`, `VocabularyWord`, `StudySession`, `CharacterProgress`, `VocabularyProgress`, and the later `Script`/`KanaKind`/`KanaUnits`/`ScriptScope`/`PracticeLayout`/`KanaChart` types), repository interfaces (incl. `SettingsRepository`, `HandwritingRecognizer`), `domain/learning` (pure SRS scheduler + session generator), and `domain/scoring` — the pure handwriting-quality scorer (`GeometryScorer`, `ScoringMode`, `CharacterScore`, `CharacterScoreSummary`). | **Pure Kotlin + `kotlinx.coroutines`** (`Flow` in `PracticeRepository`). Zero Android imports. `domain/learning` and `domain/scoring` additionally take no clock reads internally — every computation is parameterized (`todayStartMillis: Long`; explicit stroke/cell inputs), which is what makes them unit-testable without instrumentation. |
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
`settingsRepository` and `handwritingRecognizer`), still all manual.

Wiring chain (all in `HiraganaApplication.kt` and `PracticeViewModel.kt`):

1. `HiraganaApplication.onCreate()` creates `container = AppContainer(this)`.
   `HiraganaApplication` is registered via `android:name=".HiraganaApplication"` in
   `app/src/main/AndroidManifest.xml`.
2. `AppContainer` (same file as the Application class) holds, all `by lazy`:
   - `database: HiraganaDatabase` — `HiraganaDatabase.build(appContext)` (Room v3,
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
   )
   ```

   The ViewModel itself only sees the six `domain` repository interfaces (plus, as
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
only via `_uiState.update { … }`). As of Phase 3 the ViewModel drives a `SessionPhase`
state machine (docs/PHASE3-DESIGN.md §6):

```
LOADING → REVIEWS → NEW_CHARACTER → PRACTICE → VOCABULARY → COMPLETE → (FREE)
```

`NEW_CHARACTER` and `PRACTICE` are deliberately collapsed into one UI-visible step: the
Phase 2 animated preview auto-plays as soon as the new character is shown, and its first
trace *is* practice repetition 1. `COMPLETE` now offers three continuations (see §5):
**"Start next session"** (`startNextSession()` — primary, hidden when `allCaughtUp`;
regenerates a fresh `StudySession` from the just-updated cards, so restarting the app is
no longer the only way to keep learning), **"Keep practicing"** (`startFreePractice()` —
the original escape hatch into `FREE`, the old Phase 1/2 endless loop over
already-learned characters, no scheduling side effects), and **"Browse library"**
(`openLibrary()`). The library (post-Phase-3) also feeds `FREE` directly:
`practiceCharacter(char)` free-practices the **whole** deck starting at the pick
(learned or not — the library is the explicit escape hatch from SRS gating), and
`practiceWord(word)` free-practices the word's kana in order with `freeWord` driving the
word header in the guide panel. Key `PracticeUiState` fields, grouped by which phase(s)
they serve:

| Field | Meaning |
|---|---|
| `isLoading`, `phase: SessionPhase`, `characters`, `errorMessage`, `canvasClearKey: Int` | Always relevant. `canvasClearKey` is a single monotonic counter bumped on every advance (each review, each practice rep, each word, each phase change) — see (b) below. |
| `reviewQueue`, `reviewsTotal`, `reviewsDone` | REVIEWS: the due-card queue (front = current), and progress counters. |
| `newCharacter`, `repetitionIndex`, `repetitionsTotal` | NEW_CHARACTER/PRACTICE: the character being introduced and its rep count (3 by default). |
| `vocabularyQueue`, `currentWord`, `wordCharIndex`, `wordsTotal`, `wordsPracticed`, `newlyUnlockedKana` | VOCABULARY: the word queue, the kana index being traced, and which unlocks are fresh this session (drives the "New word!" badge, not reinforcement picks). |
| `allCaughtUp`, `newCharacterLearned`, `wordsUnlocked`, `hasLearnedCharacters` | COMPLETE: summary stats. `newCharacterLearned` is null if the final practice rep was graded Again (introduced, not learned); `wordsUnlocked` lists only fresh unlocks; `hasLearnedCharacters` gates whether "Keep practicing" renders at all. |
| `freeCharacters`, `currentIndex`, `gradedCount`, `freeWord` | FREE: the old Phase 1/2 deck-walking fields. Via "Keep practicing" the deck is scoped to characters with `repetitions >= 1`; via a library pick it can hold the **whole** deck (`practiceCharacter`) or one word's kana in order (`practiceWord`, with `freeWord` non-null so the guide panel shows the word header). |
| `isLibraryOpen`, `allWords`, `unlockedWordKana`, `learnedCharacterIds` | LIBRARY: whether the full-screen library overlay is showing (it overlays whatever phase is active without disturbing it), every vocabulary word, and the unlock/learned sets that drive the rows' "Unlocked"/"Locked" and "Learned"/"Not started" labels. |
| `quiz: QuizState?` | QUIZ (write-from-memory overlay launched from the library; renders above it). `QuizState` holds a `QuizStage` (`SETUP`/`RUNNING`/`SUMMARY`), the requested count, a per-quiz `showGrid`, the chosen `characters`, `index`, `revealed`/`submitted` two-step flags, and `results` (auto quality-score headline by character id). Each submit auto-scores (no self-grade) and records a `PracticeSource.QUIZ` attempt — **no SRS/unlock side effects**. |
| `isGridEnabled`, `isGhostEnabled`, `isLibraryGridView`, `practiceLayout`, `activeScript`, `includeDiacritics`, `includeYoon`, `includeExtended`, `scoringMode`, `brushInkColor` | SETTINGS: seeded from and persisted via `SettingsRepository` (keys `grid_enabled`, `ghost_enabled`, `library_grid_view`, `practice_layout_span`, `active_script`, `diacritics_included`, `yoon_included`, `extended_included`, `scoring_mode`, `brush_ink_color`). |
| `lastScore`, `lastScoredCharacter`, `scoreStats` | SCORING (§8): the most recent `CharacterScore` and the glyph it was computed for (drives `ScoreFeedbackCard`), plus a `Map<String, CharacterScoreSummary>` for the Library's per-character readout. |
| derived: `currentCharacter`, `sessionProgress`, `total`, `isCurrentWordNew`, `activeScriptCharacters` | `currentCharacter` resolves per-phase (review queue head / new character / word's current kana resolved back to a `HiraganaCharacter` / free-deck position); `sessionProgress` is the human-readable line ("Review 3/5", "Practice 1/3", …) shown by `CharacterGuidePanel`; `activeScriptCharacters` is the script/opt-in-scoped view of the full deck. |

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

The user actions, plus the shared clear loop-back. Grading now routes through
`SrsScheduler` and the `SessionPhase` machine instead of a flat "advance the deck" step:

```
CLEAR (local-only; ViewModel never involved)
  User taps Clear
    └─► PracticeActionBar.onClear
          └─► CanvasState.clear()          resets session/paths → canvas recomposes,
                                           hasInk=false disables Clear + grade buttons

GRADE (Again / Hard / Good / Easy)
  User taps a grade button (enabled only when hasInk)
    └─► PracticeActionBar.onGrade(grade)
          └─► PracticeScreen: viewModel.gradeCurrentCharacter(grade, canvasState.currentSession)
                ├─ 0. double-grade guard: return early if this DrawingSession was
                │       already consumed by the previous grade (identity check)
                ├─ 1. SYNCHRONOUS snapshot: build PracticeAttempt
                │       (character, romaji, grade, session.strokes.size,
                │        session.durationMillis(), System.currentTimeMillis(), source)
                ├─ 2. viewModelScope.launch { runCatching { practiceRepository.recordAttempt(attempt) } }
                │       fire-and-forget Room insert; failures now LOGGED (Log.w, tag
                │       HiraganaSession) instead of silently swallowed
                └─ 3. phase-specific routing (gradeReview / gradePractice / gradeVocabulary / gradeFree):
                        REVIEWS      → SrsScheduler.next(...) reschedules the card;
                                       Again re-queues the character within reviewQueue
                        NEW_CHARACTER/PRACTICE → on the final rep, SrsScheduler.newCard()
                                       + next(...) seeds the character's first ReviewCard
                        VOCABULARY   → records the attempt only; no card rescheduling
                        FREE         → same as legacy: just records + advances
                      every branch bumps canvasClearKey

NEXT (skip without grading; only rendered in FREE — see §7 PracticeActionBar)
  User taps Next
    └─► PracticeActionBar.onNext
          └─► PracticeViewModel.advanceToNextCharacter()   (modulo wrap over freeCharacters, no-op elsewhere)

START NEXT SESSION (COMPLETE summary, hidden when allCaughtUp; also legal from FREE)
  User taps "Start next session"
    └─► PracticeViewModel.startNextSession()
          resets PracticeUiState (carrying over characters / allWords /
          unlockedWordKana / learnedCharacterIds, bumping canvasClearKey),
          then re-runs the same private startSession() load — the cards
          grading just updated usually open the mastery gate for the next
          character. No-op outside COMPLETE/FREE.

LIBRARY (post-Phase-3; available from the guide panel's "Library" button and
the COMPLETE summary's "Browse library")
  openLibrary()  → isLibraryOpen = true (overlay; underlying phase untouched)
  closeLibrary() → isLibraryOpen = false (also wired to system Back via BackHandler)
  practiceCharacter(char) → FREE over the WHOLE deck, currentIndex at the pick
  practiceWord(word)      → FREE over the word's kana in order, freeWord = word
  (both close the overlay, bump canvasClearKey, and abandon any in-progress
  session steps; grading in this FREE mode records PracticeSource.FREE
  attempts only — no scheduling or unlock side effects)

LOOP-BACK (after any GRADE branch, or NEXT)
  canvasClearKey incremented in PracticeUiState
    └─► StateFlow emission → PracticeScreen recomposes
          └─► LaunchedEffect(uiState.canvasClearKey) re-runs
                └─► CanvasState.clear()    blank sheet for whatever is current now
```

The ordering in GRADE is the whole point: by the time any coroutine write runs and the
canvas clears, the attempt object (and, where relevant, the rescheduled `ReviewCard`) is
already fully built and immutable. `startSession()` (called once from `init`) is the
Phase 3 addition upstream of all of this: it loads characters/cards/words/progress, asks
`LessonGenerator.generate(...)` for a `StudySession`, persists any freshly-unlocked
vocabulary rows, and enters `REVIEWS` (or skips forward through whichever steps are
empty, straight to `COMPLETE` if the whole session is empty).

## 5. Screen composition tree

Single screen, no navigation library. The top-level content branches on the library
overlay (`isLibraryOpen`, post-Phase-3) and `SessionPhase.COMPLETE` (a summary card)
versus everything else (the familiar three-pane
layout, now fed by whichever phase is active):

```
MainActivity.setContent
└─ HiraganaTheme                          (presentation/theme/Theme.kt, Material 3)
   └─ PracticeScreen                      (presentation/screens/PracticeScreen.kt)
      ├─ remember { CanvasState() }       + LaunchedEffect(uiState.canvasClearKey) (see §3/§4)
      └─ Surface (fillMaxSize, colorScheme.background)
         ├─ isLoading                  → CenteredBox { CircularProgressIndicator }
         ├─ errorMessage != null       → CenteredBox { error Text + Retry button
         │                               (viewModel.retrySession) }
         ├─ isLibraryOpen              → BackHandler { closeLibrary } +
         │                               LibraryScreen (full-screen overlay:
         │                               LazyColumn of all characters + all words,
         │                               "Learned"/"Not started" and
         │                               "Unlocked"/"Locked" labels, every row
         │                               tappable → practiceCharacter/practiceWord;
         │                               arrow or Back closes)
         ├─ phase == COMPLETE          → SessionCompleteContent (Card: reviews done /
         │                               character learned / words unlocked+practiced;
         │                               buttons: "Start next session" (primary Button,
         │                               hidden when allCaughtUp) · "Keep practicing"
         │                               (FilledTonalButton, hidden if nothing learned) ·
         │                               "Browse library" (TextButton))
         └─ else → PracticeContent
            └─ Column (fillMaxSize)
               ├─ CharacterGuidePanel      weight(0.25f)   ── top 25%
               │  └─ Row
               │     ├─ Column weight(1f): [sessionLabel line, e.g. "Review 2/5"]
               │     │                     + glyph (displayLarge, 76.sp)
               │     │                     + either "romaji · character N of M" (FREE,
               │     │                       or hidden via total=0 in session phases)
               │     │                       or the word's kana (current glyph bolded)
               │     │                       + meaning line with a "New word!" prefix
               │     │                       on fresh unlocks (VOCABULARY)
               │     │                     + "Library" TextButton (onOpenLibrary)
               │     └─ Column: AnimatedStrokePreview (square canvas + playback
               │                controls; falls back to the static numbered
               │                StrokeOrderDiagram without timing data)
               │                + "Stroke order · N" label
               ├─ TracingCanvas            weight(0.65f)   ── middle 65%
               │  └─ Surface (rounded, PaperWhite) → Canvas
               │     draw order: practice grid → ghost glyph →
               │                 completed strokes → active stroke
               │     input: pointerInteropFilter → StrokeCapture
               └─ PracticeActionBar        weight(0.10f)   ── bottom 10%
                  └─ Row: Clear(w 1.1) · Again(w 1) · Hard(w 1) · Good(w 1) · Easy(w 1)
                          · Next(w 1.1, only when showNext/FREE)
```

The 0.25 / 0.65 / 0.10 `Column` weights in `PracticeContent` are the layout contract of
the app; guide, canvas and actions always share the screen in that ratio — Phase 3 did
not touch it, only what's rendered inside each pane.

## 6. Threading model

| Work | Where it runs | Mechanism |
|---|---|---|
| Asset JSON read + parse (characters + vocabulary) | `Dispatchers.IO` | `HiraganaAssetDataSource.loadCharacters()` / `.loadVocabulary()` wrap everything in `withContext(Dispatchers.IO)`; `org.json` parsing happens there too. |
| Character cache / vocabulary word cache | caller's context, serialised | `AssetCharacterRepository` and (Phase 3) `RoomVocabularyRepository.getWords()` each guard an in-memory cache with a `kotlinx.coroutines.sync.Mutex` — concurrent first calls load once. |
| `startSession()` / grade / progress / unlock coroutines | `viewModelScope` (Main dispatcher) | Launched in `PracticeViewModel`; the suspend hops (`withContext(IO)`, Room) move real work off the main thread. `LessonGenerator.generate(...)` itself is synchronous pure computation called inline inside the coroutine. |
| Room writes (practice_history insert, review_cards upsert, character_progress/vocabulary_progress updates) | Room's own executors | All DAO methods are `suspend fun`; Room dispatches internally. Fire-and-forget inside `viewModelScope.launch { runCatching { … } }` — no result observed, but (Phase 3) failures are now logged via `Log.w(TAG, ...)` (tag `HiraganaSession`) instead of silently swallowed. |
| MotionEvent capture, stroke building, path smoothing | UI thread | `pointerInteropFilter` delivers events on the main thread; `StrokeCapture` and `StrokeRenderer` run inline in the handler. |
| `CanvasState` mutations | **UI thread only, by contract** | Documented in its KDoc. Snapshot state is technically thread-safe, but the class assumes single-threaded mutation (e.g. `startStroke`'s defensive commit, `activeStroke` is a plain non-snapshot field). All current call sites honour this: `StrokeCapture` (input), `PracticeScreen` button lambdas and `LaunchedEffect` (composition). |
| Stroke-order playback (Phase 2) | UI thread (main) | `StrokeAnimationEngine.run()` is the single suspend driver, launched from a `LaunchedEffect` in `AnimatedStrokePreview`; controls are non-suspend `onClick` mutations of desired state. Outputs are snapshot state read inside the preview's draw lambda (draw invalidation only, no recomposition per frame). |
| `SrsScheduler` / `LessonGenerator` (Phase 3) | Wherever the caller runs them (today: inside `viewModelScope` coroutines) | Pure, synchronous, no dispatcher of their own — no I/O, no clock reads; every date computation takes `todayStartMillis` as a parameter, so they carry no threading requirement at all. |

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
  collector**, but the COMPLETE summary (§8 below) is the concrete consumer the Phase 2
  KDoc anticipated for `HiraganaCharacter.frequency` and SRS: `frequency` now drives study
  order, and grading now feeds a real scheduler — `observeAttempts()`'s `Flow` itself
  remains unread, but its purpose (grade history informing later behavior) has arrived
  through a different path (persisted `ReviewCard`/`CharacterProgress` state instead of
  replaying the attempt log).
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
- **Persistence — Room v1 → v2 (and later → v3)**: `HiraganaDatabase` is now
  `version = 3`, `exportSchema = true` (schema history committed at `app/schemas/`,
  `2.json` + `3.json`). `MIGRATION_1_2` (a real `Migration(1, 2)`, no destructive
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
- **UI — session state machine**: `PracticeViewModel` now drives `SessionPhase`
  (`LOADING → REVIEWS → NEW_CHARACTER → PRACTICE → VOCABULARY → COMPLETE`, with `FREE` as
  an escape hatch from `COMPLETE`) inside `PracticeUiState` — full field/flow detail in
  §3 and §4 above. `PracticeActionBar` renders `Clear · Again · Hard · Good · Easy`,
  plus `Next` only when `showNext` (i.e. only in `FREE` — during a session the generator
  decides what's next). `CharacterGuidePanel` gained optional `sessionLabel` / `word` /
  `wordCharIndex` / `isNewWord` parameters, all defaulted so the `FREE`/legacy call shape
  is unchanged. `Color.kt` gained `GradeAgainContainer`/`GradeAgainContent` — a step more
  alarming than Hard's pink, completing a worst-to-best visual ramp.
- **Tests**: `app/src/test/java/com/hive/hiragana/domain/learning/` — `SrsSchedulerTest`,
  `LessonGeneratorTest`, `VocabularyUnlockTest` — were the project's first tests (45 at
  Phase 3), since joined by pure-domain suites for the katakana/opt-in seam and the
  scoring/chart work (see §8-scoring) and the first `PracticeViewModelTest`;
  **110 JUnit4 tests total** today, all green
  (`testDebugUnitTest`). Pure domain logic made this practical without instrumentation,
  unlike Phase 2's UI/engine surface.

### Handwriting-quality scoring, quiz, and the Library chart (post-Phase-3, additive)

Three later features layer on without touching the drawing pipeline or the learning
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
- **Write-from-memory quiz.** `presentation/screens/QuizScreen.kt` + a `QuizState`
  (`QuizStage` `SETUP`/`RUNNING`/`SUMMARY`) held in `PracticeUiState.quiz`, launched from
  the Library, overlaying whatever phase is active. Each submitted character is
  auto-scored (no self-grade) and recorded as `PracticeSource.QUIZ` with **no SRS or
  unlock side effects**.
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

## 9. File responsibilities (representative, not exhaustive)

Paths relative to `app/src/main/java/com/hive/hiragana/` unless noted. This table
covers the Phase 1–3 core in full; the later additive files (the katakana/opt-in seam,
handwriting scoring, quiz, Library chart, settings) are listed as a group at the end
rather than exhaustively re-inventoried — the `main` set is now **68 files**, the test
set **11 files (110 tests)** (incl. the first `PracticeViewModelTest`).

### Root (2)

| File | Responsibility |
|---|---|
| `HiraganaApplication.kt` | `Application` subclass exposing `container`; also defines `AppContainer` (lazy Room DB + `HiraganaAssetDataSource` + all four repositories). |
| `MainActivity.kt` | Single `ComponentActivity`; `setContent` → `HiraganaTheme` → `PracticeScreen` with `PracticeViewModel.Factory`. |

### `domain` (15)

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
| `domain/learning/SrsScheduler.kt` | Phase 3: pure `object` — SM-2-style grade transitions, clock-injected via `todayStartMillis` parameter. |
| `domain/learning/LessonGenerator.kt` | Phase 3: pure `class` — assembles reviews + gated new character + vocabulary into a `StudySession`; excludes orphaned cards. |

### `data` (14)

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
| `data/local/HiraganaDatabase.kt` | Room DB v3 (v1 → v2 → v3), `hiragana-practice.db`, `exportSchema = true`; `MIGRATION_1_2` (rename + Phase 3 tables) and `MIGRATION_2_3` (nullable score columns); `build(context)` factory registering both. |

### `drawing` (9) — untouched by Phase 3; `canvas/` rewritten by the post-Phase-3 brush rework

| File | Responsibility |
|---|---|
| `drawing/stroke/StrokePoint.kt` | One sampled point: x, y, pressure, event-time timestamp. |
| `drawing/stroke/UserStroke.kt` | One pen-down→pen-up stroke; `averagePressure()` (no callers since the brush rework). |
| `drawing/stroke/DrawingSession.kt` | All strokes for the current character; `durationMillis()` extension (first→last timestamp). |
| `drawing/stroke/StrokeCapture.kt` | Raw `MotionEvent` → `CanvasState` calls: batched historical samples, pressure coerced 0..2, single-active-pointer palm rejection, stylus + finger. |
| `drawing/canvas/CanvasState.kt` | `@Stable` snapshot-state holder: session, completed brush-ribbon `Path`s, active path, plain-var `brushBaseWidthPx`; `clear()`; UI-thread-only. |
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
| `presentation/viewmodels/PracticeViewModel.kt` | Phase 3 session state machine (`SessionPhase`, `PracticeUiState` via `StateFlow`): generates/walks a `StudySession`, routes grading through `SrsScheduler`, persists progress/unlocks, companion `Factory`. Post-Phase-3 API: `startNextSession()`, `openLibrary()`/`closeLibrary()`, `practiceCharacter(char)`, `practiceWord(word)`, `retrySession()`. |
| `presentation/components/TracingCanvas.kt` | Drawing surface: render order grid→ghost→completed→active; publishes `brushBaseWidthPx` (20.dp × cell-proportional `brushScale(cellSide, referenceSide)` — linear below the single-char 2×2 reference cell, half-slope above it) to `CanvasState` every draw pass and fills the brush-ribbon `Path`s plain (width baked into geometry — the old 9dp × `widthScale` stroke math is gone); `pointerInteropFilter` input. |
| `presentation/components/StrokeOrderDiagram.kt` | Static diagram: Compose `PathParser` per stroke, viewBox scaling, numbered badges at each first `MoveTo`. Fallback when a character has no `strokeAnimations`. |
| `presentation/components/AnimatedStrokePreview.kt` | Phase 2 animated demo: preview canvas (guide → completed → active → pen dot) + playback controls (play/pause, replay, 0.5×/1×/2× speed cycle, auto-repeat); engine per character; static-diagram fallback. Untouched by Phase 3 (it is now also the Phase 3 "new character" introduction). |
| `presentation/components/PracticeActionBar.kt` | Clear · Again · Hard · Good · Easy row, plus Next only when `showNext` (FREE); ink-gated enabling; grade button colors. |
| `presentation/components/CharacterGuidePanel.kt` | Top panel: glyph + (session label or romaji/deck-position or vocabulary word+meaning+"New word!" badge) + animated stroke-order preview + count label; optional `onOpenLibrary` renders a "Library" `TextButton`. |
| `presentation/screens/PracticeScreen.kt` | The single screen: collects `uiState`, remembers `CanvasState`, `LaunchedEffect(canvasClearKey)` clear, branches on library overlay (with `BackHandler`) / `COMPLETE` (with its three continuation buttons) vs the 0.25/0.65/0.10 `Column`, loading/error(+Retry) branches. |
| `presentation/screens/LibraryScreen.kt` | Post-Phase-3 full-screen library overlay: `LazyColumn` of every character ("Learned"/"Not started") and every word ("Unlocked"/"Locked"), all rows tappable into FREE practice (`practiceCharacter`/`practiceWord`); closed by the back arrow or system Back. |

### `app/src/test/.../domain/learning` (3, Phase 3)

| File | Responsibility |
|---|---|
| `SrsSchedulerTest.kt` | Every grade transition, ease floor/cap, interval floors, Again-reset, due-date math around start-of-day. |
| `LessonGeneratorTest.kt` | Mastery gate (both clauses + boundary), review cap + overdue ordering, deck-exhausted, backlog-blocked, orphaned-card exclusion, requeue semantics. |
| `VocabularyUnlockTest.kt` | `requiredCharacters` derivation, unlock rule, learned-definition boundary (repetitions 0 vs 1), reinforcement pick order. |
| `domain/model/{ScriptTest, KanaKindTest, KanaUnitsTest, ScriptScopeTest, KanaChartTest}.kt` | The katakana/opt-in seam and Library chart: script classification, base-vs-voiced NFD, base+small unit fusion, four-flag scope filtering, chart section/placement. |
| `domain/scoring/{GeometryScorerTest, CharacterScoreTest}.kt` | Geometry FORM/FIT dials + tags, and `CharacterScore.combine` mode blending / recognition gate / headline caps. |

### Later additive files (post-Phase-3 — grouped)

Paths relative to `app/src/main/java/com/hive/hiragana/`.

| Group | Files |
|---|---|
| Katakana / opt-in seam (domain) | `domain/model/Script.kt`, `KanaKind.kt`, `KanaUnits.kt`, `ScriptScope.kt`, `PracticeLayout.kt` |
| Settings | `domain/repository/SettingsRepository.kt`, `data/repository/PrefsSettingsRepository.kt` |
| Handwriting scoring | `domain/scoring/{GeometryScorer, ScoringMode, CharacterScore, CharacterScoreSummary}.kt`, `domain/repository/HandwritingRecognizer.kt`, `data/recognition/MlKitHandwritingRecognizer.kt`, `presentation/components/ScoreFeedbackCard.kt` |
| Library chart | `domain/model/KanaChart.kt` |
| Quiz | `presentation/screens/QuizScreen.kt` (state in `PracticeViewModel.kt`) |
| Ink color | `presentation/components/BrushColorPicker.kt` |
