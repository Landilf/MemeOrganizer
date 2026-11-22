package ru.landilf.memeorganizer.data.storage.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.landilf.memeorganizer.data.storage.database.AppDatabase
import ru.landilf.memeorganizer.data.storage.mappers.meme.MemeStorageMapper
import ru.landilf.memeorganizer.domain.model.importing.ImportResult
import ru.landilf.memeorganizer.domain.model.importing.ImportSource
import java.io.File

@RunWith(AndroidJUnit4::class)
class MemeRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: MemeRepositoryImpl
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = MemeRepositoryImpl(context, database.memeDao(), MemeStorageMapper())
        sourceFile = File.createTempFile("meme-test-", ".png", context.cacheDir)
        sourceFile.writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    @After
    fun tearDown() {
        runBlocking {
            database.memeDao().observeMemes().first().forEach {
                File(context.filesDir, "memes/${it.storedFileName}").delete()
            }
        }
        database.close()
        sourceFile.delete()
    }

    @Test
    fun importsFileAndDetectsDuplicate() = runBlocking {
        val source = ImportSource(Uri.fromFile(sourceFile).toString())

        val first = repository.importMeme(source)
        val second = repository.importMeme(source)

        assertTrue(first is ImportResult.Imported)
        assertTrue(second is ImportResult.Duplicate)
        val stored = database.memeDao().observeMemes().first()
        assertEquals(1, stored.size)
        assertTrue(File(context.filesDir, "memes/${stored.single().storedFileName}").readBytes().contentEquals(sourceFile.readBytes()))
        assertEquals(stored.single().id, (second as ImportResult.Duplicate).existingMeme.id)
    }

    @Test
    fun concurrentImportsCreateOnlyOneRecord() = runBlocking {
        val source = ImportSource(Uri.fromFile(sourceFile).toString())

        val results = List(2) { async { repository.importMeme(source) } }.awaitAll()

        assertEquals(1, results.count { it is ImportResult.Imported })
        assertEquals(1, results.count { it is ImportResult.Duplicate })
        assertEquals(1, repository.observeMemes().first().size)
    }
}
