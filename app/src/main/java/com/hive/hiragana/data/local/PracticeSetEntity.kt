package com.hive.hiragana.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Room row for one saved practice set's header (`practice_sets` table, Room
 * v4; [updatedAt] added in v6). Its members live in [PracticeSetItemEntity]
 * rows keyed by [id]; the two are read together via [PracticeSetWithItems].
 * [type] stores `PracticeSetType.name`, [script] stores `Script.key`.
 * [updatedAt] is bumped on rename/member edits (creation sets it to
 * [createdAt]); the `defaultValue` matches MIGRATION_5_6's ALTER TABLE default
 * so Room's schema validation passes on migrated databases.
 */
@Entity(tableName = "practice_sets")
data class PracticeSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val script: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)

/**
 * Room row for one member of a practice set (`practice_set_items` table). Deleted
 * with its parent set via [ForeignKey.CASCADE]. [value] is the member key (glyph
 * for character sets, kana for word sets); [position] preserves selection order.
 */
@Entity(
    tableName = "practice_set_items",
    foreignKeys = [
        ForeignKey(
            entity = PracticeSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("setId")],
)
data class PracticeSetItemEntity(
    @PrimaryKey(autoGenerate = true) val itemId: Long = 0L,
    val setId: Long,
    val value: String,
    val position: Int,
)

/** A practice set with its member rows, assembled by Room's [Relation]. */
data class PracticeSetWithItems(
    @Embedded val set: PracticeSetEntity,
    @Relation(parentColumn = "id", entityColumn = "setId")
    val items: List<PracticeSetItemEntity>,
)
