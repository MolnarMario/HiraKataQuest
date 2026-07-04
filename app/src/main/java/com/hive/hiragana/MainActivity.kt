package com.hive.hiragana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        setContent {
            HiraganaTheme {
                PracticeScreen(
                    viewModel = viewModel(factory = PracticeViewModel.Factory),
                )
            }
        }
    }
}
