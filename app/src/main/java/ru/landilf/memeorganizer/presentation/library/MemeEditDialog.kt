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
fun MemeEditDialog(state: EditState, suggestions: List<TagSuggestion>, onDescription: (String) -> Unit, onTags: (List<String>) -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    var input by rememberSaveable(state.memeId) { mutableStateOf("") }
    var error by rememberSaveable(state.memeId) { mutableStateOf(false) }
    var confirmClose by rememberSaveable(state.memeId) { mutableStateOf(false) }
    fun close() {
        if (state.saving) return
        if (input.isNotBlank() || (state.original != null && MemeDetails(state.description, state.tags) != state.original)) confirmClose = true else onClose()
    }
    fun addTag(name: String) {
        val value = name.trim()
        if (value.isEmpty() || value.length > MemeTextRules.MAX_TAG_LENGTH || state.tags.any { MemeTextRules.normalizeTag(it) == MemeTextRules.normalizeTag(value) }) {
            error = true
        } else {
            onTags(state.tags + value)
            input = ""
            error = false
        }
    }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text(stringResource(R.string.edit_discard_title)) },
        text = { Text(stringResource(R.string.edit_discard_message)) },
        dismissButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text(stringResource(R.string.yes_button)) } },
        confirmButton = { TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.no_button)) } }
    )
    Dialog(onDismissRequest = ::close) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.titleLarge)
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                    OutlinedTextField(value = state.description, onValueChange = { if (it.length <= MemeTextRules.MAX_DESCRIPTION_LENGTH) onDescription(it) }, label = { Text(stringResource(R.string.edit_description)) }, supportingText = { Text(stringResource(R.string.edit_description_count, state.description.length, MemeTextRules.MAX_DESCRIPTION_LENGTH)) }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = input, onValueChange = { if (it.length <= MemeTextRules.MAX_TAG_LENGTH) { input = it; error = false } }, label = { Text(stringResource(R.string.edit_tag)) }, isError = error, modifier = Modifier.weight(1f), singleLine = true, enabled = !state.saving)
                        IconButton(onClick = { addTag(input) }, enabled = !state.saving) { Icon(Icons.Default.Add, stringResource(R.string.edit_add_tag)) }
                    }
                    if (error) Text(stringResource(R.string.edit_invalid_tag), color = MaterialTheme.colorScheme.error)
                    MemeTextRules.suggestions(suggestions, input).filterNot { suggestion -> state.tags.any { MemeTextRules.normalizeTag(it) == MemeTextRules.normalizeTag(suggestion.name) } }.take(8).forEach { suggestion ->
                        TextButton(onClick = { addTag(suggestion.name) }, enabled = !state.saving) { Text(suggestion.name) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tags.forEach { tag -> InputChip(selected = true, onClick = { onTags(state.tags - tag) }, label = { Text(tag) }, trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.edit_remove_tag, tag)) }, enabled = !state.saving) }
                    }
                    if (state.error) Text(stringResource(R.string.edit_error), color = MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::close, enabled = !state.saving) { Text(stringResource(R.string.cancel_button)) }
                    TextButton(onClick = { if (input.isNotBlank()) error = true else onSave() }, enabled = !state.loading && !state.saving && state.original != null) { Text(stringResource(R.string.edit_save)) }
                }
            }
        }
    }
}
