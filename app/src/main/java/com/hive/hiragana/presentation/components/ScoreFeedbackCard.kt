package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.scoring.CharacterScore
import com.hive.hiragana.presentation.theme.LocalKanaColors
import com.hive.hiragana.domain.scoring.ScoringMode

/**
 * Compact, non-blocking handwriting-quality readout shown after a graded
 * character: the headline score in a quality-band color, the mode's dial
 * label, and up to two short coaching tags. Purely presentational — it
 * takes a computed [CharacterScore] and renders it; all scoring lives in
 * the domain layer.
 *
 * Sizing: the card wraps its content but never exceeds the width its caller
 * allows — callers give it the canvas width minus a safe inset, and long
 * coaching tags WRAP inside that (the tag column is the row's one flexible
 * element) instead of stretching the card past the canvas edge.
 *
 * A small ⓘ opens the "How your writing is scored" explainer
 * ([ScoringInfoDialog]), so the numbers are never a mystery.
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
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val band = qualityBand(score.headline, LocalKanaColors.current.isDark)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = band.container,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
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
            // The one flexible element: takes what's left and wraps long
            // coaching tags inside it, so the card can't outgrow the width
            // the caller allows (fill = false keeps a short card compact).
            Column(modifier = Modifier.weight(1f, fill = false)) {
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
            IconButton(
                onClick = { showInfo = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "How your writing is scored",
                    tint = band.content,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (showInfo) {
        ScoringInfoDialog(onDismiss = { showInfo = false })
    }
}

/** Plain-language explainer for the handwriting score, opened from the card's ⓘ. */
@Composable
fun ScoringInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // Copy is deliberately tight so the whole dialog fits WITHOUT
        // scrolling on a 360×700dp phone (it needed a scroll before, which
        // read as broken for this little text); the scroll stays only as a
        // safety net for huge font scales.
        title = { Text("How scoring works") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoSection(
                    title = "Form",
                    body = "How close your shape is to the reference character. " +
                        "Size and position don't matter: only the shape counts, " +
                        "the way Japanese handwriting is actually graded.",
                )
                InfoSection(
                    title = "Technique",
                    body = "The coaching line under the score: the right number " +
                        "of strokes, in the right order and direction.",
                )
                InfoSection(
                    title = "Fit",
                    body = "How well you fill and centre the guide cell. Scored " +
                        "only in Fit or Both mode.",
                )
                InfoSection(
                    title = "Readability",
                    body = "Your ink is also checked by the device's handwriting " +
                        "recognizer: a shape that reads as a different character " +
                        "caps the score with “Looks more like …”.",
                )
                Text(
                    text = "Scores are advice only. They never lock or gate anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private data class QualityBand(val label: String, val container: Color, val content: Color)

/**
 * Score → verdict word + color band. Green rewards legible form; low scores
 * are gentle, not punitive. The dark pairs invert the value relationship —
 * deep containers with light content — at the same five hues.
 */
private fun qualityBand(score: Int, dark: Boolean): QualityBand = if (dark) {
    when {
        score >= 90 -> QualityBand("Excellent", Color(0xFF2C5531), Color(0xFFC0EBBB))
        score >= 75 -> QualityBand("Great", Color(0xFF344F33), Color(0xFFCBE8C0))
        score >= 60 -> QualityBand("Good", Color(0xFF574A21), Color(0xFFF4E3A5))
        score >= 40 -> QualityBand("Keep practicing", Color(0xFF5C4226), Color(0xFFF7D5AE))
        else -> QualityBand("Try again", Color(0xFF5D2B25), Color(0xFFF6B8B0))
    }
} else {
    when {
        score >= 90 -> QualityBand("Excellent", Color(0xFFCDECC9), Color(0xFF1B5E20))
        score >= 75 -> QualityBand("Great", Color(0xFFDCEFD6), Color(0xFF2E7D32))
        score >= 60 -> QualityBand("Good", Color(0xFFFDF1C7), Color(0xFF7A5B00))
        score >= 40 -> QualityBand("Keep practicing", Color(0xFFFBE0C6), Color(0xFF8A4B12))
        else -> QualityBand("Try again", Color(0xFFF6D2CE), Color(0xFF9B2C22))
    }
}

private fun dialSuffix(mode: ScoringMode): String = when (mode) {
    ScoringMode.FORM -> " · form"
    ScoringMode.FIT -> " · fit"
    ScoringMode.BOTH -> " · form + fit"
    ScoringMode.OFF -> ""
}
