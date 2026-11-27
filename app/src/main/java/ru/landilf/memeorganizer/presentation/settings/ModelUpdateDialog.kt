package ru.landilf.memeorganizer.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
fun ModelUpdateDialog(
    state: ModelUpdateUiState,
    onAccept: (ModelRelease) -> Unit,
    onPostpone: (ModelRelease) -> Unit
) {
    val progress = state.progress
    if (progress != null) {
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
                    if (progress is ModelDownloadProgress.Downloading) Text(
                        stringResource(
                            R.string.model_update_progress_percent,
                            (fraction * 100).toInt()
                        ), modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {}
        )
        return
    }
    val release = state.available ?: return
    AlertDialog(
        onDismissRequest = { onPostpone(release) },
        title = { Text(stringResource(R.string.model_update_available_title)) },
        text = {
            Text(
                stringResource(
                    R.string.model_update_available_message,
                    release.tag,
                    release.archiveSizeBytes / (1024f * 1024f)
                )
            )
        },
        dismissButton = { TextButton(onClick = { onPostpone(release) }) { Text(stringResource(R.string.no_button)) } },
        confirmButton = { TextButton(onClick = { onAccept(release) }) { Text(stringResource(R.string.yes_button)) } }
    )
}
