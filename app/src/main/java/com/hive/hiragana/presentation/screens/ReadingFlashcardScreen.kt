package com.hive.hiragana.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.RomajiMatch
import com.hive.hiragana.presentation.components.PronunciationButton
import com.hive.hiragana.presentation.viewmodels.ReadingFlashcardState

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
    modifier: Modifier = Modifier,
) {
    val card = state.current ?: return
    var typingMode by rememberSaveable { mutableStateOf(false) }
    var typed by remember(state.index) { mutableStateOf("") }
    var typedCorrect by remember(state.index) { mutableStateOf<Boolean?>(null) }
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

            // Top toggle — turn the romaji field on/off at any time.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Type the reading",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = typingMode, onCheckedChange = { typingMode = it })
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
                // to a cap — device-responsive. dp values are used as sp (≈ at
                // the default font scale). The single-line reveal below leaves
                // more height for the glyph than the old stacked layout.
                val glyphCount = card.glyph.length.coerceAtLeast(1)
                val innerWidth = (maxWidth.value - 88f).coerceAtLeast(48f)
                val glyphSize = minOf(
                    (innerWidth / glyphCount) * 0.92f,
                    avail.value * 0.34f,
                    120f,
                ).coerceAtLeast(40f).sp
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Tapping the card reveals too — but only in reveal mode
                            // (typing mode reveals via Check / the keyboard's Done).
                            .clickable(enabled = !state.revealed && !typingMode, onClick = onReveal),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = vPad, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            Text(
                                text = card.glyph,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = glyphSize,
                                    lineHeight = glyphSize,
                                ),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                            if (state.revealed) {
                            if (typingMode && typedCorrect != null) {
                                Text(
                                    text = if (typedCorrect == true) "Correct!" else "Not quite",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (typedCorrect == true) {
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
                                Text(
                                    text = card.romaji,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                PronunciationButton(
                                    glyph = card.glyph,
                                    status = ttsStatus,
                                    onSpeak = onSpeak,
                                )
                                card.meaning?.let { meaning ->
                                    Text(
                                        text = "·  $meaning",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
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
                    if (state.revealed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                                Text("Missed")
                            }
                            Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                                Text("Got it")
                            }
                        }
                    } else {
                        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
                            Text("Show answer")
                        }
                    }
                }
            }
        }
    }
}
