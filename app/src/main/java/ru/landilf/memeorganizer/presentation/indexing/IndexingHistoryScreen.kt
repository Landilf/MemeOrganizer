package ru.landilf.memeorganizer.presentation.indexing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingProgress
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingRun
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexingHistoryScreen(
    progress: MediaIndexingProgress?,
    runs: List<MediaIndexingRun>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.indexing_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (runs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.indexing_history_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                progress?.let { current ->
                    item {
                        IndexingProgressCard(
                            progress = current,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                items(runs.filterNot { it.isActive }, key = MediaIndexingRun::id) { run ->
                    IndexingRunCard(
                        run = run,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IndexingProgressCard(progress: MediaIndexingProgress, modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis(), progress) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Card(modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(
                    R.string.indexing_progress_title,
                    progress.processedCount,
                    progress.totalCount
                ),
                style = MaterialTheme.typography.titleMedium
            )
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(
                    R.string.indexing_progress_elapsed,
                    formatDuration(now - progress.startedAtMillis)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = progress.estimatedRemainingMillis?.let { remaining ->
                    stringResource(R.string.indexing_progress_remaining, formatDuration(remaining))
                } ?: stringResource(R.string.indexing_progress_estimating),
                style = MaterialTheme.typography.bodyMedium
            )
            if (progress.failedCount > 0) {
                Text(
                    stringResource(R.string.indexing_progress_failed, progress.failedCount),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun IndexingRunCard(run: MediaIndexingRun, modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis(), run) {
        while (run.isActive) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val endedAtMillis = run.finishedAtMillis ?: now
    Card(modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (run.isActive) {
                    stringResource(R.string.indexing_history_running)
                } else {
                    stringResource(R.string.indexing_history_completed)
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    R.string.indexing_history_started,
                    DateFormat.getDateTimeInstance().format(Date(run.startedAtMillis))
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(
                    R.string.indexing_history_result,
                    run.processedCount,
                    run.totalCount,
                    run.failedCount
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(
                    R.string.indexing_history_duration,
                    formatDuration(endedAtMillis - run.startedAtMillis)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) / 1_000).toInt()
    return when {
        seconds < 60 -> stringResource(R.string.indexing_duration_seconds, seconds)
        seconds < 3_600 -> stringResource(
            R.string.indexing_duration_minutes_seconds,
            seconds / 60,
            seconds % 60
        )

        else -> stringResource(
            R.string.indexing_duration_hours_minutes,
            seconds / 3_600,
            (seconds % 3_600) / 60
        )
    }
}
