package com.hive.hiragana.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.presentation.components.ConceptInfo
import com.hive.hiragana.presentation.components.ConceptInfoIcon
import com.hive.hiragana.presentation.components.KanaOverviewButton

/**
 * The app's home / launcher: pick a track — Reading (recognize the printed
 * forms) or Writing (handwriting practice). Shown whenever
 * [com.hive.hiragana.presentation.viewmodels.PracticeUiState.appMode] is null.
 * Selecting a card calls [onPick]; back from here exits the app.
 */
@Composable
fun OpeningScreen(
    onPick: (AppMode) -> Unit,
    beginnerMode: Boolean = false,
    onSetBeginnerMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The title + cards fill the space above the footer and stay
            // vertically centered. Scrollable but still centered: the column
            // keeps at least the region height (so Center works when everything
            // fits) and on short screens / large fonts it scrolls instead of
            // clipping. Plain Center + verticalScroll would clip the TOP of
            // overflowing content with no way to reach it.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val minHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = minHeight)
                        .padding(horizontal = 28.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "HiraKataQuest",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "What would you like to practice?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )

                    ModeCard(
                        title = "Reading",
                        subtitle = "Recognize the printed kana you meet on signs, apps, and online. " +
                            "See a character or word, recall its reading.",
                        onClick = { onPick(AppMode.READING) },
                    )
                    Column(modifier = Modifier.padding(top = 20.dp)) {
                        ModeCard(
                            title = "Writing",
                            subtitle = "Practice handwriting: trace the stroke-order form and get " +
                                "instant feedback on your shape.",
                            onClick = { onPick(AppMode.WRITING) },
                        )
                    }
                }
            }

            // Beginner mode lives in a small, horizontally-centered footer pinned
            // to the bottom of the screen (not in the flow with the cards).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(top = 4.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The "New to kana?" overview is itself a beginner tooltip, so it
                // only appears when Beginner mode is on.
                if (beginnerMode) {
                    KanaOverviewButton(modifier = Modifier.padding(bottom = 2.dp))
                }
                BeginnerModeRow(
                    beginnerMode = beginnerMode,
                    onSetBeginnerMode = onSetBeginnerMode,
                )
            }
        }
    }
}

/** The plain-language explainer for what Beginner mode does and who it helps. */
private val BeginnerModeInfo = ConceptInfo(
    title = "Beginner mode",
    body = "Beginner mode adds small ⓘ help icons and a \"New to kana?\" guide " +
        "around the app, explaining terms like hiragana, katakana, and voiced or " +
        "combined kana in plain language. Turn it on if you are new to Japanese " +
        "or want those explanations; leave it off for a cleaner screen once the " +
        "terms feel familiar.",
)

/**
 * The compact "Beginner mode" control (leading ⓘ explainer, small label,
 * trailing [Switch]) shown centered in the opening screen's bottom footer — it
 * is the control for the tooltips, not a tooltip itself. Default OFF (kept
 * clean); ON reveals the kana-concept help. The switch is scaled down and
 * tracks the system font scale so it stays proportional to the small label
 * (never larger than a default switch).
 */
@Composable
private fun BeginnerModeRow(
    beginnerMode: Boolean,
    onSetBeginnerMode: (Boolean) -> Unit,
) {
    val switchScale = (0.72f * LocalDensity.current.fontScale).coerceIn(0.5f, 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ConceptInfoIcon(concept = BeginnerModeInfo)
        Text(
            text = "Beginner mode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
        Switch(
            checked = beginnerMode,
            onCheckedChange = onSetBeginnerMode,
            modifier = Modifier.scale(switchScale),
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        // Cap-then-fill: full-width on phones, 420dp on very wide screens
        // (centered by the parent column). Was fillMaxWidth().width(420.dp) —
        // a contradictory pair where the fixed width silently lost out.
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
