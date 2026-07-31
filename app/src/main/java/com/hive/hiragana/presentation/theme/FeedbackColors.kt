package com.hive.hiragana.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic answer-feedback colors, each with a light/dark variant selected by
 * `dark` (read `LocalKanaColors.current.isDark` at the call site, the same
 * pattern as [scoreSpectrumColor]).
 *
 * A CORRECT answer is green everywhere (owner decision: the vermilion `primary`
 * read like a mistake). A MISS is a soft amber in low-stakes PRACTICE
 * (flashcards); the graded quiz keeps `colorScheme.error` red. The "How to
 * remember" reveal chip uses a calm teal so help never looks like an error.
 */
fun correctColor(dark: Boolean): Color = if (dark) FeedbackGreenDark else FeedbackGreen

fun practiceMissColor(dark: Boolean): Color = if (dark) FeedbackAmberDark else FeedbackAmber

data class HintChipColors(val container: Color, val content: Color)

fun hintChipColors(dark: Boolean): HintChipColors =
    if (dark) HintChipColors(HintTealContainerDark, OnHintTealContainerDark)
    else HintChipColors(HintTealContainer, OnHintTealContainer)
