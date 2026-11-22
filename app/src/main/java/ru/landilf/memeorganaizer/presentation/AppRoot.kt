package ru.landilf.memeorganaizer.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.landilf.memeorganaizer.BuildConfig
import ru.landilf.memeorganaizer.presentation.theme.MemeOrganizerTheme
import kotlinx.coroutines.launch

private enum class MainTab(val title: String, val icon: ImageVector, val message: String) {
    Albums("Альбомы", Icons.Default.Collections, "Здесь будут ваши альбомы"),
    Home("Главная", Icons.Default.Home, "Здесь будут ваши мемы"),
    Favorites("Избранное", Icons.Default.Favorite, "Здесь будут избранные мемы")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    MemeOrganizerTheme {
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                MainTabsScreen(onSettingsClick = { navController.navigate("settings") })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabsScreen(onSettingsClick: () -> Unit) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(initialPage = MainTab.Home.ordinal, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(tabs[pagerState.currentPage].title) }, actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                }
            })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(tabs[page].message, style = MaterialTheme.typography.titleLarge)
                if (tabs[page] == MainTab.Home) {
                    Text("Добавление мемов появится позже", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
