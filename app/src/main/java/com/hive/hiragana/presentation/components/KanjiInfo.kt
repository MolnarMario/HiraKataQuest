package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.KanjiForm
import com.hive.hiragana.domain.model.KanjiUsage
import com.hive.hiragana.presentation.theme.LocalKanaColors
import com.hive.hiragana.presentation.theme.OnUsageKanaContainer
import com.hive.hiragana.presentation.theme.OnUsageKanaContainerDark
import com.hive.hiragana.presentation.theme.OnUsageKanjiContainer
import com.hive.hiragana.presentation.theme.OnUsageKanjiContainerDark
import com.hive.hiragana.presentation.theme.OnUsageMixedContainer
import com.hive.hiragana.presentation.theme.OnUsageMixedContainerDark
import com.hive.hiragana.presentation.theme.UsageKanaContainer
import com.hive.hiragana.presentation.theme.UsageKanaContainerDark
import com.hive.hiragana.presentation.theme.UsageKanjiContainer
import com.hive.hiragana.presentation.theme.UsageKanjiContainerDark
import com.hive.hiragana.presentation.theme.UsageMixedContainer
import com.hive.hiragana.presentation.theme.UsageMixedContainerDark

/**
 * An opt-in "see the kanji" cue for a vocabulary word that has a kanji
 * spelling (e.g. 彼女 for かのじょ): a small **"Kanji"** button that sits
 * wherever the word appears in practice or the quiz; tapping it opens a
 * reference card with the kanji large, the kana reading, the meaning, a
 * colored tag for how often the kanji form is met, and — for homophones — the
 * other kanji that share the reading.
 *
 * The button is **colored by [kanjiUsage]** (green usually-kanji / blue mixed /
 * grey usually-kana) so the learner can tell at a glance whether the word is
 * normally written in kanji or kana without opening the dialog. The dialog then
 * explains the color and shows the actual kanji.
 *
 * Purely informative: HiraKataQuest teaches and scores kana only, so this never
 * gates study. It renders nothing when [kanji] is null (callers pass it
 * unconditionally — the [PrintDivergenceBadge]/[MnemonicBadge] discipline). The
 * "never quizzed" reassurance lives on the Study-scope "Kanji reference" toggle,
 * so the dialog itself stays uncluttered — it adds content only for the special
 * cases (a usage tag, homophone alternates).
 *
 * @param compact tighter padding for cramped chrome (e.g. the writing-quiz reveal).
 */
@Composable
fun KanjiBadge(
    kanji: String?,
    reading: String,
    meaning: String,
    modifier: Modifier = Modifier,
    kanjiUsage: KanjiUsage? = null,
    otherForms: List<KanjiForm> = emptyList(),
    compact: Boolean = false,
) {
    if (kanji.isNullOrBlank()) return

    // Keyed on the kanji so advancing to a different word closes a left-open
    // dialog instead of silently swapping its content.
    var showDialog by rememberSaveable(kanji) { mutableStateOf(false) }

    val dark = LocalKanaColors.current.isDark
    val usageColors = kanjiUsage?.let { usageColors(it, dark) }
    val container = usageColors?.first ?: MaterialTheme.colorScheme.tertiaryContainer
    val content = usageColors?.second ?: MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Text(
            text = "Kanji",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 5.dp else 6.dp,
            ),
        )
    }

    if (showDialog) {
        KanjiDialog(
            kanji = kanji,
            reading = reading,
            meaning = meaning,
            kanjiUsage = kanjiUsage,
            otherForms = otherForms,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun KanjiDialog(
    kanji: String,
    reading: String,
    meaning: String,
    kanjiUsage: KanjiUsage?,
    otherForms: List<KanjiForm>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kanji") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = kanji,
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = reading,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                if (kanjiUsage != null) {
                    UsageTag(kanjiUsage)
                }
                if (otherForms.isNotEmpty()) {
                    Text(
                        text = "Same reading, other kanji",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // Beginner context: homophones sound identical; the kanji or
                    // the sentence tells them apart. Kept to one gentle line.
                    Text(
                        text = "In Japanese some words sound the same but use a " +
                            "different kanji and meaning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    otherForms.forEach { form -> OtherFormRow(form) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

/** One homophone alternate: its kanji in a usage-colored pill + its meaning. */
@Composable
private fun OtherFormRow(form: KanjiForm) {
    val dark = LocalKanaColors.current.isDark
    val (container, content) = form.usage?.let { usageColors(it, dark) }
        ?: (MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 30),
            color = container,
            contentColor = content,
        ) {
            // ~2x the label size: small kanji are unreadable, and the alternate
            // is the point of this row.
            Text(
                text = form.kanji,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        Text(
            text = form.meaning,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Colored likelihood pill: how often the kanji form is met in real life. */
@Composable
private fun UsageTag(usage: KanjiUsage) {
    val (container, content) = usageColors(usage, LocalKanaColors.current.isDark)
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = content,
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text(text = usage.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** container/on-container colors for a usage tier, per theme. */
private fun usageColors(usage: KanjiUsage, dark: Boolean): Pair<Color, Color> = when (usage) {
    KanjiUsage.USUALLY_KANJI ->
        if (dark) UsageKanjiContainerDark to OnUsageKanjiContainerDark
        else UsageKanjiContainer to OnUsageKanjiContainer
    KanjiUsage.MIXED ->
        if (dark) UsageMixedContainerDark to OnUsageMixedContainerDark
        else UsageMixedContainer to OnUsageMixedContainer
    KanjiUsage.USUALLY_KANA ->
        if (dark) UsageKanaContainerDark to OnUsageKanaContainerDark
        else UsageKanaContainer to OnUsageKanaContainer
}
