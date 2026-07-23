# Changelog

All notable changes to **HiraKataQuest** (a Japanese kana handwriting trainer).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The app is pre-1.0: everything below is development toward **0.1.0** (`versionCode 1`),
listed newest-first by milestone. Detailed provenance lives in
[`docs/HISTORY.md`](docs/HISTORY.md); phase contracts in `docs/PHASE2-DESIGN.md` /
`docs/PHASE3-DESIGN.md`.

---

## [Unreleased]

### 2026-07-23 — 0.1.8: Opening-screen and "About kana" polish

#### Changed
- **Beginner mode** now sits in a small, horizontally-centered footer at the
  bottom of the start screen (previously a full-width row above the mode cards).
  Its label is smaller and the switch scales down with the system font size.
- **"About kana"** (opened from "New to kana?") was rewritten: it now opens with
  what kana are and why the correct stroke order and direction matter, keeps
  hiragana and katakana, adds a short note on why kanji are left out of this app,
  and points you to the ⓘ next to the voiced, combined, and extended kana in the
  Study-scope menu instead of listing them here.

#### Fixed
- Turning Beginner mode on no longer nudges the start-screen cards upward: the
  "New to kana?" link now appears in place without shifting anything above it.

### 2026-07-23 — 0.1.7: Brush effects, Beginner mode, and read-aloud

#### Added
- **Brush effects** — pick the style your strokes are drawn with, next to the
  ink color in the Options sheet: **Brush** (the original), **Calligraphy 筆**
  (the line swells on slow, heavy strokes and thins on fast flicks, with pointed
  ends), **Fountain pen** (thin and crisp), and **Marker** (bold and translucent,
  so overlaps darken). Purely visual: it never affects your handwriting score.
- **Beginner mode** — a toggle on the opening screen (off by default). When on,
  it reveals plain-language ⓘ help for hiragana, katakana, and the voiced,
  combined, and extended kana groups, plus a "New to kana?" overview. Its own ⓘ
  explains what the mode is and who it helps. Leave it off for a cleaner screen.
- **Kana concept explainers** — the ⓘ help above, written for first-time
  learners (shown only in Beginner mode).
- **Read aloud automatically** — an optional Study-scope setting (Writing only):
  each new character or word is spoken by the device voice the moment it appears,
  so you hear the reading without tapping the speaker.

#### Fixed
- **Reading flashcards** — the typed-romaji answer and reading no longer slip
  behind the keyboard on smaller screens, and the printed glyph now sits
  centered on the card instead of low.

### 2026-07-11 — Responsiveness: large-font & multi-device hardening

#### Fixed
- **Large system fonts no longer break the UI** (found on a Realme GT 7 Pro; the
  app was hand-tuned to a Pixel 7 at font scale 1.0):
  - the **1×1/2×2/3×3 layout picker** labels no longer wrap/clip in the control strip;
  - the **flashcard reveal** (reading · audio · meaning) no longer renders shredded
    text next to the speaker, and the big glyph can no longer overflow its card;
  - the quiz setup's **"Exact number"** label/hint no longer wraps awkwardly
    ("+" orphaned on its own line);
  - the practice-set cards' **Quiz · Practice · Edit · Rename · Delete** actions
    always fit on ONE line (the labels shrink together when space is tight);
  - the Library's **search field** gets real width while searching (the view
    toggle, Sort menu, and "Quiz me!" hide until search closes).
- The opening screen scrolls (still centered) instead of clipping on short
  screens / huge fonts; its cards' width modifier fixed for very wide screens.

#### Changed
- **Edge-to-edge**: the app now draws behind the system bars (transparent bars,
  washi background showing through) with content inset via `safeDrawing` —
  also future-proofs Android 15's forced edge-to-edge and display cutouts.
- The practice screen's guide panel and action bar **grow with the system font
  scale** (the canvas absorbs the difference); same for the writing quiz header.
- Dense "icon-like" chrome (gojūon chart cells, selection grid cells, corner
  score badges, the layout-picker labels) is now **font-scale-independent** —
  sized like icons; the list views remain the fully font-scaling path.
- Word-grid columns widen with the font scale (fewer, wider columns).

### 2026-07-09 — In-app updates (no Play Store)

#### Added
- **In-app self-updater** — on launch the app checks the project's **GitHub
  Releases** for a newer version and, if one exists, shows an **"Update available"**
  dialog. Tap **Update** to download the new APK and hand it to Android's installer
  (**Later** dismisses; **Skip** silences that one version). No Play Store, no extra
  app. Uses plain `HttpURLConnection` (no new dependency); a flaky/offline network
  fails silently. The one-time "install unknown apps" grant is requested on first use.
- **Proper release signing** — release builds are now signed with a dedicated
  release keystore (via a gitignored `keystore.properties`), so update-over-install
  works reliably. `BuildConfig` is enabled for the version comparison.

#### Changed
- Version bumped to **0.1.1** (`versionCode 2`).
- New permissions: `INTERNET`, `REQUEST_INSTALL_PACKAGES`; added a `FileProvider`
  to serve the downloaded APK to the installer.

### 2026-07-08 — Reading/Writing split: opening screen, reading quiz & flashcards

#### Added
- **An opening screen (home) to pick Reading or Writing** — the app now starts on a
  top-level chooser, and you can switch modes anytime from the new **Reading | Writing**
  toggle in the Study-scope sheet.
- **Reading mode** — two new ways to study without handwriting:
  - a **reading quiz** — see the printed glyph or word and answer by **multiple choice**
    or by **typing the romaji** (lenient matching); word items can optionally also test
    the **English meaning**.
  - a **flashcard** study mode — flip a printed card to reveal the reading, audio, and
    meaning; a **"Type the reading"** toggle adds a romaji input field.
- **Reading accuracy tracking** — recorded per glyph/word as **two independent
  percentages** (multiple-choice and typed). The Library tints cells by their **average**
  (no "best" — reading is pass/fail), shows both percentages in list rows, and can sort
  by **Average / Multiple-choice / Typed**, ascending or descending.
- New **Room v5** table `reading_progress` (`MIGRATION_4_5`).

#### Changed
- **Writing mode always uses the handwritten (KanjiVG-stroke) ghost.** The old
  **Printed / Handwritten** "Character style" selector is **replaced** by the
  Reading | Writing mode toggle.
- The Activity now uses `windowSoftInputMode=adjustResize` and the flashcard card is
  responsive, so nothing clips behind the on-screen keyboard.

#### Tests
- Build + unit tests green (added `RomajiMatchTest`); device-verified on a Pixel 7,
  including the live v4→v5 migration.

### 2026-07-08 — Library sorting, smart set-building & Options-sheet polish

#### Added
- **Sort the Library list by your scores** — a Sort menu in the Library app bar
  (list view only; the chart keeps its fixed gojūon order) reorders characters or
  words by **average** or **best** score, ascending or descending, with a
  **Default order** reset. Not-yet-practiced items sink to the bottom, and
  changing the order snaps the list back to the top so the new leaders are visible.
- **Smart "All" when building a practice set** — in *Add practice set*, the **All**
  button opens a popup that selects only the items you still need to drill:
  **"Only under a score of"** (default **85**, −/+ stepper, tap-to-type a whole
  number 1–99, and **70 / 80 / 90 / 99** quick-pick chips) plus a checkbox
  **"Exclude {characters|words} I have not studied yet"** (on by default). Turn the
  cap off to select everything (the previous behavior). Applies on **Select**.
- **Score-aware selection list** — the set-builder's list rows now mirror the
  Library browse rows (score-color tint + "avg · best"/"Not practiced yet") and
  carry the same Sort menu, so weak items are easy to spot; selection shows as a
  checkbox plus a border over the score tint.

#### Changed
- **Brush ink color moved into the Options (Study-scope) sheet** as a labeled
  *Brush ink color* row (with its color swatch); the app-bar slot it vacated now
  holds the Sort menu.
- **The Options sheet fits on a ~6.3″ phone (e.g. Pixel 7) without scrolling** at
  default font sizes — tightened section spacing, dividers, and toggle-row padding
  (still uncramped; the ~90 %-height scroll fallback remains for very large fonts).

### 2026-07-06 — Handwriting rendering: fixed-width brush & Printed/Handwritten style

#### Added
- **Character style — Printed vs Handwritten** — a persisted `CharacterForm`
  setting (selector in the Study-scope sheet). *Printed* keeps the typographic
  font ghost you read on signs and screens; *Handwritten* draws the guide ghost
  from the same KanjiVG strokes the demo and scorer use (a grey brush), for both
  practice and the quiz. Rendering only — it never changes what is studied,
  demonstrated, or scored.
- **A "character X of Y" / "word X of Y" position line** under the reading in the
  practice guide panel (the item's place in the active-script scoped list).

#### Changed
- **The stroke-order demo now renders as a bold, glyph-proportional brush**
  (previously a thin 3.5 dp wireframe), matching the printed form of the glyph.
- **User ink is now a uniform fixed-width brush** — dropped pen-pressure (finger
  contact size), speed thinning, and head/tail tapers, so a chubby finger and a
  thin stylus draw identically and the ink matches the demo. Width is the same
  fraction of the glyph as the demo at every size, so words, composite glyphs, and
  2×2/3×3 grids thin automatically. Scoring is unaffected (it reads the raw points).
- Removed the redundant **Replay** button from the stroke-order controls (Play
  already restarts the animation).

### 2026-07-06 — Audio pronunciation (device text-to-speech)

#### Added
- **Tap to hear a kana or word** — a small speaker button sits next to every
  reading (practice guide panel, Library list rows + word cards, and the quiz
  prompt + summary) and speaks it with the device's Japanese text-to-speech voice.
  Uses the framework `TextToSpeech` (ja-JP) fed the raw kana glyph — **no new
  dependency, permission, manifest, or Room change**. If the Japanese voice is not
  installed the button opens the system voice-data installer; if TTS is
  unavailable it greys out. Availability re-checks on resume, so a freshly
  installed voice starts working without a restart. (The dense gojūon chart cells
  intentionally omit the button — no room.)

### 2026-07-05 — Library search & free-practice polish

#### Added
- **Romaji search in the Library** — words are now findable by their reading,
  not just kana/meaning: `ねこ` matches `neko`, `ne ko`, `ねこ`, or `cat`. The
  query and every searched field are run through a shared `searchNormalize()`
  (drops all whitespace, lowercases), so romaji typed with or without spaces and
  in any case compare equal, and multi-word glosses match too ("ice cream" ⇒
  "icecream"). Word readings come from the ViewModel's `pronunciationOf`.
  Kana input is unchanged (still scoped to the active Hiragana/Katakana tab).

#### Changed
- **Library search field** uses a normal search-input text size (`bodyLarge`,
  16 sp) instead of the oversized title style, so a longer query like
  "ka me ra" fits the narrow title slot without the single-line field scrolling
  and clipping the first character.
- **Multi-copy layouts are repetition worksheets for words too** — in 2×2/3×3,
  a word (like a single character) now shows **Clear · Next** with no
  Submit/scoring; the Submit→score two-step exists only in the 1×1 layout.
- **Per-kana word score chips** are responsive — an equal-weight row that resizes
  to fit a 4+ kana word on one line inside the canvas, with insets that clear the
  paper card's rounded corner (both practice and quiz).

### 2026-07-05 — Guided study removed; built-in default practice sets

#### Removed
- **The guided SRS study session** — the app is now **free practice + the
  "Quiz me!" quiz** only. Deleted `SrsScheduler`, `LessonGenerator`,
  `StudySession`, and `PracticeActionBar` (the Again/Hard/Good/Easy grade bar;
  `FreePracticeActionBar` split into its own file), plus the three
  `domain/learning` test files. `SessionPhase` collapsed to `LOADING`/`FREE`;
  the COMPLETE summary, the "Start a study session" / "Start next session" entry
  points, and the Library's "Learned/Locked" status labels are gone.

#### Added
- **Built-in default practice sets** — ~32 sets seeded once per install
  (persisted `default_sets_seeded` flag): for each script, the 10 gojūon rows +
  the Voiced / Combined / Extended kana categories + starter word sets
  (Colors / Nature / Seasons; Food & drink / Countries / Everyday items). They
  are ordinary editable sets afterward, and a deleted default does not return.

#### Changed
- Whether writing is scored is now governed **solely** by the **Score my
  writing** setting — there is no separate graded flow.
- Unscored Library cells / rows / cards use a neutral tint (previously a
  learned/unlocked highlight); the score-spectrum coloring is unchanged.
- **Room untouched at v4** — the `review_cards` / `character_progress` /
  `vocabulary_progress` tables + entities/DAOs + `RoomLearningRepository`
  remain as dormant, unused schema (removing them would need a destructive
  migration).

#### Tests
- Suite is now **84 unit tests, 0 failures** (removed the SRS / lesson-generator
  / vocabulary-unlock suites; trimmed `ScriptScopeTest` to its pure filter/gating
  cases; added default-practice-set seeding tests).

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
