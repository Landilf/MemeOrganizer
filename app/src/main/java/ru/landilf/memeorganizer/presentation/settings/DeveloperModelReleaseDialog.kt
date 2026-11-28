package ru.landilf.memeorganizer.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.data.ml.ModelRelease
import ru.landilf.memeorganizer.presentation.common.ConfirmationDialog

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
        ModelDownloadProgressDialog(progress)
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
    ConfirmationDialog(
        title = { Text(stringResource(R.string.developer_model_release_confirm_title)) },
        message = {
            Text(
                stringResource(
                    R.string.developer_model_release_confirm_message,
                    release.tag,
                    release.archiveSizeBytes / MEBIBYTE.toFloat()
                )
            )
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

private const val MEBIBYTE = 1024 * 1024
