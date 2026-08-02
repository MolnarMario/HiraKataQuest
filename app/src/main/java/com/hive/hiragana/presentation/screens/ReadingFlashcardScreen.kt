package com.hive.hiragana.presentation.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.hive.hiragana.presentation.theme.LocalKanaColors
import com.hive.hiragana.presentation.theme.correctColor
import com.hive.hiragana.presentation.theme.practiceMissColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.hive.hiragana.R
import com.hive.hiragana.domain.audio.VoiceStatus
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.MeaningMatch
import com.hive.hiragana.domain.model.PrintDivergence
import com.hive.hiragana.domain.model.RomajiMatch
import com.hive.hiragana.domain.model.SpecialKana
import com.hive.hiragana.presentation.components.CustomMnemonicStore
import com.hive.hiragana.presentation.components.KanjiBadge
import com.hive.hiragana.presentation.components.MnemonicBadge
import com.hive.hiragana.presentation.components.MnemonicHintButton
import com.hive.hiragana.presentation.components.PrintDivergenceBadge
import com.hive.hiragana.presentation.components.PronunciationButton
import com.hive.hiragana.presentation.components.SpecialKanaBadge
import com.hive.hiragana.presentation.viewmodels.ReadingFlashcardState
import kotlin.random.Random

// The reveal region's FIXED reserved height. It is reserved in every state
// (blank/Hint before answering, the result after) and the glyph takes the
// leftover above it — so the glyph box, hence the glyph, is identical in the
// question and answer states and nothing jumps on reveal. Sized to hold the
// tallest reveal we show: Correct!/Not quite + the reading·audio·meaning row +
// the badges row (the "Looks different" badge and the "How to remember" chip
// share ONE row — the chip goes icon-only when the badge is also present, the
// same rule the practice footer uses — so this stays 3 rows, never 4).
//
// ⚠️ IF YOU ADD A REVEAL ROW (or un-share the badges): raise this so it still
// fits. Do NOT drop the reserve / switch to wrapContentHeight / shrink the
// bottom section to "make room" — that regrows the card and makes the glyph
// jump. Under-sizing this reserve is what clipped the mnemonic chip on device
// (2026-07-26); the verticalScroll on the reveal is only an extreme-font safety.
private val RevealReserve = 132.dp

// A single answer-option row height. Every bottom-section slot uses it so the
// section is the same height whether it shows the options or just Next (with
// blank spacers for the spent slots) — that constant height keeps the card
// above from resizing, so the glyph doesn't jump on reveal.
private val OptionSlotHeight = 52.dp

/**
 * Reading-track flashcard study: flip through the scoped deck/words. The front
 * shows the printed (gothic) glyph/word.
 *
 * A **Typing | Multiple choice segmented control at the top** switches between
 * two equal study styles, flippable at any time:
 * - **Multiple choice** — tap the reading among deck distractors; the card
 *   reveals Correct!/Not quite + the answer, then Next advances.
 * - **Typing** — productive recall: a romaji field with an inline Check/Next
 *   button (~2/3 field, 1/3 button); type + Check (or the keyboard's Done)
 *   reveals the answer via the lenient [RomajiMatch]. Once revealed the field
 *   stops accepting input (the answer is locked) but keeps focus so the
 *   keyboard stays open, and its bottom-right key becomes Next — the whole run
 *   can be driven from the keyboard without it ever closing.
 *
 * An optional **"Also test meaning"** toggle (words only — characters have no
 * meaning) adds a second step: after the reading, the learner answers what the
 * word means, in the SAME style (type it in Typing, tap it in Multiple choice).
 * The card reveals both results together. Shown only for word decks.
 *
 * Pure study — answers are deliberately NOT recorded into the reading-quiz
 * percentages; only the reading quiz writes `reading_progress`.
 *
 * Layout: the glyph and the result/meaning region get FIXED shares of the card
 * ([GLYPH_SHARE]/[REVEAL_SHARE]) and the bottom section reserves a constant
 * height ([OptionSlotHeight] per slot), so the card never resizes and the glyph
 * stays the same size AND position before and after the result shows — nothing
 * jumps on reveal. The reveal gets the larger share, sized to hold all its rows
 * (result + reading + the "Looks different" badge + the "How to remember" chip)
 * so none of them clip; its verticalScroll is only a small-screen / large-font
 * safety. Nothing clips when the keyboard shrinks the window (adjustResize; on
 * API 30+ the IME arrives via PracticeScreen's safeDrawing inset host instead).
 * The field keeps focus across card flips, so the keyboard stays open for a
 * smooth run; it only closes when switching to Multiple choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingFlashcardScreen(
    state: ReadingFlashcardState,
    onReveal: (Boolean) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onSpeak: (String) -> Unit,
    ttsStatus: VoiceStatus,
    // Deck characters for a kana string's reading units — feeds the
    // "print differs" badge on the reveal (it needs strokes to render the
    // handwritten form next to the printed front).
    unitCharactersFor: (String) -> List<HiraganaCharacter> = { emptyList() },
    // Whether to also test the word's meaning after the reading (words only).
    testMeaning: Boolean = false,
    onSetTestMeaning: (Boolean) -> Unit = {},
    // Memory hints: the pre-answer Hint button + the "How to remember" chip
    // on the reveal (both render only where a kana has a mnemonic), plus the
    // learner's promoted alternates and the picker's selection sink.
    mnemonicsEnabled: Boolean = true,
    // Whether the informative "Kanji" button shows (opt-in; question + reveal).
    kanjiHintsEnabled: Boolean = false,
    mnemonicChoices: Map<String, String> = emptyMap(),
    onChooseMnemonic: (String, String?) -> Unit = { _, _ -> },
    customMnemonics: CustomMnemonicStore = CustomMnemonicStore(),
    modifier: Modifier = Modifier,
) {
    val card = state.current ?: return
    // Special marks (ー, small tsu, small vowels) are answered by their role
    // ("long vowel", "small tsu") and always by choice, since there is no romaji
    // to type. ん/を read normally, so markCard is false and they type as usual.
    val readingAnswer = SpecialKana.readingLabel(card.glyph) ?: card.romaji
    val markCard = SpecialKana.readingLabel(card.glyph) != null
    var typingMode by rememberSaveable { mutableStateOf(false) }
    // Typing is suppressed on a mark card even when the toggle is on.
    val effectiveTyping = typingMode && !markCard
    // Reading answer + result (shared by both answer styles). Keyed on
    // state.serial, NOT state.index: a 1-card deck wraps index 0→0 on Next, so
    // an index key would never reset and the card would re-reveal forever off
    // its stale answer (see ReadingFlashcardState.serial).
    var typed by remember(state.serial) { mutableStateOf("") }
    var readingOk by remember(state.serial) { mutableStateOf<Boolean?>(null) }
    // Meaning answer + result — used only when meaning practice is on and the
    // card is a word (characters have no meaning).
    var meaningTyped by remember(state.serial) { mutableStateOf("") }
    var meaningOk by remember(state.serial) { mutableStateOf<Boolean?>(null) }

    // Meaning practice is words-only; the toggle is hidden for a character deck.
    val isWordDeck = state.cards.any { it.meaning != null }
    val needMeaning = testMeaning && card.meaning != null

    // Option sets (correct + up to 3 deck distractors, fixed per card): reading
    // draws from the readings, meaning from the other meanings (distinct seed).
    val readingOptions = remember(state.index, state.cards) {
        // Correct answer + pool both use each card's readable reading (a role
        // label for marks, romaji otherwise) so no placeholder like "xtsu" shows.
        readingChoiceOptions(
            readingAnswer,
            state.cards.map { SpecialKana.readingLabel(it.glyph) ?: it.romaji },
            state.index,
        )
    }
    val meaningOptions = remember(state.index, state.cards) {
        readingChoiceOptions(card.meaning.orEmpty(), state.cards.mapNotNull { it.meaning }, state.index + 7)
    }

    // The card's reading units, resolved once per card (the reveal badges and
    // the hint all need them).
    val cardUnits = remember(card.glyph) { unitCharactersFor(card.glyph) }
    // Whether this card also carries the "Looks different in handwriting" badge
    // (only さ/き/り). When it does, the mnemonic chip beside it goes icon-only
    // so the two fit one row (keeps the reveal at 3 rows — see RevealReserve).
    val hasDivergence = remember(cardUnits) {
        cardUnits.any { PrintDivergence.noteFor(it.character) != null }
    }
    // A special-kana note (ー, small tsu, small vowels, ん, を). Present with a
    // mnemonic only on ん/を, so it takes text precedence over the mnemonic chip
    // (which goes icon-only) but yields to the rarer divergence warning.
    val hasSpecial = remember(cardUnits) {
        cardUnits.any { SpecialKana.noteFor(it.character) != null }
    }
    // The word's kanji spelling, if any — the informative "see the kanji" chip
    // (opt-in via the Kanji-reference toggle).
    val hasKanji = kanjiHintsEnabled && card.kanji != null

    // Reading answered but the meaning still owed — the intermediate step
    // before the card reveals.
    val inMeaningPhase = readingOk != null && needMeaning && meaningOk == null && !state.revealed
    // Combined pass: reading right AND (if tested) meaning right.
    val allOk = readingOk == true && (meaningOk != false)

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Keyboard follows the toggle: opens when typing is turned on, closes when
    // it's turned off. Focus is never cleared on Next, so it stays open across
    // cards for a continuous flow.
    LaunchedEffect(effectiveTyping) {
        if (effectiveTyping) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    // Reveal once the reading is answered and no meaning is owed — covers both
    // the meaning-off path and turning the toggle off mid-card.
    LaunchedEffect(readingOk, needMeaning, state.revealed) {
        if (readingOk != null && !needMeaning && !state.revealed) onReveal(readingOk == true)
    }

    // Answer the reading step; the effect above reveals when no meaning is owed.
    fun answerReading(correct: Boolean) { readingOk = correct }
    // Answer the meaning step and reveal the card.
    fun answerMeaning(correct: Boolean) { meaningOk = correct; onReveal(readingOk == true && correct) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The activity is windowSoftInputMode=adjustResize, so the window shrinks
        // to sit above the keyboard — the content lays out in the reduced height
        // (card shrinks, field + button stay just above the keys), no panning.
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close flashcards")
                }
                Text(
                    // Slightly smaller than the title scale so the meaning toggle
                    // fits beside it; takes the leftover width and truncates first.
                    text = "Card ${state.index + 1} of ${state.cards.size}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Words can also be quizzed on their MEANING (characters have
                // none, so this is hidden for a character deck). It sits in the
                // top-right corner to save a full row; flippable mid-run. The
                // meaning is asked in the SAME style as the reading, after it.
                if (isWordDeck) {
                    Text(
                        text = "Test meaning",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    // The Material switch is oversized next to a label-scale
                    // caption, so scale it down to read as proportional to it.
                    Switch(
                        checked = testMeaning,
                        onCheckedChange = onSetTestMeaning,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .scale(0.7f),
                    )
                }
            }

            // Two EQUAL study methods, so a segmented control — not an on/off
            // switch, which would frame one as the default and the other as an
            // "enabled" extra. The selected half is filled; neither is subordinate.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = typingMode,
                    onClick = { typingMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        // Checkmark when active (clear "selected" cue); the mode's
                        // own icon when not.
                        SegmentedButtonDefaults.Icon(
                            active = typingMode,
                            inactiveContent = {
                                Icon(
                                    imageVector = Icons.Filled.Create,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            },
                        )
                    },
                    label = {
                        Text(
                            "Typing",
                            fontWeight = if (typingMode) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                )
                SegmentedButton(
                    selected = !typingMode,
                    onClick = { typingMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(
                            active = !typingMode,
                            inactiveContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            },
                        )
                    },
                    label = {
                        Text(
                            "Multiple choice",
                            fontWeight = if (!typingMode) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                )
            }

            // Card area. The glyph and the result/meaning region get FIXED
            // shares of the card height, so the word stays exactly the same
            // size before and after the result shows — the result region is
            // reserved even while blank, rather than stealing height from the
            // glyph on reveal. The result region scrolls if a long meaning +
            // badge can't fit its share (never clips).
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val avail = maxHeight
                val vPad = when {
                    avail >= 360.dp -> 32.dp
                    avail >= 240.dp -> 20.dp
                    else -> 10.dp
                }
                val gap = when {
                    avail >= 320.dp -> 16.dp
                    avail >= 240.dp -> 10.dp
                    else -> 8.dp
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = vPad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // The glyph takes the LEFTOVER above the reveal's fixed
                            // reserve ([RevealReserve]). Because that reserve is a
                            // constant and the card height is constant (the bottom
                            // section reserves a constant height — see below), the
                            // glyph's box is identical in the question and answer
                            // states, so the glyph stays the exact same size AND
                            // position when you answer. Nothing jumps on reveal.
                            // FlashcardGlyph also size-caps itself, so extra
                            // leftover on a tall screen is just padding, never a
                            // stretched glyph.
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                FlashcardGlyph(
                                    glyph = card.glyph,
                                    boxWidth = this.maxWidth,
                                    boxHeight = this.maxHeight,
                                )
                            }
                            // Result/meaning region: the FIXED [RevealReserve]
                            // height (see its doc for the sizing rule + the ⚠️
                            // warning), reserved in both states so the glyph above
                            // never moves. verticalScroll is an extreme-font safety
                            // only; at normal sizes nothing scrolls.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(RevealReserve)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
                            ) {
                                if (state.revealed) {
                                Text(
                                    // The streak encouragement is the headline; a
                                    // gentle amber streak-broken line stands in for
                                    // "Not quite" (practice is low-stakes).
                                    text = state.streakMessage ?: if (allOk) "Correct!" else "Not quite",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (allOk) {
                                        correctColor(LocalKanaColors.current.isDark)
                                    } else {
                                        practiceMissColor(LocalKanaColors.current.isDark)
                                    },
                                )
                                // Reading · audio · meaning on ONE line; wraps only if
                                // it truly can't fit (responsive on narrow screens).
                                // When meaning was tested, each part carries a ✓/✗ so
                                // the learner sees which step they missed.
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        8.dp,
                                        Alignment.CenterHorizontally,
                                    ),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    // Romaji + speaker are ONE unbreakable child:
                                    // FlowRow measures each child against the
                                    // REMAINING line width, and a squeezed Text
                                    // wraps internally every couple of characters
                                    // (the mangled reveal seen on device) instead
                                    // of moving to the next line whole.
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (needMeaning) ResultMark(readingOk == true)
                                        Text(
                                            // A lone mark reveals its role, not
                                            // its placeholder romaji.
                                            text = SpecialKana.readingLabel(card.glyph)
                                                ?: card.romaji,
                                            style = MaterialTheme.typography.headlineSmall,
                                        )
                                        PronunciationButton(
                                            glyph = card.glyph,
                                            status = ttsStatus,
                                            onSpeak = onSpeak,
                                        )
                                    }
                                    card.meaning?.let { meaning ->
                                        // One unbreakable child so FlowRow moves
                                        // the whole meaning to the next line if it
                                        // doesn't fit (rather than wrapping it
                                        // mid-word); content centered so it sits
                                        // under the middle of the card, not
                                        // left-shifted by any reserved width.
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(
                                                4.dp,
                                                Alignment.CenterHorizontally,
                                            ),
                                        ) {
                                            if (needMeaning) ResultMark(meaningOk == true)
                                            Text(
                                                text = if (needMeaning) meaning else "·  $meaning",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                                // Both badges share ONE row so the reveal stays 3
                                // rows (see RevealReserve). The divergence warning
                                // (only さ/き/り) keeps its text; the mnemonic chip
                                // goes icon-only when the divergence badge is also
                                // present so the pair fits 360dp — the same rule
                                // the practice footer uses. Each badge renders
                                // nothing when it has no content, so this Row is
                                // usually just the mnemonic chip (or empty).
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PrintDivergenceBadge(
                                        unitCharacters = cardUnits,
                                        label = "Looks different in handwriting",
                                    )
                                    SpecialKanaBadge(
                                        unitCharacters = cardUnits,
                                        iconOnly = hasDivergence,
                                    )
                                    KanjiBadge(
                                        kanji = card.kanji.takeIf { kanjiHintsEnabled },
                                        reading = card.romaji,
                                        meaning = card.meaning.orEmpty(),
                                        kanjiUsage = card.kanjiUsage,
                                        otherForms = card.otherKanjiForms,
                                        compact = hasDivergence || hasSpecial,
                                    )
                                    if (mnemonicsEnabled) {
                                        MnemonicBadge(
                                            unitCharacters = cardUnits,
                                            iconOnly = hasDivergence || hasSpecial || hasKanji,
                                            mnemonicChoices = mnemonicChoices,
                                            onChoose = onChooseMnemonic,
                                            customs = customMnemonics,
                                        )
                                    }
                                }
                            } else if (inMeaningPhase) {
                                // Reading answered — confirm it, then ask the meaning.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    ResultMark(readingOk == true)
                                    Text(
                                        text = "Reading: ${card.romaji}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "What does it mean?",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            } else {
                                // Pre-answer: the reserved result region is
                                // otherwise blank, so the Hint + Kanji buttons
                                // sit here and are replaced by the result on
                                // reveal — nothing moves. Both are opt-in taps
                                // (each renders nothing when unavailable): the
                                // Hint keyword nudges the sound; the "Kanji"
                                // button opens the reference glyph for the word.
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (mnemonicsEnabled) {
                                        MnemonicHintButton(
                                            unitCharacters = cardUnits,
                                            showGlyph = true,
                                            mnemonicChoices = mnemonicChoices,
                                            onChoose = onChooseMnemonic,
                                            customs = customMnemonics,
                                        )
                                    }
                                    KanjiBadge(
                                        kanji = card.kanji.takeIf { kanjiHintsEnabled },
                                        reading = card.romaji,
                                        meaning = card.meaning.orEmpty(),
                                        kanjiUsage = card.kanjiUsage,
                                        otherForms = card.otherKanjiForms,
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }

            // Bottom section: the answer input for the current step (reading,
            // then meaning if enabled).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (effectiveTyping) {
                    // Field + action share ONE row (~2/3 field, 1/3 button) — a
                    // guess is short, so the field needn't span the row. The
                    // keyboard stays open the WHOLE run: the field is never
                    // read-only (which dismisses the IME and caused the
                    // open/close churn), so after each Check it just stops
                    // accepting input while the bottom-right key advances the
                    // step (Check reading → Check meaning → Next).
                    val fieldValue = if (inMeaningPhase) meaningTyped else typed
                    fun submitTyped() {
                        when {
                            state.revealed -> onNext()
                            inMeaningPhase ->
                                if (meaningTyped.isNotBlank()) {
                                    answerMeaning(MeaningMatch.matches(meaningTyped, card.meaning.orEmpty()))
                                }
                            readingOk == null ->
                                if (typed.isNotBlank()) answerReading(RomajiMatch.matches(typed, card.romaji))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = fieldValue,
                            // Locked after reveal: keystrokes are ignored so the
                            // answer can't change, but the field keeps focus so
                            // the keyboard stays up (no read-only dismissal).
                            onValueChange = {
                                if (!state.revealed) {
                                    if (inMeaningPhase) meaningTyped = it else typed = it
                                }
                            },
                            modifier = Modifier
                                .weight(2f)
                                .focusRequester(focusRequester),
                            label = { Text(if (inMeaningPhase) "Type the meaning" else "Type the romaji") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitTyped() }),
                        )
                        Button(
                            onClick = { submitTyped() },
                            enabled = state.revealed || fieldValue.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) {
                            Text(if (state.revealed) "Next" else "Check", maxLines = 1)
                        }
                    }
                } else {
                    // Multiple choice: tap the reading, then (if testing meaning)
                    // tap the meaning, then Next advances.
                    // Every slot is a FIXED height so this bottom section is the
                    // same height whether it shows the answer options or just
                    // Next — which keeps the CARD above a constant height, so the
                    // glyph never resizes/jumps on reveal (the card's fixed
                    // glyph/reveal split depends on this constant height). The
                    // reveal state reserves the spent option rows as BLANK spacers
                    // (not disabled pills — those read as broken buttons; the
                    // learner just sees clean space below Next).
                    //
                    // ⚠️ Do NOT collapse the reveal state to Next-only: that
                    // shrinks the bottom, grows the card, and makes the glyph jump.
                    when {
                        state.revealed -> {
                            val slots = if (needMeaning) meaningOptions else readingOptions
                            Button(
                                onClick = onNext,
                                modifier = Modifier.fillMaxWidth().height(OptionSlotHeight),
                            ) {
                                Text("Next")
                            }
                            repeat((slots.size - 1).coerceAtLeast(0)) {
                                Spacer(modifier = Modifier.fillMaxWidth().height(OptionSlotHeight))
                            }
                        }
                        inMeaningPhase ->
                            meaningOptions.forEach { option ->
                                OutlinedButton(
                                    onClick = { answerMeaning(option == card.meaning) },
                                    modifier = Modifier.fillMaxWidth().height(OptionSlotHeight),
                                ) {
                                    Text(option)
                                }
                            }
                        else ->
                            readingOptions.forEach { option ->
                                OutlinedButton(
                                    onClick = { answerReading(option == readingAnswer) },
                                    modifier = Modifier.fillMaxWidth().height(OptionSlotHeight),
                                ) {
                                    Text(option)
                                }
                            }
                    }
                }
            }
        }
    }
}

/**
 * The card's printed glyph, drawn with the native text renderer and centered on
 * its measured INK bounds. A Compose [Text] centers the font's LINE box
 * instead, and CJK line metrics sink the kana ink well below that box's
 * center — on device the card showed far more empty space above the character
 * than below it. Here the ink is fitted to the given box (width for longer
 * words, leftover height, and a size cap), the canvas wraps the ink exactly,
 * and the draw centers on the ink's own bounds — the same metric-driven
 * centering as the tracing ghost (drawGhostCharacter). The print-forms
 * typeface keeps さ/き/り in their connected printed forms; every other glyph
 * falls back to the system font at render time, exactly like [PrintFontFamily].
 * Deliberately fontScale-independent, matching the previous toNonScalingSp
 * sizing (the glyph is bounded by geometry, not text preferences).
 */
@Composable
internal fun FlashcardGlyph(
    glyph: String,
    boxWidth: Dp,
    boxHeight: Dp,
) {
    val context = LocalContext.current
    val inkColor = LocalContentColor.current
    val density = LocalDensity.current
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = ResourcesCompat.getFont(context, R.font.print_forms)
        }
    }
    // Probe the ink at a reference size, scale to fit the box (92% of each
    // side) and the cap, then re-measure at the final size so the centering
    // offsets are exact. Leaves paint.textSize set for the draw below.
    val bounds = remember(glyph, boxWidth, boxHeight) {
        val probe = 100f
        paint.textSize = probe
        val b = android.graphics.Rect()
        paint.getTextBounds(glyph, 0, glyph.length, b)
        if (!b.isEmpty) {
            with(density) {
                val scale = minOf(
                    boxWidth.toPx() * 0.92f / b.width(),
                    boxHeight.toPx() * 0.92f / b.height(),
                    120.dp.toPx() / probe,
                )
                paint.textSize = (probe * scale).coerceAtLeast(24.dp.toPx())
            }
            paint.getTextBounds(glyph, 0, glyph.length, b)
        }
        b
    }
    if (bounds.isEmpty) return
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { bounds.height().toDp() }),
    ) {
        paint.color = inkColor.toArgb()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                glyph,
                size.width / 2f - bounds.exactCenterX(),
                size.height / 2f - bounds.exactCenterY(),
                paint,
            )
        }
    }
}

/** A small ✓/✗ marking one answered step right or wrong on the reveal. */
@Composable
private fun ResultMark(ok: Boolean) {
    Text(
        text = if (ok) "✓" else "✗",
        style = MaterialTheme.typography.titleMedium,
        color = if (ok) {
            correctColor(LocalKanaColors.current.isDark)
        } else {
            practiceMissColor(LocalKanaColors.current.isDark)
        },
    )
}

/**
 * Multiple-choice options for a flashcard reading: the correct reading plus up to
 * three distinct distractors drawn from the deck, in a fixed (seeded) order per
 * card so the layout doesn't reshuffle on recomposition. Mirrors the reading
 * quiz's option builder.
 */
private fun readingChoiceOptions(correct: String, pool: List<String>, seed: Int): List<String> {
    val distractors = pool.filter { it != correct }.distinct().shuffled(Random(seed.toLong())).take(3)
    return (distractors + correct).shuffled(Random(seed.toLong() + 1))
}
