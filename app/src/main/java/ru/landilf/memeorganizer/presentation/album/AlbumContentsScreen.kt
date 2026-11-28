package ru.landilf.memeorganizer.presentation.album

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus
import ru.landilf.memeorganizer.presentation.common.MediaIndexingBadge
import ru.landilf.memeorganizer.presentation.common.MediaThumbnail
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumContentsScreen(
    title: String,
    memes: List<Meme>,
    indexingStatuses: Map<Long, MediaIndexingStatus>,
    onBack: () -> Unit,
    onMemeClick: (Meme) -> Unit,
    onIndexingFailure: (Long) -> Unit = {},
    onRemoveMemes: ((Set<Long>) -> Unit)? = null
) {
    val context = LocalContext.current
    val supportsSelection = onRemoveMemes != null
    var selected by remember { mutableStateOf(setOf<Long>()) }
    BackHandler(enabled = supportsSelection && selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(enabled = !supportsSelection || selected.isEmpty(), onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!supportsSelection || selected.isEmpty()) Text(title)
                    else Text(stringResource(R.string.import_selected_count, selected.size))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (supportsSelection && selected.isNotEmpty()) {
                BottomAppBar {
                    TextButton(
                        onClick = {
                            onRemoveMemes?.invoke(selected)
                            selected = emptySet()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Text(stringResource(R.string.albums_remove_memes))
                    }
                }
            }
        }
    ) { padding ->
        if (memes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.albums_contents_empty),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        } else {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize().padding(padding),
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(memes, key = Meme::id) { meme ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(
                            onClick = {
                                if (!supportsSelection || selected.isEmpty()) onMemeClick(meme)
                                else {
                                    selected = if (meme.id in selected) {
                                        selected - meme.id
                                    } else {
                                        selected + meme.id
                                    }
                                }
                            },
                            onLongClick = if (supportsSelection) {
                                { selected = selected + meme.id }
                            } else {
                                null
                            }
                        )
                    ) {
                        MediaThumbnail(
                            Uri.fromFile(File(context.filesDir, "memes/${meme.storedFileName}")),
                            meme.mimeType,
                            Modifier.fillMaxSize()
                        )
                        MediaIndexingBadge(
                            status = indexingStatuses[meme.id],
                            modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp),
                            onClick = if (
                                indexingStatuses[meme.id] == MediaIndexingStatus.Failed
                            ) {
                                { onIndexingFailure(meme.id) }
                            } else {
                                null
                            }
                        )
                        if (meme.id in selected) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = { selected = selected - meme.id },
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}
