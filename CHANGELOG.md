# Changelog

All notable changes to **HiraKataQuest** (a Japanese kana handwriting trainer).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The app is pre-1.0: everything below is development toward **0.1.0** (`versionCode 1`),
listed newest-first by milestone. Detailed provenance lives in
[`docs/HISTORY.md`](docs/HISTORY.md); phase contracts in `docs/PHASE2-DESIGN.md` /
`docs/PHASE3-DESIGN.md`.

---

## [Unreleased]

### 2026-07-05 — Code-review round 4 (practice-sets & scoring hardening)

#### Fixed
- **Library drag-select** no longer inverts: dragging across already-selected
  items now deselects them. The gesture read a stale captured selection set;
  it now reads the live set via `rememberUpdatedState`.
- **Duplicate-key crash-proofing** — word lists, grids, and selection rows now
  use unique Compose list keys, so a future homograph word (e.g. あめ rain/candy,
  はし bridge/chopsticks) can't hard-crash the Library. (Shipped data is clean today.)
- **Word stroke-order demo** play/pause reflects whether *any* kana is animating,
  so the control no longer shows the wrong icon or restarts a single kana out of sync.
- **Layout switch mid-review** clears pending FREE feedback and the submitted flag,
  so changing the 1×1/2×2/3×3 layout no longer strands an "Again/Next" card over a
  blank sheet.
- **Quiz with scoring Off** now counts written-but-unscored items as *written*
  instead of mislabeling them "Skipped".
- **SRS interval overflow** — the review interval is capped at `MAX_INTERVAL_DAYS`
  (100 years), preventing integer overflow after very long GOOD/EASY streaks.
- **Brush width** is clamped against degenerate/transient canvas layouts (no giant
  ink blob during a layout change).
- **Startup** warms the `SharedPreferences` disk load off the main thread.
- Doc reconciliations for `ReviewCard` (repetitions / due-date semantics).

#### Tests
- Suite grew to **129 unit tests, 0 failures** (regressions added for layout-reset,
  scoring-off recording, SRS interval cap, stroke-attribution ties, degenerate
  geometry scoring).

### 2026-07-05 — Practice sets & word quizzes

#### Added
- **Practice sets** — user-curated, named lists of characters or words, persisted
  via **Room v4** (`practice_sets` + `practice_set_items`, additive `MIGRATION_3_4`).
  Built in a Library **selection mode** (tap + drag-range select, list/grid toggle)
  and managed in a **sets manager** (rename / delete / edit members / Quiz / Practice,
  a sort menu, and a color-coded per-set average score).
- **Word quizzes** — the "Quiz me!" flow now handles whole words as well as single
  characters (words scored per-kana with an average). The quiz setup gained a source
  picker: the current study scope, or any saved set.

#### Changed
- The Study-scope ("Filter") sheet opens to full content height.

### 2026-07-05 — Per-kana word scoring, FREE two-step, and Library score UI

#### Added
- **Per-kana whole-word scoring** — a multi-kana word traced on one sheet is scored
  per kana (`StrokeAttribution` assigns each stroke to its nearest cell); per-kana
  score chips + an average render on the canvas and persist to history.
- **FREE-practice two-step**: write → **Submit** (scores, keeps the ink, shows
  feedback) → **Again** (retry the same glyph) / **Next** (advance); an empty canvas
  offers **Skip**.
- **Per-kana stroke-order previews** for words, sharing one playback-controls row.
- **Library score color-coding** — a red→green spectrum tints chart squares, word
  cards, and list rows, with a corner score badge.
- **Brush ink-color picker** — trace in any color (HSV dialog in the Library top bar);
  purely visual, persisted, never affects scoring or scheduling.
- **"Score my writing"** Form/Fit/Both/Off picker added to the Library's Study-scope
  sheet (previously quiz-only). **Off** ⇒ FREE practice collapses to Clear · Next and
  records no history row (SRS/unlock unaffected).

#### Changed
- Scoring is restricted to a single 1×1 cell; multi-copy 2×2/3×3 layouts are treated
  as repetition worksheets (no score).
- Word stroke-preview sizing fixed so 3+ kana words show every demo at once (no clipping).

#### Fixed (review rounds during this batch)
- `scoringMode` / `scoreStats` preserved across session rebuilds (was silently reset
  by "Start next session").
- Stale async handwriting-recognition results can no longer clobber the current glyph's
  score.
- First-trace `introduce` + `increment` run sequentially so the trace count can't be lost.
- `ScriptScope` word gating is per-reading-unit, so a mixed dakuten+yōon word (でんしゃ)
  is offered only when actually unlockable.
- Stale word-score chips cleared on scoring-mode change; word-preview effects scoped by
  engine identity.
- Added the project's first ViewModel tests.

---

## 0.1.0 development

### 2026-07-04 — Full kana inventory (all hiragana + katakana)

#### Added
- **Extended kana** (third opt-in) — small tsu (っ/ッ), small vowels, ゔ/ヴ, 22
  foreign-sound katakana composites (ファ/ティ/ヴァ/シェ …), and obsolete ゐゑ/ヰヱ.
  Completes the inventory: **113 hiragana + 136 katakana**, ~397 words.
- **Yōon** (second opt-in) — contracted syllables (きゃ/しゅ/きょ …), the app's first
  **composite characters** (2-code-point glyphs whose strokes are composed from a base
  kana + a small ゃ/ゅ/ょ). `KanaUnits` groups a base + small kana into one reading unit.
- **Dakuten / handakuten** (first opt-in) — voiced (が/ざ/だ/ば) and semi-voiced (ぱ)
  kana for both scripts, classified from the glyph via Unicode NFD (`KanaKind`).
- **Katakana** — a second script alongside hiragana (46 homologues + ー), a persisted
  `activeScript` setting, and a Hiragana/Katakana selector in the Library.

Each advanced group is **off by default** (a Library toggle folds it in) so beginners
meet only the base gojūon first. The learning engine and Room schema were reused
unchanged (new rows are additive; off-scope cards are inert orphans).

#### Changed
- Deck grown from 5 characters to the full 46-character basic gojūon (あ〜ん), then to
  the complete voiced/yōon/extended sets, all via the KanjiVG stroke-geometry pipeline.
- Vocabulary grown to hundreds of composability-validated words per script.

#### Fixed
- `startNextSession` no longer drops `activeScript` / opt-in settings on rebuild.
- Script/scope toggles act as in-place Library filters instead of bouncing the user out.

### 2026-07-04/05 — Rebrand & smart-learning polish

#### Added
- **Rebrand to "HiraKataQuest"** with a new hinomaru launcher icon.
- **Handwriting-quality scorer** (`domain/scoring/` — geometry form/fit dials + ML Kit
  digital-ink recognition; advisory only, never touches SRS), persisted via **Room v3**.
- **"Quiz me!"** write-from-memory mode.
- **Library** overlay for free practice of any character/word, plus a list ⇄ chart
  (gojūon cheat-sheet) view.

#### Changed
- Stroke geometry re-sourced from **KanjiVG** (CC BY-SA); ink rendered as a
  variable-width **brush ribbon**.
- Force-dark opt-out (light-only theme) after a dark-mode device inversion.

### 2026-07-04 — Phase 3: Smart Learning System

#### Added
- **Spaced repetition** (`SrsScheduler`, simplified SM-2) and **session generation**
  (`LessonGenerator`) — pure, clock-injected, unit-tested.
- **Vocabulary unlocking** — words unlock once every required kana is learned.
- **Session state machine** (`PracticeViewModel`): REVIEWS → NEW/PRACTICE → VOCABULARY
  → COMPLETE → FREE; grades AGAIN/HARD/GOOD/EASY.
- **Persistence** — Room v1→v2 (`MIGRATION_1_2`: rename `practice_attempts` →
  `practice_history`, add `source`, add SRS/progress/vocabulary tables), `exportSchema`.
- The project's **first tests** (45 JUnit4).

### 2026-07-03 — Phase 2: Animated stroke-order demonstrations

#### Added
- `StrokeAnimationEngine` — strokes draw themselves in recorded order, direction, and
  rhythm, with visible pen lifts; play / pause / replay / speed (0.5×–2×) / auto-repeat.
- Timed `strokeAnimations` data generated offline from the SVG stroke paths; the static
  numbered diagram (`StrokeOrderDiagram`) became the fallback for glyphs without timing.

#### Fixed
- Guide-panel controls no longer crush to zero width on short screens.
- `strokeAnimations` are consistency-checked against `strokes[]` and voided (with a
  fallback to the static diagram) when malformed.

### 2026-07-03 — Phase 1: Static tracing MVP

#### Added
- Initial app: single `PracticeScreen` — character guide (top), a custom Compose
  tracing `Canvas` with a 2×2 genkōyōshi grid + light-grey ghost glyph + stylus/finger
  input with pressure capture (middle), and Clear / Next / grade buttons (bottom).
- Drawing engine (`StrokePoint` / `UserStroke` / `DrawingSession`, `CanvasState`), MVVM
  structure, Material 3, minimal Room, and the first 5 hiragana (あいうえお).
