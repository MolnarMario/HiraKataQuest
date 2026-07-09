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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.PracticeSet
import com.hive.hiragana.domain.model.PracticeSetType
import com.hive.hiragana.domain.scoring.CharacterScore
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.domain.scoring.WordUnitScore
import com.hive.hiragana.drawing.canvas.CanvasState
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.presentation.components.PronunciationButton
import kotlin.random.Random
import com.hive.hiragana.presentation.components.ScoreFeedbackCard
import com.hive.hiragana.presentation.components.TracingCanvas
import com.hive.hiragana.presentation.components.WordAverageCard
import com.hive.hiragana.presentation.components.WordScoreChips
import com.hive.hiragana.presentation.theme.InkBlack
import kotlin.math.roundToInt
import com.hive.hiragana.domain.model.RomajiMatch
import com.hive.hiragana.presentation.viewmodels.QuizKind
import com.hive.hiragana.presentation.viewmodels.QuizStage
import com.hive.hiragana.presentation.viewmodels.QuizState
import com.hive.hiragana.presentation.viewmodels.ReadingAnswerStyle
import com.hive.hiragana.presentation.viewmodels.WordQuizItem

/**
 * Full-screen "Quiz me!" overlay, launched from the library: the learner
 * writes an item (a single character, or — for a word set — a whole word)
 * from its romaji prompt alone. The ghost tracing guide is off while writing;
 * "Show answer" reveals it on the canvas (plus the kana in the header) for
 * self-checking. The crosshair practice grid is a per-quiz choice (setup
 * switch, changeable mid-quiz).
 *
 * Three stages ([QuizState.stage]): SETUP picks the source (the active-script
 * character scope, or any saved practice set), how many items (5/10/20/All
 * presets, or an exact number), and whether to show the grid; RUNNING is
 * prompt → write → optional reveal → submit (auto-scored), with Back/Next for
 * moving through the deck; SUMMARY lists every item with its score, or
 * "Skipped" if it was never written. Attempts persist as PracticeSource.QUIZ;
 * scheduling is never touched. Words are scored per-kana (the same engine as
 * whole-word practice) and their average is the item's quiz score.
 */
@Composable
fun QuizScreen(
    quiz: QuizState,
    deckSize: Int,
    practiceSets: List<PracticeSet>,
    canvasState: CanvasState,
    onSetCount: (Int) -> Unit,
    onToggleGrid: () -> Unit,
    onSelectSource: (Long?) -> Unit,
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
    lastWordScores: List<WordUnitScore>?,
    lastWordAverage: Int?,
    inkColor: Color = InkBlack,
    // Traced-ghost form + a per-glyph stroke lookup for the HANDWRITTEN ghost
    // (the quiz reveals the ghost only after Submit); PRINTED keeps the font.
    characterForm: CharacterForm = CharacterForm.PRINTED,
    glyphStrokesFor: (List<String>) -> List<List<String>> = { emptyList() },
    // Reading-quiz callbacks (ignored by writing quizzes).
    onReadingResult: (Boolean) -> Unit = {},
    onSetAnswerStyle: (ReadingAnswerStyle) -> Unit = {},
    onSetTestMeaning: (Boolean) -> Unit = {},
    // Pronunciation (device TTS).
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
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
                practiceSets = practiceSets,
                onSetCount = onSetCount,
                onToggleGrid = onToggleGrid,
                onSelectSource = onSelectSource,
                onStart = onStart,
                onClose = onClose,
                scoringMode = scoringMode,
                onSetScoringMode = onSetScoringMode,
                onSetAnswerStyle = onSetAnswerStyle,
                onSetTestMeaning = onSetTestMeaning,
            )
            QuizStage.RUNNING -> if (quiz.kind == QuizKind.READING) {
                ReadingQuizRunning(
                    quiz = quiz,
                    onReadingResult = onReadingResult,
                    onAdvance = onAdvance,
                    onBack = onBack,
                    onClose = onClose,
                    onSpeak = onSpeak,
                    ttsStatus = ttsStatus,
                )
            } else {
                QuizRunning(
                    quiz = quiz,
                    canvasState = canvasState,
                    onToggleGrid = onToggleGrid,
                    onSubmit = onSubmit,
                    onAdvance = onAdvance,
                    onBack = onBack,
                    onClose = onClose,
                    lastScore = lastScore,
                    lastScoredCharacter = lastScoredCharacter,
                    lastWordScores = lastWordScores,
                    lastWordAverage = lastWordAverage,
                    inkColor = inkColor,
                    characterForm = characterForm,
                    glyphStrokesFor = glyphStrokesFor,
                    onSpeak = onSpeak,
                    ttsStatus = ttsStatus,
                )
            }
            QuizStage.SUMMARY -> QuizSummary(
                quiz = quiz,
                onQuizAgain = onQuizAgain,
                onClose = onClose,
                onSpeak = onSpeak,
                ttsStatus = ttsStatus,
            )
        }
    }
}

/** Reading-quiz answer styles shown on the setup card. */
private val READING_ANSWER_CHOICES = listOf(
    ReadingAnswerStyle.CHOICE to "Choose",
    ReadingAnswerStyle.TYPE to "Type",
)

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
    practiceSets: List<PracticeSet>,
    onSetCount: (Int) -> Unit,
    onToggleGrid: () -> Unit,
    onSelectSource: (Long?) -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit,
    scoringMode: ScoringMode,
    onSetScoringMode: (ScoringMode) -> Unit,
    onSetAnswerStyle: (ReadingAnswerStyle) -> Unit,
    onSetTestMeaning: (Boolean) -> Unit,
) {
    val selectedSet = practiceSets.firstOrNull { it.id == quiz.selectedSetId }
    val isWordSource = selectedSet?.type == PracticeSetType.WORDS
    val itemNoun = if (isWordSource) "words" else "characters"
    val reading = quiz.kind == QuizKind.READING
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
                        text = if (reading) "Read from sight" else "Write from memory",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (reading) {
                            "See the printed character or word and recall its " +
                                "reading — by choosing or typing the romaji."
                        } else {
                            "Each item is prompted by its sound only — no ghost " +
                                "guide to trace. Write it, check the answer, see your " +
                                "score."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Source picker — only when the learner has saved sets to
                    // choose from; otherwise the default scope source is implied.
                    if (practiceSets.isNotEmpty()) {
                        Text(
                            text = "Practice from",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        QuizSourcePicker(
                            selectedSet = selectedSet,
                            practiceSets = practiceSets,
                            onSelectSource = onSelectSource,
                        )
                    }

                    Text(
                        text = "How many $itemNoun?",
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
                    if (reading) {
                        Text(
                            text = "How do you want to answer?",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            READING_ANSWER_CHOICES.forEachIndexed { index, (style, label) ->
                                SegmentedButton(
                                    selected = style == quiz.readingAnswerStyle,
                                    onClick = { onSetAnswerStyle(style) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = READING_ANSWER_CHOICES.size,
                                    ),
                                    icon = {},
                                    label = { Text(label) },
                                )
                            }
                        }
                        if (isWordSource) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Also test meaning",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "As well as the reading, ask what the word means.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = quiz.readingTestMeaning,
                                    onCheckedChange = onSetTestMeaning,
                                )
                            }
                        }
                    } else {
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
 * The READING quiz's running screen: show the printed (gothic) glyph/word and
 * have the learner recall its reading — by picking or typing the romaji, and
 * optionally the English meaning for words. No canvas. On answering, the item
 * reveals (correctness + reading + meaning + audio) via [onReadingResult]; Next
 * advances. The prompt is [QuizItem.answer] (the glyph) — the inverse of the
 * writing quiz, whose prompt is the romaji.
 */
@Composable
private fun ReadingQuizRunning(
    quiz: QuizState,
    onReadingResult: (Boolean) -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSpeak: (String) -> Unit,
    ttsStatus: VoiceStatus,
) {
    val current = quiz.current ?: return
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    val needMeaning = quiz.readingTestMeaning && current.subtitle != null

    // Per-item answer state; resets when the index changes.
    var romajiOk by remember(quiz.index) { mutableStateOf<Boolean?>(null) }
    var meaningOk by remember(quiz.index) { mutableStateOf<Boolean?>(null) }
    var typed by remember(quiz.index) { mutableStateOf("") }

    val romajiOptions = remember(quiz.index) {
        readingOptions(current.prompt, quiz.items.map { it.prompt }, quiz.index)
    }
    val meaningOptions = remember(quiz.index) {
        readingOptions(current.subtitle.orEmpty(), quiz.items.mapNotNull { it.subtitle }, quiz.index + 7)
    }

    fun finish() {
        onReadingResult(romajiOk == true && (!needMeaning || meaningOk == true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showExitConfirm = true }) { Text("Exit") }
            IconButton(onClick = onBack, enabled = quiz.index > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
            }
            Text(
                text = "Read ${quiz.index + 1}/${quiz.items.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The printed prompt — the real-world (gothic) form to recognize.
            Text(
                text = current.answer,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )

            if (quiz.revealed) {
                ReadingRevealCard(
                    item = current,
                    romajiOk = romajiOk == true,
                    meaningOk = if (needMeaning) meaningOk == true else null,
                    onSpeak = onSpeak,
                    ttsStatus = ttsStatus,
                )
            } else if (romajiOk == null) {
                Text("What's the reading?", style = MaterialTheme.typography.titleSmall)
                ReadingAnswerInput(
                    style = quiz.readingAnswerStyle,
                    options = romajiOptions,
                    expected = current.prompt,
                    typed = typed,
                    onTypedChange = { typed = it },
                    onAnswered = { ok ->
                        romajiOk = ok
                        typed = ""
                        if (!needMeaning) finish()
                    },
                )
            } else {
                // Romaji answered; if meaning is being tested, ask it (always as
                // multiple choice — typing an English gloss would be too strict).
                Text(
                    text = "Reading: ${current.prompt}  ${if (romajiOk == true) "✓" else "✗"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (romajiOk == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (needMeaning && meaningOk == null) {
                    Text("What does it mean?", style = MaterialTheme.typography.titleSmall)
                    ReadingAnswerInput(
                        style = ReadingAnswerStyle.CHOICE,
                        options = meaningOptions,
                        expected = current.subtitle.orEmpty(),
                        typed = "",
                        onTypedChange = {},
                        onAnswered = { ok -> meaningOk = ok; finish() },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (quiz.revealed) {
                Button(onClick = onAdvance) {
                    Text(if (quiz.isLast) "Finish" else "Next")
                }
            } else {
                TextButton(onClick = onAdvance) { Text("Skip") }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit quiz?") },
            text = { Text("Your answered items are saved.") },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; onClose() }) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/** MCQ options for one reading item: the correct answer plus up to 3 distinct distractors, order fixed per item. */
private fun readingOptions(correct: String, pool: List<String>, seed: Int): List<String> {
    val distractors = pool.filter { it != correct }.distinct().shuffled(Random(seed.toLong())).take(3)
    return (distractors + correct).shuffled(Random(seed.toLong() + 1))
}

/** One reading answer surface: a column of choice buttons, or a romaji text field + Check. */
@Composable
private fun ReadingAnswerInput(
    style: ReadingAnswerStyle,
    options: List<String>,
    expected: String,
    typed: String,
    onTypedChange: (String) -> Unit,
    onAnswered: (Boolean) -> Unit,
) {
    if (style == ReadingAnswerStyle.CHOICE) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                OutlinedButton(
                    onClick = { onAnswered(option == expected) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(option)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = onTypedChange,
                singleLine = true,
                label = { Text("Type the romaji") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onAnswered(RomajiMatch.matches(typed, expected)) },
                enabled = typed.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Check")
            }
        }
    }
}

/** Post-answer reveal: correctness, the reading, the meaning, and an audio button. */
@Composable
private fun ReadingRevealCard(
    item: com.hive.hiragana.presentation.viewmodels.QuizItem,
    romajiOk: Boolean,
    meaningOk: Boolean?,
    onSpeak: (String) -> Unit,
    ttsStatus: VoiceStatus,
) {
    val allOk = romajiOk && (meaningOk != false)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (allOk) "Correct!" else "Not quite",
                style = MaterialTheme.typography.titleMedium,
                color = if (allOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                )
                PronunciationButton(
                    glyph = item.answer,
                    status = ttsStatus,
                    onSpeak = onSpeak,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            item.subtitle?.let { meaning ->
                Text(text = meaning, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * The quiz-source dropdown: the default active-script character scope, or any
 * saved practice set. Rendered as a button opening a [DropdownMenu].
 */
@Composable
private fun QuizSourcePicker(
    selectedSet: PracticeSet?,
    practiceSets: List<PracticeSet>,
    onSelectSource: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedSet?.let { "${it.name} (${it.members.size})" } ?: "Study scope",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Study scope (characters)") },
                onClick = {
                    onSelectSource(null)
                    expanded = false
                },
            )
            practiceSets.forEach { set ->
                val kind = if (set.type == PracticeSetType.WORDS) "words" else "chars"
                DropdownMenuItem(
                    text = { Text("${set.name} · ${set.members.size} $kind") },
                    onClick = {
                        onSelectSource(set.id)
                        expanded = false
                    },
                )
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
    lastWordScores: List<WordUnitScore>?,
    lastWordAverage: Int?,
    inkColor: Color,
    characterForm: CharacterForm = CharacterForm.PRINTED,
    glyphStrokesFor: (List<String>) -> List<List<String>> = { emptyList() },
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
) {
    val current = quiz.current ?: return
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    val isWord = current is WordQuizItem

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
                    text = "Quiz ${quiz.index + 1}/${quiz.items.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = current.prompt,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Meaning hint for words (helps recall the whole word).
                current.subtitle?.let { meaning ->
                    Text(
                        text = meaning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (quiz.submitted) {
                        "Compare with the guide, then Next"
                    } else {
                        "Write it from memory"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Hear the reading — the same sound the romaji prompt already
                // gives, so it never reveals the glyph shape being tested.
                PronunciationButton(
                    glyph = current.answer,
                    status = ttsStatus,
                    onSpeak = onSpeak,
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
                        text = current.answer,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
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
                characters = current.glyphs,
                showGrid = quiz.showGrid,
                // Ghost guide strokes appear only after Submit (revealed) — the
                // "official answer" comes up alongside the score.
                showGhost = quiz.revealed,
                layout = PracticeLayout.SINGLE,
                inkColor = inkColor,
                characterForm = characterForm,
                glyphStrokes = if (characterForm == CharacterForm.HANDWRITTEN) {
                    glyphStrokesFor(current.glyphs)
                } else {
                    emptyList()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
            )
            // Feedback shows only in the review sub-state (after Submit); it is
            // cleared on advance so it doesn't distract on the next item. A word
            // shows per-kana chips + an average; a character shows one card.
            if (quiz.submitted) {
                if (isWord && lastWordScores != null) {
                    WordScoreChips(
                        // Same inset as free practice: clear the paper card's
                        // 14dp edge + 20dp rounded corner so all chips stay
                        // fully inside the white canvas.
                        scores = lastWordScores,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp, start = 22.dp, end = 22.dp),
                    )
                    lastWordAverage?.let { avg ->
                        WordAverageCard(
                            average = avg,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp),
                        )
                    }
                } else {
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
                Text("Items you've already submitted are saved, but you'll leave the quiz.")
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
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
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
                // "written" counts items actually submitted (regardless of
                // whether scoring produced a headline — e.g. ScoringMode.OFF);
                // "scored" backs the average and excludes the unscored ones.
                val written = quiz.items.count { it.key in quiz.submittedKeys }
                val scored = quiz.items.mapNotNull { quiz.results[it.key] }
                Text(
                    text = if (written > 0) {
                        val base = "Wrote $written of ${quiz.items.size}"
                        if (scored.isNotEmpty()) {
                            "$base · average ${scored.average().roundToInt()}"
                        } else {
                            base
                        }
                    } else {
                        "You skipped every item"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                quiz.items.forEach { item ->
                    val score = quiz.results[item.key]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = item.answer,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = item.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PronunciationButton(
                            glyph = item.answer,
                            status = ttsStatus,
                            onSpeak = onSpeak,
                        )
                        // Three-way: a numeric score; "—" for an item written
                        // but not scored (e.g. ScoringMode.OFF); "Skipped" for a
                        // genuinely-unwritten item.
                        val wasWritten = item.key in quiz.submittedKeys
                        Text(
                            text = when {
                                score != null -> score.toString()
                                wasWritten -> "—"
                                else -> "Skipped"
                            },
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
