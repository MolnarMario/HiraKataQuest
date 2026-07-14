package com.hive.hiragana.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.RomajiMatch
import com.hive.hiragana.presentation.components.PrintDivergenceBadge
import com.hive.hiragana.presentation.components.PrintFontFamily
import com.hive.hiragana.presentation.components.PronunciationButton
import com.hive.hiragana.presentation.components.toNonScalingSp
import com.hive.hiragana.presentation.viewmodels.ReadingFlashcardState
import kotlin.random.Random

/**
 * Reading-track flashcard study: flip through the scoped deck/words. The front
 * shows the printed (gothic) glyph/word.
 *
 * A **"Type the reading" toggle at the top** switches between two study styles,
 * flippable at any time:
 * - **off** — reveal-and-self-grade: "Show answer" (or tapping the card) reveals
 *   the reading + meaning + audio, then Got it / Missed advance.
 * - **on** — productive recall: a romaji field; type + Check (or the keyboard's
 *   Done) reveals whether you were right plus the answer, then Next advances.
 *
 * Self-graded study — it deliberately does NOT feed the reading-quiz percentages.
 *
 * Keyboard flow: the field lives in the bottom section under [imePadding], so
 * opening the keyboard lifts it (and the glyph above it) clear of the keys — no
 * overlap. The field keeps focus across card flips, so the keyboard stays open
 * for a smooth run; it only closes when the toggle is turned off.
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

            // Card area — sizes itself to the available height so the WHOLE card
            // (glyph + the revealed reading, audio, and meaning) stays within
            // view. When the keyboard shrinks the space the glyph scales down
            // (display → medium → small) and paddings tighten, instead of the
            // reveal overflowing; a scroll is kept as a last-resort safety net.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val avail = maxHeight
                // Glyph as large as fits: scaled to the card's width (so longer
                // words shrink and never overflow), to the available height, and
                // to a cap — device-responsive. The size is computed in dp space,
                // so it converts via toNonScalingSp: applying it as a raw .sp
                // made the glyph fontScale× larger than the width budget assumed
                // (it overflowed the card on devices with large default fonts).
                val glyphCount = card.glyph.length.coerceAtLeast(1)
                val innerWidth = (maxWidth.value - 88f).coerceAtLeast(48f)
                val glyphSize = minOf(
                    (innerWidth / glyphCount) * 0.92f,
                    avail.value * 0.34f,
                    120f,
                ).coerceAtLeast(40f).dp.toNonScalingSp()
                val vPad = when {
                    avail >= 360.dp -> 32.dp
                    avail >= 240.dp -> 20.dp
                    else -> 14.dp
                }
                val gap = if (avail >= 320.dp) 16.dp else 10.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = vPad, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            Text(
                                text = card.glyph,
                                // PrintFontFamily swaps さ/き/り (only) to their
                                // connected printed forms; everything else falls
                                // back to the system font. See PrintDivergence.
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = PrintFontFamily,
                                    fontSize = glyphSize,
                                    lineHeight = glyphSize,
                                ),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
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
 * Multiple-choice options for a flashcard reading: the correct reading plus up to
 * three distinct distractors drawn from the deck, in a fixed (seeded) order per
 * card so the layout doesn't reshuffle on recomposition. Mirrors the reading
 * quiz's option builder.
 */
private fun readingChoiceOptions(correct: String, pool: List<String>, seed: Int): List<String> {
    val distractors = pool.filter { it != correct }.distinct().shuffled(Random(seed.toLong())).take(3)
    return (distractors + correct).shuffled(Random(seed.toLong() + 1))
}
