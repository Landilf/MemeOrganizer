package ru.landilf.memeorganizer.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import ru.landilf.memeorganizer.data.storage.dao.AlbumDao
import ru.landilf.memeorganizer.data.storage.dao.MemeDao
import ru.landilf.memeorganizer.data.storage.dao.TagDao
import ru.landilf.memeorganizer.data.storage.database.AppDatabase
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumEntity
import ru.landilf.memeorganizer.data.storage.entities.album.AlbumMemeEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.MemeTagEntity
import ru.landilf.memeorganizer.data.storage.entities.meme.TagEntity
import ru.landilf.memeorganizer.domain.model.album.AlbumNameRules
import ru.landilf.memeorganizer.domain.model.backup.LibraryBackupSummary
import ru.landilf.memeorganizer.domain.model.backup.LibraryRestoreSummary
import ru.landilf.memeorganizer.domain.model.meme.MemeMediaType
import ru.landilf.memeorganizer.domain.model.meme.MemeTextRules
import ru.landilf.memeorganizer.domain.repository.LibraryBackupRepository
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryBackupRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val memeDao: MemeDao,
    private val tagDao: TagDao,
    private val albumDao: AlbumDao
) : LibraryBackupRepository {
    private val automaticBackupMutex = Mutex()

    override suspend fun create(destination: Uri): LibraryBackupSummary = withContext(Dispatchers.IO) {
        val snapshot = database.withTransaction { createSnapshot() }
        context.contentResolver.openOutputStream(destination)?.buffered()?.use { output ->
            writeArchive(output, snapshot)
        } ?: error("Cannot open backup destination")
        LibraryBackupSummary(snapshot.memes.size, snapshot.albums.size)
    }

    override suspend fun createInDirectory(directory: Uri): LibraryBackupSummary =
        automaticBackupMutex.withLock {
            withContext(Dispatchers.IO) {
                val snapshot = database.withTransaction { createSnapshot() }
                val parent = requireNotNull(DocumentFile.fromTreeUri(context, directory)) {
                    "Cannot open backup directory"
                }
                check(parent.exists() && parent.canWrite()) { "Backup directory is unavailable" }
                recoverAutomaticBackup(parent)
                val destination = requireNotNull(
                    parent.createFile("application/zip", AUTOMATIC_BACKUP_TEMP_FILE_NAME)
                ) {
                    "Cannot create automatic backup"
                }
                context.contentResolver.openOutputStream(destination.uri)?.buffered()?.use { output ->
                    writeArchive(output, snapshot)
                } ?: error("Cannot open automatic backup destination")
                replaceAutomaticBackup(parent, destination)
                LibraryBackupSummary(snapshot.memes.size, snapshot.albums.size)
            }
        }

    /** Finalizes a completed staged archive after an interrupted replacement. */
    private fun recoverAutomaticBackup(parent: DocumentFile) {
        val current = parent.findFile(AUTOMATIC_BACKUP_FILE_NAME)
        val staged = parent.findFile(AUTOMATIC_BACKUP_TEMP_FILE_NAME)
        val legacyPrevious = parent.findFile(LEGACY_AUTOMATIC_BACKUP_PREVIOUS_FILE_NAME)
        when {
            current == null && staged != null -> {
                copyStagedBackupToPrimary(parent, staged)
                check(staged.delete()) { "Cannot remove recovered automatic backup" }
                legacyPrevious?.let { previous ->
                    check(previous.delete()) { "Cannot remove obsolete automatic backup" }
                }
            }

            current == null && legacyPrevious != null -> {
                copyStagedBackupToPrimary(parent, legacyPrevious)
                check(legacyPrevious.delete()) { "Cannot remove recovered automatic backup" }
            }

            current != null -> {
                staged?.let { temporary ->
                    check(temporary.delete()) { "Cannot remove incomplete automatic backup" }
                }
                legacyPrevious?.let { previous ->
                    check(previous.delete()) { "Cannot remove obsolete automatic backup" }
                }
            }
        }
    }

    /**
     * Some SAF providers do not implement document renaming. A completed staged archive therefore
     * remains available until its bytes have been copied into a newly created primary document.
     */
    private fun replaceAutomaticBackup(parent: DocumentFile, destination: DocumentFile) {
        parent.findFile(AUTOMATIC_BACKUP_FILE_NAME)?.let { current ->
            check(current.delete()) { "Cannot replace automatic backup" }
        }
        copyStagedBackupToPrimary(parent, destination)
        check(destination.delete()) { "Cannot remove temporary automatic backup" }
    }

    private fun copyStagedBackupToPrimary(parent: DocumentFile, staged: DocumentFile) {
        val primary = requireNotNull(
            parent.createFile("application/zip", AUTOMATIC_BACKUP_FILE_NAME)
        ) { "Cannot create automatic backup destination" }
        try {
            check(primary.name == AUTOMATIC_BACKUP_FILE_NAME) {
                "Cannot create automatic backup with the expected name"
            }
            val input = requireNotNull(context.contentResolver.openInputStream(staged.uri)) {
                "Cannot open temporary automatic backup"
            }
            input.buffered().use { source ->
                context.contentResolver.openOutputStream(primary.uri)?.buffered()?.use(source::copyTo)
                    ?: error("Cannot open automatic backup destination")
            }
        } catch (error: Exception) {
            primary.delete()
            throw error
        }
    }

    override suspend fun restore(source: Uri): LibraryRestoreSummary = withContext(Dispatchers.IO) {
        val archive = copyArchiveToCache(source)
        val stagingRoot = File(context.filesDir, ".restore-${UUID.randomUUID()}")
        try {
            ZipFile(archive).use { zip ->
                val snapshot = readSnapshot(zip)
                val candidateMediaDirectory = File(stagingRoot, "memes")
                extractMedia(zip, snapshot, candidateMediaDirectory)
                replaceLibrary(snapshot, candidateMediaDirectory)
                LibraryRestoreSummary(snapshot.memes.size, snapshot.albums.size)
            }
        } finally {
            archive.delete()
            stagingRoot.deleteRecursively()
        }
    }

    private suspend fun createSnapshot(): LibraryBackupSnapshot {
        val memes = memeDao.getAll()
        val tagsByMemeId = tagDao.getAllMemeTags().groupBy({ it.memeId }, { it.name })
        val albums = albumDao.getAll()
        val memeHashById = memes.associate { it.id to it.contentHash }
        val albumMemeHashes = albumDao.getAllMemeLinks().groupBy({ it.albumId }, { link ->
            requireNotNull(memeHashById[link.memeId])
        })
        return LibraryBackupSnapshot(
            memes = memes.map { meme ->
                BackupMeme(
                    contentHash = meme.contentHash,
                    fileName = meme.fileName,
                    mediaType = meme.mediaType,
                    mimeType = meme.mimeType,
                    addedAtMillis = meme.addedAtMillis,
                    description = meme.description,
                    tags = tagsByMemeId[meme.id].orEmpty(),
                    isFavorite = meme.isFavorite,
                    lastUsedAtMillis = meme.lastUsedAtMillis,
                    usageCount = meme.usageCount,
                    storedFileName = meme.storedFileName
                )
            },
            albums = albums.map { album ->
                BackupAlbum(
                    name = album.name,
                    createdAtMillis = album.createdAtMillis,
                    memeHashes = albumMemeHashes[album.id].orEmpty()
                )
            }
        )
    }

    private fun writeArchive(output: java.io.OutputStream, snapshot: LibraryBackupSnapshot) {
        ZipOutputStream(output).use { zip ->
            zip.writeTextEntry(MANIFEST_ENTRY, snapshot.toJson().toString())
            snapshot.memes.forEach { meme ->
                val media = File(mediaDirectory, meme.storedFileName)
                check(media.isFile) { "Media file is missing: ${meme.contentHash}" }
                zip.writeFileEntry(mediaEntryName(meme.contentHash), media)
            }
        }
    }

    private fun copyArchiveToCache(source: Uri): File {
        val target = File.createTempFile("library-backup-", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().buffered().use(input::copyTo)
            } ?: error("Cannot open backup archive")
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        return target
    }

    private fun readSnapshot(zip: ZipFile): LibraryBackupSnapshot {
        val entries = buildList {
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) add(enumeration.nextElement())
        }
        val manifest = entries.singleOrNull { it.name == MANIFEST_ENTRY } ?: error(
            "Backup manifest is missing"
        )
        require(!manifest.isDirectory) { "Backup manifest is invalid" }
        val root = zip.getInputStream(manifest).bufferedReader().use { JSONObject(it.readText()) }
        return LibraryBackupSnapshot.fromJson(root).also { snapshot ->
            snapshot.validate(entries.map(ZipEntry::getName).toSet())
        }
    }

    private fun extractMedia(
        zip: ZipFile,
        snapshot: LibraryBackupSnapshot,
        candidateDirectory: File
    ) {
        check(candidateDirectory.mkdirs() || candidateDirectory.isDirectory) {
            "Cannot prepare media directory"
        }
        snapshot.memes.forEach { meme ->
            val entry = zip.getEntry(mediaEntryName(meme.contentHash))
                ?: error("Media entry is missing: ${meme.contentHash}")
            val target = File(candidateDirectory, meme.storedFileName)
            zip.getInputStream(entry).use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            check(target.sha256() == meme.contentHash) { "Media hash mismatch: ${meme.contentHash}" }
        }
    }

    private suspend fun replaceLibrary(
        snapshot: LibraryBackupSnapshot,
        candidateMediaDirectory: File
    ) {
        val previousDirectory = File(context.filesDir, ".memes-before-restore-${UUID.randomUUID()}")
        val hadPreviousDirectory = mediaDirectory.exists()
        if (hadPreviousDirectory && !mediaDirectory.renameTo(previousDirectory)) {
            error("Cannot prepare current library for restore")
        }
        if (!candidateMediaDirectory.renameTo(mediaDirectory)) {
            if (hadPreviousDirectory) previousDirectory.renameTo(mediaDirectory)
            error("Cannot activate restored media")
        }
        try {
            database.withTransaction {
                albumDao.deleteAll()
                memeDao.deleteAll()
                tagDao.deleteAll()

                val restoredMemes = snapshot.memes.map { meme ->
                    MemeEntity(
                        fileName = meme.fileName,
                        mediaType = meme.mediaType,
                        mimeType = meme.mimeType,
                        addedAtMillis = meme.addedAtMillis,
                        contentHash = meme.contentHash,
                        storedFileName = meme.storedFileName,
                        description = meme.description,
                        isFavorite = meme.isFavorite,
                        lastUsedAtMillis = meme.lastUsedAtMillis,
                        usageCount = meme.usageCount
                    )
                }
                val restoredIds = memeDao.insertAll(restoredMemes)
                val memeIdByHash = snapshot.memes.map { it.contentHash }.zip(restoredIds).toMap()
                val tagIdByNormalizedName = mutableMapOf<String, Long>()
                snapshot.memes.forEach { meme ->
                    val memeId = requireNotNull(memeIdByHash[meme.contentHash])
                    meme.tags.forEach { name ->
                        val normalized = MemeTextRules.normalizeTag(name)
                        val tagId = tagIdByNormalizedName.getOrPut(normalized) {
                            tagDao.insertTag(TagEntity(name = name, normalizedName = normalized))
                        }
                        tagDao.insertLink(MemeTagEntity(memeId, tagId))
                    }
                }
                snapshot.albums.forEach { album ->
                    val albumId = albumDao.insert(
                        AlbumEntity(
                            name = album.name,
                            normalizedName = AlbumNameRules.normalize(album.name),
                            createdAtMillis = album.createdAtMillis
                        )
                    )
                    check(albumId != -1L) { "Cannot restore album: ${album.name}" }
                    albumDao.insertMemeLinks(
                        album.memeHashes.map { hash ->
                            AlbumMemeEntity(albumId, requireNotNull(memeIdByHash[hash]))
                        }
                    )
                }
            }
        } catch (error: Exception) {
            val failedDirectory = File(context.filesDir, ".memes-failed-restore-${UUID.randomUUID()}")
            if (mediaDirectory.exists()) mediaDirectory.renameTo(failedDirectory)
            if (hadPreviousDirectory) previousDirectory.renameTo(mediaDirectory)
            failedDirectory.deleteRecursively()
            throw error
        }
        previousDirectory.deleteRecursively()
    }

    private fun LibraryBackupSnapshot.toJson(): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", FORMAT_VERSION)
        .put("created_at_ms", System.currentTimeMillis())
        .put("memes", JSONArray(memes.map(BackupMeme::toJson)))
        .put("albums", JSONArray(albums.map(BackupAlbum::toJson)))

    private data class LibraryBackupSnapshot(
        val memes: List<BackupMeme>,
        val albums: List<BackupAlbum>
    ) {
        fun validate(entryNames: Set<String>) {
            require(memes.map(BackupMeme::contentHash).distinct().size == memes.size) {
                "Backup contains duplicate media"
            }
            require(memes.map(BackupMeme::storedFileName).distinct().size == memes.size) {
                "Backup contains duplicate media filenames"
            }
            val hashes = memes.map(BackupMeme::contentHash).toSet()
            memes.forEach { meme ->
                require(HASH_REGEX.matches(meme.contentHash)) { "Invalid media hash" }
                require(meme.storedFileName.matches(FILE_NAME_REGEX)) { "Invalid media filename" }
                require(meme.mediaType in MemeMediaType.entries.map { it.name }) {
                    "Unsupported media type"
                }
                MemeTextRules.validate(meme.description, meme.tags)
                require(entryNames.contains(mediaEntryName(meme.contentHash))) {
                    "Media entry is missing"
                }
            }
            require(albums.map { AlbumNameRules.normalize(it.name) }.distinct().size == albums.size) {
                "Backup contains duplicate albums"
            }
            albums.forEach { album ->
                AlbumNameRules.validate(album.name)
                require(album.memeHashes.all(hashes::contains)) { "Album references unknown media" }
                require(album.memeHashes.distinct().size == album.memeHashes.size) {
                    "Album contains duplicate media"
                }
            }
        }

        companion object {
            fun fromJson(root: JSONObject): LibraryBackupSnapshot {
                require(root.getString("format") == FORMAT) { "Unsupported backup format" }
                require(root.getInt("version") == FORMAT_VERSION) { "Unsupported backup version" }
                return LibraryBackupSnapshot(
                    memes = root.getJSONArray("memes").jsonObjects().map(BackupMeme::fromJson),
                    albums = root.getJSONArray("albums").jsonObjects().map(BackupAlbum::fromJson)
                )
            }
        }
    }

    private data class BackupMeme(
        val contentHash: String,
        val fileName: String,
        val mediaType: String,
        val mimeType: String,
        val addedAtMillis: Long,
        val description: String,
        val tags: List<String>,
        val isFavorite: Boolean,
        val lastUsedAtMillis: Long?,
        val usageCount: Long,
        val storedFileName: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("content_hash", contentHash)
            .put("file_name", fileName)
            .put("media_type", mediaType)
            .put("mime_type", mimeType)
            .put("added_at_ms", addedAtMillis)
            .put("description", description)
            .put("tags", JSONArray(tags))
            .put("is_favorite", isFavorite)
            .put("last_used_at_ms", lastUsedAtMillis ?: JSONObject.NULL)
            .put("usage_count", usageCount)
            .put("stored_file_name", storedFileName)

        companion object {
            fun fromJson(json: JSONObject): BackupMeme = BackupMeme(
                contentHash = json.getString("content_hash"),
                fileName = json.getString("file_name"),
                mediaType = json.getString("media_type"),
                mimeType = json.getString("mime_type"),
                addedAtMillis = json.getLong("added_at_ms"),
                description = json.getString("description"),
                tags = json.getJSONArray("tags").stringValues(),
                isFavorite = json.getBoolean("is_favorite"),
                lastUsedAtMillis = json.optLongOrNull("last_used_at_ms"),
                usageCount = json.getLong("usage_count"),
                storedFileName = json.getString("stored_file_name")
            )
        }
    }

    private data class BackupAlbum(
        val name: String,
        val createdAtMillis: Long,
        val memeHashes: List<String>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("name", name)
            .put("created_at_ms", createdAtMillis)
            .put("meme_hashes", JSONArray(memeHashes))

        companion object {
            fun fromJson(json: JSONObject): BackupAlbum = BackupAlbum(
                name = json.getString("name"),
                createdAtMillis = json.getLong("created_at_ms"),
                memeHashes = json.getJSONArray("meme_hashes").stringValues()
            )
        }
    }

    private fun ZipOutputStream.writeTextEntry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.writeFileEntry(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        try {
            file.inputStream().buffered().use { it.copyTo(this) }
        } finally {
            closeEntry()
        }
    }

    private fun File.sha256(): String = inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val mediaDirectory: File get() = File(context.filesDir, "memes")

    private companion object {
        const val FORMAT = "meme-organizer-library-backup"
        const val FORMAT_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
        const val AUTOMATIC_BACKUP_FILE_NAME = "meme-organizer-library-backup.zip"
        const val AUTOMATIC_BACKUP_TEMP_FILE_NAME = ".meme-organizer-library-backup.new.zip"
        const val LEGACY_AUTOMATIC_BACKUP_PREVIOUS_FILE_NAME =
            ".meme-organizer-library-backup.previous.zip"
        val HASH_REGEX = Regex("[0-9a-f]{64}")
        val FILE_NAME_REGEX = Regex("[A-Za-z0-9-]{1,64}")

        fun mediaEntryName(contentHash: String): String = "media/$contentHash"
    }
}

private fun JSONArray.jsonObjects(): List<JSONObject> =
    List(length()) { index -> getJSONObject(index) }

private fun JSONArray.stringValues(): List<String> =
    List(length()) { index -> getString(index) }

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else getLong(key)
