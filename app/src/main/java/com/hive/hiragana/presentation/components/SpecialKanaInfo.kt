package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.model.SpecialKana

/**
 * The "this isn't an ordinary syllable" cue for the special kana in
 * [SpecialKana] — the long-vowel mark ー, the small tsu っ/ッ, the small vowels
 * ぁぃぅぇぉ/ァィゥェォ, the single ん/ン, and the particle を/ヲ. It is a small
 * chip shown next to the reading (the same treatment [PrintDivergenceBadge]
 * gets); its presence is the "take note" signal and tapping it opens a short
 * explanation of how the character behaves.
 *
 * [unitCharacters] are the deck characters for the item's reading units (a
 * single character, or one per unit of a word); characters with nothing
 * special are filtered out, and the chip renders nothing when none qualify, so
 * callers can pass unconditionally — exactly like [PrintDivergenceBadge].
 */
@Composable
fun SpecialKanaBadge(
    unitCharacters: List<HiraganaCharacter>,
    modifier: Modifier = Modifier,
    // Icon-only rendering for tight chrome, so a full-text pill wrapped to a
    // second line is never dropped by a height-constrained parent.
    iconOnly: Boolean = false,
) {
    val entries = remember(unitCharacters) {
        unitCharacters
            .mapNotNull { ch -> SpecialKana.noteFor(ch.character)?.let { ch.character to it } }
            .distinctBy { it.second.title }
    }
    if (entries.isEmpty()) return

    // A single special kana names itself on the chip ("Long-vowel mark"); the
    // rare word that holds more than one falls back to a generic label.
    val label = if (entries.size == 1) entries.first().second.title else "Special kana"

    // Keyed on the affected glyphs so advancing to a different card closes a
    // left-open dialog instead of silently swapping its content.
    var showDialog by rememberSaveable(entries.joinToString("") { it.first }) {
        mutableStateOf(false)
    }

    Surface(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(percent = 50),
        // tertiaryContainer so the chip reads as its own thing beside the
        // mnemonic (primaryContainer) and print-divergence (secondaryContainer)
        // chips it can share a row with.
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        if (iconOnly) {
            Box(
                modifier = Modifier.size(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }

    if (showDialog) {
        SpecialKanaDialog(entries = entries, onDismiss = { showDialog = false })
    }
}

/** A short explanation per special kana — glyph specimen beside its note. */
@Composable
private fun SpecialKanaDialog(
    entries: List<Pair<String, SpecialKana.Note>>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entries.size == 1) entries.first().second.title else "Good to know") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                entries.forEach { (glyph, note) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // When several notes stack, each keeps its own heading;
                        // a lone note already has it in the dialog title.
                        if (entries.size > 1) {
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = glyph,
                                    style = MaterialTheme.typography.displaySmall,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = note.body,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}
