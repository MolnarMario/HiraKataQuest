package com.hive.hiragana.drawing.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.hive.hiragana.domain.model.StrokeDefinition
import com.hive.hiragana.domain.model.StrokeFrame
import kotlin.math.ceil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle of the demonstration driven by [StrokeAnimationEngine].
 *
 * - [Idle]     nothing has played yet, or [StrokeAnimationEngine.reset] was
 *              called; all outputs are cleared.
 * - [Playing]  the timeline is advancing.
 * - [Paused]   frozen mid-timeline; outputs keep their last values so the
 *              partial stroke and pen tip stay visible exactly where they stopped.
 * - [Finished] the last stroke completed with auto-repeat off; the fully drawn
 *              character stays on screen until the next play/replay/reset.
 */
enum class PlaybackState { Idle, Playing, Paused, Finished }

/**
 * Wall-clock floor (~one display frame at 60 Hz) for a stroke whose recording
 * has zero duration — a single-frame dot. Without it a dot stroke would occupy
 * zero frames and pop into existence with no visible pen-down (contract §2.1:
 * "minimum 1-frame wall duration").
 */
private const val MIN_STROKE_DURATION_MS = 16L

/** Clamp bounds for [StrokeAnimationEngine.setSpeed] (contract: 0.25f..4f). */
private const val MIN_SPEED_MULTIPLIER = 0.25f
private const val MAX_SPEED_MULTIPLIER = 4f

/**
 * Drives an animated stroke-order demonstration for one character: which
 * strokes are already drawn, how far the current stroke has progressed, and
 * where the pen tip is — all as Compose snapshot state that a `Canvas` can
 * read directly and redraw automatically.
 *
 * Same design philosophy as `CanvasState` (docs/DRAWING.md): a [Stable]
 * snapshot-state holder with no Android framework dependencies beyond the
 * Compose runtime/ui, and **UI-thread-only by contract** — controls are meant
 * for `onClick` handlers and [run] for a `LaunchedEffect`, all on the main
 * thread. Nothing here is safe for concurrent mutation from other threads.
 *
 * Timeline (contract §2.1) — durations shown at 1x; every wall duration
 * divides by [speedMultiplier]:
 *
 * ```
 * play()
 *   |
 *   v
 * stroke 1 -> pause -> stroke 2 -> pause -> ... -> stroke N --> Finished
 * (pen down..up)  (pen lifted, penPosition = null)         |
 *   ^                                                      | autoRepeat: hold one
 *   +------------------------------------------------------+ pause, clear, redraw
 * ```
 *
 * Stroke *i*'s animated duration at 1x equals its recorded
 * `points.last().time`, so the demonstration reproduces the original writing
 * rhythm rather than a fixed per-stroke tween. [progress] advances through
 * pauses too, covering the whole cycle 0f..1f.
 *
 * The only supported usage (contract §2):
 *
 * ```
 * val engine = remember(character.character) { StrokeAnimationEngine(character.strokeAnimations) }
 * LaunchedEffect(engine) { engine.run() }             // single driver
 * LaunchedEffect(engine) { if (autoPlay) engine.play() }
 * ```
 *
 * Robustness: the engine never throws for degenerate-but-well-formed data —
 * empty stroke lists, single-frame strokes, unsorted or empty recordings all
 * degrade gracefully (see [strokeCount], [play], and contract §2.1).
 *
 * @param strokes timed stroke recordings in viewBox space. Defensively
 *   re-sorted by [StrokeDefinition.order] (the parser sorts too, but the
 *   engine must not depend on that); entries with no points are dropped.
 * @param interStrokePauseMs pen-lifted pause between consecutive strokes at
 *   1x speed; negative values are treated as 0.
 */
@Stable
class StrokeAnimationEngine(
    strokes: List<StrokeDefinition>,
    interStrokePauseMs: Long = 350L,
) {

    private val strokePauseMs: Long = interStrokePauseMs.coerceAtLeast(0L)

    /**
     * The demonstration timeline: sanitized strokes in ascending [StrokeDefinition.order],
     * each placed at its 1x start offset so [progress] is a cheap division from
     * any mid-stroke position.
     */
    private val timeline: List<TimelineStroke> = buildList {
        val ordered = strokes.filter { it.points.isNotEmpty() }.sortedBy { it.order }
        var offsetMs = 0L
        ordered.forEachIndexed { index, definition ->
            val recordedMs = definition.points.last().time
            val durationMs = if (recordedMs > 0L) recordedMs else MIN_STROKE_DURATION_MS
            add(TimelineStroke(definition.points, durationMs, offsetMs))
            offsetMs += durationMs
            if (index < ordered.lastIndex) offsetMs += strokePauseMs
        }
    }

    /** Full 1x cycle length: all stroke durations plus the pauses between them. */
    private val totalCycleMs: Float =
        timeline.lastOrNull()?.let { (it.startOffsetMs + it.durationMs).toFloat() } ?: 0f

    /**
     * Number of strokes the demonstration animates. Equals `strokes.size` for
     * well-formed data; degenerate entries with an empty points list are
     * dropped defensively rather than crashing mid-animation.
     */
    val strokeCount: Int = timeline.size

    // --- Snapshot-state outputs (safe to read from a draw scope) ---

    /** Where playback is in its lifecycle; starts [PlaybackState.Idle]. */
    var playbackState: PlaybackState by mutableStateOf(PlaybackState.Idle)
        private set

    /**
     * Playback speed; 1f plays the recorded rhythm as written. Only [setSpeed]
     * mutates it (clamped 0.25f..4f). Both stroke and pause wall durations
     * divide by this value.
     */
    var speedMultiplier: Float by mutableFloatStateOf(1f)
        private set

    // Backing state kept private so the generated JVM setter cannot clash
    // with the contract's explicit setAutoRepeat(Boolean) below.
    private var _autoRepeat: Boolean by mutableStateOf(false)

    /**
     * When true, the demonstration wraps from the last stroke back to stroke 1
     * instead of entering [PlaybackState.Finished]. Read at the end of each
     * cycle, so toggling mid-play affects the current cycle's ending.
     */
    val autoRepeat: Boolean get() = _autoRepeat

    /**
     * 0-based index (into the animated stroke sequence) of the stroke
     * currently animating — or, during an inter-stroke pause and when
     * Finished, of the last stroke that completed. -1 while Idle.
     */
    var currentStrokeIndex: Int by mutableIntStateOf(-1)
        private set

    private val completedStrokePaths = mutableStateListOf<Path>()

    /**
     * ViewBox-space paths of the strokes fully drawn so far, in stroke order.
     * Entries are stable objects: each stroke's finished path is built once
     * (on its first completion) and the same instance is re-added on every
     * auto-repeat cycle, so consumers can rely on reference equality.
     */
    val completedPaths: List<Path> get() = completedStrokePaths

    /**
     * Partial viewBox-space path of the stroke being animated: all frames
     * reached so far plus a linear segment to the interpolated pen tip.
     * Null whenever no stroke is mid-animation (Idle, inter-stroke pauses,
     * Finished). While Paused mid-stroke it keeps the frozen partial path.
     */
    var activePath: Path? by mutableStateOf(null)
        private set

    /**
     * Interpolated pen tip in viewBox space; null whenever the pen is lifted —
     * before playback starts, between strokes, after the last stroke, and
     * while Idle/Finished. The null gaps are what make pen lifts visible.
     * While Paused mid-stroke it keeps the frozen tip position.
     */
    var penPosition: Offset? by mutableStateOf(null)
        private set

    /**
     * 0f..1f across the whole character's cycle, inter-stroke pauses included
     * (it keeps advancing while the pen is lifted). Speed-invariant: measured
     * on the 1x timeline, so changing [speedMultiplier] never jumps it.
     * Reaches exactly 1f when the last stroke completes; resets to 0f on each
     * auto-repeat wrap and on [reset].
     */
    var progress: Float by mutableFloatStateOf(0f)
        private set

    /**
     * Desired-state generation counter. Bumped by every control that demands a
     * restart-from-scratch ([replay], [reset], [play] from Idle/Finished); the
     * driver treats any epoch change as "abandon the current cycle without
     * writing another output". Snapshot state so the driver's snapshotFlow
     * watchers observe it.
     */
    private var timelineEpoch: Int by mutableIntStateOf(0)

    private var driverRunning = false

    // --- Non-suspend controls (call from onClick; UI thread) ---

    /**
     * From Idle or Finished: restart the demonstration from stroke 1.
     * From Paused: resume in place — mid-stroke, from the frozen pen position.
     * While already Playing: no-op.
     *
     * With no animatable strokes this goes straight to [PlaybackState.Finished]
     * (with [progress] = 1f) without involving the driver — the degenerate
     * "nothing to demonstrate" case is trivially complete (contract §2.1).
     */
    fun play() {
        if (timeline.isEmpty()) {
            progress = 1f
            playbackState = PlaybackState.Finished
            return
        }
        when (playbackState) {
            PlaybackState.Idle, PlaybackState.Finished -> {
                timelineEpoch++
                playbackState = PlaybackState.Playing
            }
            PlaybackState.Paused -> playbackState = PlaybackState.Playing
            PlaybackState.Playing -> Unit
        }
    }

    /**
     * Freezes the demonstration exactly where it is: the partial [activePath]
     * and [penPosition] stay on screen, and mid-pause countdowns stop too.
     * No-op unless currently Playing, so repeated calls are harmless.
     */
    fun pause() {
        if (playbackState == PlaybackState.Playing) {
            playbackState = PlaybackState.Paused
        }
    }

    /**
     * Always restarts from stroke 1 and plays, whatever the current state —
     * including mid-play and mid-pause. Outputs are cleared by the driver as
     * the new cycle starts (typically the next frame).
     */
    fun replay() {
        if (timeline.isEmpty()) {
            progress = 1f
            playbackState = PlaybackState.Finished
            return
        }
        timelineEpoch++
        playbackState = PlaybackState.Playing
    }

    /**
     * Sets the playback speed, clamped to 0.25f..4f. Takes effect immediately,
     * including mid-stroke: the driver re-tweens the remainder of the current
     * segment at the new wall duration from its current position, so there is
     * no visual jump. While Paused, the new speed applies on resume.
     * NaN is ignored (defensive: it would poison every wall-duration division).
     */
    fun setSpeed(multiplier: Float) {
        if (multiplier.isNaN()) return
        speedMultiplier = multiplier.coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER)
    }

    /** Enables/disables wrapping back to stroke 1 after the last stroke. */
    fun setAutoRepeat(enabled: Boolean) {
        _autoRepeat = enabled
    }

    /**
     * Back to [PlaybackState.Idle] with every output cleared, synchronously —
     * callers can rely on a blank demonstration the moment this returns. Any
     * in-flight cycle is abandoned (the epoch bump prevents even an already
     * scheduled animation frame from resurrecting stale ink).
     */
    fun reset() {
        timelineEpoch++
        playbackState = PlaybackState.Idle
        currentStrokeIndex = -1
        completedStrokePaths.clear()
        activePath = null
        penPosition = null
        progress = 0f
    }

    // --- Single driver coroutine ---

    /**
     * The single driver that owns ALL suspend work. Call exactly once per
     * instance, from a `LaunchedEffect` — [Animatable] needs the composition's
     * `MonotonicFrameClock`, and the UI-thread contract needs the main
     * dispatcher. Loops until the calling scope is cancelled (cancellation
     * propagates out as usual); it never completes normally.
     *
     * Starting a second concurrent driver throws [IllegalStateException] —
     * that is API misuse, not a data edge case, and two drivers would corrupt
     * the timeline. A sequential re-run after cancellation is permitted.
     */
    suspend fun run() {
        check(!driverRunning) {
            "StrokeAnimationEngine.run() is already active; call it exactly once per instance."
        }
        driverRunning = true
        try {
            // Pin the motion duration scale to 1: Compose otherwise honours
            // the system animator scale, and with animations disabled
            // device-wide (animator_duration_scale = 0 — common once
            // developer options are on) every stroke would complete in a
            // single frame, popping fully drawn. This demonstration is
            // instructional content whose MOTION is the information — like
            // a video player, it must play regardless of that setting; the
            // user controls it through play/pause/speed instead. (Found in
            // the first device run, Pixel 7 with animations off.)
            withContext(DemonstrationMotionScale) {
                while (currentCoroutineContext().isActive) {
                    snapshotFlow { playbackState }.first { it == PlaybackState.Playing }
                    driveTimeline(timelineEpoch)
                }
            }
        } finally {
            driverRunning = false
        }
    }

    private object DemonstrationMotionScale : MotionDurationScale {
        override val scaleFactor: Float get() = 1f
    }

    // Implementation notes:
    //
    // Desired-state / driver split. The controls above never touch the
    // animation clock: they only write snapshot "desired state" — playbackState,
    // speedMultiplier, autoRepeat — plus timelineEpoch, the restart counter.
    // run() is the sole owner of suspend work: it sleeps on a snapshotFlow
    // until playbackState becomes Playing, then executes the timeline segment
    // by segment (stroke, pause, stroke, ...). Each segment advances a private
    // Animatable (LinearEasing tween over the remaining 1x-milliseconds divided
    // by the speed) while a concurrent snapshotFlow watcher races it, cancelling
    // the tween's job the moment (epoch, playbackState, speed) changes. A
    // cancelled Animatable keeps its current value — which is exactly the
    // resume cursor:
    //   - pause():   the segment loop parks in awaitResumeOrAbort; outputs stay
    //                frozen at the last written frame;
    //   - setSpeed(): the loop re-tweens the remainder from the current value
    //                at the new wall duration — no visual jump;
    //   - replay()/reset()/play()-from-Finished: the epoch differs, so every
    //                layer unwinds via `return false` without writing another
    //                output, and run()'s outer loop starts a fresh cycle.
    // Every output write is guarded by `timelineEpoch == epoch`, so an
    // animation frame that lands after reset() cleared the outputs (frame
    // callbacks and click handlers interleave freely on the main thread)
    // cannot resurrect stale ink. Idle/Finished can never be observed under an
    // unchanged epoch mid-cycle — the transitions into them either bump the
    // epoch (reset) or are written by the driver itself (Finished) — but the
    // segment loop still treats them as abort defensively.

    /**
     * Executes one demonstration from stroke 1 until it finishes, wraps
     * (auto-repeat), or is abandoned because [timelineEpoch] moved past
     * [epoch]. Pauses do not end it — it parks in place and continues.
     */
    private suspend fun driveTimeline(epoch: Int) {
        // A reset() issued before run() was woken leaves state Idle under the
        // latest epoch: there is nothing to drive.
        if (playbackState != PlaybackState.Playing && playbackState != PlaybackState.Paused) return
        if (timeline.isEmpty()) {
            // Defensive twin of the play()/replay() shortcut.
            if (timelineEpoch == epoch) {
                progress = 1f
                playbackState = PlaybackState.Finished
            }
            return
        }
        if (!beginCycleOutputs(epoch)) return
        while (true) {
            for (index in timeline.indices) {
                currentStrokeIndex = index
                if (!animateStroke(index, epoch)) return
                if (!commitStroke(index, epoch)) return
                if (index < timeline.lastIndex && !animateInterStrokePause(index, epoch)) return
            }
            if (timelineEpoch != epoch) return
            if (!autoRepeat) {
                playbackState = PlaybackState.Finished
                return
            }
            // Auto-repeat wrap: hold the completed character on screen for one
            // inter-stroke pause (pen lifted, progress at 1f) before clearing —
            // the wrap is stroke N -> stroke 1, so it gets the same pause as
            // any other stroke boundary; an instant clear would look like a
            // glitch, not a pen lift.
            if (!animateSegment(epoch, strokePauseMs) { }) return
            if (!beginCycleOutputs(epoch)) return
        }
    }

    /** Clears per-cycle outputs at the start of a (re)play cycle. */
    private fun beginCycleOutputs(epoch: Int): Boolean {
        if (timelineEpoch != epoch) return false
        completedStrokePaths.clear()
        activePath = null
        penPosition = null
        progress = 0f
        return true
    }

    /** Animates stroke [index]'s partial path, pen tip and progress. */
    private suspend fun animateStroke(index: Int, epoch: Int): Boolean {
        val stroke = timeline[index]
        return animateSegment(epoch, stroke.durationMs) { positionMs ->
            activePath = StrokeFramePath.partialPath(stroke.frames, positionMs)
            penPosition = StrokeFramePath.penPositionAt(stroke.frames, positionMs)
            progress = cycleProgress(stroke.startOffsetMs + positionMs)
        }
    }

    /**
     * Publishes stroke [index]'s finished path (a stable, once-built object)
     * and lifts the pen. Runs synchronously after [animateStroke] returned
     * true, so no control can interleave between the two.
     */
    private fun commitStroke(index: Int, epoch: Int): Boolean {
        if (timelineEpoch != epoch) return false
        val stroke = timeline[index]
        completedStrokePaths.add(stroke.fullPath)
        activePath = null
        penPosition = null
        progress = cycleProgress((stroke.startOffsetMs + stroke.durationMs).toFloat())
        return true
    }

    /**
     * The pen-lifted pause after stroke [index]. Only [progress] moves;
     * [penPosition] stays null so the lift is visible.
     */
    private suspend fun animateInterStrokePause(index: Int, epoch: Int): Boolean {
        val stroke = timeline[index]
        val pauseStartMs = stroke.startOffsetMs + stroke.durationMs
        return animateSegment(epoch, strokePauseMs) { positionMs ->
            progress = cycleProgress(pauseStartMs + positionMs)
        }
    }

    /**
     * Advances one timeline segment of [durationMs] 1x-milliseconds from 0 to
     * completion, reporting positions through [onFrame], while honouring the
     * desired state: pause parks it, a speed change re-tweens the remainder,
     * an epoch change aborts it. Returns true when the segment ran to its end,
     * false when abandoned; [onFrame] is never invoked under a stale epoch.
     */
    private suspend fun animateSegment(
        epoch: Int,
        durationMs: Long,
        onFrame: (positionMs: Float) -> Unit,
    ): Boolean {
        if (timelineEpoch != epoch) return false
        onFrame(0f)
        if (durationMs <= 0L) return true
        val clock = Animatable(0f)
        val targetMs = durationMs.toFloat()
        while (clock.value < targetMs) {
            if (timelineEpoch != epoch) return false
            when (playbackState) {
                PlaybackState.Playing -> {
                    val speed = speedMultiplier
                    val wallMs = ceil((targetMs - clock.value) / speed).toInt().coerceAtLeast(1)
                    raceAgainstControls(epoch, speed) {
                        clock.animateTo(
                            targetValue = targetMs,
                            animationSpec = tween(durationMillis = wallMs, easing = LinearEasing),
                        ) {
                            if (timelineEpoch == epoch) onFrame(value)
                        }
                    }
                }
                PlaybackState.Paused -> awaitResumeOrAbort(epoch)
                // Unreachable under an unchanged epoch (see implementation
                // notes); abort rather than trusting that reasoning forever.
                else -> return false
            }
        }
        if (timelineEpoch != epoch) return false
        onFrame(targetMs)
        return true
    }

    /**
     * Runs [animation] until it completes on its own or a control change
     * (epoch, play/pause, speed) makes its tween stale, in which case only the
     * animation job is cancelled — the caller's loop decides what to do next.
     * Cancellation stops the Animatable at its current value, preserving the
     * resume cursor.
     */
    private suspend fun raceAgainstControls(
        epoch: Int,
        speed: Float,
        animation: suspend () -> Unit,
    ) {
        coroutineScope {
            val animationJob = launch { animation() }
            val interruptJob = launch {
                snapshotFlow {
                    timelineEpoch != epoch ||
                        playbackState != PlaybackState.Playing ||
                        speedMultiplier != speed
                }.first { it }
                animationJob.cancel()
            }
            animationJob.join()
            interruptJob.cancel()
        }
    }

    /** Parks while Paused; wakes on resume, replay, or reset (epoch change). */
    private suspend fun awaitResumeOrAbort(epoch: Int) {
        snapshotFlow { timelineEpoch != epoch || playbackState != PlaybackState.Paused }
            .first { it }
    }

    /** Maps a 1x timeline position to whole-character [progress]. */
    private fun cycleProgress(elapsed1xMs: Float): Float =
        if (totalCycleMs <= 0f) 1f else (elapsed1xMs / totalCycleMs).coerceIn(0f, 1f)
}

/**
 * One stroke laid out on the 1x demonstration timeline.
 *
 * [startOffsetMs] is where the stroke's pen-down falls within the full cycle
 * (the preceding strokes' durations plus the pauses between them); [durationMs]
 * is the recorded `points.last().time` with the [MIN_STROKE_DURATION_MS] floor
 * applied to single-frame dots.
 */
private class TimelineStroke(
    val frames: List<StrokeFrame>,
    val durationMs: Long,
    val startOffsetMs: Long,
) {

    /**
     * The finished path, built once on first completion and reused verbatim on
     * every auto-repeat cycle — `completedPaths` entries must be stable objects.
     * [LazyThreadSafetyMode.NONE] because the engine is UI-thread-only.
     */
    val fullPath: Path by lazy(LazyThreadSafetyMode.NONE) { StrokeFramePath.fullPath(frames) }
}
