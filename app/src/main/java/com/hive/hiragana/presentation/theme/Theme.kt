package com.hive.hiragana.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.hive.hiragana.domain.model.AppearanceMode

private val LightColors = lightColorScheme(
    primary = Vermilion,
    onPrimary = PaperWhite,
    primaryContainer = VermilionContainer,
    onPrimaryContainer = OnVermilionContainer,
    secondary = IndigoSecondary,
    background = WashiBackground,
    surface = WashiBackground,
)

// The "sumi / night-washi" dark scheme: the vermilion brand at M3 dark tones
// over warm brown-grey darks (never pure black). The surfaceContainer* roles
// are set to warm steps too, so sheets/dialogs/menus (which default to the
// baseline purple-tinted dark neutrals) stay on-palette.
private val DarkColors = darkColorScheme(
    primary = VermilionGlow,
    onPrimary = OnVermilionGlow,
    primaryContainer = VermilionContainerDark,
    onPrimaryContainer = VermilionContainer, // 0xFFFFDAD4 — M3 symmetry with light
    secondary = IndigoSecondaryDark,
    onSecondary = OnIndigoSecondaryDark,
    secondaryContainer = IndigoContainerDark,
    onSecondaryContainer = OnIndigoContainerDark,
    background = SumiBackground,
    surface = SumiBackground,
    onBackground = SumiOnBackground,
    onSurface = SumiOnBackground,
    surfaceVariant = SumiSurfaceVariant,
    onSurfaceVariant = SumiOnSurfaceVariant,
    outline = SumiOutline,
    surfaceContainerLowest = Color(0xFF100D09),
    surfaceContainerLow = Color(0xFF1D1913),
    surfaceContainer = NightPaper,
    surfaceContainerHigh = Color(0xFF2B2620),
    surfaceContainerHighest = Color(0xFF363028),
)

/**
 * Resolves the learner's [AppearanceMode] to the boolean [HiraganaTheme]
 * consumes: SYSTEM follows the device, LIGHT/DARK force it.
 */
@Composable
fun AppearanceMode.resolvesToDark(): Boolean = when (this) {
    AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
}

/**
 * App-wide Material 3 theme. Light (the default) is the original warm-paper
 * design; dark is a DELIBERATE, app-controlled "sumi / night-washi" scheme
 * added 2026-07-26, driven by the persisted [AppearanceMode] setting (via
 * [darkTheme], resolved in MainActivity with [resolvesToDark]) — NOT by
 * isSystemInDarkTheme() alone, and NOT by platform force-dark:
 * android:forceDarkAllowed stays false in res/values/themes.xml because
 * force-dark's blind inversion produced a broken mixed look (found in the
 * first device run, 2026-07-04), so we control every dark color ourselves,
 * including the canvas ("paper") palette published through [LocalKanaColors]
 * (dark writing sheet, dimmed ghost/grid, chalk default ink).
 */
@Composable
fun HiraganaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKanaColors provides if (darkTheme) DarkKanaColors else LightKanaColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HiraganaTypography,
            content = content,
        )
    }
}
