package com.hive.hiragana.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.drawing.canvas.CanvasState
import com.hive.hiragana.presentation.components.CharacterGuidePanel
import com.hive.hiragana.presentation.components.FreePracticeActionBar
import com.hive.hiragana.presentation.components.PracticeActionBar
import com.hive.hiragana.presentation.components.ScoreFeedbackCard
import com.hive.hiragana.presentation.components.TracingCanvas
import com.hive.hiragana.presentation.components.WordAverageCard
import com.hive.hiragana.presentation.components.WordScoreChips
import com.hive.hiragana.presentation.viewmodels.PracticeUiState
import com.hive.hiragana.presentation.viewmodels.PracticeViewModel
import com.hive.hiragana.presentation.viewmodels.SessionPhase

/**
 * The single screen, now driven by the Phase 3 session state machine:
 *   - LOADING: spinner
 *   - REVIEWS / NEW_CHARACTER / PRACTICE / VOCABULARY / FREE: the
 *     three-pane layout (guide 25% / canvas 65% / action bar 10%)
 *   - COMPLETE: a centered summary card with a "Keep practicing" escape
 *     hatch into FREE
 */
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canvasState = remember { CanvasState() }

    // Start from a blank sheet on every advance — each review, each
    // practice rep, each word, and every phase change bump this
    // monotonic counter (see PracticeUiState.canvasClearKey).
    LaunchedEffect(uiState.canvasClearKey) {
        canvasState.clear()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            uiState.isLoading -> CenteredBox { CircularProgressIndicator() }

            uiState.errorMessage != null -> CenteredBox {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FilledTonalButton(onClick = viewModel::retrySession) {
                        Text("Retry")
                    }
                }
            }

            uiState.quiz != null -> {
                BackHandler { viewModel.closeQuiz() }
                QuizScreen(
                    quiz = uiState.quiz ?: return@Surface,
                    // Bound to the selected source (the active-script scope, or
                    // a saved set) so the count presets, the "All" label, and
                    // the stepper match the pool startQuiz will build.
                    deckSize = uiState.quizSourceSize(uiState.quiz?.selectedSetId),
                    practiceSets = uiState.practiceSets,
                    canvasState = canvasState,
                    onSetCount = viewModel::setQuizCount,
                    onToggleGrid = viewModel::toggleQuizGrid,
                    onSelectSource = viewModel::setQuizSource,
                    onStart = viewModel::startQuiz,
                    onSubmit = {
                        // Pass the per-kana cells too so a WORD quiz item scores
                        // cell-by-cell (harmless for a single character).
                        viewModel.quizSubmit(
                            canvasState.currentSession,
                            canvasState.lastCell,
                            canvasState.kanaCellRects,
                        )
                    },
                    onAdvance = viewModel::quizAdvance,
                    onBack = viewModel::quizBack,
                    onQuizAgain = viewModel::startQuiz,
                    onClose = viewModel::closeQuiz,
                    scoringMode = uiState.scoringMode,
                    onSetScoringMode = viewModel::setScoringMode,
                    lastScore = uiState.lastScore,
                    lastScoredCharacter = uiState.lastScoredCharacter,
                    lastWordScores = uiState.lastWordScores,
                    lastWordAverage = uiState.lastWordAverage,
                    inkColor = Color(uiState.brushInkColor),
                )
            }

            uiState.isLibraryOpen -> {
                BackHandler { viewModel.closeLibrary() }
                LibraryScreen(
                    characters = uiState.characters,
                    words = uiState.allWords,
                    learnedCharacterIds = uiState.learnedCharacterIds,
                    unlockedWordKana = uiState.unlockedWordKana,
                    scoreStats = uiState.scoreStats,
                    activeScript = uiState.activeScript,
                    onSelectScript = viewModel::setActiveScript,
                    includeDiacritics = uiState.includeDiacritics,
                    onToggleDiacritics = viewModel::setIncludeDiacritics,
                    includeYoon = uiState.includeYoon,
                    onToggleYoon = viewModel::setIncludeYoon,
                    includeExtended = uiState.includeExtended,
                    onToggleExtended = viewModel::setIncludeExtended,
                    onPracticeCharacter = viewModel::practiceCharacter,
                    onPracticeWord = viewModel::practiceWord,
                    onStartQuiz = { viewModel.openQuiz() },
                    onClose = viewModel::closeLibrary,
                    isGridView = uiState.isLibraryGridView,
                    onSetGridView = viewModel::setLibraryGridView,
                    pronunciation = uiState::pronunciationOf,
                    brushInkColor = uiState.brushInkColor,
                    onSelectBrushColor = viewModel::setBrushInkColor,
                    scoringMode = uiState.scoringMode,
                    onSetScoringMode = viewModel::setScoringMode,
                    practiceSets = uiState.practiceSets,
                    onSaveSet = viewModel::savePracticeSet,
                    onUpdateSetMembers = viewModel::updatePracticeSetMembers,
                    onRenameSet = viewModel::renamePracticeSet,
                    onDeleteSet = viewModel::deletePracticeSet,
                    onLaunchSetQuiz = { id -> viewModel.openQuiz(id) },
                    onPracticeSet = viewModel::practiceSet,
                    openSetsManagerSignal = uiState.pendingOpenSetsManager,
                    onConsumeOpenSetsManager = viewModel::consumeOpenSetsManager,
                )
            }

            uiState.phase == SessionPhase.COMPLETE -> SessionCompleteContent(
                uiState = uiState,
                onStartNextSession = viewModel::startNextSession,
                onKeepPracticing = viewModel::startFreePractice,
                onOpenLibrary = viewModel::openLibrary,
            )

            else -> PracticeContent(
                uiState = uiState,
                canvasState = canvasState,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun PracticeContent(
    uiState: PracticeUiState,
    canvasState: CanvasState,
    viewModel: PracticeViewModel,
) {
    val character = uiState.currentCharacter
    val isFree = uiState.phase == SessionPhase.FREE
    val activeWord = when {
        uiState.phase == SessionPhase.VOCABULARY -> uiState.currentWord
        isFree -> uiState.freeWord
        else -> null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CharacterGuidePanel(
            character = character,
            // Deck position is only meaningful in FREE; passing total = 0
            // during session phases hides the "character N of M" line
            // (session progress lives in sessionLabel instead).
            position = if (isFree) uiState.currentIndex + 1 else 0,
            total = if (isFree) uiState.freeCharacters.size else 0,
            sessionLabel = uiState.sessionProgress,
            word = activeWord,
            wordCharacters = activeWord?.let(uiState::wordUnitCharacters) ?: emptyList(),
            pronunciation = activeWord?.let(uiState::pronunciationOf),
            isNewWord = uiState.isCurrentWordNew,
            onOpenLibrary = viewModel::openLibrary,
            isGridEnabled = uiState.isGridEnabled,
            onToggleGrid = viewModel::toggleGrid,
            isGhostEnabled = uiState.isGhostEnabled,
            onToggleGhost = viewModel::toggleGhost,
            practiceLayout = uiState.practiceLayout,
            onSelectLayout = viewModel::setPracticeLayout,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.25f),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f),
        ) {
            TracingCanvas(
                canvasState = canvasState,
                characters = uiState.canvasGlyphs,
                showGrid = uiState.isGridEnabled,
                showGhost = uiState.isGhostEnabled,
                layout = uiState.practiceLayout,
                inkColor = Color(uiState.brushInkColor),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
            )
            // Advisory quality readout floats over the sheet where the writing
            // area (centred) won't be covered. A scored WHOLE WORD shows a
            // per-kana chip row above and its average below; a single character
            // shows the one feedback card.
            val wordScores = uiState.lastWordScores
            if (wordScores != null) {
                WordScoreChips(
                    scores = wordScores,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp, start = 8.dp, end = 8.dp),
                )
                uiState.lastWordAverage?.let { avg ->
                    WordAverageCard(
                        average = avg,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                    )
                }
            } else {
                uiState.lastScore?.let { score ->
                    ScoreFeedbackCard(
                        score = score,
                        glyph = uiState.lastScoredCharacter,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp),
                    )
                }
            }
        }

        if (isFree) {
            // FREE practice: two-step Submit→Next (mirrors the quiz). "Again"
            // rewrites the same character; Next advances the character deck.
            // Word free practice is a single repeating sheet — no Next.
            FreePracticeActionBar(
                hasInk = canvasState.hasInk,
                submitted = uiState.freeSubmitted,
                // Next cycles the deck: a character deck, or a word-SET queue
                // (>1 word). A lone word (library word tap) has nothing to
                // advance to, so it stays a single repeating sheet.
                canAdvance = if (uiState.freeWord != null) {
                    uiState.freeWords.size > 1
                } else {
                    uiState.freeCharacters.isNotEmpty()
                },
                // Multi-copy 2×2/3×3 character practice is a repetition
                // worksheet (Clear·Next, no scoring); words keep the two-step.
                repetitionMode = uiState.freeWord == null &&
                    uiState.practiceLayout != PracticeLayout.SINGLE,
                // Scoring Off (Study Scope → "Score my writing" = Off) drops
                // the Submit/score step, leaving just Clear · Next.
                scoringEnabled = uiState.scoringMode != ScoringMode.OFF,
                onClear = { canvasState.clear() },
                onSubmit = {
                    viewModel.freeSubmit(
                        canvasState.currentSession,
                        canvasState.lastCell,
                        canvasState.kanaCellRects,
                    )
                },
                onRetry = viewModel::freeRetry,
                onNext = viewModel::advanceToNextCharacter,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.10f),
            )
        } else {
            PracticeActionBar(
                hasInk = canvasState.hasInk,
                onClear = { canvasState.clear() },
                onGrade = { grade ->
                    viewModel.gradeCurrentCharacter(
                        grade,
                        canvasState.currentSession,
                        canvasState.lastCell,
                        canvasState.kanaCellRects,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.10f),
            )
        }
    }
}

@Composable
private fun SessionCompleteContent(
    uiState: PracticeUiState,
    onStartNextSession: () -> Unit,
    onKeepPracticing: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    CenteredBox {
        Card {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Session complete",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (uiState.allCaughtUp) {
                    Text(
                        text = "You're all caught up — nothing due right now.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = "Reviews done: ${uiState.reviewsDone}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    uiState.newCharacterLearned?.let { learned ->
                        Text(
                            text = "New character learned: $learned",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (uiState.wordsUnlocked.isNotEmpty()) {
                        Text(
                            text = "Words unlocked: ${uiState.wordsUnlocked.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "Words practiced: ${uiState.wordsPracticed}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // The primary continuation: regenerate a session from the
                // just-updated cards (usually introduces the next character).
                // Pointless when the generator already came back empty —
                // that's what allCaughtUp means.
                if (!uiState.allCaughtUp) {
                    Button(onClick = onStartNextSession) {
                        Text("Start next session")
                    }
                }
                if (uiState.hasLearnedCharacters) {
                    FilledTonalButton(onClick = onKeepPracticing) {
                        Text("Keep practicing")
                    }
                }
                OutlinedButton(onClick = onOpenLibrary) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Browse library")
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
