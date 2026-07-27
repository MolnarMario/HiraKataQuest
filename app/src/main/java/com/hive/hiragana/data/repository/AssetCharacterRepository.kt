package com.hive.hiragana.data.repository

import com.hive.hiragana.data.assets.HiraganaAssetDataSource
import com.hive.hiragana.domain.model.HiraganaCharacter
import com.hive.hiragana.domain.repository.CharacterRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [CharacterRepository] backed by bundled asset JSON. Loads once, then
 * serves from an in-memory cache, sorted by [HiraganaCharacter.frequency]
 * ascending — study order is the frequency rank, not `index.json`'s file
 * listing order (today the two coincide, but the ordering source is now
 * explicitly the rank, per docs/PHASE3-DESIGN.md §1).
 */
class AssetCharacterRepository(
    private val dataSource: HiraganaAssetDataSource,
) : CharacterRepository {

    private val mutex = Mutex()
    private var cache: List<HiraganaCharacter>? = null

    override suspend fun getCharacters(): List<HiraganaCharacter> = mutex.withLock {
        // Only cache a non-empty load: an empty result means every asset
        // directory failed to read, and caching it would poison the cache so
        // the error screen's Retry (which re-reads through here) could never
        // recover until process death.
        cache ?: dataSource.loadCharacters().sortedBy { it.frequency }
            .also { if (it.isNotEmpty()) cache = it }
    }
}
