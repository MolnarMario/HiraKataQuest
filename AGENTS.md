# AGENTS.md — Hiragana Trace

Android app for practicing hiragana handwriting: trace a light-grey ghost glyph on a genkouyoushi-style grid with stylus or finger, self-grade (Again/Hard/Good/Easy), attempts persist to Room. A Phase 3 spaced-repetition scheduler and session generator decide what to review/introduce/practice each visit. Kotlin, Jetpack Compose, Material 3, MVVM, Room via KSP, manual DI (no Hilt). Single module `:app`, single screen, package `com.hive.hiragana`.

**Status: Phase 1 (static tracing MVP), Phase 2 (animated stroke-order demonstrations), and Phase 3 (Smart Learning System — SRS scheduling, personalized sessions, vocabulary unlocking) are all COMPLETE.** Phase 1 `assembleDebug` on 2026-07-03; Phase 2 implemented and built 2026-07-03, re-verified/reviewed/fixed 2026-07-04; Phase 3 implemented, built, and unit-tested 2026-07-04 (`assembleDebug` + `testDebugUnitTest` both green, 45 unit tests / 0 failures — the project's first tests) and adversarially reviewed (2 agents, 0 P0, both verdicts ship; review fixes applied per `docs/PHASE3-DESIGN.md` §11). **None of the three phases has been exercised on a device/emulator**, including Phase 3's live v1→v2 Room migration (statically verified against the exported schema only). History: `docs/HISTORY.md`; Phase 2 contract: `docs/PHASE2-DESIGN.md`; Phase 3 contract + as-built notes: `docs/PHASE3-DESIGN.md`.

## Build & run

### Path A — Android Studio (any machine)
Open the project root in Android Studio Koala or newer. Gradle sync provisions Gradle 8.7 from `gradle/wrapper/gradle-wrapper.properties`. Run the `app` configuration.

### Path B — CLI on THIS machine (verified BUILD SUCCESSFUL 2026-07-03, ~2 min)
Locally provisioned toolchain (not on PATH):

| Tool | Location |
|---|---|
| JDK 17 (portable Temurin) | `C:\Users\Mario\.jdks\jdk-17.0.19+10` |
| Android SDK (platforms;android-34, build-tools;34.0.0, licenses accepted) | `C:\Users\Mario\AppData\Local\Android\Sdk` |
| Gradle 8.7 | `C:\Users\Mario\.gradle-dist\gradle-8.7\bin\gradle.bat` |

PowerShell:
```powershell
$env:JAVA_HOME = 'C:\Users\Mario\.jdks\jdk-17.0.19+10'
$env:ANDROID_HOME = 'C:\Users\Mario\AppData\Local\Android\Sdk'
& 'C:\Users\Mario\.gradle-dist\gradle-8.7\bin\gradle.bat' -p 'C:\Users\Mario\Downloads\hive-test2' assembleDebug --console=plain
```
APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Notes:
- **No Gradle wrapper JAR / `gradlew` scripts are committed** — only `gradle-wrapper.properties`. On other machines run `gradle wrapper --gradle-version 8.7` once, or use a standalone Gradle 8.7 as above.
- `local.properties` (machine-local, gitignored) already pins `sdk.dir` to the SDK path above.
- Toolchain details also recorded in user memory: `android-build-toolchain.md`.

## Project structure

```
build.gradle.kts / settings.gradle.kts     root project "HiraganaTrace", module :app
gradle/libs.versions.toml                  version catalog (AGP 8.5.2, Kotlin 2.0.20, KSP 2.0.20-1.0.25,
                                           Compose BOM 2024.09.03, Room 2.6.1)
app/build.gradle.kts                       compile/target SDK 34, minSdk 26, Java 11, versionName 0.1.0,
                                           minification off
app/src/main/assets/hiragana/              index.json (file manifest; study order now comes from
app/src/main/assets/katakana/              `frequency`, not this order) + one JSON per character
                                           (incl. generated strokeAnimations timing data) +
                                           vocabulary.json. ONE dir PER SCRIPT — COMPLETE kana set:
                                           hiragana 113 chars (46 base + 25 dakuten + 33 yōon + 9
                                           extended; 247 words) + katakana 136 (47 base + 25 dakuten
                                           + 33 yōon + 9 extended + 22 composites; 150 words). Script
                                           from dir; KanaKind (voiced) + yōon/extended (KanaUnits)
                                           derived from glyph; yōon + extended-katakana strokes
                                           COMPOSED from base + small kana. Voiced kana, yōon, and
                                           extended kana are three independent opt-ins
                                           (SettingsRepository.include{Diacritics,Yoon,Extended},
                                           all default off)
tools/generate_stroke_animations.py        offline generator+validator for strokeAnimations (Python 3)
app/schemas/                               Room exported-schema JSON history (Phase 3; exportSchema=true)
app/src/test/java/com/hive/hiragana/domain/learning/
                                           SrsSchedulerTest, LessonGeneratorTest, VocabularyUnlockTest
                                           (45 JUnit4 tests total — the project's first tests, Phase 3)
app/src/main/java/com/hive/hiragana/
  HiraganaApplication.kt                   Application + AppContainer (manual DI)
  MainActivity.kt                          ComponentActivity -> HiraganaTheme -> PracticeScreen
  domain/model/                            HiraganaCharacter (+ id: String, asset basename),
                                           Grade (AGAIN/HARD/GOOD/EASY, Phase 3; legacy OKAY -> GOOD),
                                           PracticeAttempt (+ source: PracticeSource REVIEW/NEW/VOCAB/FREE),
                                           StrokeDefinition (StrokeFrame timing data, Phase 2),
                                           ReviewCard, VocabularyWord (+ fromKana derivation),
                                           StudySession, CharacterProgress, VocabularyProgress (Phase 3)
  domain/learning/                         SrsScheduler (pure SM-2-style transitions, clock-injected),
                                           LessonGenerator (pure session builder, clock-injected) — Phase 3
  domain/repository/                       CharacterRepository, PracticeRepository,
                                           LearningRepository, VocabularyRepository (interfaces)
  data/assets/HiraganaAssetDataSource.kt   org.json parsing of character + vocabulary asset JSONs
                                           on Dispatchers.IO
  data/repository/                         AssetCharacterRepository (Mutex-guarded cache, sorts by
                                           frequency ascending), RoomPracticeRepository (entity<->domain
                                           mapping, legacy grade/source fallback), RoomLearningRepository,
                                           RoomVocabularyRepository (Mutex-cached getWords)
  data/local/                              Room v2 ('hiragana-practice.db', exportSchema=true):
                                           PracticeAttemptEntity/Dao (table practice_history, was
                                           practice_attempts pre-Phase-3), ReviewCardEntity/Dao,
                                           CharacterProgressEntity/Dao, VocabularyProgressEntity/Dao,
                                           HiraganaDatabase (MIGRATION_1_2)
  drawing/stroke/                          StrokePoint, UserStroke, DrawingSession,
                                           StrokeCapture (MotionEvent -> CanvasState)
  drawing/canvas/                          CanvasState (@Stable snapshot-state holder),
                                           StrokeRenderer (smoothing + pressure width),
                                           CanvasRenderers (grid + ghost glyph DrawScope extensions)
  drawing/animation/                       StrokeAnimationEngine (@Stable playback engine, Phase 2),
                                           StrokeFramePath (timed frames -> partial Path + pen tip)
  presentation/screens/PracticeScreen.kt   the single screen; Column weights 0.25/0.65/0.10
  presentation/components/                 CharacterGuidePanel (+ Phase 3 session label / vocabulary
                                           word header + controls row),
                                           BrushColorPicker (HSV ink-color dialog, no new deps;
                                           opened from the Library app-bar swatch),
                                           AnimatedStrokePreview (Phase 2 demo
                                           + playback controls), StrokeOrderDiagram (static fallback),
                                           TracingCanvas (inkColor param colors user ink at draw
                                           time), PracticeActionBar (Clear/Again/Hard/Good/Easy
                                           + Next in FREE only)
  presentation/viewmodels/                 PracticeViewModel (Phase 3 session state machine,
                                           SessionPhase) + PracticeUiState + Factory
  presentation/theme/                      Color.kt (+ GradeAgain colors), Type.kt, Theme.kt
                                           (light-only Material 3)
```

## Conventions & architecture rules

- **Manual DI only.** Dependencies live in `AppContainer` (`HiraganaApplication.kt`): lazy Room DB + `AssetCharacterRepository` + `RoomPracticeRepository` + (Phase 3) `RoomLearningRepository` + `RoomVocabularyRepository`, sharing one lazy `HiraganaAssetDataSource`. ViewModels get them via `PracticeViewModel.Factory`. Do not introduce Hilt/Koin.
- **State split.** `PracticeViewModel` owns session/deck state only (`PracticeUiState` via `StateFlow`, driven by the Phase 3 `SessionPhase` state machine). ALL transient drawing state lives in `CanvasState`, a `@Stable` snapshot-state holder `remember`ed in `PracticeScreen` and cleared by a `LaunchedEffect` keyed on `PracticeUiState.canvasClearKey` — keep drawing state OUT of the ViewModel. Grading snapshots the session synchronously, fire-and-forgets the Room insert (and, depending on the active phase, an SRS reschedule), and advances the session.
- **Input path.** `TracingCanvas` uses `pointerInteropFilter` -> `StrokeCapture`: raw MotionEvents, batched historical samples, pressure coerced 0..2, single-active-pointer palm rejection, stylus + finger. Don't replace with `detectDragGestures` — pressure and history would be lost.
- **Render order** (TracingCanvas): 2x2 dashed grid -> font-rendered light-grey ghost glyph (native `canvas.drawText`) -> completed strokes -> active stroke. Ink width = 9dp base x widthScale from AVERAGE stroke pressure (clamped 0.55..1.6); midpoint quadratic-bezier smoothing in `StrokeRenderer`.
- **JSON parsing:** platform `org.json` on `Dispatchers.IO`. No kotlinx.serialization/Moshi/Gson — don't add one.
- **Dependencies** go through the version catalog `gradle/libs.versions.toml` only.
- **Theming:** Material 3 comes from Compose only; the platform theme is `android:Theme.Material.Light.NoActionBar` (`res/values/themes.xml`). No AppCompat, no Material Components XML themes.
- `StrokeOrderDiagram` is static: parses per-stroke SVG paths with Compose `PathParser`, scales from `viewBox` (default 100), numbered badges at each stroke's first `MoveTo`. Since Phase 2 it is the **fallback** rendering, used only when a character has no `strokeAnimations`.
- **Animation engine** (`drawing/animation/StrokeAnimationEngine.kt`) follows the `CanvasState` pattern: `@Stable`, UI-thread-only snapshot-state holder. Non-suspend controls (`play`/`pause`/`replay`/`setSpeed` 0.25–4×/`setAutoRepeat`/`reset`) only mutate desired state; a single `suspend run()` driver launched from a `LaunchedEffect` owns all suspend work (`Animatable` + LinearEasing clock, epoch-guarded outputs). One engine per character, `remember`ed on the glyph in `AnimatedStrokePreview`; `run()` and the auto-play `play()` need separate `LaunchedEffect`s.
- **`strokeAnimations` is derived data** — generated from `strokes[]` by `tools/generate_stroke_animations.py` (coordinates in viewBox space 0–100, NOT normalized; per-stroke times in ms, strictly increasing from 0). Never hand-edit it; re-run the generator, which self-validates.
- **SRS is pure domain logic (Phase 3).** `domain/learning/SrsScheduler.kt` (grade transitions) and `domain/learning/LessonGenerator.kt` (session builder) take no Android dependencies and read no clock internally — every date computation takes a `todayStartMillis: Long` parameter (or a `java.time.Clock` one layer up, in `PracticeViewModel`), so both are deterministic and unit-tested without instrumentation. Keep new learning logic in this package pure the same way.
- **Session state machine (Phase 3).** `PracticeViewModel` now owns a `SessionPhase` state machine (`LOADING`→`REVIEWS`→`NEW_CHARACTER`/`PRACTICE`→`VOCABULARY`→`COMPLETE`→`FREE`) inside `PracticeUiState`, same state-split rule as before: session/deck progress lives in the ViewModel, transient ink stays in `CanvasState`. `PracticeUiState.canvasClearKey` is the single monotonic counter `PracticeScreen`'s clearing `LaunchedEffect` keys off, replacing the old `currentIndex`/`characters` key pair.
- **Derived data covers vocabulary too.** `VocabularyWord.requiredCharacters` is never stored in `vocabulary.json` — it's derived from `kana` by `VocabularyWord.fromKana` (the one place the rule lives; parser and tests both call it), the same "one source of truth" discipline as `strokeAnimations`.
- **Room migrations are required from here on.** `exportSchema = true` (Phase 3) with the schema directory committed at `app/schemas/`; any future schema change needs a real `Migration`, additive-only where possible (see `HiraganaDatabase.MIGRATION_1_2` for the rename + new-tables pattern) — no more falling back to a version-1 wipe.
- **Reading/Writing split (branch `feat/reading-writing-split`).** An `AppMode` {READING, WRITING} (persisted `app_mode`) picks a track from a new home `OpeningScreen.kt`; the Study-scope Reading|Writing toggle replaced the old Printed/Handwritten selector (writing now always uses the handwritten ghost). READING adds: a reading **QUIZ** (`QuizKind`/`ReadingAnswerStyle` on `QuizState`, `ReadingQuizRunning` — multiple-choice or typed romaji via `RomajiMatch`, optional word-meaning); a reading **FLASHCARD** study mode (`ReadingFlashcardScreen` + `ReadingFlashcardState`/`ReadingCard`, optional "Type the reading" field); and reading scoring in a new **Room v5** table `reading_progress` (`ReadingProgressEntity`/Dao, `ReadingProgress` model with `mcqPercent`/`typePercent`/`colorPercent`, `ReadingProgressRepository`/`RoomReadingProgressRepository`), surfaced as Library reading-mode tint/rows/sort (MC/Type/Avg, no best). The Activity sets `windowSoftInputMode=adjustResize` for the typed-answer fields.

## Gotchas

- `strokeImage` PNGs referenced in the character JSONs (e.g. `a.png`) **do not exist** in assets — the field is reserved for later phases; nothing loads them.
- `svgPath` in the JSONs duplicates `strokes.joinToString(" ")`; the parser also defaults it that way.
- `strokeAnimations` parsing is all-or-nothing per character **and** consistency-checked against `strokes[]` (entry count, orders exactly 1..N, no empty point lists). Malformed or inconsistent data is voided with a `Log.w` (tag `HiraganaAssets`) and the guide panel falls back to the static diagram.
- Phase 2 playback is build-verified and adversarially code-reviewed (2026-07-04) but has **never run on a device/emulator**.
- `PracticeRepository.observeAttempts()` is implemented (DAO Flow) but has no caller yet.
- **45 unit tests cover `domain/learning`** (`SrsSchedulerTest`, `LessonGeneratorTest`, `VocabularyUnlockTest`) — the "zero tests" era is over, but UI and data layers are still untested, and nothing has run on a device/emulator (see Phase 3 status above).
- The legacy `Grade.OKAY` name (Phase 1/2 stored rows) decodes to `Grade.GOOD`; any other unrecognised stored grade string also falls back to `Grade.GOOD` (`RoomPracticeRepository.toDomain`). Similarly, an unrecognised stored `PracticeSource` falls back to `PracticeSource.FREE`.
- The Room table holding graded attempts was **renamed `practice_attempts` → `practice_history`** in the Phase 3 v1→v2 migration (`MIGRATION_1_2`), which also adds its `source` column (`DEFAULT 'FREE'`) and creates `review_cards`, `character_progress`, `vocabulary_progress`.
- **Session-first UX**: `PracticeActionBar`'s Next button only renders during `SessionPhase.FREE` — inside a session the `LessonGenerator` decides what's next, so there's nothing to skip to.
- Phase 3 (SRS scheduler, session state machine, vocabulary unlocking, Room v2) is build-verified and unit-tested (45/45 green) and adversarially reviewed (0 P0) but, like Phase 2, has **never run on a device/emulator** — this includes the live v1→v2 migration, which is only statically verified against the exported schema.
- Portrait-locked (`android:screenOrientation="portrait"` in the manifest).
- No dark theme: hardcoded light status/navigation bar colors (`#FBF8F4`) in `themes.xml`; `HiraganaTheme` is light-only.
- Room `exportSchema = true` as of Phase 3, with the schema history committed at `app/schemas/`.
- The directory is **not a git repo** (as of 2026-07-03).

## AI Hive coordination

Before doing substantial work, read `.aihive/board.md`. Append terse, dated entries to its Activity log (`- [AgentName] YYYY-MM-DD what you did/are doing`). **Never edit the roster block** between `AIHIVE:ROSTER:BEGIN/END` markers.

## Docs index

- `README.md` — user-facing overview, features, quick start.
- `docs/ARCHITECTURE.md` — layers, state ownership, DI graph, data flow.
- `docs/DRAWING.md` — input capture, smoothing, pressure width, render pipeline.
- `docs/DATA.md` — asset JSON schema, repositories, Room schema.
- `docs/PHASE2-DESIGN.md` — Phase 2 contract (strokeAnimations schema, engine API, UI contract) + as-built notes.
- `docs/PHASE3-DESIGN.md` — Phase 3 contract (SRS scheduler, lesson generator, vocabulary unlocking, Room v2 migration, session UI) + as-built amendments.
- `docs/HISTORY.md` — build/session history and Phase 1 vs Phase 2 vs Phase 3 status.
