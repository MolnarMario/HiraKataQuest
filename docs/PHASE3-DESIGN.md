# Phase 3 Design — Smart Learning System

> **⚠️ REMOVED 2026-07-05 — historical document.** The guided study session
> described in this file **no longer exists**: the SM-2 spaced-repetition
> scheduler (`SrsScheduler`), the lesson generator (`LessonGenerator`), the
> `StudySession` model, and the `PracticeViewModel` session state machine were
> all deleted on 2026-07-05. The app is now **free practice + the "Quiz me!"
> quiz** only, with **built-in default practice sets**
> (`domain/model/DefaultPracticeSets.kt`) providing structured learning instead.
> This document is retained for historical context on how Phase 3 was designed
> and built. The Room SRS tables (`review_cards`, `character_progress`,
> `vocabulary_progress`) remain in the schema as **dormant, unused** data
> (removing them would require a destructive migration). See `docs/HISTORY.md`
> (timeline row 27) for the removal.

Status: **CONTRACT — proposed (2026-07-04), implementation not started.**
Written before any Phase 3 code, following the pattern that worked for Phase 2:
fixed contract → parallel workstreams → integration build → adversarial review →
docs updated *in the same phase* (lesson learned from the Phase 2 doc drift).

Phase 3 goal (from the owner's prompt): a learning engine on top of the existing
drawing engine — frequency-based character ordering, SM-2-style spaced repetition,
vocabulary unlocking, and personalized session generation. The drawing/animation
pipeline (Phases 1–2) is **not touched**.

Reading order for a cold pickup: `docs/HISTORY.md` → this file → `.aihive/board.md`.

---

## 0. Decisions that resolve prompt-vs-codebase conflicts

The Phase 3 prompt sketches shapes that collide with shipped code in five places.
Resolutions (each carried through the sections below):

| # | Conflict | Decision |
|---|---|---|
| 1 | Prompt's `HiraganaCharacter(id, character, romaji, frequencyRank)` vs the shipped 8-field model | **Extend, don't replace.** Add `id: String` (the asset basename, e.g. `"a"` — already unique and stable). The existing `frequency` field *is* `frequencyRank` (docs/DATA.md has always said it was reserved for SRS ordering); keep the field name to avoid touching every call site. |
| 2 | Prompt's grades Again/Hard/Good/Easy vs shipped `Grade { EASY, OKAY, HARD }` | **Extend the enum to `{ AGAIN, HARD, GOOD, EASY }`.** `OKAY` is renamed `GOOD`; `RoomPracticeRepository.toDomain` maps legacy stored `"OKAY"` strings → `GOOD` explicitly (its unknown-string fallback also becomes `GOOD`). |
| 3 | Prompt's `@Entity ReviewCard` (entity = domain object) vs the project's entity/domain split | **Keep the split** (`data/local/ReviewCardEntity` ↔ `domain/model/ReviewCard`), same as `PracticeAttemptEntity`/`PracticeAttempt`. Field names and the `review_cards` schema match the prompt exactly. |
| 4 | Prompt's vocab examples (あした, した) need し and た — not in the deck | **Phase 3 core ships vocabulary composable from あいうえお only** (real words exist: あい love, いえ house, うえ above, あお blue, いう to say, あう to meet, いいえ no, うお fish, おい hey/nephew). Extending the syllabary is content work (SVG stroke authoring + generator run), split out as an optional follow-up workstream (§10) — the unlock rule makes new words light up automatically as characters ship. |
| 5 | Prompt's `practice_history` table vs shipped `practice_attempts` | **Rename in the v1→v2 migration** (`ALTER TABLE practice_attempts RENAME TO practice_history`) and add a `source` column. Cheap, satisfies the prompt, keeps existing rows. |

Architecture rules that still bind (CLAUDE.md): manual DI only; deck/session state
in the ViewModel, transient ink in `CanvasState`; `org.json` parsing on
`Dispatchers.IO`; dependencies via the version catalog; Phase 1/2 tracing and
animation pipelines unchanged.

---

## 1. Domain model (exact shapes)

```kotlin
// domain/model/Grade.kt — extended
enum class Grade(val label: String) { AGAIN("Again"), HARD("Hard"), GOOD("Good"), EASY("Easy") }

// domain/model/HiraganaCharacter.kt — one added field
val id: String,          // asset basename from index.json ("a", "i", …); stable PK for progress tables

// domain/model/ReviewCard.kt — new (schema mirrors the prompt)
data class ReviewCard(
    val characterId: String,
    val repetitions: Int,        // successful reviews in a row (Again resets to 0)
    val intervalDays: Int,
    val easeFactor: Float,       // init 1.7; the "Good" multiplier
    val nextReviewDate: Long,    // epoch millis at local start-of-day it becomes due
)

// domain/model/VocabularyWord.kt — new (shape per prompt)
data class VocabularyWord(
    val kana: String,
    val meaning: String,
    val requiredCharacters: Set<String>,   // DERIVED by the parser from kana's characters
)

// domain/model/StudySession.kt — new (LessonGenerator output)
data class StudySession(
    val reviews: List<HiraganaCharacter>,      // due cards resolved to characters, most-overdue first
    val newCharacter: HiraganaCharacter?,      // null when the mastery gate is closed or deck exhausted
    val practiceRepetitions: Int,              // required traces of newCharacter (3)
    val vocabulary: List<VocabularyWord>,      // newly unlocked first, then reinforcement picks
)
```

Character ordering: `AssetCharacterRepository` sorts the loaded list by
`frequency` ascending (NOT `index.json` order — today the two coincide, but the
ordering source is now explicitly the rank, per the prompt). `index.json` remains
purely a file manifest.

---

## 2. SRS scheduler (`domain/learning/SrsScheduler.kt`)

A pure object — no Android, no clock reads. Signature:

```kotlin
object SrsScheduler {
    fun next(card: ReviewCard, grade: Grade, todayStartMillis: Long): ReviewCard
    fun newCard(characterId: String, todayStartMillis: Long): ReviewCard   // repetitions 0, interval 0, ease 1.7, due today
}
```

Transitions (simplified SM-2, per the prompt; `interval'` in whole days):

| Grade | repetitions' | interval' | easeFactor' | due |
|---|---|---|---|---|
| AGAIN | 0 | 0 | unchanged | today (requeued within the session) |
| HARD | unchanged | **1** (flat, per prompt) | −0.15, floor 1.3 | today + 1d |
| GOOD | +1 | `max(1, ceil(interval × easeFactor))` | unchanged | today + interval' |
| EASY | +1 | `max(2, ceil(interval × 2.5))` | +0.15, cap 2.5 | today + interval' |

Interpretation notes (normative):

- The prompt's "Good → multiply by 1.7" is implemented as *multiply by
  `easeFactor`, initialized to 1.7* — identical behavior until Hard/Easy adjust
  the ease, which gives the stored `easeFactor` field a real job instead of being
  dead weight. Growth at default ease: 0 → 1 → 2 → 4 → 7 → 12 → 21 → 36 days.
- "Hard → 1 day" is taken literally, even for a mature card (a 30-day card graded
  Hard collapses to 1 day). Accepted as the simplified spec; the alternative
  (`interval × 1.2`) is noted for a future tuning pass, not built now.
- `nextReviewDate` is epoch millis of **local start-of-day**; a card is due when
  `nextReviewDate <= now`. All date math goes through an injected
  `Clock`/`todayStartMillis` parameter so the scheduler and generator are unit-testable
  and DST/timezone behavior is centralized in one helper.
- AGAIN during a session requeues the card at the back of today's review queue;
  the session step isn't complete until every review card has left the queue with
  a non-AGAIN grade (this is the "repeat until recalled" loop).

---

## 3. Vocabulary system

**Asset**: new `app/src/main/assets/hiragana/vocabulary.json`, parsed by
`HiraganaAssetDataSource` alongside the characters (same `org.json` +
`Dispatchers.IO` convention):

```json
{ "words": [ { "kana": "あい", "meaning": "love" }, { "kana": "いえ", "meaning": "house" }, … ] }
```

`requiredCharacters` is **not stored in JSON** — the parser derives it as the set
of distinct characters in `kana`. One source of truth, no drift possible.
Starter word list (all real words, all composable from あいうえお):
あい love · いえ house · うえ up/above · あお blue · いう to say · あう to meet ·
いいえ no · おい hey!/nephew · うお fish.

**Unlock rule** (exactly the prompt's): a word unlocks when
`learnedCharacters.containsAll(word.requiredCharacters)`.
**Learned** is defined as: the character has a `ReviewCard` with
`repetitions >= 1` (introduced *and* recalled successfully at least once —
introduction alone is not enough).

Unlocking is persisted (`vocabulary_progress` row created with `unlockedAt`);
the unlock check runs during session generation, so newly learned characters
surface their words at the next session start.

---

## 4. Lesson generator (`domain/learning/LessonGenerator.kt`)

Pure class per the prompt; all inputs passed in, deterministic, no I/O:

```kotlin
class LessonGenerator(
    private val maxReviewsPerSession: Int = 10,
    private val newCharBacklogLimit: Int = 5,
    private val practiceRepetitions: Int = 3,
    private val vocabReinforcementCount: Int = 2,
) {
    fun generate(
        allCharacters: List<HiraganaCharacter>,     // already frequency-sorted
        cards: List<ReviewCard>,
        allWords: List<VocabularyWord>,
        unlockedWordKana: Set<String>,              // from vocabulary_progress
        wordLastPracticedAt: Map<String, Long>,
        todayStartMillis: Long,
    ): StudySession
}
```

Rules (normative):

1. **Reviews**: cards with `nextReviewDate < todayStartMillis + 1 day` —
   strictly less than, amended 2026-07-04 (originally written `<=`, see §11):
   a card stamped exactly at tomorrow's start-of-day is NOT due today,
   otherwise a card rescheduled to "+1 day" mid-session would reappear as due
   in a same-day regenerated session (process death → relaunch), an
   unfinishable loop. Sorted most-overdue first (tie-broken by characterId),
   capped at `maxReviewsPerSession` (overflow stays due — it simply reappears
   tomorrow).
2. **New character** — the "prevent advancing before mastery" gate. Pick the
   lowest-`frequency` character with no `ReviewCard`, but only if BOTH:
   (a) the due backlog before this session is `<= newCharBacklogLimit`, and
   (b) every previously introduced character has `repetitions >= 1`
   (i.e., the last new character has been successfully recalled at least once).
   Otherwise `newCharacter = null` and the session is review/vocab-only.
3. **Practice repetitions**: `practiceRepetitions` (3) traces of the new
   character — the first with the Phase 2 animated demonstration fresh on screen,
   the rest as reinforcement. The final trace's grade seeds the character's
   `ReviewCard` via `SrsScheduler.newCard` + `next`.
4. **Vocabulary**: every locked word whose requirements are now met → unlocked
   (persist + include, newly unlocked first); plus up to `vocabReinforcementCount`
   already-unlocked words, least-recently practiced first.

**Crash-safe resumability for free**: the generator reads only DB state, and every
completed step writes DB state (rescheduled card / created card / unlock row /
practice_history row). Process death mid-session → regenerating yields exactly the
remaining work. No session table needed; nothing to persist about the session itself.

---

## 5. Persistence — Room v1 → v2

Migration (additive + one rename; a real `Migration(1, 2)`, no destructive fallback):

- `ALTER TABLE practice_attempts RENAME TO practice_history`; add column
  `source TEXT NOT NULL DEFAULT 'FREE'` (values `REVIEW` / `NEW` / `VOCAB` / `FREE`).
- `CREATE TABLE review_cards` — `characterId TEXT PK`, `repetitions INTEGER`,
  `intervalDays INTEGER`, `easeFactor REAL`, `nextReviewDate INTEGER` (per prompt).
- `CREATE TABLE character_progress` — `characterId TEXT PK`,
  `introducedAt INTEGER`, `totalTraces INTEGER`.
- `CREATE TABLE vocabulary_progress` — `kana TEXT PK`, `unlockedAt INTEGER`,
  `timesPracticed INTEGER`, `lastPracticedAt INTEGER NULL`.

Also in this workstream, closing two long-documented warts:

- **`exportSchema = true`** and commit the schema directory (docs/DATA.md caveat 6
  has demanded this since Phase 1; a migration without schema history is how apps
  die).
- The migration is exercised by the existing `allowBackup` restore path
  (docs/DATA.md caveat 5) — a v1 backup restored onto v2 must upgrade cleanly.

New DAOs (`ReviewCardDao`, `CharacterProgressDao`, `VocabularyProgressDao`) follow
`PracticeAttemptDao`'s style (suspend funs + `Flow` where observed). New domain
interfaces `LearningRepository` (cards + progress) and `VocabularyRepository`,
implemented in `data/repository/`, wired as two more lazies in `AppContainer`.
`recordAttempt`'s silent-failure wart is NOT inherited: new repository writes log
failures via the same `Log.w(TAG)` pattern introduced in the Phase 2 parser fix.

---

## 6. UI — session flow on the single screen

No navigation library; the session is a state machine inside `PracticeViewModel`
(deck/session state belongs in the ViewModel per the state-split rule; `CanvasState`
stays untouched):

```
SessionPhase: REVIEWS → NEW_CHARACTER → PRACTICE → VOCABULARY → COMPLETE
```

- **REVIEWS**: guide panel shows the due character (animated preview already works
  per character); the action bar's four grade buttons drive `SrsScheduler.next`.
  AGAIN requeues; the phase ends when the queue is empty.
- **NEW_CHARACTER**: the Phase 2 animation IS the introduction — auto-play the
  demonstration, then `practiceRepetitions` traced repetitions (PRACTICE), the last
  grade seeding the new `ReviewCard`.
- **VOCABULARY**: guide panel shows the word (kana + meaning + "new word!" badge on
  fresh unlocks); the user traces each kana of the word in sequence — the entire
  existing tracing pipeline is reused per character, ghost glyph included. Grades
  record to `practice_history(source=VOCAB)` but do NOT reschedule character cards.
- **COMPLETE**: summary (reviews done, character learned, words unlocked) + a
  "keep practicing" escape hatch into the old free-trace loop over learned
  characters (`source=FREE`).

`PracticeUiState` gains: `phase`, `sessionProgress` (e.g. "Review 3/5"),
`currentWord: VocabularyWord?`, `wordCharIndex`, and completion stats.
`PracticeActionBar`: Clear · Again · Hard · Good · Easy (Next is removed during
sessions — the generator decides what's next; that's the point of Phase 3).
One new color in `Color.kt` for the Again button.

---

## 7. Tests — first in the project (ends the "zero tests" era)

Phase 3's core is pure Kotlin, which is exactly what unit tests are for. Add
`junit` via the version catalog + `testImplementation`; new `app/src/test/`:

- `SrsSchedulerTest` — every grade transition, ease floor/cap, interval floors,
  Again-reset, due-date math around start-of-day.
- `LessonGeneratorTest` — mastery gate (both clauses), review cap + overdue
  ordering, deck-exhausted, backlog-blocked, requeue semantics.
- `VocabularyUnlockTest` — derivation of `requiredCharacters`, unlock rule,
  learned-definition boundary (repetitions 0 vs 1), reinforcement pick order.

Run: same toolchain as CLAUDE.md, task `testDebugUnitTest`. These tests are the
acceptance evidence for success criteria 1–4 — the emulator gap that haunts
Phase 2 does not apply to pure logic.

---

## 8. Workstreams (parallel, ownership exclusive — Phase 2 pattern)

| Workstream | Owns exclusively |
|---|---|
| **B0 contract** (orchestrator, first) | this file; `domain/model/{Grade,ReviewCard,VocabularyWord,StudySession}.kt`; `id` on `HiraganaCharacter` |
| **S — scheduler** | `domain/learning/SrsScheduler.kt` + `SrsSchedulerTest` |
| **L — lesson engine** | `domain/learning/LessonGenerator.kt` + `LessonGeneratorTest` + `VocabularyUnlockTest` |
| **D — data** | Room v2 (entities, DAOs, migration, schema export), repositories + interfaces, `vocabulary.json` + parser changes, `AppContainer`, legacy-grade mapping in `RoomPracticeRepository` |
| **U — ui** | `PracticeViewModel` session machine + `PracticeUiState`, `PracticeActionBar`, `CharacterGuidePanel` session/word header, completion state, `Color.kt` |
| **Integration** (after S/L/D/U) | build + unit-test run, wiring fixes anywhere, adversarial review, doc updates (CLAUDE.md, README, ARCHITECTURE, DATA, HISTORY — **in this phase, not after**) |

S and L depend only on B0 — they can start immediately and are fully testable
without D or U. Nobody touches `drawing/`, `TracingCanvas`, `CanvasState`,
`StrokeAnimationEngine`, or `AnimatedStrokePreview`.

## 9. Acceptance criteria (verified at integration)

1. `assembleDebug` succeeds AND `testDebugUnitTest` passes (all new tests green).
2. Character order everywhere derives from `frequency` rank, not `index.json` position.
3. Scheduler: each grade produces exactly the §2 table's card state (test-proven).
4. Mastery gate: with an un-recalled new character or a backlog > limit, the next
   session contains no new character (test-proven).
5. Unlock: learning あ+い unlocks あい exactly once, persistently; words needing
   unlearned characters stay locked (test-proven).
6. Session flow runs REVIEWS → NEW_CHARACTER → PRACTICE → VOCABULARY → COMPLETE
   on-device with grading, requeue-on-Again, and the summary screen.
7. A v1 database upgrades to v2 with `practice_attempts` rows surviving (as
   `practice_history` with `source='FREE'`).
8. Phases 1–2 behavior unchanged: tracing input path, render order, animated
   preview all as documented.

## 10. Out of scope (explicitly)

- **More characters** (し/た etc. to enable あした/した from the prompt's examples):
  content workstream — hand-authored stroke SVGs + `generate_stroke_animations.py`
  run + `frequency` ranks. The unlock system needs zero code changes to absorb them.
- Stroke-order *correctness* validation of traces; SRS tuning (Hard × 1.2, fuzz,
  leech detection); notifications/reminders; stats/history screen (though
  `observeAttempts()` finally gets its consumer candidate in the COMPLETE summary —
  optional, not required).

## 11. As-built amendments (post-review fixes, 2026-07-04)

Implemented 2026-07-04; integration build + first-ever test run green on the
first try (39 tests, now 45 after review fixes). Two adversarial review agents
(logic/data+migration; session UI) audited the phase: **0 P0 defects, both
verdicts ship**. Their findings amended this contract in the following places:

1. **Due window (§4 rule 1)**: `<=` → `<` (see the amended rule text). The
   implementation had the correct strict inequality all along; the contract
   text was the bug.
2. **Orphaned cards (§4)**: cards whose `characterId` no longer matches any
   deck character are ignored *entirely* by the generator — they must not
   consume review slots, inflate the gate's backlog count, or hold gate
   clause (b) closed forever (they can never be reviewed, so they would
   otherwise poison the gate permanently after any future character rename).
3. **Fresh-unlock plumbing (§6)**: `PracticeUiState` carries
   `newlyUnlockedKana`; the COMPLETE summary's "Words unlocked" lists only
   fresh unlocks (reinforcement picks were previously mislabeled as unlocked),
   and the guide panel shows the §6-mandated "New word!" badge on them.
4. **Double-grade guard (§6)**: `gradeCurrentCharacter` ignores a grade whose
   `DrawingSession` was already consumed (identity check) — two simultaneous
   taps on adjacent grade buttons could otherwise grade two queue entries
   from one trace, rescheduling an untraced character.
5. **"Learned" consistency (§3/§6)**: the FREE deck filters on
   `repetitions >= 1` (not merely card-exists); `newCharacterLearned` in the
   summary is withheld when the final practice rep was graded AGAIN (the card
   is seeded with repetitions 0 — introduced, not learned); the COMPLETE
   screen hides "Keep practicing" when nothing is learned yet.
6. **Derivation single-source (§3)**: `requiredCharacters` derivation moved
   to `VocabularyWord.fromKana` (domain, unit-tested — the parser calls it),
   and `RoomVocabularyRepository.getWords()` caches parsed words behind a
   Mutex like `AssetCharacterRepository`.

Accepted quirks noted by review (by design, not fixed): a Hard grade collapses
even a mature card to 1 day (§2's literal rule); practice repetitions restart
from rep 1 after process death mid-introduction; `unlockedAt` is stamped with
start-of-day; reviewers' migration caveat stands — the v1→v2 migration is
statically verified against Room's exported schema but has not run on a
device/emulator.
