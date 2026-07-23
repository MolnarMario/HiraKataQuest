package com.hive.hiragana.presentation.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hive.hiragana.presentation.theme.InkBlack

/**
 * One-tap preset swatches: black (the default ink) first, then one of each main
 * rainbow colour. Rendered as equal-width circles that always fill the row, so
 * the whole set fits perfectly regardless of how many there are.
 */
private val PRESET_SWATCHES = listOf(
    InkBlack,          // default ink
    Color(0xFFE53935), // red
    Color(0xFFFB8C00), // orange
    Color(0xFFFBC02D), // yellow
    Color(0xFF43A047), // green
    Color(0xFF1E88E5), // blue
    Color(0xFF3949AB), // indigo
    Color(0xFF8E24AA), // violet
)

/**
 * A compact HSV brush-color picker in a dialog — a saturation/value square, a
 * hue slider, quick preset swatches, and a live preview of the ink on white so
 * the learner can judge legibility before committing. All local while picking;
 * [onPick] fires once on "Done" (Cancel discards). Built from Compose Canvas +
 * `Color.hsv` + `android.graphics.Color.colorToHSV`, so no color-picker
 * dependency is added.
 */
@Composable
fun BrushColorPicker(
    initial: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seed HSV from the current color. Kept as three floats so the SV panel and
    // hue slider can move independently (ARGB would lose hue at value 0/sat 0).
    val seed = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(seed[0]) }        // 0..360
    var sat by remember { mutableFloatStateOf(seed[1]) }        // 0..1
    var value by remember { mutableFloatStateOf(seed[2]) }      // 0..1
    val current = Color.hsv(hue, sat, value)
    val outline = MaterialTheme.colorScheme.outline

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.width(300.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Brush color", style = MaterialTheme.typography.titleLarge)

                SaturationValuePanel(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { s, v -> sat = s; value = v },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .clip(RoundedCornerShape(12.dp)),
                )

                HueSlider(
                    hue = hue,
                    onChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(CircleShape),
                )

                // Preset shortcuts: black (default) + one of each main rainbow
                // colour. Each takes an equal share of the row width (weight),
                // so the whole set always fits and every swatch is the same size.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PRESET_SWATCHES.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, outline, CircleShape)
                                .clickable {
                                    val hsv = FloatArray(3).also { AndroidColor.colorToHSV(swatch.toArgb(), it) }
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onPick(current) }) { Text("Done") }
                }
            }
        }
    }
}

/**
 * The 2-D saturation (x) × value (y) square for the current [hue]. A single
 * gesture handler (down + drag) sets both from the touch position; the thumb
 * ring marks the current point.
 */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (sat: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun report(pos: Offset) {
                    val s = (pos.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                    onChange(s, v)
                }
                report(down.position)
                down.consume()
                drag(down.id) { change ->
                    report(change.position)
                    change.consume()
                }
            }
        },
    ) {
        // Saturation left→right (white → full hue), then value top→bottom
        // (unchanged → black) laid over it: the classic SV square.
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = (sat * size.width).coerceIn(0f, size.width)
        val cy = ((1f - value) * size.height).coerceIn(0f, size.height)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
    }
}

/** Horizontal rainbow hue slider (0..360°) with a thumb at the current hue. */
@Composable
private fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueStops = remember {
        List(7) { i -> Color.hsv(i * 60f, 1f, 1f) } // red→…→red
    }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun report(x: Float) = onChange((x / size.width).coerceIn(0f, 1f) * 360f)
                report(down.position.x)
                down.consume()
                drag(down.id) { change ->
                    report(change.position.x)
                    change.consume()
                }
            }
        },
    ) {
        drawRect(Brush.horizontalGradient(hueStops))
        val cx = (hue / 360f * size.width).coerceIn(0f, size.width)
        val cy = size.height / 2f
        drawCircle(Color.White, radius = size.height / 2f - 2.dp.toPx(), center = Offset(cx, cy), style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = size.height / 2f - 2.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
    }
}
