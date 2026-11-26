package ru.landilf.memeorganizer.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.landilf.memeorganizer.R

@Composable
fun LibraryBackupSection(viewModel: LibraryBackupViewModel) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var restoreSource by remember { mutableStateOf<Uri?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { destination ->
        if (destination != null) viewModel.create(destination)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source ->
        if (source != null) restoreSource = source
    }

    if (restoreSource != null) {
        AlertDialog(
            onDismissRequest = { restoreSource = null },
            title = { Text(stringResource(R.string.settings_restore_library_title)) },
            text = { Text(stringResource(R.string.settings_restore_library_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    val source = restoreSource ?: return@TextButton
                    restoreSource = null
                    viewModel.restore(source)
                }) { Text(stringResource(R.string.yes_button)) }
            },
            dismissButton = {
                TextButton(onClick = { restoreSource = null }) {
                    Text(stringResource(R.string.no_button))
                }
            }
        )
    }

    Column {
        Text(
            stringResource(R.string.settings_library_backup),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.settings_library_backup_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Button(
            onClick = {
                viewModel.clearStatus()
                createLauncher.launch("meme-organizer-library-backup.zip")
            },
            enabled = !status.isBusy(),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(stringResource(R.string.settings_create_library_backup))
        }
        OutlinedButton(
            onClick = {
                viewModel.clearStatus()
                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            },
            enabled = !status.isBusy(),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.settings_restore_library))
        }
        when (val current = status) {
            LibraryBackupStatus.Creating -> BackupProgress(R.string.settings_library_backup_creating)
            LibraryBackupStatus.Restoring -> BackupProgress(R.string.settings_library_backup_restoring)
            is LibraryBackupStatus.CreateSuccess -> Text(
                stringResource(
                    R.string.settings_library_backup_created,
                    current.summary.memeCount,
                    current.summary.albumCount
                ),
                modifier = Modifier.padding(top = 12.dp)
            )

            is LibraryBackupStatus.RestoreSuccess -> Text(
                stringResource(
                    R.string.settings_library_backup_restored,
                    current.summary.memeCount,
                    current.summary.albumCount
                ),
                modifier = Modifier.padding(top = 12.dp)
            )

            LibraryBackupStatus.CreateError -> Text(
                stringResource(R.string.settings_library_backup_create_error),
                modifier = Modifier.padding(top = 12.dp)
            )

            LibraryBackupStatus.RestoreError -> Text(
                stringResource(R.string.settings_library_backup_restore_error),
                modifier = Modifier.padding(top = 12.dp)
            )

            LibraryBackupStatus.Idle -> Unit
        }
    }
}

@Composable
private fun BackupProgress(textRes: Int) {
    Text(stringResource(textRes), modifier = Modifier.padding(top = 12.dp))
    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
}
