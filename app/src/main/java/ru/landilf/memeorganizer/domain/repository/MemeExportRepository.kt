package ru.landilf.memeorganizer.domain.repository

import android.net.Uri
import ru.landilf.memeorganizer.domain.model.meme.Meme

interface MemeExportRepository {
    /** Copies the library-owned media file to a URI chosen by the user. */
    suspend fun export(meme: Meme, destination: Uri)
}
