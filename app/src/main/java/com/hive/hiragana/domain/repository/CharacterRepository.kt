package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.model.HiraganaCharacter

/**
 * Read-only source of the practice deck. The MVP's only implementation is
 * AssetCharacterRepository, which loads bundled asset JSON.
 */
interface CharacterRepository {
    /** Returns the practice characters in study order. */
    suspend fun getCharacters(): List<HiraganaCharacter>
}
