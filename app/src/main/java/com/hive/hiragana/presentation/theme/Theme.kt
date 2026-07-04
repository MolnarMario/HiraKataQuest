package com.hive.hiragana.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Vermilion,
    onPrimary = PaperWhite,
    primaryContainer = VermilionContainer,
    onPrimaryContainer = OnVermilionContainer,
    secondary = IndigoSecondary,
    background = WashiBackground,
    surface = WashiBackground,
)

/**
 * App-wide Material 3 theme — deliberately light-only. The warm-paper
 * design (washi background, hardcoded light status/navigation bars in
 * res/values/themes.xml, PaperWhite tracing sheet) has no dark variant;
 * an earlier half-designed dark scheme keyed off isSystemInDarkTheme()
 * produced a broken mixed look on dark-mode devices (found in the first
 * device run, 2026-07-04) and was removed. The platform layer opts out
 * of force-dark in themes.xml for the same reason.
 */
@Composable
fun HiraganaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = HiraganaTypography,
        content = content,
    )
}
