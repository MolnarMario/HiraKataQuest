# CLAUDE.md — HiraKataQuest

**HiraKataQuest** — an Android app for practicing Japanese **kana** handwriting (hiragana + katakana, all groups): trace a light-grey ghost glyph on a genkouyoushi-style grid with stylus or finger, self-grade (Again/Hard/Good/Easy), attempts persist to Room. A Phase 3 spaced-repetition scheduler and session generator decide what to review/introduce/practice each visit; handwriting is auto-scored, and a "Quiz me!" mode tests writing from memory. Kotlin, Jetpack Compose, Material 3, MVVM, Room via KSP, manual DI (no Hilt). Single module `:app`, single screen, package `com.hive.hiragana` (the package/`applicationId` is unchanged by the rename — the user-facing name lives in `strings.xml`).

**Status: Phase 1 (static tracing MVP), Phase 2 (animated stroke-order demonstrations), and Phase 3 (Smart Learning System — SRS scheduling, personalized sessions, vocabulary unlocking) are all COMPLETE**, plus a 2026-07-04 post-device-feedback batch: KanjiVG-sourced stroke geometry (viewBox 109, CC BY-SA attribution required — see `tools/import_kanjivg.py`), brush-ribbon ink rendering, a Start-next-session button on the COMPLETE summary, a Library overlay for free practice of any character/word, and a force-dark opt-out. Two 2026-07-04 content expansions then grew the deck from 5 to the full 46-character basic gojūon (あ〜ん via the same KanjiVG pipeline, frequencies = gojūon order 1–46; no dakuten/yōon yet) and `vocabulary.json` to 153 common words (every kana except を covered). A 2026-07-04 **katakana section** then added a second script alongside hiragana (device-verified on the Pixel 7): a `Script` enum + `assets/katakana/` (46 homologues + the long-vowel mark ー = 47 entries, frequencies 101–147, same KanjiVG pipeline) + 66 katakana loanwords, a persisted `activeScript` setting, and a Hiragana/Katakana selector in the Library that scopes what is studied, quizzed, and browsed. The learning engine and Room schema were **reused unchanged** (script filtering happens in the ViewModel before `LessonGenerator`; katakana rows are additive — no migration). A 2026-07-04 **dakuten/handakuten** addition then added the voiced (が/ざ/だ/ば) and semi-voiced (ぱ) kana for both scripts (25 each: hiragana freq 47–71, katakana 148–172, same pipeline) plus their words, but as an **opt-in**: a persisted `includeDiacritics` setting (default OFF) gates them out of session generation, the quiz, word-unlock, and the Library lists so beginners meet only the base gojūon; a "Voiced kana (゛゜)" toggle in the Library folds them into the active script (after the base kana, by frequency) when the learner is ready. Classification is derived from the glyph (`KanaKind`, via Unicode NFD); engine and Room again unchanged (dakuten rows additive; off-scope cards are inert orphans). A 2026-07-04 **yōon** addition then added the contracted syllables (きゃ/しゅ/きょ …, 33 per script: hiragana freq 72–104, katakana 173–205) plus their words — the app's first **composite characters** (a 2-code-point glyph like "きゃ" with NO single KanjiVG file, so geometry is composed from the base kana + a small ゃ/ゅ/ょ by `tools/import_kanjivg.py`). Yōon are a **second independent opt-in** (persisted `includeYoon`, default OFF; a "Combined kana (ゃゅょ)" Library toggle) gating them out of sessions/quiz/unlock/lists until the learner is ready. A centralized `KanaUnits` splitter groups a base + small ゃゅょ into one reading unit so yōon vocabulary unlocks correctly; engine and Room again unchanged (yōon rows additive, off-scope orphans inert). Device-verified on the Pixel 7. A 2026-07-04 **extended-kana** addition then **completed the kana inventory**: the small tsu (っ/ッ), small vowels (ぁぃぅぇぉ/ァィゥェォ), ゔ/ヴ, 22 extended-katakana foreign-sound composites (ファ/ティ/ウェ/ヴァ/シェ/チェ/ツォ …), and the obsolete ゐゑ/ヰヱ — behind a **third opt-in** (`includeExtended`, default OFF; "Extended kana (っ ァ)" Library toggle). `KanaUnits` now fuses small vowels too (so ソファ→ソ, ファ unlocks); the extended composites reuse the yōon composition; ゔ/ヴ classify as dakuten (they sit under the Voiced opt-in, not Extended). The app now covers **all hiragana and katakana** (base, dakuten/handakuten, yōon, extended): 113 hiragana + 136 katakana characters, ~397 words. Device-verified. Phase 1 `assembleDebug` on 2026-07-03; Phase 2 implemented and built 2026-07-03, re-verified/reviewed/fixed 2026-07-04; Phase 3 implemented, built, and unit-tested 2026-07-04 (`assembleDebug` + `testDebugUnitTest` both green, 45 unit tests / 0 failures — the project's first tests) and adversarially reviewed (2 agents, 0 P0, both verdicts ship; review fixes applied per `docs/PHASE3-DESIGN.md` §11). **All of the above has since been device-verified on a Pixel 7 (Android 16)**, including the live Room migrations. Further additions, also device-verified: a handwriting-quality **scorer** (`domain/scoring/` — pure geometry form/fit dials + ML Kit digital-ink recognition; advisory only, never touches SRS) persisted via **Room v3** (`MIGRATION_2_3` adds nullable `form_score`/`fit_score`/`recognition_score` columns to `practice_history`); a write-from-memory **"Quiz me!"** overlay (`QuizScreen`, `PracticeSource.QUIZ`, no SRS side effects); a Library **list⇄chart** view (a gojūon cheat-sheet built by `domain/model/KanaChart.kt`); a persisted **brush ink color**; and a **rebrand to "HiraKataQuest"** with a new hinomaru launcher icon (vermilion circle + white print あ/ア, adaptive vector from `ic_launcher_foreground.xml`/`ic_launcher_monochrome.xml`). History: `docs/HISTORY.md`; Phase 2 contract: `docs/PHASE2-DESIGN.md`; Phase 3 contract + as-built notes: `docs/PHASE3-DESIGN.md`.

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
                                           vocabulary.json. ONE such directory PER SCRIPT — the FULL
                                           kana inventory: hiragana 113 chars (46 base 1–46 + 25
                                           dakuten 47–71 + 33 yōon 72–104 + 9 extended 105–113; 247
                                           words) + katakana 136 chars (47 base 101–147 + 25 dakuten
                                           148–172 + 33 yōon 173–205 + 9 extended singles 206–214 + 22
                                           foreign composites 215–236; 150 words). strokes[] geometry
                                           is KanjiVG-sourced, viewBox 109 (yōon + extended-katakana
                                           strokes are COMPOSED from base + small kana). Script (from
                                           directory), KanaKind (voiced, NFD) and yōon/extended-ness
                                           (KanaUnits) are all derived, never stored in JSON
tools/import_kanjivg.py                    imports vendored KanjiVG SVGs (tools/kanjivg/, CC BY-SA)
                                           into the character JSONs as absolute M/L/C/Q strokes[];
                                           loops over BOTH script dirs (index.json-driven); CODEPOINTS
                                           maps each id (incl. kata_*) to its hex codepoint;
                                           ALWAYS re-run generate_stroke_animations.py after it
tools/generate_stroke_animations.py        offline generator+validator for strokeAnimations (Python 3);
                                           also loops over both script dirs
app/schemas/                               Room exported-schema JSON history (Phase 3; exportSchema=true)
app/src/test/java/com/hive/hiragana/domain/{learning,model}/
                                           SrsSchedulerTest, LessonGeneratorTest, VocabularyUnlockTest,
                                           ScriptTest, ScriptScopeTest (+ a scoring package from another
                                           workstream) — JUnit4, run with testDebugUnitTest
app/src/main/java/com/hive/hiragana/
  HiraganaApplication.kt                   Application + AppContainer (manual DI)
  MainActivity.kt                          ComponentActivity -> HiraganaTheme -> PracticeScreen
  domain/model/                            HiraganaCharacter (+ id: String, asset basename),
                                           Grade (AGAIN/HARD/GOOD/EASY, Phase 3; legacy OKAY -> GOOD),
                                           PracticeAttempt (+ source: PracticeSource
                                           REVIEW/NEW/VOCAB/FREE/QUIZ),
                                           StrokeDefinition (StrokeFrame timing data, Phase 2),
                                           ReviewCard, VocabularyWord (+ fromKana derivation),
                                           StudySession, CharacterProgress, VocabularyProgress (Phase 3),
                                           PracticeLayout (1×1/2×2/3×3 canvas repetition tiling),
                                           Script (HIRAGANA/KATAKANA; key = asset dir + pref value),
                                           KanaKind (BASE/DAKUTEN/HANDAKUTEN, NFD-derived),
                                           KanaUnits (splits kana into reading units — base + small
                                           ゃゅょ/vowel = one unit; classifies yōon + extended),
                                           ScriptScope (pure 4-flag filter: Script + voiced + yōon +
                                           extended opt-ins); HiraganaCharacter.script/.kind/.isYoon
                                           /.isExtended + VocabularyWord.script/.usesDiacritics/
                                           .usesYoon/.usesExtended (all derived)
  domain/learning/                         SrsScheduler (pure SM-2-style transitions, clock-injected),
                                           LessonGenerator (pure session builder, clock-injected) — Phase 3
  domain/model/KanaChart.kt                pure gojūon-chart builder (scoped chars → aligned
                                           TableSections + FlowSections) for the Library grid view.
                                           SCRIPT-AGNOSTIC: the VM passes a script-filtered deck in
  domain/scoring/                          GeometryScorer (pure handwriting-quality scorer: form +
                                           fit dials, stroke count/order/direction, no Android deps),
                                           CharacterScore(+combine)/GeometryScore/RecognitionResult,
                                           ScoringMode (FORM/FIT/BOTH/OFF), CharacterScoreSummary,
                                           Pt/CellRect — advisory scoring, never touches SRS
  domain/repository/                       CharacterRepository, PracticeRepository (+observeScoreSummaries),
                                           LearningRepository, VocabularyRepository,
                                           SettingsRepository (interfaces), HandwritingRecognizer (ML Kit iface)
  data/recognition/MlKitHandwritingRecognizer.kt
                                           ML Kit Digital Ink (Japanese, v19.0.0 for 16KB page-size
                                           support); async model download, graceful geometry-only fallback
  data/assets/HiraganaAssetDataSource.kt   org.json parsing of character + vocabulary asset JSONs
                                           on Dispatchers.IO; loads every Script's assets/<key>/ dir
                                           (per-root graceful skip) and tags each char with its Script
  data/repository/                         AssetCharacterRepository (Mutex-guarded cache, sorts the
                                           merged multi-script deck by frequency ascending),
                                           RoomPracticeRepository (entity<->domain mapping, legacy
                                           grade/source fallback), RoomLearningRepository,
                                           RoomVocabularyRepository (Mutex-cached getWords),
                                           PrefsSettingsRepository (SharedPreferences, NOT DataStore —
                                           grid_enabled + ghost_enabled + practice_layout_span +
                                           active_script + diacritics_included + yoon_included +
                                           extended_included + library_grid_view + scoring_mode +
                                           brush_ink_color don't justify the dep)
  data/local/                              Room v2 ('hiragana-practice.db', exportSchema=true):
                                           PracticeAttemptEntity/Dao (table practice_history, was
                                           practice_attempts pre-Phase-3), ReviewCardEntity/Dao,
                                           CharacterProgressEntity/Dao, VocabularyProgressEntity/Dao,
                                           HiraganaDatabase (MIGRATION_1_2)
  drawing/stroke/                          StrokePoint, UserStroke, DrawingSession,
                                           StrokeCapture (MotionEvent -> CanvasState)
  drawing/canvas/                          CanvasState (@Stable snapshot-state holder),
                                           StrokeRenderer (filled brush ribbons: per-point
                                           pressure+speed width, smoothing, tapers),
                                           CanvasRenderers (grid + ghost glyph DrawScope extensions)
  drawing/animation/                       StrokeAnimationEngine (@Stable playback engine, Phase 2),
                                           StrokeFramePath (timed frames -> partial Path + pen tip)
  presentation/screens/PracticeScreen.kt   the main screen; Column weights 0.25/0.65/0.10
  presentation/screens/LibraryScreen.kt    full-screen library overlay (all characters + words,
                                           tap → FREE practice; Back/arrow closes). Chrome is a
                                           TopAppBar (view toggle + collapsible search + Filter +
                                           Quiz me!) over the tabs; script + advanced-kana opt-ins
                                           live in a "Study scope" ModalBottomSheet (Filter icon,
                                           badge dot when non-default) — idle header ~2 rows.
                                           List⇄chart(grid) toggle (persisted isLibraryGridView):
                                           chart = gojūon cheat-sheet from KanaChartBuilder
                                           (uniform fixed cell size; base/voiced aligned a/i/u/e/o
                                           tables, yōon 3-col, ん/ー/extended in gutter-aligned
                                           flow); words = FlowRow of kana/romaji/meaning cards
  presentation/screens/QuizScreen.kt       write-from-memory quiz overlay above the library:
                                           SETUP (5/10/20/All presets + exact-count box w/ +/−
                                           stepper + quiz-local grid switch) → RUNNING (romaji
                                           prompt, ghost off until Show answer reveals it on the
                                           canvas, Back/Next(=Finish on last) skip w/o grading)
                                           → SUMMARY (grades by char id, Skipped when ungraded);
                                           attempts record as PracticeSource.QUIZ, never touches SRS
  presentation/components/                 CharacterGuidePanel (+ Phase 3 session label / vocabulary
                                           word header; bottom controls row: Library button +
                                           1×1/2×2/3×3 layout picker + grid & ghost toggles +
                                           brush-color swatch), BrushColorPicker (HSV ink-color
                                           dialog: sat/value square + hue slider + theme presets
                                           + live preview; no new deps), AnimatedStrokePreview
                                           (Phase 2 demo + playback controls), StrokeOrderDiagram
                                           (static fallback), TracingCanvas (tiles span² repetition
                                           slots into the column count that maximizes cell size;
                                           `inkColor` param colors user ink at draw time),
                                           PracticeActionBar (Clear/Again/Hard/Good/Easy
                                           + Next in FREE only)
  presentation/viewmodels/                 PracticeViewModel (Phase 3 session state machine,
                                           SessionPhase; QuizState/QuizStage overlay state)
                                           + PracticeUiState + Factory
  presentation/theme/                      Color.kt (+ GradeAgain colors), Type.kt, Theme.kt
                                           (light-only Material 3)
```

## Conventions & architecture rules

- **Manual DI only.** Dependencies live in `AppContainer` (`HiraganaApplication.kt`): lazy Room DB + `AssetCharacterRepository` + `RoomPracticeRepository` + (Phase 3) `RoomLearningRepository` + `RoomVocabularyRepository`, sharing one lazy `HiraganaAssetDataSource`. ViewModels get them via `PracticeViewModel.Factory`. Do not introduce Hilt/Koin.
- **State split.** `PracticeViewModel` owns session/deck state only (`PracticeUiState` via `StateFlow`, driven by the Phase 3 `SessionPhase` state machine). ALL transient drawing state lives in `CanvasState`, a `@Stable` snapshot-state holder `remember`ed in `PracticeScreen` and cleared by a `LaunchedEffect` keyed on `PracticeUiState.canvasClearKey` — keep drawing state OUT of the ViewModel. Grading snapshots the session synchronously, fire-and-forgets the Room insert (and, depending on the active phase, an SRS reschedule), and advances the session.
- **Input path.** `TracingCanvas` uses `pointerInteropFilter` -> `StrokeCapture`: raw MotionEvents, batched historical samples, pressure coerced 0..2, single-active-pointer palm rejection, stylus + finger. Don't replace with `detectDragGestures` — pressure and history would be lost.
- **Render order** (TracingCanvas): 2x2 dashed grid -> font-rendered light-grey ghost glyph (native `canvas.drawText`) -> completed strokes -> active stroke. Ink is a **filled variable-width brush ribbon** (`StrokeRenderer.buildBrushPath`): 20dp base width x cell-proportional scale (`brushScale(cellSide, referenceSide)` in TracingCanvas.kt — reference is the single-character 2×2 cell; smaller cells, incl. word rows and dense layouts, thin the ink linearly, growth above the reference is half-slope so the 1×1 sheet lands at ~1.5×; safe because every cell-size change bumps `canvasClearKey`) x per-point pressure scale (0.55..1.6, same curve as Phase 1) x per-point speed thinning (fast flicks thin to 0.55), moving-average width smoothing, head taper always, tail taper only on commit (the live stroke keeps a full-width tip), round end caps as discs; centerline and both edges midpoint-quadratic smoothed. Width is baked into the path in px (`CanvasState.brushBaseWidthPx`, refreshed each draw pass), so consumers just fill — no Stroke style, no width multiplier.
- **Brush ink color is a purely-visual persisted setting.** The user picks the ink color (default charcoal `InkBlack`) via the `BrushColorPicker` HSV dialog, opened from the swatch button in `CharacterGuidePanel`'s controls row. Stored as a raw ARGB `Int` (`SettingsRepository.get/setBrushInkColor`, key `brush_ink_color`, default `0xFF20222E` = `InkBlack`) — any color is valid, so no enum/decoder. It rides `PracticeUiState.brushInkColor` (seeded at construction, preserved across every state reconstruction — same discipline as `activeScript`/`isLibraryGridView`) into `TracingCanvas`'s `inkColor` param, which colors user ink **at draw time** (`drawPath(path, color = inkColor)`) for BOTH practice and the quiz (same `TracingCanvas`). Reactive param, NOT a `CanvasState` field, so a setting change actually recomposes/redraws. It touches ONLY rendering — never stroke geometry, scoring, or scheduling; the didactic grey ghost glyph and stroke-order preview are unaffected. Committed on the picker's "Done" (Cancel/dismiss discards).
- **JSON parsing:** platform `org.json` on `Dispatchers.IO`. No kotlinx.serialization/Moshi/Gson — don't add one.
- **Dependencies** go through the version catalog `gradle/libs.versions.toml` only.
- **Theming:** Material 3 comes from Compose only; the platform theme is `android:Theme.Material.Light.NoActionBar` (`res/values/themes.xml`). No AppCompat, no Material Components XML themes.
- `StrokeOrderDiagram` is static: parses per-stroke SVG paths with Compose `PathParser`, scales from `viewBox` (default 100), numbered badges at each stroke's first `MoveTo`. Since Phase 2 it is the **fallback** rendering, used only when a character has no `strokeAnimations`.
- **Animation engine** (`drawing/animation/StrokeAnimationEngine.kt`) follows the `CanvasState` pattern: `@Stable`, UI-thread-only snapshot-state holder. Non-suspend controls (`play`/`pause`/`replay`/`setSpeed` 0.25–4×/`setAutoRepeat`/`reset`) only mutate desired state; a single `suspend run()` driver launched from a `LaunchedEffect` owns all suspend work (`Animatable` + LinearEasing clock, epoch-guarded outputs). One engine per character, `remember`ed on the glyph in `AnimatedStrokePreview`; `run()` and the auto-play `play()` need separate `LaunchedEffect`s.
- **`strokes[]` and `strokeAnimations` are both derived data.** Stroke geometry comes from vendored KanjiVG SVGs (`tools/kanjivg/`, CC BY-SA 3.0 — keep the attribution in README/docs) via `tools/import_kanjivg.py`, which normalizes to absolute M/L/C/Q and sets `viewBox: 109`; `strokeAnimations` is generated from `strokes[]` by `tools/generate_stroke_animations.py` (coordinates in viewBox space 0–109, NOT normalized; per-stroke times in ms, strictly increasing from 0). Never hand-edit either; re-run import then generator (each self-validates).
- **SRS is pure domain logic (Phase 3).** `domain/learning/SrsScheduler.kt` (grade transitions) and `domain/learning/LessonGenerator.kt` (session builder) take no Android dependencies and read no clock internally — every date computation takes a `todayStartMillis: Long` parameter (or a `java.time.Clock` one layer up, in `PracticeViewModel`), so both are deterministic and unit-tested without instrumentation. Keep new learning logic in this package pure the same way.
- **Session state machine (Phase 3).** `PracticeViewModel` now owns a `SessionPhase` state machine (`LOADING`→`REVIEWS`→`NEW_CHARACTER`/`PRACTICE`→`VOCABULARY`→`COMPLETE`→`FREE`) inside `PracticeUiState`, same state-split rule as before: session/deck progress lives in the ViewModel, transient ink stays in `CanvasState`. `PracticeUiState.canvasClearKey` is the single monotonic counter `PracticeScreen`'s clearing `LaunchedEffect` keys off, replacing the old `currentIndex`/`characters` key pair.
- **Derived data covers vocabulary too.** `VocabularyWord.requiredCharacters` is never stored in `vocabulary.json` — it's derived from `kana` by `VocabularyWord.fromKana` (the one place the rule lives; parser and tests both call it), the same "one source of truth" discipline as `strokeAnimations`. Likewise `VocabularyWord.script` is derived from `kana` (any Katakana-block char → KATAKANA).
- **Scripts are a data + wiring concern, not an engine change.** A `Script` (HIRAGANA/KATAKANA) lives on every `HiraganaCharacter` (set by the loader from the asset directory — the directory IS the script; nothing new in the JSON). `HiraganaAssetDataSource` loads BOTH `assets/<script>/` dirs and concatenates (per-root graceful skip — a missing dir is logged, not fatal); the one `AssetCharacterRepository` cache sorts the merged deck by frequency (hiragana 1–46, katakana 101–147, disjoint bands so hiragana studies first). `PracticeViewModel` keeps the FULL merged deck in `PracticeUiState.characters`/`allWords` (so `pronunciationOf` and stored romaji resolve across scripts) and derives `activeScriptCharacters` via `ScriptScope` to feed session generation, the quiz pool, and FREE practice — so `LessonGenerator`/`SrsScheduler` stay untouched (off-script `ReviewCard`s are inert orphans). The Library's Hiragana/Katakana selector calls `setActiveScript`, which persists via `SettingsRepository` and does a phase-independent rebuild (NOT `startNextSession`, whose guard is COMPLETE/FREE-only; note `startNextSession` itself must preserve `activeScript`/`includeDiacritics` across its state reconstruction). New scripts = new asset dir + `CODEPOINTS` entries + tool run; **no Room migration** (new `kata_*` ids / katakana kana are additive rows).
- **Voiced kana (dakuten/handakuten) are opt-in.** `KanaKind` classifies every glyph BASE vs DAKUTEN vs HANDAKUTEN via Unicode NFD (voiced kana decompose to base + U+3099/U+309A), so nothing is stored in JSON. A persisted `SettingsRepository.includeDiacritics` (default **false**) is threaded into `ScriptScope.scopedCharacters/scopedWords`, which drop voiced kana (and words that use them, via `VocabularyWord.usesDiacritics`) unless it's on — gating session generation, the quiz, unlocks, AND the Library lists in one place. The Library's "Voiced kana" `FilterChip` calls `setIncludeDiacritics` (same persist-then-phase-independent-rebuild pattern as `setActiveScript`; both preserve `isLibraryOpen` so the selector/toggle act as **in-place filters** — the rebuilt session waits behind the still-open overlay rather than bouncing the user out). Voiced-kana frequencies sit above the base band per script (hiragana 47–71, katakana 148–172), so even once opted in the base gojūon is always introduced first; progress made on voiced kana while enabled survives toggling off (their cards just go inert as off-scope orphans).
- **Yōon are a second, independent opt-in — and the only composite characters.** A yōon (きゃ, しゅ …) has a 2-code-point glyph with NO single Unicode point, so: (a) its `strokes[]` are **composed** offline by `import_kanjivg.py` (base -i kana scaled left + small ゃ/ゅ/ょ scaled bottom-right — see `YOON`/`compose_yoon`), the app's only multi-source geometry; (b) `KanaUnits.split` groups a base + small ゃゅょ into ONE reading unit — the single rule shared by `VocabularyWord.fromKana` (so "きょう"→`{きょ, う}` matches the "きょ" deck char), `PracticeUiState.canvasGlyphs` (one cell per unit), `pronunciationOf`, and the stored word romaji; (c) `HiraganaCharacter.isYoon`/`VocabularyWord.usesYoon` gate them via persisted `includeYoon` (default **false**, the "Combined kana" `FilterChip`, `setIncludeYoon`), **independent** of the voiced opt-in — `ScriptScope` checks yōon first, so a voiced-base yōon like ぎゃ is gated by `includeYoon` alone. `drawGhostCharacter` shrinks-to-fit so the wider 2-glyph ghost never clips. Yōon frequencies sit above both prior bands (hiragana 72–104, katakana 173–205). Same additive/no-migration/inert-orphan story.
- **Extended kana are a third opt-in — completing the inventory.** `includeExtended` (default **false**, "Extended kana (っ ァ)" `FilterChip`, `setIncludeExtended`) gates the small tsu (っ/ッ), small vowels (ぁぃぅぇぉ/ァィゥェォ), 22 extended-katakana composites (ファ, ティ, ウェ, ヴァ, シェ, チェ, ツォ …), and obsolete ゐゑ/ヰヱ. `KanaUnits` now fuses small vowels as well as small ゃゅょ (so ソファ→`{ソ, ファ}` unlocks against the "ファ" deck char), but the small tsu stays its own unit (きって→`{き, っ, て}`); `isExtended`/`usesExtended` = has small vowel OR sokuon OR obsolete. `ScriptScope` is now a 4-flag gate with priority **extended → yōon → voiced**, so each opt-in stays independent — e.g. a foreign combo like ヴァ (voiced base + small vowel) is gated by `includeExtended` alone. Extended composites reuse `compose_composite` (the merged `COMPOSITES = YOON + EXTENDED` table); **`ゔ`/`ヴ` are NOT extended** — they decompose to a dakuten (`KanaKind`) so they live under the Voiced opt-in. Extended frequencies sit above yōon (hiragana 105–113, katakana 206–236). With all three opt-ins the deck is the complete kana set.
- **Room migrations are required from here on.** `exportSchema = true` (Phase 3) with the schema directory committed at `app/schemas/`; any future schema change needs a real `Migration`, additive-only where possible (see `HiraganaDatabase.MIGRATION_1_2` for the rename + new-tables pattern) — no more falling back to a version-1 wipe.

## Gotchas

- `strokeImage` PNGs referenced in the character JSONs (e.g. `a.png`) **do not exist** in assets — the field is reserved for later phases; nothing loads them.
- `svgPath` in the JSONs duplicates `strokes.joinToString(" ")`; the parser also defaults it that way.
- `strokeAnimations` parsing is all-or-nothing per character **and** consistency-checked against `strokes[]` (entry count, orders exactly 1..N, no empty point lists). Malformed or inconsistent data is voided with a `Log.w` (tag `HiraganaAssets`) and the guide panel falls back to the static diagram.
- Phase 2 playback is build-verified, adversarially code-reviewed (2026-07-04), and **device-verified on a Pixel 7** (the stroke-order demo animates correctly — note it pins `MotionDurationScale` so it still plays when system animations are disabled).
- `PracticeRepository.observeAttempts()` is implemented (DAO Flow) but has no caller yet.
- **~110 unit tests** cover the pure domain logic (and now the ViewModel) — `domain/learning` (`SrsSchedulerTest`, `LessonGeneratorTest`, `VocabularyUnlockTest`) plus `domain/model` (`KanaChartTest`, `KanaKindTest`, `KanaUnitsTest`, `ScriptTest`, `ScriptScopeTest`) and `domain/scoring` (`GeometryScorerTest`, `CharacterScoreTest`). The UI and data layers remain unit-untested, but the app has been device-verified on a Pixel 7 (see status above).
- The legacy `Grade.OKAY` name (Phase 1/2 stored rows) decodes to `Grade.GOOD`; any other unrecognised stored grade string also falls back to `Grade.GOOD` (`RoomPracticeRepository.toDomain`). Similarly, an unrecognised stored `PracticeSource` falls back to `PracticeSource.FREE`.
- The Room table holding graded attempts was **renamed `practice_attempts` → `practice_history`** in the Phase 3 v1→v2 migration (`MIGRATION_1_2`), which also adds its `source` column (`DEFAULT 'FREE'`) and creates `review_cards`, `character_progress`, `vocabulary_progress`.
- **Session-first UX**: `PracticeActionBar`'s Next button only renders during `SessionPhase.FREE` — inside a session the `LessonGenerator` decides what's next, so there's nothing to skip to. The COMPLETE summary's primary action is `startNextSession()` (regenerates a session from the just-updated cards; usually introduces the next character). The **Library** overlay (`isLibraryOpen` in `PracticeUiState`, entries in the guide panel + COMPLETE summary) lets the user free-practice ANY character or word — `practiceCharacter`/`practiceWord` enter FREE with the whole deck / the word's kana (`freeWord` drives the word header), recorded as `PracticeSource.FREE` with no SRS/unlock side effects.
- Phase 3 (SRS scheduler, session state machine, vocabulary unlocking) is build-verified, unit-tested, adversarially reviewed (0 P0), and **device-verified on a Pixel 7** — including the live v1→v2 Room migration (verified on-device by upgrading over an existing v1 DB). The DB is now at **v3** (the scoring-columns migration `MIGRATION_2_3`), also device-verified.
- Portrait-locked (`android:screenOrientation="portrait"` in the manifest).
- No dark theme: hardcoded light status/navigation bar colors (`#FBF8F4`) in `themes.xml`; `HiraganaTheme` is light-only, and `android:forceDarkAllowed=false` opts out of system force-dark (without it, Pixel/Android 16 inverts the whole UI when the system is in dark mode — found in the first device run).
- Room `exportSchema = true` as of Phase 3, with the schema history committed at `app/schemas/`.
- The project is a **git repository published at `github.com/MolnarMario/HiraKataQuest`** (public). `.gitignore` excludes build outputs, `local.properties`, `.idea/`, and `.aihive/` (coordination metadata is not part of the app).

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
