# HiraKataQuest

**HiraKataQuest** is an Android app for practising Japanese kana handwriting with a stylus.
Covers the **complete kana inventory for both scripts** — hiragana and
katakana — with animated stroke-order demonstrations, spaced-repetition
scheduling, vocabulary unlocking, and a 4-grade self-assessment (Again / Hard /
Good / Easy) workflow. Beginners start on the base gojūon (あ〜ん / ア〜ン); three
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
  vocabulary unlocking): complete** — implemented, built, and unit-tested
  2026-07-04 (`assembleDebug` + `testDebugUnitTest` both green, 45 unit
  tests / 0 failures, the project's first tests), then adversarially
  reviewed (2 agents, 0 P0, both verdicts ship) with fixes applied the same
  day.
- **Device-verified on a Pixel 7 (Android 16)**: fresh-install session flow,
  persistence, SRS scheduling, and the live v1→v2 (and later v2→v3) Room
  migrations all confirmed on hardware, along with the fix batch that first
  device run prompted (KanjiVG stroke geometry, brush-ribbon ink, a
  Start-next-session button, the Library, and a force-dark opt-out).
- **Beyond the three phases** (all built + device-verified): full hiragana +
  katakana across all groups; a Library **list⇄chart** (gojūon cheat-sheet)
  view; a write-from-memory **"Quiz me!"** mode; **handwriting-quality scoring**
  (geometry form/fit + ML Kit recognition, advisory only, on **Room v3**); a
  configurable **brush ink color**; and a **rebrand to "HiraKataQuest"** with a
  new hinomaru launcher icon (vermilion circle + white あ/ア).
- See [docs/HISTORY.md](docs/HISTORY.md) for how the project got here (the
  original build session was lost; the docs are the recovery record).

## What the app does

Each visit runs a short generated **study session**: due spaced-repetition
reviews first, then at most one new character (introduced with the animated
stroke-order demo and traced three times), then any vocabulary words that
character just unlocked, then a summary — after which you can keep
free-practicing over everything you've learned so far.

- **Reviews** — characters due for spaced repetition surface first, oldest-due first; grade each with **Again / Hard / Good / Easy** and the scheduler reschedules it (Again re-queues it later in the same session).
- **New character** — introduced via the animated stroke-order demonstration (strokes draw themselves with the recorded rhythm and visible pen lifts, with play/pause, replay, speed and auto-repeat controls), then traced three times; the third grade seeds its review schedule. A new character is only introduced once your review backlog is small and the previous new character has been successfully recalled at least once.
- **Vocabulary** — words composable entirely from characters you've learned unlock automatically and are traced **whole**: one sheet with a practice cell per kana, graded once per word, with a "New word!" badge on fresh unlocks.
- **Summary** — reviews done, character learned, words unlocked/practiced; then **Start next session** (regenerates a session immediately — usually introducing the next character — no app restart needed), "Keep practicing" (an untimed free-trace loop over everything learned so far), or the library.
- **Library** — browse every character and word (with learned/unlocked status) from the guide panel or the summary, and tap anything to free-practice it, gated or not; library grades never touch the review schedule. A **Hiragana / Katakana** selector at the top (next to "Quiz me!") switches the active script: it scopes what the library lists, what the session generator introduces/reviews, and what the quiz draws from. The choice persists across restarts (default Hiragana), and each script keeps its own independent progress. Beside it, three independent opt-in toggles (all off by default) fold extra kana into the active script: **Voiced kana (゛゜)** adds the dakuten/handakuten (が, ぱ …), **Combined kana (ゃゅょ)** adds the yōon contracted syllables (きゃ, しゅ …), and **Extended kana (っ ァ)** adds the small tsu, small vowels, and foreign-sound combos (ファ, ティ, ウェ …). Until you turn one on, sessions, the quiz, unlocks, and even these lists show only the base gojūon, so a beginner is never confronted with が or ファ before the basics. Each group is introduced after the base kana, and any progress you make on it is kept if you toggle it back off.
- **Quiz me!** — from the library: pick how many characters (5/10/20/All presets, or type/step an exact number in the box they pre-populate; learned characters are drawn first), then write each from its romaji prompt alone — no ghost guide while writing, while the crosshair practice grid is a per-quiz switch (it doesn't change your normal setting). **Show answer** reveals the kana and the light-grey guide strokes on the canvas for self-checking; grade yourself, or move freely with **Back/Next** (Next becomes **Finish** on the last character; ungraded characters show as Skipped in the per-character summary). Quiz grades record as `QUIZ` and never touch the review schedule.
- **Handwriting scoring** — after each single character you write, an advisory quality score appears (it never changes your self-grade or review schedule). A **Score my writing** setting (Form / Fit / Both / Off, on the quiz setup card) chooses what it judges: **Form** rewards correct shape, stroke order, direction and proportion — normalized so writing bigger/smaller or off-centre in the box is *not* penalized (how Japanese handwriting is actually graded); **Fit** rewards filling and centring the guide cell. A hybrid engine combines our own stroke geometry with on-device **ML Kit Digital Ink** recognition (Japanese) for a legibility check; recognition works offline once its model has downloaded and degrades gracefully to geometry-only until then. The Library shows each character's average and best score so you can track progress over time.
- The tracing canvas: a genkouyoushi-style 2×2 practice grid with a light-grey ghost glyph to trace over, capturing stylus **and** finger input with per-point pressure via raw `MotionEvent`s (`pointerInteropFilter`), including Android's batched historical samples. Ink renders as a **brush-style ribbon** whose width follows pen pressure and speed, with tapered ends. Make it your own with the **brush-color swatch** at the end of the controls strip: tap it to open a full color picker (a saturation/value square, a hue slider, quick preset colors, and a live preview) and paint in any color you like — it applies to both practice and the quiz, persists across restarts, and leaves the grey ghost guide untouched. A **grid-free mode** toggle (in the controls strip at the bottom of the guide panel, next to the Library button) hides the helper lines for less-assisted practice — the ghost glyph stays — and the choice persists across restarts. A matching **ghost toggle** (the あ button beside it) hides the light-grey ghost glyph instead, for writing from memory once tracing feels easy; it persists the same way, and the two combine freely (blank-page mode = both off). A **repetition layout** picker in the same strip tiles the sheet with **1×1, 2×2, or 3×3** copies (1, 4, or 9) of the current character or word, so you can practice it several times per page before clearing or grading; this also persists across restarts. The copies arrange themselves to make the cells as large as the sheet allows — single characters keep the square grid, while words stack as full-width rows down the page, worksheet-style.
- Bottom bar: **Clear** · **Again** · **Hard** · **Good** · **Easy**, plus **Next** (only shown during free practice, since a session's generator decides what comes next).

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Compose Canvas · MVVM · Room (KSP) · manual DI (`AppContainer`).

## Documentation

| Doc | What it covers |
| --- | --- |
| [CLAUDE.md](CLAUDE.md) | Build commands (including machine-specific toolchain paths) and instructions for AI agent sessions. |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layer map, state ownership (`PracticeViewModel` vs `CanvasState`), and the manual-DI wiring. |
| [docs/DRAWING.md](docs/DRAWING.md) | Input capture (`StrokeCapture`), rendering pipeline, pressure-to-width model. |
| [docs/DATA.md](docs/DATA.md) | Character/vocabulary asset JSON format and the Room persistence layer (v2). |
| [docs/PHASE3-DESIGN.md](docs/PHASE3-DESIGN.md) | Phase 3 contract: SRS scheduler, lesson generator, vocabulary unlocking, Room v2 migration, session UI — plus as-built amendments. |
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
│   ├── viewmodels/PracticeViewModel.kt   # session state machine + grading
│   └── theme/
├── domain/
│   ├── model/                    # HiraganaCharacter, Grade, PracticeAttempt, ReviewCard,
│   │                              # VocabularyWord, StudySession, CharacterProgress, VocabularyProgress
│   ├── repository/               # CharacterRepository, PracticeRepository,
│   │                              # LearningRepository, VocabularyRepository (interfaces)
│   └── learning/                 # SrsScheduler, LessonGenerator (pure, clock-injected)
├── data/
│   ├── assets/                   # JSON loader for assets/hiragana/ (characters + vocabulary.json)
│   ├── local/                    # Room v3: 4 entities/DAOs, MIGRATION_1_2 + MIGRATION_2_3, exportSchema=true
│   └── repository/               # repo implementations
└── drawing/
    ├── stroke/                   # StrokePoint, UserStroke, DrawingSession, StrokeCapture
    ├── canvas/                   # CanvasState, StrokeRenderer, grid/ghost renderers
    └── animation/                # StrokeAnimationEngine, StrokeFramePath (stroke-order playback)
```

Timing data for the animations is generated offline by
`tools/generate_stroke_animations.py` (see below). Room's exported schema
history lives in `app/schemas/`. Unit tests (`SrsSchedulerTest`,
`LessonGeneratorTest`, `VocabularyUnlockTest`) live in
`app/src/test/java/com/hive/hiragana/domain/learning/`.

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
- **Room persists learning state, not just a grade log**: `practice_history`
  (renamed from `practice_attempts` in the v1→v2 migration) is still written
  on every grade, now tagged with which session step produced it; three new
  tables (`review_cards`, `character_progress`, `vocabulary_progress`) hold
  the spaced-repetition and unlock state the session generator reads back
  each time it builds a session.
- **Grading behavior depends on the active session phase**: during a review
  it reschedules the character's spaced-repetition card; during a new
  character's practice reps it (on the final rep) seeds that card; during
  vocabulary it just records the attempt; only in free practice does it
  behave like Phase 1/2 (advance to the next character, no scheduling). Clear
  always just wipes the sheet and stays.

## Phase 3 (shipped) and beyond

**Phase 3 (shipped):** a smart learning system on top of the drawing/animation
engine — frequency-ordered character introduction, a simplified SM-2 spaced-
repetition scheduler (`domain/learning/SrsScheduler.kt`), a deterministic
session generator (`domain/learning/LessonGenerator.kt`) that assembles due
reviews + at most one new character + newly-unlocked/reinforcement vocabulary,
and a `PracticeViewModel` session state machine that walks the learner through
it on the existing single screen. Grades expanded from Easy/Okay/Hard to the
four-point Again/Hard/Good/Easy scale. Room upgraded to v2 (additive migration,
exported schema). 45 unit tests cover the pure scheduler/generator/vocabulary
logic — the project's first tests. See `docs/PHASE3-DESIGN.md` for the full
contract and as-built amendments; not yet exercised on a device/emulator
(including the live database migration).

**Future candidates:** more kana rows (the unlock system absorbs new
vocabulary automatically once new characters ship), stroke-order validation
against the reference paths, a stats/history screen (`observeAttempts()` has
a concrete consumer candidate in the COMPLETE summary), and SRS tuning
(the Hard-grade flat 1-day collapse, ease fuzz, leech detection).
