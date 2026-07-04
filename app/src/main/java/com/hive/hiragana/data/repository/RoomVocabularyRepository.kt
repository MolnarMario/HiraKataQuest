package com.hive.hiragana.data.repository

import android.util.Log
import com.hive.hiragana.data.assets.HiraganaAssetDataSource
import com.hive.hiragana.data.local.VocabularyProgressDao
import com.hive.hiragana.data.local.VocabularyProgressEntity
import com.hive.hiragana.domain.model.VocabularyProgress
import com.hive.hiragana.domain.model.VocabularyWord
import com.hive.hiragana.domain.repository.VocabularyRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [VocabularyRepository] backed by assets/hiragana/vocabulary.json (words)
 * and Room (unlock/practice state). A malformed vocabulary asset must not
 * crash the app: [getWords] catches parse failures, logs them (same
 * `Log.w(TAG)` pattern as HiraganaAssetDataSource's animation parsing), and
 * returns an empty list instead — unlike RoomLearningRepository, failures
 * are swallowed here because they originate from bundled asset content, not
 * a caller-actionable I/O op (docs/PHASE3-DESIGN.md §5).
 */
class RoomVocabularyRepository(
    private val dataSource: HiraganaAssetDataSource,
    private val vocabularyProgressDao: VocabularyProgressDao,
) : VocabularyRepository {

    private val wordsMutex = Mutex()
    private var cachedWords: List<VocabularyWord>? = null

    /**
     * Words are parsed once and cached for the process lifetime (same
     * pattern as AssetCharacterRepository) — bundled assets cannot change
     * under a running app, and that includes caching the empty-list
     * fallback for a malformed file rather than re-parsing and re-logging
     * on every call.
     */
    override suspend fun getWords(): List<VocabularyWord> = wordsMutex.withLock {
        cachedWords ?: runCatching { dataSource.loadVocabulary() }
            .getOrElse { error ->
                Log.w(TAG, "Malformed vocabulary.json; no vocabulary words available", error)
                emptyList()
            }
            .also { cachedWords = it }
    }

    override suspend fun getProgress(): List<VocabularyProgress> =
        vocabularyProgressDao.getAll().map { it.toDomain() }

    override suspend fun unlock(kana: String, atMillis: Long) {
        vocabularyProgressDao.insert(
            VocabularyProgressEntity(kana = kana, unlockedAt = atMillis, timesPracticed = 0, lastPracticedAt = null)
        )
    }

    override suspend fun recordPractice(kana: String, atMillis: Long) {
        vocabularyProgressDao.recordPractice(kana, atMillis)
    }

    private companion object {
        const val TAG = "HiraganaAssets"
    }
}

private fun VocabularyProgressEntity.toDomain() = VocabularyProgress(
    kana = kana,
    unlockedAt = unlockedAt,
    timesPracticed = timesPracticed,
    lastPracticedAt = lastPracticedAt,
)
