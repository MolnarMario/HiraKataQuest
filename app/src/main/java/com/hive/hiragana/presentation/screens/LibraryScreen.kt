package com.hive.hiragana.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.domain.model.ReadingProgress
import com.hive.hiragana.domain.model.FlowSection
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.KanaChartBuilder
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
import com.hive.hiragana.domain.scoring.ScoringMode
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.ScriptScope
import com.hive.hiragana.presentation.components.AutoShrinkText
import com.hive.hiragana.presentation.components.CategoryFilter
import com.hive.hiragana.presentation.components.ConceptInfo
import com.hive.hiragana.presentation.components.ChoiceRow
import com.hive.hiragana.presentation.components.ConceptInfoIcon
import com.hive.hiragana.presentation.components.KanaConcepts
import com.hive.hiragana.presentation.components.PrintFontFamily
import com.hive.hiragana.presentation.components.PronunciationButton
import com.hive.hiragana.domain.model.TableRow
import com.hive.hiragana.domain.model.TableSection
import com.hive.hiragana.domain.model.VocabularyWord
import com.hive.hiragana.domain.model.PracticeSet
import com.hive.hiragana.domain.model.PracticeSetType
import com.hive.hiragana.presentation.components.BrushColorPicker
import com.hive.hiragana.presentation.components.BrushEffectPicker
import com.hive.hiragana.drawing.canvas.BrushEffect
import com.hive.hiragana.presentation.components.fontScaleFraction
import com.hive.hiragana.presentation.components.rememberEqualShareTextScale
import com.hive.hiragana.presentation.components.rememberUniformTextScale
import com.hive.hiragana.presentation.components.toNonScalingSp
import com.hive.hiragana.presentation.theme.scoreSpectrumColor
import com.hive.hiragana.presentation.theme.LocalKanaColors
import com.hive.hiragana.domain.model.AppearanceMode
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.InputChip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The score used to color a cell/row: the number the learner was actually
 * shown, falling back to the raw dials for pre-2026-08-02 rows.
 *
 * This used to read `avgForm ?: avgFit` — the RAW geometry — while the
 * feedback card showed the recognition-adjusted headline, so one attempt had
 * two different numbers (an エ displayed as 65 contributed 88 here).
 */
private fun CharacterScoreSummary?.avgScore(): Int? = this?.displayAverage

/** The best score used for sorting, on the same displayed scale as [avgScore]. */
private fun CharacterScoreSummary?.bestScore(): Int? = this?.displayBest

/**
 * Compact writing-score line for a word row ("Avg 82 · Best 90"), mirroring the
 * reading-progress detail line so a word's numeric score is legible in the list
 * and not just implied by the row's green tint.
 */
private fun wordScoreSubtitle(summary: CharacterScoreSummary?): String {
    val avg = summary.avgScore() ?: return "Not practiced yet"
    val best = summary.bestScore()
    return if (best != null) "Avg $avg · Best $best" else "Avg $avg"
}

/**
 * Sort orders for the character/word lists. Writing mode uses AVG/BEST (from the
 * handwriting scorer); Reading mode uses AVG/MC/TYPE (the reading percentages).
 * DEFAULT is the incoming gojūon order.
 */
private enum class ItemSort {
    DEFAULT, AVG_DESC, AVG_ASC, BEST_DESC, BEST_ASC, MC_DESC, MC_ASC, TYPE_DESC, TYPE_ASC
}

/** Writing-mode sort options (average + best). */
private val ITEM_SORT_OPTIONS = listOf(
    ItemSort.DEFAULT to "Default order",
    ItemSort.AVG_DESC to "Average: High → Low",
    ItemSort.AVG_ASC to "Average: Low → High",
    ItemSort.BEST_DESC to "Best: High → Low",
    ItemSort.BEST_ASC to "Best: Low → High",
)

/** Reading-mode sort options: alphabetical + the three reading percentages, both ways. */
private val READING_SORT_OPTIONS = listOf(
    ItemSort.DEFAULT to "Default order",
    ItemSort.AVG_DESC to "Average: High → Low",
    ItemSort.AVG_ASC to "Average: Low → High",
    ItemSort.MC_DESC to "Multiple choice: High → Low",
    ItemSort.MC_ASC to "Multiple choice: Low → High",
    ItemSort.TYPE_DESC to "Typed: High → Low",
    ItemSort.TYPE_ASC to "Typed: Low → High",
)

/**
 * Reorder a list by handwriting-scored history (average or best). Unscored items
 * sink to the end in BOTH directions. Stable, so the gojūon order breaks ties and
 * DEFAULT is a no-op. Reading-only sort modes fall through to DEFAULT here.
 */
private fun <T> List<T>.sortedByScore(
    mode: ItemSort,
    scoreOf: (T) -> CharacterScoreSummary?,
): List<T> = when (mode) {
    ItemSort.AVG_DESC -> sortedByDescending { scoreOf(it).avgScore() ?: Int.MIN_VALUE }
    ItemSort.AVG_ASC -> sortedBy { scoreOf(it).avgScore() ?: Int.MAX_VALUE }
    ItemSort.BEST_DESC -> sortedByDescending { scoreOf(it).bestScore() ?: Int.MIN_VALUE }
    ItemSort.BEST_ASC -> sortedBy { scoreOf(it).bestScore() ?: Int.MAX_VALUE }
    else -> this
}

/**
 * Reorder a list by reading accuracy (average / multiple-choice / typed). Items
 * not yet quizzed in that style sink to the end in both directions.
 */
private fun <T> List<T>.sortedByReading(
    mode: ItemSort,
    statOf: (T) -> ReadingProgress?,
): List<T> = when (mode) {
    ItemSort.AVG_DESC -> sortedByDescending { statOf(it)?.colorPercent ?: Int.MIN_VALUE }
    ItemSort.AVG_ASC -> sortedBy { statOf(it)?.colorPercent ?: Int.MAX_VALUE }
    ItemSort.MC_DESC -> sortedByDescending { statOf(it)?.mcqPercent ?: Int.MIN_VALUE }
    ItemSort.MC_ASC -> sortedBy { statOf(it)?.mcqPercent ?: Int.MAX_VALUE }
    ItemSort.TYPE_DESC -> sortedByDescending { statOf(it)?.typePercent ?: Int.MIN_VALUE }
    ItemSort.TYPE_ASC -> sortedBy { statOf(it)?.typePercent ?: Int.MAX_VALUE }
    else -> this
}

/** Reading-mode list-row subtitle: the three percentages (– until that style is tried). */
private fun readingSubtitle(stat: ReadingProgress?): String {
    if (stat == null) return "Not quizzed yet"
    fun p(v: Int?) = v?.let { "$it%" } ?: "–"
    return "MC ${p(stat.mcqPercent)} · Typing ${p(stat.typePercent)} · Avg ${p(stat.colorPercent)}"
}

/**
 * Browsable library of every character and vocabulary word (Phase 3.1),
 * split into a Characters tab and a Words tab.
 *
 * Everything is tappable — any character or word can be freely practiced.
 * Cells/rows are tinted by their average score (neutral when unscored). Picks
 * route to [PracticeViewModel.practiceCharacter] / [PracticeViewModel.practiceWord],
 * which run plain FREE practice with no side effects.
 *
 * **Chrome layout (redesigned):** the header is a single Material 3
 * [TopAppBar] — back, title, and three actions: a collapsible search
 * (its field takes over the title slot while active), a Filter action that
 * opens the "Study scope" [ModalBottomSheet], and the "Quiz me!" CTA. All
 * the "what am I studying" scope controls (Hiragana/Katakana selector +
 * the three advanced-kana opt-ins) live in that sheet rather than stacked
 * down the screen, so the idle header is just the app bar + the tab row and
 * the list gets the vertical space. A dot on the Filter icon signals when
 * scope differs from the beginner default.
 *
 * The script selector and opt-ins are the app's "what am I studying"
 * controls: they scope not just this browsing view but also what the
 * session generator and the quiz draw from (the ViewModel rebuilds the
 * session on change). The full merged deck ([characters]/[words] hold BOTH
 * scripts and base + advanced kana) is passed in and filtered HERE via
 * [ScriptScope], so the two tab counts recompute automatically and the
 * lists only ever show what is currently in scope. All three opt-ins are
 * off by default, so a beginner only sees the base gojūon.
 *
 * The search query filters characters by glyph/romaji and words by
 * kana/meaning; both tab labels show their match counts so hits on the
 * inactive tab stay discoverable. Query, tab, search-expanded, and sheet
 * visibility are transient overlay-local UI state ([rememberSaveable]d
 * here, never in the ViewModel).
 *
 * **Practice sets** (hand-picked character/word lists, distinct from the
 * declarative scope opt-ins) live on the third tab, [SetsTabContent] (scoped
 * to the active script, with a persisted sort menu, per-set average-score
 * badge, and Quiz/Practice/Edit/Rename/Delete per set). Its "New" button picks
 * the set type, then enters a [SelectionModeContent] over that tab's FULL
 * unscoped list (tap-toggle + drag-range select, list⇄grid toggle). Selection
 * visibility is overlay-local; saving a set — and finishing a set-launched
 * quiz ([PracticeViewModel.pendingOpenSetsManager]) — both land back on the
 * Sets tab rather than the browse view.
 *
 * Rendered as a full-screen overlay above whatever phase is active; Back
 * (or the arrow) closes it — except while searching, when Back just
 * collapses the search field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    characters: List<HiraganaCharacter>,
    words: List<VocabularyWord>,
    // Writing-mode per-glyph handwriting history (avg/best). In Reading mode the
    // cells are instead colored/scored from [readingStats]; the body derives a
    // single `scoreStats` that switches on [appMode].
    writingScoreStats: Map<String, CharacterScoreSummary> = emptyMap(),
    readingStats: Map<String, ReadingProgress> = emptyMap(),
    activeScript: Script,
    onSelectScript: (Script) -> Unit,
    includeDiacritics: Boolean,
    onToggleDiacritics: (Boolean) -> Unit,
    includeYoon: Boolean,
    onToggleYoon: (Boolean) -> Unit,
    includeExtended: Boolean,
    onToggleExtended: (Boolean) -> Unit,
    onPracticeCharacter: (HiraganaCharacter) -> Unit,
    onPracticeWord: (VocabularyWord) -> Unit,
    onStartQuiz: () -> Unit,
    onClose: () -> Unit,
    isGridView: Boolean = false,
    onSetGridView: (Boolean) -> Unit = {},
    // Traced-ghost form (Printed / Handwritten) + its setter, surfaced as a
    // selector in the Study-scope sheet.
    appMode: AppMode = AppMode.WRITING,
    onSetAppMode: (AppMode) -> Unit = {},
    // Which tab to open on (0 = Characters, 1 = Words, 2 = Sets), seeded from
    // the persisted last-used section; [onSelectTab] persists the switch.
    initialTab: Int = 0,
    onSelectTab: (Int) -> Unit = {},
    pronunciation: (VocabularyWord) -> String = { "" },
    // When on, the kana-concept ⓘ help icons show in the scope sheet; off hides
    // them (Beginner mode, set on the opening screen).
    beginnerMode: Boolean = false,
    brushInkColor: Int = 0xFF20222E.toInt(),
    onSelectBrushColor: (Int) -> Unit = {},
    // The dark-appearance ink twin; the Options row edits whichever theme is
    // active. Default = InkChalk.
    brushInkColorDark: Int = 0xFFEFE7DA.toInt(),
    onSelectBrushColorDark: (Int) -> Unit = {},
    brushEffect: BrushEffect = BrushEffect.BRUSH,
    onSelectBrushEffect: (BrushEffect) -> Unit = {},
    scoringMode: ScoringMode = ScoringMode.FORM,
    onSetScoringMode: (ScoringMode) -> Unit = {},
    // Auto-narration: speak each newly shown character/word aloud (Writing only).
    autoNarrateEnabled: Boolean = false,
    onSetAutoNarrate: (Boolean) -> Unit = {},
    // Reading answer read-aloud: speak the answer once revealed (Reading only).
    readingAutoSpeakEnabled: Boolean = true,
    onSetReadingAutoSpeak: (Boolean) -> Unit = {},
    // Memory hints (mnemonics): the "How to remember" chip + quiz/flashcard
    // Hint buttons (both modes).
    mnemonicsEnabled: Boolean = true,
    onSetMnemonics: (Boolean) -> Unit = {},
    // Kanji reference: the opt-in "Kanji" button on words (default off).
    kanjiHintsEnabled: Boolean = false,
    onSetKanjiHints: (Boolean) -> Unit = {},
    // Light/dark appearance (System / Light / Dark), shown at the bottom of
    // the Study-scope sheet.
    appearance: AppearanceMode = AppearanceMode.LIGHT,
    onSetAppearance: (AppearanceMode) -> Unit = {},
    practiceSets: List<PracticeSet> = emptyList(),
    onSaveSet: (String, PracticeSetType, Script, List<String>) -> Unit = { _, _, _, _ -> },
    onUpdateSetMembers: (Long, List<String>) -> Unit = { _, _ -> },
    onRenameSet: (Long, String) -> Unit = { _, _ -> },
    onDeleteSet: (Long) -> Unit = {},
    onLaunchSetQuiz: (Long) -> Unit = {},
    onPracticeSet: (Long) -> Unit = {},
    // Builds a Word set from the learner's strong kana (85+ in writing AND
    // reading), invoked from the Sets tab "Add new set" menu.
    onGenerateStrongKanaSet: () -> Unit = {},
    // The persisted Words-tab category filter (hiragana only) + its mutators.
    // VM-backed so it survives entering a word and returning to the list.
    libraryCategories: List<String> = emptyList(),
    onToggleCategory: (String) -> Unit = {},
    onClearCategories: () -> Unit = {},
    // The sets manager's persisted sort order (a [SetSort] name; null/unknown
    // falls back to the default). Persisted so the list keeps the learner's
    // chosen order across edit flows, quiz round-trips, and restarts.
    setsSortKey: String? = null,
    onSetsSortSelected: (String) -> Unit = {},
    openSetsManagerSignal: Boolean = false,
    onConsumeOpenSetsManager: () -> Unit = {},
    // Pronunciation (device TTS) shown on each browse row/card.
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
    modifier: Modifier = Modifier,
) {
    // The score map the whole Library colors + sorts by. Writing mode uses the
    // handwriting history; Reading mode derives a summary from readingStats
    // whose "average" dial is the reading colorPercent (average of the MCQ% and
    // typed% the learner has done, or the single one) and which has no "best" —
    // reading is pass/fail. Everything downstream is unchanged.
    val scoreStats: Map<String, CharacterScoreSummary> =
        if (appMode == AppMode.READING) {
            readingStats.mapValues { (key, r) ->
                CharacterScoreSummary(
                    character = key,
                    attempts = r.mcqAttempts + r.typeAttempts,
                    avgForm = r.colorPercent,
                    bestForm = null,
                    avgFit = null,
                    bestFit = null,
                )
            }
        } else {
            writingScoreStats
        }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showScopeSheet by rememberSaveable { mutableStateOf(false) }

    // ---- Practice-set editing state (overlay-local, transient) ----
    // Selection mode presents the FULL list for one tab+script (ignoring the
    // Study Scope opt-ins) with multi-select; the sets manager lists saved sets.
    val selectedValues = remember { mutableStateListOf<String>() }
    var selectionMode by remember { mutableStateOf(false) }
    var editingSetId by remember { mutableStateOf<Long?>(null) }
    var selectionType by remember { mutableStateOf(PracticeSetType.CHARACTERS) }
    var selectionScript by remember { mutableStateOf(activeScript) }
    var showNameDialog by remember { mutableStateOf(false) }

    // ---- Category filter (Words browse tab) ----
    // Hiragana-only: katakana loanwords carry no category, so the control hides.
    // The SELECTION is VM-backed ([libraryCategories]) so it survives leaving the
    // Library (a practice tap) and coming back.
    val availableCategories = remember(words, activeScript) {
        words.filter { it.script == activeScript }
            .mapNotNull { it.category }
            .distinct()
            .sorted()
    }
    // Only categories still present in the active script apply (a stale one from
    // before a script switch is ignored, and never filters the list to empty).
    val activeCategories = remember(libraryCategories, availableCategories) {
        libraryCategories.filter { it in availableCategories }
    }

    fun startNewSet(type: PracticeSetType) {
        selectionType = type
        selectionScript = activeScript
        selectedValues.clear()
        editingSetId = null
        selectionMode = true
    }

    fun startEditSet(set: PracticeSet) {
        selectionType = set.type
        selectionScript = set.script
        selectedValues.clear()
        selectedValues.addAll(set.members)
        editingSetId = set.id
        selectionMode = true
    }

    fun exitSelection() {
        // Any exit from selection — save, cancel (✕), or Back — lands on the
        // Sets tab rather than the Library browse page, whether the set was new
        // or edited. onSelectTab persists it so the tab survives a later
        // decompose (e.g. a set-launched quiz composing above the Library).
        selectionMode = false
        editingSetId = null
        selectedValues.clear()
        showNameDialog = false
        selectedTab = 2
        onSelectTab(2)
    }

    // When a set-launched quiz/flashcard closes, the ViewModel raises a one-shot
    // so the library reopens on the Sets tab (the quiz decomposed this screen,
    // resetting selectedTab). Select the tab, persist it, then clear the shot.
    LaunchedEffect(openSetsManagerSignal) {
        if (openSetsManagerSignal) {
            selectedTab = 2
            onSelectTab(2)
            onConsumeOpenSetsManager()
        }
    }

    // While searching, system Back collapses the field instead of closing
    // the whole overlay (registered after PracticeScreen's close handler, so
    // it wins by LIFO only when enabled).
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }
    // Selection mode captures Back to step back out of itself (onto the Sets
    // tab) rather than closing the whole library (LIFO — enabled only in its
    // own state). The Sets tab itself does not capture Back — like the other
    // tabs, Back closes the Library.
    BackHandler(enabled = selectionMode) { exitSelection() }

    // Scope to the active script and the three opt-ins first (the selector +
    // toggles are the "what am I studying" controls), then apply the free-text
    // search on top. Both narrowings feed the tab match-count labels below.
    // Memoized: scoping walks the full merged deck applying per-glyph NFD/kind/
    // yōon classification, so recomputing it on every recomposition (each scroll
    // frame, each keystroke) is wasted main-thread work. Key on the actual inputs.
    val scriptCharacters = remember(
        characters, activeScript, includeDiacritics, includeYoon, includeExtended,
    ) {
        ScriptScope.scopedCharacters(
            characters, activeScript, includeDiacritics, includeYoon, includeExtended,
        )
    }
    val scriptWords = remember(
        words, activeScript, includeDiacritics, includeYoon, includeExtended,
    ) {
        ScriptScope.scopedWords(
            words, activeScript, includeDiacritics, includeYoon, includeExtended,
        )
    }
    // Category filter (Words browse): narrow to the selected topical groups
    // before search/sort. No selection = all words, in the original order.
    val categoryWords = remember(scriptWords, activeCategories) {
        if (activeCategories.isEmpty()) scriptWords
        else scriptWords.filter { it.category in activeCategories }
    }
    val trimmedQuery = query.trim()
    // Normalize the query once: drop all whitespace and lowercase, so romaji
    // typed with or without spaces ("neko" / "ne ko") and any casing compare
    // equal. Kana are unaffected by lowercasing.
    val normalizedQuery = remember(trimmedQuery) { trimmedQuery.searchNormalize() }
    val filteredCharacters = remember(scriptCharacters, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            scriptCharacters
        } else {
            scriptCharacters.filter { it.matches(normalizedQuery) }
        }
    }
    // Per-word reading (かう → "ka u") so words are searchable by romaji, not
    // just kana/meaning. Keyed on scriptWords ONLY: `pronunciation` is a fresh
    // method reference each recomposition, so keying on it would defeat the memo
    // and rebuild this O(words) map on every keystroke (same reasoning as the
    // Words grid's romajiByKana).
    val wordRomaji = remember(categoryWords) {
        categoryWords.associate { it.kana to pronunciation(it) }
    }
    val filteredWords = remember(categoryWords, normalizedQuery, wordRomaji) {
        if (normalizedQuery.isEmpty()) {
            categoryWords
        } else {
            categoryWords.filter { it.matches(normalizedQuery, wordRomaji[it.kana].orEmpty()) }
        }
    }
    // The Sets tab shows only the active script's sets (a set is built in one
    // writing system), then the shared top-bar search filters them by name.
    val scriptSets = remember(practiceSets, activeScript) {
        practiceSets.filter { it.script == activeScript }
    }
    val filteredSets = remember(scriptSets, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            scriptSets
        } else {
            scriptSets.filter { it.name.searchNormalize().contains(normalizedQuery) }
        }
    }

    // List-mode sort by scored history (both tabs share one order). Session-local
    // like the sets-manager sort; the chart/grid and tab counts stay unsorted.
    var listSort by remember { mutableStateOf(ItemSort.DEFAULT) }
    // In the DEFAULT (unsorted) order, an active query is ranked by relevance so
    // an exact/whole-word/prefix hit floats above an incidental substring hit
    // ("hat" beats "that"/"what"); ties keep the deck's frequency order (stable
    // sort). An explicit score/reading sort is the user's choice and wins over
    // relevance.
    val sortedCharacters = remember(filteredCharacters, listSort, scoreStats, readingStats, appMode, normalizedQuery) {
        when {
            listSort != ItemSort.DEFAULT && appMode == AppMode.READING ->
                filteredCharacters.sortedByReading(listSort) { readingStats[it.character] }
            listSort != ItemSort.DEFAULT ->
                filteredCharacters.sortedByScore(listSort) { scoreStats[it.character] }
            normalizedQuery.isEmpty() -> filteredCharacters
            else -> filteredCharacters.sortedBy { it.relevance(normalizedQuery) }
        }
    }
    val sortedWords = remember(filteredWords, listSort, scoreStats, readingStats, appMode, normalizedQuery, wordRomaji) {
        when {
            listSort != ItemSort.DEFAULT && appMode == AppMode.READING ->
                filteredWords.sortedByReading(listSort) { readingStats[it.kana] }
            listSort != ItemSort.DEFAULT ->
                filteredWords.sortedByScore(listSort) { scoreStats[it.kana] }
            normalizedQuery.isEmpty() -> filteredWords
            else -> filteredWords.sortedBy { it.relevance(normalizedQuery, wordRomaji[it.kana].orEmpty()) }
        }
    }
    // Jump back to the top whenever the order changes, so the new first items are
    // visible instead of being left scrolled down in the previous order's tail.
    val browseListState = rememberLazyListState()
    LaunchedEffect(listSort) { browseListState.scrollToItem(0) }

    // A dot on the Filter icon whenever scope differs from the beginner
    // default (any advanced-kana group opted in). Script choice is a mode,
    // not a hide-things filter, so it does not raise the dot.
    val scopeIsNonDefault = includeDiacritics || includeYoon || includeExtended

    // Grid/chart applies only when browsing; an active search falls back to the
    // list (matching results are inherently a list, and a filtered chart would
    // show holes). The view toggle hides while the search field is expanded.
    val gridMode = isGridView && trimmedQuery.isEmpty()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            selectionMode -> SelectionModeContent(
                type = selectionType,
                editing = editingSetId != null,
                characters = remember(characters, selectionScript) {
                    characters.filter { it.script == selectionScript }
                },
                words = remember(words, selectionScript) {
                    words.filter { it.script == selectionScript }
                },
                scoreStats = scoreStats,
                pronunciation = pronunciation,
                selectedValues = selectedValues,
                onToggle = { value ->
                    if (value in selectedValues) selectedValues.remove(value)
                    else selectedValues.add(value)
                },
                onSetSelected = { value, selected ->
                    if (selected) {
                        if (value !in selectedValues) selectedValues.add(value)
                    } else {
                        selectedValues.remove(value)
                    }
                },
                onSelectAll = { all ->
                    selectedValues.clear()
                    selectedValues.addAll(all)
                },
                onDeselectAll = { selectedValues.clear() },
                onSave = { showNameDialog = true },
                onCancel = { exitSelection() },
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
            LibraryTopBar(
                searching = searching,
                query = query,
                onQueryChange = { query = it },
                onToggleSearch = {
                    if (searching) {
                        searching = false
                        query = ""
                    } else {
                        searching = true
                    }
                },
                scopeIsNonDefault = scopeIsNonDefault,
                onOpenScope = { showScopeSheet = true },
                onStartQuiz = onStartQuiz,
                onClose = onClose,
                // The Sets tab has no chart/grid form and its own inline sort,
                // so the top-bar view toggle + list Sort menu hide there.
                showViewToggle = selectedTab != 2,
                isGridView = isGridView,
                onToggleView = { onSetGridView(!isGridView) },
                showSort = !gridMode && selectedTab != 2,
                sortMode = listSort,
                onSortSelected = { listSort = it },
                sortOptions = if (appMode == AppMode.READING) READING_SORT_OPTIONS else ITEM_SORT_OPTIONS,
            )

            // A full-width TabRow (equal thirds spanning the whole width) — a
            // ScrollableTabRow content-sizes its tabs and left-aligns them, so on
            // wider screens (e.g. the Pixel 7) the three tabs bunched at the left
            // with dead space on the right (user-caught). TabRow gives every tab
            // an equal 1/3 share; the shrink below keeps the longest label
            // ("Characters (113)") from clipping inside its third at 360dp. Unlike
            // rememberUniformTextScale (which fits the SUM into the total width, a
            // ScrollableTabRow model), rememberEqualShareTextScale fits the WIDEST
            // label into a single equal share — the right constraint here.
            // BoxWithConstraints is the PARENT of the row (safe, unlike a
            // shrink-measuring child inside a Tab).
            BoxWithConstraints {
                val countedLabels = listOf(
                    "Characters (${filteredCharacters.size})",
                    "Words (${filteredWords.size})",
                    "Sets (${filteredSets.size})",
                )
                val tabStyle = MaterialTheme.typography.titleSmall
                // Material Tab reserves 16dp horizontal text padding per side.
                val tabReserve = 32.dp
                // The live counts are the first thing to go: at 360dp with a
                // 1.3 font scale "Characters (46)" needs ~0.63 of its size,
                // below a readable floor, and ellipsized to "Characters (4…".
                // Unscaled raw factor (minScale 0) decides; the counts stay
                // whenever they fit at 0.7 or more.
                val countedScale = rememberEqualShareTextScale(
                    labels = countedLabels,
                    style = tabStyle,
                    availableWidth = maxWidth,
                    reservedPerItem = tabReserve,
                    minScale = 0f,
                )
                val dropCounts = countedScale < 0.7f
                val tabLabels = if (dropCounts) listOf("Characters", "Words", "Sets") else countedLabels
                val shortScale = rememberEqualShareTextScale(
                    labels = tabLabels,
                    style = tabStyle,
                    availableWidth = maxWidth,
                    reservedPerItem = tabReserve,
                    // "Characters" alone needs ~0.55 of its size in a 360dp
                    // third at a 2.0 font scale.
                    minScale = 0.5f,
                )
                val tabScale = if (dropCounts) shortScale else countedScale
                val tabLabelStyle = tabStyle.copy(
                    fontSize = tabStyle.fontSize * tabScale,
                    lineHeight = if (tabStyle.lineHeight.isSp) {
                        tabStyle.lineHeight * tabScale
                    } else {
                        tabStyle.lineHeight
                    },
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabLabels.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index; onSelectTab(index) },
                            text = {
                                Text(
                                    text = label,
                                    style = tabLabelStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                    // Shrunk to fit its third; ellipsis is only a
                                    // last-resort guard below the scale floor.
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            // Category filter for the Words tab (hiragana only — the control
            // hides itself when there are no categories). The dropdown ADDS
            // categories; each selected one shows as a chip you can remove with
            // its ✕. Removing the last chip restores the full list.
            if (selectedTab == 1 && availableCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryFilter(
                        categories = availableCategories,
                        selected = activeCategories.toSet(),
                        onToggle = onToggleCategory,
                        onClear = onClearCategories,
                        fixedLabel = "Category",
                    )
                    // Removable chips (scroll horizontally if many are picked).
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        activeCategories.forEach { category ->
                            InputChip(
                                selected = true,
                                onClick = { onToggleCategory(category) },
                                label = {
                                    Text(category.replaceFirstChar { it.uppercase() })
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove $category filter",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            when {
                // First branch: a persisted chart/grid view must never route the
                // Sets tab to CharacterChart/WordGrid.
                selectedTab == 2 -> SetsTabContent(
                    sets = filteredSets,
                    hasAnySets = scriptSets.isNotEmpty(),
                    query = trimmedQuery,
                    activeScript = activeScript,
                    scoreStats = scoreStats,
                    sortMode = SetSort.fromKey(setsSortKey),
                    onSortSelected = { onSetsSortSelected(it.name) },
                    onNewSet = { type -> startNewSet(type) },
                    onGenerateStrongKanaSet = onGenerateStrongKanaSet,
                    onEdit = { startEditSet(it) },
                    onRename = onRenameSet,
                    onDelete = onDeleteSet,
                    onQuiz = onLaunchSetQuiz,
                    onPractice = onPracticeSet,
                )
                gridMode && selectedTab == 0 -> CharacterChart(
                    characters = filteredCharacters,
                    scoreStats = scoreStats,
                    onPracticeCharacter = onPracticeCharacter,
                )
                gridMode -> WordGrid(
                    words = filteredWords,
                    scoreStats = scoreStats,
                    pronunciation = pronunciation,
                    onPracticeWord = onPracticeWord,
                    onSpeak = onSpeak,
                    ttsStatus = ttsStatus,
                )
                else -> LazyColumn(
                    state = browseListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selectedTab == 0) {
                        items(sortedCharacters, key = { it.id }) { character ->
                            LibraryRow(
                                onClick = { onPracticeCharacter(character) },
                                glyph = character.character,
                                title = character.romaji,
                                subtitle = if (appMode == AppMode.READING) {
                                    readingSubtitle(readingStats[character.character])
                                } else {
                                    characterSubtitle(
                                        character.strokeCount,
                                        scoreStats[character.character],
                                    )
                                },
                                score = scoreStats[character.character].avgScore(),
                                showPronunciation = true,
                                onSpeak = onSpeak,
                                ttsStatus = ttsStatus,
                            )
                        }
                        if (filteredCharacters.isEmpty()) {
                            item { NoMatchesMessage(trimmedQuery, "characters") }
                        }
                    } else {
                        items(sortedWords, key = { "${it.kana}\u0000${it.meaning}" }) { word ->
                            LibraryRow(
                                onClick = { onPracticeWord(word) },
                                glyph = word.kana,
                                title = word.meaning,
                                // The romaji reading is the primary subtitle in
                                // both modes; the third line carries the score —
                                // reading progress in Reading mode, the writing
                                // avg/best in Writing mode — so the number is
                                // visible, not just implied by the row's tint.
                                subtitle = pronunciation(word),
                                detail = if (appMode == AppMode.READING) {
                                    readingSubtitle(readingStats[word.kana])
                                } else {
                                    wordScoreSubtitle(scoreStats[word.kana])
                                },
                                score = scoreStats[word.kana].avgScore(),
                                showPronunciation = true,
                                onSpeak = onSpeak,
                                ttsStatus = ttsStatus,
                            )
                        }
                        if (filteredWords.isEmpty()) {
                            item { NoMatchesMessage(trimmedQuery, "words") }
                        }
                    }

                    item {
                        Text(
                            text = "Tap anything to practice it freely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            }
        }

        if (showScopeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showScopeSheet = false },
                // Open straight to full content height (fit-content) instead of
                // the half-expanded default that needed a drag/scroll to reveal
                // everything; the content still caps at ~90% height with scroll.
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                ScopeSheetContent(
                    activeScript = activeScript,
                    onSelectScript = onSelectScript,
                    beginnerMode = beginnerMode,
                    appMode = appMode,
                    onSetAppMode = onSetAppMode,
                    includeDiacritics = includeDiacritics,
                    onToggleDiacritics = onToggleDiacritics,
                    includeYoon = includeYoon,
                    onToggleYoon = onToggleYoon,
                    includeExtended = includeExtended,
                    onToggleExtended = onToggleExtended,
                    scoringMode = scoringMode,
                    onSetScoringMode = onSetScoringMode,
                    autoNarrateEnabled = autoNarrateEnabled,
                    onSetAutoNarrate = onSetAutoNarrate,
                    readingAutoSpeakEnabled = readingAutoSpeakEnabled,
                    onSetReadingAutoSpeak = onSetReadingAutoSpeak,
                    mnemonicsEnabled = mnemonicsEnabled,
                    onSetMnemonics = onSetMnemonics,
                    kanjiHintsEnabled = kanjiHintsEnabled,
                    onSetKanjiHints = onSetKanjiHints,
                    appearance = appearance,
                    onSetAppearance = onSetAppearance,
                    brushInkColor = brushInkColor,
                    onSelectBrushColor = onSelectBrushColor,
                    brushInkColorDark = brushInkColorDark,
                    onSelectBrushColorDark = onSelectBrushColorDark,
                    brushEffect = brushEffect,
                    onSelectBrushEffect = onSelectBrushEffect,
                )
            }
        }

        if (showNameDialog) {
            SetNameDialog(
                initialName = editingSetId
                    ?.let { id -> practiceSets.firstOrNull { it.id == id }?.name }
                    .orEmpty(),
                itemCount = selectedValues.size,
                onConfirm = { name ->
                    val members = selectedValues.toList()
                    val id = editingSetId
                    if (id == null) {
                        onSaveSet(name, selectionType, selectionScript, members)
                    } else {
                        onUpdateSetMembers(id, members)
                        onRenameSet(id, name)
                    }
                    // exitSelection() returns to the sets manager.
                    exitSelection()
                },
                onDismiss = { showNameDialog = false },
            )
        }
    }
}

/**
 * The single header row. Back + title normally; while [searching] the title
 * slot becomes a borderless autofocused search field. Actions: search
 * toggle, list/chart view toggle, the [SortMenu] (list mode only, shown when
 * [showSort]), Filter (dotted when [scopeIsNonDefault]) opening the scope
 * sheet, and the "Quiz me!" CTA. The brush-ink-color picker moved into the
 * scope sheet. Window insets are zeroed because the host activity is not
 * edge-to-edge (the system already reserves the status-bar strip).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    scopeIsNonDefault: Boolean,
    onOpenScope: () -> Unit,
    onStartQuiz: () -> Unit,
    onClose: () -> Unit,
    showViewToggle: Boolean,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    showSort: Boolean,
    sortMode: ItemSort,
    onSortSelected: (ItemSort) -> Unit,
    sortOptions: List<Pair<ItemSort, String>> = ITEM_SORT_OPTIONS,
) {
    // No windowInsets override: the routing Box in PracticeScreen pads by
    // safeDrawing and CONSUMES those insets, so the app bar's default insets
    // already resolve to zero here.
    TopAppBar(
        navigationIcon = {
            // The 48dp IconButton centres its 24dp glyph, so the arrow tip sits
            // ~20dp from the screen edge — further in than the trailing "Quiz
            // me!" button's right edge. Nudge it left (fixed dp = same on every
            // screen/density) so both edge insets read symmetric.
            IconButton(onClick = onClose, modifier = Modifier.offset(x = BackArrowNudge)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to start screen",
                )
            }
        },
        title = {
            if (searching) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                // BasicTextField (not Material TextField) so the input is a
                // single text-height line vertically centered like the title
                // text and the action icons — the Material field reserves label
                // space that pushed the text below the icon centers.
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Pull the "Search" text close to the arrow (same as "Library").
                        .offset(x = TitleNudge)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    // The TopAppBar title style is ~22sp (titleLarge) — too large
                    // for the narrow title slot left after the action cluster, so a
                    // longer reading like "ka me ra" overflowed and the single-line
                    // field scrolled, clipping the first glyph. A normal search-input
                    // size (bodyLarge, 16sp) fits those queries without scrolling.
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                // Shifted left by the same nudge as the back arrow so the gap
                // between the arrow and "Library" is as tight as before the
                // arrow moved. One line, never wrapped — shrinks to fit the
                // slot left by the action cluster ("Libra…" on a 360dp-wide
                // device otherwise), ellipsizing only below the shrink floor.
                AutoShrinkText(
                    text = "Library",
                    style = LocalTextStyle.current,
                    // A fixed 12dp floor: a 15sp floor itself doubled at a 2.0
                    // font scale and still didn't fit, so the title showed "…".
                    minFontSize = 12.dp.toNonScalingSp(),
                    modifier = Modifier.offset(x = TitleNudge),
                )
            }
        },
        // Compact the action cluster so the "Library" title keeps its room:
        // drop the 48dp min-touch enforcement and size each icon button to
        // 40dp (−8dp apiece), and give "Quiz me!" tighter padding. This frees
        // ~40dp of title width, enough for "Library" on a normal phone; the
        // title's ellipsis (above) covers the narrowest screens gracefully.
        // Purely spacing — every action stays comfortably tappable at 40dp.
        actions = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                // Search FIRST (leftmost action): expanding the field replaces
                // the "Library" title text, and its close (✕) sits right
                // beside the field. While searching, the view toggle, Sort
                // menu, and "Quiz me!" hide so the narrow title slot gets
                // real field width — on large-font devices the old always-on
                // cluster squeezed the field down to ~2 visible characters.
                // (The view toggle is a no-op during search anyway: a live
                // query forces list mode.) Only ✕ and the scope filter stay,
                // since scope changes affect the search results.
                IconButton(onClick = onToggleSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (searching) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (searching) "Close search" else "Search",
                    )
                }
                // List ⇄ chart(grid) view toggle. Shows the icon of the mode
                // you'd switch TO.
                if (showViewToggle && !searching) {
                    IconButton(onClick = onToggleView, modifier = Modifier.size(40.dp)) {
                        if (isGridView) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "List view",
                            )
                        } else {
                            val glyphColor = LocalContentColor.current
                            Canvas(
                                modifier = Modifier.size(24.dp),
                                contentDescription = "Chart view",
                            ) {
                                drawGridViewGlyph(glyphColor)
                            }
                        }
                    }
                }
                // Sort the list by scored history — list mode only (the chart
                // has a fixed gojūon order). Takes the slot the brush-colour
                // swatch used to hold; the brush colour now lives in the scope
                // sheet as a labeled row.
                if (showSort && !searching) {
                    SortMenu(
                        contentDescription = "Sort list",
                        label = null,
                        options = sortOptions,
                        selected = sortMode,
                        onSelect = onSortSelected,
                    )
                }
                BadgedBox(
                    badge = { if (scopeIsNonDefault) Badge() },
                ) {
                    IconButton(onClick = onOpenScope, modifier = Modifier.size(40.dp)) {
                        val tuneColor = LocalContentColor.current
                        Canvas(
                            modifier = Modifier.size(24.dp),
                            contentDescription = "Study scope",
                        ) {
                            drawTuneGlyph(tuneColor)
                        }
                    }
                }
                // Entry to the write-from-memory quiz — it lives in the library
                // because that's the ungated browse-anything space. Hidden
                // while searching (see the cluster comment above).
                if (!searching) {
                    FilledTonalButton(
                        onClick = onStartQuiz,
                        // More end than start padding so "Quiz me!" isn't crammed
                        // against the button's right edge; a tighter icon→text gap
                        // keeps the label close to the pencil.
                        contentPadding = PaddingValues(start = 10.dp, end = 16.dp),
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Create,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(6.dp))
                        // Grows with the font only up to 1.3x. Past that the
                        // button took the whole bar at a 2.0 font scale on a
                        // 360dp phone and squeezed the "Library" title to "…".
                        Text(
                            "Quiz me!",
                            fontSize = (14.dp * fontScaleFraction(1.3f)).toNonScalingSp(),
                        )
                    }
                }
            }
        },
    )
}

/**
 * The "Study scope" bottom-sheet body: the Hiragana/Katakana selector plus
 * the three advanced-kana opt-ins as switch rows (each with room for its
 * hint). Changes apply live via the callbacks — the ViewModel rebuilds the
 * session and re-scopes the lists — so there is no Apply button.
 */
@Composable
private fun ScopeSheetContent(
    activeScript: Script,
    onSelectScript: (Script) -> Unit,
    beginnerMode: Boolean,
    appMode: AppMode,
    onSetAppMode: (AppMode) -> Unit,
    includeDiacritics: Boolean,
    onToggleDiacritics: (Boolean) -> Unit,
    includeYoon: Boolean,
    onToggleYoon: (Boolean) -> Unit,
    includeExtended: Boolean,
    onToggleExtended: (Boolean) -> Unit,
    scoringMode: ScoringMode,
    onSetScoringMode: (ScoringMode) -> Unit,
    autoNarrateEnabled: Boolean,
    onSetAutoNarrate: (Boolean) -> Unit,
    readingAutoSpeakEnabled: Boolean,
    onSetReadingAutoSpeak: (Boolean) -> Unit,
    mnemonicsEnabled: Boolean,
    onSetMnemonics: (Boolean) -> Unit,
    kanjiHintsEnabled: Boolean,
    onSetKanjiHints: (Boolean) -> Unit,
    appearance: AppearanceMode,
    onSetAppearance: (AppearanceMode) -> Unit,
    brushInkColor: Int,
    onSelectBrushColor: (Int) -> Unit,
    brushInkColorDark: Int,
    onSelectBrushColorDark: (Int) -> Unit,
    brushEffect: BrushEffect,
    onSelectBrushEffect: (BrushEffect) -> Unit,
) {
    // Cap at ~90% of the screen and make the body scroll only if it overflows
    // (short devices). On a normal screen the sheet — opened skipPartiallyExpanded
    // — shows every option at once with no scrolling needed.
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Study scope",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        ChoiceRow(
            labels = Script.entries.map { it.displayLabel },
            selectedIndex = Script.entries.indexOf(activeScript),
            onSelect = { onSelectScript(Script.entries[it]) },
            // The ⓘ (24dp + 4dp gap) rides the SELECTED segment only. Its own
            // click is consumed, so tapping it opens the explainer without
            // re-selecting the segment; to read about the other script you
            // first select it (its icon then appears), then tap the icon.
            trailingWidth = if (beginnerMode) 28.dp else 0.dp,
            trailing = { index ->
                val script = Script.entries[index]
                if (script == activeScript && beginnerMode) {
                    ConceptInfoIcon(
                        concept = when (script) {
                            Script.HIRAGANA -> KanaConcepts.hiragana
                            Script.KATAKANA -> KanaConcepts.katakana
                        },
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
        )

        ScopeToggleRow(
            checked = includeDiacritics,
            onCheckedChange = onToggleDiacritics,
            label = "Voiced kana (゛゜)",
            hint = "が, ざ, ぱ …",
            info = KanaConcepts.voiced.takeIf { beginnerMode },
        )
        ScopeToggleRow(
            checked = includeYoon,
            onCheckedChange = onToggleYoon,
            label = "Combined kana (ゃゅょ)",
            hint = "きゃ, しゅ, きょ …",
            info = KanaConcepts.combined.takeIf { beginnerMode },
        )
        ScopeToggleRow(
            checked = includeExtended,
            onCheckedChange = onToggleExtended,
            label = "Extended kana (っ ァ)",
            hint = "っ, ファ, ティ …",
            info = KanaConcepts.extended.takeIf { beginnerMode },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Top-level track. Reading = recognize the printed forms; Writing =
        // handwriting practice. Switching here flips the whole app in place (the
        // Library stays open) — the same choice offered on the opening screen.
        Text(
            text = "I want to practice",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Reading recognizes the printed kana you meet in the wild; " +
                "Writing practices handwriting with stroke-order feedback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceRow(
            labels = AppMode.entries.map { it.displayLabel },
            selectedIndex = AppMode.entries.indexOf(appMode),
            onSelect = { onSetAppMode(AppMode.entries[it]) },
        )

        // Scoring is a Writing-only concern; hidden in Reading mode.
        if (appMode == AppMode.WRITING) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // How practice writing is judged (also used by the quiz). This is the
            // one global scoring setting; the quiz setup card shows the same picker.
            // "Off" means no score — and in FREE practice it drops the Submit step,
            // leaving just Clear · Next (see FreePracticeActionBar).
            Text(
                text = "Score my writing",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Form rewards correct shape and stroke order; Fit rewards " +
                    "filling the box. Off skips scoring (no Submit in practice).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceRow(
                labels = SCORING_CHOICES.map { it.second },
                selectedIndex = SCORING_CHOICES.indexOfFirst { it.first == scoringMode },
                onSelect = { onSetScoringMode(SCORING_CHOICES[it].first) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Auto-narration: speak each newly shown character/word aloud the
            // instant it appears (in free practice and the writing quiz), so the
            // learner hears the reading without tapping the speaker. Writing-only
            // — narrating in Reading would give the answer away.
            SettingsSwitchRow(
                title = "Read aloud automatically",
                hint = "Speak each new character or word the moment it " +
                    "shows, so you hear it without tapping the speaker.",
                checked = autoNarrateEnabled,
                onCheckedChange = onSetAutoNarrate,
            )
        }

        // The Reading twin of the narration toggle above: Reading must not
        // speak a glyph when it appears (it would give the answer away), so
        // it speaks AFTER the answer instead — on the flashcard reveal and
        // the reading-quiz reveal. Same label, mode-appropriate behavior.
        if (appMode == AppMode.READING) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            SettingsSwitchRow(
                title = "Read aloud automatically",
                hint = "Speak the character or word after you answer, " +
                    "so you hear the reading you just recalled.",
                checked = readingAutoSpeakEnabled,
                onCheckedChange = onSetReadingAutoSpeak,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Memory hints (mnemonics): a keyword + short story per kana, shown as
        // the "How to remember" chip and the quiz/flashcard Hint buttons.
        // Mode-independent (both tracks use them), so it sits outside the
        // appMode blocks above. Display-only — never affects scores.
        SettingsSwitchRow(
            title = "Memory hints",
            hint = "Show a keyword and short story that tie each kana's " +
                "shape to its sound. Covers the 46 basic hiragana for now.",
            checked = mnemonicsEnabled,
            onCheckedChange = onSetMnemonics,
        )

        // Kanji reference: an opt-in "Kanji" button on words that have a kanji
        // spelling. Off by default — this app teaches kana; the kanji is extra
        // reference for the curious. Display-only, like Memory hints.
        SettingsSwitchRow(
            title = "Kanji reference",
            hint = "Show a \"Kanji\" button on words written with kanji, colored by " +
                "how often the kanji form is used. Tap it to see the kanji, reading, " +
                "and meaning. For reference only — never quizzed or scored.",
            checked = kanjiHintsEnabled,
            onCheckedChange = onSetKanjiHints,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Brush ink colour — a purely-visual personalization (the colour your
        // strokes are drawn in). Moved here from the app bar so it reads as the
        // labeled setting it is; the swatch on the right opens the HSV picker.
        // Each appearance keeps its own ink; the row edits the ACTIVE one.
        val darkAppearanceActive = LocalKanaColors.current.isDark
        val activeInk = if (darkAppearanceActive) brushInkColorDark else brushInkColor
        val onPickActiveInk = if (darkAppearanceActive) onSelectBrushColorDark else onSelectBrushColor
        var showColorPicker by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showColorPicker = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Brush ink color",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (darkAppearanceActive) {
                        "The color your strokes are drawn in. Dark mode keeps its own ink."
                    } else {
                        "The color your strokes are drawn in."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val ink = Color(activeInk)
            val outline = MaterialTheme.colorScheme.outline
            Canvas(modifier = Modifier.size(28.dp)) {
                drawCircle(ink)
                drawCircle(outline, style = Stroke(1.dp.toPx()))
            }
        }
        if (showColorPicker) {
            BrushColorPicker(
                initial = Color(activeInk),
                onPick = {
                    onPickActiveInk(it.toArgb())
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Brush effect — the artistic stroke style (Brush / Calligraphy /
        // Fountain pen / Marker), independent of the ink colour above. The row
        // shows the current effect; tapping opens a preview picker.
        var showEffectPicker by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEffectPicker = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Brush effect",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "The style your strokes are drawn with.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = brushEffect.displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (showEffectPicker) {
            BrushEffectPicker(
                selected = brushEffect,
                inkColor = Color(activeInk),
                onPick = {
                    onSelectBrushEffect(it)
                    showEffectPicker = false
                },
                onDismiss = { showEffectPicker = false },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        // Appearance — System follows the device's dark-mode setting; Light/Dark
        // force it. App-controlled end to end (forceDarkAllowed stays false):
        // dark mode darkens the whole UI INCLUDING the writing paper, which then
        // uses its own ink (the row above).
        Text(text = "Appearance", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Dark turns the writing paper dark and gives it its own " +
                "ink color. System follows your device setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceRow(
            labels = AppearanceMode.entries.map { it.displayLabel },
            selectedIndex = AppearanceMode.entries.indexOf(appearance),
            onSelect = { onSetAppearance(AppearanceMode.entries[it]) },
        )
    }
}

/** The "Score my writing" choices, shown here and on the quiz setup card. */
private val SCORING_CHOICES = listOf(
    ScoringMode.FORM to "Form",
    ScoringMode.FIT to "Fit",
    ScoringMode.BOTH to "Both",
    ScoringMode.OFF to "Off",
)

/**
 * One opt-in: label + hint on the left, a [Switch] on the right. When [info] is
 * given, a small ⓘ next to the label opens a plain-language explainer for
 * first-time learners.
 */
@Composable
private fun ScopeToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    hint: String,
    info: ConceptInfo? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (info != null) {
                    ConceptInfoIcon(
                        concept = info,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A labelled settings switch row (title + hint on the left, Switch on the
 * right) — the shared shape of the scope sheet's non-scope toggles (the two
 * read-aloud rows and Memory hints), so their layout can't drift apart.
 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Human-readable label for the script selector, with a representative glyph
 * prefix. UI-only — the domain [Script.key] stays the machine identity.
 */
private val Script.displayLabel: String
    get() = when (this) {
        Script.HIRAGANA -> "あ Hiragana"
        Script.KATAKANA -> "ア Katakana"
    }

/**
 * A hand-drawn "tune"/sliders filter glyph — two rails, each with a knob —
 * because the bundled core icon set has no filter/tune icon and this project
 * avoids the `material-icons-extended` dependency (same approach as the
 * grid/pause/repeat glyphs drawn elsewhere).
 */
private fun DrawScope.drawTuneGlyph(color: Color) {
    val w = size.width
    val h = size.height
    val railWidth = size.minDimension * 0.09f
    val knob = size.minDimension * 0.14f
    val yTop = h * 0.34f
    val yBot = h * 0.66f
    drawLine(color, Offset(w * 0.12f, yTop), Offset(w * 0.88f, yTop), railWidth, StrokeCap.Round)
    drawLine(color, Offset(w * 0.12f, yBot), Offset(w * 0.88f, yBot), railWidth, StrokeCap.Round)
    drawCircle(color, knob, Offset(w * 0.36f, yTop))
    drawCircle(color, knob, Offset(w * 0.64f, yBot))
}

/**
 * A hand-drawn "sort" glyph — three left-aligned rounded lines of decreasing
 * length — for the sets-manager sort menu (same hand-drawn-glyph convention as
 * the tune/grid icons; avoids depending on `material-icons-extended`).
 */
private fun DrawScope.drawSortGlyph(color: Color) {
    val x0 = size.width * 0.14f
    val maxLen = size.width * 0.72f
    val stroke = size.minDimension * 0.12f
    val fractions = listOf(1f, 0.66f, 0.33f)
    listOf(0.26f, 0.5f, 0.74f).forEachIndexed { i, yf ->
        val y = size.height * yf
        drawLine(
            color = color,
            start = Offset(x0, y),
            end = Offset(x0 + maxLen * fractions[i], y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** A hand-drawn 2×2-squares glyph for the chart-view toggle (same convention). */
private fun DrawScope.drawGridViewGlyph(color: Color) {
    val gap = size.minDimension * 0.16f
    val cell = (size.minDimension - gap) / 2f
    val radius = CornerRadius(cell * 0.28f, cell * 0.28f)
    for (row in 0..1) {
        for (col in 0..1) {
            drawRoundRect(
                color = color,
                topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = radius,
            )
        }
    }
}

// ---- Chart (grid) view -------------------------------------------------

/** Gutter width reserved for the consonant row label, kept aligned with the header. */
private val ChartRowLabelWidth = 22.dp

/**
 * The Characters grid: a gojūon cheat-sheet chart built by [KanaChartBuilder]
 * from the (already-scoped) [characters]. Aligned [TableSection]s render as a
 * vowel column-header row + one [Row] of equal-weight cells per consonant row
 * (gaps left blank so columns stay aligned); [FlowSection]s (ん, ー, extended)
 * render as a wrapping [FlowRow] of content-sized cells so wide composite kana
 * (ファ/きゃ) never clip. Every cell taps into free practice like the list rows.
 */
@Composable
private fun CharacterChart(
    characters: List<HiraganaCharacter>,
    scoreStats: Map<String, CharacterScoreSummary>,
    onPracticeCharacter: (HiraganaCharacter) -> Unit,
) {
    val chart = remember(characters) { KanaChartBuilder.build(characters) }
    // One cell size for EVERY section (base 5-wide, yōon 3-wide, extended flow),
    // derived to fit 5 columns across: gutter + 5 cells + inter-cell gaps within
    // the list's 16dp side padding. Uniform size = aligned columns, yōon that
    // doesn't stretch, and extended cells that aren't shrunken.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellSize = ((maxWidth - 32.dp - ChartRowLabelWidth - 20.dp) / 5)
            .coerceIn(40.dp, 72.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            chart.sections.forEach { section ->
                section.title?.let { title ->
                    item { ChartSectionHeader(title) }
                }
                when (section) {
                    is TableSection -> {
                        item { ChartColumnLabels(section.columnLabels, cellSize) }
                        items(section.rows) { row ->
                            ChartTableRow(row, cellSize, scoreStats, onPracticeCharacter)
                        }
                    }
                    is FlowSection -> item {
                        ChartCellFlow(section.cells, cellSize, scoreStats, onPracticeCharacter)
                    }
                }
            }
            item {
                Text(
                    text = "Tap any cell to practice it freely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChartSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ChartColumnLabels(labels: List<String>, cellSize: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.width(ChartRowLabelWidth))
        labels.forEach { label ->
            Text(
                text = label,
                // Chrome text (non-scaling), matching the chart cells the
                // labels align with.
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.dp.toNonScalingSp(),
                    lineHeight = 16.dp.toNonScalingSp(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(cellSize),
            )
        }
    }
}

@Composable
private fun ChartTableRow(
    row: TableRow,
    cellSize: Dp,
    scoreStats: Map<String, CharacterScoreSummary>,
    onPracticeCharacter: (HiraganaCharacter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.rowLabel,
            // Chrome text (non-scaling), matching the chart cells.
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.dp.toNonScalingSp(),
                lineHeight = 16.dp.toNonScalingSp(),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(ChartRowLabelWidth),
        )
        row.cells.forEach { character ->
            if (character != null) {
                ChartCell(
                    character = character,
                    score = scoreStats[character.character].avgScore(),
                    onClick = { onPracticeCharacter(character) },
                    modifier = Modifier.size(cellSize),
                )
            } else {
                Spacer(Modifier.size(cellSize))
            }
        }
    }
}

/**
 * The ragged sections (ん, ー, extended): fixed-size cells (same as the table)
 * that wrap. Indented by the row-label gutter so the first cell lines up under
 * the chart's first column instead of floating.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCellFlow(
    cells: List<HiraganaCharacter>,
    cellSize: Dp,
    scoreStats: Map<String, CharacterScoreSummary>,
    onPracticeCharacter: (HiraganaCharacter) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ChartRowLabelWidth + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.forEach { character ->
            ChartCell(
                character = character,
                score = scoreStats[character.character].avgScore(),
                onClick = { onPracticeCharacter(character) },
                modifier = Modifier.size(cellSize),
            )
        }
    }
}

/**
 * Insets for the TOP-RIGHT corner score (the other corners hold the glyph,
 * wide for words / composite kana). The top is smaller than the end to
 * visually balance the digits' font padding — text sits below its layout-box
 * top and has side bearing — so it reads as equally spaced from both edges.
 */
private val ScoreInsetTop = 3.dp
private val ScoreInsetEnd = 5.dp

/**
 * Leftward nudge for the app-bar back arrow so its visible tip is the same
 * distance from the screen edge as the trailing "Quiz me!" button's right edge.
 * A fixed dp (density-independent), so the symmetry holds on every screen.
 */
private val BackArrowNudge = (-7).dp

/**
 * Leftward offset for the title ("Library" / "Search") — larger than
 * [BackArrowNudge] so the title sits closer to the arrow than the default
 * nav-icon slot allows, roughly halving the arrow→text gap. Fixed dp, so the
 * spacing is the same on every screen. The arrow keeps its own (smaller) nudge
 * so its edge inset stays symmetric with the trailing button.
 */
private val TitleNudge = (-15).dp

/**
 * The corner score's text style with [includeFontPadding] off, so the text box
 * hugs the digits (no reserved line ascent/descent). That makes an equal
 * top/right inset read as visually equal — otherwise the built-in font padding
 * adds an invisible band above the digits and the score looks top-heavy.
 */
private val ScoreTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** One chart cell: glyph over tiny romaji, tinted by score; taps to practice. */
@Composable
private fun ChartCell(
    character: HiraganaCharacter,
    score: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Score tint when scored; otherwise a neutral surface.
    val container = score?.let { scoreSpectrumColor(it, LocalKanaColors.current.isDark) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                // 2-codepoint kana (きゃ, ファ) get a smaller size so they fit the
                // fixed cell without clipping. Chrome text (non-scaling sp):
                // the dense chart cell is dp-sized, so its text behaves like
                // an icon — the list view is the fully font-scaling path.
                text = character.character,
                fontFamily = PrintFontFamily,
                fontSize = if (character.character.length > 1) {
                    16.dp.toNonScalingSp()
                } else {
                    22.dp.toNonScalingSp()
                },
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = character.romaji,
                fontSize = 11.dp.toNonScalingSp(),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (score != null) {
            Text(
                text = "$score",
                fontSize = 9.dp.toNonScalingSp(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = ScoreTextStyle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ScoreInsetTop, end = ScoreInsetEnd),
            )
        }
    }
}

/**
 * The Words grid: a [LazyVerticalGrid] of adaptive-width cards, so a long word
 * (ありがとう / パーティー) fits its cell and the grid only composes/measures the
 * cards actually on screen (vs. building all ~250 up front — the old eager
 * [FlowRow] stuttered while scrolling). Cards are tinted by score; every card
 * taps into free practice.
 */
@Composable
private fun WordGrid(
    words: List<VocabularyWord>,
    scoreStats: Map<String, CharacterScoreSummary>,
    pronunciation: (VocabularyWord) -> String,
    onPracticeWord: (VocabularyWord) -> Unit,
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
) {
    if (words.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            NoMatchesMessage("", "words")
        }
        return
    }
    // Precompute the reading for every word ONCE per list, off the scroll hot
    // path. Keyed on `words` ONLY: `pronunciation` is a fresh method reference
    // (uiState::pronunciationOf) each recomposition, so keying on it would defeat
    // the memo and re-run this whole O(words) build on every recomposition.
    val romajiByKana = remember(words) {
        words.associate { it.kana to pronunciation(it) }
    }
    // Adaptive columns reproduce the old FlowRow wrapping; 84.dp ≈ the cards'
    // 76.dp min width plus breathing room. The min grows with the font scale
    // (capped) so large-font settings get fewer, wider columns instead of
    // scaled-up text jamming into scale-1 column widths.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 84.dp * fontScaleFraction(1.6f)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(words, key = { "${it.kana}\u0000${it.meaning}" }, contentType = { "wordCard" }) { word ->
            WordCard(
                kana = word.kana,
                meaning = word.meaning,
                romaji = romajiByKana[word.kana].orEmpty(),
                score = scoreStats[word.kana].avgScore(),
                onClick = { onPracticeWord(word) },
                onSpeak = onSpeak,
                ttsStatus = ttsStatus,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Tap any word to practice it freely.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun WordCard(
    kana: String,
    meaning: String,
    romaji: String,
    score: Int?,
    onClick: () -> Unit,
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
) {
    // Score tint when scored; otherwise a neutral surface.
    val container = score?.let { scoreSpectrumColor(it, LocalKanaColors.current.isDark) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minWidth = 76.dp)
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Kana and romaji shrink to the card instead of clipping: the
            // column minimum only grows to 1.6x, so at a 2.0 font scale a word
            // like ありがとう was wider than its card. The meaning keeps its
            // ellipsis (it's the longest line and the least needed at a glance).
            AutoShrinkText(
                text = kana,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PrintFontFamily,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Reading between the kana and the meaning (e.g. かう / ka u / to buy).
            AutoShrinkText(
                text = romaji,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = meaning,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PronunciationButton(
                glyph = kana,
                status = ttsStatus,
                onSpeak = onSpeak,
            )
        }
        if (score != null) {
            Text(
                text = "$score",
                // Chrome text (non-scaling): a corner badge, like an icon.
                fontSize = 10.dp.toNonScalingSp(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                style = ScoreTextStyle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ScoreInsetTop, end = ScoreInsetEnd),
            )
        }
    }
}

/**
 * Normalizes a search token: drops ALL whitespace and lowercases. Lets romaji
 * typed with or without spaces ("ne ko" / "neko") and any casing compare equal,
 * and collapses spacing in multi-word English glosses ("ice cream"). Kana are
 * untouched by lowercasing, so a Japanese-keyboard query still matches kana.
 * Both the query and every searched field are passed through this before
 * comparing.
 */
private fun String.searchNormalize(): String =
    filterNot { it.isWhitespace() }.lowercase()

/** [query] is already [searchNormalize]d by the caller. */
private fun HiraganaCharacter.matches(query: String): Boolean =
    character.contains(query) ||
        romaji.searchNormalize().contains(query) ||
        id.searchNormalize().contains(query)

/**
 * [query] is already [searchNormalize]d by the caller; [romaji] is the word's
 * space-separated reading (from the ViewModel), matched space-insensitively so
 * "ねこ" is findable by "neko" or "ne ko".
 */
private fun VocabularyWord.matches(query: String, romaji: String): Boolean =
    kana.contains(query) ||
        meaning.searchNormalize().contains(query) ||
        romaji.searchNormalize().contains(query)

/**
 * How well [field] matches [query] (already [searchNormalize]d): the lower the
 * tier the stronger the match. This is what floats an exact hit above a mere
 * substring hit so searching "hat" ranks the word "hat" above "that"/"what".
 * [field] is the raw human string (it may carry spaces, e.g. a multi-word
 * meaning "that over there" or space-separated romaji "bo u shi"); the
 * whitespace-stripped comparison is what makes "boushi" and "bo u shi" both an
 * exact match on that word's romaji.
 */
private fun matchTier(query: String, field: String): Int {
    if (field.isEmpty()) return NO_MATCH
    val norm = field.searchNormalize()
    return when {
        norm == query -> 0 // exact
        field.lowercase().split(' ', '\t', '\n', '\r').any { it == query } -> 1 // whole word
        norm.startsWith(query) -> 2 // prefix
        norm.contains(query) -> 3 // substring
        else -> NO_MATCH
    }
}

private const val NO_MATCH = Int.MAX_VALUE

/** Best (lowest) match tier across a character's searchable fields. */
private fun HiraganaCharacter.relevance(query: String): Int =
    minOf(matchTier(query, character), matchTier(query, romaji), matchTier(query, id))

/** Best (lowest) match tier across a word's searchable fields. */
private fun VocabularyWord.relevance(query: String, romaji: String): Int =
    minOf(matchTier(query, kana), matchTier(query, meaning), matchTier(query, romaji))

/**
 * A slim autofocused search field used by the practice-set screens (the set
 * builder's item list and the sets manager): a leading magnifier plus a trailing
 * clear that empties the query. Toggled open by a magnifier icon in each header.
 */
@Composable
private fun PracticeSetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun NoMatchesMessage(query: String, what: String) {
    Text(
        text = "No $what match “$query”.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/** Character-row subtitle: stroke count, plus a compact quality summary once scored. */
private fun characterSubtitle(strokeCount: Int, summary: CharacterScoreSummary?): String {
    val base = "$strokeCount strokes"
    val avg = summary.avgScore() ?: return base
    val best = summary.bestScore()
    return if (best != null) "$base · avg $avg · best $best" else "$base · avg $avg"
}

/**
 * One tappable row: the glyph (or word kana) large on the left, name and
 * detail in the middle; the whole row is tinted by score when scored.
 */
@Composable
private fun LibraryRow(
    onClick: () -> Unit,
    glyph: String,
    title: String,
    subtitle: String,
    score: Int?,
    showPronunciation: Boolean = false,
    detail: String? = null,
    onSpeak: (String) -> Unit = {},
    ttsStatus: VoiceStatus = VoiceStatus.UNAVAILABLE,
) {
    // Tint the whole row by score when available; otherwise the default card color.
    val cardColors = score?.let {
        CardDefaults.cardColors(containerColor = scoreSpectrumColor(it, LocalKanaColors.current.isDark))
    } ?: CardDefaults.cardColors()
    Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = PrintFontFamily,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // A muted third line for secondary info (e.g. reading-progress
                // stats under a word's romaji in Reading mode), shown only when
                // provided so Writing-mode rows keep their two-line shape.
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showPronunciation) {
                PronunciationButton(
                    glyph = glyph,
                    status = ttsStatus,
                    onSpeak = onSpeak,
                )
            }
        }
    }
}

// ---- Practice-set selection mode & manager -----------------------------

/** One selectable entry (character glyph or word kana) shown during selection. */
private data class SelectionRow(
    val value: String,
    val glyph: String,
    val label: String,
    // Pre-normalized glyph + reading + meaning/id, for whitespace/case-insensitive search.
    val search: String,
)

/**
 * Selection mode: the FULL list for one tab+script (ignoring the Study Scope
 * opt-ins), with a selection header bar (live count, Select all / Deselect all
 * / Save) replacing the normal chrome. A plain tap toggles a row; a drag across
 * rows toggles a range immediately (no hold delay) and auto-scrolls near the
 * edges so long lists stay reachable in one gesture.
 */
@Composable
private fun SelectionModeContent(
    type: PracticeSetType,
    editing: Boolean,
    characters: List<HiraganaCharacter>,
    words: List<VocabularyWord>,
    scoreStats: Map<String, CharacterScoreSummary>,
    pronunciation: (VocabularyWord) -> String,
    selectedValues: List<String>,
    onToggle: (String) -> Unit,
    onSetSelected: (String, Boolean) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onDeselectAll: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // Category filter (word sets only): pick topical groups so a "Family"/"Food"
    // set is a couple of taps. Derived from the passed (script-filtered) words,
    // so it's empty (and the control hidden) for katakana / character sets.
    val availableCategories = remember(words) {
        words.mapNotNull { it.category }.distinct().sorted()
    }
    val selectedCategories = remember { mutableStateListOf<String>() }
    val visibleWords = remember(words, selectedCategories.toList()) {
        if (selectedCategories.isEmpty()) words
        else words.filter { it.category in selectedCategories }
    }
    val rows = remember(type, characters, visibleWords) {
        if (type == PracticeSetType.CHARACTERS) {
            characters.map {
                SelectionRow(
                    value = it.character,
                    glyph = it.character,
                    label = it.romaji,
                    search = "${it.character} ${it.romaji} ${it.id}".searchNormalize(),
                )
            }
        } else {
            visibleWords.map {
                SelectionRow(
                    value = it.kana,
                    glyph = it.kana,
                    label = it.meaning,
                    search = "${it.kana} ${pronunciation(it)} ${it.meaning}".searchNormalize(),
                )
            }
        }
    }
    // O(1) membership for the rows (the backing list's .contains is O(n)); this
    // read also subscribes the list to selection changes so rows recompose.
    val selectedSet = selectedValues.toSet()
    val noun = if (type == PracticeSetType.CHARACTERS) "characters" else "words"

    var gridView by remember { mutableStateOf(false) }

    // Search filters the list/grid by glyph, reading, or meaning (session-local).
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val normalizedQuery = remember(query) { query.trim().searchNormalize() }
    val filteredRows = remember(rows, normalizedQuery) {
        if (normalizedQuery.isEmpty()) rows
        else rows.filter { it.search.contains(normalizedQuery) }
    }
    // The chart grid (Characters only) needs the actual HiraganaCharacters, not
    // the flattened SelectionRows, to feed KanaChartBuilder — filtered by the
    // same query so search behaves identically in both grid layouts.
    val filteredCharacters = remember(characters, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            characters
        } else {
            characters.filter {
                "${it.character} ${it.romaji} ${it.id}".searchNormalize().contains(normalizedQuery)
            }
        }
    }

    // List-mode sort by scored history (session-local); grid mode keeps deck order.
    var selectionSort by remember { mutableStateOf(ItemSort.DEFAULT) }
    val sortedRows = remember(filteredRows, selectionSort, scoreStats) {
        filteredRows.sortedByScore(selectionSort) { scoreStats[it.value] }
    }

    // "All" opens a smart-select popup: cap the selection to items scoring under
    // a threshold (default 85, hard to reach), optionally excluding items never
    // studied. Transient — reset to defaults each time the popup opens.
    var allMenuOpen by remember { mutableStateOf(false) }
    var capEnabled by remember { mutableStateOf(true) }
    var threshold by remember { mutableStateOf(85) }
    var excludeUnstudied by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          // Compact the icon buttons (40dp, no 48dp min-touch reservation) so the
          // title + search/sort/grid/All/None/Save all fit one row without the
          // title column being crushed into an ugly multi-line wrap.
          CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Cancel selection")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // One line (ellipsized) so the crowded action row never forces
                    // an ugly wrap; the "N selected" line below carries the count.
                    text = if (editing) "Edit $noun" else "Select $noun",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${selectedValues.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Search the selectable list by glyph / reading / meaning. Toggles a
            // full-width field below the header; closing it clears the query.
            IconButton(
                onClick = {
                    if (searching) {
                        searching = false
                        query = ""
                    } else {
                        searching = true
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (searching) "Close search" else "Search $noun",
                )
            }
            // Sort by scored history — list mode only (grid keeps deck order),
            // so weak items can be found while selecting. Icon-only to save room.
            if (!gridView) {
                SortMenu(
                    contentDescription = "Sort $noun",
                    label = null,
                    options = ITEM_SORT_OPTIONS,
                    selected = selectionSort,
                    onSelect = { selectionSort = it },
                )
            }
            // List ⇄ grid toggle for the selectable items (shows the mode you'd
            // switch TO, same convention as the Library's view toggle). Tap and
            // drag-select behave identically in both layouts.
            IconButton(onClick = { gridView = !gridView }, modifier = Modifier.size(40.dp)) {
                if (gridView) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "List view",
                    )
                } else {
                    val glyphColor = LocalContentColor.current
                    Canvas(modifier = Modifier.size(22.dp), contentDescription = "Grid view") {
                        drawGridViewGlyph(glyphColor)
                    }
                }
            }
            Box {
                TextButton(
                    onClick = {
                        // Present the recommended weak-item selection on open.
                        capEnabled = true
                        threshold = 85
                        excludeUnstudied = true
                        allMenuOpen = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("All") }
                DropdownMenu(
                    expanded = allMenuOpen,
                    onDismissRequest = { allMenuOpen = false },
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 288.dp, max = 360.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { capEnabled = !capEnabled },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = capEnabled,
                                onCheckedChange = { capEnabled = it },
                            )
                            Text(
                                text = "Only under a score of",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        ScoreThresholdStepper(
                            value = threshold,
                            onValueChange = { threshold = it },
                            enabled = capEnabled,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = capEnabled) {
                                    excludeUnstudied = !excludeUnstudied
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = excludeUnstudied,
                                onCheckedChange = { excludeUnstudied = it },
                                enabled = capEnabled,
                            )
                            Text(
                                text = "Exclude $noun I have not studied yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (capEnabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Button(
                            onClick = {
                                val subset = if (!capEnabled) {
                                    rows.map { it.value }
                                } else {
                                    rows.mapNotNull { row ->
                                        val score = scoreStats[row.value].avgScore()
                                        val keep = when {
                                            score != null -> score < threshold
                                            excludeUnstudied -> false
                                            else -> true
                                        }
                                        row.value.takeIf { keep }
                                    }
                                }
                                onSelectAll(subset)
                                allMenuOpen = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) { Text("Select") }
                    }
                }
            }
            TextButton(
                onClick = onDeselectAll,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("None") }
            Button(
                onClick = onSave,
                enabled = selectedValues.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text("Save") }
          }
        }
        if (searching) {
            PracticeSetSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search $noun",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (type == PracticeSetType.WORDS && availableCategories.isNotEmpty()) {
            CategoryFilter(
                categories = availableCategories,
                selected = selectedCategories.toSet(),
                onToggle = { c ->
                    if (c in selectedCategories) selectedCategories.remove(c)
                    else selectedCategories.add(c)
                },
                onClear = { selectedCategories.clear() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider()
        when {
            filteredRows.isEmpty() -> NoMatchesMessage(query.trim(), noun)
            // Characters get the gojūon chart layout (same as the Library's
            // browse grid) so a letter is as easy to find here as it is there;
            // words keep the adaptive card grid — there's no table for them.
            gridView && type == PracticeSetType.CHARACTERS -> SelectableCharacterChart(
                filteredCharacters, selectedSet, onToggle, onSetSelected,
            )
            gridView -> SelectableGrid(filteredRows, selectedSet, onToggle, onSetSelected)
            else -> SelectableList(sortedRows, selectedSet, scoreStats, onToggle, onSetSelected)
        }
    }
}

/**
 * Score-cap editor for the "All" popup: a numeric box flanked by −/+ steppers,
 * followed by [quickPicks] chips (common thresholds) to the right of "+". Steppers
 * step the value; typing works the other way; a chip jumps straight to it. Clamps
 * to 1..99 (a whole number under a perfect 100) and snaps the box back. The stepper
 * + chips sit in a [FlowRow] so the chips wrap to a second line on narrow popups.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScoreThresholdStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    quickPicks: List<Int> = listOf(70, 80, 90, 99),
) {
    fun clamp(n: Int) = n.coerceIn(1, 99)
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onValueChange(clamp(value - 1)) },
                enabled = enabled && value > 1,
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
                            // effect, even when the clamp lands on the current one
                            // (typing "0" while value is already 1) — otherwise
                            // the LaunchedEffect never fires and the box desyncs.
                            val clamped = clamp(n)
                            text = clamped.toString()
                            onValueChange(clamped)
                        }
                    }
                },
                enabled = enabled,
                modifier = Modifier.width(72.dp),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            FilledTonalIconButton(
                onClick = { onValueChange(clamp(value + 1)) },
                enabled = enabled && value < 99,
            ) {
                Text(text = "+", style = MaterialTheme.typography.titleMedium)
            }
        }
        quickPicks.forEach { pick ->
            FilterChip(
                modifier = Modifier.align(Alignment.CenterVertically),
                selected = enabled && value == pick,
                onClick = { onValueChange(clamp(pick)) },
                enabled = enabled,
                label = { Text(pick.toString()) },
            )
        }
    }
}

/**
 * The list layout for selection mode: one full-width [SelectableRow] per item.
 * Tap toggles; a vertical drag toggles a range immediately (no hold delay) and
 * auto-scrolls near the top/bottom edge so long lists stay reachable.
 */
@Composable
private fun SelectableList(
    rows: List<SelectionRow>,
    selectedSet: Set<String>,
    scoreStats: Map<String, CharacterScoreSummary>,
    onToggle: (String) -> Unit,
    onSetSelected: (String, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    // pointerInput is keyed only on `rows` (re-keying on the selection would cancel an
    // in-progress drag), so read the selection through rememberUpdatedState to avoid a
    // stale closure inverting drag-to-deselect after the first selection.
    val liveSelected by rememberUpdatedState(selectedSet)
    var dragTarget by remember { mutableStateOf(true) }
    var dragActive by remember { mutableStateOf(false) }
    var lastDragIndex by remember { mutableStateOf(-1) }
    var pointerY by remember { mutableStateOf(0f) }
    var viewportHeight by remember { mutableStateOf(0) }

    // A new (re-sorted) rows identity means the order changed — return to the top
    // so the new first items are visible rather than the previous order's tail.
    LaunchedEffect(rows) { listState.scrollToItem(0) }

    fun indexAt(y: Float): Int? =
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { y >= it.offset && y <= it.offset + it.size }?.index

    fun applyRange(toIndex: Int) {
        if (toIndex < 0 || toIndex >= rows.size) return
        val from = if (lastDragIndex < 0) toIndex else lastDragIndex
        val range = if (toIndex >= from) from..toIndex else toIndex..from
        for (i in range) onSetSelected(rows[i].value, dragTarget)
        lastDragIndex = toIndex
    }

    LaunchedEffect(dragActive) {
        if (!dragActive) return@LaunchedEffect
        while (dragActive) {
            val h = viewportHeight.toFloat()
            if (h > 0f) {
                val delta = when {
                    pointerY < h * 0.12f -> -h * 0.03f
                    pointerY > h * 0.88f -> h * 0.03f
                    else -> 0f
                }
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    indexAt(pointerY)?.let { applyRange(it) }
                }
            }
            withFrameNanos { }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeight = it.height }
            .pointerInput(rows) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val idx = indexAt(offset.y)
                        if (idx != null) {
                            dragTarget = rows[idx].value !in liveSelected
                            lastDragIndex = -1
                            pointerY = offset.y
                            dragActive = true
                            applyRange(idx)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        pointerY = change.position.y
                        indexAt(change.position.y)?.let { applyRange(it) }
                    },
                    onDragEnd = { dragActive = false; lastDragIndex = -1 },
                    onDragCancel = { dragActive = false; lastDragIndex = -1 },
                )
            },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { "${it.value}\u0000${it.label}" }) { row ->
            SelectableRow(
                glyph = row.glyph,
                label = row.label,
                summary = scoreStats[row.value],
                selected = row.value in selectedSet,
                onClick = { onToggle(row.value) },
            )
        }
    }
}

/**
 * The grid layout for selection mode: compact square cells that wrap. Tap
 * toggles; a drag sweeps a range (in reading order) immediately, with the same
 * edge auto-scroll as the list.
 */
@Composable
private fun SelectableGrid(
    rows: List<SelectionRow>,
    selectedSet: Set<String>,
    onToggle: (String) -> Unit,
    onSetSelected: (String, Boolean) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // pointerInput is keyed only on `rows` (re-keying on the selection would cancel an
    // in-progress drag), so read the selection through rememberUpdatedState to avoid a
    // stale closure inverting drag-to-deselect after the first selection.
    val liveSelected by rememberUpdatedState(selectedSet)
    var dragTarget by remember { mutableStateOf(true) }
    var dragActive by remember { mutableStateOf(false) }
    var lastDragIndex by remember { mutableStateOf(-1) }
    var pointerX by remember { mutableStateOf(0f) }
    var pointerY by remember { mutableStateOf(0f) }
    var viewportHeight by remember { mutableStateOf(0) }

    fun indexAt(x: Float, y: Float): Int? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            x >= info.offset.x && x <= info.offset.x + info.size.width &&
                y >= info.offset.y && y <= info.offset.y + info.size.height
        }?.index

    fun applyRange(toIndex: Int) {
        if (toIndex < 0 || toIndex >= rows.size) return
        val from = if (lastDragIndex < 0) toIndex else lastDragIndex
        val range = if (toIndex >= from) from..toIndex else toIndex..from
        for (i in range) onSetSelected(rows[i].value, dragTarget)
        lastDragIndex = toIndex
    }

    LaunchedEffect(dragActive) {
        if (!dragActive) return@LaunchedEffect
        while (dragActive) {
            val h = viewportHeight.toFloat()
            if (h > 0f) {
                val delta = when {
                    pointerY < h * 0.12f -> -h * 0.03f
                    pointerY > h * 0.88f -> h * 0.03f
                    else -> 0f
                }
                if (delta != 0f) {
                    gridState.scrollBy(delta)
                    indexAt(pointerX, pointerY)?.let { applyRange(it) }
                }
            }
            withFrameNanos { }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 68.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeight = it.height }
            .pointerInput(rows) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val idx = indexAt(offset.x, offset.y)
                        if (idx != null) {
                            dragTarget = rows[idx].value !in liveSelected
                            lastDragIndex = -1
                            pointerX = offset.x
                            pointerY = offset.y
                            dragActive = true
                            applyRange(idx)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        pointerX = change.position.x
                        pointerY = change.position.y
                        indexAt(change.position.x, change.position.y)?.let { applyRange(it) }
                    },
                    onDragEnd = { dragActive = false; lastDragIndex = -1 },
                    onDragCancel = { dragActive = false; lastDragIndex = -1 },
                )
            },
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(rows, key = { "${it.value}\u0000${it.label}" }) { row ->
            SelectableGridCell(
                glyph = row.glyph,
                label = row.label,
                selected = row.value in selectedSet,
                onClick = { onToggle(row.value) },
            )
        }
    }
}

/** One selectable grid cell: glyph over tiny label, tinted + checked when selected. */
@Composable
private fun SelectableGridCell(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = glyph,
                fontFamily = PrintFontFamily,
                // Chrome text (non-scaling): the aspect-locked cell can't
                // grow with font scale; the selection LIST is the scaling path.
                fontSize = if (glyph.length > 1) {
                    18.dp.toNonScalingSp()
                } else {
                    24.dp.toNonScalingSp()
                },
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                fontSize = 10.dp.toNonScalingSp(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(16.dp),
            )
        }
    }
}

/**
 * The Characters grid for selection mode: the SAME gojūon cheat-sheet layout as
 * the Library's browse chart ([CharacterChart] / [KanaChartBuilder]) — aligned
 * vowel columns and consonant rows, with Voiced/Combined(yōon)/Extended
 * sections — instead of a plain wrapping grid, so a character is exactly as
 * easy to find while building a set as it is while browsing. Word sets keep
 * [SelectableGrid] (adaptive cards); there's no table layout for words to
 * align to.
 *
 * Tap toggles a cell. A drag sweeps a range: since this isn't a single
 * [LazyVerticalGrid] (the chart mixes fixed-column table rows and wrapping
 * flow rows), cells are hit-tested by their own reported window bounds
 * (`cellBounds`) rather than a grid index.
 */
@Composable
private fun SelectableCharacterChart(
    characters: List<HiraganaCharacter>,
    selectedSet: Set<String>,
    onToggle: (String) -> Unit,
    onSetSelected: (String, Boolean) -> Unit,
) {
    val chart = remember(characters) { KanaChartBuilder.build(characters) }
    // pointerInput is keyed only on `chart`, so read the live selection through
    // rememberUpdatedState — the same stale-closure guard as SelectableGrid.
    val liveSelected by rememberUpdatedState(selectedSet)
    val cellBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragTarget by remember { mutableStateOf(true) }
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun valueAt(windowPosition: Offset): String? =
        cellBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(windowPosition) }?.key

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerCoords = it }
            .pointerInput(chart) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val coords = containerCoords ?: return@detectDragGestures
                        val value = valueAt(coords.localToWindow(offset)) ?: return@detectDragGestures
                        dragTarget = value !in liveSelected
                        onSetSelected(value, dragTarget)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val coords = containerCoords ?: return@detectDragGestures
                        valueAt(coords.localToWindow(change.position))?.let { onSetSelected(it, dragTarget) }
                    },
                    onDragEnd = {},
                    onDragCancel = {},
                )
            },
    ) {
        // Same uniform cell-size derivation as the browse CharacterChart, so
        // selection mode and browsing read as the same chart.
        val cellSize = ((maxWidth - 32.dp - ChartRowLabelWidth - 20.dp) / 5)
            .coerceIn(40.dp, 72.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            chart.sections.forEach { section ->
                section.title?.let { title -> item { ChartSectionHeader(title) } }
                when (section) {
                    is TableSection -> {
                        item { ChartColumnLabels(section.columnLabels, cellSize) }
                        items(section.rows) { row ->
                            SelectableChartTableRow(
                                row = row,
                                cellSize = cellSize,
                                selectedSet = selectedSet,
                                onToggle = onToggle,
                                cellBounds = cellBounds,
                            )
                        }
                    }
                    is FlowSection -> item {
                        SelectableChartCellFlow(
                            cells = section.cells,
                            cellSize = cellSize,
                            selectedSet = selectedSet,
                            onToggle = onToggle,
                            cellBounds = cellBounds,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableChartTableRow(
    row: TableRow,
    cellSize: Dp,
    selectedSet: Set<String>,
    onToggle: (String) -> Unit,
    cellBounds: MutableMap<String, Rect>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.rowLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.dp.toNonScalingSp(),
                lineHeight = 16.dp.toNonScalingSp(),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(ChartRowLabelWidth),
        )
        row.cells.forEach { character ->
            if (character != null) {
                SelectableChartCell(
                    character = character,
                    selected = character.character in selectedSet,
                    onClick = { onToggle(character.character) },
                    modifier = Modifier.size(cellSize),
                    cellBounds = cellBounds,
                )
            } else {
                Spacer(Modifier.size(cellSize))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectableChartCellFlow(
    cells: List<HiraganaCharacter>,
    cellSize: Dp,
    selectedSet: Set<String>,
    onToggle: (String) -> Unit,
    cellBounds: MutableMap<String, Rect>,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ChartRowLabelWidth + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.forEach { character ->
            SelectableChartCell(
                character = character,
                selected = character.character in selectedSet,
                onClick = { onToggle(character.character) },
                modifier = Modifier.size(cellSize),
                cellBounds = cellBounds,
            )
        }
    }
}

/** One chart cell in selection mode: [ChartCell]'s layout, tinted + checked when selected. */
@Composable
private fun SelectableChartCell(
    character: HiraganaCharacter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cellBounds: MutableMap<String, Rect>,
) {
    // Drop this cell's hit-test rect when it leaves composition (scrolled out,
    // or filtered away by the search). A stale rect would overlap whichever
    // cell moved into its old spot and let a drag select an unseen character.
    DisposableEffect(character.character, cellBounds) {
        val value = character.character
        onDispose { cellBounds.remove(value) }
    }
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { cellBounds[character.character] = it.boundsInWindow() }
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = character.character,
                fontFamily = PrintFontFamily,
                fontSize = if (character.character.length > 1) {
                    16.dp.toNonScalingSp()
                } else {
                    22.dp.toNonScalingSp()
                },
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = character.romaji,
                fontSize = 11.dp.toNonScalingSp(),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(16.dp),
            )
        }
    }
}

/**
 * One row in selection mode — mirrors the Library browse row so past performance
 * is visible while building a set: a checkbox, the glyph, its reading/meaning, and
 * an "avg · best" line, with the card tinted by score (neutral when unpracticed).
 * Selection is shown by the checkbox + a primary border (kept legible over the tint).
 */
@Composable
private fun SelectableRow(
    glyph: String,
    label: String,
    summary: CharacterScoreSummary?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val avg = summary.avgScore()
    val best = summary.bestScore()
    val container = avg?.let { scoreSpectrumColor(it, LocalKanaColors.current.isDark) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    val subtitle = when {
        avg != null && best != null -> "avg $avg · best $best"
        avg != null -> "avg $avg"
        else -> "Not practiced yet"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Text(
                text = glyph,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = PrintFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The Sets tab body (the third Library tab, beside Characters and Words): list
 * the active script's saved sets with actions to quiz, practice (no scoring
 * side effects), edit membership, rename, or delete. [sets] arrives already
 * script-scoped AND name-filtered by the shared top-bar search; [hasAnySets]
 * distinguishes "no sets at all" (guidance to create one) from "no search
 * match". A slim header carries the count, the persisted [sortMode] menu, and
 * the "New" button whose menu picks the set type (there is no browse tab to
 * infer it from here).
 */
@Composable
private fun SetsTabContent(
    sets: List<PracticeSet>,
    hasAnySets: Boolean,
    query: String,
    activeScript: Script,
    scoreStats: Map<String, CharacterScoreSummary>,
    // Hoisted (persisted) sort order: this composable leaves composition during
    // the edit flow and set-launched quizzes, so a local remember would silently
    // reset the learner's chosen order on every return.
    sortMode: SetSort,
    onSortSelected: (SetSort) -> Unit,
    onNewSet: (PracticeSetType) -> Unit,
    onGenerateStrongKanaSet: () -> Unit,
    onEdit: (PracticeSet) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onQuiz: (Long) -> Unit,
    onPractice: (Long) -> Unit,
) {
    // Each set's average score (null = no scored members), computed once and
    // reused for both sorting and the per-row badge.
    val scoreOf = remember(sets, scoreStats) {
        sets.associate { it.id to setAverage(it, scoreStats) }
    }
    val sortedSets = remember(sets, sortMode, scoreOf) {
        when (sortMode) {
            SetSort.NAME_ASC -> sets.sortedBy { it.name.lowercase() }
            SetSort.NAME_DESC -> sets.sortedByDescending { it.name.lowercase() }
            // Unscored sets sort to the end in both directions.
            SetSort.SCORE_DESC -> sets.sortedByDescending { scoreOf[it.id] ?: Int.MIN_VALUE }
            SetSort.SCORE_ASC -> sets.sortedBy { scoreOf[it.id] ?: Int.MAX_VALUE }
            // Recency = created OR last edited (rename/member change bumps a
            // set back to the top of "newest"). The id tie-break keeps the
            // batch-seeded default sets — stamped within the same few ms — in
            // their true insertion order.
            SetSort.NEWEST -> sets.sortedWith(
                compareByDescending<PracticeSet> { it.updatedAt }.thenByDescending { it.id }
            )
            SetSort.OLDEST -> sets.sortedWith(
                compareBy<PracticeSet> { it.updatedAt }.thenBy { it.id }
            )
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // Slim header: a leading (persisted) sort menu + the count + an
        // "Add new set" button whose menu picks the set type. Always rendered —
        // even with zero sets, "Add new set" must stay reachable; only the sort
        // menu is count-gated.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sets.size > 1) {
                SetSortMenu(sortMode = sortMode, onSelect = onSortSelected)
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = "${sets.size} set${if (sets.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            var newExpanded by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(
                    onClick = { newExpanded = true },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Add new set")
                }
                DropdownMenu(
                    expanded = newExpanded,
                    onDismissRequest = { newExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Character set") },
                        onClick = {
                            newExpanded = false
                            onNewSet(PracticeSetType.CHARACTERS)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Word set") },
                        onClick = {
                            newExpanded = false
                            onNewSet(PracticeSetType.WORDS)
                        },
                    )
                    HorizontalDivider()
                    // One-tap: a Word set of everything whose kana you've already
                    // mastered (85+ in writing AND reading).
                    DropdownMenuItem(
                        text = { Text("From my strong kana") },
                        onClick = {
                            newExpanded = false
                            onGenerateStrongKanaSet()
                        },
                    )
                }
            }
        }
        HorizontalDivider()
        if (!hasAnySets) {
            Text(
                text = "No ${activeScript.key} practice sets yet. Tap “Add new set” to build " +
                    "one from any characters or words. (Sets follow the script " +
                    "picked in Options.)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else if (sets.isEmpty()) {
            Text(
                text = "No sets match “${query.trim()}”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            // Jump back to the top when the order's HEAD changes — picking a
            // sort, a rename/member edit bumping a set to #1 under the Updated
            // sorts, or a search keystroke changing the first match. The keyed
            // LazyColumn otherwise stays anchored on the previously-top item, so
            // the freshly bumped set slides in ABOVE the viewport and sits there
            // invisible (user-caught: "it goes to the top, but it is off screen").
            val setsListState = rememberLazyListState()
            LaunchedEffect(sortMode, sortedSets.firstOrNull()?.id) {
                setsListState.scrollToItem(0)
            }
            LazyColumn(
                state = setsListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sortedSets, key = { it.id }) { set ->
                    SetManagerRow(
                        set = set,
                        score = scoreOf[set.id],
                        onEdit = { onEdit(set) },
                        onRename = { name -> onRename(set.id, name) },
                        onDelete = { onDelete(set.id) },
                        onQuiz = { onQuiz(set.id) },
                        onPractice = { onPractice(set.id) },
                    )
                }
            }
        }
    }
}

/** Sort orders for the practice-sets manager. */
private enum class SetSort {
    NAME_ASC, NAME_DESC, SCORE_DESC, SCORE_ASC, NEWEST, OLDEST;

    companion object {
        /** Decode a persisted [name]; unknown/null falls back to [NAME_ASC]. */
        fun fromKey(key: String?): SetSort =
            entries.firstOrNull { it.name == key } ?: NAME_ASC
    }
}

/**
 * Average of a set's members' scored history (avgForm ?: avgFit), over members
 * that have any scored attempt; null when none are scored. Same source as the
 * char/word cards, so scoring-off runs (which record nothing) never contribute.
 */
private fun setAverage(set: PracticeSet, scoreStats: Map<String, CharacterScoreSummary>): Int? =
    set.members.mapNotNull { scoreStats[it].avgScore() }
        .let { if (it.isEmpty()) null else it.average().roundToInt() }

/**
 * Generic sort control: a hand-drawn sort glyph (icon-only when [label] is null,
 * else glyph + label) that drops down the [options], checking the active one.
 * Shared by the sets manager and the character/word list + selection sorts.
 */
@Composable
private fun <T> SortMenu(
    contentDescription: String,
    label: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (label == null) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
                val color = LocalContentColor.current
                Canvas(modifier = Modifier.size(22.dp), contentDescription = contentDescription) {
                    drawSortGlyph(color)
                }
            }
        } else {
            TextButton(onClick = { expanded = true }) {
                val color = LocalContentColor.current
                Canvas(modifier = Modifier.size(18.dp), contentDescription = contentDescription) {
                    drawSortGlyph(color)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(label)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (mode, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                    trailingIcon = if (mode == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** The set-manager sort: icon-only [SortMenu] over the four name/score orders. */
@Composable
private fun SetSortMenu(sortMode: SetSort, onSelect: (SetSort) -> Unit) {
    SortMenu(
        contentDescription = "Sort sets",
        label = null,
        options = SET_SORT_OPTIONS,
        selected = sortMode,
        onSelect = onSelect,
    )
}

private val SET_SORT_OPTIONS = listOf(
    SetSort.NAME_ASC to "Name: A → Z",
    SetSort.NAME_DESC to "Name: Z → A",
    SetSort.NEWEST to "Updated: Newest first",
    SetSort.OLDEST to "Updated: Oldest first",
    SetSort.SCORE_DESC to "Score: High → Low",
    SetSort.SCORE_ASC to "Score: Low → High",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetManagerRow(
    set: PracticeSet,
    score: Int?,
    onEdit: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onQuiz: () -> Unit,
    onPractice: () -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val kindLabel = if (set.type == PracticeSetType.WORDS) "words" else "characters"
    // Tint the whole card by the set's average score, matching the char/word cards.
    val cardColors = score?.let {
        CardDefaults.cardColors(containerColor = scoreSpectrumColor(it, LocalKanaColors.current.isDark))
    } ?: CardDefaults.cardColors()
    Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = set.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Leave room for the corner score so a long name can't collide.
                    modifier = Modifier.padding(end = 28.dp),
                )
                Text(
                    text = "${set.members.size} $kindLabel · ${set.script.displayLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // All five actions on ONE line at any font scale: the labels
                // share a single shrink factor (uniform, so no label looks out
                // of place) instead of the old FlowRow wrap, which dropped
                // Delete onto a lonely second line on large-font devices.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val actions = listOf(
                        "Quiz" to onQuiz,
                        "Practice" to onPractice,
                        "Edit" to onEdit,
                        "Rename" to { showRename = true },
                        "Delete" to { showDeleteConfirm = true },
                    )
                    val actionStyle = MaterialTheme.typography.labelLarge
                    val scale = rememberUniformTextScale(
                        labels = actions.map { it.first },
                        style = actionStyle,
                        availableWidth = maxWidth,
                        // 12dp content padding + the row's 4dp spacing = 16dp
                        // per button, plus 2dp of slack for per-label pixel
                        // rounding. At exactly 15dp the row came out ~4dp too
                        // wide and clipped "Delete" to "Dele" (360dp, font 1.3).
                        reservedPerItem = 18.dp,
                        // A clickable Surface keeps a 48dp minimum touch
                        // target, so "Quiz" and "Edit" each take 48dp (+4dp
                        // spacing) however small their text shrinks. Leaving
                        // that out is what clipped "Delete" at large fonts.
                        minItemWidth = 52.dp,
                    )
                    val shrunkStyle = actionStyle.copy(
                        fontSize = actionStyle.fontSize * scale,
                        lineHeight = if (actionStyle.lineHeight.isSp) {
                            actionStyle.lineHeight * scale
                        } else {
                            actionStyle.lineHeight
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        actions.forEach { (label, action) ->
                            TextButton(
                                onClick = action,
                                // TextButton's default 58dp min width defeats
                                // the shrink; 40dp height keeps touch targets.
                                modifier = Modifier.defaultMinSize(
                                    minWidth = 1.dp,
                                    minHeight = 40.dp,
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = shrunkStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
            if (score != null) {
                Text(
                    text = "$score",
                    // Chrome text (non-scaling): a corner badge, like an icon.
                    fontSize = 13.dp.toNonScalingSp(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = ScoreTextStyle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 16.dp),
                )
            }
        }
    }

    if (showRename) {
        SetNameDialog(
            initialName = set.name,
            itemCount = set.members.size,
            onConfirm = { name ->
                onRename(name)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete “${set.name}”?") },
            text = { Text("This removes the saved set. Your practice history isn't affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/** Prompts for a set name on save (new set) or rename (existing set). */
@Composable
private fun SetNameDialog(
    initialName: String,
    itemCount: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Name this set" else "Save set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Set name") },
                    singleLine = true,
                )
                Text(
                    text = "$itemCount item${if (itemCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
