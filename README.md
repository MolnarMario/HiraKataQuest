# HiraKataQuest

**HiraKataQuest** is an Android app for learning Japanese kana, split into **two
tracks you pick from an opening screen**:

- **Reading** — learn to recognize the **printed (gothic) kana** you meet on
  signs, apps, and online. A reading **quiz** (see a printed character or word,
  answer by multiple choice or by typing the romaji — with lenient matching;
  words can optionally also test the English meaning) and a **flashcard** study
  mode (flip a printed card to reveal its reading, audio, and meaning; a "Type
  the reading" toggle adds a romaji field).
- **Writing** — the handwriting practice: trace the **handwritten (kaisho)**
  stroke form over a ghost glyph, watch the stroke-order demo, and get advisory
  geometry + ML Kit scoring.

You switch tracks anytime from the opening screen or from the **Reading |
Writing** toggle in the Library's Study-scope sheet.

It covers the **complete kana inventory for both scripts** — hiragana and
katakana — with animated stroke-order demonstrations, free-practice tracing, a
write-from-memory quiz, and advisory handwriting scoring. Structured study comes
from **built-in, editable default practice sets** (gojūon rows, kana categories,
and starter word sets, per script). (A guided spaced-repetition study session
existed through 2026-07-04 but was **removed 2026-07-05** — see Project status.)
Beginners start on the base gojūon (あ〜ん / ア〜ン); three
independent **opt-ins** (all off by default, switched on from the Library when
you're ready) fold in the rest so nobody is overwhelmed early: **voiced kana**
(dakuten が/ざ/だ/ば, handakuten ぱ), **combined kana** (yōon きゃ/しゅ/きょ), and
**extended kana** (the small tsu っ, small vowels, and foreign-sound combos like
ファ/ティ/ウェ/ヴァ). A Hiragana/Katakana selector switches which script you
study, quiz, and browse; progress for the two scripts is tracked independently.

## Project status

- **Phase 1 (static tracing MVP): complete** and build-verified — a clean
  `assembleDebug` succeeded on 2026-07-03.
- **Phase 2 (animated stroke-order demonstrations): complete** — implemented
  and built 2026-07-03; re-verified, adversarially code-reviewed and
  review-fixed 2026-07-04.
- **Phase 3 (Smart Learning System — SRS scheduling, personalized sessions,
  vocabulary unlocking): implemented 2026-07-04, then REMOVED 2026-07-05.**
  The guided study session (SM-2 scheduler, lesson generator, session state
  machine, and the COMPLETE summary) was deleted; the app is now
  **free-practice + quiz** only, with built-in default practice sets providing
  structured learning instead. The SRS scheduler is retained only as dormant,
  unused code/schema (removing it would need a destructive Room migration).
- **Device-verified on a Pixel 7 (Android 16)** across many revisions: the live
  v1→v2 (and later v2→v3, v3→v4) Room migrations, persistence, and — most
  recently, 2026-07-05 — the guided-study removal (cold start opens free
  practice, no grade bar; the seeded default practice sets appear for both
  scripts), along with the earlier fix batch that the first device run prompted
  (KanjiVG stroke geometry, brush-ribbon ink, the Library, and a force-dark
  opt-out).
- **Beyond the three phases** (all built + device-verified): full hiragana +
  katakana across all groups; a Library **list⇄chart** (gojūon cheat-sheet)
  view; a write-from-memory **"Quiz me!"** mode (characters *and* whole words,
  scored per kana); **handwriting-quality scoring** (geometry form/fit + ML
  Kit recognition, advisory only); user-curated **practice sets** — plus
  **built-in default sets** per script — to quiz or free-practice hand-picked
  characters/words; a configurable **brush ink color**; a split into **Reading
  and Writing tracks** (a printed-kana reading quiz + flashcards alongside the
  handwriting practice, reading accuracy tracked on **Room v5**); **memory-hint
  mnemonics** for the base hiragana (keyword + shape story per kana,
  learner-selectable alternates, accent overlay drawings on the real strokes);
  and a **rebrand to "HiraKataQuest"** with a new hinomaru launcher icon
  (vermilion circle + white あ/ア).
- See [docs/HISTORY.md](docs/HISTORY.md) for how the project got here (the
  original build session was lost; the docs are the recovery record).

## What the app does

The app **opens on a track picker**: choose **Reading** (recognize printed kana)
or **Writing** (handwriting practice). You can switch at any time from that
screen or from the **Reading | Writing** toggle in the Library's Study-scope
sheet (which replaced the old Printed / Handwritten "Character style" option).

- **Reading track** — study the printed gothic kana with a **reading quiz** and a
  **flashcard** mode. In the quiz you see a printed character or word and answer
  either by **multiple choice** or by **typing the romaji** (lenient matching, so
  minor spelling slips still pass); for words you can optionally also be tested on
  the **English meaning**. Flashcards flip from the printed front to reveal the
  reading, audio, and meaning, with a **"Type the reading"** toggle that adds a
  romaji field. Reading accuracy is tracked per item as **two percentages** — a
  multiple-choice % and a typed % — and their average tints the Library (reading
  is pass/fail, so there's no "best" score).
- **Writing track** — the handwriting practice below: it **opens into free
  practice**, picking up a random character (or word) from the section of the
  Library you last browsed, scoped to whatever you're studying, so you can start
  writing immediately. Writing always uses the **handwritten** ghost glyph.
  Whether your writing is scored is decided entirely by the **Score my writing**
  setting (on ⇒ write → Submit → feedback; off ⇒ just Clear · Next). There is no
  timed or graded "session" — tracing, the quiz, and the practice sets are all
  self-directed.

Structured practice comes from the **Library**: browse every character and word,
tap anything to practice it, quiz yourself, and use the **practice sets** — both
the ones you build and the built-in defaults that ship per script (see below). The
Library doubles as the app's "home": pressing the system **Back** button from
practice opens the Library, and pressing Back again from the Library exits the app
(so **Practice → Back → Library → Back → exit**).

- **Stroke-order demonstrations** — the guide panel plays an animated demo of the current character (strokes draw themselves with the recorded rhythm and visible pen lifts, with play/pause, replay, speed and auto-repeat controls). Words show a **stroke-order demo per kana**, sharing one set of playback controls — the same controls single characters get.
- **"Looks different in print"** — three kana genuinely change shape between print and handwriting, the top three of the classic 活字体 vs 手書き textbook chart (its milder cases like ふ or そ stay close enough to need no warning): さ, き, and り, whose strokes join into one continuous line in the printed form but are traced as separate strokes in the school handwriting form. Wherever one of these is on screen — the writing guide panel, a flashcard's answer, or the reading quiz's reveal — a small tappable chip appears; it opens a side-by-side of the printed form next to the handwritten form with a one-line note on what differs, so you learn to recognize both. The printed forms are real typeset text from a small bundled font subset (Kosugi for さ/き, IBM Plex Sans JP for り — no single free gothic face connects all three, and Android's own screen font draws them all in the detached, handwriting style, so it couldn't demonstrate the difference). Every typed-glyph surface — the flashcard front, the reading-quiz prompt, the Library's list rows, chart cells, and word cards, and the writing guide panel's header — renders these three kana through that subset, so the connected form IS the app's printed form everywhere; all other characters keep the system font via automatic fallback. Voiced and combined variants inherit the note automatically (ぎ, きゃ …); katakana forms don't differ, so they never show it.
- **Memory hints (mnemonics)** — every one of the 46 basic hiragana carries a keyword and a short story that tie its handwritten shape to its sound (あ is **APPLE**: the bar and curve cross at the top, the fruit swings below in one plump loop). A tappable **"How to remember"** chip appears in writing practice and on flashcard and quiz answer reveals, and a **"Hint"** button offers the story *before* you answer a quiz item — in the writing quiz the hint is text-only, since the shape is the answer you're being tested on. Ten characters go further with small vermilion accent drawings layered onto the real stroke shapes: a line and barb turning し into a fishhook, chopsticks lifting ぬ's noodles, spray flying off つ's wave. And because everyone's brain hooks onto different images, each kana ships with alternates: open the dialog's **"More ways to remember"** list and promote whichever image works for you — the choice is saved per character and used everywhere. It all sits behind a **Memory hints** switch in the Study-scope sheet (on by default) and never affects your scores.
- **Hear the pronunciation** — a small speaker button next to every reading (guide panel, Library rows and word cards, the quiz prompt and summary) says the kana or word aloud using the device's built-in Japanese text-to-speech. If the phone doesn't have a Japanese voice installed, tapping the button opens the system "install voice data" screen so it can be downloaded for free; the button re-enables itself when you return. On a device with no Japanese-capable TTS engine at all, the button is simply greyed out.
- **Library** — browse every character and word, and tap anything to free-practice it; nothing is gated behind progress. Each character square / word card is **tinted by your average score** on a red → orange → yellow → green spectrum, with the score shown small in its top-right corner — in both the list and the chart views — so you can see at a glance what still needs work (word practice feeds each kana's stats, so the individual kana light up too). Unscored items keep a neutral tint. A **Hiragana / Katakana** selector at the top (next to "Quiz me!") switches the active script: it scopes what the library lists and what the quiz draws from. The choice persists across restarts (default Hiragana), and each script keeps its own independent progress. Beside it, three independent opt-in toggles (all off by default) fold extra kana into the active script: **Voiced kana (゛゜)** adds the dakuten/handakuten (が, ぱ …), **Combined kana (ゃゅょ)** adds the yōon contracted syllables (きゃ, しゅ …), and **Extended kana (っ ァ)** adds the small tsu, small vowels, and foreign-sound combos (ファ, ティ, ウェ …). Until you turn one on, the quiz and the lists show only the base gojūon, so a beginner is never confronted with が or ファ before the basics. Any progress you make on a group is kept if you toggle it back off. The Study scope sheet also carries a **Reading | Writing** toggle for switching tracks (it replaced the old Printed / Handwritten "Character style" option) and opens to its full content height on a normal screen, scrolling only as a fallback on short ones. A third **Sets** tab sits beside Characters and Words at the top of the Library — see the Practice sets section below. In the Reading track the Library tint comes from your reading accuracy (the average of the multiple-choice and typed percentages) instead of a handwriting score.
- **Quiz me!** — from the library: pick how many items (5/10/20/All presets, or type/step an exact number in the box they pre-populate), then write each from its romaji prompt alone — no ghost guide while writing, while the crosshair practice grid is a per-quiz switch (it doesn't change your normal setting). The quiz handles **words as well as single characters**: a word is written whole and scored **per kana with an average**, the same way whole-word practice is. A **source dropdown** on the setup screen lets you quiz your normal study scope or any saved practice set. **Show answer** reveals the kana and the light-grey guide strokes on the canvas for self-checking; auto-scored, or move freely with **Back/Next** (Next becomes **Finish** on the last item; ungraded items show as Skipped in the per-item summary). Quiz attempts record as `QUIZ`.
- **Practice sets** — hand-picked lists of specific characters *or* words (never mixed) that you curate and save to quiz or free-practice later. Unlike the **Study scope** toggles (which switch whole categories on or off), a set is an explicit membership list — the exact items you chose. The app **ships with a batch of built-in default sets** for each script (the 10 gojūon rows, the Voiced / Combined / Extended kana categories, and a few starter word sets like Colors, Nature, Food & drink), seeded into your library the first time you open it; these are ordinary sets afterwards, so you can rename, edit, or delete them (a deleted default does not come back). Practice sets live on their own **Sets tab** at the top of the Library, beside Characters and Words (scoped to the active script — Hiragana shows only hiragana sets, Katakana only katakana). Each set's card is **tinted by its average score** (the same red → orange → yellow → green coloring as the character and word cards — the average across that set's members, from scored practice and quizzes only, so a set only ever practiced with scoring off shows no score; in the Reading track the tint comes from your reading accuracy instead), with a **sort control** (a menu offering name A→Z, name Z→A, newest/oldest first, and score high→low / low→high) and the Library search filtering sets by name. Each saved set offers **Quiz**, **Practice** (free practice over the set, or flashcards in the Reading track), **Edit** (change its membership), **Rename**, and **Delete**. To build one, tap **New** and pick **Character set** or **Word set**; that drops the full list of that type (in the active script) into a **selection mode** that ignores the scope toggles, with a **list/grid view toggle** — tap an item to toggle it, or click-and-drag across items to toggle a range in one gesture (no hold delay; both work the same in either layout), with **Select all / Deselect all / Save** (Save asks for a name). After you save a set (new or edited), and after you finish a quiz launched from a set, you land back on the **Sets tab** rather than the browse view. Sets persist across restarts.
- **Handwriting scoring** — after each single (XL) character you write, an advisory quality score appears (it never changes your self-grade or review schedule). When you practice a whole **word** in the XL layout, **each kana is scored separately**: per-kana score chips appear above the canvas (with quick coaching like "Use 2 strokes"), plus the word's average below — and each kana's stats feed back into the Library. The multi-copy **L / M / S** layouts are pure repetition worksheets, so nothing is scored there (the bar reads **Undo · Clear · Next**); scoring applies only to single XL writing. A **Score my writing** setting (Form / Fit / Both / Off — on the quiz setup card **and** in the Library's Study Scope sheet) chooses what it judges: **Form** rewards correct shape, stroke order, direction and proportion — normalized so writing bigger/smaller or off-centre in the box is *not* penalized (how Japanese handwriting is actually graded); **Fit** rewards filling and centring the guide cell; **Off** turns practice into pure practice — no score is computed and **no attempt is recorded** (there's nothing to score), so free practice drops the Submit step and just offers **Undo · Clear · Next**. A hybrid engine combines our own stroke geometry with on-device **ML Kit Digital Ink** recognition (Japanese) for a legibility check; recognition works offline once its model has downloaded and degrades gracefully to geometry-only until then. The legibility check is **shape-twin aware**: a small kana (ぁ, っ, ゃ…) drawn full-size in the cell is pixel-identical to its regular twin, so the recognizer reading it as あ/つ/や still counts as legible — without this, every small-kana attempt was unfairly capped no matter how well it was written. The feedback card carries a small **ⓘ** that opens a plain-language "How your writing is scored" explainer (Form / Technique / Fit / Readability, and the fact that scores are advice only), and the card always stays within the canvas width — long coaching tips wrap instead of running off screen. The Library shows each character's average and best score so you can track progress over time.
- The tracing canvas: a genkouyoushi-style 2×2 practice grid with a light-grey ghost glyph to trace over, capturing stylus **and** finger input with per-point pressure via raw `MotionEvent`s (`pointerInteropFilter`), including Android's batched historical samples. Ink renders as a **brush-style ribbon** whose width follows pen pressure and speed, with tapered ends. Make it your own with the **brush-color swatch** in the Library's top bar (between Search and the Study-scope filter): tap it to open a full color picker (a saturation/value square, a hue slider, quick preset colors, and a live preview) and paint in any color you like — it applies to both practice and the quiz, persists across restarts, and leaves the grey ghost guide untouched. A **grid-free mode** toggle (in the controls strip at the bottom of the guide panel, next to the Library button) hides the helper lines for less-assisted practice — the ghost glyph stays — and the choice persists across restarts. A matching **ghost toggle** (the あ button beside it) hides the light-grey ghost glyph instead, for writing from memory once tracing feels easy; it persists the same way, and the two combine freely (blank-page mode = both off). A **sheet size** picker in the same strip chooses **XL, L, M, or S** cells: XL is one big scored copy (what the quiz uses), while L/M/S tile the sheet with progressively smaller copies of the current character or word — as many complete rows as fit the page, worksheet-style, with no half-empty corner; this also persists across restarts. An **Undo** button next to Clear reverts just your latest stroke, so one slip doesn't cost the whole character.
- Bottom bar: free practice uses a two-step flow like the quiz: write → **Submit** (auto-scores what you wrote, shows the feedback, and keeps your ink on the sheet) → then **Again** to rewrite the *same* character or **Next** to move on. An empty canvas offers **Skip** in place of Submit; with scoring **Off** (or in the multi-copy 2×2 / 3×3 repetition layouts) there's nothing to submit, so it collapses to **Clear · Next**.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Compose Canvas · MVVM · Room (KSP) · manual DI (`AppContainer`).

## Documentation

| Doc | What it covers |
| --- | --- |
| [CLAUDE.md](CLAUDE.md) | Build commands (including machine-specific toolchain paths) and instructions for AI agent sessions. |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layer map, state ownership (`PracticeViewModel` vs `CanvasState`), and the manual-DI wiring. |
| [docs/DRAWING.md](docs/DRAWING.md) | Input capture (`StrokeCapture`), rendering pipeline, pressure-to-width model. |
| [docs/DATA.md](docs/DATA.md) | Character/vocabulary asset JSON format and the Room persistence layer (v5). |
| [docs/PHASE3-DESIGN.md](docs/PHASE3-DESIGN.md) | Phase 3 contract: SRS scheduler, lesson generator, vocabulary unlocking, Room v2 migration, session UI — plus as-built amendments. (Guided study removed 2026-07-05; retained for history.) |
| [docs/HISTORY.md](docs/HISTORY.md) | Project provenance: phases, what was built when, and known warts. |

## Building

No Gradle wrapper JAR is committed. Either:

1. **Android Studio** (Koala or newer): *File → Open* the project root; let it sync and provision Gradle 8.7, then Run. Or:
2. **CLI**: with a local Gradle 8.5+ and JDK 17: `gradle wrapper --gradle-version 8.7` once, then `./gradlew assembleDebug`.

Requires Android SDK 34; runs on devices from API 26.

Machine-specific provisioned-toolchain paths and the exact verified CLI
command live in [CLAUDE.md](CLAUDE.md).

## Project structure

```
app/src/main/java/com/hive/hiragana/
├── HiraganaApplication.kt        # AppContainer (manual DI)
├── MainActivity.kt
├── presentation/
│   ├── screens/PracticeScreen.kt         # 25/65/10 layout
│   ├── components/                       # guide panel, animated preview, stroke diagram, canvas, action bar
│   ├── viewmodels/PracticeViewModel.kt   # free-practice launcher + scoring (SessionPhase LOADING/FREE)
│   └── theme/
├── domain/
│   ├── model/                    # HiraganaCharacter, Grade, PracticeAttempt, VocabularyWord,
│   │                              # PracticeSet, DefaultPracticeSets, PracticeLayout, Script/KanaKind/…
│   │                              # (ReviewCard/CharacterProgress/VocabularyProgress retained but dormant)
│   ├── repository/               # CharacterRepository, PracticeRepository, VocabularyRepository,
│   │                              # PracticeSetRepository, SettingsRepository (interfaces)
│   │                              # (LearningRepository retained but dormant)
├── data/
│   ├── assets/                   # JSON loader for assets/hiragana/ (characters + vocabulary.json)
│   ├── local/                    # Room v6: entities/DAOs, MIGRATION_1_2 … MIGRATION_5_6 (adds practice_sets.updatedAt), exportSchema=true
│   └── repository/               # repo implementations
└── drawing/
    ├── stroke/                   # StrokePoint, UserStroke, DrawingSession, StrokeCapture
    ├── canvas/                   # CanvasState, StrokeRenderer, grid/ghost renderers
    └── animation/                # StrokeAnimationEngine, StrokeFramePath (stroke-order playback)
```

Timing data for the animations is generated offline by
`tools/generate_stroke_animations.py` (see below). Room's exported schema
history lives in `app/schemas/`. Unit tests (84, JUnit4) live under
`app/src/test/java/com/hive/hiragana/domain/{model,scoring}/` and
`presentation/viewmodels/` — the `domain/learning` scheduler/generator tests
were removed with the guided study session on 2026-07-05.

## Character data format

One JSON file per character in `assets/hiragana/`, listed in `index.json`
(a file manifest only — study order is derived from `frequency`, ascending):

```json
{
  "character": "あ",
  "romaji": "a",
  "frequency": 1,
  "strokeImage": "a.png",
  "viewBox": 109,
  "strokes": ["M 31.01 33 C ...", "..."],
  "svgPath": "all strokes joined",
  "strokeAnimations": [
    { "order": 1, "points": [ { "x": 22.0, "y": 33.0, "time": 0 }, "..." ] }
  ]
}
```

`strokes` holds one SVG path per stroke (109×109 viewBox) and drives the
animated stroke-order demonstration, its faint guide, and the fallback
numbered diagram. The geometry is **imported from KanjiVG** (see the
attribution below) by `tools/import_kanjivg.py` from the SVGs vendored in
`tools/kanjivg/`; `strokeAnimations` (Phase 2) holds timed pen positions
per stroke — **derived data** generated from `strokes` by
`tools/generate_stroke_animations.py`. Never hand-edit either; re-run the
import tool and then the generator (both self-validate). `strokeImage` is
reserved for a future raster alternative and ships no PNGs in this phase.

> **Attribution**: stroke-order geometry is from the
> [KanjiVG](http://kanjivg.tagaini.net) project, © Ulrich Apel, used and
> adapted under
> [CC BY-SA 3.0](http://creativecommons.org/licenses/by-sa/3.0/). The
> vendored SVGs in `tools/kanjivg/` retain their original license headers.
> The printed forms of さ/き/り (shown on every typed surface: flashcards,
> the reading quiz, the Library, the guide panel, and the comparison
> dialog's "In print" specimen) use an 8-glyph merged subset of
> [Kosugi](https://fonts.google.com/specimen/Kosugi) (© Motoya,
> [Apache License 2.0](tools/fonts/Kosugi-LICENSE.txt)) and
> [IBM Plex Sans JP](https://fonts.google.com/specimen/IBM+Plex+Sans+JP)
> (© IBM, [SIL OFL 1.1](tools/fonts/IBMPlexSansJP-OFL.txt)) — see
> `tools/fonts/README.md`.

Each script has its own `assets/<script>/vocabulary.json` (Phase 3), listing
words composable from that script's deck:

```json
{ "words": [ { "kana": "あい", "meaning": "love" }, { "kana": "いえ", "meaning": "house" } ] }
```

Each word stores only `kana` and `meaning` — the set of characters it
requires is **derived**, never stored, by `VocabularyWord.fromKana` from
the distinct characters in `kana` (and its script is derived the same way).
The **katakana** file ships **66 loanwords** written only with the 47-entry
katakana deck (46 homologues + ー; no dakuten or small kana) — コーヒー,
ラーメン, タクシー, ケーキ, カメラ, ホテル, アメリカ, レストラン and
more — every katakana except the archaic particle ヲ is covered. The **hiragana** file ships 153 common words, all
composable from the 46-character deck (no dakuten, small kana, or long
vowel marks) and spanning everyday themes: people and family (わたし,
はは, おとうと), animals (ねこ, いぬ, きつね), nature and seasons (やま,
そら, ゆき, はる), time (あさ, あした, きのう), food (すし, さとう,
みかん), everyday objects and places (ほん, くるま, えき, せかい), basic
verbs and adjectives (のむ, よむ, おおきい, たのしい), demonstratives
(これ, それ, あそこ), numbers, and greetings (おはよう, こんにちは,
さようなら). Every kana except を (the object particle, which forms no
standalone words) appears in at least one word.

## Design decisions worth knowing

- **Ghost glyph is font-rendered**, not SVG: the system font guarantees a
  typographically correct shape to trace. The SVG stroke paths (KanjiVG)
  drive the stroke-order demonstration and diagram, so both now match real
  calligraphic proportions.
- **Rendering order** on the canvas is fixed: grid → ghost → completed
  strokes → active stroke.
- **Brush ink**: user strokes render as filled variable-width ribbons
  (`StrokeRenderer.buildBrushPath`) — width follows per-point pen pressure
  and speed (fast flicks thin out), with tapered ends and round caps;
  centerline and edges are smoothed with midpoint quadratic béziers. The
  ink **color** is a separate, purely-visual concern applied at draw time
  (`TracingCanvas`'s `inkColor` param) — a user-picked, persisted ARGB value
  (default charcoal `InkBlack`); it never touches geometry, scoring, or the
  grey ghost/preview.
- **Room logs scored attempts**: `practice_history` (renamed from
  `practice_attempts` in the v1→v2 migration) records each scored attempt,
  tagged with its source (`FREE` / `QUIZ`). The old spaced-repetition tables
  (`review_cards`, `character_progress`, `vocabulary_progress`) remain in the
  schema but are **dormant** — the guided study session that read them was
  removed 2026-07-05, and dropping the tables would need a destructive
  migration, so those tables stay unused. Two more tables
  (`practice_sets`, `practice_set_items`) back the practice sets, and a
  `reading_progress` table (added in the v4→v5 migration) records the Reading
  track's per-item multiple-choice and typed accuracy. The database is at **v5**.
- **Practice is self-directed, two-step**: writing anything is a write →
  **Submit** (auto-score + record a `FREE` attempt, keeping the ink) →
  **Again** (rewrite the same character) / **Next** (advance) flow, mirroring
  the quiz. With scoring off (or in a multi-copy repetition layout) it
  collapses to **Clear · Next**. Clear always just wipes the sheet and stays.

## Phase 3 (removed) and beyond

**Phase 3 (implemented 2026-07-04, removed 2026-07-05):** a smart learning system
once sat on top of the drawing/animation engine — frequency-ordered character
introduction, a simplified SM-2 spaced-repetition scheduler, a deterministic
session generator, and a `PracticeViewModel` session state machine. It was
**removed** on 2026-07-05: the app is now free-practice + quiz only, with
built-in default practice sets in place of the generated sessions. The Grade
enum's four-point Again/Hard/Good/Easy scale and the Room tables it used remain,
but the scheduler, lesson generator, and session UI are gone. See
`docs/PHASE3-DESIGN.md` for the historical contract and `docs/HISTORY.md` for the
removal.

**Future candidates:** more kana rows (new characters and their vocabulary drop
straight into the browse lists and default sets), stroke-order validation
against the reference paths, and a stats/history screen (`observeAttempts()` is
implemented but has no consumer yet).
