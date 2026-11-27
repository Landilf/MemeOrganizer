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
import kotlinx.coroutines.withContext

private val videoFrames = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private suspend fun loadVideoFrame(context: android.content.Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        videoFrames.get(uri.toString()) ?: runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(
                    0,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    512,
                    512
                )
                else retriever.getFrameAtTime(0)
            } finally {
                retriever.release()
            }
        }.getOrNull()?.also { videoFrames.put(uri.toString(), it) }
    }

@Composable
fun MediaThumbnail(uri: Uri, mimeType: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val video = mimeType?.startsWith("video/") == true
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, mimeType) {
        value = null
        value = if (video) loadVideoFrame(context, uri) else withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, options) }
                var sample = 1
                while (options.outWidth / sample > 512 || options.outHeight / sample > 512) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
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
        else Icon(
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
                    ) { decoder, info, _ ->
                        val largest = maxOf(info.size.width, info.size.height)
                        if (largest > 2048) decoder.setTargetSampleSize((largest + 2047) / 2048)
                    }
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
        ZoomableMedia(
            uri,
            modifier.pointerInput(uri) { detectTapGestures(onTap = { onToggleControls() }) }) { transform ->
            MediaThumbnail(
                uri,
                mimeType,
                Modifier.fillMaxSize().then(transform)
            )
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
