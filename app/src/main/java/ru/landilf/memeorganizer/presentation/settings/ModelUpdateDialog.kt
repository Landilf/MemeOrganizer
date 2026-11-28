package ru.landilf.memeorganizer.presentation.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.data.ml.ModelRelease
import ru.landilf.memeorganizer.presentation.common.ConfirmationDialog

@Composable
fun ModelUpdateDialog(
    state: ModelUpdateUiState,
    onAccept: (ModelRelease) -> Unit,
    onPostpone: (ModelRelease) -> Unit
) {
    state.progress?.let { progress ->
        ModelDownloadProgressDialog(progress)
        return
    }
    val release = state.available ?: return
    ConfirmationDialog(
        title = { Text(stringResource(R.string.model_update_available_title)) },
        message = {
            Text(
                stringResource(
                    R.string.model_update_available_message,
                    release.tag,
                    release.archiveSizeBytes / (1024f * 1024f)
                )
            )
        },
        onConfirm = { onAccept(release) },
        onDismiss = { onPostpone(release) }
    )
}
