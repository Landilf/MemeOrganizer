package ru.landilf.memeorganizer.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.data.ml.ModelDownloadProgress

@Composable
internal fun ModelDownloadProgressDialog(progress: ModelDownloadProgress) {
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
