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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
import ru.landilf.memeorganizer.presentation.library.LibraryViewer
import ru.landilf.memeorganizer.presentation.settings.ModelSettingsViewModel
import ru.landilf.memeorganizer.presentation.settings.ModelStatus
import ru.landilf.memeorganizer.presentation.settings.ModelUpdateDialog
import ru.landilf.memeorganizer.presentation.settings.ModelUpdateNotice
import ru.landilf.memeorganizer.presentation.settings.ModelUpdateViewModel
import ru.landilf.memeorganizer.presentation.settings.DeveloperScreen
import ru.landilf.memeorganizer.presentation.settings.DeveloperViewModel
import ru.landilf.memeorganizer.presentation.search.SearchScreen
import ru.landilf.memeorganizer.presentation.search.SearchViewModel

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
    val modelUpdateViewModel: ModelUpdateViewModel = hiltViewModel()
    val modelUpdateState by modelUpdateViewModel.state.collectAsStateWithLifecycle()
    MemeOrganizerTheme {
        ModelUpdateDialog(
            modelUpdateState,
            modelUpdateViewModel::install,
            modelUpdateViewModel::postpone
        )
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                val libraryViewModel: LibraryViewModel = hiltViewModel()
                val memes by libraryViewModel.memes.collectAsStateWithLifecycle()
                MainTabsScreen(
                    onSettingsClick = { navController.navigate("settings") },
                    onSearchClick = { navController.navigate("search") },
                    onFilesSelected = { sources ->
                        importViewModel.setSources(sources); navController.navigate(
                        "import"
                    )
                    },
                    memes = memes,
                    libraryViewModel = libraryViewModel,
                    summary = importState.summary,
                    onSummaryShown = importViewModel::clearSummary
                )
            }
            composable("search") {
                val searchViewModel: SearchViewModel = hiltViewModel()
                val searchState by searchViewModel.state.collectAsStateWithLifecycle()
                SearchScreen(
                    state = searchState,
                    onQueryChanged = searchViewModel::updateQuery,
                    onBack = {
                        searchViewModel.abandonCurrentSearch()
                        navController.popBackStack()
                    },
                    onMemeClick = { result ->
                        searchViewModel.open(result)
                        navController.navigate("viewer/${result.meme.id}?searchId=${result.searchId}&rank=${result.rank}&impressionAtMs=${result.impressionAtMs}")
                    }
                )
            }
            composable(
                route = "viewer/{memeId}?searchId={searchId}&rank={rank}&impressionAtMs={impressionAtMs}",
                arguments = listOf(
                    navArgument("searchId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("rank") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("impressionAtMs") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { entry ->
                val libraryViewModel: LibraryViewModel = hiltViewModel()
                val memes by libraryViewModel.memes.collectAsStateWithLifecycle()
                val memeId = entry.arguments?.getString("memeId")?.toLongOrNull()
                val searchId = entry.arguments?.getString("searchId")
                val rank = entry.arguments?.getInt("rank") ?: -1
                val impressionAtMs = entry.arguments?.getLong("impressionAtMs") ?: -1L
                if (memeId != null) LibraryViewer(
                    memes = memes,
                    initialId = memeId,
                    viewModel = libraryViewModel,
                    onBack = { navController.popBackStack() },
                    onCopy = { meme ->
                        if (searchId != null && rank > 0 && impressionAtMs > 0) {
                            libraryViewModel.recordSearchCopy(searchId, meme, rank, impressionAtMs)
                        }
                    }
                )
            }
            composable("settings") {
                val modelViewModel: ModelSettingsViewModel = hiltViewModel()
                val modelStatus by modelViewModel.status.collectAsStateWithLifecycle()
                val modelPicker =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) modelViewModel.install(uri)
                    }
                Scaffold(topBar = {
                    TopAppBar(title = {}, navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_button)
                            )
                        }
                    })
                }) { padding ->
                    Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                        Text(
                            stringResource(R.string.settings_text_model),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(
                                when (modelStatus) {
                                    ModelStatus.Checking -> R.string.settings_model_checking
                                    ModelStatus.NotInstalled -> R.string.settings_model_not_installed
                                    ModelStatus.Installing -> R.string.settings_model_installing
                                    ModelStatus.Installed -> R.string.settings_model_installed
                                    ModelStatus.Error -> R.string.settings_model_error
                                }
                            )
                        )
                        if (modelStatus == ModelStatus.Installing || modelStatus == ModelStatus.Checking) LinearProgressIndicator(
                            Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                modelPicker.launch(
                                    arrayOf(
                                        "application/gzip",
                                        "application/x-gzip",
                                        "application/octet-stream",
                                        "application/x-tar",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = modelStatus != ModelStatus.Installing && modelStatus != ModelStatus.Checking
                        ) {
                            Text(stringResource(R.string.settings_model_choose))
                        }
                        TextButton(
                            onClick = { modelUpdateViewModel.checkForUpdates() },
                            enabled = !modelUpdateState.checking && modelUpdateState.progress == null
                        ) {
                            Text(stringResource(R.string.settings_model_check_update))
                        }
                        val updateNotice = when (modelUpdateState.notice) {
                            ModelUpdateNotice.None -> null
                            ModelUpdateNotice.UpToDate -> R.string.settings_model_update_up_to_date
                            ModelUpdateNotice.Offline -> R.string.settings_model_update_offline
                            ModelUpdateNotice.Installed -> R.string.settings_model_update_installed
                            ModelUpdateNotice.Error -> R.string.settings_model_update_error
                        }
                        if (modelUpdateState.checking) Text(stringResource(R.string.settings_model_update_checking))
                        if (updateNotice != null) Text(stringResource(updateNotice))
                        if (BuildConfig.DEBUG) {
                            TextButton(onClick = { navController.navigate("developer") }) {
                                Text(stringResource(R.string.settings_developer))
                            }
                            TextButton(onClick = { navController.navigate("components") }) {
                                Text(stringResource(R.string.settings_theme_components))
                            }
                        }
                    }
                }
            }
            composable("components") {
                ComponentGallery(
                    initialDark = isSystemInDarkTheme(),
                    onBack = { navController.popBackStack() })
            }
            composable("developer") {
                val developerViewModel: DeveloperViewModel = hiltViewModel()
                DeveloperScreen(developerViewModel) { navController.popBackStack() }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MainTabsScreen(
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFilesSelected: (List<String>) -> Unit,
    memes: List<ru.landilf.memeorganizer.domain.model.meme.Meme>,
    libraryViewModel: LibraryViewModel,
    summary: ImportSummary?,
    onSummaryShown: () -> Unit
) {
    val tabs = MainTab.entries
    val pagerState =
        rememberPagerState(initialPage = MainTab.Home.ordinal, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var openedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val deleteError by libraryViewModel.deleteError.collectAsStateWithLifecycle()
    if (deleteError) AlertDialog(
        onDismissRequest = libraryViewModel::clearDeleteError,
        title = { Text(stringResource(R.string.library_delete_error)) },
        confirmButton = {
            TextButton(onClick = libraryViewModel::clearDeleteError) {
                Text(stringResource(R.string.ok_button))
            }
        })
    if (openedId != null) {
        LaunchedEffect(openedId) { snackbar.currentSnackbarData?.dismiss() }
        LibraryViewer(memes, openedId!!, libraryViewModel, onBack = { openedId = null })
        return
    }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.library_delete_title)) },
        text = { Text(stringResource(R.string.library_delete_message)) },
        dismissButton = {
            TextButton(onClick = {
                libraryViewModel.delete(selected); selected = emptySet(); confirmDelete = false
            }) { Text(stringResource(R.string.yes_button)) }
        },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
            }) { Text(stringResource(R.string.no_button)) }
        }
    )
    fun acceptUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        onFilesSelected(uris.map(Uri::toString))
    }

    val galleryPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
            acceptUris(it)
        }
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            acceptUris(it)
        }
    val summaryText = summary?.asText()
    LaunchedEffect(summaryText) {
        if (summaryText != null) {
            onSummaryShown()
            scope.launch { snackbar.showSnackbar(summaryText) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {}, actions = {
                if (selected.isNotEmpty()) Text(
                    stringResource(
                        R.string.import_selected_count,
                        selected.size
                    ), Modifier.padding(end = 20.dp)
                )
                else {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_open)
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_open)
                        )
                    }
                }
            })
        },
        bottomBar = {
            if (selected.isNotEmpty()) BottomAppBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = {
                        selected = memes.map { it.id }.toSet()
                    }) {
                        Icon(
                            Icons.Default.SelectAll,
                            null
                        ); Text(stringResource(R.string.import_select_all))
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Default.Delete,
                            null
                        ); Text(stringResource(R.string.import_delete))
                    }
                }
            } else NavigationBar {
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
            if (tabs[pagerState.currentPage] == MainTab.Home && selected.isEmpty()) {
                Box {
                    FloatingActionButton(onClick = {
                        menuExpanded = !menuExpanded
                    }) { Icon(Icons.Default.Add, stringResource(R.string.library_add)) }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_import_gallery)) },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                            onClick = {
                                menuExpanded = false
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_import_files)) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                            onClick = {
                                menuExpanded = false
                                filePicker.launch(arrayOf("image/*", "video/*"))
                            })
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = selected.isEmpty(),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            if (tabs[page] == MainTab.Home && memes.isNotEmpty()) {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(memes, key = { it.id }) { meme ->
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(
                                onLongClick = { selected = selected + meme.id },
                                onClick = {
                                    if (selected.isEmpty()) openedId = meme.id else selected =
                                        if (meme.id in selected) selected - meme.id else selected + meme.id
                                })
                        ) {
                            MediaThumbnail(
                                Uri.fromFile(
                                    File(
                                        context.filesDir,
                                        "memes/${meme.storedFileName}"
                                    )
                                ), meme.mimeType, Modifier.fillMaxSize()
                            )
                            if (meme.id in selected) Checkbox(
                                true,
                                { selected = selected - meme.id },
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            } else Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(tabs[page].message),
                    style = MaterialTheme.typography.titleLarge
                )
                if (tabs[page] == MainTab.Home) {
                    Text(
                        stringResource(R.string.library_add_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
