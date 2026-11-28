package ru.landilf.memeorganizer.data.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.landilf.memeorganizer.core.logging.AppLogger
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

private val Context.modelUpdateDataStore by preferencesDataStore("model_update")

data class ModelRelease(
    val tag: String,
    val title: String,
    val isPrerelease: Boolean,
    val archiveName: String,
    val archiveUrl: String,
    val archiveSizeBytes: Long,
    val checksumsUrl: String,
    val archiveSha256: String? = null
)

sealed interface ModelUpdateCheck {
    data object UpToDate : ModelUpdateCheck
    data object Offline : ModelUpdateCheck
    data class Available(val release: ModelRelease) : ModelUpdateCheck
}

sealed interface ModelDownloadProgress {
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadProgress
    data object Installing : ModelDownloadProgress
}

class ModelUpdateOfflineException : IllegalStateException("No internet connection")

@Singleton
class GitHubModelUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val installer: LocalModelInstaller
) {
    suspend fun checkForUpdate(manual: Boolean): ModelUpdateCheck = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext ModelUpdateCheck.Offline
        val release = latestRelease()
        val preferences = context.modelUpdateDataStore.data.first()
        val installedTag = installer.activePackage()?.releaseTag
        context.modelUpdateDataStore.edit { stored ->
            if (installedTag == null) stored.remove(INSTALLED_TAG) else stored[INSTALLED_TAG] =
                installedTag
        }
        val dismissedTag = preferences[DISMISSED_TAG]
        if (installedTag != null && compareVersions(
                release.tag,
                installedTag
            ) <= 0
        ) return@withContext ModelUpdateCheck.UpToDate
        if (!manual && dismissedTag == release.tag) return@withContext ModelUpdateCheck.UpToDate
        ModelUpdateCheck.Available(release)
    }

    /**
     * Lists installable model bundles for the developer picker. Unlike automatic updates, this
     * intentionally includes prereleases and allows selecting an older package.
     */
    suspend fun availableReleases(): List<ModelRelease> = withContext(Dispatchers.IO) {
        if (!isOnline()) throw ModelUpdateOfflineException()
        val connection = openConnection(ALL_RELEASES_URL)
        val releases = try {
            require(connection.responseCode in 200..299) { "Cannot load model releases" }
            org.json.JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
        (0 until releases.length()).mapNotNull { index ->
            val release = releases.getJSONObject(index)
            if (release.getBoolean("draft")) return@mapNotNull null
            runCatching { release.toModelRelease(loadChecksum = false) }
                .onFailure { error ->
                    AppLogger.ml {
                        "update.release.skip tag=${release.optString("tag_name")} type=${error.javaClass.simpleName}"
                    }
                }
                .getOrNull()
        }
    }

    suspend fun dismiss(release: ModelRelease) {
        context.modelUpdateDataStore.edit { it[DISMISSED_TAG] = release.tag }
    }

    suspend fun recordInstalledTag(tag: String?) {
        if (tag == null) return
        context.modelUpdateDataStore.edit { it[INSTALLED_TAG] = tag }
    }

    suspend fun downloadAndInstall(
        release: ModelRelease,
        onProgress: (ModelDownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!isOnline()) throw ModelUpdateOfflineException()
        AppLogger.ml { "update.download.start tag=${release.tag} bytes=${release.archiveSizeBytes}" }
        val archiveSha256 = release.archiveSha256
            ?: checksumFor(downloadText(release.checksumsUrl), release.archiveName)
        val connection = openConnection(release.archiveUrl)
        try {
            require(connection.responseCode in 200..299) { "Cannot download model archive" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.archiveSizeBytes
            connection.inputStream.use { source ->
                val progress = ProgressInputStream(source, total) { downloaded ->
                    onProgress(ModelDownloadProgress.Downloading(downloaded, total))
                }
                installer.installDownloaded(progress, archiveSha256) {
                    onProgress(ModelDownloadProgress.Installing)
                }
            }
            context.modelUpdateDataStore.edit {
                it[INSTALLED_TAG] = release.tag
                it.remove(DISMISSED_TAG)
            }
            AppLogger.ml { "update.download.done tag=${release.tag}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun latestRelease(): ModelRelease {
        val connection = openConnection(LATEST_RELEASE_URL)
        val release = try {
            require(connection.responseCode in 200..299) { "Cannot check model updates" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
        require(!release.getBoolean("draft") && !release.getBoolean("prerelease")) { "Latest release is not stable" }
        return release.toModelRelease(loadChecksum = true)
    }

    private fun JSONObject.toModelRelease(loadChecksum: Boolean): ModelRelease {
        val tag = getString("tag_name")
        require(VERSION_PATTERN.matches(tag)) { "Unsupported model release tag" }
        val assets = getJSONArray("assets")
        fun asset(predicate: (String) -> Boolean): JSONObject? = (0 until assets.length())
            .asSequence().map { assets.getJSONObject(it) }
            .firstOrNull { predicate(it.getString("name")) }

        val archive =
            requireNotNull(asset { it.endsWith(".tar.gz") }) { "Model archive is missing" }
        val checksums = requireNotNull(asset {
            it.equals(
                "SHA256SUMS.txt",
                ignoreCase = true
            )
        }) { "SHA256SUMS.txt is missing" }
        val archiveName = archive.getString("name")
        val checksumsUrl = checksums.getString("browser_download_url")
        return ModelRelease(
            tag = tag,
            title = optString("name").ifBlank { tag },
            isPrerelease = getBoolean("prerelease"),
            archiveName = archiveName,
            archiveUrl = archive.getString("browser_download_url"),
            archiveSizeBytes = archive.getLong("size"),
            checksumsUrl = checksumsUrl,
            archiveSha256 = if (loadChecksum) {
                checksumFor(downloadText(checksumsUrl), archiveName)
            } else {
                null
            }
        )
    }

    private fun downloadText(url: String): String {
        val connection = openConnection(url)
        return try {
            require(connection.responseCode in 200..299) { "Cannot download checksums" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun checksumFor(contents: String, archiveName: String): String = contents.lineSequence()
        .mapNotNull { line -> CHECKSUM_PATTERN.matchEntire(line.trim()) }
        .firstOrNull { it.groupValues[2].removePrefix("*") == archiveName }
        ?.groupValues?.get(1)?.lowercase()
        ?: error("Archive checksum is missing")

    private fun openConnection(url: String): HttpsURLConnection {
        require(url.startsWith("https://")) { "Only HTTPS releases are supported" }
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MemeOrganizer-Android")
        }
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private class ProgressInputStream(
        input: InputStream,
        private val totalBytes: Long,
        private val report: (Long) -> Unit
    ) : FilterInputStream(input) {
        private var downloaded = 0L
        private var lastReported = -REPORT_INTERVAL

        override fun read(): Int = super.read().also { if (it >= 0) onRead(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) onRead(it.toLong()) }

        private fun onRead(read: Long) {
            downloaded += read
            if (downloaded - lastReported >= REPORT_INTERVAL || downloaded == totalBytes) {
                lastReported = downloaded
                report(downloaded)
            }
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val l =
            requireNotNull(VERSION_PATTERN.matchEntire(left)).groupValues.drop(1).map(String::toInt)
        val r = requireNotNull(VERSION_PATTERN.matchEntire(right)).groupValues.drop(1)
            .map(String::toInt)
        return l.zip(r).firstOrNull { it.first != it.second }?.let { it.first.compareTo(it.second) }
            ?: 0
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/alexo375/meme-organizer/releases/latest"
        const val ALL_RELEASES_URL =
            "https://api.github.com/repos/alexo375/meme-organizer/releases?per_page=20"
        val INSTALLED_TAG = stringPreferencesKey("installed_model_release_tag")
        val DISMISSED_TAG = stringPreferencesKey("dismissed_model_release_tag")
        val VERSION_PATTERN = Regex("v(\\d+)\\.(\\d+)\\.(\\d+)-mobile")
        val CHECKSUM_PATTERN = Regex("([0-9a-fA-F]{64})\\s+(.+)")
        const val REPORT_INTERVAL = 256L * 1024L
    }
}
