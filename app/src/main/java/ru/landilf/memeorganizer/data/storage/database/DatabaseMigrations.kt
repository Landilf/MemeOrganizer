package ru.landilf.memeorganizer.data.storage.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
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
