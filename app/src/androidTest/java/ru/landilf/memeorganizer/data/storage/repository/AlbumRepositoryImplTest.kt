package ru.landilf.memeorganizer.data.storage.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.landilf.memeorganizer.data.storage.database.AppDatabase
import ru.landilf.memeorganizer.data.storage.mappers.album.AlbumStorageMapper

@RunWith(AndroidJUnit4::class)
class AlbumRepositoryImplTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AlbumRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = AlbumRepositoryImpl(database.albumDao(), AlbumStorageMapper())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createsAlbumsAndRejectsNormalizedDuplicate() = runBlocking {
        assertTrue(repository.create("Коты"))
        assertFalse(repository.create("  коты  "))

        val albums = repository.observeAlbums().first()
        assertEquals(listOf("Коты"), albums.map { it.name })
    }
}
