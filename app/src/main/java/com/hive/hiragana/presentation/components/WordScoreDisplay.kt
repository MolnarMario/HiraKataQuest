package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.scoring.WordUnitScore
import com.hive.hiragana.presentation.theme.LocalKanaColors
import com.hive.hiragana.presentation.theme.scoreSpectrumColor

/**
 * A row of compact per-kana score chips shown above the canvas after a whole
 * word is submitted (1×1 practice) — one chip per scored reading unit, each
 * tinted on the red→green [scoreSpectrumColor] spectrum by its headline. Reads
 * left-to-right in the same order as the word's cells.
 *
 * The chips share the available width equally ([Modifier.weight]) so any number
 * of kana always fits on one row within the canvas — a 4+ kana word no longer
 * runs off the right edge. The caller supplies the horizontal insets from the
 * screen edges; the gaps between chips and the padding inside each chip are
 * kept here.
 */
@Composable
fun WordScoreChips(
    scores: List<WordUnitScore>,
    modifier: Modifier = Modifier,
) {
    val darkTint = LocalKanaColors.current.isDark
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        scores.forEach { unit ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = scoreSpectrumColor(unit.score.headline, darkTint),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = unit.glyph,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${unit.score.headline}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Coaching feedback (same tags as the single-character card):
                    // e.g. "Use 2 strokes (you drew 1)", "Looks more like X".
                    unit.score.tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The whole-word average score, shown below the canvas — a pill tinted by the
 * average on the same red→green spectrum as the per-kana chips.
 */
@Composable
fun WordAverageCard(
    average: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = scoreSpectrumColor(average, LocalKanaColors.current.isDark),
    ) {
        Row(
            // Vertical padding kept slim so the pill sits shorter than the
            // reserved footer strip — the strip centres it, leaving a clear
            // margin between the pill and the paper card's bottom edge.
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Average",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$average",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
