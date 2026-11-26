package ru.landilf.memeorganizer.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.domain.model.settings.AutomaticBackupPeriod
import java.text.DateFormat
import java.util.Date

@Composable
fun AutomaticBackupSection(viewModel: AutomaticBackupViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.setDirectory(uri)
    }

    Column {
        Text(
            stringResource(R.string.settings_automatic_backup),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.settings_automatic_backup_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text(
                stringResource(R.string.settings_automatic_backup_enabled),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.automaticBackupEnabled,
                onCheckedChange = viewModel::setEnabled
            )
        }
        OutlinedButton(
            onClick = {
                viewModel.clearNotice()
                directoryPicker.launch(null)
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.settings_automatic_backup_choose_directory))
        }
        Text(
            stringResource(
                R.string.settings_automatic_backup_directory,
                settings.automaticBackupDirectoryName
                    ?: stringResource(R.string.settings_automatic_backup_directory_not_selected)
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            stringResource(R.string.settings_automatic_backup_period),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        AutomaticBackupPeriod.entries.forEach { period ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = settings.automaticBackupPeriod == period,
                    onClick = { viewModel.setPeriod(period) }
                )
                Text(
                    stringResource(period.labelRes()),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Button(
            onClick = viewModel::runNow,
            enabled = settings.automaticBackupEnabled &&
                    settings.automaticBackupDirectoryUri != null,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.settings_automatic_backup_run_now))
        }
        settings.automaticBackupLastSuccessAtMillis?.let { timestamp ->
            Text(
                stringResource(
                    R.string.settings_automatic_backup_last_success,
                    DateFormat.getDateTimeInstance().format(Date(timestamp))
                ),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        settings.automaticBackupLastError?.let { error ->
            Text(
                stringResource(R.string.settings_automatic_backup_last_error, error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        notice?.let { value ->
            Text(
                stringResource(value.messageRes()),
                color = if (value == AutomaticBackupNotice.DirectoryError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun AutomaticBackupPeriod.labelRes(): Int = when (this) {
    AutomaticBackupPeriod.Every3Days -> R.string.settings_automatic_backup_period_3_days
    AutomaticBackupPeriod.Weekly -> R.string.settings_automatic_backup_period_week
    AutomaticBackupPeriod.Every2Weeks -> R.string.settings_automatic_backup_period_2_weeks
}

private fun AutomaticBackupNotice.messageRes(): Int = when (this) {
    AutomaticBackupNotice.DirectoryRequired -> R.string.settings_automatic_backup_directory_required
    AutomaticBackupNotice.DirectorySaved -> R.string.settings_automatic_backup_directory_saved
    AutomaticBackupNotice.DirectoryError -> R.string.settings_automatic_backup_directory_error
    AutomaticBackupNotice.Queued -> R.string.settings_automatic_backup_queued
}
