package com.hive.hiragana.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room setup for the app. v2 (Phase 3, docs/PHASE3-DESIGN.md §5) adds the
 * spaced-repetition and vocabulary tables alongside the renamed practice
 * history table; [MIGRATION_1_2] upgrades a v1 database in place. v3
 * (handwriting quality scoring, docs/PHASE4-DESIGN.md) adds three nullable
 * score columns to practice_history; [MIGRATION_2_3] upgrades in place.
 */
@Database(
    entities = [
        PracticeAttemptEntity::class,
        ReviewCardEntity::class,
        CharacterProgressEntity::class,
        VocabularyProgressEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class HiraganaDatabase : RoomDatabase() {

    abstract fun practiceAttemptDao(): PracticeAttemptDao
    abstract fun reviewCardDao(): ReviewCardDao
    abstract fun characterProgressDao(): CharacterProgressDao
    abstract fun vocabularyProgressDao(): VocabularyProgressDao

    companion object {
        /**
         * v1 -> v2: renames `practice_attempts` to `practice_history` and
         * adds its `source` column, then creates the three Phase 3 tables.
         * Additive + one rename, no destructive fallback (docs/PHASE3-DESIGN.md §5).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE practice_attempts RENAME TO practice_history")
                db.execSQL(
                    "ALTER TABLE practice_history ADD COLUMN source TEXT NOT NULL DEFAULT 'FREE'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `review_cards` (
                        `characterId` TEXT NOT NULL,
                        `repetitions` INTEGER NOT NULL,
                        `intervalDays` INTEGER NOT NULL,
                        `easeFactor` REAL NOT NULL,
                        `nextReviewDate` INTEGER NOT NULL,
                        PRIMARY KEY(`characterId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `character_progress` (
                        `characterId` TEXT NOT NULL,
                        `introducedAt` INTEGER NOT NULL,
                        `totalTraces` INTEGER NOT NULL,
                        PRIMARY KEY(`characterId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vocabulary_progress` (
                        `kana` TEXT NOT NULL,
                        `unlockedAt` INTEGER NOT NULL,
                        `timesPracticed` INTEGER NOT NULL,
                        `lastPracticedAt` INTEGER,
                        PRIMARY KEY(`kana`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v2 -> v3: adds three nullable REAL score columns to
         * practice_history (handwriting quality scoring, docs/PHASE4-DESIGN.md).
         * Purely additive; nullable so all existing rows read back as
         * "unscored" with no default needed.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE practice_history ADD COLUMN form_score REAL")
                db.execSQL("ALTER TABLE practice_history ADD COLUMN fit_score REAL")
                db.execSQL("ALTER TABLE practice_history ADD COLUMN recognition_score REAL")
            }
        }

        fun build(context: Context): HiraganaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HiraganaDatabase::class.java,
                "hiragana-practice.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
