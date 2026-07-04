# Project History & Provenance

**Hiragana Trace** was built entirely by AI agents from three written prompts (Phases 1–3, Phase 1 and 2 reproduced verbatim below; Phase 3's prompt is summarized in `docs/PHASE3-DESIGN.md` §0, which also records how it was reconciled against the shipped codebase before implementation). The original build session was lost before any documentation was written; this file is the recovery record of what was asked for, what was delivered, and how that was verified. Everything in this document was re-verified against the source tree on 2026-07-03 during the post-loss re-audit, and again on 2026-07-04 as Phase 3 landed.

Companion docs: [README.md](../README.md) (overview), [CLAUDE.md](../CLAUDE.md) (build commands and conventions), [docs/ARCHITECTURE.md](ARCHITECTURE.md), [docs/DRAWING.md](DRAWING.md), [docs/DATA.md](DATA.md), [docs/PHASE2-DESIGN.md](PHASE2-DESIGN.md), [docs/PHASE3-DESIGN.md](PHASE3-DESIGN.md). The live activity log is `.aihive/board.md`.

---

## 1. Timeline

Events of **2026-07-03 and 2026-07-04**, as recorded in `.aihive/board.md`:

| # | Event | Detail |
|---|-------|--------|
| 1 | Phase 1 scaffolded | Full project written in one pass: Kotlin sources, Gradle config, `assets/hiragana/` JSONs, README. The board log records 29 Kotlin files at scaffold time (the later audit entry says 30; the on-disk count under `app/src/main/java/` today is 29). Not compiled at this point — the machine had no JDK or Android SDK. |
| 2 | Build toolchain provisioned | Temurin JDK 17 → `C:\Users\Mario\.jdks\jdk-17.0.19+10`, Android SDK 34 → `C:\Users\Mario\AppData\Local\Android\Sdk`, Gradle 8.7 distribution → `C:\Users\Mario\.gradle-dist\gradle-8.7`. Done specifically to produce a user-requested demo APK. |
| 3 | First build | `assembleDebug` — **BUILD SUCCESSFUL in 2m05s with zero code fixes**. The scaffolded code compiled as written. APK at `app/build/outputs/apk/debug/app-debug.apk`, copy at `C:\Users\Mario\Downloads\HiraganaTrace-debug.apk` (9.7 MB; both files still present and verified). |
| 4 | **Session lost** | The original agent session (all in-context knowledge of the build) was lost. No documentation existed yet. |
| 5 | Full codebase re-audit | Fresh session re-read the entire codebase with 5 subagents and gap-checked it against both prompts. Verdict: Phase 1 = all 16 requirements implemented; Phase 2 = 0 of 7 requirement groups present. Also noted: no tests, no Gradle wrapper JAR committed. |
| 6 | Retroactive documentation | This doc set (CLAUDE.md, docs/ARCHITECTURE.md, docs/DRAWING.md, docs/DATA.md, docs/HISTORY.md, README refresh, KDoc pass) written **before** starting Phase 2 implementation. |
| 7 | Phase 2 contract + parallel workstreams | `docs/PHASE2-DESIGN.md` and `domain/model/StrokeDefinition.kt` written first as a fixed contract; three parallel workstreams implemented against it — **D** data (`tools/generate_stroke_animations.py`, all 5 JSONs regenerated + validated, model/parser extension), **E** engine (`drawing/animation/`), **U** UI (`AnimatedStrokePreview` + guide-panel swap). |
| 8 | Phase 2 integration build | One integration fix (JVM setter clash on the engine's `autoRepeat`), then `assembleDebug` — **BUILD SUCCESSFUL** (~23:51). Queued next step: adversarial review. |
| 9 | **Second session lost** | Usage limit hit before the adversarial review ran. The docs written at step 6 still described Phase 2 as "not started"; the board log was the only record of steps 7–8. |
| 10 | Phase 2 re-verification (**2026-07-04**) | Resumed session confirmed the recovered state: build reproducible (37/37 tasks up-to-date), all 5 JSONs pass the generator's validator (validate-only run), packaged APK assets contain `strokeAnimations`. `Downloads\HiraganaTrace-debug.apk` refreshed to the Phase 2 build. |
| 11 | Adversarial review + fixes (**2026-07-04**) | Two independent review agents (engine; UI/data): **0 P0 defects, verdict ship-for-demo**. Fixed same day: P1 — guide-panel controls row crushed to zero width on short screens (square constraint moved onto the preview canvas); P2 — parser now consistency-checks `strokeAnimations` vs `strokes[]` and logs voiding. Rebuilt successfully; this doc set reconciled with reality. |
| 12 | Phase 3 contract + plan approval (**2026-07-04**) | `docs/PHASE3-DESIGN.md` written as a fixed contract before any Phase 3 code, resolving five prompt-vs-codebase conflicts (§0: extend `HiraganaCharacter`/`Grade` rather than replace, keep the entity/domain split for `ReviewCard`, scope vocabulary to characters already shipped, rename `practice_attempts`→`practice_history`). User approved the plan, including scoping vocabulary to words composable from あいうえお and the session-first UX with a free-practice escape hatch. |
| 13 | Phase 3 B0 + four parallel workstreams (**2026-07-04**) | Orchestrator landed the B0 domain contract (`Grade` extended to AGAIN/HARD/GOOD/EASY, `HiraganaCharacter.id`, `ReviewCard`/`VocabularyWord`/`StudySession`/`CharacterProgress`/`VocabularyProgress`, `LearningRepository`/`VocabularyRepository` interfaces, `PracticeAttempt.source`), compile-verified, then launched four workstreams against it: **S** scheduler (`SrsScheduler` + tests), **L** lesson engine (`LessonGenerator` + tests), **D** data (Room v1→v2, `vocabulary.json`, `AppContainer` wiring, legacy-grade mapping), **U** session UI (`PracticeViewModel` state machine, `PracticeUiState`, `PracticeActionBar`, `CharacterGuidePanel`). |
| 14 | Phase 3 integration build (**2026-07-04**) | All four workstreams landed. **Integration green on the first try**: `assembleDebug` + `testDebugUnitTest` BUILD SUCCESSFUL in 42s, zero cross-workstream fixes needed. **First-ever test run**: 39 tests, 0 failures (SrsScheduler 15, LessonGenerator 13, VocabularyUnlock 11). Room schema exported (`app/schemas/.../2.json`); `MIGRATION_1_2` statically verified against it, all 4 tables matching column-for-column. `Downloads\HiraganaTrace-debug.apk` refreshed. |
| 15 | Phase 3 adversarial review + fixes (**2026-07-04**) | Two independent review agents (logic/data+migration; session UI): **0 P0 defects, both verdicts ship**. Findings fixed same day: P1 — `wordsUnlocked` mislabeled reinforcement picks as unlocked (fixed via `newlyUnlockedKana` plumbing + "New word!" badge); P1 — a double-grade race (two near-simultaneous grade taps could grade two queue entries from one trace) closed with a `DrawingSession` identity guard; P2s — orphaned `ReviewCard`s now excluded from the generator's slots/backlog/gate; the FREE deck and "learned" checks unified on `repetitions >= 1`; `requiredCharacters` derivation moved to `VocabularyWord.fromKana`; `getWords()` Mutex-cached. `docs/PHASE3-DESIGN.md` §4's due-window text corrected (`<=`→`<`, the code was already right) and a new §11 as-built-amendments section added. Tests grew 39→45 (orphan/backlog-boundary/derivation cases, plus one legacy test fixture fix). `assembleDebug` + `testDebugUnitTest` all green (45/45); `Downloads\HiraganaTrace-debug.apk` refreshed (9,825,597 bytes). |
| 16 | K-row content expansion (**2026-07-04**) | Character deck doubled 5→10: かきくけこ added through the existing KanjiVG pipeline (5 new vendored SVGs in `tools/kanjivg/`, `CODEPOINTS` map extended in `import_kanjivg.py`, import + `generate_stroke_animations.py` re-run — all 10 characters validated, frequencies 6–10). `vocabulary.json` grew 9→23 words (14 new, all composable from the 10-character set; every K-row kana appears in at least one word). Data-only change, zero Kotlin edits; `assembleDebug` + forced `testDebugUnitTest` rerun both green. |
| 17 | Full basic-gojūon expansion (**2026-07-04**) | Deck completed 10→46: さ行 through ん (36 new characters) through the same KanjiVG pipeline (36 vendored SVGs, `CODEPOINTS` extended, import + animation generators re-run, all 46 validated; frequencies = gojūon order 1–46). No dakuten/handakuten or yōon yet. `vocabulary.json` grew 23→153 common words, script-validated: all composable from the 46-glyph deck, no duplicates, every kana except を (object particle — no standalone words exist) covered. Data-only change; build + tests green; installed on the Pixel 7. |
| 18 | Katakana section (**2026-07-04**, device-verified) | A second script alongside hiragana, planned via a 7-agent design+critique workflow (2 P0s caught pre-implementation: quiz `deckSize` had to be scoped too; `setActiveScript` had to bypass `startNextSession`'s COMPLETE/FREE-only guard). New `Script` enum + `ScriptScope` pure filter; `HiraganaCharacter.script` (loader-assigned per directory) + derived `VocabularyWord.script`; `HiraganaAssetDataSource` loads both `assets/hiragana` + new `assets/katakana` (47 chars = 46 homologues + ー, freq 101–147, same KanjiVG pipeline with the tools looping both dirs) with per-root graceful skip; `SettingsRepository.activeScript` (SharedPreferences, default hiragana). `PracticeViewModel` keeps the full merged deck for cross-script romaji lookups but scopes session generation, the quiz pool, and FREE practice to the active script via `ScriptScope` — **`LessonGenerator`/`SrsScheduler` and the Room schema untouched** (off-script cards are inert orphans; `kata_*` rows are additive, no migration). Library gained a segmented Hiragana/Katakana selector (section switch). 66 composability-validated katakana loanwords (every kana but ヲ). Tests +11 (`ScriptTest`, `ScriptScopeTest`); full suite green. **Device (Pixel 7):** default hiragana session; switch → katakana-only ア session with stroke animation; hiragana `review_cards`/`character_progress` **byte-identical before/after** (pulled + diffed); round-trip resumes the hiragana schedule; `active_script` pref persists. `Downloads\HiraganaTrace-debug.apk` refreshed. |
| 19 | Dakuten/handakuten, opt-in (**2026-07-04**) | The voiced (が/ざ/だ/ば) and semi-voiced (ぱ) kana for both scripts — 25 hiragana (freq 47–71) + 25 katakana (148–172) via the same KanjiVG pipeline (50 SVGs vendored, `CODEPOINTS` +50) — plus 40 hiragana + 35 katakana words (composability-validated; every new glyph covered except archaic ヂ/ヅ). Deliberately **opt-in** so beginners aren't confused: new `KanaKind` (BASE/DAKUTEN/HANDAKUTEN, derived from the glyph via Unicode NFD — U+3099/U+309A) + `SettingsRepository.includeDiacritics` (default **false**); `ScriptScope` now gates characters and words on it, so voiced kana are absent from session generation, the quiz, unlocks, and the Library lists until the learner enables the "Voiced kana (゛゜)" `FilterChip` in the Library. `setIncludeDiacritics` mirrors `setActiveScript` (persist + phase-independent rebuild). Voiced-kana frequency bands sit above base per script, so base is always introduced first. **Also fixed a latent bug from row 18**: `startNextSession` dropped `activeScript` in its state reconstruction (tapping "Start next session" while studying katakana silently reverted to hiragana) — it now preserves `activeScript` and `includeDiacritics`. Engine + Room again untouched (voiced-kana cards/words additive; off-scope = inert orphans). Tests 74→83 (`KanaKindTest`, `ScriptScopeTest` diacritic cases); `assembleDebug` + `testDebugUnitTest` green. **Device (Pixel 7):** clean install + launch, zero `HiraganaAssets` warnings (all 50 dakuten animations valid), hiragana `review_cards`/`character_progress` **byte-identical before/after** (pulled + diffed). Full toggle verification (after the owner unlocked the device): with the "Voiced kana" chip off the lists/sessions/quiz show base kana only (katakana Characters 47 / Words 66), and toggling it on updates **in place** to 72 / 101 (and back) — which surfaced + fixed a UX bug: `setActiveScript`/`setIncludeDiacritics` rebuilt the session with a fresh `PracticeUiState` (`isLibraryOpen=false`), bouncing the user out of the Library on every toggle; both now preserve `isLibraryOpen` so the controls read as in-place filters. |
| 20 | Yōon, opt-in + composite characters (**2026-07-04**, device-verified) | Contracted syllables (きゃ/しゅ/きょ …), 33 per script (hiragana freq 72–104, katakana 173–205) + 40 hiragana / 27 katakana words. The app's first **composite characters**: a yōon glyph is two code points (き + small ゃ) with NO single KanjiVG file, so `import_kanjivg.py` gained a `YOON` table + `transform_path`/`compose_yoon` that build the strokes from the base kana (scaled left) + a small ゃ/ゅ/ょ (scaled bottom-right). A centralized `KanaUnits.split` groups base + small ゃゅょ into one reading unit — the single rule now used by `VocabularyWord.fromKana` (so "きょう" → `{きょ, う}` matches the "きょ" deck char), `canvasGlyphs` (one cell per unit), `pronunciationOf`, and the stored word romaji. A **second, independent opt-in** (`SettingsRepository.includeYoon` default **false**; "Combined kana (ゃゅょ)" `FilterChip`; `setIncludeYoon`): `HiraganaCharacter.isYoon`/`VocabularyWord.usesYoon` feed a 3-flag `ScriptScope` that checks yōon first, so a voiced-base yōon (ぎゃ) is gated by `includeYoon` alone. `drawGhostCharacter` gained a shrink-to-fit so the wider 2-glyph ghost never clips. Engine + Room untouched (additive, inert orphans). Tests 83→92 (`KanaUnitsTest`, `ScriptScopeTest` yōon cases); build green. **Device (Pixel 7):** both opt-in chips render; enabling "Combined kana" updates the lists **in place** (katakana Characters 47→80, Words 66→93) with the library staying open and the voiced opt-in unaffected (independent); キャ searches, free-practices, and shows a correct composite ghost + 5-stroke animation that fits the cell; hiragana `review_cards`/`character_progress` **byte-identical before/after**. |
| 21 | Extended kana — inventory complete (**2026-07-04**, device-verified) | The remaining kana, so the app now covers **all** hiragana and katakana: the small tsu (っ/ッ), small vowels (ぁぃぅぇぉ/ァィゥェォ), ゔ/ヴ, 22 extended-katakana foreign-sound composites (ファ/ティ/ウェ/ヴァ/シェ/チェ/ツォ …), and the obsolete ゐゑ/ヰヱ — 9 hiragana + 31 katakana entries + 14 hiragana / 22 katakana words. A **third, independent opt-in** (`SettingsRepository.includeExtended` default **false**; "Extended kana (っ ァ)" `FilterChip`; `setIncludeExtended`). `KanaUnits` now fuses small vowels as well as small ゃゅょ (ソファ → `{ソ, ファ}`), while the small tsu stays its own unit (きって → `{き, っ, て}`); `isExtended`/`usesExtended` = small vowel OR sokuon OR obsolete. `ScriptScope` became a 4-flag gate with priority **extended → yōon → voiced**, keeping the opt-ins independent (e.g. ヴァ is gated by `includeExtended` alone). Extended-katakana composites reuse the yōon composition via a merged `COMPOSITES = YOON + EXTENDED` table (`compose_composite`). **`ゔ`/`ヴ` are classified as dakuten** (they decompose to a combining mark) so they live under the Voiced opt-in, not Extended. Engine + Room untouched (additive, inert orphans). Tests 92→97 (`KanaUnitsTest` small-vowel/isExtended, `ScriptScopeTest` extended cases); build green. **Device (Pixel 7):** all three opt-in chips render; enabling "Extended kana" updates the katakana lists **in place** (Characters 47→77, Words 66→88) with the library staying open and the other two opt-ins unaffected — and the **+30 (not +31)** confirms ヴ correctly sits under the Voiced gate; ファ searches, free-practices, and shows a correct composite ghost + animation; hiragana `review_cards`/`character_progress` **byte-identical before/after**. |
| 22 | Brush ink-color picker (**2026-07-05**, device-verified) | A "fun customization" the user asked for: the traced ink is now any color, not just charcoal. New `BrushColorPicker` (a Compose HSV dialog — saturation/value square + hue slider + the 6 theme presets + a live-on-white preview, built from `Canvas`/`pointerInput`/`Color.hsv`/`android.graphics.Color.colorToHSV`, **no new dependency**) opens from a color swatch button added to `CharacterGuidePanel`'s controls row. The color is a persisted raw ARGB `Int` (`SettingsRepository.get/setBrushInkColor`, key `brush_ink_color`, default `InkBlack`), rides `PracticeUiState.brushInkColor` into `TracingCanvas`'s new `inkColor` param, and colors user ink **at draw time** for both practice and the quiz (same `TracingCanvas`). Purely visual — stroke geometry, scoring, and SRS are untouched, and the grey ghost glyph + stroke-order preview stay grey. `assembleDebug` + `testDebugUnitTest` green (97/97). **Device (Pixel 7):** swatch opens the picker seeded to the current color; hue slider + SV square drive the live preview; Done applies (ink drew vivid yellow, swatch updated); color **persisted across a force-stop + relaunch**; the quiz canvas inked in the same color; Cancel/dismiss discarded a pending change. Concurrent with Agent 1's Library list⇄chart work on the same shared files (`Settings*`, `PracticeViewModel`/`UiState`, `PracticeScreen`) — verified no clobber: both features' fields survive all 5 `PracticeUiState` reconstructions and the list⇄chart toggle still works. `Downloads\HiraganaTrace-debug.apk` refreshed. |

Note: the project is **not a git repository** — there is no commit history to consult. This file and `.aihive/board.md` are the only provenance records.

---

## 2. Original Phase 1 Prompt

As provided by the project owner:

```
# PHASE 1: Build the Static Hiragana Tracing MVP
You are a senior Android engineer building an MVP Android application for learning Japanese Hiragana handwriting using a stylus.
## Tech Stack Requirements
Kotlin / Jetpack Compose / Compose Canvas / MVVM architecture / Room database (prepare but keep minimal) / Material 3
## Objective
Build a fully functional tracing MVP with: top character guide area, main drawing canvas, bottom action buttons, manual self-assessment workflow. Only the first 5 hiragana: あ い う え お.
## Project Structure
presentation/{screens,components,viewmodels}, domain/{model,repository}, data/{assets,repository}, drawing/{canvas,stroke}
## Screen Layout
Single PracticeScreen. Top 25%: current hiragana + static stroke-order image or SVG. Middle 65%: custom Compose Canvas, stylus+touch, 2x2 Japanese practice grid, light grey ghost character underneath, user strokes above. Bottom 10%: Clear, Next, Easy/Okay/Hard grading buttons.
## Drawing Engine Requirements
data class StrokePoint(x: Float, y: Float, pressure: Float, timestamp: Long); data class UserStroke(points: MutableList<StrokePoint>); data class DrawingSession(strokes: MutableList<UserStroke>). Implement: pointerInteropFilter, stylus support, pressure capture, path rendering, stroke storage. Maintain CanvasState: activePath, completedPaths, currentSession.
## Canvas Rendering Order
1. practice grid 2. ghost character 3. completed strokes 4. active stroke
## Character Data
assets/hiragana/ JSON per character: {"character":"あ","romaji":"a","frequency":1,"svgPath":"...","strokeImage":"a.png"}
## Success Criteria
User can: see a character, trace with stylus, clear canvas, move to next character, manually grade themselves.
```

---

## 3. Phase 1 Compliance Audit (2026-07-03 re-audit)

Every requirement re-verified against source after the session loss. All 16 implemented.

| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Single `PracticeScreen`, no navigation | Implemented | `presentation/screens/PracticeScreen.kt` is the only screen; `MainActivity` sets it directly. |
| 2 | Top 25%: character + static stroke-order image or SVG | Implemented | `Modifier.weight(0.25f)` on `CharacterGuidePanel` (`PracticeScreen.kt:84`); static SVG-rendered `StrokeOrderDiagram` with numbered stroke badges (`presentation/components/StrokeOrderDiagram.kt`). |
| 3 | Middle 65%: custom Compose Canvas | Implemented | `Modifier.weight(0.65f)` on `TracingCanvas` (`PracticeScreen.kt:92`). |
| 4 | Bottom 10%: action buttons | Implemented | `Modifier.weight(0.10f)` on `PracticeActionBar` (`PracticeScreen.kt:105`). |
| 5 | Stylus + touch support | Implemented | `drawing/stroke/StrokeCapture.kt` handles `MotionEvent`s tool-agnostically (finger and stylus both draw); tracks `lastToolType` / `isStylus`. |
| 6 | `pointerInteropFilter` | Implemented | `TracingCanvas.kt:68` — `.pointerInteropFilter { event -> strokeCapture.onMotionEvent(event) }`. |
| 7 | Pressure capture | Implemented | `StrokeCapture` records per-point pressure coerced to 0..2, **including batched historical samples** (`collectStrokePoints`), and pressure is actually used: ink width = 9dp base × `StrokeRenderer.pressureWidthScale()` (0.55..1.6). |
| 8 | Exact `StrokePoint` / `UserStroke` / `DrawingSession` shapes | Implemented | `drawing/stroke/StrokePoint.kt`, `UserStroke.kt`, `DrawingSession.kt` — fields match the prompt exactly (plus helper functions). |
| 9 | `CanvasState` with `activePath`, `completedPaths`, `currentSession` | Implemented | `drawing/canvas/CanvasState.kt` — all three fields present as snapshot state, plus `activeWidthScale`. |
| 10 | Render order: grid → ghost → completed → active | Implemented | `TracingCanvas.kt:76-107` — the four draw calls appear in exactly that order, with numbered comments. |
| 11 | 2×2 Japanese practice grid | Implemented | `drawing/canvas/CanvasRenderers.kt` — `drawPracticeGrid()`: solid rounded border + dashed centre cross (genkouyoushi cell). |
| 12 | Light-grey ghost character underneath | Implemented | `CanvasRenderers.kt` — `drawGhostCharacter()`: native-canvas `drawText` in `GhostGrey`, centred in the cell. |
| 13 | Per-character JSON in `assets/hiragana/` per the schema | Implemented | `a/i/u/e/o.json` carry `character`, `romaji`, `frequency`, `strokeImage` (`svgPath` derivable), **plus** extra fields beyond the spec: `viewBox` and per-stroke `strokes[]`. See docs/DATA.md. |
| 14 | Clear / Next / Easy / Okay / Hard all functional; grades persisted | Implemented | `PracticeActionBar.kt` renders all five; `PracticeViewModel.gradeCurrentCharacter()` snapshots the session and inserts a row into Room table `practice_attempts`. |
| 15 | MVVM + exact package structure | Implemented | `presentation/{screens,components,viewmodels}`, `domain/{model,repository}`, `data/{assets,repository}` (+ `data/local` for Room), `drawing/{canvas,stroke}` under `com.hive.hiragana`. |
| 16 | Material 3; only あいうえお | Implemented | `androidx.compose.material3` throughout with a custom M3 theme; `assets/hiragana/index.json` listed exactly `["a","i","u","e","o"]` at Phase-1 scope (the deck has since grown to 10 — see timeline row 16). |

### Documented intra-phase caveats

Two deviations noted during the audit — both cosmetic-level, neither a spec violation:

1. **Pressure → width is per-stroke average, not per-point.** `StrokeRenderer.pressureWidthScale()` maps the stroke's *average* pressure to a single width multiplier; a stroke does not vary in thickness along its length. (The live stroke's width does update as its running average changes.)
2. **`strokeImage` PNGs do not exist.** The JSONs reference `a.png` etc. per the prompt's schema, but no PNGs were shipped — `assets/hiragana/` contains only JSON. The field is parsed and carried on `HiraganaCharacter.strokeImage` but never used; the SVG `strokes[]` diagram fills that role instead.

Other known warts (silently swallowed `recordAttempt` failures, `Grade.valueOf` → `OKAY` fallback, redundant `svgPath`, unused `observeAttempts()`, zero tests) are catalogued in docs/ARCHITECTURE.md and docs/DATA.md.

---

## 4. Original Phase 2 Prompt

As provided by the project owner:

```
# PHASE 2: Add Animated Stroke Order Demonstrations
You are extending an existing Android Hiragana tracing application. Do not redesign the architecture.
## Objective
Replace static stroke-order images with animated demonstrations showing stroke order, stroke direction, writing rhythm, writing speed, pen lifts.
## Data Model
Extend character JSON: {"character":"あ","strokes":[{"order":1,"points":[{"x":0.23,"y":0.52,"time":0},{"x":0.28,"y":0.57,"time":50}]}]}
Create: data class StrokeFrame(x: Float, y: Float, time: Long); data class StrokeDefinition(order: Int, points: List<StrokeFrame>)
## Animation Engine
drawing/animation/StrokeAnimationEngine.kt — animate each stroke independently, support pauses between strokes, support replay, preserve original writing speed, preserve stroke order.
## UI Changes
Replace static image area with: animated preview canvas, play button, replay button, auto-play mode.
## Rendering Pipeline
stroke1 -> pause -> stroke2 -> pause -> stroke3. Use Animatable, LaunchedEffect, Compose Canvas.
## Additional Features
Playback speed multiplier, auto-repeat, pause/resume.
## Success Criteria
User can watch the character being written, replay the animation, observe stroke direction, observe timing and rhythm, and immediately begin tracing afterward.
```

---

## 5. Phase 2 Status: COMPLETE (implemented 2026-07-03; verified, reviewed and fixed 2026-07-04)

All 7 requirement groups of the prompt are implemented:

| Phase 2 requirement group | As built |
|---------------------------|----------|
| Objective — animated stroke demonstrations | `AnimatedStrokePreview` in the guide panel: strokes draw themselves in order with the recorded rhythm and visible pen lifts (pen dot disappears between strokes) |
| Data model — timed `points[]` in JSON; `StrokeFrame` / `StrokeDefinition` | `strokeAnimations` in all five JSONs (generated by `tools/generate_stroke_animations.py`); `domain/model/StrokeDefinition.kt` matches the prompt's exact shapes. Deliberate deviation: coordinates are viewBox space (0–100), not normalized 0–1 — one shared coordinate space with `strokes[]` (PHASE2-DESIGN §1.1) |
| Animation engine — `drawing/animation/StrokeAnimationEngine.kt` | Implemented: independent per-stroke animation, inter-stroke pauses, replay, original writing speed preserved (recorded per-stroke durations), ascending stroke order |
| UI changes — animated preview, play / replay buttons, auto-play | Implemented, plus pause and an auto-repeat toggle; auto-plays on launch and on every character change |
| Rendering pipeline — `Animatable` + `LaunchedEffect` stroke sequencing | Implemented: `Animatable`/LinearEasing clock inside a single suspend `run()` driver launched from a `LaunchedEffect` |
| Additional features — speed multiplier, auto-repeat, pause/resume | Implemented: 0.5×/1×/2× UI cycle (engine accepts 0.25–4×), auto-repeat, mid-stroke pause/resume that continues in place |
| Success criteria | Met in code — build-verified and adversarially reviewed. **Not yet observed on a device/emulator** |

How the Phase 1 groundwork was used: the per-stroke SVG paths + `viewBox` became the generator's input (timed points are resampled from them rather than hand-authored); `StrokeOrderDiagram` became the fallback rendering for characters without timing data; `observeAttempts()` remains implemented-but-unused.

Verification record (2026-07-04): build reproducible; all five JSONs pass the generator's validator; two independent adversarial review agents found **0 P0 defects** (verdict: ship-for-demo), and their P1/P2 findings were fixed and rebuilt the same day (timeline rows 10–11, as-built notes in PHASE2-DESIGN §7). The remaining honest gaps at the time: no device/emulator run had exercised the playback UI, and there were still zero tests — both addressed differently by Phase 3 below (tests, yes; device run, still no).

---

## 6. Phase 3 Status: COMPLETE (implemented, built, tested, and reviewed 2026-07-04)

Phase 3 ("Smart Learning System") added a learning engine on top of the existing
drawing/animation pipeline: frequency-based character ordering, a simplified-SM-2 spaced
repetition scheduler, vocabulary unlocking, and personalized session generation. Unlike
Phase 1/2, there is no single short verbatim prompt to reproduce here — the design
process started from the owner's goals and a fixed contract (`docs/PHASE3-DESIGN.md`)
was negotiated and written *before* any implementation, explicitly resolving five
places where the original ask collided with the shipped Phase 1/2 codebase (documented
in that file's §0: extending rather than replacing `HiraganaCharacter`/`Grade`, keeping
the entity/domain split for the new persisted models, scoping the vocabulary list to
characters already in the deck, and renaming rather than duplicating the attempts
table). This "contract first" approach mirrors what Phase 2 learned the hard way (row
6 above): write the contract and get it approved before parallel workstreams start,
and update the docs in the same phase rather than after.

| Phase 3 area | As built |
|---|---|
| Character ordering | `AssetCharacterRepository` sorts by `HiraganaCharacter.frequency` ascending; `index.json` demoted to a file manifest |
| SRS scheduler | `domain/learning/SrsScheduler.kt` — pure, clock-injected (`todayStartMillis` parameter); simplified SM-2 transitions for AGAIN/HARD/GOOD/EASY exactly matching PHASE3-DESIGN §2's table |
| Lesson generator | `domain/learning/LessonGenerator.kt` — pure, clock-injected; assembles due reviews (capped, most-overdue first), a mastery-gated new character, and vocabulary (fresh unlocks + reinforcement) into one `StudySession` |
| Vocabulary system | `assets/hiragana/vocabulary.json` (9 words at Phase-3 ship, all composable from あいうえお; 153 words after the 2026-07-04 K-row and full-gojūon expansions) + `VocabularyWord.fromKana` derivation of `requiredCharacters`; unlock rule is "every required character has `repetitions >= 1`" |
| Persistence | Room v1→v2: `MIGRATION_1_2` (rename + source column + 3 new tables), `exportSchema = true` + committed `app/schemas/`, 2 new repositories |
| Session UI | `PracticeViewModel` `SessionPhase` state machine (LOADING→REVIEWS→NEW_CHARACTER→PRACTICE→VOCABULARY→COMPLETE→FREE); `Grade` extended to AGAIN/HARD/GOOD/EASY; action bar drops Next except in FREE |
| Tests | 45 JUnit4 tests (`SrsSchedulerTest`, `LessonGeneratorTest`, `VocabularyUnlockTest`) — the project's first tests |
| Success criteria (PHASE3-DESIGN §9) | All 8 met in code and/or test-proven; criterion 6 (full on-device session flow) is asserted by the state-machine logic and its tests but **not yet observed on a device/emulator** |

Verification record (2026-07-04, same day as implementation): integration was green on
the first try — `assembleDebug` + `testDebugUnitTest` BUILD SUCCESSFUL, 39/39 tests
passing on the very first run (timeline row 14). Two independent adversarial review
agents (logic/data+migration; session UI) then found **0 P0 defects** (both verdicts:
ship); their P1/P2 findings were fixed the same day, growing the suite to 45/45 green
and producing `docs/PHASE3-DESIGN.md` §11 (as-built amendments — see timeline row 15 and
that file directly for the itemized list). The remaining honest gaps, unchanged from
Phase 2's pattern: **no device/emulator run has ever exercised the session flow**,
including — uniquely risky for Phase 3 — the live v1→v2 database migration, which is
today verified only statically against the exported schema.

---

## 7. Build-Verification Record

The first verification build (Phase 1: 2026-07-03, `assembleDebug`, 2m05s, zero source fixes) used a user-scope toolchain provisioned the same day — this machine previously had no Java or Android tooling. Later builds on the same toolchain: Phase 2 integration (2026-07-03 ~23:51, one source fix), the Phase 2 post-review-fix build (2026-07-04, `BUILD SUCCESSFUL in 11s`, 5 tasks executed), the Phase 3 integration build (2026-07-04, `assembleDebug` + `testDebugUnitTest` in 42s, first-ever test run 39/39 green), and the Phase 3 post-review-fix build (2026-07-04, `assembleDebug` + `testDebugUnitTest` all green, 45/45 tests).

| Component | Location |
|-----------|----------|
| JDK | `C:\Users\Mario\.jdks\jdk-17.0.19+10` (portable Temurin 17) |
| Android SDK | `C:\Users\Mario\AppData\Local\Android\Sdk` (platform 34, build-tools 34.0.0, platform-tools, cmdline-tools) |
| Gradle | `C:\Users\Mario\.gradle-dist\gradle-8.7\bin\gradle.bat` |

Important: the repo commits **only** `gradle/wrapper/gradle-wrapper.properties` (pointing at Gradle 8.7-bin) — there is **no wrapper JAR and no `gradlew`/`gradlew.bat`**, so `./gradlew` does not work out of the box. Build with the standalone Gradle distribution above, `JAVA_HOME` set to the JDK, and `ANDROID_HOME` set to the SDK (`local.properties` already contains `sdk.dir`). The exact command line is in [CLAUDE.md](../CLAUDE.md); in short:

```
gradle.bat -p C:\Users\Mario\Downloads\hive-test2 assembleDebug --console=plain
```

For the unit test suite (Phase 3 onward): the same environment variables, then
`gradle.bat -p C:\Users\Mario\Downloads\hive-test2 testDebugUnitTest --console=plain`.

Build stack as verified: AGP 8.5.2, Kotlin 2.0.20 + `kotlin-compose` plugin, KSP 2.0.20-1.0.25, Compose BOM 2024.09.03, Room 2.6.1, JUnit 4.13.2 (Phase 3), compile/target SDK 34, minSdk 26, Java target 11, versionName 0.1.0 (see `gradle/libs.versions.toml` and `app/build.gradle.kts`).

Artifacts (all verified present on 2026-07-04, now the Phase 3 build):

- `app/build/outputs/apk/debug/app-debug.apk`
- `C:\Users\Mario\Downloads\HiraganaTrace-debug.apk` (9,825,597 bytes ≈ 9.8 MB after the Phase 3 review-fix rebuild; the Phase 2 copy was 9,764,092 bytes; the original Phase 1 copy was 9,696,241 bytes)
