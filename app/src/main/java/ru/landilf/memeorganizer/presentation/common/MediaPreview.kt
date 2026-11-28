package ru.landilf.memeorganizer.presentation.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.VideoView
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import ru.landilf.memeorganizer.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

private const val THUMBNAIL_SIZE_PX = 512

private val thumbnails = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}
private val thumbnailDecodeSemaphore = Semaphore(permits = 2)

private fun thumbnailKey(uri: Uri, video: Boolean) =
    if (video) "video:${uri}" else "image:${uri}"

private fun generatedThumbnailFile(uri: Uri): File? {
    if (uri.scheme != "file") return null
    val source = uri.path?.let(::File) ?: return null
    val mediaDirectory = source.parentFile?.takeIf { it.name == "memes" } ?: return null
    val filesDirectory = mediaDirectory.parentFile ?: return null
    return File(filesDirectory, "thumbnails/${source.name}.webp")
}

private suspend fun loadThumbnail(
    key: String,
    decode: () -> Bitmap?
): Bitmap? =
    withContext(Dispatchers.IO) {
        thumbnails.get(key) ?: thumbnailDecodeSemaphore.withPermit {
            thumbnails.get(key) ?: runCatching(decode).getOrNull()?.also { thumbnails.put(key, it) }
        }
    }

private suspend fun loadVideoFrame(context: android.content.Context, uri: Uri): Bitmap? {
    val key = thumbnailKey(uri, video = true)
    return loadThumbnail(key) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMBNAIL_SIZE_PX,
                THUMBNAIL_SIZE_PX
            )
            else retriever.getFrameAtTime(0)
        } finally {
            retriever.release()
        }
    }
}

private suspend fun loadImageThumbnail(context: android.content.Context, uri: Uri): Bitmap? {
    val key = thumbnailKey(uri, video = false)
    return loadThumbnail(key) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (
                bounds.outWidth / sample > THUMBNAIL_SIZE_PX ||
                bounds.outHeight / sample > THUMBNAIL_SIZE_PX
            ) {
                sample *= 2
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
        }
    }
}

private suspend fun loadOriginalImage(context: android.content.Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

private suspend fun loadGeneratedThumbnail(file: File): Bitmap? =
    loadThumbnail("thumbnail:${file.path}") { BitmapFactory.decodeFile(file.path) }

@Composable
fun MediaThumbnail(uri: Uri, mimeType: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val video = mimeType?.startsWith("video/") == true
    val key = remember(uri, video) { thumbnailKey(uri, video) }
    val generatedFile = remember(uri) { generatedThumbnailFile(uri) }
    val generatedKey = generatedFile?.let { "thumbnail:${it.path}" }
    val bitmap by produceState<Bitmap?>(
        initialValue = generatedKey?.let(thumbnails::get) ?: thumbnails.get(key),
        key1 = key,
        key2 = generatedKey
    ) {
        if (generatedFile != null) {
            repeat(50) {
                if (generatedFile.isFile) {
                    value = loadGeneratedThumbnail(generatedFile)
                    return@produceState
                }
                delay(100)
            }
        }
        value = thumbnails.get(key) ?: if (video) {
            loadVideoFrame(context, uri)
        } else {
            loadImageThumbnail(context, uri)
        }
    }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) Image(
            image.asImageBitmap(),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        else if (generatedFile == null) Icon(
            Icons.Default.BrokenImage,
            contentDescription = stringResource(R.string.media_preview_unavailable)
        )
        if (video) Icon(
            Icons.Default.PlayCircle,
            contentDescription = stringResource(R.string.media_video_description),
            tint = androidx.compose.ui.graphics.Color.White
        )
    }
}

@Composable
fun FullMedia(
    uri: Uri,
    mimeType: String?,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    controlsVisible: Boolean = true,
    onToggleControls: () -> Unit = {}
) {
    if (mimeType?.startsWith("video/") == true) {
        VideoPreview(uri, modifier, active, controlsVisible, onToggleControls)
    } else if (Build.VERSION.SDK_INT >= 28) {
        val context = LocalContext.current
        val drawable by produceState<android.graphics.drawable.Drawable?>(null, uri) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(
                            context.contentResolver,
                            uri
                        )
                    )
                }.getOrNull()
            }
        }
        ZoomableMedia(
            uri,
            modifier.pointerInput(uri) { detectTapGestures(onTap = { onToggleControls() }) }) { transform ->
            AndroidView(
                modifier = Modifier.fillMaxSize().then(transform),
                factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = {
                    it.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.start()
                },
                onRelease = { (it.drawable as? AnimatedImageDrawable)?.stop() })
        }
    } else {
        val context = LocalContext.current
        val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
            value = loadOriginalImage(context, uri)
        }
        ZoomableMedia(
            uri,
            modifier.pointerInput(uri) { detectTapGestures(onTap = { onToggleControls() }) }) { transform ->
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().then(transform),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun ZoomableMedia(uri: Uri, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    var scale by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        offset = Offset(
            (offset.x + pan.x).coerceIn(-maxX, maxX),
            (offset.y + pan.y).coerceIn(-maxY, maxY)
        )
    }
    Box(
        modifier.onSizeChanged { size = it }
            .transformable(state = transformState, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center
    ) {
        content(Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y; clip =
            true
        })
    }
}

@Composable
private fun VideoPreview(
    uri: Uri,
    modifier: Modifier,
    active: Boolean,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    var started by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    var position by remember(uri) { mutableStateOf(0) }
    var duration by remember(uri) { mutableStateOf(0) }
    var playerView by remember(uri) { mutableStateOf<VideoView?>(null) }
    val frame by produceState<Bitmap?>(null, uri) { value = loadVideoFrame(context, uri) }

    LaunchedEffect(active) {
        if (!active) {
            started = false; playing = false
        }
    }
    LaunchedEffect(started, playing) {
        while (started && playing) {
            position = playerView?.currentPosition ?: 0
            delay(250)
        }
    }
    Box(
        modifier.background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center
    ) {
        ZoomableMedia(uri, Modifier.fillMaxSize().pointerInput(uri, started, active, duration) {
            detectTapGestures(
                onTap = { if (!started && active) started = true else onToggleControls() },
                onDoubleTap = { point ->
                    if (started && active && duration > 0) {
                        val current = playerView?.currentPosition ?: position
                        position =
                            (current + if (point.x < size.width / 2f) -5000 else 5000).coerceIn(
                                0,
                                duration
                            )
                        playerView?.seekTo(position)
                    }
                }
            )
        }) { transform ->
            if (started && active) {
                AndroidView(modifier = Modifier.fillMaxSize().then(transform), factory = {
                    VideoView(it).apply {
                        playerView = this
                        setOnPreparedListener { mediaPlayer ->
                            duration = mediaPlayer.duration.coerceAtLeast(0)
                            start()
                            playing = true
                        }
                        setOnCompletionListener { playing = false; position = duration }
                        setVideoURI(uri)
                    }
                }, onRelease = { it.stopPlayback(); playerView = null })
            } else {
                frame?.let {
                    Image(
                        it.asImageBitmap(),
                        null,
                        Modifier.fillMaxSize().then(transform),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        if ((controlsVisible || !started) && active) {
            IconButton(onClick = {
                if (!started) started = true
                else playerView?.let {
                    if (it.isPlaying) {
                        it.pause(); playing = false
                    } else {
                        it.start(); playing = true
                    }
                }
            }, modifier = Modifier.align(Alignment.Center)) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayCircle,
                    if (playing) stringResource(R.string.video_pause) else stringResource(R.string.video_play),
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (started && active && controlsVisible) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = if (duration > 0) (position.toFloat() / duration).coerceIn(
                        0f,
                        1f
                    ) else 0f, onValueChange = {
                        position = (it * duration).toInt()
                        playerView?.seekTo(position)
                    }, modifier = Modifier.weight(1f), enabled = duration > 0
                )
                Text(
                    stringResource(
                        R.string.video_position,
                        position / 60000,
                        position / 1000 % 60
                    ), color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}
