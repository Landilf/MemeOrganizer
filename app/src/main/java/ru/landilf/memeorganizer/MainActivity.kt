package ru.landilf.memeorganizer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.landilf.memeorganizer.presentation.AppRoot

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sharedMediaSources = MutableSharedFlow<List<String>>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        publishSharedMedia(intent)
        setContent { AppRoot(sharedMediaSources.asSharedFlow()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishSharedMedia(intent)
    }

    private fun publishSharedMedia(intent: Intent) {
        val sources = intent.sharedMediaUris()
        if (sources.isNotEmpty()) sharedMediaSources.tryEmit(sources.map(Uri::toString))
    }

    private fun Intent.sharedMediaUris(): List<Uri> {
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return emptyList()
        val streamUris = buildList {
            IntentCompat.getParcelableExtra(this@sharedMediaUris, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let(::add)
            IntentCompat.getParcelableArrayListExtra(
                this@sharedMediaUris,
                Intent.EXTRA_STREAM,
                Uri::class.java
            )?.let(::addAll)
            clipData?.let { clip ->
                repeat(clip.itemCount) { index -> clip.getItemAt(index).uri?.let(::add) }
            }
        }.distinct()
        return streamUris.filter { uri ->
            contentResolver.getType(uri)?.let(::isSupportedMediaType)
                ?: type?.let(::isSupportedMediaType)
                ?: false
        }
    }

    private fun isSupportedMediaType(mimeType: String): Boolean =
        mimeType.startsWith("image/", ignoreCase = true) ||
            mimeType.startsWith("video/", ignoreCase = true)
}
