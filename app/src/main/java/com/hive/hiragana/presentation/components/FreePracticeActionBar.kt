package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val CompactPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

/**
 * Free-practice action row (10% of the screen) — a two-step flow mirroring
 * the quiz: write → **Submit** (scores + shows the result, keeps the ink) →
 * **Again** (rewrite the SAME character) / **Next** (advance to the next).
 * Before submitting, an empty canvas offers **Skip** for the character deck.
 *
 * @param submitted false = writing sub-state (Undo · Clear · Submit/Skip),
 * true = reviewing the just-scored attempt (Again · Next).
 * @param canUndo true when a committed stroke exists to revert — the Undo
 * button (shown while writing, next to Clear) removes just the latest stroke
 * so one slip doesn't cost the rest of the character.
 * @param canAdvance true for the character deck (Next/Skip cycle through it);
 * false for single-word practice, which repeats one sheet and has no Next.
 * @param repetitionMode true for any multi-copy 2×2/3×3 sheet — character OR
 * word. Scoring is a 1×1-only feature (one guide cell per glyph), so a
 * repetition worksheet can't be meaningfully scored and the two-step collapses
 * to **Clear · Next** (no Submit, no score).
 * @param scoringEnabled false when the learner turned scoring **Off** (Study
 * Scope → "Score my writing" = Off): there is no score to earn, so the
 * two-step likewise collapses to **Clear · Next** — no Submit, no result.
 */
@Composable
fun FreePracticeActionBar(
    hasInk: Boolean,
    submitted: Boolean,
    canAdvance: Boolean,
    repetitionMode: Boolean,
    scoringEnabled: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (repetitionMode || !scoringEnabled) {
            // No single-character score to give — either a multi-copy
            // worksheet, or scoring turned Off in Study Scope. Skip the
            // Submit/Again step: just Undo/Clear and move on. Next exists only
            // for the character deck; single-word practice has nothing to
            // advance to, so Undo · Clear fill the row.
            UndoButton(canUndo = canUndo, onUndo = onUndo)
            OutlinedButton(
                onClick = onClear,
                enabled = hasInk,
                modifier = Modifier.weight(1f),
                contentPadding = CompactPadding,
            ) {
                BarLabel("Clear")
            }
            if (canAdvance) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(2f),
                    contentPadding = CompactPadding,
                ) {
                    BarLabel("Next")
                }
            }
        } else if (!submitted) {
            UndoButton(canUndo = canUndo, onUndo = onUndo)
            OutlinedButton(
                onClick = onClear,
                enabled = hasInk,
                modifier = Modifier.weight(1f),
                contentPadding = CompactPadding,
            ) {
                BarLabel("Clear")
            }
            Button(
                onClick = { if (hasInk) onSubmit() else onNext() },
                enabled = hasInk || canAdvance,
                modifier = Modifier.weight(2f),
                contentPadding = CompactPadding,
            ) {
                BarLabel(if (!hasInk && canAdvance) "Skip" else "Submit")
            }
        } else {
            // A single weighted child fills the row, so Again alone (word
            // practice) spans full width; with Next it takes a third.
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                contentPadding = CompactPadding,
            ) {
                BarLabel("Again")
            }
            if (canAdvance) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(2f),
                    contentPadding = CompactPadding,
                ) {
                    BarLabel("Next")
                }
            }
        }
    }
}

/**
 * Reverts just the latest committed stroke — a slip shouldn't cost every
 * other stroke the way Clear does. Present only while writing (both the
 * scored two-step and the repetition worksheet); the reviewing sub-state
 * keeps its Again · Next pair.
 */
@Composable
private fun RowScope.UndoButton(canUndo: Boolean, onUndo: () -> Unit) {
    OutlinedButton(
        onClick = onUndo,
        enabled = canUndo,
        modifier = Modifier.weight(1f),
        contentPadding = CompactPadding,
    ) {
        BarLabel("Undo")
    }
}

// Shrinks to fit its button. TextOverflow.Visible used to let a large-font
// label draw past the button edge ("Undo" and "Clear" touched it at 2.0x).
@Composable
private fun BarLabel(text: String) {
    AutoShrinkText(text = text)
}
