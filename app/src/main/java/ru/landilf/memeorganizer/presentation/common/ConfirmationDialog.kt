package ru.landilf.memeorganizer.presentation.common

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.landilf.memeorganizer.R

@Composable
fun ConfirmationDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        dismissButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.yes_button))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.no_button))
            }
        }
    )
}
