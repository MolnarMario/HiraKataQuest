package com.hive.hiragana.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.hive.hiragana.presentation.components.fontScaleFraction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.hive.hiragana.domain.update.UpdateState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.presentation.viewmodels.QuizKind
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

    // Re-check the Japanese TTS voice each time the app resumes, so a voice the
    // user just installed (via the pronunciation button's download prompt)
    // flips the button from "download" to "play" without a restart.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTtsAvailability()
    }

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
        // The Surface paints the washi background edge-to-edge (behind the
        // system bars — see enableEdgeToEdge in MainActivity); the routing Box
        // insets ALL content by safeDrawing (status/nav bars + display cutout
        // + keyboard), so every when{} branch below is covered by this one
        // modifier and nothing renders under bars or cutouts.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
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

            uiState.appMode == null -> {
                // Home: pick a track. Back here falls through to the system (exit).
                OpeningScreen(onPick = viewModel::setAppMode)
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
                    characterForm = CharacterForm.HANDWRITTEN,
                    glyphStrokesFor = uiState::ghostStrokesFor,
                    onReadingResult = viewModel::readingResult,
                    onSetAnswerStyle = viewModel::setReadingAnswerStyle,
                    onSetTestMeaning = viewModel::setReadingTestMeaning,
                    unitCharactersFor = uiState::unitCharacters,
                    onSpeak = viewModel::speakTts,
                    ttsStatus = uiState.ttsStatus,
                )
            }

            uiState.readingFlashcard != null -> {
                BackHandler { viewModel.closeReadingFlashcards() }
                ReadingFlashcardScreen(
                    state = uiState.readingFlashcard ?: return@Surface,
                    onReveal = viewModel::flashcardReveal,
                    onNext = viewModel::flashcardNext,
                    onClose = viewModel::closeReadingFlashcards,
                    onSpeak = viewModel::speakTts,
                    ttsStatus = uiState.ttsStatus,
                    unitCharactersFor = uiState::unitCharacters,
                )
            }

            uiState.appMode == AppMode.READING || uiState.isLibraryOpen -> {
                // The Library is the shared hub: Reading always lands here;
                // Writing opens it on demand. Hardware Back goes to the opening
                // "home" screen (home → Back → exit). The in-app Back arrow
                // returns to writing practice via onClose.
                BackHandler { viewModel.goToOpening() }
                LibraryScreen(
                    characters = uiState.characters,
                    words = uiState.allWords,
                    writingScoreStats = uiState.scoreStats,
                    readingStats = uiState.readingStats,
                    activeScript = uiState.activeScript,
                    onSelectScript = viewModel::setActiveScript,
                    includeDiacritics = uiState.includeDiacritics,
                    onToggleDiacritics = viewModel::setIncludeDiacritics,
                    includeYoon = uiState.includeYoon,
                    onToggleYoon = viewModel::setIncludeYoon,
                    includeExtended = uiState.includeExtended,
                    onToggleExtended = viewModel::setIncludeExtended,
                    onPracticeCharacter = { ch ->
                        // Reading mode: tapping an item opens the flashcard flip;
                        // Writing mode: free handwriting practice (as before).
                        if (uiState.appMode == AppMode.READING) {
                            viewModel.openReadingFlashcardsForCharacter(ch)
                        } else {
                            viewModel.practiceCharacter(ch)
                        }
                    },
                    onPracticeWord = { w ->
                        if (uiState.appMode == AppMode.READING) {
                            viewModel.openReadingFlashcardsForWord(w)
                        } else {
                            viewModel.practiceWord(w)
                        }
                    },
                    onStartQuiz = {
                        viewModel.openQuiz(
                            kind = if (uiState.appMode == AppMode.READING) {
                                QuizKind.READING
                            } else {
                                QuizKind.WRITING
                            },
                        )
                    },
                    onClose = {
                        // Reading has no underlying tracing screen to return to,
                        // so its Library closes to the opening "home".
                        if (uiState.appMode == AppMode.READING) {
                            viewModel.goToOpening()
                        } else {
                            viewModel.closeLibrary()
                        }
                    },
                    isGridView = uiState.isLibraryGridView,
                    onSetGridView = viewModel::setLibraryGridView,
                    appMode = uiState.appMode ?: AppMode.WRITING,
                    onSetAppMode = viewModel::setAppMode,
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
                    onLaunchSetQuiz = { id ->
                        viewModel.openQuiz(
                            id,
                            kind = if (uiState.appMode == AppMode.READING) {
                                QuizKind.READING
                            } else {
                                QuizKind.WRITING
                            },
                        )
                    },
                    onPracticeSet = viewModel::practiceSet,
                    openSetsManagerSignal = uiState.pendingOpenSetsManager,
                    onConsumeOpenSetsManager = viewModel::consumeOpenSetsManager,
                    onSpeak = viewModel::speakTts,
                    ttsStatus = uiState.ttsStatus,
                )
            }

            else -> {
                // Free practice is a "detail" screen: hardware Back returns to
                // the Library rather than exiting the app.
                BackHandler { viewModel.openLibrary() }
                PracticeContent(
                    uiState = uiState,
                    canvasState = canvasState,
                    viewModel = viewModel,
                )
            }
        }

            // Update-available overlay — a Dialog window, so it floats over any
            // branch above (incl. the opening/home screen where it usually appears).
            UpdateDialog(
                state = uiState.updateState,
                onUpdate = viewModel::startUpdate,
                onRetry = viewModel::retryUpdate,
                onLater = viewModel::dismissUpdate,
                onSkip = viewModel::skipUpdate,
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

    // The guide panel and action bar hold text that grows with the system
    // font scale, but their 25%/10% height shares were hand-tuned at scale 1
    // — grow both with the font (weights are relative, so the canvas simply
    // absorbs the difference) instead of letting the panel texts clip.
    val fontGrow = fontScaleFraction(1.4f)
    Column(modifier = Modifier.fillMaxSize()) {
        CharacterGuidePanel(
            character = character,
            positionLabel = uiState.practicePositionLabel,
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
            onSpeak = viewModel::speakTts,
            ttsStatus = uiState.ttsStatus,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.25f * fontGrow),
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
                // Writing always traces the handwritten (KanjiVG) ghost — the
                // form you actually write, coherent with the demo and scorer.
                characterForm = CharacterForm.HANDWRITTEN,
                glyphStrokes = uiState.canvasGhostStrokes,
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
                .weight(0.10f * fontGrow),
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

/**
 * The in-app update flow, rendered as a dialog window (so it floats over whatever
 * screen is showing). [UpdateState.Idle]/[UpdateState.Checking]/
 * [UpdateState.UpToDate]/[UpdateState.Installing] render nothing. See
 * [com.hive.hiragana.domain.update.AppUpdater].
 */
@Composable
private fun UpdateDialog(
    state: UpdateState,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("Update available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${state.info.versionName} is ready to install.")
                    if (state.info.notes.isNotBlank()) {
                        Text(
                            text = state.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Calm the sideload-install scare: Android/Play Protect shows a
                    // security warning for apps not from the Play Store. Tell the
                    // user up front so "Install anyway" reads as expected, not alarming.
                    Text(
                        text = "Heads up: Android may show a security warning — tap " +
                            "\"More details\", then \"Install anyway\". That's expected " +
                            "for apps installed outside the Play Store.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onUpdate) { Text("Update") } },
            dismissButton = {
                Row {
                    TextButton(onClick = onSkip) { Text("Skip") }
                    TextButton(onClick = onLater) { Text("Later") }
                }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            // Non-cancellable while downloading (tapping outside / Back does nothing).
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Version ${state.info.versionName} — ${state.percent}%")
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("Update failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onRetry) { Text("Try again") } },
            dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
        )

        UpdateState.Idle,
        UpdateState.Checking,
        UpdateState.Installing,
        UpdateState.UpToDate -> Unit
    }
}
