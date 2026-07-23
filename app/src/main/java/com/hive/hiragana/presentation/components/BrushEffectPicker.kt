package com.hive.hiragana.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hive.hiragana.drawing.canvas.BrushEffect
import com.hive.hiragana.drawing.canvas.StrokeRenderer
import com.hive.hiragana.drawing.stroke.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A brush-effect picker dialog: one row per [BrushEffect] with a live rendered
 * stroke sample beside its name (drawn with the same [StrokeRenderer] the canvas
 * uses, so the preview is faithful), tinted with the current [inkColor]. Tapping
 * a row selects that effect and closes; the current one is highlighted. No new
 * dependency — just Compose Canvas.
 */
@Composable
fun BrushEffectPicker(
    selected: BrushEffect,
    inkColor: Color,
    onPick: (BrushEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.width(320.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Brush effect",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                BrushEffect.entries.forEach { effect ->
                    BrushEffectRow(
                        effect = effect,
                        inkColor = inkColor,
                        selected = effect == selected,
                        onClick = { onPick(effect) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun BrushEffectRow(
    effect: BrushEffect,
    inkColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = effect.displayLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            modifier = Modifier.width(120.dp),
        )
        BrushSample(
            effect = effect,
            inkColor = inkColor,
            modifier = Modifier.weight(1f).height(40.dp),
        )
    }
}

/**
 * Draws one representative stroke for [effect]: a gentle wave whose timing makes
 * the ends slow and the middle fast, so a speed-varying effect (calligraphy)
 * visibly swells and thins. Fed through [StrokeRenderer.buildBrushPath] with the
 * effect's own width profile + cap, then filled at the effect's alpha.
 */
@Composable
private fun BrushSample(effect: BrushEffect, inkColor: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val marginX = w * 0.06f
        val centerY = h / 2f
        val amplitude = h * 0.24f
        val n = 28
        val points = ArrayList<StrokePoint>(n)
        var timeMs = 0L
        for (i in 0 until n) {
            val u = i.toFloat() / (n - 1)
            val x = marginX + u * (w - 2 * marginX)
            val y = centerY + amplitude * sin(u * 1.6 * PI).toFloat()
            points.add(StrokePoint(x = x, y = y, pressure = 1f, timestamp = timeMs))
            // dt: slow (thick) at the ends, fast (thin) through the middle.
            val fastness = 1f - abs(2f * u - 1f)
            val dt = (28f - 22f * fastness).toLong().coerceAtLeast(1L)
            timeMs += dt
        }
        val spec = effect.spec
        val path = StrokeRenderer.buildBrushPath(
            points = points,
            baseWidthPx = h * 0.32f * spec.widthScale,
            profile = spec.widthProfile,
            cap = spec.cap,
        )
        drawPath(path = path, color = inkColor, alpha = spec.alpha)
    }
}
