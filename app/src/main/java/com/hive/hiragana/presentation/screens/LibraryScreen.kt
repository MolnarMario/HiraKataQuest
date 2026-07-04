package com.hive.hiragana.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.hive.hiragana.domain.model.FlowSection
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.KanaChartBuilder
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.scoring.CharacterScoreSummary
import com.hive.hiragana.domain.model.ScriptScope
import com.hive.hiragana.domain.model.TableRow
import com.hive.hiragana.domain.model.TableSection
import com.hive.hiragana.domain.model.VocabularyWord

/**
 * Browsable library of every character and vocabulary word (Phase 3.1),
 * split into a Characters tab and a Words tab.
 *
 * Everything is tappable — the library is the deliberate escape hatch from
 * SRS gating, so unlearned characters and still-locked words can be freely
 * practiced too; status labels ("Learned", "Locked") are informational
 * only. Picks route to [PracticeViewModel.practiceCharacter] /
 * [PracticeViewModel.practiceWord], which run plain FREE practice with no
 * scheduling or unlock side effects.
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
 * Rendered as a full-screen overlay above whatever phase is active; Back
 * (or the arrow) closes it — except while searching, when Back just
 * collapses the search field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    characters: List<HiraganaCharacter>,
    words: List<VocabularyWord>,
    learnedCharacterIds: Set<String>,
    unlockedWordKana: Set<String>,
    scoreStats: Map<String, CharacterScoreSummary> = emptyMap(),
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
    pronunciation: (VocabularyWord) -> String = { "" },
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showScopeSheet by rememberSaveable { mutableStateOf(false) }

    // While searching, system Back collapses the field instead of closing
    // the whole overlay (registered after PracticeScreen's close handler, so
    // it wins by LIFO only when enabled).
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    // Scope to the active script and the three opt-ins first (the selector +
    // toggles are the "what am I studying" controls), then apply the free-text
    // search on top. Both narrowings feed the tab match-count labels below.
    val scriptCharacters = ScriptScope.scopedCharacters(
        characters, activeScript, includeDiacritics, includeYoon, includeExtended,
    )
    val scriptWords = ScriptScope.scopedWords(
        words, activeScript, includeDiacritics, includeYoon, includeExtended,
    )
    val trimmedQuery = query.trim()
    val filteredCharacters = if (trimmedQuery.isEmpty()) {
        scriptCharacters
    } else {
        scriptCharacters.filter { it.matches(trimmedQuery) }
    }
    val filteredWords = if (trimmedQuery.isEmpty()) {
        scriptWords
    } else {
        scriptWords.filter { it.matches(trimmedQuery) }
    }

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
        Column(modifier = Modifier.fillMaxSize()) {
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
                showViewToggle = !searching,
                isGridView = isGridView,
                onToggleView = { onSetGridView(!isGridView) },
            )

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Characters (${filteredCharacters.size})") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Words (${filteredWords.size})") },
                )
            }

            when {
                gridMode && selectedTab == 0 -> CharacterChart(
                    characters = filteredCharacters,
                    learnedCharacterIds = learnedCharacterIds,
                    onPracticeCharacter = onPracticeCharacter,
                )
                gridMode -> WordGrid(
                    words = filteredWords,
                    unlockedWordKana = unlockedWordKana,
                    pronunciation = pronunciation,
                    onPracticeWord = onPracticeWord,
                )
                else -> LazyColumn(
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
                        items(filteredCharacters, key = { it.id }) { character ->
                            LibraryRow(
                                onClick = { onPracticeCharacter(character) },
                                glyph = character.character,
                                title = character.romaji,
                                subtitle = characterSubtitle(
                                    character.strokeCount,
                                    scoreStats[character.character],
                                ),
                                status = if (character.id in learnedCharacterIds) {
                                    "Learned"
                                } else {
                                    "Not started"
                                },
                                statusHighlighted = character.id in learnedCharacterIds,
                            )
                        }
                        if (filteredCharacters.isEmpty()) {
                            item { NoMatchesMessage(trimmedQuery, "characters") }
                        }
                    } else {
                        items(filteredWords, key = { it.kana }) { word ->
                            LibraryRow(
                                onClick = { onPracticeWord(word) },
                                glyph = word.kana,
                                title = word.meaning,
                                subtitle = pronunciation(word),
                                status = if (word.kana in unlockedWordKana) "Unlocked" else "Locked",
                                statusHighlighted = word.kana in unlockedWordKana,
                            )
                        }
                        if (filteredWords.isEmpty()) {
                            item { NoMatchesMessage(trimmedQuery, "words") }
                        }
                    }

                    item {
                        Text(
                            text = "Tap anything to practice it freely — grades here " +
                                "don't affect your review schedule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        if (showScopeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showScopeSheet = false },
                sheetState = rememberModalBottomSheetState(),
            ) {
                ScopeSheetContent(
                    activeScript = activeScript,
                    onSelectScript = onSelectScript,
                    includeDiacritics = includeDiacritics,
                    onToggleDiacritics = onToggleDiacritics,
                    includeYoon = includeYoon,
                    onToggleYoon = onToggleYoon,
                    includeExtended = includeExtended,
                    onToggleExtended = onToggleExtended,
                )
            }
        }
    }
}

/**
 * The single header row. Back + title normally; while [searching] the title
 * slot becomes a borderless autofocused search field. Actions: search
 * toggle, Filter (dotted when [scopeIsNonDefault]) opening the scope sheet,
 * and the "Quiz me!" CTA. Window insets are zeroed because the host activity
 * is not edge-to-edge (the system already reserves the status-bar strip).
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
) {
    TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close library",
                )
            }
        },
        title = {
            if (searching) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search romaji, kana, or meaning") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            } else {
                Text("Library")
            }
        },
        actions = {
            // List ⇄ chart(grid) view toggle. Shows the icon of the mode you'd
            // switch TO; hidden while the search field is open (search is a list).
            if (showViewToggle) {
                IconButton(onClick = onToggleView) {
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
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (searching) "Close search" else "Search",
                )
            }
            BadgedBox(
                badge = { if (scopeIsNonDefault) Badge() },
            ) {
                IconButton(onClick = onOpenScope) {
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
            // because that's the ungated browse-anything space.
            FilledTonalButton(
                onClick = onStartQuiz,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Create,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Quiz me!")
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
    includeDiacritics: Boolean,
    onToggleDiacritics: (Boolean) -> Unit,
    includeYoon: Boolean,
    onToggleYoon: (Boolean) -> Unit,
    includeExtended: Boolean,
    onToggleExtended: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Study scope",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Script.entries.forEachIndexed { index, script ->
                SegmentedButton(
                    selected = script == activeScript,
                    onClick = { onSelectScript(script) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = Script.entries.size,
                    ),
                    icon = {},
                    label = { Text(script.displayLabel) },
                )
            }
        }

        ScopeToggleRow(
            checked = includeDiacritics,
            onCheckedChange = onToggleDiacritics,
            label = "Voiced kana (゛゜)",
            hint = "が, ざ, ぱ …",
        )
        ScopeToggleRow(
            checked = includeYoon,
            onCheckedChange = onToggleYoon,
            label = "Combined kana (ゃゅょ)",
            hint = "きゃ, しゅ, きょ …",
        )
        ScopeToggleRow(
            checked = includeExtended,
            onCheckedChange = onToggleExtended,
            label = "Extended kana (っ ァ)",
            hint = "っ, ファ, ティ …",
        )
    }
}

/** One opt-in: label + hint on the left, a [Switch] on the right. */
@Composable
private fun ScopeToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    hint: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
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
    learnedCharacterIds: Set<String>,
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
                            ChartTableRow(row, cellSize, learnedCharacterIds, onPracticeCharacter)
                        }
                    }
                    is FlowSection -> item {
                        ChartCellFlow(section.cells, cellSize, learnedCharacterIds, onPracticeCharacter)
                    }
                }
            }
            item {
                Text(
                    text = "Tap any cell to practice it freely — grades here don't " +
                        "affect your review schedule.",
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
                style = MaterialTheme.typography.labelSmall,
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
    learnedCharacterIds: Set<String>,
    onPracticeCharacter: (HiraganaCharacter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.rowLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(ChartRowLabelWidth),
        )
        row.cells.forEach { character ->
            if (character != null) {
                ChartCell(
                    character = character,
                    learned = character.id in learnedCharacterIds,
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
    learnedCharacterIds: Set<String>,
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
                learned = character.id in learnedCharacterIds,
                onClick = { onPracticeCharacter(character) },
                modifier = Modifier.size(cellSize),
            )
        }
    }
}

/** One chart cell: glyph over tiny romaji, tinted when learned; taps to practice. */
@Composable
private fun ChartCell(
    character: HiraganaCharacter,
    learned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (learned) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            // 2-codepoint kana (きゃ, ファ) get a smaller size so they fit the
            // fixed cell without clipping.
            text = character.character,
            fontSize = if (character.character.length > 1) 16.sp else 22.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = character.romaji,
            fontSize = 9.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The Words grid: a wrapping [FlowRow] of content-sized cards, so a long word
 * (ありがとう / パーティー) simply takes the width it needs and wraps — no fixed
 * grid squares to clip it. Unlocked words are tinted; every card taps into
 * free practice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordGrid(
    words: List<VocabularyWord>,
    unlockedWordKana: Set<String>,
    pronunciation: (VocabularyWord) -> String,
    onPracticeWord: (VocabularyWord) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (words.isEmpty()) {
            NoMatchesMessage("", "words")
            return@Column
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            words.forEach { word ->
                WordCard(
                    word = word,
                    romaji = pronunciation(word),
                    unlocked = word.kana in unlockedWordKana,
                    onClick = { onPracticeWord(word) },
                )
            }
        }
        Text(
            text = "Tap any word to practice it freely — grades here don't " +
                "affect your review schedule.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun WordCard(
    word: VocabularyWord,
    romaji: String,
    unlocked: Boolean,
    onClick: () -> Unit,
) {
    val container = if (unlocked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 76.dp)
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = word.kana,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Reading between the kana and the meaning (e.g. かう / ka u / to buy).
        Text(
            text = romaji,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = word.meaning,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun HiraganaCharacter.matches(query: String): Boolean =
    character.contains(query) ||
        romaji.contains(query, ignoreCase = true) ||
        id.contains(query, ignoreCase = true)

private fun VocabularyWord.matches(query: String): Boolean =
    kana.contains(query) ||
        meaning.contains(query, ignoreCase = true)

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
    val avg = summary?.let { it.avgForm ?: it.avgFit } ?: return base
    val best = summary.bestForm ?: summary.bestFit
    return if (best != null) "$base · avg $avg · best $best" else "$base · avg $avg"
}

/**
 * One tappable row: the glyph (or word kana) large on the left, name and
 * detail in the middle, learned/unlocked status on the right.
 */
@Composable
private fun LibraryRow(
    onClick: () -> Unit,
    glyph: String,
    title: String,
    subtitle: String,
    status: String,
    statusHighlighted: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
            }
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = if (statusHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(72.dp),
            )
        }
    }
}
