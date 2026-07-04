# Drawing Engine

Deep-dive into the tracing subsystem of Hiragana Trace: how raw touch/stylus
input becomes smoothed, pressure- and speed-weighted brush ink on the
practice canvas. This is
the most intricate part of the app; this document is written so the subsystem
could be reimplemented from it alone.

Scope note: sections 1–6 describe the **tracing pipeline** (Phase 1, plus the
post-Phase-3 **brush ink rework** — see section 5). The Phase 2
animated stroke-order demonstration is **implemented** and lives in the same
`drawing` subsystem (`drawing/animation/`); section 7 covers both guide
components — the animated preview and the static diagram it falls back to.
The normative Phase 2 contract (data schema, engine API, UI behavior) is
[docs/PHASE2-DESIGN.md](PHASE2-DESIGN.md). **Phase 3 (Smart Learning System —
SRS scheduling, session generation, vocabulary unlocking) did not touch this
subsystem** — it only changed *when* and *why* a character reaches
this canvas (see [docs/PHASE3-DESIGN.md](PHASE3-DESIGN.md) and
[docs/ARCHITECTURE.md](ARCHITECTURE.md) §8), never how tracing itself works.
The subsequent **brush rework did**: `StrokeRenderer` was rewritten (uniform
polyline stroke → filled variable-width ribbon), and `CanvasState` /
`TracingCanvas` changed with it; input capture (`StrokeCapture`), the
grid/ghost renderers, and the animation engine are unchanged.
See [docs/HISTORY.md](HISTORY.md) for the phase history and
[docs/ARCHITECTURE.md](ARCHITECTURE.md) for how this subsystem plugs into
the rest of the app.

## Source map

| File | Role |
|---|---|
| `app/src/main/java/com/hive/hiragana/drawing/stroke/StrokePoint.kt` | One sampled input point |
| `app/src/main/java/com/hive/hiragana/drawing/stroke/UserStroke.kt` | One pen-down→pen-up stroke + `averagePressure()` |
| `app/src/main/java/com/hive/hiragana/drawing/stroke/DrawingSession.kt` | All strokes for the current character + `durationMillis()` |
| `app/src/main/java/com/hive/hiragana/drawing/stroke/StrokeCapture.kt` | MotionEvent → CanvasState translation, palm rejection |
| `app/src/main/java/com/hive/hiragana/drawing/canvas/CanvasState.kt` | `@Stable` snapshot-state holder; stroke state machine; `brushBaseWidthPx` |
| `app/src/main/java/com/hive/hiragana/drawing/canvas/StrokeRenderer.kt` | `buildBrushPath`: filled variable-width brush ribbon (pressure × speed widths, smoothing, end tapers) |
| `app/src/main/java/com/hive/hiragana/drawing/canvas/CanvasRenderers.kt` | `drawPracticeGrid`, `drawGhostCharacter` DrawScope extensions |
| `app/src/main/java/com/hive/hiragana/presentation/components/TracingCanvas.kt` | The composable: input filter + four-layer draw pass |
| `app/src/main/java/com/hive/hiragana/presentation/components/StrokeOrderDiagram.kt` | Static SVG-based stroke-order guide with numbered badges (Phase 2 fallback) |
| `app/src/main/java/com/hive/hiragana/drawing/animation/StrokeAnimationEngine.kt` | Phase 2 playback engine (`@Stable` snapshot state, desired-state controls + `run()` driver) |
| `app/src/main/java/com/hive/hiragana/drawing/animation/StrokeFramePath.kt` | Timed frames → full/partial `Path` + interpolated pen tip |
| `app/src/main/java/com/hive/hiragana/presentation/components/AnimatedStrokePreview.kt` | Animated stroke-order demo canvas + playback controls |

## 1. Pipeline overview

```
 Android input system
        |
        |  android.view.MotionEvent (raw: pressure, tool type,
        |  batched historical samples, eventTime)
        v
 TracingCanvas.kt
   Modifier.pointerInteropFilter { event -> strokeCapture.onMotionEvent(event) }
        |
        v
 StrokeCapture  (drawing/stroke/StrokeCapture.kt)
   - decodes actionMasked, tracks single activePointerId (palm rejection)
   - unpacks historical samples, coerces pressure into 0..2
   - emits StrokePoint(x, y, pressure, timestamp)
        |
        |  startStroke / extendStroke / finishStroke / cancelStroke
        v
 CanvasState  (drawing/canvas/CanvasState.kt)   [@Stable, snapshot state]
   - activeStroke : UserStroke        (raw points, private, non-snapshot)
   - activePath   : Path?             (snapshot state -> triggers redraw)
   - completedPaths : SnapshotStateList<Path>
   - brushBaseWidthPx : Float         (plain var; canvas assigns from 20.dp
                                       × cell-proportional scale every draw
                                       pass)
   - currentSession : DrawingSession  (raw data handed to grading)
        |                                        |
        |  calls into                            |  session snapshot on grade
        v                                        v
 StrokeRenderer  (object)                 PracticeViewModel.gradeCurrentCharacter
   - buildBrushPath(points,                (strokeCount + durationMillis persisted
       baseWidthPx, taperTail): Path        to Room; see docs/DATA.md)
     filled variable-width ribbon;
     taperTail=false live / true committed
        |
        v
 TracingCanvas draw pass (Compose Canvas, every frame the state changes)
   1. drawPracticeGrid      (2x2 dashed genkouyoushi cell)
   2. drawGhostCharacter    (light-grey font glyph, native drawText)
   3. completedPaths        (filled brush ribbons — width baked into geometry)
   4. activePath            (filled brush ribbon, full-width tip)
```

Everything runs on the UI thread. `CanvasState` is explicitly documented as
UI-thread-only; there is no locking anywhere in the pipeline.

## 2. Input capture (`StrokeCapture.kt`)

### Why `pointerInteropFilter` instead of `pointerInput`

`TracingCanvas` attaches `Modifier.pointerInteropFilter` (an
`ExperimentalComposeUiApi`) rather than the idiomatic
`Modifier.pointerInput { detectDragGestures { ... } }`. The reason is fidelity:
the interop filter hands over the **raw `android.view.MotionEvent`**, which
exposes two things Compose's pointer API does not surface as conveniently:

1. **Per-point pressure** (`getPressure` / `getHistoricalPressure`) — needed
   for the pressure→width mapping in section 5.
2. **Batched historical samples** — Android coalesces high-frequency input
   (stylus digitizers sample far faster than the display refreshes) into a
   single `ACTION_MOVE` carrying `historySize` earlier samples. Reading them
   via `getHistoricalX/Y/Pressure/EventTime` recovers the full sample density,
   which is what makes the smoothed curve follow fast pen motion accurately.

It also gives direct access to `getToolType`, so `StrokeCapture` records
`lastToolType` and exposes `isStylus` (`TOOL_TYPE_STYLUS`). Both stylus and
finger input are accepted; nothing filters on tool type.

`onMotionEvent` always returns `true`, so the canvas consumes every event it
receives.

### MotionEvent action cases

`StrokeCapture` keeps one field, `activePointerId`, initialised to
`MotionEvent.INVALID_POINTER_ID`. Dispatch is on `event.actionMasked`:

| Action | Behaviour |
|---|---|
| `ACTION_DOWN` | First pointer touches down. Its id (pointer index 0) becomes `activePointerId`, `lastToolType` is recorded, and `canvasState.startStroke(...)` is called with a single `StrokePoint` built from index 0. |
| `ACTION_MOVE` | `findPointerIndex(activePointerId)`; if `-1` (active pointer not in this event, or no active pointer) the event is ignored. Otherwise `canvasState.extendStroke(...)` receives the batched points: all `historySize` historical samples **first**, then the current sample (`collectStrokePoints`). |
| `ACTION_UP` | Last pointer lifts. If the active pointer's index resolves, its final position is appended via `extendStroke(listOf(...))`, then `finishStroke()` commits the stroke and `activePointerId` resets to `INVALID_POINTER_ID`. |
| `ACTION_POINTER_UP` | A non-last pointer lifts in a multi-touch gesture. Only if `getPointerId(actionIndex) == activePointerId` does the stroke end (`finishStroke()` + reset). If some *other* finger lifted, nothing happens — the drawing pointer keeps drawing. Note: unlike `ACTION_UP`, the final sample is **not** appended before committing. |
| `ACTION_CANCEL` | System stole the gesture (e.g. a parent intercepted). `canvasState.cancelStroke()` **discards** the in-progress stroke — it is never committed to the session — and `activePointerId` resets. |
| `ACTION_POINTER_DOWN` | **Deliberately unhandled.** Extra pointers that land while a stroke is in progress never become the active pointer, so a palm or second finger resting on the screen contributes nothing. This is the app's palm rejection. |

Palm-rejection consequences worth knowing: because new pointers are never
adopted, if the drawing pointer lifts first in a multi-touch situation
(`ACTION_POINTER_UP` path), the still-touching palm/finger cannot continue or
start a stroke — no new stroke begins until all pointers lift and a fresh
`ACTION_DOWN` arrives. Subsequent `ACTION_MOVE`s in that window hit the
`findPointerIndex(INVALID_POINTER_ID) == -1` guard and are dropped.

### Point construction

Both the current-sample helper (`strokePointAt`) and the historical loop
(`collectStrokePoints`) build:

```kotlin
StrokePoint(
    x = getX(pointerIndex),                       // canvas-local px
    y = getY(pointerIndex),
    pressure = getPressure(...).coerceIn(0f, 2f), // clamp outliers
    timestamp = eventTime,                        // ms since boot
)
```

- **Pressure coercion 0..2**: raw `MotionEvent` pressure is nominally ~0..1
  (fingers usually report ~1.0) but devices may report calibrated values above
  1; the clamp caps garbage readings so the width math in section 5 stays sane.
  Applied identically to historical samples
  (`getHistoricalPressure(...).coerceIn(0f, 2f)`).
- **Timestamps** are `MotionEvent.eventTime` / `getHistoricalEventTime(h)` —
  milliseconds since boot, monotonic, so differences between any two points are
  valid durations (used by `durationMillis()`).

## 3. Data model

### `StrokePoint` (`drawing/stroke/StrokePoint.kt`)

| Field | Type | Meaning |
|---|---|---|
| `x`, `y` | `Float` | Canvas-local pixel coordinates (as delivered by the MotionEvent to the Canvas composable) |
| `pressure` | `Float` | Raw MotionEvent pressure, coerced into `0..2`. Stylus hardware reports real pressure; fingers usually ~1.0 |
| `timestamp` | `Long` | MotionEvent event time, ms since boot |

### `UserStroke` (`drawing/stroke/UserStroke.kt`)

One continuous pen-down → pen-up stroke.

| Field | Type | Meaning |
|---|---|---|
| `points` | `MutableList<StrokePoint>` | Samples in arrival order; mutated in place by `CanvasState.extendStroke` |

`averagePressure(): Float` is a **member function of `UserStroke`** (not an
extension). It returns the arithmetic mean of all point pressures, or `1f` for
an empty point list. Since the brush rework it has **no callers** — the old
whole-stroke width scale used it; `buildBrushPath` applies pressure per
point instead (section 5).

### `DrawingSession` (`drawing/stroke/DrawingSession.kt`)

All strokes drawn for the character currently on the canvas. A new session
starts whenever the canvas is cleared or the character changes.

| Field | Type | Meaning |
|---|---|---|
| `strokes` | `MutableList<UserStroke>` | Committed strokes in completion order (cancelled strokes are never added) |

`durationMillis(): Long` is a **top-level extension function on
`DrawingSession`, declared in the same file** (`DrawingSession.kt`). Exact
semantics: timestamp of the *last point of the last stroke* minus timestamp of
the *first point of the first stroke*, `coerceAtLeast(0L)`; returns `0L` if
either end is missing. This is **wall-clock session span** — it includes the
pauses between strokes, not just time with the pen down. Both timestamps are
ms-since-boot, so the difference is a valid duration.

### Render-ready form: a plain `Path` (was `CompletedStroke`)

Since the brush rework the render-ready form of a finished stroke is just an
`androidx.compose.ui.graphics.Path` — the filled brush ribbon from
`StrokeRenderer.buildBrushPath` with the ink width **baked into the
geometry**, so no per-stroke width multiplier needs to travel with it. The
old `CompletedStroke` wrapper (`path` + frozen `widthScale`) is **gone**;
`CanvasState.completedPaths` is now a `SnapshotStateList<Path>`.

Raw data (`UserStroke` in the session) and render data (`Path`s in
`completedPaths`) are kept in parallel: the session feeds grading/persistence,
the paths feed the draw pass.

## 4. `CanvasState` lifecycle

`CanvasState` (`drawing/canvas/CanvasState.kt`) is a `@Stable` class holding
Compose snapshot state. It is created in `PracticeScreen` with
`remember { CanvasState() }` (it intentionally survives recomposition but not
configuration changes — see docs/ARCHITECTURE.md for the state-split rationale)
and wiped by:

```kotlin
LaunchedEffect(uiState.canvasClearKey) { canvasState.clear() }
```

so any advance — grading, pressing Next, a vocabulary kana moving forward, a
session-phase change, or the initial load — starts from a blank sheet.
(Phase 3 replaced the original `LaunchedEffect(uiState.currentIndex, uiState.characters)`
key with this single monotonic counter, bumped by the ViewModel on every
advance regardless of cause — see docs/ARCHITECTURE.md §3 — but the clearing
mechanism itself, and everything below in this section, is unchanged.)

Properties:

| Property | Backing | Visibility | Purpose |
|---|---|---|---|
| `brushBaseWidthPx` | plain `var Float = 48f` (deliberately **not** snapshot state) | public | Full brush width in px at neutral pressure/speed, baked into every path built from then on. `TracingCanvas` assigns it from `20.dp.toPx()` × `brushScale(cellSide, referenceSide)` (cell-proportional; see §Draw pass) on **every draw pass**, before input can arrive — a cheap plain-field write that must not invalidate the draw |
| `currentSession` | `mutableStateOf(DrawingSession())` | public get, private set | Raw point data handed to the ViewModel on grade |
| `completedPaths` | `mutableStateListOf<Path>()` | public | Finished brush ribbons for the draw pass |
| `activePath` | `mutableStateOf<Path?>(null)` | public get, private set | Brush ribbon of the in-progress stroke |
| `activeStroke` | plain `var UserStroke?` | private | Raw points of the in-progress stroke (not snapshot state — only its derived `activePath` needs to be observable) |
| `hasInk` | derived getter | public | `completedPaths.isNotEmpty() \|\| activePath != null`; enables the Clear/grade buttons in `PracticeActionBar` |

(The old `activeWidthScale` property and `CompletedStroke` wrapper are gone
— width is per-point ribbon geometry now, not a stroke-level multiplier.)

### State machine

Two states: **Idle** (`activeStroke == null`) and **Drawing**
(`activeStroke != null`).

| State | Call | Effect | Next state |
|---|---|---|---|
| Idle | `startStroke(p)` | New `UserStroke([p])`; build `activePath` (`buildBrushPath`, `taperTail = false`) | Drawing |
| Idle | `extendStroke(pts)` | No-op (`activeStroke ?: return`) | Idle |
| Idle | `finishStroke()` | No-op (`activeStroke ?: return`) | Idle |
| Idle | `cancelStroke()` | Nulls already-null refs (harmless) | Idle |
| Drawing | `startStroke(p)` | **Defensive commit**: `activeStroke?.let(::commitStroke)` first, then start the new stroke as above | Drawing |
| Drawing | `extendStroke(pts)` | Append `pts` to `activeStroke.points`; rebuild `activePath` from scratch (empty `pts` is a no-op) | Drawing |
| Drawing | `finishStroke()` | `commitStroke(stroke)` — see below | Idle |
| Drawing | `cancelStroke()` | Drop `activeStroke` and `activePath`; the raw points are **discarded**, never added to the session | Idle |
| any | `clear()` | Null active refs, `completedPaths.clear()`, `currentSession = DrawingSession()` (fresh instance) | Idle |

The live stroke is always built with `taperTail = false` — its "tail" is the
moving pen tip and must stay at full width while drawing continues; the
brush-flick exit taper appears only when the stroke commits.

`commitStroke(stroke)` (private) does three things: appends the raw
`UserStroke` to `currentSession.strokes`, appends the final ribbon —
`buildBrushPath(stroke.points, brushBaseWidthPx, taperTail = true)` — to
`completedPaths`, and nulls `activeStroke`/`activePath`.

The **defensive commit in `startStroke`** guards against a missed
UP/CANCEL: if a stroke was somehow left open when a new `ACTION_DOWN` arrives,
its points are committed rather than silently lost, and the state machine
cannot leak an orphaned stroke.

### Why reassigning `activePath` triggers redraw

`Path` is a mutable object, and Compose snapshot state only observes
*assignment*, not internal mutation. So `extendStroke` never mutates the
existing path — it rebuilds a **fresh `Path`** from all points via
`StrokeRenderer.buildBrushPath` and reassigns `activePath`. The comment in
the source is explicit: *"Reassigning a fresh Path is what triggers the
redraw."* The `Canvas` lambda in `TracingCanvas` reads `activePath` and
iterates `completedPaths`, so every reassignment or
list mutation invalidates the draw pass. (Cost note: this is an O(n) rebuild
per MOVE event over the stroke's full point list — acceptable at hiragana
stroke lengths, see section 8.)

### Hand-off to grading

When the user taps a grade button (Again/Hard/Good/Easy as of Phase 3, née
Easy/Okay/Hard), `PracticeScreen` passes `canvasState.currentSession` to
`PracticeViewModel.gradeCurrentCharacter`, which builds the `PracticeAttempt`
(**synchronously** — `strokes.size` and `durationMillis()` are read before
any coroutine launches) precisely because grading will immediately bump
`PracticeUiState.canvasClearKey` and trigger the `LaunchedEffect` that clears
this canvas. The Room insert itself is fire-and-forget; as of Phase 3 its
failures are logged (`Log.w`, tag `HiraganaSession`) rather than silently
swallowed, and grading may also reschedule a spaced-repetition card
depending on the active session phase — see docs/DATA.md and
docs/ARCHITECTURE.md §4/§8 for the full write path.

## 5. Rendering (`StrokeRenderer.kt` + `TracingCanvas.kt`)

### The brush ribbon — `buildBrushPath(points, baseWidthPx, taperTail): Path`

The single entry point of the rewritten `StrokeRenderer` (the old
`buildSmoothPath` / `pressureWidthScale` pair is **gone**). Instead of a
uniform-width polyline drawn with a `Stroke` style, it returns a **closed,
filled `Path`** — a variable-width ribbon whose two edges are offset from a
smoothed centerline along its normals, with round-cap discs closing both
ends. Every sampled point gets its own ink width from pen pressure *and* pen
speed, so the result reads like a felt brush: fast flicks thin out, firm
slow strokes swell. Parameters: `points` (raw samples, canvas px, draw
order), `baseWidthPx` (full ink width at neutral pressure/speed — see
`CanvasState.brushBaseWidthPx`), `taperTail` (`false` for the live stroke,
`true` at commit).

The stages, in order (all constants are `private const val`s in the file):

1. **Decimation** — input points closer than `MIN_POINT_STEP = 1.5f` px to
   their kept predecessor are dropped (stabilises the edge normals), except
   that the stroke's true endpoint always survives so the ink reaches
   exactly where the pen stopped. If fewer than 2 points remain (a tap), a
   single round blot is emitted instead: an oval of radius
   `halfBase × pressureScale(point.pressure)` at the touch point.
2. **Per-point width profile** — each point's raw half-width is
   `halfBase × pressureScale × speedScale`, where:
   - *pressure*: `(0.55f + 0.9f * pressure).coerceIn(0.55f, 1.6f)` — the
     same curve and clamps the Phase 1 whole-stroke scale used, now applied
     per point;
   - *speed*: no thinning at or below `SPEED_FULL_WIDTH = 0.3f` px/ms;
     above it the multiplier is `1 − (speed − 0.3) × 0.12` per px/ms
     (`SPEED_THINNING = 0.12f`), floored at `MIN_SPEED_SCALE = 0.55f` so
     fast flicks thin out but never vanish. Batched samples sharing a
     timestamp (`dt <= 0`) reuse the previous point's speed scale.
3. **Width smoothing** — a moving average over a ±2-point window
   (`WIDTH_SMOOTHING_RADIUS = 2`) so neither jittery pressure sensors nor
   batched timestamps make the edges wobble.
4. **End tapers** (applied to the smoothed profile, by arc length):
   - *head, always*: over the first `HEAD_TAPER_PX = 12f` px the width
     ramps from `HEAD_START_SCALE = 0.7f` up to full;
   - *tail, only when `taperTail = true`*: over the final
     `TAIL_TAPER_PX = 28f` px the width falls to `TAIL_END_SCALE = 0.35f`
     — the brush-flick exit that must not appear on the live stroke, whose
     tail is the moving pen tip.
   Every half-width is floored at `MIN_HALF_WIDTH = 0.75f` px so degenerate
   input still leaves visible ink (the same floor applies to `halfBase`
   itself).
5. **Centerline smoothing** — one binomial pass
   (`(p₋₁ + 2p + p₊₁) / 4`, endpoints pinned) over the decimated points.
6. **Outline** — left/right edge points are offset from the centerline
   along its normals by the per-point half-widths; the path travels down
   the left edge and back up the right, **each edge appended with the same
   midpoint quadratic-bézier smoothing the old line renderer used**, then
   closes. Finally two oval "round cap" discs at the first/last centerline
   points (at their local half-widths) are added — non-zero fill unions
   them with the ribbon.

Consumers draw the returned path with a **plain fill** — no `Stroke` style,
no cap/join flags, no width multiplication; the width is baked into the
geometry.

### Draw pass and layer order

`TracingCanvas` wraps a Compose `Canvas` in a `Surface` (rounded 20.dp,
`PaperWhite`, elevation 2.dp) with `clipToBounds()`. The drawing cell is a
centred square: `side = size.minDimension - 16.dp.toPx()`.

Render order, bottom to top:

1. **Practice grid** (`drawPracticeGrid`)
2. **Ghost character** (`drawGhostCharacter`, `GhostGrey` = `0xFFD9D9E0`)
3. **Completed strokes** (filled brush ribbons, opaque `inkColor`)
4. **Active stroke** (filled brush ribbon, opaque `inkColor`)

Between steps 2 and 3 the draw pass publishes the ink width:
`canvasState.brushBaseWidthPx = BrushBaseWidth.toPx() * brushScale(side, referenceSide)`
on **every** pass (a cheap plain-field write — deliberately not snapshot
state, so it cannot re-invalidate the draw). `BrushBaseWidth` is 20.dp,
tuned for the **reference cell** — the square a single character gets on
the 2×2 sheet, `(min(availWidth, availHeight) − slotGap) / 2`. The scale
is the actual best-fit cell side over that reference: below 1 the brush
thins **linearly** (denser layouts and the shrunken per-kana cells of a
word row get proportionally finer ink automatically), above 1 growth is
**halved** (`1 + (ratio − 1)/2` — the 1×1 cell is 2× the reference but
ink only grows ~1.5×). Cell-size-dependent width is safe because every
cell-size change (advance, layout switch) bumps `canvasClearKey`, clearing
committed ink before it could outlive its scale.
Brush geometry is built in pixels at *input* time,
so the width must be current before the next stroke starts. The old
`9.dp × widthScale` stroke-style width math is gone; both ink layers are
`drawPath(path, color = inkColor)` plain fills. `inkColor` is a `TracingCanvas`
parameter (default `InkBlack`) sourced from the user-picked, persisted
`PracticeUiState.brushInkColor` — a purely-visual choice applied at draw time,
so it never affects geometry, width, or the grey ghost/preview (see the
`BrushColorPicker` component and the brush-ink-color convention in CLAUDE.md).

The ghost sits *under* the ink deliberately: the pale grey glyph is the
template, and the learner's opaque near-black strokes must read on top of it
so coverage of the template is immediately visible — drawing the ghost above
the ink would wash out exactly the feedback the tracing exercise exists to
give.

## 6. Practice grid and ghost glyph (`CanvasRenderers.kt`)

### `drawPracticeGrid(cell, borderColor, guideColor)`

Mimics one cell of a genkouyoushi (原稿用紙) practice sheet:

- **Outer border**: `drawRoundRect` over the full `cell`, corner radius
  `CornerRadius(16f, 16f)` (raw px, not dp), `Stroke(width = 2.dp)`,
  colour `GridBorder` (`0xFF8D99A6`).
- **Centre cross** dividing the cell into 2×2 quadrants: two dashed lines,
  `PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)` (14px on / 12px
  off), width `1.5.dp`, colour `GridGuide` (`0xFFC7CFD8`), inset `6.dp` from
  the cell edges at each end. Horizontal line runs at `cell.center.y` from
  `cell.left + inset` to `cell.right - inset`; vertical line mirrors that at
  `cell.center.x`.

### `drawGhostCharacter(character, cell, paint)`

The ghost is **font-rendered**, not built from the stroke SVG paths — the
system font guarantees a typographically correct glyph (the SVG stroke data
drives only the small `StrokeOrderDiagram`). It draws through the **native**
canvas because Compose has no first-class `drawText` for this use here:
`drawIntoCanvas { canvas.nativeCanvas.drawText(character, cx, baseline, paint) }`.

- Empty string → early return.
- `paint.textSize = cell.height * 0.78f` (glyph fills ~78% of the cell).
- The `Paint` (remembered in `TracingCanvas`) is antialiased, colour
  `GhostGrey`, `textAlign = Paint.Align.CENTER` — the paint handles
  horizontal centering on `cell.center.x`.
- **Baseline centering math**: `drawText`'s y is the *baseline*, so vertical
  centering uses font metrics:
  `baseline = cell.center.y - (metrics.ascent + metrics.descent) / 2f`.
  `ascent` is negative (top above baseline), `descent` positive;
  `(ascent + descent) / 2` is the signed offset from the baseline to the
  midpoint of the ascent–descent span. Subtracting it from the cell's centre
  places that span's midpoint exactly at the vertical centre of the cell.

## 7. Stroke-order guide: animated preview + static diagram

The guide panel (top 25% of `PracticeScreen`, via `CharacterGuidePanel`)
shows `AnimatedStrokePreview` (Phase 2): strokes draw themselves in order
with the recorded rhythm over a faint full-character guide, a pen-tip dot
disappearing between strokes (the visible pen lift), and a compact controls
row (play/pause, replay, 0.5×/1×/2× speed cycle, auto-repeat). The pipeline:

```
strokeAnimations JSON (timed frames, viewBox space; docs/DATA.md)
   └─► StrokeAnimationEngine  (@Stable; controls mutate desired state,
        suspend run() drives an Animatable clock; outputs completedPaths /
        activePath / penPosition / progress as snapshot state)
        └─► StrokeFramePath   (frames ≤ t → smoothed partial Path + pen tip,
             the midpoint-bézier smoothing the pre-brush StrokeRenderer
             drew ink with, and which the ribbon's edges still use)
             └─► AnimatedStrokePreview canvas (reads outputs in the draw
                  lambda → per-frame draw invalidation, no recomposition)
```

Full engine semantics (pause/resume, speed math, epoch-guarded aborts,
edge cases) are specified in [docs/PHASE2-DESIGN.md](PHASE2-DESIGN.md) §2.

### Static `StrokeOrderDiagram` (`presentation/components/StrokeOrderDiagram.kt`)

The Phase 1 diagram is kept as the **fallback**, rendered by
`AnimatedStrokePreview` whenever a character has no `strokeAnimations`:
every stroke rendered at once with a numbered badge at its starting point.

- **Parsing** (in `remember(character.character)`, so once per character):
  each entry of `HiraganaCharacter.strokes` (one SVG path string per stroke,
  in stroke order — see docs/DATA.md for the JSON contract) goes through
  Compose's `androidx.compose.ui.graphics.vector.PathParser`:
  `PathParser().parsePathString(pathData)`, then `toPath()` for the drawable
  `Path` and `toNodes()` to find the **first `PathNode.MoveTo`**, whose
  coordinates become the badge anchor (`Offset.Zero` if none). Each parse is
  wrapped in `runCatching { ... }.getOrNull()` inside `mapNotNull`, so a
  malformed path is silently skipped rather than crashing the diagram.
- **viewBox scaling**: paths live in a square coordinate space of
  side `character.viewBox` (parser default `100f`; the shipped
  KanjiVG-derived files all carry `109`). The diagram computes
  `scale = size.minDimension / character.viewBox` and draws all strokes inside
  `withTransform({ scale(scale, scale, Offset.Zero) })`. Crucially the stroke
  style is `width = 3.5.dp.toPx() / scale`: the transform will multiply the
  width back up by `scale`, so the on-screen line is always exactly 3.5dp
  regardless of viewBox units or diagram size — **scale-invariant stroke
  width**. Colour defaults to `DiagramStroke` (`0xFF6B7280`), round cap/join.
- **Numbered badges** are drawn *outside* the transform, in screen space, at
  `Offset(start.x * scale, start.y * scale)` — i.e. at each stroke's first
  `MoveTo`, manually scaled. Badge: filled circle of radius
  `size.minDimension * 0.075f` in `MaterialTheme.colorScheme.primary`; label
  `"${index + 1}"` in `PaperWhite`, fake-bold, `textSize = radius * 1.35f`,
  centred with the same font-metrics baseline math as the ghost glyph
  (`center.y - (ascent + descent) / 2f`), drawn via `nativeCanvas.drawText`.

## 8. Known limitations

Honest list; several are also flagged in the project-wide warts section of
[CLAUDE.md](../CLAUDE.md) and [README.md](../README.md).

- **Per-stroke average width, no taper — FIXED (brush rework).** The old
  renderer applied one width scale (from the stroke's *average* pressure)
  uniformly to the whole path, with no entry/exit taper — the single most
  visible difference from real calligraphy apps, and the live stroke's
  entire width shifted as the running average changed. `buildBrushPath`
  now gives every point its own width and adds head/tail tapers
  (section 5); the whole-stroke-width artifacts are gone.
- **No velocity-based effects — FIXED (brush rework).** Point timestamps
  now drive speed thinning in the width profile (section 5) in addition to
  feeding `durationMillis()` for analytics.
- **Single simultaneous pointer.** By design (palm rejection), only one
  pointer draws. Corollaries: `ACTION_POINTER_DOWN` is ignored so a second
  finger can never draw; if the active pointer lifts during multi-touch, no
  drawing is possible until *all* pointers lift and a new `ACTION_DOWN`
  arrives; and the `ACTION_POINTER_UP` path commits without appending the
  final sample (unlike `ACTION_UP` — a minor, cosmetic asymmetry).
- **O(n) path rebuild per MOVE.** `extendStroke` re-smooths the entire point
  list into a fresh `Path` on every event. Fine for hiragana-length strokes;
  would need incremental path building for long free-drawing strokes.
- **Ghost/diagram shape mismatch is possible.** The ghost glyph is the system
  font's rendering; the stroke-order diagram is hand-authored SVG data. The
  two are not guaranteed to overlap pixel-perfectly.
- **Nothing visual is persisted.** Only `strokeCount` and `durationMs` survive
  into Room; the actual ink (points, paths, pressure) is discarded on clear.
- **`pointerInteropFilter` is experimental** (`@ExperimentalComposeUiApi`),
  accepted as the cost of raw-MotionEvent fidelity.
- **No tests cover the drawing subsystem.** The project's 45 unit tests
  (added in Phase 3) are all `domain/learning` (SRS scheduler, lesson
  generator, vocabulary unlocking) — none exercise `StrokeCapture`,
  `CanvasState`, `StrokeRenderer`, or the animation engine. The
  brush-ribbon geometry (`buildBrushPath` is pure input→`Path` math) and
  the `StrokeCapture` state machine remain the most test-worthy candidates
  here.
