package com.hive.hiragana

import android.app.Application
import android.content.Context
import com.hive.hiragana.data.assets.HiraganaAssetDataSource
import com.hive.hiragana.data.local.HiraganaDatabase
import com.hive.hiragana.data.recognition.MlKitHandwritingRecognizer
import com.hive.hiragana.data.repository.AssetCharacterRepository
import com.hive.hiragana.data.repository.PrefsSettingsRepository
import com.hive.hiragana.data.repository.RoomLearningRepository
import com.hive.hiragana.data.repository.RoomPracticeRepository
import com.hive.hiragana.data.repository.RoomPracticeSetRepository
import com.hive.hiragana.data.repository.RoomVocabularyRepository
import com.hive.hiragana.domain.repository.CharacterRepository
import com.hive.hiragana.domain.repository.HandwritingRecognizer
import com.hive.hiragana.domain.repository.LearningRepository
import com.hive.hiragana.domain.repository.PracticeRepository
import com.hive.hiragana.domain.repository.PracticeSetRepository
import com.hive.hiragana.domain.repository.SettingsRepository
import com.hive.hiragana.domain.repository.VocabularyRepository

/**
 * Application entry point. Creates the [AppContainer] in [onCreate] and
 * exposes it so consumers (see PracticeViewModel.Factory) can pull their
 * dependencies from it.
 */
class HiraganaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Warm the SharedPreferences disk load off the main thread so the
        // ViewModel's synchronous first read (~10 prefs during construction)
        // is served from memory instead of forcing a blocking disk load.
        Thread { container.settingsRepository.isGridEnabled() }
            .apply { isDaemon = true; name = "prefs-warmup" }
            .start()
    }
}

/**
 * Hand-rolled dependency container — deliberately simple for the MVP;
 * swap for Hilt if the object graph grows.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: HiraganaDatabase by lazy {
        HiraganaDatabase.build(appContext)
    }

    private val assetDataSource: HiraganaAssetDataSource by lazy {
        HiraganaAssetDataSource(appContext.assets)
    }

    val characterRepository: CharacterRepository by lazy {
        AssetCharacterRepository(assetDataSource)
    }

    val practiceRepository: PracticeRepository by lazy {
        RoomPracticeRepository(database.practiceAttemptDao())
    }

    val learningRepository: LearningRepository by lazy {
        RoomLearningRepository(database.reviewCardDao(), database.characterProgressDao())
    }

    val vocabularyRepository: VocabularyRepository by lazy {
        RoomVocabularyRepository(assetDataSource, database.vocabularyProgressDao())
    }

    val practiceSetRepository: PracticeSetRepository by lazy {
        RoomPracticeSetRepository(database.practiceSetDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        PrefsSettingsRepository(appContext)
    }

    val handwritingRecognizer: HandwritingRecognizer by lazy {
        MlKitHandwritingRecognizer()
    }
}
