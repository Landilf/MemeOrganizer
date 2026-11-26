package ru.landilf.memeorganizer.presentation.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.landilf.memeorganizer.R
import ru.landilf.memeorganizer.domain.model.meme.MemeDetails
import ru.landilf.memeorganizer.domain.model.meme.MemeTextRules
import ru.landilf.memeorganizer.domain.model.meme.TagSuggestion

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemeEditDialog(
    state: EditState,
    suggestions: List<TagSuggestion>,
    onDescription: (String) -> Unit,
    onTags: (List<String>) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) = MemeDetailsEditorDialog(
    editorKey = state.memeId.toString(),
    description = state.description,
    tags = state.tags,
    original = state.original,
    loading = state.loading,
    saving = state.saving,
    error = state.error,
    suggestions = suggestions,
    onDescription = onDescription,
    onTags = onTags,
    onSave = onSave,
    onClose = onClose
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemeDetailsEditorDialog(
    editorKey: String,
    description: String,
    tags: List<String>,
    original: MemeDetails?,
    suggestions: List<TagSuggestion>,
    loading: Boolean = false,
    saving: Boolean = false,
    error: Boolean = false,
    onDescription: (String) -> Unit,
    onTags: (List<String>) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    var input by rememberSaveable(editorKey) { mutableStateOf("") }
    var inputError by rememberSaveable(editorKey) { mutableStateOf(false) }
    var confirmClose by rememberSaveable(editorKey) { mutableStateOf(false) }
    fun close() {
        if (saving) return
        if (input.isNotBlank() || (original != null && MemeDetails(description, tags) != original)
        ) confirmClose = true else onClose()
    }

    fun addTag(name: String) {
        val value = name.trim()
        if (value.isEmpty() || value.length > MemeTextRules.MAX_TAG_LENGTH || tags.any {
                MemeTextRules.normalizeTag(
                    it
                ) == MemeTextRules.normalizeTag(value)
            }) {
            inputError = true
        } else {
            onTags(tags + value)
            input = ""
            inputError = false
        }
    }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text(stringResource(R.string.edit_discard_title)) },
        text = { Text(stringResource(R.string.edit_discard_message)) },
        dismissButton = {
            TextButton(onClick = { confirmClose = false; onClose() }) {
                Text(
                    stringResource(R.string.yes_button)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                confirmClose = false
            }) { Text(stringResource(R.string.no_button)) }
        }
    )
    Dialog(onDismissRequest = ::close) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.edit_title),
                    style = MaterialTheme.typography.titleLarge
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            if (it.length <= MemeTextRules.MAX_DESCRIPTION_LENGTH) onDescription(it)
                        },
                        label = { Text(stringResource(R.string.edit_description)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.edit_description_count,
                                    description.length,
                                    MemeTextRules.MAX_DESCRIPTION_LENGTH
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving
                    )
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                if (it.length <= MemeTextRules.MAX_TAG_LENGTH) {
                                    input = it; inputError = false
                                }
                            },
                            label = { Text(stringResource(R.string.edit_tag)) },
                            isError = inputError,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !saving
                        )
                        IconButton(onClick = { addTag(input) }, enabled = !saving) {
                            Icon(
                                Icons.Default.Add,
                                stringResource(R.string.edit_add_tag)
                            )
                        }
                    }
                    if (inputError) Text(
                        stringResource(R.string.edit_invalid_tag),
                        color = MaterialTheme.colorScheme.error
                    )
                    MemeTextRules.suggestions(suggestions, input).filterNot { suggestion ->
                        tags.any {
                            MemeTextRules.normalizeTag(it) == MemeTextRules.normalizeTag(suggestion.name)
                        }
                    }.take(8).forEach { suggestion ->
                        TextButton(
                            onClick = { addTag(suggestion.name) },
                            enabled = !saving
                        ) { Text(suggestion.name) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onTags(tags - tag) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        stringResource(R.string.edit_remove_tag, tag)
                                    )
                                },
                                enabled = !saving
                            )
                        }
                    }
                    if (error) Text(
                        stringResource(R.string.edit_error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = ::close,
                        enabled = !saving
                    ) { Text(stringResource(R.string.cancel_button)) }
                    TextButton(
                        onClick = { if (input.isNotBlank()) inputError = true else onSave() },
                        enabled = !loading && !saving && original != null
                    ) { Text(stringResource(R.string.edit_save)) }
                }
            }
        }
    }
}
