package ru.landilf.memeorganizer.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus

@Composable
fun MediaIndexingBadge(status: MediaIndexingStatus?, modifier: Modifier = Modifier) {
    val appearance = when (status) {
        MediaIndexingStatus.Pending -> IndexingBadgeAppearance(
            label = R.string.indexing_status_pending,
            icon = Icons.Default.Schedule,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )

        MediaIndexingStatus.Partial -> IndexingBadgeAppearance(
            label = R.string.indexing_status_partial,
            icon = Icons.Default.CheckCircle,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        MediaIndexingStatus.Failed -> IndexingBadgeAppearance(
            label = R.string.indexing_status_failed,
            icon = Icons.Default.Error,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )

        null, MediaIndexingStatus.Done -> null
    } ?: return

    Surface(
        modifier = modifier,
        color = appearance.containerColor,
        contentColor = appearance.contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = appearance.icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp).padding(end = 3.dp)
            )
            Text(
                text = stringResource(appearance.label),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private data class IndexingBadgeAppearance(
    val label: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color
)
