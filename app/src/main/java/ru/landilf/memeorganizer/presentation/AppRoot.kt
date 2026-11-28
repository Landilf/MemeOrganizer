package ru.landilf.memeorganizer.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
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
import kotlinx.coroutines.flow.Flow
import java.io.File
import ru.landilf.memeorganizer.presentation.common.MediaThumbnail
import ru.landilf.memeorganizer.presentation.common.MediaIndexingBadge
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
import ru.landilf.memeorganizer.presentation.settings.AppSettingsViewModel
import ru.landilf.memeorganizer.presentation.settings.LibraryBackupSection
import ru.landilf.memeorganizer.presentation.settings.LibraryBackupViewModel
import ru.landilf.memeorganizer.presentation.settings.AutomaticBackupSection
import ru.landilf.memeorganizer.presentation.settings.AutomaticBackupViewModel
import ru.landilf.memeorganizer.presentation.search.SearchScreen
import ru.landilf.memeorganizer.presentation.search.SearchViewModel
import ru.landilf.memeorganizer.domain.model.album.Album
import ru.landilf.memeorganizer.domain.model.meme.Meme
import ru.landilf.memeorganizer.domain.model.meme.MemeMediaType
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingStatus
import ru.landilf.memeorganizer.domain.model.ml.MediaIndexingProgress
import ru.landilf.memeorganizer.presentation.library.AlbumNameError
import ru.landilf.memeorganizer.presentation.library.AlbumNameDialog
import ru.landilf.memeorganizer.presentation.library.AlbumPickerDialog
import ru.landilf.memeorganizer.presentation.library.DeleteMemesConfirmationDialog
import ru.landilf.memeorganizer.presentation.album.AlbumContentsScreen
import ru.landilf.memeorganizer.presentation.indexing.IndexingHistoryScreen
import ru.landilf.memeorganizer.domain.model.settings.AppSettings
import ru.landilf.memeorganizer.domain.model.settings.LibrarySort
import ru.landilf.memeorganizer.domain.model.settings.SortDirection

private enum class MainTab(val title: Int, val icon: ImageVector, val message: Int) {
    Albums(R.string.navigation_albums, Icons.Default.Collections, R.string.albums_empty),
    Home(R.string.navigation_home, Icons.Default.Home, R.string.library_empty),
    Favorites(R.string.navigation_favorites, Icons.Default.Favorite, R.string.favorites_empty)
}

private enum class SystemAlbum(@StringRes val title: Int) {
    RecentlyAdded(R.string.albums_recently_added),
    RecentlyUsed(R.string.albums_recently_used)
}

private const val SEARCH_CANDIDATE_IDS_KEY = "search_candidate_ids"
private const val RECENT_ALBUM_PERIOD_MILLIS = 30L * 24 * 60 * 60 * 1_000

private fun List<Meme>.sortedByPreference(
    sort: LibrarySort,
    direction: SortDirection?
): List<Meme> {
    val defaultOrder = when (sort) {
        LibrarySort.AddedAt -> sortedWith(
            compareByDescending<Meme> { it.addedAt }.thenByDescending { it.id }
        )
        LibrarySort.UsageCount -> sortedWith(
            compareByDescending<Meme> { it.usageCount }
                .thenByDescending { it.lastUsedAt }
                .thenByDescending { it.addedAt }
        )
        LibrarySort.MediaType -> sortedWith(
            compareBy<Meme> { it.mediaType.sortOrder }.thenByDescending { it.addedAt }
        )
    }
    return if (direction == SortDirection.Ascending) defaultOrder.reversed() else defaultOrder
}

private val MemeMediaType.sortOrder: Int
    get() = when (this) {
        MemeMediaType.Image -> 0
        MemeMediaType.Gif -> 1
        MemeMediaType.Video -> 2
    }

private val LibrarySort.label: Int
    get() = when (this) {
        LibrarySort.AddedAt -> R.string.library_sort_added_at
        LibrarySort.UsageCount -> R.string.library_sort_usage_count
        LibrarySort.MediaType -> R.string.library_sort_media_type
    }

private fun SortDirection?.next(): SortDirection = when (this) {
    null, SortDirection.Ascending -> SortDirection.Descending
    SortDirection.Descending -> SortDirection.Ascending
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(sharedMediaSources: Flow<List<String>> = kotlinx.coroutines.flow.emptyFlow()) {
    val navController = rememberNavController()
    val importViewModel: ImportViewModel = hiltViewModel()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val modelUpdateViewModel: ModelUpdateViewModel = hiltViewModel()
    val modelUpdateState by modelUpdateViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(sharedMediaSources) {
        sharedMediaSources.collect { sources ->
            if (sources.isEmpty()) return@collect
            importViewModel.setSources(sources)
            navController.navigate("import") { launchSingleTop = true }
        }
    }
    MemeOrganizerTheme {
        ModelUpdateDialog(
            modelUpdateState,
            modelUpdateViewModel::install,
            modelUpdateViewModel::postpone
        )
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                val libraryViewModel: LibraryViewModel = hiltViewModel()
                val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
                val memes by libraryViewModel.memes.collectAsStateWithLifecycle()
                val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
                val indexingStatuses by libraryViewModel.indexingStatuses.collectAsStateWithLifecycle()
                val manualIndexingInProgress by libraryViewModel.manualIndexingInProgress
                    .collectAsStateWithLifecycle()
                val indexingProgress by libraryViewModel.indexingProgress.collectAsStateWithLifecycle()
                val appSettings by appSettingsViewModel.settings.collectAsStateWithLifecycle()
                MainTabsScreen(
                    onSettingsClick = { navController.navigate("settings") },
                    onSearchClick = { navController.navigate("search") },
                    onIndexingHistoryClick = { navController.navigate("indexingHistory") },
                    onFilesSelected = { sources ->
                        importViewModel.setSources(sources); navController.navigate(
                        "import"
                    )
                    },
                    memes = memes,
                    albums = albums,
                    indexingStatuses = indexingStatuses,
                    manualIndexingInProgress = manualIndexingInProgress,
                    indexingProgress = indexingProgress,
                    appSettings = appSettings,
                    libraryViewModel = libraryViewModel,
                    onLibrarySortSelected = appSettingsViewModel::setLibrarySort,
                    onLibrarySortDirectionSelected = appSettingsViewModel::setLibrarySortDirection,
                    summary = importState.summary,
                    onSummaryShown = importViewModel::clearSummary
                )
            }
            composable("indexingHistory") {
                val libraryViewModel: LibraryViewModel = hiltViewModel()
                val runs by libraryViewModel.indexingRuns.collectAsStateWithLifecycle()
                val progress by libraryViewModel.indexingProgress.collectAsStateWithLifecycle()
                val queuePaused by libraryViewModel.indexingQueuePaused.collectAsStateWithLifecycle()
                IndexingHistoryScreen(
                    progress = progress,
                    runs = runs,
                    queuePaused = queuePaused,
                    onPauseQueue = libraryViewModel::pauseIndexingQueue,
                    onResumeQueue = libraryViewModel::resumeIndexingQueue,
                    onBack = { navController.popBackStack() }
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
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                SEARCH_CANDIDATE_IDS_KEY,
                                ArrayList(searchState.results.map { it.meme.id })
                            )
                        navController.navigate(
                            "viewer/${result.meme.id}?searchId=${result.searchId}" +
                                "&rank=${result.rank}&impressionAtMs=${result.impressionAtMs}"
                        )
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
                val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
                val memeId = entry.arguments?.getString("memeId")?.toLongOrNull()
                val searchId = entry.arguments?.getString("searchId")
                val rank = entry.arguments?.getInt("rank") ?: -1
                val impressionAtMs = entry.arguments?.getLong("impressionAtMs") ?: -1L
                val searchCandidateIds = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<ArrayList<Long>>(SEARCH_CANDIDATE_IDS_KEY)
                val memesById = memes.associateBy(Meme::id)
                val candidateMemes = searchCandidateIds?.mapNotNull { candidateId ->
                    memesById[candidateId]
                }
                val searchRankByMemeId = searchCandidateIds
                    ?.mapIndexed { index, candidateId -> candidateId to index + 1 }
                    ?.toMap()
                if (memeId != null) LibraryViewer(
                    memes = candidateMemes ?: memes,
                    albums = albums,
                    initialId = memeId,
                    viewModel = libraryViewModel,
                    onBack = { navController.popBackStack() },
                    onCopy = { meme ->
                        val actionRank = searchRankByMemeId?.get(meme.id) ?: rank
                        if (searchId != null && actionRank > 0 && impressionAtMs > 0) {
                            libraryViewModel.recordSearchCopy(
                                searchId,
                                meme,
                                actionRank,
                                impressionAtMs
                            )
                        }
                    },
                    onShare = { meme ->
                        val actionRank = searchRankByMemeId?.get(meme.id) ?: rank
                        if (searchId != null && actionRank > 0 && impressionAtMs > 0) {
                            libraryViewModel.recordSearchShare(
                                searchId,
                                meme,
                                actionRank,
                                impressionAtMs
                            )
                        }
                    },
                    onFavorite = { meme, isFavorite ->
                        val actionRank = searchRankByMemeId?.get(meme.id) ?: rank
                        if (isFavorite && searchId != null && actionRank > 0 && impressionAtMs > 0) {
                            libraryViewModel.recordSearchFavorite(
                                searchId,
                                meme,
                                actionRank,
                                impressionAtMs
                            )
                        }
                    }
                )
            }
            composable("settings") {
                val modelViewModel: ModelSettingsViewModel = hiltViewModel()
                val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
                val libraryBackupViewModel: LibraryBackupViewModel = hiltViewModel()
                val automaticBackupViewModel: AutomaticBackupViewModel = hiltViewModel()
                val modelStatus by modelViewModel.status.collectAsStateWithLifecycle()
                val appSettings by appSettingsViewModel.settings.collectAsStateWithLifecycle()
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
                    Column(
                        modifier = Modifier.padding(padding).padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                                    ModelStatus.InsufficientStorage -> R.string.settings_model_storage_error
                                    ModelStatus.Error -> R.string.settings_model_error
                                }
                            )
                        )
                        if (modelStatus == ModelStatus.Installing || modelStatus == ModelStatus.Checking) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
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
                            ModelUpdateNotice.InsufficientStorage -> R.string.settings_model_storage_error
                            ModelUpdateNotice.Error -> R.string.settings_model_update_error
                        }
                        if (modelUpdateState.checking) Text(stringResource(R.string.settings_model_update_checking))
                        if (updateNotice != null) Text(stringResource(updateNotice))
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        LibraryBackupSection(libraryBackupViewModel)
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        AutomaticBackupSection(automaticBackupViewModel)
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        Text(
                            stringResource(R.string.settings_system_albums),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.albums_recently_added),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = appSettings.showRecentlyAddedAlbum,
                                onCheckedChange = appSettingsViewModel::setRecentlyAddedAlbumVisible
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.albums_recently_used),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = appSettings.showRecentlyUsedAlbum,
                                onCheckedChange = appSettingsViewModel::setRecentlyUsedAlbumVisible
                            )
                        }
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
    onIndexingHistoryClick: () -> Unit,
    onFilesSelected: (List<String>) -> Unit,
    memes: List<Meme>,
    albums: List<Album>,
    indexingStatuses: Map<Long, MediaIndexingStatus>,
    manualIndexingInProgress: Boolean,
    indexingProgress: MediaIndexingProgress?,
    appSettings: AppSettings,
    libraryViewModel: LibraryViewModel,
    onLibrarySortSelected: (LibrarySort) -> Unit,
    onLibrarySortDirectionSelected: (SortDirection) -> Unit,
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
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var openedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openedFromFavorites by rememberSaveable { mutableStateOf(false) }
    var openedFromAlbum by rememberSaveable { mutableStateOf(false) }
    var openedAlbumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openedSystemAlbum by rememberSaveable { mutableStateOf<SystemAlbum?>(null) }
    var albumDialogVisible by rememberSaveable { mutableStateOf(false) }
    var albumPickerVisible by rememberSaveable { mutableStateOf(false) }
    var albumName by rememberSaveable { mutableStateOf("") }
    var albumOptionsId by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumRenameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamedAlbumName by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var manualIndexingOnlyFailed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val deleteError by libraryViewModel.deleteError.collectAsStateWithLifecycle()
    val indexingFailure by libraryViewModel.indexingFailure.collectAsStateWithLifecycle()
    val albumCreationError by libraryViewModel.albumCreationError.collectAsStateWithLifecycle()
    val albumRenameError by libraryViewModel.albumRenameError.collectAsStateWithLifecycle()
    val albumMemes by libraryViewModel.albumMemes.collectAsStateWithLifecycle()
    val openedAlbum = albums.firstOrNull { it.id == openedAlbumId }
    val sortedMemes = remember(memes, appSettings.librarySort, appSettings.librarySortDirection) {
        memes.sortedByPreference(appSettings.librarySort, appSettings.librarySortDirection)
    }
    val visibleSystemAlbums = SystemAlbum.entries.filter { album ->
        when (album) {
            SystemAlbum.RecentlyAdded -> appSettings.showRecentlyAddedAlbum
            SystemAlbum.RecentlyUsed -> appSettings.showRecentlyUsedAlbum
        }
    }
    val recentStartMillis = System.currentTimeMillis() - RECENT_ALBUM_PERIOD_MILLIS
    val systemAlbumMemes = when (openedSystemAlbum) {
        SystemAlbum.RecentlyAdded -> memes.filter { it.addedAt.toEpochMilli() >= recentStartMillis }
        SystemAlbum.RecentlyUsed -> memes.filter { meme ->
            meme.lastUsedAt?.toEpochMilli()?.let { it >= recentStartMillis } == true
        }.sortedByDescending { it.lastUsedAt }
        null -> emptyList()
    }
    val albumWithOptions = albums.firstOrNull { it.id == albumOptionsId }
    val albumToRename = albums.firstOrNull { it.id == albumRenameId }
    val albumToDelete = albums.firstOrNull { it.id == albumDeleteId }
    val failedIndexingCount = indexingStatuses.count { it.value == MediaIndexingStatus.Failed }
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
    indexingFailure?.let { failure ->
        AlertDialog(
            onDismissRequest = libraryViewModel::dismissIndexingFailure,
            title = { Text(stringResource(R.string.indexing_failure_title)) },
            text = {
                Text(stringResource(R.string.indexing_failure_message, failure.reason))
            },
            confirmButton = {
                TextButton(onClick = libraryViewModel::retryIndexingFailure) {
                    Text(stringResource(R.string.indexing_failure_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = libraryViewModel::dismissIndexingFailure) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
    if (openedId != null) {
        LaunchedEffect(openedId) { snackbar.currentSnackbarData?.dismiss() }
        val viewerMemes = when {
            openedFromAlbum -> albumMemes
            openedSystemAlbum != null -> systemAlbumMemes
            openedFromFavorites -> sortedMemes.filter { it.isFavorite }
            else -> sortedMemes
        }
        LibraryViewer(
            memes = viewerMemes,
            initialId = openedId!!,
            viewModel = libraryViewModel,
            onBack = { openedId = null },
            albums = albums,
            currentAlbumId = if (openedFromAlbum) openedAlbumId else null
        )
        return
    }
    if (openedAlbum != null) {
        AlbumContentsScreen(
            title = openedAlbum.name,
            memes = albumMemes,
            indexingStatuses = indexingStatuses,
            onBack = {
                openedAlbumId = null
                openedFromAlbum = false
            },
            onMemeClick = { meme ->
                openedFromAlbum = true
                openedFromFavorites = false
                openedId = meme.id
            },
            onIndexingFailure = libraryViewModel::openIndexingFailure,
            onRemoveMemes = { memeIds ->
                libraryViewModel.removeMemesFromAlbum(openedAlbum.id, memeIds)
            }
        )
        return
    }
    if (openedSystemAlbum != null) {
        AlbumContentsScreen(
            title = stringResource(openedSystemAlbum!!.title),
            memes = systemAlbumMemes,
            indexingStatuses = indexingStatuses,
            onBack = {
                openedSystemAlbum = null
                openedFromAlbum = false
            },
            onMemeClick = { meme ->
                openedFromAlbum = false
                openedFromFavorites = false
                openedId = meme.id
            },
            onIndexingFailure = libraryViewModel::openIndexingFailure
        )
        return
    }
    if (albumDialogVisible) {
        val errorText = when (albumCreationError) {
            AlbumNameError.EmptyName -> R.string.albums_create_error_empty
            AlbumNameError.Duplicate -> R.string.albums_create_error_duplicate
            AlbumNameError.Failed -> R.string.albums_create_error_failed
            null -> null
        }
        AlbumNameDialog(
            title = R.string.albums_create_title,
            confirmLabel = R.string.albums_create,
            name = albumName,
            errorText = errorText,
            onNameChange = {
                albumName = it
                libraryViewModel.clearAlbumCreationError()
            },
            onConfirm = {
                libraryViewModel.createAlbum(albumName) {
                    albumName = ""
                    albumDialogVisible = false
                }
            },
            onDismiss = {
                libraryViewModel.clearAlbumCreationError()
                albumDialogVisible = false
            }
        )
    }
    if (albumWithOptions != null) {
        AlertDialog(
            onDismissRequest = { albumOptionsId = null },
            title = { Text(stringResource(R.string.albums_manage)) },
            text = { Text(albumWithOptions.name) },
            confirmButton = {
                TextButton(onClick = {
                    renamedAlbumName = albumWithOptions.name
                    libraryViewModel.clearAlbumRenameError()
                    albumRenameId = albumWithOptions.id
                    albumOptionsId = null
                }) { Text(stringResource(R.string.albums_rename)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    albumDeleteId = albumWithOptions.id
                    albumOptionsId = null
                }) { Text(stringResource(R.string.albums_delete)) }
            }
        )
    }
    if (albumToRename != null) {
        val errorText = when (albumRenameError) {
            AlbumNameError.EmptyName -> R.string.albums_rename_error_empty
            AlbumNameError.Duplicate -> R.string.albums_rename_error_duplicate
            AlbumNameError.Failed -> R.string.albums_rename_error_failed
            null -> null
        }
        AlbumNameDialog(
            title = R.string.albums_rename_title,
            confirmLabel = R.string.albums_rename,
            name = renamedAlbumName,
            errorText = errorText,
            onNameChange = {
                renamedAlbumName = it
                libraryViewModel.clearAlbumRenameError()
            },
            onConfirm = {
                libraryViewModel.renameAlbum(albumToRename.id, renamedAlbumName) {
                    albumRenameId = null
                }
            },
            onDismiss = {
                libraryViewModel.clearAlbumRenameError()
                albumRenameId = null
            }
        )
    }
    if (albumToDelete != null) {
        AlertDialog(
            onDismissRequest = { albumDeleteId = null },
            title = { Text(stringResource(R.string.albums_delete_title)) },
            text = { Text(stringResource(R.string.albums_delete_message)) },
            confirmButton = {
                TextButton(onClick = { albumDeleteId = null }) {
                    Text(stringResource(R.string.no_button))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    libraryViewModel.deleteAlbum(albumToDelete.id) {
                        if (openedAlbumId == albumToDelete.id) openedAlbumId = null
                        albumDeleteId = null
                    }
                }) { Text(stringResource(R.string.yes_button)) }
            }
        )
    }
    manualIndexingOnlyFailed?.let { onlyFailed ->
        val affectedCount = if (onlyFailed) failedIndexingCount else memes.size
        AlertDialog(
            onDismissRequest = { manualIndexingOnlyFailed = null },
            title = {
                Text(
                    stringResource(
                        if (onlyFailed) {
                            R.string.library_retry_failed_indexing_title
                        } else {
                            R.string.library_reprocess_all_title
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (onlyFailed) {
                            R.string.library_retry_failed_indexing_message
                        } else {
                            R.string.library_reprocess_all_message
                        },
                        affectedCount
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manualIndexingOnlyFailed = null
                    libraryViewModel.scheduleManualIndexing(onlyFailed) { scheduledCount ->
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(
                                    R.string.library_indexing_scheduled,
                                    scheduledCount
                                )
                            )
                        }
                    }
                }) { Text(stringResource(R.string.library_reprocess_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { manualIndexingOnlyFailed = null }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
    if (albumPickerVisible) {
        AlbumPickerDialog(
            albums = albums,
            onAlbumSelected = { album ->
                libraryViewModel.addMemesToAlbum(album.id, selected) {
                    selected = emptySet()
                    albumPickerVisible = false
                }
            },
            onDismiss = { albumPickerVisible = false }
        )
    }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    if (confirmDelete) {
        DeleteMemesConfirmationDialog(
            onConfirm = {
                libraryViewModel.delete(selected)
                selected = emptySet()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
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
        MainTab.Home -> sortedMemes
        MainTab.Favorites -> sortedMemes.filter { it.isFavorite }
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
                    if (tabs[pagerState.currentPage] != MainTab.Albums) {
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.library_sort)
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                LibrarySort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(Modifier.size(24.dp)) {
                                                    if (sort == appSettings.librarySort) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null
                                                        )
                                                    }
                                                }
                                                Box(Modifier.size(24.dp)) {
                                                    if (sort == appSettings.librarySort) {
                                                        when (appSettings.librarySortDirection) {
                                                            null -> Unit
                                                            SortDirection.Descending -> Icon(
                                                                Icons.Default.ArrowDownward,
                                                                contentDescription = null
                                                            )
                                                            SortDirection.Ascending -> Icon(
                                                                Icons.Default.ArrowUpward,
                                                                contentDescription = null
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(stringResource(sort.label))
                                            }
                                        },
                                        onClick = {
                                            sortMenuExpanded = false
                                            if (sort == appSettings.librarySort) {
                                                onLibrarySortDirectionSelected(
                                                    appSettings.librarySortDirection.next()
                                                )
                                            } else {
                                                onLibrarySortSelected(sort)
                                                onLibrarySortDirectionSelected(SortDirection.Descending)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onIndexingHistoryClick) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.indexing_history_open)
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { manualIndexingOnlyFailed = failedIndexingCount > 0 },
                            enabled = memes.isNotEmpty() &&
                                !manualIndexingInProgress && indexingProgress == null
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.library_reprocess)
                            )
                        }
                        indexingProgress?.let { progress ->
                            CircularProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
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
                                galleryPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                    )
                                )
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
                MainTab.Home -> sortedMemes
                MainTab.Favorites -> sortedMemes.filter { it.isFavorite }
                MainTab.Albums -> emptyList()
            }
            if (tabs[page] == MainTab.Albums) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lazyItems(visibleSystemAlbums, key = { it.name }) { album ->
                        Card(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
                                    openedSystemAlbum = album
                                    openedAlbumId = null
                                }
                            )
                        ) {
                            Text(
                                text = stringResource(album.title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    lazyItems(albums, key = { it.id }) { album ->
                        Card(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = { openedAlbumId = album.id },
                                onLongClick = { albumOptionsId = album.id }
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f).padding(16.dp)
                                )
                                IconButton(onClick = { albumOptionsId = album.id }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.albums_manage)
                                    )
                                }
                            }
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
                            MediaIndexingBadge(
                                status = indexingStatuses[meme.id],
                                modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp),
                                onClick = if (
                                    indexingStatuses[meme.id] == MediaIndexingStatus.Failed
                                ) {
                                    { libraryViewModel.openIndexingFailure(meme.id) }
                                } else {
                                    null
                                }
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
