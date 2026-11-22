package ru.landilf.memeorganaizer.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.landilf.memeorganaizer.BuildConfig
import ru.landilf.memeorganaizer.presentation.theme.MemeOrganizerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    MemeOrganizerTheme {
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Meme Organizer") }, actions = {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    })
                }) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Здесь будут ваши мемы", style = MaterialTheme.typography.titleLarge)
                        Text("Добавление мемов появится позже", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            composable("settings") {
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Настройки") }, navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    })
                }) { padding ->
                    Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                        Text("Настройки появятся позже")
                        if (BuildConfig.DEBUG) {
                            TextButton(onClick = { navController.navigate("components") }) {
                                Text("Посмотреть элементы темы")
                            }
                        }
                    }
                }
            }
            composable("components") {
                ComponentGallery(initialDark = isSystemInDarkTheme(), onBack = { navController.popBackStack() })
            }
        }
    }
}
