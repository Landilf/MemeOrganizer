package ru.landilf.memeorganizer.data.storage.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.landilf.memeorganizer.data.storage.database.AppDatabase
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.core.logging.LightLogStore
import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MemeDetailsRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: MemeDetailsRepositoryImpl
    private lateinit var file: File
    private var id: Long = 0

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = MemeDetailsRepositoryImpl(
            context,
            database,
            LightLogStore(context, database.lightLogDao())
        )
        val directory = File(context.filesDir, "memes").apply { mkdirs() }
        file = File(directory, UUID.randomUUID().toString()).apply { writeText("media") }
        id = database.memeDao().insert(
            MemeEntity(
                fileName = "cat.png",
                mediaType = "Image",
                mimeType = "image/png",
                addedAtMillis = 1,
                contentHash = "hash",
                storedFileName = file.name
            )
        )
    }

    @After
    fun tearDown() {
        database.close(); file.delete()
    }

    @Test
    fun savesAndReusesTagsAndKeepsUnusedTags() = runBlocking {
        repository.save(id, MemeDetails("Описание", listOf("Кот", "Смешной мем")))
        assertEquals(MemeDetails("Описание", listOf("Кот", "Смешной мем")), repository.load(id))
        repository.save(id, MemeDetails("Новое", listOf(" кот ")))
        assertEquals(MemeDetails("Новое", listOf("Кот")), repository.load(id))
        val tags = repository.observeTags().first().associateBy { it.name }
        assertEquals(1, tags.getValue("Кот").usageCount)
        assertEquals(0, tags.getValue("Смешной мем").usageCount)
    }

    @Test
    fun deletingMemeRemovesCopyAndLinksButKeepsTags() = runBlocking {
        repository.save(id, MemeDetails("", listOf("Кот")))
        repository.delete(setOf(id))
        assertFalse(file.exists())
        assertNull(database.memeDao().getById(id))
        assertEquals(0, repository.observeTags().first().single().usageCount)
    }
}
