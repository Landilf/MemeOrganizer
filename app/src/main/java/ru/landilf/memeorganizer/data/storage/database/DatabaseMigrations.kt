package ru.landilf.memeorganizer.data.storage.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
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
