package com.hive.hiragana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hive.hiragana.presentation.screens.PracticeScreen
import com.hive.hiragana.presentation.theme.HiraganaTheme
import com.hive.hiragana.presentation.viewmodels.PracticeViewModel

/**
 * The app's only activity: applies [HiraganaTheme] and hosts
 * [PracticeScreen]. Locked to portrait in the manifest.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars (the washi background extends under
        // them); PracticeScreen pads its content by WindowInsets.safeDrawing.
        // Also future-proofs targetSdk 35, which forces edge-to-edge.
        enableEdgeToEdge()
        setContent {
            HiraganaTheme {
                PracticeScreen(
                    viewModel = viewModel(factory = PracticeViewModel.Factory),
                )
            }
        }
    }
}
