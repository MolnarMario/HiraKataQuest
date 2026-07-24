package com.hive.hiragana.presentation.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.hive.hiragana.R
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.MeaningMatch
import com.hive.hiragana.domain.model.PrintDivergence
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
 * - **Typing** — productive recall: a romaji field with an inline Check/Next
 *   button (~2/3 field, 1/3 button); type + Check (or the keyboard's Done)
 *   reveals the answer via the lenient [RomajiMatch]. Once revealed the field
 *   stops accepting input (the answer is locked) but keeps focus so the
 *   keyboard stays open, and its bottom-right key becomes Next — the whole run
 *   can be driven from the keyboard without it ever closing.
 *
 * An optional **"Also test meaning"** toggle (words only — characters have no
 * meaning) adds a second step: after the reading, the learner answers what the
 * word means, in the SAME style (type it in Typing, tap it in Multiple choice).
 * The card reveals both results together. Shown only for word decks.
 *
 * Pure study — answers are deliberately NOT recorded into the reading-quiz
 * percentages; only the reading quiz writes `reading_progress`.
 *
 * Layout: the glyph and the result/meaning region get FIXED shares of the card
 * height, so the word stays the same size before and after the result shows
 * (the result region is reserved even while blank, and scrolls if it can't fit
 * rather than shrinking the glyph). Nothing clips when the keyboard shrinks the
 * window (adjustResize; on API 30+ the IME arrives via PracticeScreen's
 * safeDrawing inset host instead). The field keeps focus across card flips, so
 * the keyboard stays open for a smooth run; it only closes when switching to
 * Multiple choice.
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
    // Whether to also test the word's meaning after the reading (words only).
    testMeaning: Boolean = false,
    onSetTestMeaning: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val card = state.current ?: return
    var typingMode by rememberSaveable { mutableStateOf(false) }
    // Reading answer + result (shared by both answer styles).
    var typed by remember(state.index) { mutableStateOf("") }
    var readingOk by remember(state.index) { mutableStateOf<Boolean?>(null) }
    // Meaning answer + result — used only when meaning practice is on and the
    // card is a word (characters have no meaning).
    var meaningTyped by remember(state.index) { mutableStateOf("") }
    var meaningOk by remember(state.index) { mutableStateOf<Boolean?>(null) }

    // Meaning practice is words-only; the toggle is hidden for a character deck.
    val isWordDeck = state.cards.any { it.meaning != null }
    val needMeaning = testMeaning && card.meaning != null

    // Option sets (correct + up to 3 deck distractors, fixed per card): reading
    // draws from the readings, meaning from the other meanings (distinct seed).
    val readingOptions = remember(state.index, state.cards) {
        readingChoiceOptions(card.romaji, state.cards.map { it.romaji }, state.index)
    }
    val meaningOptions = remember(state.index, state.cards) {
        readingChoiceOptions(card.meaning.orEmpty(), state.cards.mapNotNull { it.meaning }, state.index + 7)
    }

    // Whether this card carries a "Looks different in handwriting" badge (さ/き/り
    // and their voiced/yōon variants). It's a per-card constant, so widening the
    // reveal region's share of the card for these cards doesn't resize the glyph
    // on reveal — it just gives the extra badge room to sit in view without a
    // scroll. Card sizes to content share: glyph 0.6/reveal 0.4 normally, an
    // even 0.5/0.5 when the badge is present.
    val hasDivergenceBadge = remember(card.glyph) {
        unitCharactersFor(card.glyph).any { PrintDivergence.noteFor(it.character) != null }
    }
    val glyphWeight = if (hasDivergenceBadge) 0.5f else 0.6f
    val revealWeight = if (hasDivergenceBadge) 0.5f else 0.4f

    // Reading answered but the meaning still owed — the intermediate step
    // before the card reveals.
    val inMeaningPhase = readingOk != null && needMeaning && meaningOk == null && !state.revealed
    // Combined pass: reading right AND (if tested) meaning right.
    val allOk = readingOk == true && (meaningOk != false)

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

    // Reveal once the reading is answered and no meaning is owed — covers both
    // the meaning-off path and turning the toggle off mid-card.
    LaunchedEffect(readingOk, needMeaning, state.revealed) {
        if (readingOk != null && !needMeaning && !state.revealed) onReveal()
    }

    // Answer the reading step; the effect above reveals when no meaning is owed.
    fun answerReading(correct: Boolean) { readingOk = correct }
    // Answer the meaning step and reveal the card.
    fun answerMeaning(correct: Boolean) { meaningOk = correct; onReveal() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The activity is windowSoftInputMode=adjustResize, so the window shrinks
        // to sit above the keyboard — the content lays out in the reduced height
        // (card shrinks, field + button stay just above the keys), no panning.
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close flashcards")
                }
                Text(
                    // Slightly smaller than the title scale so the meaning toggle
                    // fits beside it; takes the leftover width and truncates first.
                    text = "Card ${state.index + 1} of ${state.cards.size}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Words can also be quizzed on their MEANING (characters have
                // none, so this is hidden for a character deck). It sits in the
                // top-right corner to save a full row; flippable mid-run. The
                // meaning is asked in the SAME style as the reading, after it.
                if (isWordDeck) {
                    Text(
                        text = "Test meaning",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    // The Material switch is oversized next to a label-scale
                    // caption, so scale it down to read as proportional to it.
                    Switch(
                        checked = testMeaning,
                        onCheckedChange = onSetTestMeaning,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .scale(0.7f),
                    )
                }
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

            // Card area. The glyph and the result/meaning region get FIXED
            // shares of the card height, so the word stays exactly the same
            // size before and after the result shows — the result region is
            // reserved even while blank, rather than stealing height from the
            // glyph on reveal. The result region scrolls if a long meaning +
            // badge can't fit its share (never clips).
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
                        .padding(horizontal = 24.dp, vertical = vPad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // The glyph gets a FIXED share of the card height, so
                            // it stays the same size whether or not the result is
                            // showing (it no longer shrinks on reveal).
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(glyphWeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                FlashcardGlyph(
                                    glyph = card.glyph,
                                    boxWidth = this.maxWidth,
                                    boxHeight = this.maxHeight,
                                )
                            }
                            // Reserved result/meaning region — the other fixed
                            // share, blank until answered. Centered content;
                            // scrolls if a long meaning + badge exceeds its share
                            // so it can never clip.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(revealWeight)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
                            ) {
                                if (state.revealed) {
                                Text(
                                    text = if (allOk) "Correct!" else "Not quite",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (allOk) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                                // Reading · audio · meaning on ONE line; wraps only if
                                // it truly can't fit (responsive on narrow screens).
                                // When meaning was tested, each part carries a ✓/✗ so
                                // the learner sees which step they missed.
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
                                        if (needMeaning) ResultMark(readingOk == true)
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
                                        // One unbreakable child so FlowRow moves
                                        // the whole meaning to the next line if it
                                        // doesn't fit (rather than wrapping it
                                        // mid-word); content centered so it sits
                                        // under the middle of the card, not
                                        // left-shifted by any reserved width.
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(
                                                4.dp,
                                                Alignment.CenterHorizontally,
                                            ),
                                        ) {
                                            if (needMeaning) ResultMark(meaningOk == true)
                                            Text(
                                                text = if (needMeaning) meaning else "·  $meaning",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                                // Reading track shows the printed form, so warn when
                                // the handwritten one looks different (renders
                                // nothing for unaffected kana).
                                PrintDivergenceBadge(
                                    unitCharacters = unitCharactersFor(card.glyph),
                                    label = "Looks different in handwriting",
                                )
                            } else if (inMeaningPhase) {
                                // Reading answered — confirm it, then ask the meaning.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    ResultMark(readingOk == true)
                                    Text(
                                        text = "Reading: ${card.romaji}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "What does it mean?",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            }
                        }
                    }
                }
            }

            // Bottom section: the answer input for the current step (reading,
            // then meaning if enabled).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (typingMode) {
                    // Field + action share ONE row (~2/3 field, 1/3 button) — a
                    // guess is short, so the field needn't span the row. The
                    // keyboard stays open the WHOLE run: the field is never
                    // read-only (which dismisses the IME and caused the
                    // open/close churn), so after each Check it just stops
                    // accepting input while the bottom-right key advances the
                    // step (Check reading → Check meaning → Next).
                    val fieldValue = if (inMeaningPhase) meaningTyped else typed
                    fun submitTyped() {
                        when {
                            state.revealed -> onNext()
                            inMeaningPhase ->
                                if (meaningTyped.isNotBlank()) {
                                    answerMeaning(MeaningMatch.matches(meaningTyped, card.meaning.orEmpty()))
                                }
                            readingOk == null ->
                                if (typed.isNotBlank()) answerReading(RomajiMatch.matches(typed, card.romaji))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = fieldValue,
                            // Locked after reveal: keystrokes are ignored so the
                            // answer can't change, but the field keeps focus so
                            // the keyboard stays up (no read-only dismissal).
                            onValueChange = {
                                if (!state.revealed) {
                                    if (inMeaningPhase) meaningTyped = it else typed = it
                                }
                            },
                            modifier = Modifier
                                .weight(2f)
                                .focusRequester(focusRequester),
                            label = { Text(if (inMeaningPhase) "Type the meaning" else "Type the romaji") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitTyped() }),
                        )
                        Button(
                            onClick = { submitTyped() },
                            enabled = state.revealed || fieldValue.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) {
                            Text(if (state.revealed) "Next" else "Check", maxLines = 1)
                        }
                    }
                } else {
                    // Multiple choice: tap the reading, then (if testing meaning)
                    // tap the meaning, then Next advances.
                    when {
                        state.revealed -> {
                            // Keep the SAME number of button rows as the answering
                            // state so the card above doesn't grow/shrink between
                            // the question and the result: Next on top, then the
                            // spent option slots as empty greyed (disabled) pills —
                            // no leftover text to distract, just placeholders that
                            // hold the height.
                            val shown = if (needMeaning) meaningOptions else readingOptions
                            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                                Text("Next")
                            }
                            repeat((shown.size - 1).coerceAtLeast(0)) {
                                OutlinedButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {}
                            }
                        }
                        inMeaningPhase ->
                            meaningOptions.forEach { option ->
                                OutlinedButton(
                                    onClick = { answerMeaning(option == card.meaning) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(option)
                                }
                            }
                        else ->
                            readingOptions.forEach { option ->
                                OutlinedButton(
                                    onClick = { answerReading(option == card.romaji) },
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

/** A small ✓/✗ marking one answered step right or wrong on the reveal. */
@Composable
private fun ResultMark(ok: Boolean) {
    Text(
        text = if (ok) "✓" else "✗",
        style = MaterialTheme.typography.titleMedium,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
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
