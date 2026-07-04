# Phase 2 Design — Animated Stroke-Order Demonstrations

Status: **IMPLEMENTED (2026-07-03) — build-verified, adversarially reviewed and review-fixed 2026-07-04.**
Device/emulator verification is still pending; §7 records the as-built deviations.
This document was the agreed contract between parallel implementation workstreams.
If you are picking this project up cold: read `docs/HISTORY.md` first (what Phase 2
is and why), then this file (how it is being built), then check `.aihive/board.md`
for how far implementation got.

Phase 2 goal (from the original prompt, verbatim intent): replace the static
stroke-order diagram with an animated demonstration showing stroke order, stroke
direction, writing rhythm/speed, and pen lifts — with play/replay controls,
auto-play, playback-speed multiplier, auto-repeat, and pause/resume. Architecture
must not be redesigned; this is an additive extension to the Phase 1 codebase
(see `docs/ARCHITECTURE.md`).

---

## 1. Data model

### 1.1 JSON schema extension (`assets/hiragana/*.json`)

Each character file gains one new top-level field, `strokeAnimations`, generated
offline by the tool in §4 from the existing `strokes[]` SVG paths:

```json
"strokeAnimations": [
  {
    "order": 1,
    "points": [
      { "x": 22.0, "y": 33.0, "time": 0 },
      { "x": 24.1, "y": 32.7, "time": 14 }
    ]
  }
]
```

Rules:

- `order` is 1-based and matches the index+1 of the corresponding entry in `strokes[]`.
- `points[i].time` is **milliseconds since the start of that stroke** (each stroke
  has its own clock starting at 0), strictly increasing, first point always `time: 0`.
- **Coordinates are in the character's `viewBox` space (0–100), NOT normalized 0–1.**
  This is a deliberate divergence from the Phase 2 prompt's example (which showed
  `0.23`-style values) so that `strokeAnimations`, `strokes[]` SVG paths, and the
  existing `StrokeOrderDiagram` renderer all share one coordinate space.
- All other fields are unchanged; `strokes[]` remains the geometry source of truth.
  `strokeAnimations` is derived data — never hand-edit it; re-run the generator.

### 1.2 Domain models (`domain/model/StrokeDefinition.kt`) — exact shapes

```kotlin
package com.hive.hiragana.domain.model

data class StrokeFrame(
    val x: Float,
    val y: Float,
    val time: Long,
)

data class StrokeDefinition(
    val order: Int,
    val points: List<StrokeFrame>,
)
```

`HiraganaCharacter` gains one field (default keeps old call sites compiling):

```kotlin
val strokeAnimations: List<StrokeDefinition> = emptyList(),
```

### 1.3 Parser (`data/assets/HiraganaAssetDataSource.kt`)

`strokeAnimations` is **optional** in the JSON: absent/malformed → `emptyList()`
(backward compatible; the UI falls back to the static diagram, §5). Entries are
sorted by `order` after parsing; individual malformed strokes fail the whole list
(all-or-nothing per character) so a half-animated character never ships.

---

## 2. Animation engine — public API (exact contract)

New package `drawing/animation/`, file `StrokeAnimationEngine.kt`. Same design
philosophy as `CanvasState` (see `docs/DRAWING.md`): a `@Stable` snapshot-state
holder, UI-thread only, no Android framework deps beyond Compose runtime/ui.

```kotlin
package com.hive.hiragana.drawing.animation

enum class PlaybackState { Idle, Playing, Paused, Finished }

@Stable
class StrokeAnimationEngine(
    strokes: List<StrokeDefinition>,
    interStrokePauseMs: Long = 350L,
) {
    val strokeCount: Int

    // --- Snapshot-state outputs (safe to read from a draw scope) ---
    val playbackState: PlaybackState        // starts Idle
    val speedMultiplier: Float              // default 1f
    val autoRepeat: Boolean                 // default false
    val currentStrokeIndex: Int             // -1 while Idle; index of animating/last stroke otherwise
    val completedPaths: List<Path>          // viewBox-space paths of fully drawn strokes
    val activePath: Path?                   // partial path of the stroke being animated
    val penPosition: Offset?                // interpolated pen tip (viewBox space); null when pen is lifted
    val progress: Float                     // 0f..1f across the whole character (incl. pauses)

    // --- Non-suspend controls (call from onClick; UI thread) ---
    fun play()                              // Idle/Finished: restart from stroke 1; Paused: resume in place
    fun pause()
    fun replay()                            // always restart from stroke 1 and play
    fun setSpeed(multiplier: Float)         // clamped to 0.25f..4f; takes effect immediately, mid-stroke
    fun setAutoRepeat(enabled: Boolean)
    fun reset()                             // back to Idle; all outputs cleared

    // --- Single driver coroutine; call exactly once per instance ---
    suspend fun run()                       // loops until the calling scope is cancelled
}
```

Usage pattern (the only supported one):

```kotlin
val engine = remember(character.character) { StrokeAnimationEngine(character.strokeAnimations) }
LaunchedEffect(engine) { engine.run() }    // driver
LaunchedEffect(engine) { if (autoPlay) engine.play() }
```

### 2.1 Semantics (normative)

- **Timeline**: stroke 1 animates → pen lifts (`penPosition = null`) → inter-stroke
  pause → stroke 2 → … → last stroke → `Finished` (or wrap to stroke 1 when
  `autoRepeat`). Stroke order always ascending `order`. This preserves the original
  prompt's `stroke → pause → stroke` pipeline.
- **Rhythm preservation**: stroke *i*'s animated duration at 1× equals
  `points.last().time` — i.e. the recorded writing rhythm, not a fixed tween. Wall
  durations (stroke and pause) divide by `speedMultiplier`.
- **Partial path**: at engine time *t* within a stroke, `activePath` contains all
  frames with `time <= t` plus a linear interpolation toward the next frame;
  `penPosition` is that interpolated tip. Frame-to-path conversion uses the same
  midpoint quadratic-bézier smoothing idea as `StrokeRenderer.buildSmoothPath`
  (single-frame strokes render as a dot via the same degenerate-segment trick).
- **Pause/resume**: `pause()` freezes mid-stroke (partial path stays on screen);
  `play()` from `Paused` continues from the frozen position, not from the stroke start.
- **Speed change mid-play** applies to the remainder of the current stroke without
  visual jumps.
- **Implementation constraint** (from the Phase 2 prompt): the clock is driven by
  Compose `Animatable` (LinearEasing tween per stroke) inside the `run()` loop from
  a `LaunchedEffect`. Controls only mutate desired-state; `run()` owns all suspend work.
- **Edge cases**: empty `strokes` → `play()` is a no-op that goes straight to
  `Finished`; a stroke whose duration is 0 (single frame) gets a minimum 1-frame
  wall duration; engine never throws for well-formed-but-degenerate data.

---

## 3. UI — `AnimatedStrokePreview` (exact contract)

New file `presentation/components/AnimatedStrokePreview.kt`:

```kotlin
@Composable
fun AnimatedStrokePreview(
    character: HiraganaCharacter,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
)
```

- **Fallback**: if `character.strokeAnimations` is empty, render the existing
  static `StrokeOrderDiagram` (which is kept, not deleted) — same modifier,
  squared (as-built change, see §7: callers now pass a height-only constraint).
- **Preview canvas** (square, scaled from `character.viewBox` exactly like
  `StrokeOrderDiagram` does, stroke width divided by scale):
  1. all strokes as a faint guide (existing `strokes[]` via `PathParser`,
     `DiagramStroke` at low alpha),
  2. `completedPaths` + `activePath` in full `DiagramStroke` color,
  3. a small pen-tip dot at `penPosition` in the primary color (hidden when null —
     this is what makes pen lifts visible).
- **Controls row** under the canvas, compact enough for the 25% top panel:
  play/pause toggle, replay, speed cycle button (0.5× → 1× → 2×, shows current
  value as text), auto-repeat toggle. Use core material icons / lightweight
  custom glyphs — **do not add the material-icons-extended dependency**.
- **Auto-play**: when the character changes, a fresh engine is remembered
  (keyed on `character.character`) and `play()` fires if `autoPlay`.
- `CharacterGuidePanel.kt` swaps `StrokeOrderDiagram` → `AnimatedStrokePreview`
  in its right-hand column; everything else in the panel stays.

The user must be able to trace on the main canvas while/after the preview plays —
the preview never captures input outside its control buttons.

---

## 4. Timing generator (offline tool)

`tools/generate_stroke_animations.py` (Python 3; if unavailable on the machine,
an equivalent Node script is acceptable — keep the same name stem and document it).

- Reads each character JSON listed in `assets/hiragana/index.json`, parses the
  `strokes[]` SVG paths (`M`/`C` are what the current five files use; support
  `L`/`Q` too, hard-error on anything else), flattens béziers, and resamples by
  arc length to ~2 viewBox-unit spacing (min 8, max 48 points per stroke).
- Assigns times with a natural-writing model: base pen speed **160 viewBox-units/s**,
  minimum stroke duration **180 ms**, gentle ease-in/ease-out velocity profile,
  times rounded to ms, strictly increasing, first = 0.
- Writes `strokeAnimations` back into the JSON (idempotent — regenerates from
  `strokes[]`, preserves all other fields, 2-space indent, UTF-8 no BOM).
- Self-validates after writing: stroke counts match `strokes[]`, times strictly
  increasing from 0, coordinates within `[0, viewBox]`; prints a per-character
  summary table. Non-zero exit on any violation.

---

## 5. Workstream ownership (parallel implementation — do not cross)

| Workstream | Owns (exclusively) |
|---|---|
| **B0 contract** (done first, by orchestrator) | this file, `domain/model/StrokeDefinition.kt` |
| **D — data** | `tools/*`, `assets/hiragana/*.json`, `domain/model/HiraganaCharacter.kt`, `data/assets/HiraganaAssetDataSource.kt` |
| **E — engine** | `drawing/animation/*` |
| **U — ui** | `presentation/components/AnimatedStrokePreview.kt`, `presentation/components/CharacterGuidePanel.kt` |
| **Integration** (after D/E/U) | build fixes anywhere; nobody else edits after handoff |

No workstream edits `PracticeViewModel`, `PracticeScreen`, `CanvasState`, or the
tracing pipeline — Phase 2 is confined to the guide area + data layer.

## 6. Acceptance criteria (verified at integration)

1. `assembleDebug` succeeds with the provisioned toolchain (see `CLAUDE.md`).
2. All five JSONs contain valid `strokeAnimations` passing the generator's validator.
3. Preview auto-plays on launch and on every character change; strokes appear in
   order with visible pen lifts and per-stroke rhythm.
4. Play/pause/resume, replay, 0.5×/1×/2× speed, and auto-repeat all work; pausing
   mid-stroke freezes the partial stroke; resume continues in place.
5. Static diagram still renders for a character without `strokeAnimations`.
6. Tracing canvas behavior is completely unchanged.

Status 2026-07-04: 1–2 and 5–6 verified (build + validator + adversarial code
review); 3–4 implemented and code-reviewed, but awaiting a device/emulator run.

## 7. As-built deviations (post-review fixes, 2026-07-04)

Two adversarial review agents (engine; UI/data) audited the implementation:
**0 P0 defects, verdict ship-for-demo**. Their findings changed the contract in
two places:

1. **Layout (amends §3).** `CharacterGuidePanel` no longer applies
   `aspectRatio(1f)` to the whole preview — it passes a height-only constraint,
   and the square lives on the preview canvas inside the component. Reason: with
   the whole component squared, the controls row (4 × 32 dp = 128 dp) was
   clipped inside the height-derived square, crushing the speed and auto-repeat
   buttons to zero width on short screens (≲ 360×640 dp). The fallback diagram
   squares itself (`modifier.aspectRatio(1f)`) to keep its Phase 1 footprint.
2. **Parser consistency rule (amends §1.3).** Beyond schema validity,
   `strokeAnimations` is voided unless it is *consistent* with `strokes[]`:
   exactly one entry per stroke, orders exactly 1..N, every entry non-empty.
   Schema-valid output from a buggy future generator run can therefore never
   demonstrate a wrong stroke count. Voiding now logs a `Log.w` (tag
   `HiraganaAssets`) instead of failing silently.

Accepted quirks noted by review (not fixed, by design): `autoRepeat` is read
once per cycle, so unchecking it during the end-of-cycle hold plays one more
full cycle; `MIN_STROKE_DURATION_MS` (16 ms) floors the 1× timeline rather than
the wall clock (unreachable with generator data, which floors strokes at 180 ms).
