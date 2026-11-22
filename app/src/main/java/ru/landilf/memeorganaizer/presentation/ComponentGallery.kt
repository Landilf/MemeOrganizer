package ru.landilf.memeorganaizer.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.landilf.memeorganaizer.presentation.theme.MemeOrganizerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentGallery(initialDark: Boolean, onBack: () -> Unit) {
    var dark by rememberSaveable { mutableStateOf(initialDark) }
    var text by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf(true) }
    MemeOrganizerTheme(darkTheme = dark) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Элементы темы") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            })
        }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Тёмная тема", modifier = Modifier.weight(1f))
                    Switch(checked = dark, onCheckedChange = { dark = it })
                }
                Text("Заголовок", style = MaterialTheme.typography.headlineMedium)
                Text("Основной текст и описание элементов", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = {}) { Text("Основная кнопка") }
                OutlinedButton(onClick = {}) { Text("Дополнительная кнопка") }
                TextButton(onClick = {}) { Text("Текстовая кнопка") }
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Поле ввода") })
                FilterChip(selected = selected, onClick = { selected = !selected }, label = { Text("Тег") })
                Card { Column(Modifier.padding(16.dp)) { Text("Карточка", style = MaterialTheme.typography.titleMedium); Text("Описание карточки") } }
                Row { Checkbox(checked = checked, onCheckedChange = { checked = it }); Text("Флажок", modifier = Modifier.padding(top = 12.dp)) }
                LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
