package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.model.Grade
import com.hive.hiragana.presentation.theme.GradeAgainContainer
import com.hive.hiragana.presentation.theme.GradeAgainContent
import com.hive.hiragana.presentation.theme.GradeEasyContainer
import com.hive.hiragana.presentation.theme.GradeEasyContent
import com.hive.hiragana.presentation.theme.GradeHardContainer
import com.hive.hiragana.presentation.theme.GradeHardContent
import com.hive.hiragana.presentation.theme.GradeOkayContainer
import com.hive.hiragana.presentation.theme.GradeOkayContent

private val CompactPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

/**
 * Bottom action row (10% of the screen):
 * Clear · Again · Hard · Good · Easy · (Next).
 *
 * Clear and the grade buttons only enable once something is drawn. Next is
 * only rendered when [showNext] is true (the free-practice loop, Phase 3
 * §6) — during session phases the lesson generator decides what comes next,
 * so there is nothing to skip to.
 */
@Composable
fun PracticeActionBar(
    hasInk: Boolean,
    onClear: () -> Unit,
    onGrade: (Grade) -> Unit,
    onNext: () -> Unit,
    showNext: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onClear,
            enabled = hasInk,
            modifier = Modifier.weight(1.1f),
            contentPadding = CompactPadding,
        ) {
            BarLabel("Clear")
        }

        Grade.entries.forEach { grade ->
            val (container, content) = grade.buttonColors()
            FilledTonalButton(
                onClick = { onGrade(grade) },
                enabled = hasInk,
                modifier = Modifier.weight(1f),
                contentPadding = CompactPadding,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = container,
                    contentColor = content,
                ),
            ) {
                BarLabel(grade.label)
            }
        }

        if (showNext) {
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1.1f),
                contentPadding = CompactPadding,
            ) {
                BarLabel("Next")
            }
        }
    }
}

@Composable
private fun BarLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
}

private fun Grade.buttonColors(): Pair<Color, Color> = when (this) {
    Grade.AGAIN -> GradeAgainContainer to GradeAgainContent
    Grade.HARD -> GradeHardContainer to GradeHardContent
    Grade.GOOD -> GradeOkayContainer to GradeOkayContent
    Grade.EASY -> GradeEasyContainer to GradeEasyContent
}
