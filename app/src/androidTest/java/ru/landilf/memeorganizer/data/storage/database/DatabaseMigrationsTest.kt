package ru.landilf.memeorganizer.data.storage.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.data.storage.mappers.meme.MemeStorageMapper
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationsTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "migration_${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesExistingMemesAndPreservesFileReferences() = runBlocking {
        val fileName = "migration-${UUID.randomUUID()}"
        val directory = File(context.filesDir, "memes").apply { mkdirs() }
        val file = File(directory, fileName).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            helper.createDatabase(databaseName, 1).apply {
                execSQL(
                    "INSERT INTO memes (id, fileName, mediaType, mimeType, addedAtMillis, contentHash, storedFileName) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(
                        "uuid-old",
                        "photo.png",
                        "Image",
                        "image/png",
                        123L,
                        "hash-a",
                        fileName
                    )
                )
                execSQL(
                    "INSERT INTO memes (id, fileName, mediaType, mimeType, addedAtMillis, contentHash, storedFileName) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(
                        "uuid-video",
                        "video.mp4",
                        "Video",
                        "video/mp4",
                        456L,
                        "hash-b",
                        "video-file"
                    )
                )
                close()
            }
            helper.runMigrationsAndValidate(databaseName, 2, true, DatabaseMigrations.MIGRATION_1_2)
                .close()
            val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .addMigrations(
                    DatabaseMigrations.MIGRATION_1_2,
                    DatabaseMigrations.MIGRATION_2_3,
                    DatabaseMigrations.MIGRATION_3_4,
                    DatabaseMigrations.MIGRATION_4_5,
                    DatabaseMigrations.MIGRATION_5_6,
                    DatabaseMigrations.MIGRATION_6_7
                )
                .build()
            try {
                val memes = database.memeDao().observeMemes().first()
                assertEquals(2, memes.size)
                assertEquals(listOf("hash-b", "hash-a"), memes.map { it.contentHash })
                val photo = memes.single { it.contentHash == "hash-a" }
                assertTrue(photo.id > 0)
                assertNotEquals(photo.id, memes.first().id)
                assertEquals("photo.png", photo.fileName)
                assertEquals("Image", photo.mediaType)
                assertEquals("image/png", photo.mimeType)
                assertEquals(123L, photo.addedAtMillis)
                assertEquals(fileName, MemeStorageMapper().toDomain(photo).storedFileName)
                assertTrue(
                    File(directory, photo.storedFileName).readBytes()
                        .contentEquals(byteArrayOf(1, 2, 3))
                )

                val newId = database.memeDao().insert(
                    MemeEntity(
                        fileName = "new.png",
                        mediaType = "Image",
                        mimeType = "image/png",
                        addedAtMillis = 789,
                        contentHash = "hash-c",
                        storedFileName = "new-file"
                    )
                )
                assertTrue(newId > memes.maxOf { it.id })
                assertEquals(-1L, database.memeDao().insert(photo.copy(id = 0)))
            } finally {
                database.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun migratesEmptyDatabase() {
        helper.createDatabase(databaseName, 1).close()
        helper.runMigrationsAndValidate(databaseName, 2, true, DatabaseMigrations.MIGRATION_1_2)
            .use { database ->
                database.query("SELECT COUNT(*) FROM memes").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migratesVersion2AndKeepsExistingMeme() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL("INSERT INTO memes (id, fileName, mediaType, mimeType, addedAtMillis, contentHash, storedFileName) VALUES (7, 'cat.png', 'Image', 'image/png', 123, 'hash', 'stored')")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 3, true, DatabaseMigrations.MIGRATION_2_3)
            .use { database ->
                database.query("SELECT id, fileName, contentHash, storedFileName, description FROM memes")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(7L, cursor.getLong(0))
                        assertEquals("cat.png", cursor.getString(1))
                        assertEquals("hash", cursor.getString(2))
                        assertEquals("stored", cursor.getString(3))
                        assertEquals("", cursor.getString(4))
                    }
                database.query("SELECT COUNT(*) FROM tags").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migratesVersion3AndKeepsUserData() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL("INSERT INTO memes (id, fileName, mediaType, mimeType, addedAtMillis, contentHash, storedFileName, description) VALUES (5, 'cat.png', 'Image', 'image/png', 123, 'hash', 'stored', 'кот в капюшоне')")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 4, true, DatabaseMigrations.MIGRATION_3_4)
            .use { database ->
                database.query("SELECT description FROM memes WHERE id = 5").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("кот в капюшоне", cursor.getString(0))
                }
                database.query("SELECT COUNT(*) FROM text_embeddings").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migratesVersion4AndCreatesLightLogStorage() {
        helper.createDatabase(databaseName, 4).apply {
            execSQL("INSERT INTO memes (id, fileName, mediaType, mimeType, addedAtMillis, contentHash, storedFileName, description) VALUES (9, 'cat.png', 'Image', 'image/png', 123, 'hash', 'stored', 'кот')")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 5, true, DatabaseMigrations.MIGRATION_4_5)
            .use { database ->
                database.query("SELECT COUNT(*) FROM light_log_events").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
                database.query("SELECT description FROM memes WHERE id = 9").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("кот", cursor.getString(0))
                }
            }
    }

    @Test
    fun migratesVersion5AndKeepsFavoriteDisabled() {
        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                "INSERT INTO memes (id, fileName, mediaType, mimeType, addedAtMillis, contentHash, storedFileName, description) VALUES (10, 'cat.png', 'Image', 'image/png', 123, 'hash', 'stored', 'кот')"
            )
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 6, true, DatabaseMigrations.MIGRATION_5_6)
            .use { database ->
                database.query("SELECT isFavorite FROM memes WHERE id = 10").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migratesVersion6AndCreatesAlbumStorage() {
        helper.createDatabase(databaseName, 6).close()
        helper.runMigrationsAndValidate(databaseName, 7, true, DatabaseMigrations.MIGRATION_6_7)
            .use { database ->
                database.execSQL(
                    "INSERT INTO albums (name, normalizedName, createdAtMillis) VALUES ('Коты', 'коты', 123)"
                )
                database.query("SELECT name FROM albums").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Коты", cursor.getString(0))
                }
            }
    }
}
