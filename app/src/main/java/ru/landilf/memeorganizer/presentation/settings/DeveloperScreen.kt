package ru.landilf.memeorganizer.presentation.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(viewModel: DeveloperViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val reindexedCount by viewModel.reindexedCount.collectAsStateWithLifecycle()
    val memes by viewModel.memes.collectAsStateWithLifecycle()
    val lowBatteryConfirmationRequired by viewModel.lowBatteryConfirmationRequired.collectAsStateWithLifecycle()
    var includeMedia by rememberSaveable { mutableStateOf(true) }
    var clearConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var failurePickerVisible by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.requestReindexAll() }
    val filePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                if (includeMedia) "application/zip" else "application/gzip"
            )
        ) { uri ->
            if (uri != null) viewModel.export(uri, includeMedia)
        }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.developer_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.back_button)
                    )
                }
            }
        )
    }) { padding ->
        if (clearConfirmationVisible) {
            AlertDialog(
                onDismissRequest = { clearConfirmationVisible = false },
                title = { Text(stringResource(R.string.developer_clear_logs_title)) },
                text = { Text(stringResource(R.string.developer_clear_logs_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        clearConfirmationVisible = false
                        viewModel.clearLogs()
                    }) { Text(stringResource(R.string.yes_button)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        clearConfirmationVisible = false
                    }) { Text(stringResource(R.string.no_button)) }
                }
            )
        }
        if (lowBatteryConfirmationRequired) {
            AlertDialog(
                onDismissRequest = viewModel::dismissLowBatteryConfirmation,
                title = { Text(stringResource(R.string.developer_low_battery_title)) },
                text = { Text(stringResource(R.string.developer_low_battery_message)) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmReindexAtLowBattery) {
                        Text(stringResource(R.string.yes_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissLowBatteryConfirmation) {
                        Text(stringResource(R.string.no_button))
                    }
                }
            )
        }
        if (failurePickerVisible) {
            AlertDialog(
                onDismissRequest = { failurePickerVisible = false },
                title = { Text(stringResource(R.string.developer_simulate_failure_title)) },
                text = {
                    if (memes.isEmpty()) {
                        Text(stringResource(R.string.developer_simulate_failure_empty))
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(
                                rememberScrollState()
                            )
                        ) {
                            memes.forEach { meme ->
                                TextButton(
                                    onClick = {
                                        failurePickerVisible = false
                                        viewModel.simulateIndexingFailure(meme.id)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(meme.fileName, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { failurePickerVisible = false }) {
                        Text(stringResource(R.string.cancel_button))
                    }
                }
            )
        }
        Column(Modifier.padding(padding).padding(24.dp)) {
            Text(
                stringResource(R.string.developer_logs_description),
                style = MaterialTheme.typography.bodyMedium
            )
            androidx.compose.foundation.layout.Row(
                Modifier.padding(top = 16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Checkbox(checked = includeMedia, onCheckedChange = { includeMedia = it })
                Text(
                    stringResource(R.string.developer_include_media),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (includeMedia) Text(
                stringResource(R.string.developer_include_media_warning),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    viewModel.clearStatus()
                    filePicker.launch(
                        if (includeMedia) {
                            "meme-organizer-full-debug.zip"
                        } else {
                            "meme-organizer-events.jsonl.gz"
                        }
                    )
                },
                enabled = !status.isBusy(),
                modifier = Modifier.padding(top = 16.dp)
            ) { Text(stringResource(R.string.developer_export_logs)) }
            OutlinedButton(
                onClick = { clearConfirmationVisible = true },
                enabled = !status.isBusy(),
                modifier = Modifier.padding(top = 12.dp)
            ) { Text(stringResource(R.string.developer_clear_logs)) }
            OutlinedButton(
                onClick = {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.requestReindexAll()
                    }
                },
                enabled = !status.isBusy(),
                modifier = Modifier.padding(top = 12.dp)
            ) { Text(stringResource(R.string.developer_reindex_all)) }
            OutlinedButton(
                onClick = viewModel::runFusionSmokeTest,
                enabled = !status.isBusy(),
                modifier = Modifier.padding(top = 12.dp)
            ) { Text(stringResource(R.string.developer_fusion_smoke_test)) }
            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    onClick = { failurePickerVisible = true },
                    enabled = !status.isBusy() && memes.isNotEmpty(),
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text(stringResource(R.string.developer_simulate_failure)) }
            }
            when (status) {
                DebugExportStatus.Exporting, DebugExportStatus.Clearing -> LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(top = 16.dp)
                )

                DebugExportStatus.Reindexing -> DeveloperProgress(R.string.developer_reindexing)

                DebugExportStatus.TestingFusion -> DeveloperProgress(
                    R.string.developer_fusion_smoke_testing
                )

                DebugExportStatus.SimulatingIndexingFailure -> DeveloperProgress(
                    R.string.developer_simulate_failure_running
                )

                DebugExportStatus.ExportSuccess -> Text(
                    stringResource(R.string.developer_export_success),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.ExportError -> Text(
                    stringResource(R.string.developer_export_error),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.ClearSuccess -> Text(
                    stringResource(R.string.developer_clear_logs_success),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.ClearError -> Text(
                    stringResource(R.string.developer_clear_logs_error),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.ReindexSuccess -> Text(
                    stringResource(
                        R.string.developer_reindex_success,
                        reindexedCount
                    ), Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.ReindexModelUnavailable -> Text(
                    stringResource(R.string.developer_reindex_model_unavailable),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.ReindexError -> Text(
                    stringResource(R.string.developer_reindex_error),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.FusionTestSuccess -> Text(
                    stringResource(R.string.developer_fusion_smoke_success),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.FusionTestUnavailable -> Text(
                    stringResource(R.string.developer_fusion_smoke_unavailable),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.FusionTestError -> Text(
                    stringResource(R.string.developer_fusion_smoke_error),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.SimulatedIndexingFailure -> Text(
                    stringResource(R.string.developer_simulate_failure_success),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.SimulatedIndexingFailureError -> Text(
                    stringResource(R.string.developer_simulate_failure_error),
                    Modifier.padding(top = 16.dp)
                )

                DebugExportStatus.Idle -> Unit
            }
        }
    }
}

@Composable
private fun DeveloperProgress(@StringRes messageRes: Int) {
    Text(stringResource(messageRes), Modifier.padding(top = 16.dp))
    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
}
