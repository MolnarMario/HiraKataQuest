# Data & Persistence Reference

Everything about how HiraKataQuest loads character definitions from bundled
assets and persists graded practice attempts to Room. For the overall layer
map see [ARCHITECTURE.md](ARCHITECTURE.md); for how the loaded stroke data is
rendered and traced see [DRAWING.md](DRAWING.md); for project status and known
gaps see [HISTORY.md](../docs/HISTORY.md) and the root [README.md](../README.md).

Current scope (Phases 1–3 plus the 2026-07-04 content expansions): the
**complete kana inventory for two scripts**, each in its own asset directory.
`assets/hiragana/` has 113 characters (46 base gojūon あ〜ん at frequencies 1–46,
25 dakuten/handakuten 47–71, 33 yōon 72–104, and 9 extended — small tsu っ, small
vowels, ゔ, obsolete ゐゑ — at 105–113); `assets/katakana/` has 136 (47 base incl.
the long-vowel mark ー at 101–147, 25 dakuten 148–172, 33 yōon 173–205, 9 extended
singles 206–214, and 22 foreign-sound composites ファ/ティ/… at 215–236). Each
directory has its own `index.json`, one JSON per character (including generated
Phase 2 `strokeAnimations` timing data), and its own `vocabulary.json` (Phase 3;
247 hiragana + 150 katakana words). Four properties are **derived, never
stored** in the JSON (same discipline as `strokeAnimations`): a character's
[Script][] comes from the directory it was read from, its base-vs-voiced
[KanaKind][] from the glyph's Unicode NFD decomposition, and its yōon-ness and
extended-ness from its small kana ([KanaUnits][]). `HiraganaAssetDataSource`
reads every script directory and concatenates the results into one merged deck /
word list; `AssetCharacterRepository` sorts that merged deck by `frequency`
(disjoint per-script bands, so hiragana comes before katakana and — within a
script — base before dakuten before yōon before extended). What the learner
actually studies is narrowed from that merged deck by [ScriptScope][]: to the
active [Script], and — unless they opt in — with dakuten (`includeDiacritics`),
yōon (`includeYoon`), and extended kana (`includeExtended`) held out (all default
off), so a beginner meets only the base gojūon until they enable each group.
Since the post-Phase-3 stroke rework, the `strokes[]` geometry in those assets is
**imported from KanjiVG** (© Ulrich Apel, CC BY-SA 3.0,
<http://kanjivg.tagaini.net>) rather than hand-authored — see the import
pipeline in §1; yōon and extended-katakana composites have no single KanjiVG
file, so their strokes are **composed** from the base kana + a small kana. Room
is **v5** and **unchanged by the content additions above**: those characters
(ids like `kata_a`, `ga`, `kya`, `kata_fu_a`, `sokuon`) and words are just new
rows in the existing tables — no migration. The schema itself grew four times: the
Phase 3 v1→v2 migration renamed the original graded-attempts table to
`practice_history` and added three tables for spaced-repetition state,
introduction progress, and vocabulary unlocks; a later v2→v3 migration added
three nullable handwriting-score columns to `practice_history`; a v3→v4
migration added two tables for **practice sets** — user-curated lists of
characters or words (§5); and a v4→v5 migration added the `reading_progress`
table for the **Reading track**'s per-item quiz accuracy (§5). No network, no
user-editable *asset* data; the four migrations (`MIGRATION_1_2`,
`MIGRATION_2_3`, `MIGRATION_3_4`, `MIGRATION_4_5`) are the project's only ones.

[Script]: ../app/src/main/java/com/hive/hiragana/domain/model/Script.kt
[KanaKind]: ../app/src/main/java/com/hive/hiragana/domain/model/KanaKind.kt
[KanaUnits]: ../app/src/main/java/com/hive/hiragana/domain/model/KanaUnits.kt
[ScriptScope]: ../app/src/main/java/com/hive/hiragana/domain/model/ScriptScope.kt

---

## 1. Asset pipeline

### Layout

```
app/src/main/assets/hiragana/
├── index.json         # file manifest: ordered list of character file basenames
├── a.json … n.json    # one file per character, 46 total (a i u e o / ka ki
│                      # ku ke ko / sa shi su se so / ta chi tsu te to /
│                      # na ni nu ne no / ha hi fu he ho / ma mi mu me mo /
│                      # ya yu yo / ra ri ru re ro / wa wo n)
└── vocabulary.json    # Phase 3+: 153 words, kana + meaning only
```

There are **no other files** in the assets directory. In particular, the
`a.png` … `n.png` files referenced by each JSON's `strokeImage` field do
**not exist** (see [Data integrity caveats](#7-data-integrity-caveats)).

### `index.json`

```json
{
  "characters": ["a", "i", "u", "e", "o", "ka", "ki", "ku", "ke", "ko",
                 "sa", "shi", ..., "wa", "wo", "n"]
}
```

(46 entries in gojūon order; the real file lists every id explicitly.)

- `characters` is a JSON array of **file basenames without the `.json`
  extension**. `HiraganaAssetDataSource` appends `.json` itself
  (`"$ASSET_ROOT/$name.json"`) and passes the basename through as
  `HiraganaCharacter.id` — the stable key the Phase 3 progress tables
  (`review_cards`, `character_progress`) are keyed on.
- **`index.json` is a file manifest only, as of Phase 3 — its array order is
  NOT the study order.** `AssetCharacterRepository` sorts the loaded list by
  `HiraganaCharacter.frequency` ascending before caching it, so study order
  is explicitly the frequency rank (today the two orderings coincide for all
  five shipped characters, but they are no longer the same *mechanism* —
  see docs/PHASE3-DESIGN.md §1). Before Phase 3, `frequency` was parsed but
  unused and `index.json`'s array order was the deck order; that is no
  longer true.

### Stroke geometry provenance — the KanjiVG import pipeline

`strokes[]` (and therefore `svgPath` and the generated `strokeAnimations`)
is **derived data**. The original hand-authored path approximations were
replaced wholesale by geometry imported from **KanjiVG** (© Ulrich Apel,
licensed **CC BY-SA 3.0**, <http://kanjivg.tagaini.net>), which provides
calligraphically correct stroke shapes *and* canonical stroke order for all
kana. State this attribution wherever stroke-data provenance is described.

The pipeline (`tools/import_kanjivg.py`, Python 3.8+, stdlib only,
idempotent):

1. KanjiVG SVGs are **vendored** at `tools/kanjivg/<codepoint>.svg`
   (lowercase-hex codepoint, e.g. `03042.svg` for あ), keeping their
   license headers. The asset-id → codepoint mapping is the `CODEPOINTS`
   dict at the top of the script.
2. For every basename in `index.json`, the tool extracts the per-stroke
   paths (ordered by their `-s<N>` stroke number, verified to run exactly
   1..N) and **normalizes them to absolute `M`/`L`/`C`/`Q` commands** —
   the same strict dialect `generate_stroke_animations.py` enforces.
   Relative commands and `H`/`h`/`V`/`v`/`S`/`s` are converted; anything
   else (arcs, `T`, closepath) is a hard error. All coordinates are
   validated to sit inside `[0, 109]`.
3. It rewrites each character JSON in place: `strokes[]` (one absolute
   path per stroke), `svgPath` (still written as the strokes joined with
   a single space), `viewBox` (**109** — KanjiVG's fixed `0 0 109 109`
   coordinate space), and **deletes `strokeAnimations`**, which is stale
   after any geometry change and must be regenerated by running
   `tools/generate_stroke_animations.py` immediately afterwards (until
   then the app falls back to the static stroke-order diagram).

**Never hand-edit `strokes[]`** — fix the vendored SVG or the mapping and
re-run the import, then the animation generator.

### Per-character JSON schema

All coordinates in `strokes` / `svgPath` are authored in a **square viewBox
coordinate space** whose side length is the `viewBox` field (109 for all
shipped files — KanjiVG's coordinate space). Renderers scale from that
space to pixels at draw time
(`size.minDimension / character.viewBox` in `StrokeOrderDiagram`).

| Field | Type | Required? | Default if absent | Consumer in code | Status |
|---|---|---|---|---|---|
| `character` | string (the glyph, e.g. `"あ"`) | **Yes** — `json.getString` throws `JSONException` | — | `CharacterGuidePanel` (large glyph), ghost glyph in `TracingCanvas` via `PracticeScreen`, `remember` key in `StrokeOrderDiagram`, copied into `PracticeAttempt`, matched against `VocabularyWord.requiredCharacters` | Active |
| `romaji` | string (e.g. `"a"`) | **Yes** — throws | — | `CharacterGuidePanel` label, copied into `PracticeAttempt` | Active |
| `frequency` | int (frequency rank) | **Yes** — `json.getInt` throws | — | `AssetCharacterRepository` sorts the deck by this, ascending | Active as of Phase 3 — study order derives from this field, not `index.json`'s listing order |
| `svgPath` | string (all strokes as one SVG path) | No | `strokes.joinToString(" ")` | None. Stored on the model, never read | **Redundant duplicate** of `strokes` joined with spaces; `tools/import_kanjivg.py` writes it as exactly that join, so in every shipped file it is byte-for-byte the join. Tool-maintained now — never hand-edit |
| `strokeImage` | string (PNG file name, e.g. `"a.png"`) | No | `""` (`optString`) | None. Stored on the model, never read | Parsed but **unused**; the referenced PNGs are **not shipped**. Reserved for a raster stroke-order image in a later phase |
| `viewBox` | number (side of square coord space) | No | `100.0` (`optDouble`) | `StrokeOrderDiagram` scaling | Active. All shipped files set it explicitly to **109** (KanjiVG's space, written by `import_kanjivg.py`); the parser default stays 100 |
| `strokes` | array of strings — **one SVG path per stroke, in canonical stroke order** | **Yes** — `json.getJSONArray` throws; each element read with `getString` | — | `StrokeOrderDiagram` (parsed with Compose `PathParser`, numbered badge at each path's first `MoveTo`), the faint guide in `AnimatedStrokePreview`, `HiraganaCharacter.strokeCount` shown in the guide panel ("Stroke order · N"), input to the timing generator | Active — this is the load-bearing field. **Derived data** since the KanjiVG import: written by `tools/import_kanjivg.py` from the vendored KanjiVG SVGs; never hand-edit, re-import instead (attribution above) |
| `strokeAnimations` | array of `{ "order": int, "points": [ { "x", "y", "time" }, … ] }` | No — absent → `emptyList()` | `emptyList()` | `StrokeAnimationEngine` via `HiraganaCharacter.strokeAnimations` (drives the Phase 2 animated demo, which since Phase 3 also serves as a new character's session introduction) | Active (Phase 2). **Derived data** — generated from `strokes` by `tools/generate_stroke_animations.py`; never hand-edit, re-run the generator |

`id` (the asset basename, e.g. `"a"`) is not a JSON field — `HiraganaAssetDataSource`
supplies it itself from the filename during parsing, alongside the JSON-sourced fields
above.

The import tool emits only absolute `M`/`L`/`C`/`Q` commands (the strict
dialect `generate_stroke_animations.py` accepts — anything else fails the
generator hard); the KanjiVG kana shipped today happen to produce only `M`
(absolute moveto) and `C` (absolute cubic bézier). Each stroke's badge
position comes from its first `MoveTo` node, so every stroke path begins
with `M x y`.

### `strokeAnimations` semantics (Phase 2)

- `order` is **1-based** and matches index+1 of the corresponding `strokes[]`
  entry. `points[i].time` is **milliseconds since the start of that stroke**
  (per-stroke clock), strictly increasing, first point always `time: 0`.
- **Coordinates are in viewBox space (0–109 in the shipped files), NOT
  normalized 0–1** — the same space as `strokes[]`, deliberately (one
  coordinate system for guide, diagram, and animation).
- Parsing (`parseStrokeAnimations`) is **all-or-nothing per character** and
  additionally **consistency-checked against `strokes[]`**: the list is
  voided unless it has exactly one entry per stroke, orders run exactly
  1..N, and every entry has ≥ 1 point. Malformed or inconsistent data logs a
  `Log.w` (tag `HiraganaAssets`) and yields `emptyList()`, so the UI falls
  back to the static diagram rather than demonstrating a wrong stroke count.
  Unlike a malformed *required* field, bad animation data never fails the
  deck load.
- The generator resamples each SVG path by arc length (~2 viewBox units,
  8–48 points/stroke) and assigns times with a natural-writing model
  (~160 viewBox-units/s, min 180 ms/stroke, ease-in/out). It self-validates
  after writing and exits non-zero on any violation.
- Because `import_kanjivg.py` **deletes** `strokeAnimations` when it rewrites
  the geometry, the generator must be re-run after every import — the two
  tools are a pipeline, import first, generate second.

### `vocabulary.json` schema (Phase 3)

A separate, independent asset — a malformed `vocabulary.json` never affects
the character load path (see §7 caveat 8). Full shipped contents:

```json
{
  "words": [
    { "kana": "あい", "meaning": "love" },
    { "kana": "いえ", "meaning": "house" },
    { "kana": "うえ", "meaning": "up; above" },
    { "kana": "あお", "meaning": "blue" },
    { "kana": "いう", "meaning": "to say" },
    { "kana": "あう", "meaning": "to meet" },
    { "kana": "いいえ", "meaning": "no" },
    { "kana": "おい", "meaning": "hey!" },
    { "kana": "うお", "meaning": "fish" }
  ]
}
```

Nine words, each with exactly two fields:

| Field | Type | Required? | Consumer in code |
|---|---|---|---|
| `kana` | string (the word, e.g. `"あい"`) | **Yes** — `getString` throws | `VocabularyWord.kana`; traced whole (one cell per kana on a single sheet) in the VOCABULARY session phase; also the row key in `vocabulary_progress` |
| `meaning` | string (short English gloss) | **Yes** — throws | `VocabularyWord.meaning`; shown in `CharacterGuidePanel`'s word header |

There is **no `requiredCharacters` field in the JSON.** `HiraganaAssetDataSource.loadVocabulary()`
builds each word via `VocabularyWord.fromKana(kana, meaning)`, which derives
`requiredCharacters` as the set of distinct characters in `kana` — the single
place that derivation rule lives (both the parser and `VocabularyUnlockTest`
call it, so they cannot drift apart). All 153 shipped words are composable
entirely from the 46 shipped characters (あ〜ん); every kana except を —
which forms no standalone words — appears in at least one word.

Unlike character parsing, `loadVocabulary()` **throws** on malformed JSON
rather than voiding just the bad entry — the caller (`RoomVocabularyRepository.getWords()`)
wraps the call, logs (`Log.w`, tag `HiraganaAssets`) and falls back to an
empty word list on failure, since a broken vocabulary asset must never take
down character loading. Parsed words are cached for the process lifetime
behind a `Mutex`, the same pattern as `AssetCharacterRepository`.

## 2. Complete real example — `app/src/main/assets/hiragana/a.json`

```json
{
  "character": "あ",
  "romaji": "a",
  "frequency": 1,
  "strokeImage": "a.png",
  "viewBox": 109,
  "strokes": [
    "M 31.01 33 C 31.89 33.88 33.76 34.82 36.26 34.75 C 44.88 34.5 56.26 32.63 65.76 30.5 C 67.27 30.16 70.38 29.62 72.38 30",
    "M 49.76 17.62 C 50.64 18.62 51.58 20.88 51.14 22.87 C 47.39 39.62 44.89 61 46.01 76.5 C 46.42 82.2 47.89 87.38 49.39 90.12",
    "M 65.63 44.12 C 66.38 45.24 66.79 48.51 66.13 50.24 C 61.51 62.5 54.89 74 40.76 86 C 33.9 91.83 24.88 89.75 24.51 77.62 C 24.17 66.75 37.89 54.5 56.89 50.88 C 69.31 48.51 83.89 52.26 87.39 63.63 C 91.44 76.81 83.63 90 66.51 94.12"
  ],
  "svgPath": "M 31.01 33 C 31.89 33.88 … 66.51 94.12"
}
```

(`svgPath` abbreviated above — in the real file it is the three stroke
strings joined with single spaces, exactly as `import_kanjivg.py` writes it.
`strokeAnimations` omitted for brevity — every shipped file carries it, one
entry per stroke; あ's runs 22/38/48 points over 264/461/1238 ms. All path
coordinates are KanjiVG-derived, in the 0–109 viewBox space.) The
other four files (`i.json`, `u.json`, `e.json`, `o.json`) have identical
shape; `frequency` runs 1–5 and (as of Phase 3) *is* the study order —
stroke counts are い/う = 2, あ/お = 3, え = 2. `HiraganaCharacter.id` for
this file is `"a"`, supplied by the loader from the filename, not from any
field inside the JSON shown above.

## 3. Parsing and caching

### `HiraganaAssetDataSource`
`app/src/main/java/com/hive/hiragana/data/assets/HiraganaAssetDataSource.kt`

- Constructed with an `android.content.res.AssetManager` (wired in
  `AppContainer`, see `HiraganaApplication.kt`).
- `suspend fun loadCharacters(): List<HiraganaCharacter>` and (Phase 3)
  `suspend fun loadVocabulary(): List<VocabularyWord>` each run entirely on
  `Dispatchers.IO` via `withContext`.
- Uses the **platform `org.json`** classes (`JSONObject`, `JSONArray`) — no
  kotlinx.serialization, Moshi, or Gson dependency.
- Character flow: read `hiragana/index.json` → `getJSONArray("characters")` →
  for each basename, read `hiragana/<name>.json` and `parseCharacter(json, id = name)`.
- Vocabulary flow (Phase 3, independent of the above): read
  `hiragana/vocabulary.json` → `getJSONArray("words")` → each element through
  `VocabularyWord.fromKana(json.getString("kana"), json.getString("meaning"))`.
- Strict vs lenient fields in `parseCharacter`:
  - `getString("character")`, `getString("romaji")`, `getInt("frequency")`,
    `getJSONArray("strokes")` — **throw `JSONException`** if missing/wrong
    type. A malformed file therefore fails the whole load (there is no
    per-file recovery), which surfaces in the UI as the
    `"Failed to load characters: …"` error state set in `PracticeViewModel.startSession()`'s
    `runCatching`.
  - `optString("svgPath", strokes.joinToString(" "))`,
    `optString("strokeImage")` (→ `""`),
    `optDouble("viewBox", 100.0).toFloat()` — silent defaults.
  - `loadVocabulary()`'s `getString("kana")`/`getString("meaning")` also
    throw on a malformed entry, but here the *caller* (`RoomVocabularyRepository`)
    catches it and falls back to an empty list rather than failing the
    character deck — a broken vocabulary asset must not take down tracing.

### `AssetCharacterRepository`
`app/src/main/java/com/hive/hiragana/data/repository/AssetCharacterRepository.kt`

Implements `com.hive.hiragana.domain.repository.CharacterRepository`
(single method: `suspend fun getCharacters(): List<HiraganaCharacter>`).

```kotlin
override suspend fun getCharacters(): List<HiraganaCharacter> = mutex.withLock {
    cache ?: dataSource.loadCharacters().sortedBy { it.frequency }.also { cache = it }
}
```

- A `kotlinx.coroutines.sync.Mutex` serialises the first load, so concurrent
  callers cannot trigger duplicate asset parses; later callers get the cached
  list without touching disk.
- **Sorted by `frequency` ascending (Phase 3)** — this is now the one place
  study order is decided; `index.json`'s array order no longer matters once
  loading is done (see §1).
- The cache is **never invalidated** for the process lifetime. That is safe
  because the data source is read-only APK assets: the contents cannot change
  without reinstalling the app, which restarts the process anyway. If a later
  phase adds downloadable or user-edited character sets, this class needs an
  invalidation story.

### `RoomVocabularyRepository.getWords()` (Phase 3)
`app/src/main/java/com/hive/hiragana/data/repository/RoomVocabularyRepository.kt`

The vocabulary-side analog of `AssetCharacterRepository`, caching `HiraganaAssetDataSource.loadVocabulary()`'s
result behind a `Mutex` for the process lifetime — including caching the
empty-list fallback for a malformed asset, so a broken `vocabulary.json`
only ever logs once, not on every call. This same class also owns
`vocabulary_progress` reads/writes (`getProgress`, `unlock`, `recordPractice`)
via `VocabularyProgressDao` — see §5.

## 4. Domain models

All in `app/src/main/java/com/hive/hiragana/domain/model/` unless noted.

### `HiraganaCharacter`

| Property | Type | Notes |
|---|---|---|
| `id` | `String = ""` | **Phase 3.** Stable identifier: the asset file basename from `index.json` (`"a"`, `"i"`, …), supplied by the loader, not the JSON. Primary key for `review_cards` and `character_progress`. |
| `character` | `String` | The glyph itself, e.g. `"あ"` |
| `romaji` | `String` | Romanisation, doubles as the asset file basename |
| `frequency` | `Int` | Frequency rank. **Active as of Phase 3** — `AssetCharacterRepository` sorts the deck by this field ascending; it is the study-order source, not `index.json`'s array order (see §1/§3) |
| `svgPath` | `String` | All strokes as one path string; currently unused |
| `strokeImage` | `String` | Raster image file name; unused, no PNGs shipped |
| `strokes` | `List<String>` | One SVG path per stroke, canonical stroke order (KanjiVG-derived, written by `import_kanjivg.py`) |
| `viewBox` | `Float = 100f` | Side of the square stroke coordinate space; the parser default is 100f but every shipped file carries 109 (KanjiVG) |
| `strokeAnimations` | `List<StrokeDefinition> = emptyList()` | Phase 2 timed stroke data; drives `StrokeAnimationEngine` |
| `strokeCount` | `Int` (derived: `get() = strokes.size`) | **Canonical** stroke count of the character; shown in `CharacterGuidePanel` |

### `Grade`

Manual self-assessment enum, stored in Room **by `name`** (see §5). **Phase 3 extended
this from `{EASY, OKAY, HARD}` to the four-point simplified-SM-2 scale**, in
worst-to-best order (matches the action bar's button order):

| Constant | `label` (UI text) |
|---|---|
| `AGAIN` | `"Again"` |
| `HARD` | `"Hard"` |
| `GOOD` | `"Good"` |
| `EASY` | `"Easy"` |

Rows persisted before Phase 3 stored `"OKAY"`, which no longer exists as a constant;
`RoomPracticeRepository.toDomain()` maps that legacy string (and any other unrecognised
one) to `Grade.GOOD` — see §7 caveat 2.

### `PracticeAttempt`

One graded tracing attempt, persisted to `practice_history` (renamed from
`practice_attempts` in the Phase 3 v1→v2 migration — see §5).

| Property | Type | Notes |
|---|---|---|
| `id` | `Long = 0L` | 0 until Room assigns an autogenerated id |
| `character` | `String` | Glyph traced |
| `romaji` | `String` | Copied from the character |
| `grade` | `Grade` | User's self-assessment |
| `strokeCount` | `Int` | **Strokes the USER actually drew** (`DrawingSession.strokes.size`) — *not* the character's canonical `HiraganaCharacter.strokeCount`. Comparing the two is a future-analytics hook, e.g. 5 drawn vs 3 canonical for あ |
| `durationMs` | `Long` | `DrawingSession.durationMillis()`: last sample timestamp minus first, MotionEvent "ms since boot" clock, coerced ≥ 0; `0` if nothing was drawn |
| `createdAt` | `Long` | `System.currentTimeMillis()` wall-clock epoch ms |
| `source` | `PracticeSource = PracticeSource.FREE` | **Phase 3.** Which session step produced the attempt. Rows written before Phase 3 (or by any future unrecognised source string) decode to `FREE`. |

`PracticeSource` (Phase 3, defined in the same file as `PracticeAttempt`):

| Constant | Meaning |
|---|---|
| `REVIEW` | Graded during the REVIEWS session phase |
| `NEW` | Graded during NEW_CHARACTER/PRACTICE (introducing a character) |
| `VOCAB` | Graded during VOCABULARY (tracing a word's kana) |
| `FREE` | Graded during the FREE loop, or any row predating Phase 3 |

### `ReviewCard` (Phase 3)

Spaced-repetition state for one character, mirroring the `review_cards` table (§5).

| Property | Type | Notes |
|---|---|---|
| `characterId` | `String` | `HiraganaCharacter.id` this card schedules |
| `repetitions` | `Int` | Successful reviews in a row; `Grade.AGAIN` resets to 0 |
| `intervalDays` | `Int` | Current inter-review interval, whole days |
| `easeFactor` | `Float` | The `Grade.GOOD` interval multiplier; starts at 1.7, adjusted by Hard (−0.15, floor 1.3) and Easy (+0.15, cap 2.5) |
| `nextReviewDate` | `Long` | Epoch millis of the local start-of-day the card becomes due |

Transitions live in `domain/learning/SrsScheduler.kt` — see docs/PHASE3-DESIGN.md §2 and
docs/ARCHITECTURE.md §8 for the full transition table.

### `VocabularyWord` (Phase 3)

| Property | Type | Notes |
|---|---|---|
| `kana` | `String` | The word, e.g. `"あい"`; also its stable id (`vocabulary_progress` PK) |
| `meaning` | `String` | Short English gloss |
| `requiredCharacters` | `Set<String>` | Distinct characters the word needs — **derived, never stored in JSON** |

Companion `fromKana(kana, meaning)` builds a word with `requiredCharacters` computed as
`kana.map { it.toString() }.toSet()` — the single place this derivation happens; both
`HiraganaAssetDataSource` and `VocabularyUnlockTest` call it.

### `StudySession`, `CharacterProgress`, `VocabularyProgress` (Phase 3)

`StudySession` is `LessonGenerator`'s output — `reviews: List<HiraganaCharacter>`,
`newCharacter: HiraganaCharacter?`, `practiceRepetitions: Int`,
`vocabulary: List<VocabularyWord>`, plus a derived `isEmpty`. It is never persisted; see
docs/PHASE3-DESIGN.md §4's "crash-safe resumability for free" note. `CharacterProgress`
(`characterId`, `introducedAt`, `totalTraces`) and `VocabularyProgress` (`kana`,
`unlockedAt`, `timesPracticed`, `lastPracticedAt: Long?`) mirror the `character_progress`
and `vocabulary_progress` tables one-for-one (§5).

## 5. Room persistence

All in `app/src/main/java/com/hive/hiragana/data/local/`. The schema is at **v5**.
**Phase 3 upgraded the database from v1 to v2** — additive changes plus one rename,
via a real `Migration`, no destructive fallback — a later handwriting-scoring
change upgraded **v2 to v3**, purely additive (three nullable columns), the
practice-sets feature upgraded **v3 to v4**, purely additive (two new tables), and
the Reading track upgraded **v4 to v5**, purely additive (one new table).

### Database

`HiraganaDatabase` — `version = 5` (was 1, then 2, then 3, then 4), **`exportSchema = true`** (was
`false` — this closes a wart §7 had flagged since Phase 1), file name
`"hiragana-practice.db"` (unchanged), built via the `HiraganaDatabase.build(context)`
companion helper (`Room.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()`).
Instantiated lazily in `AppContainer`. The exported schema history is committed at
`app/schemas/com.hive.hiragana.data.local.HiraganaDatabase/` — `2.json`, `3.json`,
`4.json`, and `5.json` all exist; future schema changes must add a new numbered file here via a
real `Migration`, never a destructive fallback.

### `MIGRATION_1_2`

A single `Migration(1, 2)` object, executed in order:

```sql
ALTER TABLE practice_attempts RENAME TO practice_history;
ALTER TABLE practice_history ADD COLUMN source TEXT NOT NULL DEFAULT 'FREE';

CREATE TABLE IF NOT EXISTS `review_cards` (
    `characterId` TEXT NOT NULL,
    `repetitions` INTEGER NOT NULL,
    `intervalDays` INTEGER NOT NULL,
    `easeFactor` REAL NOT NULL,
    `nextReviewDate` INTEGER NOT NULL,
    PRIMARY KEY(`characterId`)
);

CREATE TABLE IF NOT EXISTS `character_progress` (
    `characterId` TEXT NOT NULL,
    `introducedAt` INTEGER NOT NULL,
    `totalTraces` INTEGER NOT NULL,
    PRIMARY KEY(`characterId`)
);

CREATE TABLE IF NOT EXISTS `vocabulary_progress` (
    `kana` TEXT NOT NULL,
    `unlockedAt` INTEGER NOT NULL,
    `timesPracticed` INTEGER NOT NULL,
    `lastPracticedAt` INTEGER,
    PRIMARY KEY(`kana`)
);
```

A v1 database (any row set from Phases 1–2) upgrades in place: existing
`practice_attempts` rows survive under `practice_history` with `source = 'FREE'`; the
three new tables start empty, so a returning user's characters are all treated as
un-introduced until they resume a session (there is no way to backfill `ReviewCard`s
from historical grades — the old schema never recorded which character was traced with
enough structure for that). **This migration is statically verified against the exported
v2 schema but has never run against a real v1 database on a device/emulator** — see §7
caveat 9.

### `MIGRATION_2_3`

A single `Migration(2, 3)` object (handwriting-quality scoring), **purely additive**
— three nullable `REAL` columns on `practice_history`, no rename, no new table:

```sql
ALTER TABLE practice_history ADD COLUMN form_score REAL;
ALTER TABLE practice_history ADD COLUMN fit_score REAL;
ALTER TABLE practice_history ADD COLUMN recognition_score REAL;
```

The columns are **nullable with no default**, so every existing row (and every future
unscored row — a multi-copy 2×2/3×3 layout, or the unscorable-word fallback row) reads
back as "unscored" (`NULL`). Scored single-character rows and, since the per-kana word
change, both the per-kana word rows and the word-level average row carry non-null
form/fit (see §6). These persist the advisory
handwriting score alongside each attempt (§4/§5 entity below); they never feed the SRS
scheduler or any gate — the score is coaching feedback only. `HiraganaDatabase.build`
registers all four migrations (`.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`).

### `MIGRATION_3_4`

A single `Migration(3, 4)` object (the **practice-sets** feature — user-curated lists
of characters or words), **purely additive** — two new tables, no rename, no touch to
any existing data:

```sql
CREATE TABLE IF NOT EXISTS `practice_sets` (
    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    `name` TEXT NOT NULL,
    `type` TEXT NOT NULL,
    `script` TEXT NOT NULL,
    `createdAt` INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS `practice_set_items` (
    `itemId` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    `setId` INTEGER NOT NULL,
    `value` TEXT NOT NULL,
    `position` INTEGER NOT NULL,
    FOREIGN KEY(`setId`) REFERENCES `practice_sets`(`id`)
        ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS `index_practice_set_items_setId`
    ON `practice_set_items` (`setId`);
```

A set's members live in `practice_set_items` rows keyed by `setId`, deleted with their
parent set via the `ON DELETE CASCADE` foreign key; the `index_practice_set_items_setId`
index backs the relation read. Practice sets are plain user data (an explicit membership
list, not SRS state), so no clock injection is involved. The tables start empty on any
v3 database — nothing to backfill.

### `MIGRATION_4_5`

A single `Migration(4, 5)` object (the **Reading track** — recognizing the printed
gothic forms, quizzed by multiple-choice or typed romaji rather than handwriting),
**purely additive** — one new table, no rename, no touch to any existing data:

```sql
CREATE TABLE IF NOT EXISTS `reading_progress` (
    `itemKey` TEXT NOT NULL,
    `mcqAttempts` INTEGER NOT NULL,
    `mcqCorrect` INTEGER NOT NULL,
    `typeAttempts` INTEGER NOT NULL,
    `typeCorrect` INTEGER NOT NULL,
    PRIMARY KEY(`itemKey`)
);
```

The Reading quiz records its tallies **here, not in `practice_history`** (that table
stays exclusively the handwriting-attempt log). The Reading **flashcard** study mode
is self-graded and records **nothing**. The table starts empty on any v4 database —
nothing to backfill.

### Table: `practice_history` (`PracticeAttemptEntity`) — renamed from `practice_attempts`

| Column | SQL type (from Kotlin type) | Entity property | Notes |
|---|---|---|---|
| `id` | `INTEGER` PRIMARY KEY AUTOINCREMENT-style (`autoGenerate = true`) | `id: Long` | |
| `character` | `TEXT` | `character: String` | The glyph, e.g. `"あ"` |
| `romaji` | `TEXT` | `romaji: String` | |
| `grade` | `TEXT` | `grade: String` | **`Grade` enum stored as its `name`** (`"AGAIN"`/`"HARD"`/`"GOOD"`/`"EASY"`, or legacy `"OKAY"`), mapped manually in `RoomPracticeRepository` — no `TypeConverter` |
| `stroke_count` | `INTEGER` | `strokeCount: Int` | Strokes the user drew (see §4) |
| `duration_ms` | `INTEGER` | `durationMs: Long` | |
| `created_at` | `INTEGER` | `createdAt: Long` | Epoch ms; the read query sorts by this |
| `source` | `TEXT` | `source: String = "FREE"` | **Phase 3.** `PracticeSource` enum stored as its `name`; `@ColumnInfo(defaultValue = "FREE")` backs the migration's `DEFAULT 'FREE'` |
| `form_score` | `REAL`, nullable | `formScore: Float? = null` | **v3.** FORM dial (0..100) of the advisory handwriting score; `NULL` for pre-v3 rows and unscored rows (multi-copy 2×2/3×3 layouts, the unscorable-word fallback row). Set on scored **per-kana word rows** and the **word-level average row** too — see the per-kana word behavior in §6 |
| `fit_score` | `REAL`, nullable | `fitScore: Float? = null` | **v3.** FIT dial (0..100); set/`NULL` under the same conditions as `form_score` |
| `recognition_score` | `REAL`, nullable | `recognitionScore: Float? = null` | **v3.** ML Kit recognition sub-score (0..100); `NULL` when recognition didn't run |

No indices beyond the primary key. No foreign keys (characters live in
assets, not the DB). The three v3 score columns are **advisory only** — nothing
reads them back into the SRS scheduler or any gate; they are persisted for a future
history/stats readout and the Library's per-character score summary.

### DAO: `PracticeAttemptDao`

| Method | Signature | Notes |
|---|---|---|
| `insert` | `@Insert suspend fun insert(attempt: PracticeAttemptEntity): Long` | Returns the new row id (return value ignored by the caller) |
| `observeAll` | `@Query("SELECT * FROM practice_history ORDER BY created_at DESC") fun observeAll(): Flow<List<PracticeAttemptEntity>>` | Table name updated for the rename; newest first; **no current subscriber** (see §6) |
| `observeScoreStats` | `fun observeScoreStats(): Flow<List<CharacterScoreStats>>` | Per-glyph quality aggregates for the Library, `GROUP BY character` over `AVG/MAX(form_score)`, `AVG/MAX(fit_score)`, `COUNT(*)`, `WHERE form_score IS NOT NULL`. The `NOT NULL` filter keeps unscored and pre-v3 rows out; **scored per-kana word rows and word-level average rows DO pass it**, so both feed the per-glyph squares and the word card (see §6) |

### Repository: `RoomPracticeRepository`

Implements `domain.repository.PracticeRepository`. Maps entity ↔ domain with
two private extension functions:

- `PracticeAttempt.toEntity()` — `grade.name` and `source.name` into their `TEXT`
  columns; drops `id` (lets Room autogenerate).
- `PracticeAttemptEntity.toDomain()` — reverses it with
  `runCatching { Grade.valueOf(grade) }.getOrDefault(Grade.GOOD)` (any
  unrecognised stored string, including the legacy `"OKAY"`, silently becomes
  `GOOD` — see §7) and, likewise,
  `runCatching { PracticeSource.valueOf(source) }.getOrDefault(PracticeSource.FREE)`.

### Table: `review_cards` (`ReviewCardEntity`) — Phase 3

| Column | SQL type | Entity property | Notes |
|---|---|---|---|
| `characterId` | `TEXT` PRIMARY KEY | `characterId: String` | `HiraganaCharacter.id` |
| `repetitions` | `INTEGER` | `repetitions: Int` | |
| `intervalDays` | `INTEGER` | `intervalDays: Int` | |
| `easeFactor` | `REAL` | `easeFactor: Float` | |
| `nextReviewDate` | `INTEGER` | `nextReviewDate: Long` | Epoch millis, local start-of-day |

**DAO (`ReviewCardDao`)**: `getAll(): List<ReviewCardEntity>` (`suspend`, no `Flow` —
the ViewModel reads the whole table once per session start, not continuously);
`upsert(entity)` via `@Insert(onConflict = OnConflictStrategy.REPLACE)` — one row per
character, so "insert or replace" is exactly upsert semantics with a single-column PK.

**Repository (`RoomLearningRepository`)**: implements `LearningRepository`
(`getReviewCards`, `upsertReviewCard`, plus the `character_progress` methods below).
Straight entity↔domain field copies, no enum mapping needed. Unlike
`RoomPracticeRepository`, failures are **not** swallowed here — callers (the ViewModel)
decide, and do so via `runCatching { ... }.onFailure { Log.w(TAG, ..., it) }` (tag
`HiraganaSession`, in `PracticeViewModel`).

### Table: `character_progress` (`CharacterProgressEntity`) — Phase 3

| Column | SQL type | Entity property | Notes |
|---|---|---|---|
| `characterId` | `TEXT` PRIMARY KEY | `characterId: String` | |
| `introducedAt` | `INTEGER` | `introducedAt: Long` | Epoch millis when first introduced |
| `totalTraces` | `INTEGER` | `totalTraces: Int` | Incremented on every practice/review/vocab trace of this character |

**DAO (`CharacterProgressDao`)**: `getAll()`; `insert(entity)` via
`OnConflictStrategy.IGNORE` (a row is created once, on introduction, and never
overwritten); `incrementTraces(characterId)` — a raw `UPDATE ... SET totalTraces =
totalTraces + 1 WHERE characterId = :characterId` rather than a read-modify-write
round trip.

### Table: `vocabulary_progress` (`VocabularyProgressEntity`) — Phase 3

| Column | SQL type | Entity property | Notes |
|---|---|---|---|
| `kana` | `TEXT` PRIMARY KEY | `kana: String` | The word; a row exists only for unlocked words |
| `unlockedAt` | `INTEGER` | `unlockedAt: Long` | Epoch millis, stamped with start-of-day |
| `timesPracticed` | `INTEGER` | `timesPracticed: Int` | |
| `lastPracticedAt` | `INTEGER`, nullable | `lastPracticedAt: Long?` | Null until first practiced after unlock |

**DAO (`VocabularyProgressDao`)**: `getAll()`; `insert(entity)` via
`OnConflictStrategy.IGNORE` (an unlock is written once); `recordPractice(kana, atMillis)`
— a raw `UPDATE ... SET timesPracticed = timesPracticed + 1, lastPracticedAt = :atMillis`.

**Repository (`RoomVocabularyRepository`)**: implements `VocabularyRepository`
(`getWords` — described in §3 — plus `getProgress`, `unlock`, `recordPractice` over this
table).

### Table: `practice_sets` (`PracticeSetEntity`) — v4

One saved practice set's header (`data/local/PracticeSetEntity.kt`). A set is a
**user-curated, hand-picked list** of characters or words, saved so it can be quizzed
or free-practiced later — deliberately distinct from the declarative Study Scope
opt-ins (a set is an explicit membership list, not a filter config).

| Column | SQL type | Entity property | Notes |
|---|---|---|---|
| `id` | `INTEGER` PRIMARY KEY AUTOINCREMENT (`autoGenerate = true`) | `id: Long = 0L` | 0 until Room assigns it |
| `name` | `TEXT` | `name: String` | User-chosen set name |
| `type` | `TEXT` | `type: String` | **`PracticeSetType` enum stored as its `name`** (`"CHARACTERS"`/`"WORDS"`) — decides how members resolve (a glyph vs a whole word's kana). `PracticeSetType.fromKey` decodes it, falling back to `CHARACTERS` on unknown/null |
| `script` | `TEXT` | `script: String` | **`Script` enum stored as its `key`** — the writing system the set was built in (selection is always scoped to one script) |
| `createdAt` | `INTEGER` | `createdAt: Long` | `System.currentTimeMillis()` epoch ms, stamped in the repository; the observe query sorts by this |

### Table: `practice_set_items` (`PracticeSetItemEntity`) — v4

One member of a practice set (same file). Deleted with its parent set via
`ForeignKey.CASCADE`.

| Column | SQL type | Entity property | Notes |
|---|---|---|---|
| `itemId` | `INTEGER` PRIMARY KEY AUTOINCREMENT (`autoGenerate = true`) | `itemId: Long = 0L` | |
| `setId` | `INTEGER`, indexed, FK → `practice_sets(id)` `ON DELETE CASCADE` | `setId: Long` | Which set this member belongs to |
| `value` | `TEXT` | `value: String` | The member key — the glyph for a `CHARACTERS` set, the word's kana for a `WORDS` set |
| `position` | `INTEGER` | `position: Int` | Preserves the order the user selected members in |

`PracticeSetWithItems` (a `@Relation`, same file) reads a header plus its member rows
together via `@Embedded val set` + `@Relation(parentColumn = "id", entityColumn = "setId")`.

### DAO: `PracticeSetDao` — v4

`data/local/PracticeSetDao.kt`. Only used through `RoomPracticeSetRepository`.

| Method | Signature | Notes |
|---|---|---|
| `observeSetsWithItems` | `@Transaction @Query("SELECT * FROM practice_sets ORDER BY createdAt DESC") fun observeSetsWithItems(): Flow<List<PracticeSetWithItems>>` | Newest first; observed as a `Flow` so the Study Scope sheet and Quiz-me source picker stay live as sets change. `@Transaction` because it fans out to the member rows |
| `insertSet` | `@Insert suspend fun insertSet(set): Long` | Returns the new set id |
| `insertItems` | `@Insert suspend fun insertItems(items)` | |
| `deleteItems` | `@Query("DELETE FROM practice_set_items WHERE setId = :setId") suspend fun deleteItems(setId)` | |
| `renameSet` | `@Query("UPDATE practice_sets SET name = :name WHERE id = :setId") suspend fun renameSet(setId, name)` | |
| `deleteSet` | `@Query("DELETE FROM practice_sets WHERE id = :setId") suspend fun deleteSet(setId)` | Member rows cascade away with it |
| `createSet` | `@Transaction suspend fun createSet(set, values): Long` | Inserts the header then its ordered members (a private `toItemEntities` maps each value to a `position`-indexed row) in one transaction |
| `replaceItems` | `@Transaction suspend fun replaceItems(setId, values)` | Deletes then re-inserts a set's members wholesale (used when editing membership) |

**Domain model (`PracticeSet`)**: `domain/model/PracticeSet.kt` —
`PracticeSet(id, name, type: PracticeSetType, script: Script, members: List<String>)`,
where `members` are the app's script-unique keys (glyph string for `CHARACTERS`, the
word's `kana` for `WORDS`), in selection order.

**Repository (`RoomPracticeSetRepository`)**: implements `PracticeSetRepository`
(`observeSets(): Flow<List<PracticeSet>>`, `createSet`, `renameSet`, `deleteSet`,
`updateMembers`). Maps `PracticeSetWithItems` → `PracticeSet` with members sorted by
`position`; `createSet` stamps `createdAt = System.currentTimeMillis()` (practice sets
are plain user data, not SRS state, so they do **not** use the injected scheduler
clock). Wired via `AppContainer.practiceSetRepository`.

### Table: `reading_progress` (`ReadingProgressEntity`) — v5

Per-item **Reading-quiz** accuracy (`data/local/ReadingProgressEntity.kt`). One row
per glyph/word records two **independent** answer-style tallies — multiple-choice and
typed-romaji — so a learner's success rate in each style is tracked separately.
Reading is **pass/fail**, so — unlike the handwriting scorer — there is **no "best"**;
the meaningful figures are the two success percentages.

| Column | SQL type | Entity property | Notes |
|---|---|---|---|
| `itemKey` | `TEXT` PRIMARY KEY | `itemKey: String` | The printed glyph/kana string — the **same key the Library tints cells by** (glyph for a character, whole kana for a word) |
| `mcqAttempts` | `INTEGER` | `mcqAttempts: Int = 0` | Multiple-choice questions faced for this item |
| `mcqCorrect` | `INTEGER` | `mcqCorrect: Int = 0` | …of which answered correctly |
| `typeAttempts` | `INTEGER` | `typeAttempts: Int = 0` | Typed-romaji questions faced |
| `typeCorrect` | `INTEGER` | `typeCorrect: Int = 0` | …of which answered correctly |

**DAO (`ReadingProgressDao`)**: `observeAll(): Flow<List<ReadingProgressEntity>>`;
plus an **ensure-then-increment pair** used to record one answer — `ensure(row)` via
`@Insert(onConflict = OnConflictStrategy.IGNORE)` guarantees a zeroed row exists, then
`increment(key, mcqAttempts, mcqCorrect, typeAttempts, typeCorrect)` is a raw `UPDATE
… SET mcqAttempts = mcqAttempts + :mcqAttempts, …` that adds to the relevant style's
counters. This two-step is **portable across all supported SQLite versions** — it
deliberately avoids `UPSERT`/`ON CONFLICT DO UPDATE`, which needs SQLite 3.24 / API 30+
(the app's `minSdk` is 26).

**Domain model (`ReadingProgress`)**: `domain/model/ReadingProgress.kt` — mirrors the
row (`itemKey` + the four counters) and computes the read-side figures: `mcqPercent` /
`typePercent` (each null until that style has ≥ 1 attempt) and **`colorPercent`** — the
average of whichever styles have been attempted (both, one, or `null` if neither) — the
single figure that tints a Library cell.

**Repository (`RoomReadingProgressRepository`)**: implements `ReadingProgressRepository`
(`observe(): Flow<List<ReadingProgress>>` mapping rows to domain, and
`record(itemKey, typed, correct)`, which runs the ensure-then-increment pair — `typed`
routes the count into the mcq vs typed tally). `domain/repository/ReadingProgressRepository.kt`
is the interface. The **Reading flashcard** study mode is self-graded and writes nothing
here.

Which track the learner is in is **not** Room state — it persists as a new
SharedPreferences key `app_mode` (`SettingsRepository.getAppMode()`/`setAppMode()`,
`PrefsSettingsRepository.KEY_APP_MODE = "app_mode"`), stored as the `AppMode` enum's
`prefValue` (`"reading"`/`"writing"`); an unrecognised or **absent** value decodes to
`null`, meaning "not chosen yet" → the opening screen. It sits alongside the existing
`PrefsSettingsRepository` keys (`grid_enabled`, `ghost_enabled`, `practice_layout_span`,
`active_script`, `diacritics_included`, `yoon_included`, `extended_included`,
`library_grid_view`, `library_section`, `scoring_mode`, `brush_ink_color`,
`default_sets_seeded`) — plain `SharedPreferences`, not DataStore and not Room.

### Gradle/KSP wiring

In `app/build.gradle.kts`: plugin `alias(libs.plugins.ksp)` plus

```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
```

Versions pinned in `gradle/libs.versions.toml`: Room `2.6.1`, KSP
`2.0.20-1.0.25` (matching Kotlin `2.0.20`), JUnit `4.13.2` (Phase 3,
`testImplementation` only). Room's annotation processing runs through KSP,
not kapt. **`app/build.gradle.kts` now sets `ksp { arg("room.schemaLocation",
"$projectDir/schemas") } }`** (Phase 3) — the mechanism behind
`exportSchema = true`; schema JSON lands in `app/schemas/` and is committed.

### Handwriting-scoring data (v3)

The v3 score columns above are filled by an **advisory** handwriting-quality pass
computed at grade time. Its data types are:

- **`domain/scoring/`** (pure, Android-free, unit-tested like `SrsScheduler`):
  - `ScoringMode` — a persisted user setting (`OFF`/`FORM`/`FIT`/`BOTH`, keyed
    `"off"`/`"form"`/`"fit"`/`"both"`; `fromKey` falls back to `FORM`). The scorer
    always computes both dials; the mode only picks which becomes the headline number.
  - `GeometryScorer` — a stateless `object` that compares the user's drawn stroke
    polylines to the reference glyph's strokes and produces a `GeometryScore`
    (`form`, nullable `fit`, expected/actual stroke counts, coaching `tags`). FORM
    normalizes both ink and reference by their own bounding boxes (shape/order/
    direction/proportion, position-agnostic); FIT alone measures adherence to the
    guide `CellRect`. Supporting value types `Pt` and `CellRect` live in the same file.
  - `CharacterScore` — the final per-attempt result (`headline`, `form`, `fit`,
    `recognitionScore`, `recognitionRan`, `mode`, `tags`). Its `combine(geometry,
    recognition, mode)` blends the geometry pass with an optional recognition verdict:
    recognition acts as a legibility **gate** (caps the headline and adds a "looks
    more like X" tag when the ink doesn't read as the target), not the headline itself.
    `GeometryScore` and `RecognitionResult` (with an `UNAVAILABLE` sentinel) are also
    defined in `CharacterScore.kt`.
  - `CharacterScoreSummary` — a rolled-up per-glyph history (attempts, avg/best FORM,
    avg/best FIT), keyed by the glyph string, for the Library's per-character readout.
- **`data/recognition/MlKitHandwritingRecognizer`** implements the
  `domain/repository/HandwritingRecognizer` interface (`suspend recognize(strokes,
  target): RecognitionResult`, with the `RecognizerStrokePoint` input type). It is
  backed by **ML Kit Digital Ink Recognition** (`com.google.mlkit:digital-ink-recognition`,
  version `19.0.0` — `mlkitDigitalInk` in `gradle/libs.versions.toml`), downloading the
  Japanese model on demand and returning `RecognitionResult.UNAVAILABLE` until it is
  ready (or on any failure), so scoring degrades gracefully to geometry-only.

The resulting `CharacterScore`'s dials persist onto the attempt's `practice_history`
row via the three v3 columns. This is **advisory only**: nothing reads the scores back
into `SrsScheduler` or any gate — grading (Again/Hard/Good/Easy) still drives SRS
exactly as in Phase 3.

## 6. Write and read paths

### Write path

Grading now routes through `PracticeViewModel.gradeCurrentCharacter(grade, session)`
(`app/src/main/java/com/hive/hiragana/presentation/viewmodels/PracticeViewModel.kt`),
which dispatches to one of four phase-specific handlers
(`gradeReview`/`gradePractice`/`gradeVocabulary`/`gradeFree`) — see
docs/ARCHITECTURE.md §4/§8 for the full session-phase routing. What's constant across
all four, and unchanged in spirit since Phase 1:

1. A **double-grade guard** returns early if this `DrawingSession` was already consumed
   by the previous grade (Phase 3; an identity check, `session === lastGradedSession`).
2. **Synchronously snapshot** the attempt: build a `PracticeAttempt` from the grade,
   `session.strokes.size`, `session.durationMillis()`, `System.currentTimeMillis()`, and
   (Phase 3) a `PracticeSource` reflecting the active phase. Synchronous because the
   canvas — and the `DrawingSession` inside `CanvasState` — is cleared by a
   `LaunchedEffect` as soon as `PracticeUiState.canvasClearKey` changes (see
   [DRAWING.md](DRAWING.md)).
3. **Fire-and-forget persist** through one of two private helpers on `PracticeViewModel`,
   both of which persist inside a `viewModelScope.launch { runCatching { … }.onFailure { Log.w(TAG, …) } }`
   (tag `HiraganaSession`; the Phase 1/2 wart of a silently-discarded `runCatching` is
   **fixed as of Phase 3** — failures are logged, though still not retried or surfaced):
   - **`recordAttempt(character, grade, session, source)`** — single-character attempts.
     A single character is **scored** (and thus carries `form_score`/`fit_score`) only
     when it was written in exactly one 1×1 guide cell (`pendingCellRect != null`, plus a
     non-empty reference and user ink); multi-copy 2×2/3×3 layouts leave the cell null
     and record an **unscored** row (the quiz forces `PracticeLayout.SINGLE`, so it always
     scores).
   - **`recordWordAttempt(word, grade, session, source)`** — whole-word (VOCABULARY /
     word FREE) attempts. On a 1×1 sheet (one guide cell per kana unit — `pendingCells`),
     it scores each kana cell-by-cell and persists **one `practice_history` row PER kana**
     (`character` = the unit glyph, `romaji` = the unit's romaji, `form_score`/`fit_score`
     set) **PLUS one word-level row** (`character` = the full word kana string) carrying
     the **average** form/fit across the scored units. Multi-copy 2×2/3×3 (no per-kana
     cells) falls back to a single **unscored** word row.
   - **Grading off ⇒ nothing recorded.** When `scoringMode == ScoringMode.OFF`, BOTH
     helpers early-return **without inserting any row at all** — for FREE practice AND
     the SRS session phases (REVIEWS/NEW_CHARACTER/PRACTICE/VOCABULARY). This is a
     deliberate "pure practice, no score to store" policy; it resolves a prior divergence
     where FREE recorded nothing but sessions still wrote unscored rows. The SRS
     side-effects in step 4 are written **separately by the callers** and are unaffected
     by this early return.

   Consequence of the per-kana word rows: because they pass `observeScoreStats`'
   `form_score IS NOT NULL … GROUP BY character` filter, **word practice now contributes
   to each kana's per-glyph average** (and the word-level row colors the Library word
   card). Word-cell ink is drawn smaller and rougher than a single-character sheet, so it
   can pull a glyph's average down.
4. Phase-specific follow-up, without awaiting the insert: REVIEWS reschedules the card
   via `SrsScheduler.next(...)` and persists it (`LearningRepository.upsertReviewCard`,
   also `runCatching` + `Log.w` on failure); NEW_CHARACTER/PRACTICE's final rep seeds a
   new `ReviewCard`; VOCABULARY records `vocabularyRepository.recordPractice(...)`;
   FREE just advances. These SRS/unlock writes run regardless of `scoringMode` (the
   grading-off policy in step 3 gates only the `practice_history` insert, not scheduling
   or vocab unlock/staleness). Every branch bumps `canvasClearKey`, advancing the UI
   regardless of whether any of these writes succeeded.

The plain "Next" button (FREE only) calls `advanceToNextCharacter()` directly and writes
nothing.

### Read path (exists, unused — but no longer the *only* read path)

`PracticeRepository.observeAttempts(): Flow<List<PracticeAttempt>>` →
`RoomPracticeRepository.observeAttempts()` → `dao.observeAll()` mapped to
domain models, newest first, **still has zero callers** — nothing in the UI or
ViewModel collects it; it remains reserved for a future stats/history screen (§9 of
docs/PHASE3-DESIGN.md names the COMPLETE summary as a "consumer candidate," but the
summary today reads its stats from in-memory session state, not this `Flow`). What
*has* changed is that Phase 3 added real reads elsewhere: `LearningRepository.getReviewCards()`
/ `getCharacterProgress()` and `VocabularyRepository.getProgress()` are read once per
session start (`PracticeViewModel.startSession()`) to feed `LessonGenerator.generate(...)`
— so grading history now *does* influence future behavior, just through the new
progress tables rather than by replaying `practice_history`.

The score columns added in v3 also have a **live reader**: `PracticeRepository.observeScoreSummaries()`
→ `RoomPracticeRepository` (maps `dao.observeScoreStats()`'s `CharacterScoreStats` rows to
`CharacterScoreSummary`) is `collect`ed by `PracticeViewModel` into `PracticeUiState.scoreStats`
for the Library's per-glyph / per-word quality readout. This is the one place `form_score`/
`fit_score` are read back — and only in aggregate (`AVG`/`MAX` per glyph), never into the SRS
scheduler.

## 7. Data integrity caveats

Documented honestly; the Phase 1 items marked **FIXED** below were closed by Phase 3;
the rest (plus two new Phase 3 items) remain traps for future work.

1. **Silent persistence failure — FIXED (Phase 3).** `gradeCurrentCharacter`'s
   `recordAttempt` call (and every other Phase 3 repository write) now logs failures via
   `Log.w(TAG, ..., it)` (tag `HiraganaSession`) instead of discarding the `runCatching`
   result silently. Still no retry and no user-facing feedback — that remains a
   possible future improvement.
2. **Grade fallback fabricates data — target changed, not fixed.**
   `RoomPracticeRepository.toDomain()` now maps unknown `grade` strings to `Grade.GOOD`
   (previously `Grade.OKAY`, which no longer exists as a constant). This is not a bug —
   it is the documented, deliberate legacy-data mapping (docs/PHASE3-DESIGN.md §0 item
   2): any row persisted before Phase 3 stored `"OKAY"`, and `"OKAY"` decoding to `GOOD`
   is exactly the intended migration path for historical grades, not a fallback for
   corruption. The underlying risk (an unrecognised *future* string also silently
   becoming `GOOD`) is unchanged from the original caveat. The same pattern now also
   applies to `source`: an unrecognised stored `PracticeSource` string decodes to
   `FREE`.
3. **Dangling `strokeImage` references.** Every character JSON names a PNG
   (`a.png` … `o.png`) that is not in `assets/hiragana/`. Harmless today
   because nothing loads the field, but any future code that trusts it will
   get a `FileNotFoundException`. Neither Phase 2 nor Phase 3 touched the field (the
   animated demo uses `strokeAnimations` instead); either ship the PNGs or
   drop the field in a later cleanup.
4. **`svgPath` / `strokes` drift risk — largely retired by the KanjiVG
   import.** Both fields are now written together by `import_kanjivg.py`
   (`svgPath` = the strokes space-joined), so the original hand-maintenance
   drift can no longer happen as long as the rule "never hand-edit either
   field, re-import instead" is followed. The residual risk is exactly a
   violation of that rule: the parser only fills the join default when the
   key is *absent*, so a hand edit to `strokes` that forgets `svgPath`
   would still silently diverge. Nothing consumes `svgPath` either way.
5. **`allowBackup="true"` with no backup rules.** `AndroidManifest.xml` opts
   into Auto Backup but defines no `android:fullBackupContent` /
   `android:dataExtractionRules`, so `hiragana-practice.db` is backed up and
   restored wholesale by default. This is exactly the "restored v1 DB onto a future app
   version with migrations" scenario the original caveat warned about, and it is now
   live: a v1 backup restored onto the Phase 3 (v2) app must upgrade cleanly through
   `MIGRATION_1_2`. That path is statically verified against the exported schema but
   has never been exercised with a real backup/restore on a device (see caveat 9).
6. **No migrations, no exported schema — FIXED (Phase 3).** `exportSchema = true`
   (was `false`), with `app/schemas/` committed and a real `MIGRATION_1_2` (§5) —
   the exact fix this caveat asked for. Any *future* schema change still needs its own
   migration; this only retires the v1→v2 gap.
7. **A single malformed asset file fails the whole load.** Parsing is
   all-or-nothing per app start (§3); one bad JSON file blanks the entire
   deck with an error message rather than skipping the bad character. (`vocabulary.json`
   is the one exception added in Phase 3 — see caveat 8.)
8. **A malformed `vocabulary.json` does NOT fail the load (Phase 3, by design).**
   Unlike character parsing, `RoomVocabularyRepository.getWords()` catches any parse
   failure from `HiraganaAssetDataSource.loadVocabulary()` and falls back to an empty
   word list (logged once, then cached) rather than propagating the error — a
   deliberate asymmetry with caveat 7, made because vocabulary is a secondary feature
   layered on top of the core tracing experience, which must keep working either way.
9. **The v1→v2 migration has never run on a device/emulator (Phase 3).** `MIGRATION_1_2`
   is statically verified against the exported v2 schema (`app/schemas/.../2.json`) and
   was exercised in the Phase 3 test suite only insofar as the SQL was checked
   column-for-column against that schema — no actual v1 database has been upgraded at
   runtime. This is the single largest remaining risk in Phase 3's persistence layer.

## 8. HOWTO: adding a new character

Example: add か (ka). Since the KanjiVG import, stroke paths are **never
hand-authored** — new characters come from vendoring the KanjiVG SVG and
running the import pipeline.

1. **Vendor the KanjiVG SVG.** Copy KanjiVG's file for the glyph (named by
   its lowercase-hex codepoint — か U+304B is `0304b.svg`) into
   `tools/kanjivg/`, keeping its license header (KanjiVG is © Ulrich Apel,
   CC BY-SA 3.0, <http://kanjivg.tagaini.net>).

2. **Map it in `tools/import_kanjivg.py`.** Add the asset id → codepoint
   entry to the `CODEPOINTS` dict:

   ```python
   CODEPOINTS = {
       ...
       "ka": "0304b",  # か
   }
   ```

3. **Create a stub `ka.json`** (basename = romaji, by convention) under
   `app/src/main/assets/hiragana/` with the non-geometry fields — the
   import tool writes `strokes`, `svgPath`, and `viewBox` itself:

   ```json
   {
     "character": "か",
     "romaji": "ka",
     "frequency": 6
   }
   ```

   `character`, `romaji`, `frequency`, `strokes` are **mandatory at app
   parse time** — a missing one throws `JSONException` and kills the entire
   character load (caveat 7 above) — but `strokes` arrives via the import,
   so the stub only needs the first three. **Omit `strokeImage`** (unused,
   avoids caveat 3).

4. **Register it in `index.json`.** As of Phase 3 this is a **file manifest
   only** — appending anywhere in the array is enough, since
   `AssetCharacterRepository` sorts the loaded deck by `frequency` ascending
   regardless of listing order. Give the new character the `frequency` value
   matching where it should actually land in study order:

   ```json
   {
     "characters": ["a", "i", "u", "e", "o", "ka"]
   }
   ```

5. **Run the pipeline, in order:**

   ```
   py -3 tools/import_kanjivg.py
   py -3 tools/generate_stroke_animations.py
   ```

   The import writes the KanjiVG geometry (absolute `M`/`L`/`C`/`Q`,
   viewBox 109, coordinates validated inside `[0, 109]`) and deletes any
   stale `strokeAnimations`; the generator recreates the timing data and
   self-validates. Both fail hard rather than shipping bad geometry.

6. **No app code changes needed**, same as before — but two Phase 3
   behaviors to be aware of: the character enters the deck with no
   `ReviewCard`, so `LessonGenerator`'s mastery gate (docs/PHASE3-DESIGN.md
   §4) decides when it is actually introduced in a session (it will not
   appear immediately just because it is in the deck — though the Library
   screen can free-practice it right away); and if any shipped
   `vocabulary.json` word happens to use か, it becomes unlockable once か
   is learned (`repetitions >= 1`) — no vocabulary code changes needed
   either. Rebuild (`assembleDebug`) so the assets are repackaged,
   reinstall, done. Note the repository cache (§3) is per-process, so a
   hot-reload without process death will not pick up asset changes.

7. Sanity checklist: glyph renders as ghost + guide panel, diagram shows the
   right number of badges in the right places, "Stroke order · N" matches
   the canonical count, grading writes a row (verify via
   `adb shell "run-as com.hive.hiragana sqlite3 databases/hiragana-practice.db 'SELECT * FROM practice_history;'"`
   — note the table rename — or Android Studio's App Inspection, since no UI
   reads `practice_history` back yet). To see it show up in a session at all,
   check `review_cards`/`character_progress` too, since those — not
   `index.json`'s position — govern when a new character is introduced.
