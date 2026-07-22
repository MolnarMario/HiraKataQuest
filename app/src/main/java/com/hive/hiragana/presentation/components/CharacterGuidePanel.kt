package com.hive.hiragana.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.VocabularyWord

/**
 * Top guide area (25% of the screen). Two modes:
 *
 * - **Character mode** ([character] non-null, [word] null): the current
 *   character with its romaji, a pronunciation button, and (if provided) the
 *   [positionLabel] (e.g. "character 12 of 73") on the left; the animated
 *   stroke-order preview with playback controls on the right (falling back to
 *   the static numbered diagram inside [AnimatedStrokePreview] when there is
 *   no timed data).
 * - **Word mode** ([word] non-null): the whole word's kana, its English
 *   meaning + pronunciation, its [positionLabel] (e.g. "word 20 of 84"), and a
 *   row of per-kana stroke-order previews ([wordCharacters],
 *   one demo per reading unit) that share ONE playback controls row (same
 *   controls a single character gets — see [WordStrokePreview]). The word is
 *   traced as one sheet, but each kana still gets its own "how to draw it" demo.
 *
 * A full-width controls strip along the panel's bottom edge (either mode)
 * holds the Library entry, the 1×1/2×2/3×3 repetition-layout picker
 * ([practiceLayout] + [onSelectLayout]), the grid toggle, and the ghost-glyph
 * toggle — in the panel, not on the canvas, so the drawing surface keeps its
 * full height.
 */
@Composable
fun CharacterGuidePanel(
    character: HiraganaCharacter?,
    // e.g. "character 12 of 73" / "word 20 of 84" — the item's position in the
    // active script's ordered deck (characters) or word list. Null hides it.
    positionLabel: String? = null,
    modifier: Modifier = Modifier,
    word: VocabularyWord? = null,
    // The deck characters for the word's kana units, in order — drives the
    // per-kana stroke-order previews in word mode (empty in character mode).
    wordCharacters: List<HiraganaCharacter> = emptyList(),
    pronunciation: String? = null,
    onOpenLibrary: (() -> Unit)? = null,
    isGridEnabled: Boolean = true,
    onToggleGrid: (() -> Unit)? = null,
    isGhostEnabled: Boolean = true,
    onToggleGhost: (() -> Unit)? = null,
    practiceLayout: PracticeLayout = PracticeLayout.SINGLE,
    onSelectLayout: ((PracticeLayout) -> Unit)? = null,
    // Pronunciation (device TTS). Null hides the button.
    onSpeak: ((String) -> Unit)? = null,
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
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
                // The left column's stack must NEVER exceed the panel height —
                // it used to clip on compact screens (a 360dp-wide device
                // gives the 25% panel ~110dp: the position line and the
                // divergence ⓘ vanished under the controls row). Structure
                // guarantees it now: the FIXED rows (reading/meaning line,
                // position line) measure first, and the glyph sits in a
                // weight(1f) box that receives exactly the leftover height,
                // deriving its font size from that box — so no device metric
                // can push the rows out. The ⓘ divergence badge lives ON the
                // reading row (a second line for it was the first casualty).
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    GlyphLeftoverBox(
                        text = if (word != null) word.kana else character?.character.orEmpty(),
                        // displaySmall(36) for words, displayMedium(45) for
                        // single characters — the caps reproduce the old look
                        // on tall screens.
                        maxFontSize = if (word != null) 36f else 45f,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (word != null) {
                            Text(
                                text = meaningLine(
                                    word = word,
                                    pronunciation = pronunciation,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        } else {
                            Text(
                                text = character?.romaji.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val speakGlyph = word?.kana ?: character?.character
                        if (onSpeak != null && speakGlyph != null) {
                            PronunciationButton(
                                glyph = speakGlyph,
                                status = ttsStatus,
                                onSpeak = onSpeak,
                            )
                        }
                    }
                    if (positionLabel != null) {
                        Text(
                            text = positionLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // NOTE: the "Looks different in print" chip deliberately
                    // does NOT live in this panel — no row fits on compact
                    // screens without costing the glyph or the position line.
                    // In free practice it floats over the paper card's bottom
                    // margin instead (PracticeScreen), which is free space on
                    // every device because the writing grid is a width-bound
                    // square.
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

/**
 * The guide panel's headline glyph, sized to the height this box actually
 * receives (the caller gives it the leftover after the fixed rows measure,
 * via weight) and shrunk to the width for long words. A CJK line renders
 * ~1.35× its font size, so the size derives from height / 1.35, capped at
 * [maxFontSize] so tall screens keep the old display-type look.
 * PrintFontFamily keeps さ/き/り consistent with the Library and Reading
 * track — the printed reference form, while the traced ghost teaches the
 * handwritten one.
 */
@Composable
private fun GlyphLeftoverBox(
    text: String,
    maxFontSize: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val fontSize = (maxHeight.value / 1.35f).coerceIn(18f, maxFontSize)
        AutoShrinkText(
            text = text,
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = PrintFontFamily,
                fontSize = fontSize.dp.toNonScalingSp(),
                lineHeight = (fontSize * 1.35f).dp.toNonScalingSp(),
            ),
            color = MaterialTheme.colorScheme.onBackground,
            minFontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterStart),
        )
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
) = BoxWithConstraints(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
) {
    // On compact widths (a 360dp device leaves this strip ~320dp) the
    // labelled Library button starved the layout picker until its segments
    // clipped to "1x 2x 3x" — below the threshold it collapses to icon-only,
    // which more than pays for the picker's full labels.
    val compact = maxWidth < 350.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                    contentDescription = if (compact) "Library" else null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                if (!compact) {
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    // Shrinks at large font scales so the button can't eat the
                    // width the layout picker needs (see ResponsiveText policy).
                    AutoShrinkText(
                        text = "Library",
                        modifier = Modifier.widthIn(max = 96.dp),
                        style = MaterialTheme.typography.labelLarge,
                        minFontSize = 11.sp,
                    )
                }
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
                            // Chrome text: dp-sized so the label fits the fixed
                            // 40dp strip at any font scale, a notch smaller on
                            // compact widths so "1×1" never clips to "1x".
                            // NOTE: must stay a plain Text — SegmentedButtonRow
                            // measures labels with INTRINSICS, and anything
                            // BoxWithConstraints-based (AutoShrinkText) crashes
                            // with "Asking for intrinsic measurements of
                            // SubcomposeLayout" (crashed on device).
                            Text(
                                text = "${option.span}×${option.span}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = (if (compact) 10.dp else 12.dp)
                                        .toNonScalingSp(),
                                    lineHeight = 16.dp.toNonScalingSp(),
                                ),
                                maxLines = 1,
                                softWrap = false,
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
                    // Chrome text: the kana IS the icon, so it sizes in dp
                    // like one — the 44×40 pill can't grow with font scale.
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.dp.toNonScalingSp(),
                        lineHeight = 20.dp.toNonScalingSp(),
                    ),
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
 * "to buy · ka u") — one line, because the 25% panel has no room for more.
 * The pronunciation is bolded so the two parts read apart at a glance.
 */
private fun meaningLine(
    word: VocabularyWord,
    pronunciation: String?,
) = buildAnnotatedString {
    append(word.meaning)
    if (!pronunciation.isNullOrBlank()) {
        append(" · ")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(pronunciation)
        }
    }
}
