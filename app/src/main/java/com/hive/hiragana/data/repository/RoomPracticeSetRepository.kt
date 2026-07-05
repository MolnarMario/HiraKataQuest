package com.hive.hiragana.data.repository

import com.hive.hiragana.data.local.PracticeSetDao
import com.hive.hiragana.data.local.PracticeSetEntity
import com.hive.hiragana.data.local.PracticeSetWithItems
import com.hive.hiragana.domain.model.PracticeSet
import com.hive.hiragana.domain.model.PracticeSetType
import com.hive.hiragana.domain.model.Script
import com.hive.hiragana.domain.repository.PracticeSetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [PracticeSetRepository] backed by Room ([PracticeSetDao]). Maps between the
 * two-table entity shape and the flat domain [PracticeSet] (members are the
 * ordered item values). `createdAt` is stamped with wall-clock time — sets are
 * plain user data (ordering only), not SRS state, so they don't ride the
 * injected scheduler clock.
 */
class RoomPracticeSetRepository(
    private val dao: PracticeSetDao,
) : PracticeSetRepository {

    override fun observeSets(): Flow<List<PracticeSet>> =
        dao.observeSetsWithItems().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createSet(
        name: String,
        type: PracticeSetType,
        script: Script,
        members: List<String>,
    ): Long = dao.createSet(
        set = PracticeSetEntity(
            name = name,
            type = type.name,
            script = script.key,
            createdAt = System.currentTimeMillis(),
        ),
        values = members,
    )

    override suspend fun renameSet(id: Long, name: String) = dao.renameSet(id, name)

    override suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    override suspend fun updateMembers(id: Long, members: List<String>) =
        dao.replaceItems(id, members)
}

private fun PracticeSetWithItems.toDomain(): PracticeSet = PracticeSet(
    id = set.id,
    name = set.name,
    type = PracticeSetType.fromKey(set.type),
    script = Script.fromKey(set.script),
    members = items.sortedBy { it.position }.map { it.value },
)
