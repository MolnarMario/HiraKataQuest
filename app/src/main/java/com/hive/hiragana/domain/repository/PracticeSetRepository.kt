package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.model.PracticeSet
import com.hive.hiragana.domain.model.PracticeSetType
import com.hive.hiragana.domain.model.Script
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for user-curated [PracticeSet]s. Backed by Room (structured
 * user data, same pattern as review cards / vocabulary progress) — sets are
 * an explicit membership list, unlike the scalar Study Scope opt-ins that
 * live in SharedPreferences.
 */
interface PracticeSetRepository {

    /** All saved sets, newest first, kept live as they change. */
    fun observeSets(): Flow<List<PracticeSet>>

    /** Creates a new set and returns its generated id. */
    suspend fun createSet(
        name: String,
        type: PracticeSetType,
        script: Script,
        members: List<String>,
    ): Long

    suspend fun renameSet(id: Long, name: String)

    suspend fun deleteSet(id: Long)

    /** Replaces a set's members wholesale (add/remove while editing). */
    suspend fun updateMembers(id: Long, members: List<String>)
}
