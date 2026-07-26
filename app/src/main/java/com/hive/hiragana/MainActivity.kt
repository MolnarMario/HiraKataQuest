package com.hive.hiragana

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hive.hiragana.domain.model.AppearanceMode
import com.hive.hiragana.presentation.screens.PracticeScreen
import com.hive.hiragana.presentation.theme.HiraganaTheme
import com.hive.hiragana.presentation.theme.resolvesToDark
import com.hive.hiragana.presentation.viewmodels.PracticeViewModel

// Copies of androidx.activity.EdgeToEdge's private default nav-bar scrims,
// so our explicit enableEdgeToEdge calls reproduce the no-arg default's
// behavior exactly (three-button nav contrast on API 26-28).
private val LightNavScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * The app's only activity: resolves the persisted appearance to light/dark,
 * applies [HiraganaTheme], keeps the system-bar icon contrast in sync, and
 * hosts [PracticeScreen]. Locked to portrait in the manifest.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Resolve the saved appearance BEFORE the first frame: SharedPreferences
        // is synchronous (warmed by HiraganaApplication) and the ViewModel reads
        // it synchronously at construction anyway.
        val appearance =
            (application as HiraganaApplication).container.settingsRepository.getAppearance()
        val startsDark = when (appearance) {
            AppearanceMode.DARK -> true
            AppearanceMode.LIGHT -> false
            AppearanceMode.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        // Draw behind the system bars (the background extends under them);
        // PracticeScreen pads its content by WindowInsets.safeDrawing. Also
        // future-proofs targetSdk 35, which forces edge-to-edge. Icon polarity
        // follows the APP's theme, not the OS's — the old no-arg call keyed on
        // system dark and set light icons over the washi background on
        // system-dark devices (latent bug, fixed here). Re-applied reactively
        // below when the appearance changes.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT, Color.TRANSPARENT,
            ) { startsDark },
            navigationBarStyle = SystemBarStyle.auto(
                LightNavScrim, DarkNavScrim,
            ) { startsDark },
        )
        if (startsDark) {
            // Kill the white cold-start flash: the platform theme's window
            // background is light. Raw ARGB of presentation.theme.SumiBackground
            // (no Compose dep here).
            window.setBackgroundDrawable(ColorDrawable(0xFF16130E.toInt()))
        }
        setContent {
            val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.Factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = uiState.appearance.resolvesToDark()
            // Keep bar-icon contrast in step with live theme changes (the
            // documented androidx.activity pattern for dynamic themes).
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT, Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LightNavScrim, DarkNavScrim,
                    ) { darkTheme },
                )
                onDispose {}
            }
            HiraganaTheme(darkTheme = darkTheme) {
                PracticeScreen(viewModel = viewModel)
            }
        }
    }
}
