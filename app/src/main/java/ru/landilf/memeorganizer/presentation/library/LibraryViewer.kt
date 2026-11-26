package ru.landilf.memeorganizer.presentation.library

import android.net.Uri
import android.content.ClipData
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
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.presentation.common.FullMedia
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryViewer(
    memes: List<Meme>,
    initialId: Long,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onCopy: (Meme) -> Unit = {}
) {
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    val suggestions by viewModel.tags.collectAsStateWithLifecycle()
    var visible by rememberSaveable { mutableStateOf(true) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    BackHandler(enabled = edit == null) { onBack() }
    LaunchedEffect(memes.isEmpty()) { if (memes.isEmpty()) onBack() }
    if (memes.isEmpty()) return
    val pager = rememberPagerState(initialPage = memes.indexOfFirst { it.id == initialId }.coerceAtLeast(0), pageCount = { memes.size })
    if (pendingDelete != null) AlertDialog(
        onDismissRequest = { pendingDelete = null },
        title = { Text(stringResource(R.string.library_delete_title)) },
        text = { Text(stringResource(R.string.library_delete_message)) },
        dismissButton = { TextButton(onClick = { viewModel.delete(setOfNotNull(pendingDelete)); pendingDelete = null }) { Text(stringResource(R.string.yes_button)) } },
        confirmButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.no_button)) } }
    )
    edit?.let { MemeEditDialog(it, suggestions, viewModel::updateDescription, viewModel::updateTags, viewModel::saveEditor, viewModel::closeEditor) }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { memes[it].id }) { page ->
                val meme = memes[page]
                FullMedia(Uri.fromFile(File(context.filesDir, "memes/${meme.storedFileName}")), meme.mimeType, Modifier.fillMaxSize(), page == pager.settledPage, visible) { visible = !visible }
            }
            if (visible) {
                TopAppBar(modifier = Modifier.align(Alignment.TopCenter), windowInsets = WindowInsets(0, 0, 0, 0), title = {}, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button)) } }, actions = { Text(stringResource(R.string.media_viewer_position, pager.currentPage + 1, memes.size), Modifier.padding(end = 20.dp)) })
                BottomAppBar(modifier = Modifier.align(Alignment.BottomCenter), windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { memes.getOrNull(pager.currentPage)?.let { viewModel.openEditor(it.id) } }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_title)) }
                        IconButton(onClick = {
                            memes.getOrNull(pager.currentPage)?.takeIf { copyMemeToClipboard(context, it) }?.let {
                                Toast.makeText(context, R.string.library_copy_success, Toast.LENGTH_SHORT).show()
                                onCopy(it)
                            }
                        }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.library_copy)) }
                        IconButton(onClick = { pendingDelete = memes.getOrNull(pager.currentPage)?.id }) { Icon(Icons.Default.Delete, stringResource(R.string.library_delete)) }
                    }
                }
            }
        }
    }
}

private fun copyMemeToClipboard(context: android.content.Context, meme: Meme): Boolean = runCatching {
    val file = File(File(context.filesDir, "memes"), meme.storedFileName)
    check(file.isFile)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, meme.fileName, uri))
}.isSuccess
