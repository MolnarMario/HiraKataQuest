package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.scoring.CharacterScore
import com.hive.hiragana.domain.scoring.ScoringMode

/**
 * Compact, non-blocking handwriting-quality readout shown after a graded
 * character: the headline score in a quality-band color, the mode's dial
 * label, and up to two short coaching tags. Purely presentational — it
 * takes a computed [CharacterScore] and renders it; all scoring lives in
 * the domain layer.
 *
 * Advisory by design (the plan's decision): this never changes the
 * self-grade or the review schedule, it just tells the learner how the
 * shape came out.
 */
@Composable
fun ScoreFeedbackCard(
    score: CharacterScore,
    modifier: Modifier = Modifier,
    glyph: String? = null,
) {
    val band = qualityBand(score.headline)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = band.container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The scored glyph, so the card can't be misread as judging the
            // character the canvas has since advanced to.
            if (glyph != null) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.headlineMedium,
                    color = band.content,
                )
            }
            Text(
                text = "${score.headline}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = band.content,
            )
            Column {
                Text(
                    text = band.label + dialSuffix(score.mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = band.content,
                )
                if (score.tags.isNotEmpty()) {
                    Text(
                        text = score.tags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = band.content,
                    )
                }
            }
        }
    }
}

private data class QualityBand(val label: String, val container: Color, val content: Color)

/** Score → verdict word + color band. Green rewards legible form; low scores are gentle, not punitive. */
private fun qualityBand(score: Int): QualityBand = when {
    score >= 90 -> QualityBand("Excellent", Color(0xFFCDECC9), Color(0xFF1B5E20))
    score >= 75 -> QualityBand("Great", Color(0xFFDCEFD6), Color(0xFF2E7D32))
    score >= 60 -> QualityBand("Good", Color(0xFFFDF1C7), Color(0xFF7A5B00))
    score >= 40 -> QualityBand("Keep practicing", Color(0xFFFBE0C6), Color(0xFF8A4B12))
    else -> QualityBand("Try again", Color(0xFFF6D2CE), Color(0xFF9B2C22))
}

private fun dialSuffix(mode: ScoringMode): String = when (mode) {
    ScoringMode.FORM -> " · form"
    ScoringMode.FIT -> " · fit"
    ScoringMode.BOTH -> " · form + fit"
    ScoringMode.OFF -> ""
}
