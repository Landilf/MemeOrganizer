package ru.landilf.memeorganizer.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.landilf.memeorganizer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(viewModel: DeveloperViewModel, onBack: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var includeMedia by rememberSaveable { mutableStateOf(true) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(if (includeMedia) "application/zip" else "application/gzip")) { uri ->
        if (uri != null) viewModel.export(uri, includeMedia)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.developer_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button)) } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp)) {
            Text(stringResource(R.string.developer_logs_description), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Row(Modifier.padding(top = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = includeMedia, onCheckedChange = { includeMedia = it })
                Text(stringResource(R.string.developer_include_media), modifier = Modifier.padding(start = 8.dp))
            }
            if (includeMedia) Text(stringResource(R.string.developer_include_media_warning), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    viewModel.clearStatus()
                    filePicker.launch(if (includeMedia) "meme-organizer-full-debug.zip" else "meme-organizer-events.jsonl.gz")
                },
                enabled = status != DebugExportStatus.Exporting,
                modifier = Modifier.padding(top = 16.dp)
            ) { Text(stringResource(R.string.developer_export_logs)) }
            when (status) {
                DebugExportStatus.Exporting -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                DebugExportStatus.Success -> Text(stringResource(R.string.developer_export_success), Modifier.padding(top = 16.dp))
                DebugExportStatus.Error -> Text(stringResource(R.string.developer_export_error), Modifier.padding(top = 16.dp))
                DebugExportStatus.Idle -> Unit
            }
        }
    }
}
