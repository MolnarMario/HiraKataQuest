package com.hive.hiragana.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.AppMode
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
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Scrollable but still centered: the column keeps at least the full
        // viewport height (so Center works as before when everything fits),
        // and on short screens / large font scales it scrolls instead of
        // clipping the cards. Plain Center + verticalScroll would clip the
        // TOP of overflowing content with no way to reach it.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val minHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = 28.dp, vertical = 40.dp),
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
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                KanaOverviewButton(modifier = Modifier.padding(bottom = 24.dp))

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
