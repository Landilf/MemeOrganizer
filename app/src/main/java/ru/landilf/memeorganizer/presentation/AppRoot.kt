package ru.landilf.memeorganizer.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import ru.landilf.memeorganizer.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.landilf.memeorganizer.BuildConfig
import ru.landilf.memeorganizer.presentation.theme.MemeOrganizerTheme
import kotlinx.coroutines.launch
import java.io.File
import ru.landilf.memeorganizer.presentation.common.MediaThumbnail
import ru.landilf.memeorganizer.presentation.importing.ImportSummary
import ru.landilf.memeorganizer.presentation.importing.asText
import ru.landilf.memeorganizer.presentation.importing.ImportScreen
import ru.landilf.memeorganizer.presentation.importing.ImportViewModel
import ru.landilf.memeorganizer.presentation.library.LibraryViewModel

private enum class MainTab(val title: Int, val icon: ImageVector, val message: Int) {
    Albums(R.string.navigation_albums, Icons.Default.Collections, R.string.albums_empty),
    Home(R.string.navigation_home, Icons.Default.Home, R.string.library_empty),
    Favorites(R.string.navigation_favorites, Icons.Default.Favorite, R.string.favorites_empty)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val importViewModel: ImportViewModel = hiltViewModel()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    MemeOrganizerTheme {
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                val libraryViewModel: LibraryViewModel = hiltViewModel()
                val memes by libraryViewModel.memes.collectAsStateWithLifecycle()
                MainTabsScreen(
                    onSettingsClick = { navController.navigate("settings") },
                    onFilesSelected = { sources -> importViewModel.setSources(sources); navController.navigate("import") },
                    memes = memes,
                    summary = importState.summary,
                    onSummaryShown = importViewModel::clearSummary
                )
            }
            composable("settings") {
                Scaffold(topBar = {
                    TopAppBar(title = {}, navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                        }
                    })
                }) { padding ->
                    Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                        Text(stringResource(R.string.settings_empty))
                        if (BuildConfig.DEBUG) {
                            TextButton(onClick = { navController.navigate("components") }) {
                                Text(stringResource(R.string.settings_theme_components))
                            }
                        }
                    }
                }
            }
            composable("components") {
                ComponentGallery(initialDark = isSystemInDarkTheme(), onBack = { navController.popBackStack() })
            }
            composable("import") {
                ImportScreen(importState, importViewModel, onClose = {
                    if (navController.currentDestination?.route == "import") {
                        navController.popBackStack("library", inclusive = false)
                    }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabsScreen(
    onSettingsClick: () -> Unit,
    onFilesSelected: (List<String>) -> Unit,
    memes: List<ru.landilf.memeorganizer.domain.model.meme.Meme>,
    summary: ImportSummary?,
    onSummaryShown: () -> Unit
) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(initialPage = MainTab.Home.ordinal, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    fun acceptUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        onFilesSelected(uris.map(Uri::toString))
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { acceptUris(it) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { acceptUris(it) }
    val summaryText = summary?.asText()
    LaunchedEffect(summaryText) {
        if (summaryText != null) { snackbar.showSnackbar(summaryText); onSummaryShown() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {}, actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_open))
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
                        label = { Text(stringResource(tab.title)) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tabs[pagerState.currentPage] == MainTab.Home) {
                Box {
                    FloatingActionButton(onClick = { menuExpanded = !menuExpanded }) { Icon(Icons.Default.Add, stringResource(R.string.library_add)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.library_import_gallery)) }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) }, onClick = {
                            menuExpanded = false
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.library_import_files)) }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = {
                            menuExpanded = false
                            filePicker.launch(arrayOf("image/*", "video/*"))
                        })
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            if (tabs[page] == MainTab.Home && memes.isNotEmpty()) {
                LazyVerticalGrid(modifier = Modifier.fillMaxSize(), columns = GridCells.Fixed(3), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(memes, key = { it.id }) { meme ->
                        MediaThumbnail(Uri.fromFile(File(context.filesDir, "memes/${meme.storedFileName}")), meme.mimeType, Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
            } else Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(tabs[page].message), style = MaterialTheme.typography.titleLarge)
                if (tabs[page] == MainTab.Home) {
                    Text(stringResource(R.string.library_add_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
