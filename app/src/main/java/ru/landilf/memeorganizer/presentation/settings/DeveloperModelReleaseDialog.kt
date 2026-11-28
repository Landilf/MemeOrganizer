package ru.landilf.memeorganizer.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.data.ml.ModelDownloadProgress
import ru.landilf.memeorganizer.data.ml.ModelRelease

@Composable
fun DeveloperModelReleaseDialog(
    state: DeveloperModelReleaseUiState,
    onDismiss: () -> Unit,
    onSelect: (ModelRelease) -> Unit,
    onConfirm: () -> Unit,
    onDismissSelection: () -> Unit
) {
    if (!state.visible) return
    val progress = state.progress
    if (progress != null) {
        ModelReleaseProgressDialog(progress)
        return
    }
    val selectedRelease = state.selectedRelease
    if (selectedRelease != null) {
        ModelReleaseConfirmationDialog(
            release = selectedRelease,
            onConfirm = onConfirm,
            onDismiss = onDismissSelection
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.developer_model_release_picker_title)) },
        text = {
            when {
                state.loading -> LoadingContent()
                state.releases.isEmpty() -> Text(stringResource(R.string.developer_model_release_picker_empty))
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.releases.forEach { release ->
                        TextButton(
                            onClick = { onSelect(release) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    release.tag,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                val prereleaseLabel = stringResource(
                                    R.string.developer_model_release_prerelease
                                )
                                val installedLabel = stringResource(
                                    R.string.developer_model_release_installed
                                )
                                val label = buildString {
                                    append(release.title)
                                    if (release.isPrerelease) {
                                        append(" · ")
                                        append(prereleaseLabel)
                                    }
                                    if (release.tag == state.installedTag) {
                                        append(" · ")
                                        append(installedLabel)
                                    }
                                }
                                Text(label, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    stringResource(
                                        R.string.developer_model_release_size,
                                        release.archiveSizeBytes / MEBIBYTE.toFloat()
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun LoadingContent() {
    Column {
        Text(stringResource(R.string.developer_model_release_picker_loading))
        CircularProgressIndicator(Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ModelReleaseConfirmationDialog(
    release: ModelRelease,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.developer_model_release_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.developer_model_release_confirm_message,
                    release.tag,
                    release.archiveSizeBytes / MEBIBYTE.toFloat()
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.yes_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.no_button)) }
        }
    )
}

@Composable
private fun ModelReleaseProgressDialog(progress: ModelDownloadProgress) {
    val fraction = when (progress) {
        is ModelDownloadProgress.Downloading -> {
            if (progress.totalBytes > 0) {
                (progress.downloadedBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

        ModelDownloadProgress.Installing -> 1f
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.model_update_progress_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (progress is ModelDownloadProgress.Downloading) {
                            R.string.model_update_downloading
                        } else {
                            R.string.model_update_installing
                        }
                    )
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
                if (progress is ModelDownloadProgress.Downloading) {
                    Text(
                        stringResource(
                            R.string.model_update_progress_percent,
                            (fraction * 100).toInt()
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {}
    )
}

private const val MEBIBYTE = 1024 * 1024
