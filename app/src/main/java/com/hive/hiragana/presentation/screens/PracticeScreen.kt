package com.hive.hiragana.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import com.hive.hiragana.presentation.components.ScoreFeedbackCard
import com.hive.hiragana.presentation.components.TracingCanvas
import com.hive.hiragana.presentation.components.WordAverageCard
import com.hive.hiragana.presentation.components.WordScoreChips
import com.hive.hiragana.presentation.viewmodels.PracticeUiState
import com.hive.hiragana.presentation.viewmodels.PracticeViewModel

/**
 * The single screen:
 *   - LOADING: spinner
 *   - FREE: the three-pane layout (guide 25% / canvas 65% / action bar 10%)
 *   - the library and quiz overlays render above FREE.
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
                    initialTab = uiState.librarySection,
                    onSelectTab = viewModel::setLibrarySection,
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
    val activeWord = uiState.freeWord

    Column(modifier = Modifier.fillMaxSize()) {
        CharacterGuidePanel(
            character = character,
            position = uiState.currentIndex + 1,
            total = uiState.freeCharacters.size,
            word = activeWord,
            wordCharacters = activeWord?.let(uiState::wordUnitCharacters) ?: emptyList(),
            pronunciation = activeWord?.let(uiState::pronunciationOf),
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
                    // Inset past the paper card's 14dp edge + its 20dp rounded
                    // corner (and drop below the corner curve) so every chip —
                    // leftmost and rightmost included — sits fully inside the
                    // white canvas on any screen width.
                    scores = wordScores,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 22.dp, end = 22.dp),
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

        // Free practice: two-step Submit→Next (mirrors the quiz). "Again"
        // rewrites the same character; Next advances the character deck.
        // A lone word (library word tap) is a single repeating sheet — no Next.
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
            // Any multi-copy 2×2/3×3 sheet — character OR word — is a
            // repetition worksheet (Clear·Next, no Submit/scoring); scoring
            // is a 1×1-only feature, so only the SINGLE layout keeps the
            // Submit two-step.
            repetitionMode = uiState.practiceLayout != PracticeLayout.SINGLE,
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
