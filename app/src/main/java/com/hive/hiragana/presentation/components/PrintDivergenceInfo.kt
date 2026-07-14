package com.hive.hiragana.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hive.hiragana.R
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.PrintDivergence

/**
 * The "print differs" cue for kana whose printed and handwritten forms
 * genuinely diverge (see [PrintDivergence]): a small, always-present chip
 * shown next to the reading wherever such a character is on screen. Its mere
 * presence is the "take note" signal; tapping it opens a side-by-side
 * comparison in the style of the standard 活字体/手書き textbook chart: the
 * real typeset form next to a render of the character's KanjiVG (handwritten)
 * strokes, with a one-line note on what differs.
 *
 * The "In print" specimen renders in [PrintFontFamily] — NOT the system
 * font: Android's Noto Sans CJK JP draws all three kana in the detached,
 * handwriting-style form, so it demonstrates nothing (found on device — き
 * looked identical on both sides). The handwritten side is a render of the
 * character's own KanjiVG strokes, the same separate-stroke school form the
 * tracing ghost teaches.
 *
 * [unitCharacters] are the deck characters for the item's reading units (a
 * single character, or one per unit of a word); units whose forms agree are
 * filtered out, and the chip renders nothing when none diverge — callers can
 * pass unconditionally.
 */
@Composable
fun PrintDivergenceBadge(
    unitCharacters: List<HiraganaCharacter>,
    modifier: Modifier = Modifier,
) {
    val entries = remember(unitCharacters) {
        unitCharacters
            .mapNotNull { ch -> PrintDivergence.noteFor(ch.character)?.let { ch to it } }
            .distinctBy { it.first.character }
    }
    if (entries.isEmpty()) return

    // Keyed on the affected glyphs so advancing to a different card/character
    // closes a left-open dialog instead of silently swapping its content.
    var showDialog by rememberSaveable(entries.joinToString("") { it.first.character }) {
        mutableStateOf(false)
    }

    Surface(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Looks different in print",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }

    if (showDialog) {
        PrintDivergenceDialog(entries = entries, onDismiss = { showDialog = false })
    }
}

/** Side-by-side print vs handwritten comparison, one section per affected kana. */
@Composable
private fun PrintDivergenceDialog(
    entries: List<Pair<HiraganaCharacter, String>>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Print vs handwritten") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                entries.forEach { (character, note) ->
                    ComparisonSection(character = character, note = note)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

/** Square side of the two glyph specimens in a [ComparisonSection]. */
private val SpecimenSize = 72.dp

/**
 * The bundled "print forms" font (~3.4 KB, tools/fonts/): the
 * [PrintDivergence] kana + voiced/small companions, in gothic faces that
 * kept the traditional CONNECTED book forms — Kosugi (Apache 2.0) for
 * さ/ざ/き/ぎ + small ゃゅょ, IBM Plex Sans JP (OFL) for り, merged into one
 * file because no single free gothic face connects all three (verify any
 * candidate at 400px+ — small renders mislead; BIZ UDGothic fooled one).
 *
 * Applied to EVERY typed-glyph surface — the comparison dialog's "In print"
 * specimen, the flashcard front, the reading-quiz prompt, the Library's
 * rows/chart cells/word cards/selection views, and the writing guide panel
 * header — so the app consistently presents the connected book form as "the
 * printed form". Because the subset holds ONLY the divergent kana, every
 * other character falls back to the system font — applying this family to a
 * whole kana string swaps just さ/き/り (and their voiced/yōon variants).
 */
val PrintFontFamily = FontFamily(Font(R.font.print_forms))

@Composable
private fun ComparisonSection(character: HiraganaCharacter, note: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Specimen(label = "In print") {
                // Fixed dp square, so the glyph sizes in dp like an icon
                // (chrome-text rule); composites (きゃ) halve the per-glyph
                // size so both code points fit.
                val fontSize =
                    (56.dp / character.character.length.coerceAtLeast(1)).toNonScalingSp()
                Text(
                    text = character.character,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = PrintFontFamily,
                        fontSize = fontSize,
                        lineHeight = fontSize,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Specimen(label = "Handwritten") {
                StrokesGlyph(
                    strokes = character.strokes,
                    viewBox = character.viewBox,
                    modifier = Modifier.size(SpecimenSize - 8.dp),
                )
            }
        }
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
    }
}

/** A labelled glyph specimen: the form in a fixed square, its caption below. */
@Composable
private fun Specimen(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(SpecimenSize),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A glyph drawn from SVG stroke path strings as clean centerlines (no
 * stroke-order badges — this is a shape specimen, not a how-to-draw diagram;
 * the guide panel's preview teaches order).
 */
@Composable
private fun StrokesGlyph(
    strokes: List<String>,
    viewBox: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val paths = remember(strokes) {
        strokes.mapNotNull { pathData ->
            runCatching { PathParser().parsePathString(pathData).toPath() }.getOrNull()
        }
    }
    Canvas(modifier = modifier) {
        if (paths.isEmpty()) return@Canvas
        val scale = size.minDimension / viewBox
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            paths.forEach { path ->
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 3.dp.toPx() / scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}
