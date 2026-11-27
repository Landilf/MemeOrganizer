package ru.landilf.memeorganizer.presentation.library

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.core.logging.AppLogger
import ru.landilf.memeorganizer.domain.model.album.Album
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.meme.MemeMediaType
import ru.landilf.memeorganizer.presentation.common.FullMedia
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryViewer(
    memes: List<Meme>,
    initialId: Long,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    albums: List<Album> = emptyList(),
    currentAlbumId: Long? = null,
    onCopy: (Meme) -> Unit = {},
    onShare: (Meme) -> Unit = {},
    onFavorite: (Meme, Boolean) -> Unit = { _, _ -> }
) {
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val suggestions by viewModel.tags.collectAsStateWithLifecycle()
    var visible by rememberSaveable { mutableStateOf(true) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumPickerVisible by rememberSaveable { mutableStateOf(false) }
    var secondaryMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingExportId by rememberSaveable { mutableStateOf<Long?>(null) }
    var hasShownMedia by remember(initialId) { mutableStateOf(false) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { destination ->
        val meme = pendingExportId?.let { id -> memes.firstOrNull { it.id == id } }
        pendingExportId = null
        if (destination != null && meme != null) {
            viewModel.exportMeme(
                meme = meme,
                destination = destination,
                onSuccess = {
                    Toast.makeText(
                        context,
                        R.string.library_export_success,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = {
                    Toast.makeText(
                        context,
                        R.string.library_export_error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
    BackHandler(enabled = edit == null) { onBack() }
    LaunchedEffect(memes.isNotEmpty()) {
        if (memes.isNotEmpty()) {
            hasShownMedia = true
        } else if (hasShownMedia) {
            onBack()
        }
    }
    if (memes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val pager = rememberPagerState(
        initialPage = memes.indexOfFirst { it.id == initialId }.coerceAtLeast(0),
        pageCount = { memes.size })
    LaunchedEffect(memes.size) {
        if (pager.currentPage >= memes.size) {
            pager.scrollToPage(memes.lastIndex)
        }
    }
    if (pendingDelete != null) {
        DeleteMemesConfirmationDialog(
            onConfirm = {
                viewModel.delete(setOfNotNull(pendingDelete))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
    if (albumPickerVisible) {
        AlbumPickerDialog(
            albums = albums,
            excludedAlbumId = currentAlbumId,
            onAlbumSelected = { album ->
                memes.getOrNull(pager.currentPage)?.let { meme ->
                    viewModel.addMemesToAlbum(album.id, setOf(meme.id)) {
                        albumPickerVisible = false
                    }
                }
            },
            onDismiss = { albumPickerVisible = false }
        )
    }
    edit?.let {
        MemeEditDialog(
            it,
            suggestions,
            viewModel::updateDescription,
            viewModel::updateTags,
            viewModel::saveEditor,
            viewModel::closeEditor
        )
    }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { page -> memes.getOrNull(page)?.id ?: page }
            ) { page ->
                memes.getOrNull(page)?.let { meme ->
                    FullMedia(
                        Uri.fromFile(File(context.filesDir, "memes/${meme.storedFileName}")),
                        meme.mimeType,
                        Modifier.fillMaxSize(),
                        page == pager.settledPage,
                        visible
                    ) { visible = !visible }
                }
            }
            if (visible) {
                TopAppBar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.back_button)
                            )
                        }
                    },
                    actions = {
                        Text(
                            stringResource(
                                R.string.media_viewer_position,
                                pager.currentPage + 1,
                                memes.size
                            ), Modifier.padding(end = 20.dp)
                        )
                    })
                BottomAppBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = {
                            memes.getOrNull(pager.currentPage)?.let { meme ->
                                val isFavorite = !meme.isFavorite
                                viewModel.setFavorite(meme, isFavorite) {
                                    onFavorite(meme, isFavorite)
                                }
                            }
                        }) {
                            Icon(
                                if (memes.getOrNull(pager.currentPage)?.isFavorite == true) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                stringResource(
                                    if (memes.getOrNull(pager.currentPage)?.isFavorite == true) {
                                        R.string.library_remove_favorite
                                    } else {
                                        R.string.library_favorite
                                    }
                                )
                            )
                        }
                        IconButton(onClick = {
                            memes.getOrNull(pager.currentPage)?.let { viewModel.openEditor(it.id) }
                        }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_title)) }
                        IconButton(onClick = {
                            memes.getOrNull(pager.currentPage)?.let { meme ->
                                shareMeme(context, meme)
                                    .onSuccess {
                                        viewModel.markMemeUsed(meme.id)
                                        onShare(meme)
                                    }
                                    .onFailure { error ->
                                        AppLogger.app { "media.share.failed type=${error.javaClass.simpleName}" }
                                        Toast.makeText(
                                            context,
                                            R.string.library_share_error,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        }) {
                            Icon(Icons.Default.Share, stringResource(R.string.library_share))
                        }
                        if (memes.getOrNull(pager.currentPage)?.mediaType != MemeMediaType.Video) {
                            IconButton(onClick = {
                                memes.getOrNull(pager.currentPage)?.let { meme ->
                                    copyMemeToClipboard(context, meme)
                                        .onSuccess {
                                            viewModel.markMemeUsed(meme.id)
                                            Toast.makeText(
                                                context,
                                                R.string.library_copy_success,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onCopy(meme)
                                        }
                                        .onFailure { error ->
                                            AppLogger.app {
                                                "media.copy.failed type=${error.javaClass.simpleName}"
                                            }
                                            Toast.makeText(
                                                context,
                                                R.string.library_copy_error,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                }
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    stringResource(R.string.library_copy)
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { secondaryMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    stringResource(R.string.library_more_actions)
                                )
                            }
                            DropdownMenu(
                                expanded = secondaryMenuExpanded,
                                onDismissRequest = { secondaryMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_export)) },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                    onClick = {
                                        val meme = memes.getOrNull(pager.currentPage) ?: return@DropdownMenuItem
                                        secondaryMenuExpanded = false
                                        pendingExportId = meme.id
                                        exportLauncher.launch(meme.fileName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_delete)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = {
                                        secondaryMenuExpanded = false
                                        pendingDelete = memes.getOrNull(pager.currentPage)?.id
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.albums_add_memes)) },
                                    leadingIcon = { Icon(Icons.Default.Collections, null) },
                                    enabled = albums.any { it.id != currentAlbumId },
                                    onClick = {
                                        secondaryMenuExpanded = false
                                        albumPickerVisible = true
                                    }
                                )
                                if (currentAlbumId != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.albums_remove_meme)) },
                                        leadingIcon = { Icon(Icons.Default.Remove, null) },
                                        onClick = {
                                            secondaryMenuExpanded = false
                                            memes.getOrNull(pager.currentPage)?.let { meme ->
                                                viewModel.removeMemesFromAlbum(
                                                    currentAlbumId,
                                                    setOf(meme.id)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyMemeToClipboard(context: android.content.Context, meme: Meme): Result<Unit> =
    runCatching {
        val uri = memeContentUri(context, meme)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData(
            ClipDescription(meme.fileName, arrayOf(meme.mimeType)),
            ClipData.Item(uri)
        )
        clipboard.setPrimaryClip(clip)
    }

private fun shareMeme(context: android.content.Context, meme: Meme): Result<Unit> = runCatching {
    val uri = memeContentUri(context, meme)
    val intent = Intent(Intent.ACTION_SEND)
        .setType(meme.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri(meme.fileName, uri) }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.library_share_chooser)))
}

private fun memeContentUri(context: android.content.Context, meme: Meme): Uri {
    val file = File(File(context.filesDir, "memes"), meme.storedFileName)
    check(file.isFile)
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
