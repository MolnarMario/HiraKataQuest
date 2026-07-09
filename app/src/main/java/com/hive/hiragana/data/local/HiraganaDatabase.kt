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
 * score columns to practice_history; [MIGRATION_2_3] upgrades in place. v4
 * adds the two practice-set tables (user-curated lists); [MIGRATION_3_4]
 * upgrades in place, purely additively.
 */
@Database(
    entities = [
        PracticeAttemptEntity::class,
        ReviewCardEntity::class,
        CharacterProgressEntity::class,
        VocabularyProgressEntity::class,
        PracticeSetEntity::class,
        PracticeSetItemEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class HiraganaDatabase : RoomDatabase() {

    abstract fun practiceAttemptDao(): PracticeAttemptDao
    abstract fun reviewCardDao(): ReviewCardDao
    abstract fun characterProgressDao(): CharacterProgressDao
    abstract fun vocabularyProgressDao(): VocabularyProgressDao
    abstract fun practiceSetDao(): PracticeSetDao
    abstract fun readingProgressDao(): ReadingProgressDao

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

        /**
         * v3 -> v4: adds the two practice-set tables (user-curated character/
         * word lists — the practice-sets feature). Purely additive; existing
         * data is untouched.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `practice_sets` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `script` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `practice_set_items` (
                        `itemId` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `setId` INTEGER NOT NULL,
                        `value` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        FOREIGN KEY(`setId`) REFERENCES `practice_sets`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_practice_set_items_setId` " +
                        "ON `practice_set_items` (`setId`)"
                )
            }
        }

        /**
         * v4 -> v5: adds the `reading_progress` table (per-item reading-quiz
         * accuracy for the Reading track). Purely additive; existing data is
         * untouched. Column names match [ReadingProgressEntity] exactly.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reading_progress` (
                        `itemKey` TEXT NOT NULL,
                        `mcqAttempts` INTEGER NOT NULL,
                        `mcqCorrect` INTEGER NOT NULL,
                        `typeAttempts` INTEGER NOT NULL,
                        `typeCorrect` INTEGER NOT NULL,
                        PRIMARY KEY(`itemKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): HiraganaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HiraganaDatabase::class.java,
                "hiragana-practice.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
