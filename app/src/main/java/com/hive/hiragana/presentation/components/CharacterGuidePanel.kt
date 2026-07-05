package com.hive.hiragana.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.VocabularyWord

/**
 * Top guide area (25% of the screen). Two modes:
 *
 * - **Character mode** ([character] non-null, [word] null): the current
 *   character with its romaji and (in free practice, when [total] > 0)
 *   deck position on the left; the animated stroke-order preview with
 *   playback controls on the right (falling back to the static numbered
 *   diagram inside [AnimatedStrokePreview] when there is no timed data).
 * - **Word mode** ([word] non-null): the whole word's kana, its English
 *   meaning — with a "New word!" prefix when [isNewWord] (a fresh unlock,
 *   not a reinforcement pick) — and a row of per-kana stroke-order previews
 *   ([wordCharacters], one demo per reading unit) that share ONE playback
 *   controls row (same controls a single character gets — see
 *   [WordStrokePreview]). The word is traced as one sheet, but each kana still
 *   gets its own "how to draw it" demo.
 *
 * [sessionLabel] renders a small progress line at the top (e.g.
 * "Review 2/5", "Practice 1/3") whenever non-null. A full-width controls
 * strip along the panel's bottom edge (either mode) holds the Library
 * entry, the 1×1/2×2/3×3 repetition-layout picker ([practiceLayout] +
 * [onSelectLayout]), the grid toggle, and the ghost-glyph toggle — in the
 * panel, not on the canvas, so the drawing surface keeps its full height.
 */
@Composable
fun CharacterGuidePanel(
    character: HiraganaCharacter?,
    position: Int,
    total: Int,
    modifier: Modifier = Modifier,
    sessionLabel: String? = null,
    word: VocabularyWord? = null,
    // The deck characters for the word's kana units, in order — drives the
    // per-kana stroke-order previews in word mode (empty in character mode).
    wordCharacters: List<HiraganaCharacter> = emptyList(),
    pronunciation: String? = null,
    isNewWord: Boolean = false,
    onOpenLibrary: (() -> Unit)? = null,
    isGridEnabled: Boolean = true,
    onToggleGrid: (() -> Unit)? = null,
    isGhostEnabled: Boolean = true,
    onToggleGhost: (() -> Unit)? = null,
    practiceLayout: PracticeLayout = PracticeLayout.SINGLE,
    onSelectLayout: ((PracticeLayout) -> Unit)? = null,
) {
    Box(modifier = modifier) {
        if (character == null && word == null) return@Box

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (sessionLabel != null) {
                        Text(
                            text = sessionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (word != null) {
                        // displaySmall leaves room for the per-kana stroke-order
                        // previews on the right while still reading large.
                        Text(
                            text = word.kana,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = meaningLine(
                                word = word,
                                pronunciation = pronunciation,
                                isNewWord = isNewWord,
                                badgeColor = MaterialTheme.colorScheme.primary,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (character != null) {
                        Text(
                            text = character.character,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = if (total > 0) {
                                "${character.romaji} · character $position of $total"
                            } else {
                                character.romaji
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (character != null && word == null) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Height-only constraint: the preview squares its own
                        // canvas internally, so the playback controls keep
                        // their full intrinsic width even when a short panel
                        // would make the square narrower than the four buttons.
                        AnimatedStrokePreview(
                            character = character,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                        )
                        Text(
                            text = "Stroke order · ${character.strokeCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (word != null && wordCharacters.isNotEmpty()) {
                    // Word mode: a stroke-order demo per kana sharing ONE
                    // playback-controls row (same controls a single character
                    // gets) — see WordStrokePreview.
                    WordStrokePreview(
                        characters = wordCharacters,
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .padding(4.dp),
                    )
                }
            }

            if (onOpenLibrary != null || onSelectLayout != null ||
                onToggleGrid != null || onToggleGhost != null
            ) {
                PanelControlsRow(
                    onOpenLibrary = onOpenLibrary,
                    practiceLayout = practiceLayout,
                    onSelectLayout = onSelectLayout,
                    isGridEnabled = isGridEnabled,
                    onToggleGrid = onToggleGrid,
                    isGhostEnabled = isGhostEnabled,
                    onToggleGhost = onToggleGhost,
                )
            }
        }
    }
}

/** Shared height of every control in [PanelControlsRow]. */
private val ControlHeight = 40.dp

/** Width of the grid/ghost toggle pills — wider than tall so they don't read as slivers. */
private val ToggleWidth = 44.dp

/** Gap between neighbouring controls in [PanelControlsRow]. */
private val ControlGap = 10.dp

/**
 * Full-width strip along the panel's bottom edge: Library entry on the
 * left; the 1×1/2×2/3×3 repetition-layout picker, the grid toggle, and the
 * ghost-glyph toggle on the right (all canvas settings, so they sit
 * together).
 *
 * Every control is [ControlHeight] tall and the gaps come from a single
 * spacedBy, so nothing can touch. The picker is the row's one flexible
 * element (weight, equal-width segments): it absorbs whatever width the
 * fixed controls leave over, which both stops it dictating the row's
 * width on narrow screens (it used to overflow and eat the spacing) and
 * keeps the strip looking proportional.
 */
@Composable
private fun PanelControlsRow(
    onOpenLibrary: (() -> Unit)?,
    practiceLayout: PracticeLayout,
    onSelectLayout: ((PracticeLayout) -> Unit)?,
    isGridEnabled: Boolean,
    onToggleGrid: (() -> Unit)?,
    isGhostEnabled: Boolean,
    onToggleGhost: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlGap),
    ) {
        if (onOpenLibrary != null) {
            OutlinedButton(
                onClick = onOpenLibrary,
                modifier = Modifier.height(ControlHeight),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Library")
            }
        }
        if (onSelectLayout != null) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .weight(1f)
                    .height(ControlHeight),
            ) {
                PracticeLayout.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == practiceLayout,
                        onClick = { onSelectLayout(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = PracticeLayout.entries.size,
                        ),
                        // No checkmark — the fill already marks selection,
                        // and the icon would widen the selected segment.
                        icon = {},
                        label = {
                            Text(
                                text = "${option.span}×${option.span}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onToggleGrid != null) {
            // Filled when the grid is on, neutral when off — same
            // at-a-glance pattern as the auto-repeat toggle in the
            // playback controls.
            FilledIconToggleButton(
                checked = isGridEnabled,
                onCheckedChange = { onToggleGrid() },
                modifier = Modifier
                    .width(ToggleWidth)
                    .height(ControlHeight),
            ) {
                val glyphColor = LocalContentColor.current
                Canvas(
                    modifier = Modifier.size(16.dp),
                    contentDescription = if (isGridEnabled) {
                        "Hide practice grid"
                    } else {
                        "Show practice grid"
                    },
                ) {
                    drawGridGlyph(glyphColor)
                }
            }
        }
        if (onToggleGhost != null) {
            // Same at-a-glance pattern as the grid toggle. The icon is a
            // small kana — the ghost is a rendered glyph, so text IS the
            // natural icon; no hand-drawing needed.
            FilledIconToggleButton(
                checked = isGhostEnabled,
                onCheckedChange = { onToggleGhost() },
                modifier = Modifier
                    .width(ToggleWidth)
                    .height(ControlHeight),
            ) {
                Text(
                    text = "あ",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = if (isGhostEnabled) {
                            "Hide character guide"
                        } else {
                            "Show character guide"
                        }
                    },
                )
            }
        }
    }
}

/**
 * A miniature practice cell — rounded square with a centre cross — drawn by
 * hand like the pause/repeat glyphs in the playback controls: the bundled
 * core material icon set has no grid glyph, and material-icons-extended is
 * not worth the dependency for one 16dp icon.
 */
private fun DrawScope.drawGridGlyph(color: Color) {
    val strokeWidth = size.minDimension * 0.1f
    val stroke = Stroke(width = strokeWidth)
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(strokeWidth * 1.5f, strokeWidth * 1.5f),
        style = stroke,
    )
    drawLine(
        color = color,
        start = Offset(size.width / 2f, strokeWidth * 1.8f),
        end = Offset(size.width / 2f, size.height - strokeWidth * 1.8f),
        strokeWidth = strokeWidth,
    )
    drawLine(
        color = color,
        start = Offset(strokeWidth * 1.8f, size.height / 2f),
        end = Offset(size.width - strokeWidth * 1.8f, size.height / 2f),
        strokeWidth = strokeWidth,
    )
}

/**
 * The word's meaning followed by its per-kana pronunciation (e.g.
 * "to buy · ka u"), prefixed with a highlighted "New word!" badge when
 * this session freshly unlocked it (docs/PHASE3-DESIGN.md §6) — one line,
 * because the 25% panel has no room for a separate badge row. The
 * pronunciation is bolded so the two parts read apart at a glance.
 */
private fun meaningLine(
    word: VocabularyWord,
    pronunciation: String?,
    isNewWord: Boolean,
    badgeColor: Color,
) = buildAnnotatedString {
    if (isNewWord) {
        withStyle(style = SpanStyle(color = badgeColor, fontWeight = FontWeight.Bold)) {
            append("New word! ")
        }
    }
    append(word.meaning)
    if (!pronunciation.isNullOrBlank()) {
        append(" · ")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(pronunciation)
        }
    }
}
