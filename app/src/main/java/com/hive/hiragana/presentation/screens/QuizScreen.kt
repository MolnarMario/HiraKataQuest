package com.hive.hiragana.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.scoring.CharacterScore
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.drawing.canvas.CanvasState
import com.hive.hiragana.presentation.components.ScoreFeedbackCard
import com.hive.hiragana.presentation.components.TracingCanvas
import com.hive.hiragana.presentation.theme.InkBlack
import kotlin.math.roundToInt
import com.hive.hiragana.presentation.viewmodels.QuizStage
import com.hive.hiragana.presentation.viewmodels.QuizState

/**
 * Full-screen "Quiz me!" overlay, launched from the library: the learner
 * writes characters from their romaji prompt alone. The ghost tracing
 * guide is off while writing — "Show answer" reveals it on the canvas
 * (plus the kana in the header) for self-checking — while the crosshair
 * practice grid is a per-quiz choice (setup switch, changeable mid-quiz).
 *
 * Three stages ([QuizState.stage]): SETUP picks how many characters
 * (5/10/20/All presets, or an exact number typed/stepped in the box the
 * presets pre-populate) and whether to show the grid; RUNNING is prompt →
 * write → optional reveal → self-grade, with Back/Next for moving through
 * the deck without grading (Next reads Finish on the last character);
 * SUMMARY lists every character with its grade, or "Skipped" if it never
 * got one. Attempts persist as PracticeSource.QUIZ; scheduling is never
 * touched.
 */
@Composable
fun QuizScreen(
    quiz: QuizState,
    deckSize: Int,
    canvasState: CanvasState,
    onSetCount: (Int) -> Unit,
    onToggleGrid: () -> Unit,
    onStart: () -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onQuizAgain: () -> Unit,
    onClose: () -> Unit,
    scoringMode: ScoringMode,
    onSetScoringMode: (ScoringMode) -> Unit,
    lastScore: CharacterScore?,
    lastScoredCharacter: String?,
    inkColor: Color = InkBlack,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (quiz.stage) {
            QuizStage.SETUP -> QuizSetup(
                quiz = quiz,
                deckSize = deckSize,
                onSetCount = onSetCount,
                onToggleGrid = onToggleGrid,
                onStart = onStart,
                onClose = onClose,
                scoringMode = scoringMode,
                onSetScoringMode = onSetScoringMode,
            )
            QuizStage.RUNNING -> QuizRunning(
                quiz = quiz,
                canvasState = canvasState,
                onToggleGrid = onToggleGrid,
                onSubmit = onSubmit,
                onAdvance = onAdvance,
                onBack = onBack,
                onClose = onClose,
                lastScore = lastScore,
                lastScoredCharacter = lastScoredCharacter,
                inkColor = inkColor,
            )
            QuizStage.SUMMARY -> QuizSummary(quiz, onQuizAgain, onClose)
        }
    }
}

/** Scoring-mode chips shown on the quiz setup card. */
private val SCORING_CHOICES = listOf(
    ScoringMode.FORM to "Form",
    ScoringMode.FIT to "Fit",
    ScoringMode.BOTH to "Both",
    ScoringMode.OFF to "Off",
)

/** Preset count choices; the deck size itself is always last ("All n"). */
private fun quizCountOptions(deckSize: Int): List<Int> =
    (listOf(5, 10, 20).filter { it < deckSize } + deckSize).distinct()

@Composable
private fun QuizSetup(
    quiz: QuizState,
    deckSize: Int,
    onSetCount: (Int) -> Unit,
    onToggleGrid: () -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit,
    scoringMode: ScoringMode,
    onSetScoringMode: (ScoringMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close quiz",
                )
            }
            Text(
                text = "Quiz me!",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Write from memory",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Each character is prompted by its sound only — " +
                            "no ghost guide to trace. Write it, check the answer, " +
                            "grade yourself. Your review schedule isn't affected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "How many characters?",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = quizCountOptions(deckSize)
                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = option == quiz.requestedCount,
                                onClick = { onSetCount(option) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size,
                                ),
                                icon = {},
                                label = {
                                    Text(if (option == deckSize) "All" else "$option")
                                },
                            )
                        }
                    }
                    QuizCountEditor(
                        count = quiz.requestedCount,
                        deckSize = deckSize,
                        onSetCount = onSetCount,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Practice grid",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "The crosshair cell behind your writing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = quiz.showGrid,
                            onCheckedChange = { onToggleGrid() },
                        )
                    }
                    Text(
                        text = "Score my writing",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Form rewards correct shape and stroke order; Fit rewards " +
                            "filling the box. This setting sticks across all practice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SCORING_CHOICES.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = mode == scoringMode,
                                onClick = { onSetScoringMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = SCORING_CHOICES.size,
                                ),
                                icon = {},
                                label = { Text(label) },
                            )
                        }
                    }
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start quiz")
                    }
                }
            }
        }
    }
}

/**
 * Exact-count row: a numeric text box flanked by −/+ steppers. The box is
 * pre-populated from whatever the presets (or steppers) set; typing a
 * number works the other way and clamps to 1..[deckSize] in the ViewModel
 * (the box snaps to the clamped value once it differs).
 */
@Composable
private fun QuizCountEditor(
    count: Int,
    deckSize: Int,
    onSetCount: (Int) -> Unit,
) {
    var countText by remember { mutableStateOf(count.toString()) }
    // Presets and steppers change the count from outside the text box —
    // mirror them in, and snap typed-but-clamped values (e.g. "99" -> 46).
    LaunchedEffect(count) {
        if (countText.toIntOrNull() != count) countText = count.toString()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Exact number",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Type it, or step with − / +",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(
            onClick = { onSetCount(count - 1) },
            enabled = count > 1,
        ) {
            Text(text = "−", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(
            value = countText,
            onValueChange = { new ->
                if (new.length <= 3 && new.all { it.isDigit() }) {
                    countText = new
                    new.toIntOrNull()?.let(onSetCount)
                }
            },
            modifier = Modifier.width(72.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        FilledTonalIconButton(
            onClick = { onSetCount(count + 1) },
            enabled = count < deckSize,
        ) {
            Text(text = "+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun QuizRunning(
    quiz: QuizState,
    canvasState: CanvasState,
    onToggleGrid: () -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    lastScore: CharacterScore?,
    lastScoredCharacter: String?,
    inkColor: Color,
) {
    val current = quiz.current ?: return
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.22f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quiz ${quiz.index + 1}/${quiz.characters.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = current.romaji,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (quiz.submitted) {
                        "Compare with the guide, then Next"
                    } else {
                        "Write it from memory"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // The kana answer appears only after Submit (revealed), so the
                // learner commits from memory first.
                if (quiz.revealed) {
                    Text(
                        text = current.character,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Grid",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = quiz.showGrid,
                        onCheckedChange = { onToggleGrid() },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f),
        ) {
            TracingCanvas(
                canvasState = canvasState,
                characters = listOf(current.character),
                showGrid = quiz.showGrid,
                // Ghost guide strokes appear only after Submit (revealed) — the
                // "official answer" comes up alongside the score.
                showGhost = quiz.revealed,
                layout = PracticeLayout.SINGLE,
                inkColor = inkColor,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
            )
            // Feedback shows only in the review sub-state (after Submit); it is
            // cleared on advance so it doesn't distract on the next character.
            if (quiz.submitted) {
                lastScore?.let { score ->
                    ScoreFeedbackCard(
                        score = score,
                        glyph = lastScoredCharacter,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp),
                    )
                }
            }
        }

        // No self-grade — handwriting is auto-scored. Submit scores + reveals the
        // guide strokes; Next advances (feedback clears). Exit (confirmed) · Back ·
        // Clear · Submit/Next.
        val hasInk = canvasState.hasInk
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.10f)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val compact = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            OutlinedButton(
                onClick = { showExitConfirm = true },
                contentPadding = compact,
                modifier = Modifier.weight(1f),
            ) {
                Text("Exit")
            }
            OutlinedButton(
                onClick = onBack,
                enabled = quiz.index > 0,
                contentPadding = compact,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }
            OutlinedButton(
                onClick = { canvasState.clear() },
                enabled = hasInk && !quiz.submitted,
                contentPadding = compact,
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear")
            }
            Button(
                onClick = {
                    when {
                        quiz.submitted -> onAdvance()
                        hasInk -> onSubmit()
                        else -> onAdvance()
                    }
                },
                contentPadding = compact,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = when {
                        quiz.submitted -> if (quiz.isLast) "Finish" else "Next"
                        hasInk -> "Submit"
                        else -> "Skip"
                    },
                    maxLines = 1,
                )
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit quiz?") },
            text = {
                Text("Characters you've already submitted are saved, but you'll leave the quiz.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        onClose()
                    },
                ) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun QuizSummary(
    quiz: QuizState,
    onQuizAgain: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Quiz complete",
                    style = MaterialTheme.typography.headlineSmall,
                )
                val scored = quiz.characters.mapNotNull { quiz.results[it.id] }
                val written = scored.size
                Text(
                    text = if (written > 0) {
                        "Wrote $written of ${quiz.characters.size} · average ${scored.average().roundToInt()}"
                    } else {
                        "You skipped every character"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                quiz.characters.forEach { character ->
                    val score = quiz.results[character.id]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = character.character,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = character.romaji,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = score?.toString() ?: "Skipped",
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                score == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                score >= 75 -> MaterialTheme.colorScheme.primary
                                score >= 50 -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
                Button(
                    onClick = onQuizAgain,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Quiz again")
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Done")
                }
            }
        }
    }
}
