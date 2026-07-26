package com.hive.hiragana.presentation.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.presentation.theme.LocalKanaColors

private data class ParsedStroke(val path: Path, val start: Offset)

/**
 * Static stroke-order guide: every stroke of the character rendered from
 * its SVG path, with a numbered badge at each stroke's starting point.
 */
@Composable
fun StrokeOrderDiagram(
    character: HiraganaCharacter,
    modifier: Modifier = Modifier,
    strokeColor: Color = LocalKanaColors.current.diagramStroke,
) {
    val badgeColor = MaterialTheme.colorScheme.primary

    val parsedStrokes = remember(character.character) {
        character.strokes.mapNotNull { pathData ->
            runCatching {
                val parser = PathParser().parsePathString(pathData)
                val start = parser.toNodes()
                    .filterIsInstance<PathNode.MoveTo>()
                    .firstOrNull()
                    ?.let { Offset(it.x, it.y) }
                    ?: Offset.Zero
                ParsedStroke(path = parser.toPath(), start = start)
            }.getOrNull()
        }
    }

    // The numeral sits ON the primary-colored badge disc, so it's on-primary
    // (== PaperWhite in light: zero visual change; a dark-appropriate dark in
    // dark, where the primary disc is light). Keyed so a flip rebuilds it.
    val badgeTextColor = MaterialTheme.colorScheme.onPrimary
    val badgeTextPaint = remember(badgeTextColor) {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = badgeTextColor.toArgb()
            isFakeBoldText = true
        }
    }

    Canvas(modifier = modifier) {
        if (parsedStrokes.isEmpty()) return@Canvas
        val scale = size.minDimension / character.viewBox

        // Strokes, uniformly scaled from the viewBox to the drawn size.
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            parsedStrokes.forEach { stroke ->
                drawPath(
                    path = stroke.path,
                    color = strokeColor,
                    style = Stroke(
                        width = 3.5.dp.toPx() / scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        // Numbered badges at each stroke's starting point.
        val badgeRadius = size.minDimension * 0.075f
        badgeTextPaint.textSize = badgeRadius * 1.35f
        parsedStrokes.forEachIndexed { index, stroke ->
            val center = Offset(stroke.start.x * scale, stroke.start.y * scale)
            drawCircle(color = badgeColor, radius = badgeRadius, center = center)
            val metrics = badgeTextPaint.fontMetrics
            val baseline = center.y - (metrics.ascent + metrics.descent) / 2f
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText("${index + 1}", center.x, baseline, badgeTextPaint)
            }
        }
    }
}
