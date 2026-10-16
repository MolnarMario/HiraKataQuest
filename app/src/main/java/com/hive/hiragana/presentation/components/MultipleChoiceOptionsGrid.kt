package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Row height for one line of [MultipleChoiceOptionsGrid]. Callers that need
 * to reserve the same space after the options are gone (a "Next" button plus
 * blank spacer rows) must use this height too, sized via [mcqGridRows] —
 * that keeps the bottom section a constant height across the question and
 * reveal states, so nothing above it jumps.
 *
 * Raised from 64dp (device feedback, 2026-08-22: the pills sat too low for a
 * comfortable one-handed thumb reach) — bigger targets, easier to hit without
 * looking. The reveal Button/Spacer rows above and the extra bottom margin on
 * the answer section (in QuizScreen/ReadingFlashcardScreen) both lean on this
 * same constant, so a screen's glyph area shrinks by a matching amount; it
 * stays well clear of FlashcardGlyph's 120dp size cap on every supported
 * screen (the 360x~700dp compact baseline included).
 */
val MultipleChoiceRowHeight: Dp = 72.dp

private val MultipleChoiceColumnGap = 10.dp
private val MultipleChoiceRowGap = 10.dp

// Half the button's default 24dp side padding: a half-width pill on a 360dp
// phone has ~110dp of text width at the default, too little for a meaning
// like "convenience store". The slimmer vertical padding lets two lines of
// titleMedium fit the fixed row height.
private val MultipleChoicePillPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

/**
 * Multiple-choice answer pills laid out two per row instead of one per row.
 * A single stacked column left each pill the full card width but only
 * [MultipleChoiceRowHeight]-old (52dp) tall, which squeezed the label down to
 * a small centered line (device feedback, 2026-08-22). Two pills per row
 * halves the width but frees up the height each pill can claim, so the label
 * renders larger and reads better. A trailing odd option fills its row alone
 * rather than sitting half-width next to blank space.
 *
 * Half a row is narrow for an English meaning (46 shipped meanings run past
 * 17 characters, e.g. "that over there (before noun)"), and an ellipsized
 * option is an unreadable answer. So the default label wraps onto a second
 * line, and only shrinks when two lines still don't fit the pill.
 */
@Composable
fun MultipleChoiceOptionsGrid(
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    optionContent: @Composable (String) -> Unit = { option ->
        AutoShrinkText(
            text = option,
            style = MaterialTheme.typography.titleMedium,
            // Low on purpose: 9sp still renders ~18dp tall at a 2.0 font
            // scale, and a whole answer beats a truncated one.
            minFontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    },
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MultipleChoiceRowGap)) {
        options.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MultipleChoiceColumnGap),
            ) {
                row.forEach { option ->
                    OutlinedButton(
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f).height(MultipleChoiceRowHeight),
                        contentPadding = MultipleChoicePillPadding,
                    ) {
                        optionContent(option)
                    }
                }
            }
        }
    }
}

/**
 * How many [MultipleChoiceOptionsGrid] rows a given option count fills (two
 * pills per row). Callers use this to size a post-answer spacer stack that
 * matches the grid's height exactly.
 */
fun mcqGridRows(optionCount: Int): Int = (optionCount + 1) / 2
