package ru.landilf.memeorganizer.data.storage.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `media_indexing_runs` ADD COLUMN `pausedAtMillis` INTEGER")
            db.execSQL(
                "ALTER TABLE `media_indexing_runs` ADD COLUMN `totalPausedDurationMillis` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `media_indexing` ADD COLUMN `runId` INTEGER")
            db.execSQL("ALTER TABLE `media_indexing` ADD COLUMN `processingStartedAtMillis` INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `media_indexing_runs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `startedAtMillis` INTEGER NOT NULL,
                    `totalCount` INTEGER NOT NULL,
                    `completedCount` INTEGER NOT NULL,
                    `failedCount` INTEGER NOT NULL,
                    `totalDurationMillis` INTEGER NOT NULL,
                    `finishedAtMillis` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_media_indexing_runs_finishedAtMillis` ON `media_indexing_runs` (`finishedAtMillis`)"
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `media_indexing` (
                    `memeId` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `lastError` TEXT,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`memeId`),
                    FOREIGN KEY(`memeId`) REFERENCES `memes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_media_indexing_status` ON `media_indexing` (`status`)"
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `extracted_text_chunks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `memeId` INTEGER NOT NULL,
                    `source` TEXT NOT NULL,
                    `timestampMs` INTEGER,
                    `text` TEXT NOT NULL,
                    `modelReleaseTag` TEXT NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL,
                    FOREIGN KEY(`memeId`) REFERENCES `memes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_extracted_text_chunks_memeId` ON `extracted_text_chunks` (`memeId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_extracted_text_chunks_memeId_source_modelReleaseTag` ON `extracted_text_chunks` (`memeId`, `source`, `modelReleaseTag`)"
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `memes` ADD COLUMN `lastUsedAtMillis` INTEGER")
            db.execSQL("ALTER TABLE `memes` ADD COLUMN `usageCount` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_memes` (
                    `albumId` INTEGER NOT NULL,
                    `memeId` INTEGER NOT NULL,
                    PRIMARY KEY(`albumId`, `memeId`),
                    FOREIGN KEY(`albumId`) REFERENCES `albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`memeId`) REFERENCES `memes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_album_memes_memeId` ON `album_memes` (`memeId`)"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `albums` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_albums_normalizedName` ON `albums` (`normalizedName`)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `memes` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `light_log_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestampMs` INTEGER NOT NULL, `recordType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_light_log_events_timestampMs` ON `light_log_events` (`timestampMs`)")
        }
    }
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `text_embeddings` (
                    `memeId` INTEGER NOT NULL,
                    `modelSha256` TEXT NOT NULL,
                    `contentFingerprint` TEXT NOT NULL,
                    `embedding` BLOB NOT NULL,
                    `indexedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`memeId`),
                    FOREIGN KEY(`memeId`) REFERENCES `memes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_embeddings_modelSha256` ON `text_embeddings` (`modelSha256`)")
        }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `memes` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalizedName` ON `tags` (`normalizedName`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `meme_tags` (`memeId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`memeId`, `tagId`), FOREIGN KEY(`memeId`) REFERENCES `memes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meme_tags_tagId` ON `meme_tags` (`tagId`)")
        }
    }
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE `memes_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `addedAtMillis` INTEGER NOT NULL,
                    `contentHash` TEXT NOT NULL,
                    `storedFileName` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `memes_new` (`fileName`, `mediaType`, `mimeType`, `addedAtMillis`, `contentHash`, `storedFileName`)
                SELECT `fileName`, `mediaType`, `mimeType`, `addedAtMillis`, `contentHash`, `storedFileName`
                FROM `memes` ORDER BY `addedAtMillis` ASC, `id` ASC
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `memes`")
            db.execSQL("ALTER TABLE `memes_new` RENAME TO `memes`")
            db.execSQL("CREATE UNIQUE INDEX `index_memes_contentHash` ON `memes` (`contentHash`)")
        }
    }
}
