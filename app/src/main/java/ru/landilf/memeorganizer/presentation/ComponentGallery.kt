package ru.landilf.memeorganizer.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import ru.landilf.memeorganizer.R
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.landilf.memeorganizer.presentation.theme.MemeOrganizerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentGallery(initialDark: Boolean, onBack: () -> Unit) {
    var dark by rememberSaveable { mutableStateOf(initialDark) }
    var text by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf(true) }
    MemeOrganizerTheme(darkTheme = dark) {
        Scaffold(topBar = {
            TopAppBar(title = {}, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                }
            })
        }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.theme_preview_dark_theme), modifier = Modifier.weight(1f))
                    Switch(checked = dark, onCheckedChange = { dark = it })
                }
                Text(stringResource(R.string.theme_preview_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.theme_preview_body), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = {}) { Text(stringResource(R.string.theme_preview_primary_button)) }
                OutlinedButton(onClick = {}) { Text(stringResource(R.string.theme_preview_secondary_button)) }
                TextButton(onClick = {}) { Text(stringResource(R.string.theme_preview_text_button)) }
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.theme_preview_text_field)) })
                FilterChip(selected = selected, onClick = { selected = !selected }, label = { Text(stringResource(R.string.theme_preview_tag)) })
                Card { Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.theme_preview_card), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.theme_preview_card_description)) } }
                Row { Checkbox(checked = checked, onCheckedChange = { checked = it }); Text(stringResource(R.string.theme_preview_checkbox), modifier = Modifier.padding(top = 12.dp)) }
                LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
