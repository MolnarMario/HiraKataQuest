package com.hive.hiragana.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.presentation.components.PronunciationButton
import com.hive.hiragana.presentation.viewmodels.ReadingFlashcardState

/**
 * Reading-track flashcard study: flip through the scoped deck/words. The front
 * shows the printed (gothic) glyph/word; "Show answer" reveals the reading,
 * meaning, and an audio button, then Got it / Missed move to the next card.
 *
 * Self-graded study — deliberately does NOT record into the reading quiz's
 * percentages (those come from the quiz). Both buttons simply advance.
 */
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
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Front: the printed form to recognize.
                        Text(
                            text = card.glyph,
                            style = MaterialTheme.typography.displayLarge,
                            textAlign = TextAlign.Center,
                        )
                        if (state.revealed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = card.romaji,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                PronunciationButton(
                                    glyph = card.glyph,
                                    status = ttsStatus,
                                    onSpeak = onSpeak,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            card.meaning?.let { meaning ->
                                Text(
                                    text = meaning,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            if (state.revealed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                        Text("Missed")
                    }
                    Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                        Text("Got it")
                    }
                }
            } else {
                Button(
                    onClick = onReveal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text("Show answer")
                }
            }
        }
    }
}
