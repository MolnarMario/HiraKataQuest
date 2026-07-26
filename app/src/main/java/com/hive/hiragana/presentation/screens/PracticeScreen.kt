package com.hive.hiragana.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.domain.model.CharacterForm
import com.hive.hiragana.domain.model.KanaMnemonics
import com.hive.hiragana.domain.model.PracticeLayout
import com.hive.hiragana.domain.model.PrintDivergence
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.presentation.viewmodels.QuizKind
import com.hive.hiragana.presentation.viewmodels.QuizStage
import com.hive.hiragana.presentation.viewmodels.SessionPhase
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.drawing.canvas.CanvasState
import com.hive.hiragana.presentation.components.CharacterGuidePanel
import com.hive.hiragana.presentation.components.CustomMnemonicStore
import com.hive.hiragana.presentation.components.FreePracticeActionBar
import com.hive.hiragana.presentation.components.MnemonicBadge
import com.hive.hiragana.presentation.components.PrintDivergenceBadge
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

    // The learner's own mnemonics + add/delete sinks, bundled once for every
    // mnemonic dialog surface (footer chip, flashcards, both quizzes).
    val customMnemonicStore = remember(uiState.customMnemonics) {
        CustomMnemonicStore(
            byGlyph = uiState.customMnemonics,
            onAdd = viewModel::addCustomMnemonic,
            onDelete = viewModel::deleteCustomMnemonic,
        )
    }

    // Re-check the Japanese TTS voice each time the app resumes, so a voice the
    // user just installed (via the pronunciation button's download prompt)
    // flips the button from "download" to "play" without a restart.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTtsAvailability()
    }

    // Auto-narration (opt-in, Writing only): speak each newly shown
    // character/word aloud the instant it appears — in free practice AND the
    // writing quiz. Driven off UI state here (not the ViewModel) so it covers
    // every transition into a new glyph — Next, a Library pick, a scope change,
    // each quiz item — with one rule. Reading never narrates (hearing the answer
    // defeats recognition). The status gate matters: play() no-ops until the
    // voice is READY, so we key on it too and remember the last glyph we spoke
    // so returning from an overlay (Library) to the same glyph doesn't repeat.
    val narrateGlyph: String? = when {
        !uiState.autoNarrateEnabled -> null
        uiState.ttsStatus != VoiceStatus.READY -> null
        uiState.quiz != null -> uiState.quiz
            ?.takeIf { it.kind == QuizKind.WRITING && it.stage == QuizStage.RUNNING }
            ?.current?.answer
        uiState.appMode == AppMode.WRITING &&
            !uiState.isLibraryOpen &&
            uiState.readingFlashcard == null &&
            uiState.phase == SessionPhase.FREE ->
            uiState.freeWord?.kana ?: uiState.currentCharacter?.character
        else -> null
    }
    var lastNarrated by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(narrateGlyph) {
        val glyph = narrateGlyph ?: return@LaunchedEffect
        if (glyph != lastNarrated) {
            viewModel.speakTts(glyph)
            lastNarrated = glyph
        }
    }

    // Reading answer read-aloud (opt-out, Reading only): the mirror of the
    // narration above — Reading must NOT speak a glyph when it appears (that
    // would give the recognition answer away), so it speaks once the answer is
    // REVEALED instead: after a flashcard Check/choice tap and after a
    // reading-quiz answer, covering both surfaces with one rule. No dedup
    // guard needed: the key drops to null between items (revealed resets on
    // advance), so each reveal is its own transition — and unlike narration
    // there is no overlay that can re-show an already-revealed item.
    val answerGlyph: String? = when {
        !uiState.readingAutoSpeakEnabled -> null
        uiState.ttsStatus != VoiceStatus.READY -> null
        uiState.readingFlashcard?.revealed == true ->
            uiState.readingFlashcard?.current?.glyph
        uiState.quiz?.kind == QuizKind.READING &&
            uiState.quiz?.stage == QuizStage.RUNNING &&
            uiState.quiz?.revealed == true ->
            uiState.quiz?.current?.answer
        else -> null
    }
    LaunchedEffect(answerGlyph) {
        if (answerGlyph != null) viewModel.speakTts(answerGlyph)
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
                OpeningScreen(
                    onPick = viewModel::setAppMode,
                    beginnerMode = uiState.beginnerMode,
                    onSetBeginnerMode = viewModel::setBeginnerMode,
                )
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
                    brushEffect = uiState.brushEffect,
                    characterForm = CharacterForm.HANDWRITTEN,
                    glyphStrokesFor = uiState::ghostStrokesFor,
                    onReadingResult = viewModel::readingResult,
                    onSetAnswerStyle = viewModel::setReadingAnswerStyle,
                    onSetTestMeaning = viewModel::setReadingTestMeaning,
                    unitCharactersFor = uiState::unitCharacters,
                    mnemonicsEnabled = uiState.mnemonicsEnabled,
                    mnemonicChoices = uiState.mnemonicChoices,
                    onChooseMnemonic = viewModel::chooseMnemonic,
                    customMnemonics = customMnemonicStore,
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
                    testMeaning = uiState.readingMeaningPractice,
                    onSetTestMeaning = viewModel::setReadingMeaningPractice,
                    mnemonicsEnabled = uiState.mnemonicsEnabled,
                    mnemonicChoices = uiState.mnemonicChoices,
                    onChooseMnemonic = viewModel::chooseMnemonic,
                    customMnemonics = customMnemonicStore,
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
                    beginnerMode = uiState.beginnerMode,
                    brushInkColor = uiState.brushInkColor,
                    onSelectBrushColor = viewModel::setBrushInkColor,
                    brushEffect = uiState.brushEffect,
                    onSelectBrushEffect = viewModel::setBrushEffect,
                    scoringMode = uiState.scoringMode,
                    onSetScoringMode = viewModel::setScoringMode,
                    autoNarrateEnabled = uiState.autoNarrateEnabled,
                    onSetAutoNarrate = viewModel::setAutoNarrate,
                    readingAutoSpeakEnabled = uiState.readingAutoSpeakEnabled,
                    onSetReadingAutoSpeak = viewModel::setReadingAutoSpeak,
                    mnemonicsEnabled = uiState.mnemonicsEnabled,
                    onSetMnemonics = viewModel::setMnemonicsEnabled,
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
                    onPracticeSet = { id ->
                        // Reading mode: flip the set as flashcards; Writing mode:
                        // free handwriting practice over the set (as before).
                        if (uiState.appMode == AppMode.READING) {
                            viewModel.openReadingFlashcardsForSet(id)
                        } else {
                            viewModel.practiceSet(id)
                        }
                    },
                    setsSortKey = uiState.setsSortKey,
                    onSetsSortSelected = viewModel::setSetsSort,
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Short screens (a 360dp-wide compact phone gives ~700dp of content
        // height vs ~890 on the tuning device) starve the 25% guide panel
        // until its headline glyph shrinks to a sliver. Give the panel a
        // bigger share there — the canvas is width-bound (square cells), so
        // on those same screens its 65% slot has spare white space to give.
        val panelBoost = if (maxHeight < 760.dp) 1.25f else 1f
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
                .weight(0.25f * fontGrow * panelBoost),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f),
        ) {
            // Advisory quality readout lives in a RESERVED strip inside the
            // paper card, above the writing square — empty until Submit
            // (fixed height, so the grid never moves under already-drawn
            // ink). A scored WHOLE WORD fills it with the per-kana chips
            // (its average keeps the paper's bottom margin); a single
            // character shows the one feedback card. Scoring OFF ⇒ nothing
            // ever fills the strip, so it isn't reserved at all.
            val wordScores = uiState.lastWordScores
            val isWordSheet = uiState.canvasGlyphs.size > 1
            // The footer strip below the writing square is only reserved when
            // there is something to say there: a "Looks different in print"
            // divergence, a memory hint ("How to remember"), or a word average
            // after a scored word submit.
            val footerUnits = activeWord?.let(uiState::wordUnitCharacters)
                ?: listOfNotNull(character)
            val hasDivergence = footerUnits.any {
                PrintDivergence.noteFor(it.character) != null
            }
            val hasMnemonic = uiState.mnemonicsEnabled && footerUnits.any {
                KanaMnemonics.mnemonicFor(it.character) != null
            }
            val scoredSheet = uiState.scoringMode != ScoringMode.OFF &&
                uiState.practiceLayout == PracticeLayout.SINGLE
            TracingCanvas(
                canvasState = canvasState,
                characters = uiState.canvasGlyphs,
                showGrid = uiState.isGridEnabled,
                showGhost = uiState.isGhostEnabled,
                layout = uiState.practiceLayout,
                inkColor = Color(uiState.brushInkColor),
                brushEffect = uiState.brushEffect,
                // Writing always traces the handwritten (KanjiVG) ghost — the
                // form you actually write, coherent with the demo and scorer.
                characterForm = CharacterForm.HANDWRITTEN,
                glyphStrokes = uiState.canvasGhostStrokes,
                headerHeight = when {
                    !scoredSheet -> 0.dp
                    isWordSheet -> 108.dp
                    else -> 84.dp
                },
                header = {
                    if (wordScores != null) {
                        WordScoreChips(
                            scores = wordScores,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        uiState.lastScore?.let { score ->
                            ScoreFeedbackCard(
                                score = score,
                                glyph = uiState.lastScoredCharacter,
                            )
                        }
                    }
                },
                // Reserved strip BELOW the square: the divergence chip (and,
                // after a word submit, the word average — they share it, the
                // average taking precedence). Reserved structurally so the
                // chip can never overlap the square's border (it did when it
                // floated on whatever margin was left).
                footerHeight = if (hasDivergence || hasMnemonic || (isWordSheet && scoredSheet)) {
                    44.dp
                } else {
                    0.dp
                },
                footer = {
                    val avg = uiState.lastWordAverage
                    if (wordScores != null && avg != null) {
                        WordAverageCard(average = avg)
                    } else {
                        // A plain Row, NOT a FlowRow (whose overflow silently
                        // drops children on compact screens). Both chips
                        // co-occur only on さ/き/り sheets (and words holding
                        // them); two full-text pills don't fit a 360dp strip,
                        // so the mnemonic chip collapses to icon-only and the
                        // rarer divergence warning keeps its text.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hasMnemonic) {
                                MnemonicBadge(
                                    unitCharacters = footerUnits,
                                    iconOnly = hasDivergence,
                                    mnemonicChoices = uiState.mnemonicChoices,
                                    onChoose = viewModel::chooseMnemonic,
                                    customs = CustomMnemonicStore(
                                        byGlyph = uiState.customMnemonics,
                                        onAdd = viewModel::addCustomMnemonic,
                                        onDelete = viewModel::deleteCustomMnemonic,
                                    ),
                                )
                            }
                            PrintDivergenceBadge(unitCharacters = footerUnits)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
            )
        }

        // Free practice: two-step Submit→Next (mirrors the quiz). "Again"
        // rewrites the same character/word; Next advances to the next.
        FreePracticeActionBar(
            hasInk = canvasState.hasInk,
            submitted = uiState.freeSubmitted,
            // Next cycles the deck: a character deck, or a word queue (>1
            // word — library word taps now load the whole word list). A lone
            // word (a word out of scope, or a 1-word set) has nothing to
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
            // the Submit/score step, leaving just Undo · Clear · Next.
            scoringEnabled = uiState.scoringMode != ScoringMode.OFF,
            canUndo = canvasState.canUndo,
            onUndo = { canvasState.undoLastStroke() },
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
