package ru.landilf.memeorganizer.presentation.library

import android.net.Uri
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
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
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.album.Album
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
    onFavorite: (Meme, Boolean) -> Unit = { _, _ -> }
) {
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val suggestions by viewModel.tags.collectAsStateWithLifecycle()
    var visible by rememberSaveable { mutableStateOf(true) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumPickerVisible by rememberSaveable { mutableStateOf(false) }
    var secondaryMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var hasShownMedia by remember(initialId) { mutableStateOf(false) }
    val context = LocalContext.current
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
    if (pendingDelete != null) AlertDialog(
        onDismissRequest = { pendingDelete = null },
        title = { Text(stringResource(R.string.library_delete_title)) },
        text = { Text(stringResource(R.string.library_delete_message)) },
        dismissButton = {
            TextButton(onClick = {
                viewModel.delete(setOfNotNull(pendingDelete)); pendingDelete = null
            }) { Text(stringResource(R.string.yes_button)) }
        },
        confirmButton = {
            TextButton(onClick = {
                pendingDelete = null
            }) { Text(stringResource(R.string.no_button)) }
        }
    )
    if (albumPickerVisible) {
        AlertDialog(
            onDismissRequest = { albumPickerVisible = false },
            title = { Text(stringResource(R.string.albums_add_memes_title)) },
            text = {
                Column {
                    albums.filterNot { it.id == currentAlbumId }.forEach { album ->
                        TextButton(
                            onClick = {
                                memes.getOrNull(pager.currentPage)?.let { meme ->
                                    viewModel.addMemesToAlbum(album.id, setOf(meme.id)) {
                                        albumPickerVisible = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(album.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { albumPickerVisible = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
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
                                pendingDelete = meme.id
                            }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.library_delete)
                            )
                        }
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
                                copyMemeToClipboard(context, meme)
                                    .onSuccess {
                                        Toast.makeText(
                                            context,
                                            R.string.library_copy_success,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onCopy(meme)
                                    }
                                    .onFailure { error ->
                                        AppLogger.app { "media.copy.failed type=${error.javaClass.simpleName}" }
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
        val file = File(File(context.filesDir, "memes"), meme.storedFileName)
        check(file.isFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData(
            ClipDescription(meme.fileName, arrayOf(meme.mimeType)),
            ClipData.Item(uri)
        )
        clipboard.setPrimaryClip(clip)
    }
