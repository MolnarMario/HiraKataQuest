# Changelog

All notable changes to **HiraKataQuest** (a Japanese kana handwriting trainer).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The app is pre-1.0: everything below is development toward **0.1.0** (`versionCode 1`),
listed newest-first by milestone. Detailed provenance lives in
[`docs/HISTORY.md`](docs/HISTORY.md); phase contracts in `docs/PHASE2-DESIGN.md` /
`docs/PHASE3-DESIGN.md`.

---

## [Unreleased]

### 2026-08-02 — 0.4.0: A real vocabulary, kanji reference, and honest scores

#### Added
- **The word list grew from about 250 words to nearly 700.** The hiragana deck
  now carries the common JLPT N5 vocabulary, plus 25 more katakana loanwords, so
  practising words no longer runs out of material.
- **A "Kanji" button on words that have a kanji spelling** (off by default; turn
  on "Kanji reference" in Study scope). It is colored by how the word is
  normally written: green when it is usually kanji, blue when it is mixed, grey
  when it is usually kana. Tapping it opens a reference card with the kanji, the
  reading, the meaning, and, for words that sound alike, the other kanji that
  share that reading with a one-line note on the difference. Purely
  informative: it is never quizzed and never scored.
- **Filter the Words tab by category** (hiragana), with removable chips, both
  when browsing and when building a word set.
- **A "From my strong kana" set generator.** It builds a word set out of words
  whose every kana you already score 85 or better in both writing and reading,
  so review material comes from what you have actually learned.

#### Changed
- **Writing mode now opens the Library instead of a random character.** Picking
  what to practise is the first thing you do, which is how Reading mode already
  worked. Tapping a character or word drops you straight into practice, and Back
  returns to the Library.
- **Handwriting scores are a measurement now, not a cap.** Two scores used to be
  suspiciously common: a well-formed character the recognizer merely misread
  always landed on exactly 65, and a scribble that cleared a low bar always
  landed on exactly 45. Both were ceilings rather than measurements. Recognition
  now nudges the score instead of capping it, extra strokes actually cost
  something, no scoring mode is blind to the shape any more, and a backwards
  stroke is charged once rather than twice. A clean katakana "e" that used to
  show 65 with a "looks more like" complaint now scores 93; a single random line
  scores 7 instead of 45.
- **The score on the feedback card and the score in the Library finally agree.**
  The Library used to average the raw form dial while the card showed the
  adjusted number, so the same attempt could read 65 on one screen and 88 on the
  other. It now stores and averages the number you were actually shown. Practice
  recorded before this update keeps counting toward your averages.
- **Katakana are no longer misread as the kanji they came from.** Thirty
  lookalike pairs (the katakana "e" and the kanji for "work", and so on) are now
  treated as the same drawing, because nothing in the ink can tell them apart.
- **The reading quiz keeps a steady layout.** The prompt no longer jumps when
  you answer, typing works the way it does in flashcard practice (the keyboard
  stays open, the Done key checks and moves on, an empty answer skips), and
  memory-hint stories bold the reading they are teaching.

### 2026-08-02 — 0.3.3: Smarter Library search and visible scores

#### Added
- **Your writing score now shows on each word in the Library list.** Words in
  the Characters/Words list already tinted green by score; now they also print
  the number ("Avg 82 · Best 90", or "Not practiced yet"), matching the grid
  view and the character rows.
- **Reading progress keeps the romaji alongside it.** In Reading mode the word
  list used to replace a word's romaji with its progress stats; it now shows the
  romaji as usual and puts the progress on its own line, so you never lose the
  reading.

#### Changed
- **Library search ranks results by relevance.** An exact match now floats to
  the top instead of being buried under incidental substring hits, so searching
  "hat" finds the word "hat" first rather than "that" and "what". Romaji search
  works the same way, with or without spaces ("boushi" or "bo u shi").
- The reading-progress line labels the typed-answer score **"Typing"** instead
  of the terser "Type".

### 2026-07-31 — 0.3.1: Quiz polish and memory hints for every kana

#### Added
- **Memory hints now cover every kana, not just the basic hiragana.** Katakana
  and the voiced, combined, and extended kana all get keyword-and-story hints,
  most with two or three alternate images to pick from. Composed kana (like
  きゃ or ヴァ) also show an always-on **Structure** note that spells out how the
  piece is built from its parts, separate from the memory hint itself.
- **Encouragement as you build a streak in Reading mode.** Answer a run of
  reading questions correctly (in the quiz or in flashcard practice) and the
  reveal greets you with a short, escalating line of encouragement instead of a
  flat "Correct!". A gentle note marks a broken streak.
- **The writing quiz shows you how the answer is written.** When you reveal a
  writing-quiz answer, its stroke-order animation now plays in the corner (the
  same demo free practice uses), so a missed character teaches the fix, not just
  the finished shape.
- **An Undo button in the writing quiz.** The bottom bar's Back button is now
  **Undo**, which reverts just your last stroke so a slip on a many-stroke
  character no longer means clearing the whole thing.
- **Sort your writing-quiz results, and retake the ones that need work.** The
  end-of-quiz summary can now order its items by score (lowest or highest
  first), and a new button re-quizzes every character or word you scored below a
  target you set right there (default 80, with quick-pick 60/70/80/90 chips).
  It is the writing counterpart to Reading mode's "retake the ones I missed".
- **A clearer "Add new set" button** on the Practice sets tab (was a plain
  "New").

#### Changed
- The Quiz-me "Practice from" list now shows only the sets in the script you're
  currently studying, matching the Library's Sets tab.

### 2026-07-27 — 0.3.0: Dark mode, memory hints, and a Practice sets tab

#### Added
- **A dark mode.** New **Appearance** setting at the bottom of the Options
  (Study-scope) sheet: **System / Light / Dark**. It stays **Light** until you
  choose, so nothing changes on update. Dark is a warm, paper-like night theme
  (not a harsh inversion): the writing worksheet itself darkens, with the ghost
  glyph and grid dimmed to match, and your brush gets a **separate ink for dark
  mode** (a light chalk by default) so strokes stay legible on the dark sheet.
  The same brush-ink row edits whichever appearance is active. **System**
  follows your phone's own dark-mode setting. Score tints, the score-feedback card,
  and every Library surface get dark-appropriate colors too. (Also fixes a
  latent bug where the status-bar icons could turn invisible on phones set to
  system dark mode.)
- **Memory hints for the basic hiragana.** Every one of the 46 basic hiragana
  now carries a keyword and a short story that tie its shape to its sound. A
  **"How to remember"** chip appears in writing practice and on flashcard and
  quiz answer reveals, and a **"Hint"** button offers the story before you
  answer (text-only in the writing quiz, since the shape is the answer). Each
  kana ships with alternates, so you can open **"More ways to remember"** and
  promote whichever image sticks; the choice is saved per character. It all
  sits behind a **Memory hints** switch in Options (on by default) and never
  affects your scores.
- **Practice sets now have their own tab.** The sets you curate, plus the
  built-in starter sets, live on a dedicated **Sets** tab at the top of the
  Library beside Characters and Words, scoped to the script you're studying. A
  **New** button starts a Character set or Word set, and the top-bar search
  filters sets by name. This replaces the old "Add practice set" / "See sets"
  buttons in Options.

### 2026-07-25 — 0.2.3: Practice sets scoped to the chosen script

#### Changed
- **The practice-sets manager now shows only the script you have selected**
  in Options: pick Hiragana and you see just your hiragana sets, pick
  Katakana and you see just your katakana sets (Words and Character sets of
  that script still appear together). The "See sets" count next to it now
  reflects that same per-script number instead of the combined total.

### 2026-07-25 — 0.2.2: Reading-mode fixes

#### Fixed
- **Practicing a saved set in Reading mode now works.** It opens the set's
  characters or words as reading flashcards; previously the button did
  nothing in Reading mode (it only worked in Writing mode).
- **The read-aloud speaker is no longer a dead button** when your phone's
  text-to-speech engine has no Japanese voice: tapping it now opens the
  system Text-to-speech settings so you can install a Japanese voice or
  switch to an engine (such as Google Text-to-Speech) that has one.
- **The "Looks different in handwriting" note is visible without scrolling**
  on the reading flashcards for さ, き, and り — those cards now give the
  reveal area more room so the note sits in view under the reading.

### 2026-07-24 — 0.2.1: Undo, and practice-sheet sizes that earn their names

#### Added
- **Undo your latest stroke**: a new Undo button next to Clear in writing
  practice reverts just the last stroke you drew, so one slip doesn't cost
  the progress on the rest of the character or word.

#### Changed
- **The repetition layouts are now sizes — XL, L, M, S** (replacing
  1×1/2×2/3×3, whose grid names meant nothing for words). XL is one big
  scored copy — the default, and what the quiz uses. L, M, and S tile the
  sheet with progressively smaller copies, and every step down is guaranteed
  to give you both visibly smaller cells and strictly more copies to
  practice on — for single characters and words of any length. Your saved
  layout carries over (old 2×2 becomes L, old 3×3 becomes S).
- Repetition sheets now **fill the canvas with complete rows** instead of a
  fixed 4 or 9 slots — no more ragged, half-empty corner, and taller screens
  simply get more practice squares. Cells never shrink below a writable
  minimum (~40dp) for very long words.

### 2026-07-24 — 0.2.0: Practice sets sorted by recency

#### Added
- **Sort practice sets by how recently you touched them**: the sets manager's
  sort menu gains "Updated: Newest first" and "Updated: Oldest first".
  Creating, renaming, or editing a set's members counts as touching it, so the
  set you just worked on rises to the top of "Newest". (Backed by a new
  `updatedAt` column — Room v5→v6, additive; existing sets inherit their
  creation time.)

#### Changed
- **The sets list keeps your chosen sort order** across editing a set, running
  a quiz from one, and app restarts — it only changes when you pick a
  different order from the menu. (It previously reset to Name: A→Z every time
  the manager was rebuilt.)

#### Fixed
- Picking a sort order, or an edit bumping a set to #1, now scrolls the sets
  list back to the top — previously the list stayed anchored where it was, so
  the new order (or the freshly bumped set) sat invisibly above the screen.

### 2026-07-24 — 0.1.9: Meaning practice, answer read-aloud, and word-practice fixes

#### Added
- **Flashcards can now test a word's meaning too**: a new "Also test meaning"
  option on the reading flashcards (words only). After you answer the reading,
  the card asks what the word means — in the same style you're already using
  (type it in Typing mode, or pick it from choices in Multiple choice) — and
  reveals both results together.
- **Reading mode reads the answer aloud**: once you submit an answer — on a
  flashcard or in the reading quiz — the character or word is spoken, so you
  hear the reading you just recalled. On by default; turn it off with the new
  "Read aloud automatically" switch in the Options menu (Reading's counterpart
  of the Writing narration toggle — the answer is never spoken early).
- The brush color picker now offers black plus one preset for each main
  rainbow color, sized so every swatch fits the row.

#### Changed
- Flashcard typing flow: the romaji field and its Check/Next button now share
  one row, the answer locks once checked, and the keyboard stays open for the
  whole run — you can drive an entire session from the keyboard's Done key.
- The flashcard keeps the character/word the same size before and after the
  reveal (the result area is reserved instead of appearing and resizing things).

#### Fixed
- Ink now stays inside the box each stroke started in — strokes can no longer
  bleed into a neighboring character's cell on word sheets.
- Practicing a word from the Library now offers both Again and Next (Next
  cycles through the word list), matching character practice.
- The whole-word "Average" pill no longer sits flush against the paper's
  bottom edge.

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
