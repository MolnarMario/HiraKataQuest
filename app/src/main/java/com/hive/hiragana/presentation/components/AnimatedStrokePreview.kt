package com.hive.hiragana.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.drawing.animation.PlaybackState
import com.hive.hiragana.drawing.animation.StrokeAnimationEngine
import com.hive.hiragana.presentation.theme.DiagramStroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Visual size of each control button; kept small so four fit in the 25% guide panel. */
private val ControlButtonSize = 32.dp

/** The faint full-character guide the animation draws on top of. */
private val GuideStrokeColor = DiagramStroke.copy(alpha = 0.25f)

/** Pen-tip dot radius as a fraction of the canvas edge (viewBox-independent). */
private const val PenDotRadiusFraction = 0.035f

/**
 * The speed steps the cycle button walks through, in tap order. All values
 * are inside the engine's 0.25..4 clamp range, so `setSpeed` never rounds
 * them and `speedMultiplier` reads back exactly what was set.
 */
private val SpeedCycle = listOf(0.5f, 1f, 2f)

/**
 * Animated stroke-order demonstration for the guide panel (Phase 2).
 *
 * Shows the character's strokes being written in order with the recorded
 * rhythm: a faint full-character guide underneath, already-finished and
 * in-progress strokes on top in the diagram color, and a pen-tip dot that
 * disappears between strokes — the visible pen lift. A compact controls
 * row (play/pause, replay, speed cycle, auto-repeat) sits under the canvas.
 *
 * Contract points (docs/PHASE2-DESIGN.md §3):
 * - **Fallback**: when [HiraganaCharacter.strokeAnimations] is empty the
 *   composable renders the static [StrokeOrderDiagram] with the same
 *   [modifier] (squared, since the diagram has no controls row and fills
 *   its square exactly as it did in Phase 1).
 * - **Layout**: callers pass a height constraint only; the square lives on
 *   the preview canvas itself, not the component. This keeps the controls
 *   row at its intrinsic width (four [ControlButtonSize] buttons) instead
 *   of crushing trailing buttons to zero width when a short guide panel
 *   yields a square narrower than the row.
 * - **Engine lifecycle**: one [StrokeAnimationEngine] per character,
 *   remembered on [HiraganaCharacter.character]; a character change swaps
 *   in a fresh engine, which cancels the old driver coroutine and — when
 *   [autoPlay] — starts the new demonstration immediately. `run()` and the
 *   auto-play `play()` live in separate [LaunchedEffect]s because `run()`
 *   suspends forever: sequencing them in one effect would never reach
 *   `play()`.
 * - **Input transparency**: nothing here installs a pointer handler except
 *   the control buttons themselves, so tracing on the canvas below is
 *   never blocked while the preview plays.
 *
 * Drawing happens in viewBox space, uniformly scaled to the canvas exactly
 * like [StrokeOrderDiagram] (stroke width divided by the scale so it stays
 * constant in dp), which keeps the animated preview and the static diagram
 * visually interchangeable.
 */
@Composable
fun AnimatedStrokePreview(
    character: HiraganaCharacter,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
) {
    if (character.strokeAnimations.isEmpty()) {
        StrokeOrderDiagram(character = character, modifier = modifier.aspectRatio(1f))
        return
    }

    val engine = remember(character.character) {
        StrokeAnimationEngine(character.strokeAnimations)
    }
    LaunchedEffect(engine) { engine.run() }
    LaunchedEffect(engine) { if (autoPlay) engine.play() }

    // The static guide geometry never changes for a given character, so it
    // is parsed once per character, not per frame. Unparseable strokes are
    // skipped (same tolerance as StrokeOrderDiagram) — the animation still
    // plays from the timed data.
    val guidePaths = remember(character.character) {
        character.strokes.mapNotNull { pathData ->
            runCatching { PathParser().parsePathString(pathData).toPath() }.getOrNull()
        }
    }
    val penColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
        ) {
            val scale = size.minDimension / character.viewBox
            val strokeStyle = Stroke(
                width = 3.5.dp.toPx() / scale,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            // Engine outputs are snapshot state: reading them here makes the
            // canvas redraw (not recompose) on every animation frame.
            withTransform({ scale(scale, scale, Offset.Zero) }) {
                guidePaths.forEach { path ->
                    drawPath(path = path, color = GuideStrokeColor, style = strokeStyle)
                }
                engine.completedPaths.forEach { path ->
                    drawPath(path = path, color = DiagramStroke, style = strokeStyle)
                }
                engine.activePath?.let { path ->
                    drawPath(path = path, color = DiagramStroke, style = strokeStyle)
                }
            }

            // Pen tip, hidden while the pen is lifted (penPosition == null) —
            // its disappearing between strokes is what makes lifts visible.
            engine.penPosition?.let { pen ->
                drawCircle(
                    color = penColor,
                    radius = size.minDimension * PenDotRadiusFraction,
                    center = Offset(pen.x * scale, pen.y * scale),
                )
            }
        }

        PlaybackControls(engine = engine)
    }
}

/**
 * Compact playback controls: play/pause toggle, replay, speed cycle
 * (0.5× → 1× → 2×), auto-repeat toggle.
 *
 * All handlers are the engine's non-suspend control methods — they only
 * mutate desired state; the engine's single `run()` driver does the actual
 * suspend work. Pause and repeat glyphs are drawn by hand because those
 * icons are not in the bundled core material icon set and pulling in
 * material-icons-extended for two glyphs is not worth the dependency.
 */
@Composable
private fun PlaybackControls(
    engine: StrokeAnimationEngine,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val playing = engine.playbackState == PlaybackState.Playing

        IconButton(
            onClick = { if (playing) engine.pause() else engine.play() },
            modifier = Modifier.size(ControlButtonSize),
        ) {
            if (playing) {
                val glyphColor = LocalContentColor.current
                Canvas(
                    modifier = Modifier.size(14.dp),
                    contentDescription = "Pause",
                ) {
                    drawPauseGlyph(glyphColor)
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                )
            }
        }

        IconButton(
            onClick = engine::replay,
            modifier = Modifier.size(ControlButtonSize),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Replay",
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(
            onClick = { engine.setSpeed(nextSpeed(engine.speedMultiplier)) },
            modifier = Modifier.size(ControlButtonSize),
        ) {
            Text(
                text = speedLabel(engine.speedMultiplier),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }

        // Filled toggle so on/off reads at a glance: checked fills the
        // button with the primary color, unchecked stays a neutral disc.
        FilledIconToggleButton(
            checked = engine.autoRepeat,
            onCheckedChange = engine::setAutoRepeat,
            modifier = Modifier.size(ControlButtonSize),
        ) {
            val glyphColor = LocalContentColor.current
            Canvas(
                modifier = Modifier.size(16.dp),
                contentDescription = if (engine.autoRepeat) "Auto-repeat on" else "Auto-repeat off",
            ) {
                drawRepeatGlyph(glyphColor)
            }
        }
    }
}

/**
 * Next step in [SpeedCycle] after [current]. An unknown value (possible
 * only if some future caller sets a speed outside the cycle) restarts the
 * cycle at its first step rather than throwing.
 */
private fun nextSpeed(current: Float): Float {
    val index = SpeedCycle.indexOf(current)
    return SpeedCycle[(index + 1) % SpeedCycle.size]
}

/** "1×"/"2×" for whole multipliers, "0.5×"-style otherwise. */
private fun speedLabel(multiplier: Float): String =
    if (multiplier % 1f == 0f) "${multiplier.toInt()}×" else "${multiplier}×"

/** Two rounded vertical bars — the classic pause glyph. */
private fun DrawScope.drawPauseGlyph(color: Color) {
    val barWidth = size.width * 0.28f
    val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.14f, 0f),
        size = Size(barWidth, size.height),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.58f, 0f),
        size = Size(barWidth, size.height),
        cornerRadius = corner,
    )
}

/** A near-full circular arc with an arrowhead at its open end — a loop/repeat glyph. */
private fun DrawScope.drawRepeatGlyph(color: Color) {
    val strokeWidth = size.minDimension * 0.12f
    val inset = strokeWidth
    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
    val startAngle = -60f
    val sweepAngle = 285f
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )

    // Arrowhead at the arc's end, pointing clockwise (the sweep direction).
    val endRad = ((startAngle + sweepAngle) * PI / 180.0).toFloat()
    val radius = arcSize.minDimension / 2f
    val tip = center + Offset(cos(endRad), sin(endRad)) * radius
    val tangent = Offset(-sin(endRad), cos(endRad))
    val normal = Offset(cos(endRad), sin(endRad))
    val head = size.minDimension * 0.24f
    val arrow = Path().apply {
        moveTo(tip.x + tangent.x * head, tip.y + tangent.y * head)
        lineTo(tip.x + normal.x * head * 0.55f, tip.y + normal.y * head * 0.55f)
        lineTo(tip.x - normal.x * head * 0.55f, tip.y - normal.y * head * 0.55f)
        close()
    }
    drawPath(path = arrow, color = color)
}
