package ru.landilf.memeorganizer.presentation.importing

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.landilf.memeorganizer.R

@Composable
fun ImportSummary.asText(): String = when (this) {
    is ImportSummary.Completed -> stringResource(
        R.string.import_summary,
        imported,
        duplicates,
        failed
    )

    is ImportSummary.Interrupted -> stringResource(R.string.import_interrupted, imported)
}
