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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
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
import ru.landilf.memeorganizer.domain.model.album.Album
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.presentation.library.AlbumCreationError

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
                val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
                MainTabsScreen(
                    onSettingsClick = { navController.navigate("settings") },
                    onSearchClick = { navController.navigate("search") },
                    onFilesSelected = { sources ->
                        importViewModel.setSources(sources); navController.navigate(
                        "import"
                    )
                    },
                    memes = memes,
                    albums = albums,
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
                    },
                    onFavorite = { meme, isFavorite ->
                        if (isFavorite && searchId != null && rank > 0 && impressionAtMs > 0) {
                            libraryViewModel.recordSearchFavorite(
                                searchId,
                                meme,
                                rank,
                                impressionAtMs
                            )
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AlbumContentsScreen(
    album: Album,
    memes: List<Meme>,
    onBack: () -> Unit,
    onMemeClick: (Meme) -> Unit
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (memes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.albums_contents_empty),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        } else {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize().padding(padding),
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(memes, key = { it.id }) { meme ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(
                            onClick = { onMemeClick(meme) },
                            onLongClick = {}
                        )
                    ) {
                        MediaThumbnail(
                            Uri.fromFile(File(context.filesDir, "memes/${meme.storedFileName}")),
                            meme.mimeType,
                            Modifier.fillMaxSize()
                        )
                    }
                }
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
    memes: List<Meme>,
    albums: List<Album>,
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
    var openedFromFavorites by rememberSaveable { mutableStateOf(false) }
    var openedFromAlbum by rememberSaveable { mutableStateOf(false) }
    var openedAlbumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumDialogVisible by rememberSaveable { mutableStateOf(false) }
    var albumPickerVisible by rememberSaveable { mutableStateOf(false) }
    var albumName by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val deleteError by libraryViewModel.deleteError.collectAsStateWithLifecycle()
    val albumCreationError by libraryViewModel.albumCreationError.collectAsStateWithLifecycle()
    val albumMemes by libraryViewModel.albumMemes.collectAsStateWithLifecycle()
    val openedAlbum = albums.firstOrNull { it.id == openedAlbumId }
    LaunchedEffect(openedAlbumId) {
        openedAlbumId?.let(libraryViewModel::openAlbum) ?: libraryViewModel.closeAlbum()
    }
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
        val viewerMemes = when {
            openedFromAlbum -> albumMemes
            openedFromFavorites -> memes.filter { it.isFavorite }
            else -> memes
        }
        LibraryViewer(viewerMemes, openedId!!, libraryViewModel, onBack = { openedId = null })
        return
    }
    if (openedAlbum != null) {
        AlbumContentsScreen(
            album = openedAlbum,
            memes = albumMemes,
            onBack = {
                openedAlbumId = null
                openedFromAlbum = false
            },
            onMemeClick = { meme ->
                openedFromAlbum = true
                openedFromFavorites = false
                openedId = meme.id
            }
        )
        return
    }
    if (albumDialogVisible) {
        val errorText = when (albumCreationError) {
            AlbumCreationError.EmptyName -> R.string.albums_create_error_empty
            AlbumCreationError.Duplicate -> R.string.albums_create_error_duplicate
            AlbumCreationError.Failed -> R.string.albums_create_error_failed
            null -> null
        }
        AlertDialog(
            onDismissRequest = {
                libraryViewModel.clearAlbumCreationError()
                albumDialogVisible = false
            },
            title = { Text(stringResource(R.string.albums_create_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = albumName,
                        onValueChange = {
                            albumName = it
                            libraryViewModel.clearAlbumCreationError()
                        },
                        label = { Text(stringResource(R.string.albums_name)) },
                        singleLine = true,
                        isError = errorText != null
                    )
                    if (errorText != null) {
                        Text(
                            stringResource(errorText),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    libraryViewModel.createAlbum(albumName) {
                        albumName = ""
                        albumDialogVisible = false
                    }
                }) { Text(stringResource(R.string.albums_create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    libraryViewModel.clearAlbumCreationError()
                    albumDialogVisible = false
                }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
    if (albumPickerVisible) AlertDialog(
        onDismissRequest = { albumPickerVisible = false },
        title = { Text(stringResource(R.string.albums_add_memes_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                lazyItems(albums, key = { it.id }) { album ->
                    TextButton(
                        onClick = {
                            libraryViewModel.addMemesToAlbum(album.id, selected) {
                                selected = emptySet()
                                albumPickerVisible = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(album.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { albumPickerVisible = false }) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
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
    val currentMemes = when (tabs[pagerState.currentPage]) {
        MainTab.Home -> memes
        MainTab.Favorites -> memes.filter { it.isFavorite }
        MainTab.Albums -> emptyList()
    }
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
                        selected = currentMemes.map { it.id }.toSet()
                    }) {
                        Icon(
                            Icons.Default.SelectAll,
                            null
                        ); Text(stringResource(R.string.import_select_all))
                    }
                    TextButton(
                        onClick = { albumPickerVisible = true },
                        enabled = albums.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Collections, null)
                        Text(stringResource(R.string.albums_add_memes))
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
            if (tabs[pagerState.currentPage] == MainTab.Albums && selected.isEmpty()) {
                FloatingActionButton(onClick = {
                    albumName = ""
                    libraryViewModel.clearAlbumCreationError()
                    albumDialogVisible = true
                }) { Icon(Icons.Default.Add, stringResource(R.string.albums_create)) }
            } else if (tabs[pagerState.currentPage] == MainTab.Home && selected.isEmpty()) {
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
            val pageMemes = when (tabs[page]) {
                MainTab.Home -> memes
                MainTab.Favorites -> memes.filter { it.isFavorite }
                MainTab.Albums -> emptyList()
            }
            if (tabs[page] == MainTab.Albums && albums.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lazyItems(albums, key = { it.id }) { album ->
                        Card(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = { openedAlbumId = album.id },
                                onLongClick = {}
                            )
                        ) {
                            Text(
                                album.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            } else if (pageMemes.isNotEmpty()) {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(pageMemes, key = { it.id }) { meme ->
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(
                                onLongClick = { selected = selected + meme.id },
                                onClick = {
                                    if (selected.isEmpty()) {
                                        openedFromFavorites = tabs[page] == MainTab.Favorites
                                        openedFromAlbum = false
                                        openedId = meme.id
                                    } else selected =
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
