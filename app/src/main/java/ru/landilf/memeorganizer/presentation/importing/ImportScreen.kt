package ru.landilf.memeorganizer.presentation.importing

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import ru.landilf.memeorganizer.R
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.landilf.memeorganizer.presentation.common.FullMedia
import ru.landilf.memeorganizer.presentation.common.MediaThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImportScreen(state: ImportUiState, viewModel: ImportViewModel, onClose: () -> Unit) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var fullScreenSource by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerControlsVisible by rememberSaveable { mutableStateOf(true) }
    var pendingRemoval by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current
    fun back() {
        when {
            state.importing -> Unit
            fullScreenSource != null -> fullScreenSource = null
            state.selected.isNotEmpty() -> viewModel.clearSelection()
            else -> confirmCancel = true
        }
    }
    BackHandler { back() }
    LaunchedEffect(state.sources, state.importing, state.summary) {
        if (state.sources.isEmpty() && !state.importing) onClose()
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.import_cancel_confirmation_title)) },
            text = { Text(stringResource(R.string.import_cancel_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text(
                        stringResource(
                            R.string.no_button
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmCancel = false; viewModel.setSources(
                    emptyList()
                )
                }) { Text(stringResource(R.string.yes_button)) }
            }
        )
    }
    if (pendingRemoval.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = emptyList() },
            title = {
                Text(
                    if (pendingRemoval.size == 1) stringResource(R.string.import_remove_confirmation_title) else stringResource(
                        R.string.import_remove_multiple_confirmation_title,
                        pendingRemoval.size
                    )
                )
            },
            text = { Text(stringResource(R.string.import_remove_confirmation_message)) },
            dismissButton = {
                TextButton(onClick = {
                    val removed = pendingRemoval.toSet()
                    if (fullScreenSource != null) {
                        val current = state.sources.indexOf(fullScreenSource).coerceAtLeast(0)
                        val remaining = state.sources.filterNot { it in removed }
                        fullScreenSource =
                            remaining.getOrNull(current.coerceAtMost(remaining.lastIndex))
                    }
                    viewModel.remove(removed)
                    pendingRemoval = emptyList()
                }) { Text(stringResource(R.string.yes_button)) }
            },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = emptyList() }) {
                    Text(
                        stringResource(R.string.no_button)
                    )
                }
            }
        )
    }
    if (fullScreenSource != null && state.sources.isNotEmpty()) {
        val start = state.sources.indexOf(fullScreenSource).coerceAtLeast(0)
        val pager = rememberPagerState(initialPage = start, pageCount = { state.sources.size })
        Scaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(
                    pager,
                    Modifier.fillMaxSize(),
                    key = { state.sources[it] }) { page ->
                    val uri = Uri.parse(state.sources[page])
                    val mime by produceState<String?>(null, uri) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.getType(uri)
                            }.getOrNull() ?: "application/octet-stream"
                        }
                    }
                    if (mime != null) FullMedia(
                        uri, mime, Modifier.fillMaxSize(), active = page == pager.settledPage,
                        controlsVisible = viewerControlsVisible,
                        onToggleControls = { viewerControlsVisible = !viewerControlsVisible }
                    )
                }
                if (viewerControlsVisible) {
                    TopAppBar(
                        modifier = Modifier.align(Alignment.TopCenter),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        title = {},
                        actions = {
                            Text(
                                stringResource(
                                    R.string.media_viewer_position,
                                    pager.currentPage + 1,
                                    state.sources.size
                                ), Modifier.padding(end = 20.dp)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                fullScreenSource = null
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.back_button)
                                )
                            }
                        })
                    BottomAppBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = {
                                val current = pager.currentPage.coerceIn(state.sources.indices)
                                fullScreenSource = state.sources[current]
                                pendingRemoval = listOf(state.sources[current])
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    stringResource(R.string.import_remove)
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(onClick = {
                        if (!state.importing) confirmCancel = true
                    }) { Text(stringResource(R.string.cancel_button)) }
                },
                actions = {
                    Text(
                        if (state.selected.isEmpty()) stringResource(
                            R.string.import_file_count,
                            state.sources.size
                        ) else stringResource(R.string.import_selected_count, state.selected.size),
                        Modifier.padding(end = 20.dp)
                    )
                }
            )
        },
        bottomBar = {
            if (state.selected.isNotEmpty() && !state.importing) BottomAppBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = viewModel::selectAll) {
                        Icon(
                            Icons.Default.SelectAll,
                            null
                        ); Text(stringResource(R.string.import_select_all))
                    }
                    TextButton(onClick = {
                        pendingRemoval = state.selected.toList()
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            null
                        ); Text(stringResource(R.string.import_delete))
                    }
                }
            }
        },
        floatingActionButton = {
            if (state.selected.isEmpty() && !state.importing && state.sources.isNotEmpty()) {
                FloatingActionButton(onClick = viewModel::startImport) {
                    Icon(
                        Icons.Default.Check,
                        stringResource(R.string.import_confirm)
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.summary != null && !state.importing) {
                Text(
                    state.summary.asText(),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.importing) {
                LinearProgressIndicator(
                    progress = { if (state.total == 0) 0f else state.completed.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.import_progress, state.completed, state.total),
                    modifier = Modifier.padding(12.dp)
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.sources, key = { it }) { source ->
                    val uri = remember(source) { Uri.parse(source) }
                    val mime by produceState<String?>(null, uri) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.getType(uri)
                            }.getOrNull() ?: "application/octet-stream"
                        }
                    }
                    Box(
                        Modifier.aspectRatio(1f).combinedClickable(
                        enabled = !state.importing,
                        onLongClick = { viewModel.toggle(source) },
                        onClick = {
                            if (state.selected.isNotEmpty()) viewModel.toggle(source) else fullScreenSource =
                                source
                        }
                    )) {
                        if (mime != null) MediaThumbnail(uri, mime, Modifier.fillMaxSize())
                        if (source in state.selected) Checkbox(
                            checked = true,
                            onCheckedChange = { viewModel.toggle(source) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }
    }
}
