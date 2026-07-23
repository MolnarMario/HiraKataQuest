package com.hive.hiragana.presentation.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.hive.hiragana.R
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.RomajiMatch
import com.hive.hiragana.presentation.components.PrintDivergenceBadge
import com.hive.hiragana.presentation.components.PronunciationButton
import com.hive.hiragana.presentation.viewmodels.ReadingFlashcardState
import kotlin.random.Random

/**
 * Reading-track flashcard study: flip through the scoped deck/words. The front
 * shows the printed (gothic) glyph/word.
 *
 * A **Typing | Multiple choice segmented control at the top** switches between
 * two equal study styles, flippable at any time:
 * - **Multiple choice** — tap the reading among deck distractors; the card
 *   reveals Correct!/Not quite + the answer, then Next advances.
 * - **Typing** — productive recall: a romaji field; type + Check (or the
 *   keyboard's Done) reveals the same, compared with the lenient [RomajiMatch].
 *
 * Pure study — answers are deliberately NOT recorded into the reading-quiz
 * percentages; only the reading quiz writes `reading_progress`.
 *
 * Layout: the card fits its height budget BY CONSTRUCTION — the reveal rows
 * measure first and the glyph takes the actual leftover (see the card-area
 * comment and [FlashcardGlyph]) — so nothing clips when the keyboard shrinks
 * the window (adjustResize; on API 30+ the IME arrives via PracticeScreen's
 * safeDrawing inset host instead). The field keeps focus across card flips,
 * so the keyboard stays open for a smooth run; it only closes when switching
 * to Multiple choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingFlashcardScreen(
    state: ReadingFlashcardState,
    onReveal: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onSpeak: (String) -> Unit,
    ttsStatus: VoiceStatus,
    // Deck characters for a kana string's reading units — feeds the
    // "print differs" badge on the reveal (it needs strokes to render the
    // handwritten form next to the printed front).
    unitCharactersFor: (String) -> List<HiraganaCharacter> = { emptyList() },
    modifier: Modifier = Modifier,
) {
    val card = state.current ?: return
    var typingMode by rememberSaveable { mutableStateOf(false) }
    var typed by remember(state.index) { mutableStateOf("") }
    var typedCorrect by remember(state.index) { mutableStateOf<Boolean?>(null) }
    // Multiple-choice mode: whether the tapped reading was right, plus this card's
    // options (correct + up to 3 deck distractors, fixed per card).
    var choiceResult by remember(state.index) { mutableStateOf<Boolean?>(null) }
    val choiceOptions = remember(state.index, state.cards) {
        readingChoiceOptions(card.romaji, state.cards.map { it.romaji }, state.index)
    }
    // Whether the current card was answered correctly, in whichever mode is active
    // (drives the shared Correct!/Not quite reveal line).
    val answeredCorrect = if (typingMode) typedCorrect else choiceResult
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Keyboard follows the toggle: opens when typing is turned on, closes when
    // it's turned off. Focus is never cleared on Next, so it stays open across
    // cards for a continuous flow.
    LaunchedEffect(typingMode) {
        if (typingMode) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    fun check() {
        typedCorrect = RomajiMatch.matches(typed, card.romaji)
        onReveal()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The activity is windowSoftInputMode=adjustResize, so the window shrinks
        // to sit above the keyboard — the content lays out in the reduced height
        // (card shrinks, field + button stay just above the keys), no panning.
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close flashcards")
                }
                Text(
                    text = "Card ${state.index + 1} of ${state.cards.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // Two EQUAL study methods, so a segmented control — not an on/off
            // switch, which would frame one as the default and the other as an
            // "enabled" extra. The selected half is filled; neither is subordinate.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = typingMode,
                    onClick = { typingMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        // Checkmark when active (clear "selected" cue); the mode's
                        // own icon when not.
                        SegmentedButtonDefaults.Icon(
                            active = typingMode,
                            inactiveContent = {
                                Icon(
                                    imageVector = Icons.Filled.Create,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            },
                        )
                    },
                    label = {
                        Text(
                            "Typing",
                            fontWeight = if (typingMode) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                )
                SegmentedButton(
                    selected = !typingMode,
                    onClick = { typingMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(
                            active = !typingMode,
                            inactiveContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            },
                        )
                    },
                    label = {
                        Text(
                            "Multiple choice",
                            fontWeight = if (!typingMode) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                )
            }

            // Card area — the WHOLE card (glyph + the revealed reading, audio,
            // and meaning) must fit the available height, keyboard up or not.
            // The reveal rows below the glyph measure FIRST (they are the
            // fixed-height content) and the glyph derives its font size from
            // the height actually left over — the same leftover-box approach
            // as the guide panel, so neither end can clip by construction.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val avail = maxHeight
                val vPad = when {
                    avail >= 360.dp -> 32.dp
                    avail >= 240.dp -> 20.dp
                    else -> 10.dp
                }
                val gap = when {
                    avail >= 320.dp -> 16.dp
                    avail >= 240.dp -> 10.dp
                    else -> 8.dp
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = avail),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = vPad, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            // weight(1f) measures LAST, so this box receives
                            // exactly the height the reveal rows leave over;
                            // fill = false lets the card stay wrap-height when
                            // the glyph caps out below the leftover.
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                contentAlignment = Alignment.Center,
                            ) {
                                FlashcardGlyph(
                                    glyph = card.glyph,
                                    boxWidth = this.maxWidth,
                                    boxHeight = this.maxHeight,
                                )
                            }
                            if (state.revealed) {
                                if (answeredCorrect != null) {
                                    Text(
                                        text = if (answeredCorrect == true) "Correct!" else "Not quite",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (answeredCorrect == true) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                                // Reading · audio · meaning on ONE line; wraps only if
                                // it truly can't fit (responsive on narrow screens).
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        8.dp,
                                        Alignment.CenterHorizontally,
                                    ),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    // Romaji + speaker are ONE unbreakable child:
                                    // FlowRow measures each child against the
                                    // REMAINING line width, and a squeezed Text
                                    // wraps internally every couple of characters
                                    // (the mangled reveal seen on device) instead
                                    // of moving to the next line whole.
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = card.romaji,
                                            style = MaterialTheme.typography.headlineSmall,
                                        )
                                        PronunciationButton(
                                            glyph = card.glyph,
                                            status = ttsStatus,
                                            onSpeak = onSpeak,
                                        )
                                    }
                                    card.meaning?.let { meaning ->
                                        Text(
                                            text = "·  $meaning",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            // The min width forces a squeezed meaning
                                            // onto its own full line instead of
                                            // wrapping in the leftover sliver.
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .widthIn(min = 120.dp),
                                        )
                                    }
                                }
                                // Reading track shows the printed form, so warn when
                                // the handwritten one looks different (renders
                                // nothing for unaffected kana).
                                PrintDivergenceBadge(
                                    unitCharacters = unitCharactersFor(card.glyph),
                                    label = "Looks different in handwriting",
                                )
                            }
                        }
                    }
                }
            }

            // Bottom section: field (typing mode) + the primary action.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (typingMode) {
                    // Button ABOVE the field: when the keyboard pans the focused
                    // field to just above the keys, the button stays visible too.
                    if (state.revealed) {
                        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                            Text("Next")
                        }
                    } else {
                        Button(
                            onClick = { check() },
                            enabled = typed.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Check")
                        }
                    }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        label = { Text("Type the romaji") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (state.revealed) {
                                    onNext()
                                } else if (typed.isNotBlank()) {
                                    check()
                                }
                            },
                        ),
                    )
                } else {
                    // Multiple choice: tap a reading → it reveals the answer and
                    // whether you were right, then Next advances.
                    if (state.revealed) {
                        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                            Text("Next")
                        }
                    } else {
                        choiceOptions.forEach { option ->
                            OutlinedButton(
                                onClick = {
                                    choiceResult = option == card.romaji
                                    onReveal()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(option)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The card's printed glyph, drawn with the native text renderer and centered on
 * its measured INK bounds. A Compose [Text] centers the font's LINE box
 * instead, and CJK line metrics sink the kana ink well below that box's
 * center — on device the card showed far more empty space above the character
 * than below it. Here the ink is fitted to the given box (width for longer
 * words, leftover height, and a size cap), the canvas wraps the ink exactly,
 * and the draw centers on the ink's own bounds — the same metric-driven
 * centering as the tracing ghost (drawGhostCharacter). The print-forms
 * typeface keeps さ/き/り in their connected printed forms; every other glyph
 * falls back to the system font at render time, exactly like [PrintFontFamily].
 * Deliberately fontScale-independent, matching the previous toNonScalingSp
 * sizing (the glyph is bounded by geometry, not text preferences).
 */
@Composable
private fun FlashcardGlyph(
    glyph: String,
    boxWidth: Dp,
    boxHeight: Dp,
) {
    val context = LocalContext.current
    val inkColor = LocalContentColor.current
    val density = LocalDensity.current
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = ResourcesCompat.getFont(context, R.font.print_forms)
        }
    }
    // Probe the ink at a reference size, scale to fit the box (92% of each
    // side) and the cap, then re-measure at the final size so the centering
    // offsets are exact. Leaves paint.textSize set for the draw below.
    val bounds = remember(glyph, boxWidth, boxHeight) {
        val probe = 100f
        paint.textSize = probe
        val b = android.graphics.Rect()
        paint.getTextBounds(glyph, 0, glyph.length, b)
        if (!b.isEmpty) {
            with(density) {
                val scale = minOf(
                    boxWidth.toPx() * 0.92f / b.width(),
                    boxHeight.toPx() * 0.92f / b.height(),
                    120.dp.toPx() / probe,
                )
                paint.textSize = (probe * scale).coerceAtLeast(24.dp.toPx())
            }
            paint.getTextBounds(glyph, 0, glyph.length, b)
        }
        b
    }
    if (bounds.isEmpty) return
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { bounds.height().toDp() }),
    ) {
        paint.color = inkColor.toArgb()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                glyph,
                size.width / 2f - bounds.exactCenterX(),
                size.height / 2f - bounds.exactCenterY(),
                paint,
            )
        }
    }
}

/**
 * Multiple-choice options for a flashcard reading: the correct reading plus up to
 * three distinct distractors drawn from the deck, in a fixed (seeded) order per
 * card so the layout doesn't reshuffle on recomposition. Mirrors the reading
 * quiz's option builder.
 */
private fun readingChoiceOptions(correct: String, pool: List<String>, seed: Int): List<String> {
    val distractors = pool.filter { it != correct }.distinct().shuffled(Random(seed.toLong())).take(3)
    return (distractors + correct).shuffled(Random(seed.toLong() + 1))
}
