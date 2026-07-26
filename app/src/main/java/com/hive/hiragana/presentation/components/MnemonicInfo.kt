package com.hive.hiragana.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.CUSTOM_MNEMONIC_STORY_MAX
import com.hive.hiragana.domain.model.CUSTOM_MNEMONIC_TITLE_MAX
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.KanaMnemonic
import com.hive.hiragana.domain.model.KanaMnemonics
import com.hive.hiragana.domain.model.MnemonicOverlay
import com.hive.hiragana.presentation.theme.LocalKanaColors
import kotlinx.coroutines.launch

/**
 * Everything the mnemonic dialog's picker needs beyond the built-in table:
 * the learner's own entries per glyph, and the sinks to add/delete one.
 * Bundled so surfaces thread one param instead of three.
 */
@Immutable
data class CustomMnemonicStore(
    val byGlyph: Map<String, List<KanaMnemonic>> = emptyMap(),
    val onAdd: (glyph: String, title: String, description: String) -> Unit = { _, _, _ -> },
    val onDelete: (glyph: String, title: String) -> Unit = { _, _ -> },
)

/** Built-in candidates for [glyph] plus the learner's own, customs first. */
private fun mergedCandidates(glyph: String, customs: CustomMnemonicStore): List<KanaMnemonic> =
    customs.byGlyph[glyph].orEmpty() + KanaMnemonics.candidatesFor(glyph)

/**
 * The mnemonic shown for [glyph]: the entry whose keyword the learner chose
 * (built-in alternate OR their own), else the built-in default, else — only
 * when there is no built-in — their first custom. Null when the glyph has no
 * mnemonic at all.
 */
private fun resolveCurrentMnemonic(
    glyph: String,
    chosen: String?,
    customs: CustomMnemonicStore,
): KanaMnemonic? {
    val builtIns = KanaMnemonics.candidatesFor(glyph)
    val merged = customs.byGlyph[glyph].orEmpty() + builtIns
    if (merged.isEmpty()) return null
    return merged.firstOrNull { it.keyword == chosen } ?: builtIns.firstOrNull() ?: merged.first()
}

/**
 * The memory-hint ("mnemonic") affordances backed by [KanaMnemonics]: a
 * keyword anchoring a kana's sound plus a short story tying its shape to that
 * keyword, optionally illustrated by accent strokes drawn over the real glyph.
 *
 * Two entry points, both filtering their units through [KanaMnemonics] and
 * rendering NOTHING when no unit has a mnemonic, so callers pass
 * unconditionally (the [PrintDivergenceBadge] discipline). Callers gate on
 * the persisted "Memory hints" setting (`PracticeUiState.mnemonicsEnabled`).
 *
 *  - [MnemonicBadge]: the "How to remember" chip for reveal surfaces (free
 *    practice footer, flashcard reveal, quiz reveals) — the answer is on
 *    screen, so the dialog shows the glyph specimen with its overlay.
 *  - [MnemonicHintButton]: the pre-answer "Hint" for quizzes/flashcards. In
 *    the WRITING quiz the shape IS the hidden answer, so [showGlyph] = false
 *    there strips the specimen AND the kana from the dialog (the headline
 *    names the romaji, which is already the visible prompt); the story's
 *    words are the hint. Reading surfaces show the glyph as usual — there
 *    the glyph is the visible prompt and the keyword hints the hidden sound.
 *
 * Purely informative: nothing here records attempts or touches scoring.
 */
@Composable
fun MnemonicBadge(
    unitCharacters: List<HiraganaCharacter>,
    modifier: Modifier = Modifier,
    label: String = "How to remember",
    // Icon-only rendering for tight chrome (e.g. sharing the practice footer
    // with the divergence chip at 360dp, or the writing-quiz header column).
    iconOnly: Boolean = false,
    // The learner's promoted alternates (glyph -> keyword) + the picker's
    // selection sink; default = every kana shows its default mnemonic.
    mnemonicChoices: Map<String, String> = emptyMap(),
    onChoose: (String, String?) -> Unit = { _, _ -> },
    // The learner's own entries + add/delete sinks (the "Add your own" flow).
    customs: CustomMnemonicStore = CustomMnemonicStore(),
) {
    val entries = rememberMnemonicEntries(unitCharacters, mnemonicChoices, customs)
    if (entries.isEmpty()) return

    // Keyed on the affected glyphs so advancing to a different card/character
    // closes a left-open dialog instead of silently swapping its content.
    var showDialog by rememberSaveable(entries.joinToString("") { it.first.character }) {
        mutableStateOf(false)
    }

    Surface(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(percent = 50),
        // primaryContainer (vermilion family): visually distinct from the
        // divergence chip's secondaryContainer when the two sit side by side.
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        if (iconOnly) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                BulbIcon(modifier = Modifier.size(16.dp))
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BulbIcon(modifier = Modifier.size(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }

    if (showDialog) {
        MnemonicDialog(
            entries = entries,
            showGlyph = true,
            onChoose = onChoose,
            customs = customs,
            onDismiss = { showDialog = false },
        )
    }
}

/**
 * The pre-answer "Hint" affordance: a low-emphasis text button (the calm
 * helper styling of the "New to kana?" button) opening the mnemonic dialog.
 * [showGlyph] = false is the writing-quiz variant — see the file doc.
 */
@Composable
fun MnemonicHintButton(
    unitCharacters: List<HiraganaCharacter>,
    showGlyph: Boolean,
    modifier: Modifier = Modifier,
    mnemonicChoices: Map<String, String> = emptyMap(),
    onChoose: (String, String?) -> Unit = { _, _ -> },
    customs: CustomMnemonicStore = CustomMnemonicStore(),
) {
    val entries = rememberMnemonicEntries(unitCharacters, mnemonicChoices, customs)
    if (entries.isEmpty()) return

    var showDialog by rememberSaveable(entries.joinToString("") { it.first.character }) {
        mutableStateOf(false)
    }

    TextButton(
        onClick = { showDialog = true },
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier,
    ) {
        BulbIcon(modifier = Modifier.size(18.dp))
        Text(
            text = "Hint",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
    }

    if (showDialog) {
        MnemonicDialog(
            entries = entries,
            showGlyph = showGlyph,
            onChoose = onChoose,
            customs = customs,
            onDismiss = { showDialog = false },
        )
    }
}

/**
 * The units that actually carry a mnemonic, deduped, each resolved to its
 * current entry (a chosen alternate, the learner's own, or the built-in
 * default — see [resolveCurrentMnemonic]). Keyed on the choices + customs so
 * promoting/adding/deleting re-resolves the open dialog in place.
 */
@Composable
private fun rememberMnemonicEntries(
    unitCharacters: List<HiraganaCharacter>,
    mnemonicChoices: Map<String, String>,
    customs: CustomMnemonicStore,
): List<Pair<HiraganaCharacter, KanaMnemonic>> =
    remember(unitCharacters, mnemonicChoices, customs.byGlyph) {
        unitCharacters
            .mapNotNull { ch ->
                resolveCurrentMnemonic(ch.character, mnemonicChoices[ch.character], customs)
                    ?.let { ch to it }
            }
            .distinctBy { it.first.character }
    }

/**
 * One scrolling section per affected kana (a word can carry several).
 *
 * A custom [Dialog] rather than an M3 AlertDialog, because the "Add your own"
 * editor has text fields: an AlertDialog's window does NOT resize for the
 * soft keyboard, so the Description field and Save sat trapped behind it,
 * untappable (device-found). Here the dialog window is made edge-to-edge
 * ([WindowCompat.setDecorFitsSystemWindows] false) so the IME inset reaches
 * it, and [imePadding] lifts the card into the space above the keyboard while
 * the body scrolls — so both fields stay visible and tappable, and a longer
 * description is never hidden. The card keeps the AlertDialog look
 * ([AlertDialogDefaults] shape/color/elevation).
 */
@Composable
private fun MnemonicDialog(
    entries: List<Pair<HiraganaCharacter, KanaMnemonic>>,
    showGlyph: Boolean,
    onChoose: (String, String?) -> Unit,
    customs: CustomMnemonicStore,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        if (window != null) {
            SideEffect { WindowCompat.setDecorFitsSystemWindows(window, false) }
        }
        // Cap the card so it uses the vertical space generously without going
        // edge-to-edge; it centers in whatever room the keyboard leaves.
        val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
        val scrimInteraction = remember { MutableInteractionSource() }
        val cardInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tapping the dim scrim dismisses (AlertDialog behavior); no
                // ripple. imePadding shrinks this box to the space above the
                // keyboard so the centered card sits above it.
                .clickable(interactionSource = scrimInteraction, indication = null) { onDismiss() }
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .heightIn(max = maxCardHeight)
                    // Swallow taps on the card so they don't reach the scrim
                    // dismiss above (child fields/buttons consume their own).
                    .clickable(interactionSource = cardInteraction, indication = null) {},
            ) {
                // Which glyph's "Add your own" editor is open (null = none).
                // Owned here so the dialog can switch to a focused add MODE:
                // its own header + pinned Cancel/Save and NO "Got it" footer
                // (that footer is redundant while adding and was colliding with
                // Save — user-caught). Resets when the shown item changes.
                val entriesKey = entries.joinToString("") { it.first.character }
                var editingGlyph by rememberSaveable(entriesKey) { mutableStateOf<String?>(null) }
                val editingChar = editingGlyph?.let { g ->
                    entries.firstOrNull { it.first.character == g }?.first
                }

                Column(modifier = Modifier.padding(24.dp)) {
                    if (editingChar != null) {
                        AddYourOwnEditor(
                            character = editingChar,
                            showGlyph = showGlyph,
                            onCancel = { editingGlyph = null },
                            onSave = { title, description ->
                                customs.onAdd(editingChar.character, title, description)
                                editingGlyph = null
                            },
                        )
                    } else {
                        Text(
                            text = "How to remember",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            entries.forEach { (character, mnemonic) ->
                                MnemonicSection(
                                    character = character,
                                    mnemonic = mnemonic,
                                    showGlyph = showGlyph,
                                    onChoose = onChoose,
                                    customs = customs,
                                    onStartAdd = { editingGlyph = character.character },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onDismiss) { Text("Got it") }
                        }
                    }
                }
            }
        }
    }
}

/** Square side of the glyph specimen (matches the divergence dialog's). */
private val MnemonicSpecimenSize = 72.dp

@Composable
private fun MnemonicSection(
    character: HiraganaCharacter,
    mnemonic: KanaMnemonic,
    showGlyph: Boolean,
    onChoose: (String, String?) -> Unit,
    customs: CustomMnemonicStore,
    onStartAdd: () -> Unit,
) {
    val glyph = character.character
    val builtIns = remember(glyph) { KanaMnemonics.candidatesFor(glyph) }
    val builtInDefault = builtIns.firstOrNull()?.keyword
    val customList = customs.byGlyph[glyph].orEmpty()
    val customTitles = remember(customList) { customList.map { it.keyword }.toSet() }
    // Everything except the one currently shown — revealed under "Show more".
    val others = (customList + builtIns).filter { it.keyword != mnemonic.keyword }
    val currentIsCustom = mnemonic.keyword in customTitles

    // showMore resets when the resolved keyword changes (a pick collapses it).
    // The "Add your own" editor is a dialog-level MODE (onStartAdd), not local
    // state here, so the dialog can drop its "Got it" footer while editing.
    var showMore by remember(glyph, mnemonic.keyword) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showGlyph) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                MnemonicGlyph(
                    character = character,
                    overlay = mnemonic.overlay,
                    modifier = Modifier.size(MnemonicSpecimenSize),
                )
            }
        }
        // "あ is APPLE" — or, when the glyph is the hidden quiz answer,
        // "a is APPLE" (the romaji is already the visible prompt).
        Text(
            text = buildAnnotatedString {
                append(if (showGlyph) glyph else character.romaji)
                append(" is ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(mnemonic.keyword)
                }
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = mnemonic.story,
            style = MaterialTheme.typography.bodyMedium,
        )

        // Everyone's brain hooks onto different images. The shown mnemonic is
        // the current default; the OTHER options stay hidden behind "Show more"
        // so the dialog reads as one clear hint by default. Picking one (a
        // built-in alternate OR one of the learner's own) promotes it to this
        // kana's default everywhere and collapses the list — showMore is keyed
        // on the resolved keyword, so a pick resets it. "Add your own" opens the
        // inline editor above; "Delete" removes the shown mnemonic when it is
        // one of the learner's own.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (others.isNotEmpty() && !showMore) {
                CompactTextButton("Show more") { showMore = true }
            }
            CompactTextButton("Add your own") { onStartAdd() }
            if (currentIsCustom) {
                CompactTextButton("Delete") { customs.onDelete(glyph, mnemonic.keyword) }
            }
        }

        if (showMore) {
            Text(
                text = "More ways to remember",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            others.forEach { candidate ->
                val isCustom = candidate.keyword in customTitles
                Surface(
                    onClick = {
                        // Picking the app default clears the stored choice.
                        onChoose(glyph, candidate.keyword.takeIf { it != builtInDefault })
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = candidate.keyword,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                when {
                                    isCustom -> Text(
                                        text = "yours",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    candidate.keyword == builtInDefault -> Text(
                                        text = "default",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = candidate.story,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // The learner's own entries can be deleted from here;
                        // the delete tap is consumed so it doesn't also pick.
                        if (isCustom) {
                            IconButton(onClick = { customs.onDelete(glyph, candidate.keyword) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete your ${candidate.keyword} mnemonic",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The focused "Add your own" mode inside the dialog: its own header, a scroll
 * region for the two fields (a CAPS title + a description, length-limited with
 * live counters), and a PINNED Cancel/Save row that never scrolls off. Save is
 * enabled only when both fields are filled; it hands the raw text to the
 * ViewModel, which trims, uppercases the title, clamps to the limits, and
 * promotes the entry to the default.
 *
 * A ColumnScope receiver so the fields region can take `weight(1f)` of the
 * card (scrolling) while the header and the action row stay fixed — that is
 * what keeps Save reachable without a redundant "Got it" footer beneath it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.AddYourOwnEditor(
    character: HiraganaCharacter,
    showGlyph: Boolean,
    onCancel: () -> Unit,
    onSave: (title: String, description: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    // Belt-and-suspenders for a growing description: on focus, scroll it into
    // the (keyboard-shrunk) dialog viewport so its lower lines never hide.
    val descIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Text(
        text = "Add your own",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = "Your own way to remember " +
            if (showGlyph) character.character else character.romaji,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(CUSTOM_MNEMONIC_TITLE_MAX).uppercase() },
            label = { Text("Title") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            supportingText = { Text("${title.length}/$CUSTOM_MNEMONIC_TITLE_MAX") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(CUSTOM_MNEMONIC_STORY_MAX) },
            label = { Text("Description") },
            minLines = 2,
            maxLines = 4,
            supportingText = { Text("${description.length}/$CUSTOM_MNEMONIC_STORY_MAX") },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(descIntoView)
                .onFocusEvent {
                    if (it.isFocused) scope.launch { runCatching { descIntoView.bringIntoView() } }
                },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Button(
            onClick = { onSave(title, description) },
            enabled = title.isNotBlank() && description.isNotBlank(),
        ) {
            Text("Save")
        }
    }
}

/** Low-emphasis text button with tight padding for the section action row. */
@Composable
private fun CompactTextButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(text)
    }
}

/**
 * Overlay accent drawings are DISABLED (2026-07-26): the hand-authored
 * accents did not read clearly at dialog size (they looked like stray specks
 * over the glyph, not a depiction of the keyword). The [OverlayPath] /
 * [MnemonicOverlay] model, the KanaMnemonics OVERLAY blocks, and the render
 * path below are all kept intact so a stronger, externally-authored set can
 * be dropped straight back in — flip this to true to re-enable rendering.
 */
private const val OVERLAYS_ENABLED = false

/**
 * The kana's real KanjiVG strokes as muted-grey centerlines. When overlays
 * are enabled ([OVERLAYS_ENABLED]) the mnemonic's accent overlay is drawn on
 * top in the theme's vermilion, both in the shared viewBox space, so the
 * drawing sits ON the shape the learner traces. Currently overlays are off,
 * so this renders the plain grey specimen for every kana.
 */
@Composable
private fun MnemonicGlyph(
    character: HiraganaCharacter,
    overlay: MnemonicOverlay?,
    modifier: Modifier = Modifier,
) {
    val overlayPaths: List<Pair<Path, Boolean>> = remember(overlay) {
        if (!OVERLAYS_ENABLED) return@remember emptyList()
        overlay?.paths.orEmpty().mapNotNull { p ->
            runCatching { PathParser().parsePathString(p.d).toPath() }.getOrNull()
                ?.let { it to p.filled }
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        // The glyph itself: the shared KanjiVG centerline render
        // (PrintDivergenceInfo's specimen), muted so the accent pops.
        StrokesGlyph(
            strokes = character.strokes,
            viewBox = character.viewBox,
            modifier = Modifier.matchParentSize(),
            color = LocalKanaColors.current.diagramStroke,
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            if (overlayPaths.isEmpty()) return@Canvas
            val scale = size.minDimension / character.viewBox
            withTransform({ scale(scale, scale, Offset.Zero) }) {
                overlayPaths.forEach { (path, filled) ->
                    if (filled) {
                        drawPath(path = path, color = accent)
                    } else {
                        drawPath(
                            path = path,
                            color = accent,
                            style = Stroke(
                                // Slightly dominant over the grey glyph strokes.
                                width = 3.5.dp.toPx() / scale,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hand-drawn lightbulb glyph (the project's convention for icons the core
 * Material set lacks — material-icons-extended stays out of the deps): a
 * bulb circle, two base ticks, and three rays.
 */
@Composable
private fun BulbIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        // Bulb.
        drawCircle(
            color = tint,
            radius = w * 0.26f,
            center = Offset(w * 0.5f, h * 0.42f),
            style = Stroke(width = stroke),
        )
        // Screw base: two shrinking ticks under the bulb.
        drawLine(
            color = tint,
            start = Offset(w * 0.38f, h * 0.78f),
            end = Offset(w * 0.62f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.43f, h * 0.92f),
            end = Offset(w * 0.57f, h * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Rays: straight up plus two diagonals.
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.02f),
            end = Offset(w * 0.5f, h * 0.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.13f, h * 0.20f),
            end = Offset(w * 0.22f, h * 0.27f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.87f, h * 0.20f),
            end = Offset(w * 0.78f, h * 0.27f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
