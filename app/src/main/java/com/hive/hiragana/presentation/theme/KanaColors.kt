package com.hive.hiragana.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The canvas ("paper") palette that sits OUTSIDE the Material color scheme:
 * the writing sheet, the traced ghost, the practice grid, the stroke-order
 * diagram grey, and the theme's DEFAULT ink. These were hardcoded light
 * values (PaperWhite, GhostGrey, …); dark mode gives them a dark twin, so
 * they now flow through [LocalKanaColors], provided by [HiraganaTheme].
 *
 * [LightKanaColors] is EXACTLY the pre-dark-mode values — light mode is
 * pixel-identical to before. [isDark] also drives the score-tint spectrum
 * (see ScoreColors.kt) and which brush-ink preference is active.
 *
 * When adding new canvas/"paper" drawing (anything that isn't ordinary
 * Material chrome), read its colors from here instead of hardcoding a light
 * value, and it stays dark-mode-correct for free.
 */
@Immutable
data class KanaColors(
    val paper: Color,
    val ghost: Color,
    val gridBorder: Color,
    val gridGuide: Color,
    val diagramStroke: Color,
    val defaultInk: Color,
    val isDark: Boolean,
)

val LightKanaColors = KanaColors(
    paper = PaperWhite,
    ghost = GhostGrey,
    gridBorder = GridBorder,
    gridGuide = GridGuide,
    diagramStroke = DiagramStroke,
    defaultInk = InkBlack,
    isDark = false,
)

val DarkKanaColors = KanaColors(
    paper = NightPaper,
    ghost = NightGhost,
    gridBorder = NightGridBorder,
    gridGuide = NightGridGuide,
    diagramStroke = NightDiagramStroke,
    defaultInk = InkChalk,
    isDark = true,
)

/**
 * The active canvas palette. Provided by [HiraganaTheme] (dark or light);
 * defaults to [LightKanaColors] so any composable read outside the theme
 * still renders the original light values.
 */
val LocalKanaColors = staticCompositionLocalOf { LightKanaColors }
