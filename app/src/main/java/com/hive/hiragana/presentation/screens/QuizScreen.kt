package com.hive.hiragana.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.PracticeSet
import com.hive.hiragana.domain.model.PracticeSetType
import com.hive.hiragana.domain.model.PrintDivergence
import com.hive.hiragana.domain.model.StreakEncouragement
import com.hive.hiragana.domain.scoring.CharacterScore
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.domain.scoring.WordUnitScore
import com.hive.hiragana.drawing.canvas.BrushEffect
import com.hive.hiragana.drawing.canvas.CanvasState
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.presentation.components.AnimatedStrokePreview
import com.hive.hiragana.presentation.components.AutoShrinkText
import com.hive.hiragana.presentation.components.CustomMnemonicStore
import com.hive.hiragana.presentation.components.KanjiBadge
import com.hive.hiragana.presentation.components.MnemonicBadge
import com.hive.hiragana.presentation.components.MnemonicHintButton
import com.hive.hiragana.presentation.components.MultipleChoiceOptionsGrid
import com.hive.hiragana.presentation.components.MultipleChoiceRowHeight
import com.hive.hiragana.presentation.components.mcqGridRows
import com.hive.hiragana.presentation.components.PronunciationButton
import com.hive.hiragana.presentation.components.fontScaleFraction
import com.hive.hiragana.presentation.components.toNonScalingSp
import com.hive.hiragana.presentation.components.PrintDivergenceBadge
import com.hive.hiragana.presentation.components.SpecialKanaBadge
import com.hive.hiragana.presentation.components.PrintFontFamily
import kotlin.random.Random
import com.hive.hiragana.presentation.components.ScoreFeedbackCard
import com.hive.hiragana.presentation.components.TracingCanvas
import com.hive.hiragana.presentation.components.WordAverageCard
import com.hive.hiragana.presentation.components.WordScoreChips
import com.hive.hiragana.presentation.components.WordStrokePreview
import com.hive.hiragana.presentation.theme.LocalKanaColors
import com.hive.hiragana.presentation.theme.correctColor
import kotlin.math.roundToInt
import com.hive.hiragana.domain.model.KanaAnswerMatch
import com.hive.hiragana.domain.model.RomajiMatch
import com.hive.hiragana.domain.model.SpecialKana
import com.hive.hiragana.presentation.viewmodels.QuizKind
import com.hive.hiragana.presentation.viewmodels.QuizStage
import com.hive.hiragana.presentation.viewmodels.QuizState
import com.hive.hiragana.presentation.viewmodels.ReadingAnswerStyle
import com.hive.hiragana.presentation.viewmodels.ReadingDirection
import com.hive.hiragana.presentation.viewmodels.WordQuizItem
import kotlinx.coroutines.delay

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
 * prompt → write → optional reveal → submit (auto-scored), advancing forward
 * with Next (Undo reverts the last stroke; there is no Back to a prior item);
 * SUMMARY lists every item with its score, or
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
    onRetryWrong: () -> Unit = {},
    onRetryBelowScore: (Int) -> Unit = {},
    onClose: () -> Unit,
    scoringMode: ScoringMode,
    onSetScoringMode: (ScoringMode) -> Unit,
    lastScore: CharacterScore?,
    lastScoredCharacter: String?,
    lastWordScores: List<WordUnitScore>?,
    lastWordAverage: Int?,
    inkColor: Color = LocalKanaColors.current.defaultInk,
    brushEffect: BrushEffect = BrushEffect.BRUSH,
    // Traced-ghost form + a per-glyph stroke lookup for the HANDWRITTEN ghost
    // (the quiz reveals the ghost only after Submit); PRINTED keeps the font.
    characterForm: CharacterForm = CharacterForm.PRINTED,
    glyphStrokesFor: (List<String>) -> List<List<String>> = { emptyList() },
    // Reading-quiz callbacks (ignored by writing quizzes).
    onReadingResult: (Boolean) -> Unit = {},
    onSetAnswerStyle: (ReadingAnswerStyle) -> Unit = {},
    onSetReadingDirection: (ReadingDirection) -> Unit = {},
    // Candidate answers the reading quiz draws multiple-choice distractors from
    // — the whole quiz source, not just the drawn items, so a short run (or a
    // retake of one missed item) still offers plausible wrong answers. Kana for
    // the reverse direction, readings for the forward one. See
    // PracticeUiState.quizDistractorPool / quizReadingPool.
    distractorPool: List<String> = emptyList(),
    readingPool: List<String> = emptyList(),
    onSetShowHints: (Boolean) -> Unit = {},
    onSetShowStrokeDemo: (Boolean) -> Unit = {},
    onSetTimerEnabled: (Boolean) -> Unit = {},
    onSetTestMeaning: (Boolean) -> Unit = {},
    // Deck characters for a kana string's reading units — the reading quiz's
    // reveal card uses it for the "print differs" badge (handwritten strokes).
    unitCharactersFor: (String) -> List<HiraganaCharacter> = { emptyList() },
    // Memory hints: the pre-answer Hint buttons + the reveal chip, plus the
    // learner's promoted alternates and the picker's selection sink.
    mnemonicsEnabled: Boolean = true,
    // Whether the informative "Kanji" button shows on word reveals (opt-in).
    kanjiHintsEnabled: Boolean = false,
    mnemonicChoices: Map<String, String> = emptyMap(),
    onChooseMnemonic: (String, String?) -> Unit = { _, _ -> },
    customMnemonics: CustomMnemonicStore = CustomMnemonicStore(),
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
                onSetReadingDirection = onSetReadingDirection,
                onSetTestMeaning = onSetTestMeaning,
                onSetShowHints = onSetShowHints,
                onSetShowStrokeDemo = onSetShowStrokeDemo,
                onSetTimerEnabled = onSetTimerEnabled,
                mnemonicsEnabled = mnemonicsEnabled,
            )
            QuizStage.RUNNING -> if (quiz.kind == QuizKind.READING) {
                ReadingQuizRunning(
                    quiz = quiz,
                    onReadingResult = onReadingResult,
                    onAdvance = onAdvance,
                    onBack = onBack,
                    onClose = onClose,
                    distractorPool = distractorPool,
                    readingPool = readingPool,
                    onSpeak = onSpeak,
                    ttsStatus = ttsStatus,
                    unitCharactersFor = unitCharactersFor,
                    mnemonicsEnabled = mnemonicsEnabled,
                    kanjiHintsEnabled = kanjiHintsEnabled,
                    mnemonicChoices = mnemonicChoices,
                    onChooseMnemonic = onChooseMnemonic,
                    customMnemonics = customMnemonics,
                )
            } else {
                QuizRunning(
                    quiz = quiz,
                    canvasState = canvasState,
                    onToggleGrid = onToggleGrid,
                    onSubmit = onSubmit,
                    onAdvance = onAdvance,
                    onClose = onClose,
                    lastScore = lastScore,
                    lastScoredCharacter = lastScoredCharacter,
                    lastWordScores = lastWordScores,
                    lastWordAverage = lastWordAverage,
                    inkColor = inkColor,
                    brushEffect = brushEffect,
                    characterForm = characterForm,
                    glyphStrokesFor = glyphStrokesFor,
                    unitCharactersFor = unitCharactersFor,
                    mnemonicsEnabled = mnemonicsEnabled,
                    kanjiHintsEnabled = kanjiHintsEnabled,
                    mnemonicChoices = mnemonicChoices,
                    onChooseMnemonic = onChooseMnemonic,
                    customMnemonics = customMnemonics,
                    onSpeak = onSpeak,
                    ttsStatus = ttsStatus,
                )
            }
            QuizStage.SUMMARY -> QuizSummary(
                quiz = quiz,
                onQuizAgain = onQuizAgain,
                onRetryWrong = onRetryWrong,
                onRetryBelowScore = onRetryBelowScore,
                onClose = onClose,
                onSpeak = onSpeak,
                ttsStatus = ttsStatus,
                unitCharactersFor = unitCharactersFor,
                mnemonicsEnabled = mnemonicsEnabled,
                mnemonicChoices = mnemonicChoices,
                onChooseMnemonic = onChooseMnemonic,
                customMnemonics = customMnemonics,
            )
        }
    }
}

// The reading quiz keeps the same solid, no-reflow layout as the reading
// flashcard (Practice mode): the prompt glyph takes the leftover height above a
// FIXED reveal reserve, and the answer input is pinned in a fixed bottom
// section, so revealing the answer fills reserved space in place — the glyph
// never moves and nothing scrolls.
/** Height reserved for the reveal/prompt region, sized to hold the reveal. */
private val ReadingRevealReserve = 150.dp

/** Reading-quiz answer styles shown on the setup card. */
private val READING_ANSWER_CHOICES = listOf(
    ReadingAnswerStyle.CHOICE to "Multiple choice",
    ReadingAnswerStyle.TYPE to "Type it",
)

/** Reading-quiz directions shown on the setup card (see [ReadingDirection]). */
private val READING_DIRECTION_CHOICES = listOf(
    ReadingDirection.KANA_TO_ROMAJI to "Kana → romaji",
    ReadingDirection.ROMAJI_TO_KANA to "Romaji → kana",
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

/** Formats a stopwatch duration as m:ss (minutes not zero-padded). */
private fun formatQuizTime(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * A running-quiz stopwatch pill. Renders nothing unless the timer was enabled at
 * setup. While the quiz is RUNNING ([QuizState.elapsedMillis] still null) it
 * ticks once a second off the wall clock ([QuizState.startedAtMillis] is set
 * from the same [java.time.Clock] default zone, so the two agree); once the quiz
 * finishes it shows the frozen total the view model captured.
 */
@Composable
private fun QuizTimerChip(quiz: QuizState, modifier: Modifier = Modifier) {
    if (!quiz.timerEnabled) return
    val start = quiz.startedAtMillis ?: return
    val running = quiz.elapsedMillis == null
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running, start) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = quiz.elapsedMillis ?: (now - start)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "⏱ ${formatQuizTime(elapsed)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

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
    onSetReadingDirection: (ReadingDirection) -> Unit = {},
    onSetTestMeaning: (Boolean) -> Unit,
    onSetShowHints: (Boolean) -> Unit,
    onSetShowStrokeDemo: (Boolean) -> Unit = {},
    onSetTimerEnabled: (Boolean) -> Unit = {},
    mnemonicsEnabled: Boolean = true,
) {
    val selectedSet = practiceSets.firstOrNull { it.id == quiz.selectedSetId }
    val isWordSource = selectedSet?.type == PracticeSetType.WORDS
    val itemNoun = if (isWordSource) "words" else "characters"
    val reading = quiz.kind == QuizKind.READING
    val reverse = quiz.readingDirection == ReadingDirection.ROMAJI_TO_KANA
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
                        text = when {
                            reading && reverse -> "Recall the kana"
                            reading -> "Read from sight"
                            else -> "Write from memory"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = when {
                            reading && reverse ->
                                "See the reading and recall which character or " +
                                    "word it spells. Choose or type the kana."
                            reading ->
                                "See the printed character or word and recall its " +
                                    "reading. Choose or type the romaji."
                            else ->
                                "Each item is prompted by its sound only, with no ghost " +
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
                                label = {
                                    Text(
                                        text = if (option == deckSize) "All" else "$option",
                                        fontWeight = if (option == quiz.requestedCount) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                },
                            )
                        }
                    }
                    QuizCountEditor(
                        count = quiz.requestedCount,
                        deckSize = deckSize,
                        onSetCount = onSetCount,
                    )
                    // Optional stopwatch — shared by both quiz kinds. Counts up
                    // for the whole run and shows the total on the summary; no
                    // per-question pressure.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Timer",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Show a stopwatch while you go and your total " +
                                    "time on the summary.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = quiz.timerEnabled,
                            onCheckedChange = onSetTimerEnabled,
                        )
                    }
                    if (reading) {
                        // Direction first: it decides what the item even asks,
                        // which the answer-style control below then qualifies.
                        // No icon param — the default selected-checkmark is the
                        // whole cue here, and no core icon means "this way round".
                        Text(
                            text = "Which way round?",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            READING_DIRECTION_CHOICES.forEachIndexed { index, (direction, label) ->
                                SegmentedButton(
                                    selected = direction == quiz.readingDirection,
                                    onClick = { onSetReadingDirection(direction) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = READING_DIRECTION_CHOICES.size,
                                    ),
                                    label = {
                                        Text(
                                            text = label,
                                            fontWeight = if (direction == quiz.readingDirection) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                        Text(
                            text = if (reverse) {
                                "You'll see the reading and answer with the kana. " +
                                    "That's recall, the harder way round."
                            } else {
                                "You'll see the kana and answer with its reading."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "How would you like to answer?",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        // Two clearly-labelled, icon-tagged modes so it reads as a
                        // pick-one toggle, not a static label. A caption under it
                        // spells out the selected mode (and, by contrast, the other).
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            READING_ANSWER_CHOICES.forEachIndexed { index, (style, label) ->
                                SegmentedButton(
                                    selected = style == quiz.readingAnswerStyle,
                                    onClick = { onSetAnswerStyle(style) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = READING_ANSWER_CHOICES.size,
                                    ),
                                    icon = {
                                        // Checkmark when selected (the clear "active"
                                        // cue); the mode's own icon when not.
                                        SegmentedButtonDefaults.Icon(
                                            active = style == quiz.readingAnswerStyle,
                                            inactiveContent = {
                                                Icon(
                                                    imageVector = when (style) {
                                                        ReadingAnswerStyle.CHOICE ->
                                                            Icons.AutoMirrored.Filled.List
                                                        ReadingAnswerStyle.TYPE ->
                                                            Icons.Filled.Create
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                                )
                                            },
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontWeight = if (style == quiz.readingAnswerStyle) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                        Text(
                            text = when {
                                reverse && quiz.readingAnswerStyle == ReadingAnswerStyle.CHOICE ->
                                    "Pick the correct kana from a few options. " +
                                        "Tap “Type it” to write it yourself instead."
                                reverse ->
                                    "Type the kana yourself. " +
                                        "Tap “Multiple choice” to pick from options instead."
                                quiz.readingAnswerStyle == ReadingAnswerStyle.CHOICE ->
                                    "Pick the correct reading from a few options. " +
                                        "Tap “Type it” to enter the romaji yourself instead."
                                else ->
                                    "Type the romaji reading yourself. " +
                                        "Tap “Multiple choice” to pick from options instead."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Typing kana needs a Japanese IME, which the app can
                        // neither detect nor install. So this warns rather than
                        // blocks: the mode stays selectable (plenty of learners
                        // already have the keyboard), and the button opens the
                        // system settings where one is added — the same
                        // learner-fixes-a-device-gap route PronunciationButton
                        // uses for the missing TTS voice.
                        if (reverse && quiz.readingAnswerStyle == ReadingAnswerStyle.TYPE) {
                            JapaneseKeyboardWarning()
                        }
                        // Only meaningful when memory hints are globally on; the
                        // pre-answer Hint button is gated on both.
                        if (mnemonicsEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hints during the quiz",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Show a “Hint” button on each item to peek at " +
                                            "its memory aid. Turn off to test yourself cold.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = quiz.showHints,
                                    onCheckedChange = onSetShowHints,
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
                        // Same per-run pre-answer hints toggle as the reading
                        // quiz, gated on the global memory-hints setting.
                        if (mnemonicsEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hints during the quiz",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Show a “Hint” button on each item to peek at " +
                                            "its memory aid. Turn off to test yourself cold.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = quiz.showHints,
                                    onCheckedChange = onSetShowHints,
                                )
                            }
                        }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stroke animation",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "After you submit, play the answer's stroke " +
                                        "order in the corner. Turn off to just compare shapes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = quiz.showStrokeDemo,
                                onCheckedChange = onSetShowStrokeDemo,
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
                                    label = {
                                        Text(
                                            text = label,
                                            fontWeight = if (mode == scoringMode) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                    },
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
 * Setup-card notice for the one combination the device may not support: typing
 * kana requires a Japanese keyboard, and there is no API to ask whether the
 * learner has one or to install it for them. It is styled as a warning, not an
 * error, and never disables anything — a learner who does have the keyboard
 * should read past it, and one who doesn't can either tap through to the
 * keyboard settings or switch to multiple choice.
 */
@Composable
private fun JapaneseKeyboardWarning() {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Needs a Japanese keyboard",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Typing kana only works if your phone's keyboard has " +
                    "Japanese installed. If it doesn't, add it below or use " +
                    "multiple choice instead.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { openKeyboardSettings(context) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Keyboard settings")
            }
        }
    }
}

/**
 * Opens the system input-method settings, where a Japanese keyboard is enabled
 * or downloaded. Mirrors PronunciationButtons.installVoiceData: try the precise
 * screen, fall back to the general settings, and stay a silent no-op if neither
 * resolves rather than crashing on an unusual device.
 */
private fun openKeyboardSettings(context: Context) {
    val languageSettings = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(languageSettings)
        return
    } catch (_: ActivityNotFoundException) {
        // no input-method screen — fall through to general settings
    }
    try {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // Nothing to open — leave the button as a no-op on this device.
    }
}

/**
 * The READING quiz's running screen, in both directions ([ReadingDirection]).
 *
 * Forward (KANA_TO_ROMAJI) shows the printed (gothic) glyph/word and has the
 * learner recall its reading — by picking or typing the romaji, and optionally
 * the English meaning for words. Reverse (ROMAJI_TO_KANA) swaps the two: the
 * reading is the prompt and the kana is the answer, picked from [distractorPool]
 * or typed with a Japanese keyboard. Either way there is no canvas, answering
 * reveals the item (correctness + reading + meaning + audio) via
 * [onReadingResult], and Next advances.
 *
 * The layout is identical in both directions and deliberately rigid: the prompt
 * takes the leftover height above a FIXED reveal reserve, and the answer input is
 * pinned in a fixed-height bottom section, so revealing fills reserved space in
 * place. In reverse the prompt box swaps romaji for the answer glyph on reveal —
 * same box, same constraints, so the size and position still don't move.
 */
@Composable
private fun ReadingQuizRunning(
    quiz: QuizState,
    onReadingResult: (Boolean) -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    distractorPool: List<String>,
    readingPool: List<String>,
    onSpeak: (String) -> Unit,
    ttsStatus: VoiceStatus,
    unitCharactersFor: (String) -> List<HiraganaCharacter>,
    mnemonicsEnabled: Boolean,
    kanjiHintsEnabled: Boolean,
    mnemonicChoices: Map<String, String>,
    onChooseMnemonic: (String, String?) -> Unit,
    customMnemonics: CustomMnemonicStore,
) {
    val current = quiz.current ?: return
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    // Hardware/gesture Back during a running quiz asks to confirm rather than
    // discarding the quiz outright (the parent PracticeScreen's Back would call
    // closeQuiz with no prompt). Composed after the parent, so LIFO wins here.
    BackHandler { showExitConfirm = true }
    val needMeaning = quiz.readingTestMeaning && current.subtitle != null
    val reverse = quiz.readingDirection == ReadingDirection.ROMAJI_TO_KANA
    // A special mark (ー, small tsu, small vowels) has no romaji, so it is
    // tested by its role ("long vowel", "small tsu") and always by choice —
    // still quizzed, just on knowing what the mark is. That holds in BOTH
    // directions: forward there is nothing to type, and reverse shouldn't ask
    // anyone to find ー on a keyboard. ん/を read normally, so markStyle is
    // false for them and they keep the chosen answer style.
    val readingAnswer = SpecialKana.readingLabel(current.answer) ?: current.prompt
    val markStyle = SpecialKana.readingLabel(current.answer) != null
    val readingStyle = if (markStyle) ReadingAnswerStyle.CHOICE else quiz.readingAnswerStyle

    // Per-item answer state; resets when the index changes. [romajiOk] is the
    // reading step's verdict in either direction — reverse just grades the kana
    // rather than the romaji.
    var romajiOk by rememberSaveable(quiz.index) { mutableStateOf<Boolean?>(null) }
    var meaningOk by rememberSaveable(quiz.index) { mutableStateOf<Boolean?>(null) }
    var typed by rememberSaveable(quiz.index) { mutableStateOf("") }

    val romajiOptions = remember(quiz.index, reverse, distractorPool, readingPool) {
        if (reverse) {
            // Reverse options are kana, drawn from the whole quiz SOURCE rather
            // than the drawn items — otherwise a 5-item run would only ever
            // offer those 5 kana as wrong answers.
            readingOptions(current.answer, distractorPool, quiz.index)
        } else {
            // Forward options are readings, drawn from the SOURCE for the same
            // reason: the drawn items shrink on a retake, and one missed item
            // left exactly one option to tap. Correct answer + pool both use the
            // readable reading (a role label for marks, romaji otherwise) so no
            // placeholder like "xtsu" leaks into the options. The items are the
            // fallback for callers that pass no pool (previews/tests).
            readingOptions(
                readingAnswer,
                readingPool.ifEmpty { quiz.items.map { SpecialKana.readingLabel(it.answer) ?: it.prompt } },
                quiz.index,
            )
        }
    }
    val meaningOptions = remember(quiz.index) {
        readingOptions(current.subtitle.orEmpty(), quiz.items.mapNotNull { it.subtitle }, quiz.index + 7)
    }

    fun finish() {
        onReadingResult(romajiOk == true && (!needMeaning || meaningOk == true))
    }

    // Typing ergonomics, mirroring the reading flashcard: the answer field and
    // its action share ONE bottom row, the soft keyboard stays open across
    // items, and its Done key drives Check → Next so the whole run can be typed
    // without reaching for a button. Marks force multiple choice (nothing to
    // type), so typing is suppressed for them (readingStyle is CHOICE there).
    val typingReading = readingStyle == ReadingAnswerStyle.TYPE
    // The typed field is on screen during the reading step and after the reveal
    // (locked, driving Next); it yields to the multiple-choice buttons during
    // the words-only meaning step.
    val showTypingField = typingReading && (quiz.revealed || romajiOk == null)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Keep the keyboard up whenever the field is shown and drop it when it isn't
    // (meaning step, multiple choice, marks) so the choice buttons aren't hidden.
    // Across a normal item the field is shown continuously (reading → reveal →
    // next reading), so focus persists and the keyboard never blinks closed.
    LaunchedEffect(showTypingField) {
        if (showTypingField) runCatching { focusRequester.requestFocus() }
        else focusManager.clearFocus()
    }
    // Bottom-row action: Next when revealed, Check with input, Skip when empty.
    fun submitTyped() {
        when {
            quiz.revealed -> onAdvance()
            typed.isNotBlank() -> {
                // Reverse grades the typed KANA (exact after IME normalization);
                // forward grades typed romaji (lenient across romanizations).
                romajiOk = if (reverse) {
                    KanaAnswerMatch.matches(typed, current.answer)
                } else {
                    RomajiMatch.matches(typed, readingAnswer)
                }
                if (!needMeaning) finish()
            }
            else -> onAdvance() // empty field acts as Skip
        }
    }

    // Which step the item is on (drives both the reserve content and the pinned
    // bottom answer section).
    val readingStep = !quiz.revealed && romajiOk == null
    val meaningStep = !quiz.revealed && romajiOk != null && needMeaning && meaningOk == null

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
                text = "${if (reverse) "Recall" else "Read"} " +
                    "${quiz.index + 1}/${quiz.items.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            QuizTimerChip(quiz)
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            val gap = if (maxHeight >= 320.dp) 12.dp else 8.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The headline box. Forward it holds the printed prompt glyph —
                // the real-world (gothic) form to recognize (print_forms swaps
                // さ/き/り to their connected printed forms; everything else falls
                // back to the system font). Reverse it holds the READING as the
                // prompt, and swaps to that same glyph on reveal, which is the
                // one moment the hidden answer should be shown large.
                //
                // Either way it fits its box (width, leftover height, and a cap)
                // and, because the reserve below is a constant height, keeps the
                // exact same size and position on reveal — nothing jumps.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (reverse && !quiz.revealed) {
                        // Sized to its box rather than to the type scale, so the
                        // reading is the headline the glyph is in the other
                        // direction instead of a caption adrift in white space.
                        // That makes it font-scale-independent for the same
                        // reason FlashcardGlyph is: it is already as large as the
                        // space allows. AutoShrinkText then shrinks a long word's
                        // spaced reading ("ko o hi i") to one line, and the
                        // parent Box centres whatever width it settles on (its
                        // own textAlign can't — the inner Text wraps content).
                        val promptSize = (this@BoxWithConstraints.maxHeight * 0.34f)
                            .coerceIn(28.dp, 96.dp)
                        AutoShrinkText(
                            text = readingAnswer,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = promptSize.toNonScalingSp(),
                                lineHeight = (promptSize * 1.2f).toNonScalingSp(),
                            ),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        FlashcardGlyph(
                            glyph = current.answer,
                            boxWidth = this.maxWidth,
                            boxHeight = this.maxHeight,
                        )
                    }
                }

                // Fixed reveal/prompt reserve — reserved in every state so the
                // glyph above never shifts. Holds the pre-answer prompt (+ hint),
                // the meaning question, or the reveal; NEVER the answer input,
                // which is pinned in the bottom section. verticalScroll is an
                // extreme-font / small-screen safety only. Content is BOTTOM-
                // aligned (was centered) so the short pre-answer prompt sits
                // right above the answer pills instead of floating in the
                // middle of the reserve with a dead gap below it (device
                // feedback, 2026-08-22) — the reserve's own box is unaffected,
                // so the glyph above never moves.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ReadingRevealReserve)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(gap, Alignment.Bottom),
                ) {
                    when {
                        quiz.revealed -> ReadingRevealContent(
                            item = current,
                            romajiOk = romajiOk == true,
                            meaningOk = if (needMeaning) meaningOk == true else null,
                            streakMessage = quiz.streakMessage,
                            onSpeak = onSpeak,
                            ttsStatus = ttsStatus,
                            unitCharacters = unitCharactersFor(current.answer),
                            mnemonicsEnabled = mnemonicsEnabled,
                            kanjiHintsEnabled = kanjiHintsEnabled,
                            mnemonicChoices = mnemonicChoices,
                            onChooseMnemonic = onChooseMnemonic,
                            customMnemonics = customMnemonics,
                        )
                        meaningStep -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Reading: ${current.prompt}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (romajiOk == true) {
                                        correctColor(LocalKanaColors.current.isDark)
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                                ReadingResultMark(romajiOk == true)
                            }
                            Text(
                                text = "What does it mean?",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        else -> {
                            Text(
                                text = when {
                                    markStyle && reverse -> "Which mark is this?"
                                    markStyle -> "What is this mark?"
                                    reverse && current is WordQuizItem -> "Which word is this?"
                                    reverse -> "Which kana is this?"
                                    else -> "What's the reading?"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            // Pre-answer memory hint (opt-in via the setup card).
                            // Forward the glyph is the visible prompt, so the
                            // dialog may show it and the keyword hints the hidden
                            // sound. REVERSE inverts that: the glyph IS the hidden
                            // answer, so this takes the writing quiz's text-only
                            // variant (the stories are already pinned never to
                            // contain kana). Renders nothing when no unit has a
                            // mnemonic.
                            if (mnemonicsEnabled && quiz.showHints) {
                                MnemonicHintButton(
                                    unitCharacters = unitCharactersFor(current.answer),
                                    showGlyph = !reverse,
                                    mnemonicChoices = mnemonicChoices,
                                    onChoose = onChooseMnemonic,
                                    customs = customMnemonics,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom answer section — pinned, with fixed slot heights so it (hence
        // the reserve and glyph above) stays a constant height across the
        // question/answer/reveal states. Extra bottom margin (vs. the 16dp top)
        // lifts the pills off the physical screen edge for easier one-handed
        // reach (device feedback, 2026-08-22) — same reasoning as
        // MultipleChoiceRowHeight above.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                // Typing: the field + action share ONE row for the reading step
                // AND the reveal, so the keyboard stays open across items and its
                // Done key drives Check → Next. The field is never read-only —
                // after reveal it just ignores keystrokes but keeps focus.
                typingReading && (readingStep || quiz.revealed) -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { if (!quiz.revealed) typed = it },
                            modifier = Modifier
                                .weight(2f)
                                .focusRequester(focusRequester),
                            label = { Text(if (reverse) "Type the kana" else "Type the romaji") },
                            singleLine = true,
                            // Only the IME action is constrained: forcing a
                            // keyboard type here (Ascii, Text …) can suppress the
                            // learner's Japanese IME, which reverse mode needs.
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (quiz.revealed || typed.isNotBlank()) submitTyped()
                            }),
                        )
                        Button(
                            onClick = { submitTyped() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) {
                            Text(
                                text = when {
                                    quiz.revealed -> if (quiz.isLast) "Finish" else "Next"
                                    typed.isNotBlank() -> "Check"
                                    else -> "Skip"
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
                // Multiple-choice options, two pills per row (fixed-height slots).
                // Forward they are readings; reverse they are kana, set in the
                // print font at a readable size — the option IS the character
                // being judged, so it has to be legible enough to tell き from ぎ.
                // A half-width pill can't hold a long answer on one line (the
                // longest, よろしくおねがいします, overflows even at font scale 1),
                // so the label wraps to two lines and then shrinks, the same as
                // the grid's default label. An ellipsized answer can't be judged.
                readingStep -> {
                    val correctOption = if (reverse) current.answer else readingAnswer
                    val optionStyle = if (reverse) {
                        MaterialTheme.typography.titleLarge.copy(fontFamily = PrintFontFamily)
                    } else {
                        MaterialTheme.typography.titleLarge
                    }
                    MultipleChoiceOptionsGrid(
                        options = romajiOptions,
                        onSelect = { option ->
                            romajiOk = option == correctOption
                            if (!needMeaning) finish()
                        },
                        optionContent = { option ->
                            AutoShrinkText(
                                text = option,
                                style = optionStyle,
                                minFontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        },
                    )
                }
                // Meaning step is always multiple choice (typing an English gloss
                // would be too strict).
                meaningStep -> MultipleChoiceOptionsGrid(
                    options = meaningOptions,
                    onSelect = { option -> meaningOk = option == current.subtitle; finish() },
                )
                // Revealed on a multiple-choice run: Next fills the first slot and
                // the spent option rows stay as blank spacers, so this section's
                // height is unchanged from the answering state.
                quiz.revealed -> {
                    val slots = if (needMeaning) meaningOptions else romajiOptions
                    Button(
                        onClick = onAdvance,
                        modifier = Modifier.fillMaxWidth().height(MultipleChoiceRowHeight),
                    ) {
                        Text(if (quiz.isLast) "Finish" else "Next")
                    }
                    repeat((mcqGridRows(slots.size) - 1).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.fillMaxWidth().height(MultipleChoiceRowHeight))
                    }
                }
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

/**
 * Post-answer reveal content, laid out INLINE (no Card) so it fits the fixed
 * reveal reserve in [ReadingQuizRunning] without resizing the layout — the same
 * compact, solid reveal the reading flashcard (Practice mode) uses. Shows the
 * verdict, the reading (+ audio), the meaning, and the "looks different / how to
 * remember" badges. The three badges share ONE row (the mnemonic chip goes
 * icon-only when a divergence/special badge is also present) so the reveal stays
 * within the reserve.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingRevealContent(
    item: com.hive.hiragana.presentation.viewmodels.QuizItem,
    romajiOk: Boolean,
    meaningOk: Boolean?,
    streakMessage: String?,
    onSpeak: (String) -> Unit,
    ttsStatus: VoiceStatus,
    unitCharacters: List<HiraganaCharacter>,
    mnemonicsEnabled: Boolean,
    kanjiHintsEnabled: Boolean,
    mnemonicChoices: Map<String, String>,
    onChooseMnemonic: (String, String?) -> Unit,
    customMnemonics: CustomMnemonicStore,
) {
    val needMeaning = meaningOk != null
    val allOk = romajiOk && (meaningOk != false)
    val hasDivergence = unitCharacters.any { PrintDivergence.noteFor(it.character) != null }
    val hasSpecial = unitCharacters.any { SpecialKana.noteFor(it.character) != null }
    val hasAudio = !SpecialKana.isSilentMark(item.answer)

    // The streak encouragement IS the headline (a green tier line when correct,
    // a gentle red streak-broken line when wrong), falling back to the plain
    // verdict when there is no streak message.
    Text(
        text = streakMessage ?: if (allOk) "Correct!" else "Not quite",
        style = MaterialTheme.typography.titleMedium,
        color = if (allOk) correctColor(LocalKanaColors.current.isDark) else MaterialTheme.colorScheme.error,
    )
    // Reading · audio · meaning on one line, each an unbreakable child so a
    // squeezed part wraps whole to the next line rather than shredding. When
    // meaning was tested, each part carries a ✓/✗ so the missed step is clear.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (needMeaning) ReadingResultMark(romajiOk)
            // readingLabel gives a lone mark its role, not its placeholder romaji.
            Text(
                text = SpecialKana.readingLabel(item.answer) ?: item.prompt,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (hasAudio) {
                PronunciationButton(glyph = item.answer, status = ttsStatus, onSpeak = onSpeak)
            }
        }
        item.subtitle?.let { meaning ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                if (needMeaning) ReadingResultMark(meaningOk == true)
                Text(
                    text = if (needMeaning) meaning else "·  $meaning",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    // All three badges share ONE row so the reveal stays compact. The quiz
    // prompt was the printed form, so the divergence badge is labelled for the
    // handwritten side; the mnemonic chip goes icon-only when another badge is
    // present. Each renders nothing when it has no content.
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrintDivergenceBadge(
            unitCharacters = unitCharacters,
            label = "Looks different in handwriting",
            modifier = Modifier.weight(1f, fill = false),
        )
        SpecialKanaBadge(unitCharacters = unitCharacters, iconOnly = hasDivergence)
        // The word's kanji spelling, for reference (opt-in; renders nothing
        // when off or the word has none). Informative only — never quizzed.
        KanjiBadge(
            kanji = item.kanji.takeIf { kanjiHintsEnabled },
            reading = SpecialKana.readingLabel(item.answer) ?: item.prompt,
            meaning = item.subtitle.orEmpty(),
            kanjiUsage = item.kanjiUsage,
            otherForms = item.otherKanjiForms,
        )
        if (mnemonicsEnabled) {
            MnemonicBadge(
                unitCharacters = unitCharacters,
                iconOnly = hasDivergence || hasSpecial,
                mnemonicChoices = mnemonicChoices,
                onChoose = onChooseMnemonic,
                customs = customMnemonics,
            )
        }
    }
}

/** A small ✓/✗ marking one answered step right or wrong on the reveal. */
@Composable
private fun ReadingResultMark(ok: Boolean) {
    Text(
        text = if (ok) "✓" else "✗",
        style = MaterialTheme.typography.titleMedium,
        color = if (ok) correctColor(LocalKanaColors.current.isDark) else MaterialTheme.colorScheme.error,
    )
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
 * number works the other way and clamps to 1..[deckSize]. The box shows the
 * clamped value immediately (clamped on input, not just echoed from the VM) so
 * it can't desync when the clamp lands on the value the count already had
 * (e.g. typing "99" while the count is already the deck max).
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
    // Label + hint stacked full-width ABOVE the stepper row: with the old
    // side-by-side split the label column got whatever the stepper left
    // over, and at large font scales it wrapped word-by-word ("+" alone on
    // a line). Stacked, both texts always get the full width.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                        val n = new.toIntOrNull()
                        if (n == null) {
                            countText = new // allow a transient empty field
                        } else {
                            // Clamp on input so the displayed value always
                            // equals what the quiz will use, even when the
                            // clamp lands on the current count (no VM change).
                            val clamped = n.coerceIn(1, deckSize.coerceAtLeast(1))
                            countText = clamped.toString()
                            onSetCount(clamped)
                        }
                    }
                },
                // Grows with the font scale so 2-3 digits stay visible.
                modifier = Modifier.width(72.dp * fontScaleFraction()),
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
}

@Composable
private fun QuizRunning(
    quiz: QuizState,
    canvasState: CanvasState,
    onToggleGrid: () -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onClose: () -> Unit,
    lastScore: CharacterScore?,
    lastScoredCharacter: String?,
    lastWordScores: List<WordUnitScore>?,
    lastWordAverage: Int?,
    inkColor: Color,
    brushEffect: BrushEffect = BrushEffect.BRUSH,
    characterForm: CharacterForm = CharacterForm.PRINTED,
    glyphStrokesFor: (List<String>) -> List<List<String>> = { emptyList() },
    unitCharactersFor: (String) -> List<HiraganaCharacter> = { emptyList() },
    mnemonicsEnabled: Boolean = true,
    kanjiHintsEnabled: Boolean = false,
    mnemonicChoices: Map<String, String> = emptyMap(),
    onChooseMnemonic: (String, String?) -> Unit = { _, _ -> },
    customMnemonics: CustomMnemonicStore = CustomMnemonicStore(),
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
) {
    val current = quiz.current ?: return
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    // Hardware/gesture Back during a running quiz asks to confirm rather than
    // discarding the quiz outright (the parent PracticeScreen's Back would call
    // closeQuiz with no prompt). Composed after the parent, so LIFO wins here.
    BackHandler { showExitConfirm = true }
    val isWord = current is WordQuizItem

    // On reveal the right column shows the answer's stroke-order ANIMATION (the
    // same demo free practice uses). Resolve its units up front so the header
    // can claim extra height for it, and so a mark with no timed data quietly
    // falls back to just the glyph + hint.
    val answerUnits = if (quiz.revealed) unitCharactersFor(current.answer) else emptyList()
    val hasStrokeDemo = quiz.showStrokeDemo && answerUnits.any { it.strokeAnimations.isNotEmpty() }

    // The header and action bar hold text that grows with the system font
    // scale, but their height shares were hand-tuned at scale 1 — grow both
    // (weights are relative, so the canvas shrinks to absorb it) instead of
    // letting the stacked header texts clip.
    val fontGrow = fontScaleFraction(1.4f)
    // Fixed header share whether or not the stroke demo is showing, so the
    // canvas below never moves when the learner submits. The demo instead
    // fits into the header's constant leftover height (right column, weight
    // 1f) — as large as it can be without perturbing the layout.
    val headerWeight = 0.22f
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(headerWeight * fontGrow)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Quiz ${quiz.index + 1}/${quiz.items.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    QuizTimerChip(quiz)
                }
                // The prompt is the item under test, so it shrinks (floor
                // 20sp) rather than ellipsizing — a long word's romaji must
                // stay fully readable at any font scale.
                AutoShrinkText(
                    text = current.prompt,
                    style = MaterialTheme.typography.displaySmall,
                    minFontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
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
                // Hear the reading — the same sound the romaji prompt already
                // gives, so it never reveals the glyph shape being tested.
                // Before Submit the Hint shares this row (TEXT-ONLY here,
                // showGlyph = false — the shape IS the hidden answer). After
                // Submit the answer's typed (printed) form appears right here,
                // in the hint's place, with the "how to remember" pill beside
                // it — so the reveal lands next to the prompt instead of a tall
                // block that pushes the header content down.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PronunciationButton(
                        glyph = current.answer,
                        status = ttsStatus,
                        onSpeak = onSpeak,
                    )
                    if (!quiz.submitted && mnemonicsEnabled && quiz.showHints) {
                        MnemonicHintButton(
                            unitCharacters = unitCharactersFor(current.answer),
                            showGlyph = false,
                            mnemonicChoices = mnemonicChoices,
                            onChoose = onChooseMnemonic,
                            customs = customMnemonics,
                        )
                    }
                    if (quiz.revealed) {
                        AutoShrinkText(
                            text = current.answer,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = PrintFontFamily,
                            ),
                            minFontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = 8.dp),
                        )
                        if (mnemonicsEnabled) {
                            MnemonicBadge(
                                unitCharacters = answerUnits,
                                iconOnly = true,
                                mnemonicChoices = mnemonicChoices,
                                onChoose = onChooseMnemonic,
                                customs = customMnemonics,
                            )
                        }
                        // The word's kanji, for reference (opt-in; compact in
                        // this tight header; renders nothing when off/none).
                        KanjiBadge(
                            kanji = current.kanji.takeIf { kanjiHintsEnabled },
                            reading = current.prompt,
                            meaning = current.subtitle.orEmpty(),
                            kanjiUsage = current.kanjiUsage,
                            otherForms = current.otherKanjiForms,
                            compact = true,
                        )
                    }
                }
            }
            // Always fills the header height so the Grid switch pins to the
            // BOTTOM — right above the canvas's top-right corner. The
            // stroke-order demo (shown only after Submit) sits at the TOP and
            // claims the leftover height via weight(1f); before Submit an empty
            // spacer holds that space so the switch stays put. The revealed
            // answer glyph no longer lives here — it moved into the prompt's
            // hint row on the left, so the reveal doesn't push this column
            // taller.
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
            ) {
                // The stroke-order demo: shows HOW the answer is written
                // (order + direction), not just the finished shape, so a
                // missed attempt teaches the fix. One kana → the single-
                // character demo; a word → a per-kana strip. Auto-plays on
                // reveal and can be replayed/slowed with its own controls.
                if (quiz.revealed && hasStrokeDemo) {
                    if (answerUnits.size == 1) {
                        AnimatedStrokePreview(
                            character = answerUnits[0],
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp),
                        )
                    } else {
                        WordStrokePreview(
                            characters = answerUnits,
                            modifier = Modifier
                                .weight(1f)
                                .width(132.dp)
                                .padding(top = 2.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
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
                brushEffect = brushEffect,
                characterForm = characterForm,
                glyphStrokes = if (characterForm == CharacterForm.HANDWRITTEN) {
                    glyphStrokesFor(current.glyphs)
                } else {
                    emptyList()
                },
                // Feedback fills the reserved strip inside the paper (above
                // the writing square) after Submit — same slot as free
                // practice; a word's average gets the reserved footer strip
                // below the square.
                headerHeight = if (isWord) 108.dp else 84.dp,
                header = {
                    if (quiz.submitted) {
                        if (isWord && lastWordScores != null) {
                            WordScoreChips(
                                scores = lastWordScores,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            lastScore?.let { score ->
                                ScoreFeedbackCard(
                                    score = score,
                                    glyph = lastScoredCharacter,
                                )
                            }
                        }
                    }
                },
                footerHeight = if (isWord) 44.dp else 0.dp,
                footer = {
                    if (quiz.submitted) {
                        lastWordAverage?.let { avg ->
                            WordAverageCard(average = avg)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
            )
        }

        // No self-grade — handwriting is auto-scored. Submit scores + reveals the
        // guide strokes; Next advances (feedback clears). Exit (confirmed) · Undo ·
        // Clear · Submit/Next. There is deliberately no Back to a previous item:
        // the quiz moves forward only, and Undo instead reverts the last stroke.
        val hasInk = canvasState.hasInk
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.10f * fontGrow)
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
            // Undo reverts just the latest committed stroke, so a slip on a
            // many-stroke character or word doesn't force a full Clear. Only
            // meaningful while writing (before Submit) with ink to remove.
            OutlinedButton(
                onClick = { canvasState.undoLastStroke() },
                enabled = canvasState.canUndo && !quiz.submitted,
                contentPadding = compact,
                modifier = Modifier.weight(1f),
            ) {
                Text("Undo")
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
    onRetryWrong: () -> Unit,
    onRetryBelowScore: (Int) -> Unit,
    onClose: () -> Unit,
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
    unitCharactersFor: (String) -> List<HiraganaCharacter> = { emptyList() },
    mnemonicsEnabled: Boolean = true,
    mnemonicChoices: Map<String, String> = emptyMap(),
    onChooseMnemonic: (String, String?) -> Unit = { _, _ -> },
    customMnemonics: CustomMnemonicStore = CustomMnemonicStore(),
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
                // Reading-summary group order (hoisted so rememberSaveable is
                // called unconditionally): true = Wrong→Skipped→Correct.
                var wrongFirst by rememberSaveable { mutableStateOf(true) }
                // Writing-summary sort order + the "retake under a score" cutoff,
                // hoisted for the same reason (called on every summary, used only
                // in the writing branch). true = lowest score first (needs work).
                var lowScoreFirst by rememberSaveable { mutableStateOf(true) }
                var retakeThreshold by rememberSaveable { mutableStateOf(80) }
                Text(
                    text = "Quiz complete",
                    style = MaterialTheme.typography.headlineSmall,
                )
                // "answered" counts items actually submitted (regardless of
                // whether scoring produced a headline — e.g. ScoringMode.OFF);
                // "scored" backs the writing average and excludes the unscored.
                val reading = quiz.kind == QuizKind.READING
                val answered = quiz.items.count { it.key in quiz.submittedKeys }
                val scored = quiz.items.mapNotNull { quiz.results[it.key] }
                Text(
                    text = when {
                        answered == 0 -> "You skipped every item"
                        // Reading is pass/fail: a result of 100 means correct.
                        reading -> {
                            val correct = scored.count { it >= 100 }
                            "Answered $answered of ${quiz.items.size} · $correct correct"
                        }
                        scored.isNotEmpty() ->
                            "Wrote $answered of ${quiz.items.size} · average ${scored.average().roundToInt()}"
                        else -> "Wrote $answered of ${quiz.items.size}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (quiz.timerEnabled && quiz.elapsedMillis != null) {
                    Text(
                        text = "Total time ${formatQuizTime(quiz.elapsedMillis)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reading) {
                    // Group the results by outcome so wrong/skipped/correct
                    // aren't scattered through the list. One tap flips the group
                    // order between Wrong→Skipped→Correct and Correct→Skipped→
                    // Wrong (alphabetical by outcome, Skipped always the middle).
                    // Session-local, like the Library's list sort.
                    val correct = quiz.items.filter { quiz.results[it.key] == 100 }
                    val wrong = quiz.items.filter { quiz.results[it.key] == 0 }
                    val skipped = quiz.items.filter { it.key !in quiz.results }
                    val groups = if (wrongFirst) {
                        listOf("Wrong" to wrong, "Skipped" to skipped, "Correct" to correct)
                    } else {
                        listOf("Correct" to correct, "Skipped" to skipped, "Wrong" to wrong)
                    }
                    // A "needs work first" sort is meaningless with nothing to
                    // review, so a mistake-free set swaps the toggle for a hype
                    // line instead (scaled by how many were gotten right, the
                    // same escalating-tier idea as the live streak lines).
                    if (wrong.isEmpty()) {
                        if (correct.isNotEmpty()) {
                            val perfectMessage = remember(correct.size) {
                                StreakEncouragement.perfectSetMessage(correct.size)
                            }
                            Text(
                                text = perfectMessage,
                                style = MaterialTheme.typography.titleMedium,
                                color = correctColor(LocalKanaColors.current.isDark),
                            )
                        }
                    } else {
                        TextButton(onClick = { wrongFirst = !wrongFirst }) {
                            Text(if (wrongFirst) "Sorted: needs work first" else "Sorted: correct first")
                        }
                    }
                    groups.forEach { (label, items) ->
                        if (items.isNotEmpty()) {
                            Text(
                                text = "$label · ${items.size}",
                                style = MaterialTheme.typography.titleSmall,
                                color = when (label) {
                                    "Correct" -> correctColor(LocalKanaColors.current.isDark)
                                    "Wrong" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            items.forEach { item ->
                                ReadingSummaryRow(
                                    item = item,
                                    onSpeak = onSpeak,
                                    ttsStatus = ttsStatus,
                                    unitCharacters = unitCharactersFor(item.answer),
                                    mnemonicsEnabled = mnemonicsEnabled,
                                    mnemonicChoices = mnemonicChoices,
                                    onChooseMnemonic = onChooseMnemonic,
                                    customMnemonics = customMnemonics,
                                )
                            }
                        }
                    }
                } else {
                    // Sort the written items by their score so the ones that need
                    // work group together. Unscored items (skipped, or under
                    // ScoringMode.OFF) have no score to rank, so they always sink
                    // to the end regardless of direction. Session-local, like the
                    // reading group order and the Library's list sort.
                    val (scoredItems, unscoredItems) =
                        quiz.items.partition { quiz.results[it.key] != null }
                    val orderedScored = if (lowScoreFirst) {
                        scoredItems.sortedBy { quiz.results[it.key] }
                    } else {
                        scoredItems.sortedByDescending { quiz.results[it.key] }
                    }
                    val orderedItems = orderedScored + unscoredItems
                    if (scoredItems.size > 1) {
                        TextButton(onClick = { lowScoreFirst = !lowScoreFirst }) {
                            Text(
                                if (lowScoreFirst) {
                                    "Sorted: lowest score first"
                                } else {
                                    "Sorted: highest score first"
                                },
                            )
                        }
                    }
                    orderedItems.forEach { item ->
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
                            // Writing shows the numeric quality score against its
                            // bands. "·" is an item done but unscored
                            // (ScoringMode.OFF); "Skipped" a genuinely-unanswered.
                            val wasAnswered = item.key in quiz.submittedKeys
                            Text(
                                text = when {
                                    score == null -> if (wasAnswered) "·" else "Skipped"
                                    else -> score.toString()
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
                }
                // Drill just what wasn't answered correctly (wrong + skipped).
                // Hidden on a perfect round and for writing quizzes.
                if (reading) {
                    val missed = quiz.items.count { quiz.results[it.key] != 100 }
                    if (missed > 0) {
                        Button(
                            onClick = onRetryWrong,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Retake the ones I missed ($missed)")
                        }
                    }
                } else {
                    // Writing: retake every item that scored below a cutoff the
                    // learner sets here (default 80, stepped elegantly). Only
                    // meaningful once something was actually scored — hidden when
                    // scoring produced no headlines (e.g. ScoringMode.OFF).
                    if (scored.isNotEmpty()) {
                        val isWordQuiz = quiz.items.firstOrNull() is WordQuizItem
                        val underCount = quiz.items.count { item ->
                            val s = quiz.results[item.key]
                            s != null && s < retakeThreshold
                        }
                        Text(
                            text = "Retake the ones that need work",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Set a target score. You'll re-take every item " +
                                "you scored below it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        QuizScoreThresholdStepper(
                            value = retakeThreshold,
                            onValueChange = { retakeThreshold = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val noun = when {
                            underCount == 1 && isWordQuiz -> "word"
                            isWordQuiz -> "words"
                            underCount == 1 -> "character"
                            else -> "characters"
                        }
                        Button(
                            onClick = { onRetryBelowScore(retakeThreshold) },
                            enabled = underCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (underCount > 0) {
                                    "Retake $underCount $noun under $retakeThreshold"
                                } else {
                                    "Nothing under $retakeThreshold"
                                },
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onQuizAgain,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Quiz again")
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

/**
 * One reading-summary row: glyph + reading, then the mnemonic-info icon and the
 * audio button in FIXED-width trailing slots so those icons line up in a
 * vertical column across rows — the mnemonic slot renders nothing for kana
 * without a mnemonic and the audio slot nothing for a silent mark, but the
 * reserved width keeps the alignment either way. The outcome (Correct/Wrong/
 * Skipped) is stated by the section header above, so no variable-width result
 * label is needed on the row.
 */
@Composable
private fun ReadingSummaryRow(
    item: com.hive.hiragana.presentation.viewmodels.QuizItem,
    onSpeak: (String) -> Unit,
    ttsStatus: VoiceStatus,
    unitCharacters: List<HiraganaCharacter>,
    mnemonicsEnabled: Boolean,
    mnemonicChoices: Map<String, String>,
    onChooseMnemonic: (String, String?) -> Unit,
    customMnemonics: CustomMnemonicStore,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.answer,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = PrintFontFamily),
        )
        Text(
            text = SpecialKana.readingLabel(item.answer) ?: item.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (mnemonicsEnabled) {
                MnemonicBadge(
                    unitCharacters = unitCharacters,
                    mnemonicChoices = mnemonicChoices,
                    onChoose = onChooseMnemonic,
                    customs = customMnemonics,
                    iconOnly = true,
                )
            }
        }
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            PronunciationButton(
                glyph = item.answer,
                status = ttsStatus,
                onSpeak = onSpeak,
            )
        }
    }
}

/**
 * Score-cutoff editor for the writing summary's "retake the ones that need
 * work" button: a numeric box flanked by −/+ steppers, with a 70 quick-pick
 * chip on the LEFT and a 90 on the RIGHT — the whole cluster horizontally
 * centered. Typing works the other way; a chip jumps straight to it. Clamps to
 * 1..99 (a whole number under a perfect 100) and snaps the box back. Wrapped in
 * a centered [FlowRow] so the chips flow onto a second (still-centered) line if
 * a large font scale makes the single line too wide for a narrow screen.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun QuizScoreThresholdStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun clamp(n: Int) = n.coerceIn(1, 99)
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 70 quick-pick on the left of the centered stepper.
        FilterChip(
            modifier = Modifier.align(Alignment.CenterVertically),
            selected = value == 70,
            onClick = { onValueChange(clamp(70)) },
            label = { Text("70") },
        )
        // The − [value] + stepper stays one unbreakable unit so it never splits
        // across a wrap.
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onValueChange(clamp(value - 1)) },
                enabled = value > 1,
            ) {
                Text(text = "−", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (new.length <= 2 && new.all { it.isDigit() }) {
                        val n = new.toIntOrNull()
                        if (n == null) {
                            text = new // allow a transient empty field
                        } else {
                            // Clamp on input so the box always shows the value in
                            // effect, even when the clamp lands on the current one.
                            val clamped = clamp(n)
                            text = clamped.toString()
                            onValueChange(clamped)
                        }
                    }
                },
                modifier = Modifier.width(72.dp * fontScaleFraction()),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            FilledTonalIconButton(
                onClick = { onValueChange(clamp(value + 1)) },
                enabled = value < 99,
            ) {
                Text(text = "+", style = MaterialTheme.typography.titleMedium)
            }
        }
        // 90 quick-pick on the right of the centered stepper.
        FilterChip(
            modifier = Modifier.align(Alignment.CenterVertically),
            selected = value == 90,
            onClick = { onValueChange(clamp(90)) },
            label = { Text("90") },
        )
    }
}
