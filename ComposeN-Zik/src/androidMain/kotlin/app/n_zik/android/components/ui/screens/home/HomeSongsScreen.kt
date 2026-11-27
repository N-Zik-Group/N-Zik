package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview


import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.isUnmatched
import app.n_zik.android.appTextResources
import app.n_zik.android.components.navigation.FloatingActionSiblingSpace
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.enums.BuiltInPlaylist
import app.n_zik.android.enums.CacheType
import app.n_zik.android.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.android.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.components.glass.GlassToolbarButtons
import app.n_zik.android.ui.components.tab.TabHeader
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.themed.CacheSpaceIndicator
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.themed.HeaderInfo
import app.n_zik.android.ui.components.themed.MultiFloatingActionsContainer
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.builtInPlaylistKey
import app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.n_zik.android.utils.homeSongsAllSortMenuOrderKey
import app.n_zik.android.utils.homeSongsCachedSortMenuOrderKey
import app.n_zik.android.utils.homeSongsDislikedSortMenuOrderKey
import app.n_zik.android.utils.homeSongsDislikedToolbarOrderKey
import app.n_zik.android.utils.homeSongsDownloadedSortMenuOrderKey
import app.n_zik.android.utils.homeSongsFavoritesSortMenuOrderKey
import app.n_zik.android.utils.homeSongsOnDeviceSortMenuOrderKey
import app.n_zik.android.utils.homeSongsOrderKey
import app.n_zik.android.utils.homeSongsTopSortMenuOrderKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.showCachedPlaylistKey
import app.n_zik.android.utils.showDownloadedPlaylistKey
import app.n_zik.android.utils.showFavoritesPlaylistKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.showMyTopPlaylistKey
import app.n_zik.android.utils.showOnDevicePlaylistKey
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.enums.FilterBy
import app.n_zik.android.components.menu.FilterMenu
import app.n_zik.android.components.menu.originLabelRes
import app.n_zik.android.utils.filterByKey
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.utils.importYTMLikedSongs
import app.n_zik.android.utils.removeYTMLikedSongs
import app.n_zik.android.core.utils.Toaster
import org.json.JSONArray
import app.n_zik.android.components.ui.screens.home.onDevice.OnDeviceSong
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.components.tab.ImportSongsFromCSV
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.LikeComponent
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SmartShuffle
import app.n_zik.android.components.tab.SmartTrash
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.dialog.export.ExportCacheDialog
import app.n_zik.android.components.dialog.export.CachedLyricsExport
import app.n_zik.android.components.dialog.export.CachedLyricsExportDialog
import app.n_zik.android.components.dialog.export.resolveExportPayload
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.matching.SongMatch
import app.n_zik.android.core.matching.SongMatchRequest
import app.n_zik.android.core.matching.SongMatchStatus
import app.n_zik.android.core.matching.loadSongMatchFailures
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.playlistPage
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import app.n_zik.android.components.dialog.common.FfmpegPreparation
import app.n_zik.android.modules.ModuleState
import app.n_zik.android.components.dialog.common.InProgressDialog
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.search.MatchResultsDialog
import app.n_zik.android.components.dialog.media.YouTubeLinkImportDialog
import app.n_zik.android.components.tab.ImportPlaylistsMenu
import app.n_zik.android.components.tab.ImportSongsFromServices
import app.n_zik.android.components.Sort
import app.n_zik.android.themed.n_zik.component.playlist.PositionLock
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.song.UpdateSongDialog
import app.n_zik.android.components.song.PeriodSelector
import app.n_zik.android.components.ui.InlineCircularLoader
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.enums.SongSortBy
import app.n_zik.android.utils.homeSongsDownloadedToolbarOrderKey
import app.n_zik.android.utils.homeSongsToolbarOrderKey
import app.n_zik.android.utils.homeSongsFavoritesToolbarOrderKey
import app.n_zik.android.utils.homeSongsOnDeviceToolbarOrderKey
import app.n_zik.android.core.ondevice.deviceLibraryEnabled
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.utils.homeSongsTopToolbarOrderKey
import app.n_zik.android.utils.homeSongsOfflineToolbarOrderKey
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.enums.DislikeMode
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.input.nestedscroll.nestedScroll

@RequiresApi(Build.VERSION_CODES.O)
@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun HomeSongsScreen(navController: NavController, topContentInsetProvider: () -> androidx.compose.ui.unit.Dp = { 0.dp }, scrollContentInsetProvider: () -> androidx.compose.ui.unit.Dp = topContentInsetProvider) {
    val isThemePreview = LocalThemePreview.current != null
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val lazyListState = rememberLazyListState()

    // Full offline mode (spec-full-offline-mode): network toolbar actions are
    // removed from the layout; local actions stay live.
    val offlineMode by OfflineMode.rememberEnabled()

    var builtInPlaylist by rememberPreference( builtInPlaylistKey, BuiltInPlaylist.All )
    var filterBy by rememberPreference(filterByKey, FilterBy.All)
    val showOriginFilter = builtInPlaylist.hasSongOriginFilter(isYouTubeSyncEnabled())
    val sourceFilter = if (showOriginFilter) filterBy else FilterBy.All
    val onDeviceTabEnabled by rememberPreference(showOnDevicePlaylistKey, true)
    val showOnDeviceChip = deviceLibraryEnabled() && onDeviceTabEnabled
    LaunchedEffect(showOnDeviceChip, builtInPlaylist) {
        if (!showOnDeviceChip && builtInPlaylist == BuiltInPlaylist.OnDevice) {
            builtInPlaylist = BuiltInPlaylist.All
        }
    }
    var isRecommendationEnabled by remember { mutableStateOf(false) }
    var recommendationCount by remember { mutableStateOf(0) }
    var isRecommendationsLoading by remember { mutableStateOf(false) }

    var hiddenMatchRequestId by remember { mutableStateOf<String?>(null) }
    val matchScopeKey = "home-songs:${builtInPlaylist.name}"
    var matchCandidates by remember(matchScopeKey) { mutableStateOf<List<Song>?>(null) }
    val currentSongMatch by SongMatch.state.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    val matchResults by SongMatch.results.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    val songMatch = if (isThemePreview) null else {
        currentSongMatch?.takeIf { it.scopeKey == matchScopeKey } ?: matchResults[matchScopeKey]
    }
    var matchRefreshKey by remember { mutableIntStateOf(0) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteDialogTitle by remember { mutableStateOf("") }
    var deleteDialogAction by remember { mutableStateOf({}) }

    val itemsOnDisplayState = remember { mutableStateListOf<Song>() }

    val itemSelector = ItemSelector<Song>()
    fun visibleSongs() = itemsOnDisplayState.filter { it.isLocal == (builtInPlaylist == BuiltInPlaylist.OnDevice) }
    fun getSongs() = itemSelector.actionTargets(visibleSongs(), Song::id, Song::id, selectionOrder = true)
    val eligibleSelection by remember(builtInPlaylist) {
        derivedStateOf { itemSelector.isNotEmpty() && getSongs().isNotEmpty() }
    }
    val hasEligibleSelection = { eligibleSelection }

    val openMatch by rememberUpdatedState<() -> Unit> {
        val running = SongMatch.state.value?.takeIf {
            it.scopeKey == matchScopeKey && it.status == SongMatchStatus.Running
        }
        if (running != null) {
            hiddenMatchRequestId = null
        } else {
            matchCandidates = getSongs().filter { it.isUnmatched }.distinctBy(Song::id)
                .takeIf { it.isNotEmpty() }
        }
    }

    fun startMatch(songs: List<Song>) {
        if (!SongMatch.start(context, SongMatchRequest(scopeKey = matchScopeKey, songs = songs))) {
            Toaster.e(R.string.song_matching_failed_message)
        }
    }

    val search = Search.forHome()
    val locator = Locator( lazyListState, ::visibleSongs )
    val import = ImportSongsFromCSV(sourceSuffix = "HOMESONGS", likeImported = builtInPlaylist == BuiltInPlaylist.Favorites, onImportComplete = {
        val prefs = appContext().preferences
        val key = if (builtInPlaylist == BuiltInPlaylist.Favorites) Preference.HOME_SONGS_FAVORITES_SORT_BY.key else Preference.HOME_SONGS_SORT_BY.key
        prefs.edit().putString(key, SongSortBy.Custom.name).apply()
    })
    val importSpotify = ImportSongsFromServices.init(source = "SPOTIFY_IMPORT_HOMESONGS", likeImported = builtInPlaylist == BuiltInPlaylist.Favorites, onImportComplete = {
        val prefs = appContext().preferences
        val key = if (builtInPlaylist == BuiltInPlaylist.Favorites) Preference.HOME_SONGS_FAVORITES_SORT_BY.key else Preference.HOME_SONGS_SORT_BY.key
        prefs.edit().putString(key, SongSortBy.Custom.name).apply()
    })
    val importRiplay = ImportSongsFromServices.init(source = "RIPLAY_IMPORT_HOMESONGS", likeImported = builtInPlaylist == BuiltInPlaylist.Favorites, onImportComplete = {
        val prefs = appContext().preferences
        val key = if (builtInPlaylist == BuiltInPlaylist.Favorites) Preference.HOME_SONGS_FAVORITES_SORT_BY.key else Preference.HOME_SONGS_SORT_BY.key
        prefs.edit().putString(key, SongSortBy.Custom.name).apply()
    })
    val exportDialog = ExportSongsToCSVDialog(
        playlistBrowseId = "",
        playlistName = builtInPlaylist.name,
        songs = ::getSongs,
        selectionActive = { itemSelector.isActive },
    )

    var showExportCacheConfirmDialog by remember { mutableStateOf(false) }
    var showExportCacheLyricsDialog by remember { mutableStateOf(false) }
    var selectedCacheExport by rememberSaveable { mutableStateOf(false) }
    var selectedCacheTargets by remember { mutableStateOf<List<Song>?>(null) }
    var exportCacheLyricsChoice by rememberSaveable { mutableStateOf(CachedLyricsExport.None) }
    val ffmpegPreparation = remember { FfmpegPreparation() }
    DisposableEffect(ffmpegPreparation) {
        onDispose { ffmpegPreparation.dispose() }
    }
    var isExportingCache by remember { mutableStateOf(false) }
    var exportCacheProgress by remember { mutableIntStateOf(0) }
    var exportCacheTotal by remember { mutableIntStateOf(0) }

    val exportCacheFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { folderUri: Uri? ->
        val selected = selectedCacheExport
        val captured = selectedCacheTargets
        val lyricsChoice = exportCacheLyricsChoice
        selectedCacheExport = false
        selectedCacheTargets = null
        exportCacheLyricsChoice = CachedLyricsExport.None
        folderUri ?: return@rememberLauncherForActivityResult
        val songs = resolveExportPayload(selected, captured, ::visibleSongs)
        if (songs == null) {
            Toaster.e(R.string.export_selection_lost)
            return@rememberLauncherForActivityResult
        }
        if (songs.isEmpty()) return@rememberLauncherForActivityResult
        val currentBinder = binder ?: return@rememberLauncherForActivityResult

        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            appContext().contentResolver.takePersistableUriPermission(folderUri, takeFlags)
        } catch (e: Exception) {
            Timber.tag("HomeSongsScreen").w(e, "Failed to take persistable URI permission")
        }

        exportCacheTotal = songs.size
        exportCacheProgress = 0

        ExportCacheDialog.batchExport(
            folderUri = folderUri,
            songs = songs,
            binder = currentBinder,
            lyricsChoice = lyricsChoice,
            prepareFfmpeg = {
                withContext(NzikDispatchers.UI) {
                    ffmpegPreparation.awaitReady().also { state ->
                        if (state == ModuleState.READY) isExportingCache = true
                    }
                }
            },
            onProgress = { current, _, _ ->
                exportCacheProgress = current
            },
            onComplete = { successCount, failCount ->
                isExportingCache = false
                if (successCount > 0) Toaster.done()
                else Toaster.e(R.string.export_cached_songs_failed, appTextResources().getString(R.string.export_no_songs))
            }
        )
    }
    val coroutineScope = rememberCoroutineScope()

    var showYouTubeLinkDialog by remember { mutableStateOf(false) }
    DialogPresence(visible = showYouTubeLinkDialog) {
        YouTubeLinkImportDialog(
            onImport = { urlPlaylistId ->
                coroutineScope.launch(NzikDispatchers.DATA) {
                    val browseId = if (urlPlaylistId.startsWith("VL")) urlPlaylistId else "VL$urlPlaylistId"
                    Innertube.playlistPage(browseId = browseId)?.getOrNull()?.let { playlistPage ->
                        val playlistName = playlistPage.title ?: appTextResources().getString(R.string.playlist_youtube_title)
                        val playlist = Playlist(name = playlistName, browseId = browseId)
                        val playlistRowId = Database.playlistTable.insert(playlist)
                        val isFavoriteTab = builtInPlaylist == BuiltInPlaylist.Favorites
                        val songs = playlistPage.songsPage?.items?.mapNotNull {
                            it.asSong.copy(
                                totalPlayTimeMs = 1L,
                                likedAt = if (isFavoriteTab) System.currentTimeMillis() else null
                            )
                        }
                        if (songs != null) {
                            val basePos = Database.songPlaylistMapTable.getMaxPosition(playlistRowId)
                            Database.asyncTransaction {
                                songs.forEachIndexed { index, song ->
                                    songTable.upsert(listOf(song))
                                    songPlaylistMapTable.mapAtPosition(song.id, playlistRowId, basePos + 1 + index)
                                }
                            }
                            val prefs = appContext().preferences
                            val key = if (builtInPlaylist == BuiltInPlaylist.Favorites) Preference.HOME_SONGS_FAVORITES_SORT_BY.key else Preference.HOME_SONGS_SORT_BY.key
                            prefs.edit().putString(key, SongSortBy.Custom.name).apply()
                            Toaster.done()
                        }
                    }
                }
            },
            onDismiss = { showYouTubeLinkDialog = false }
        )
    }

    DialogPresence(target = matchCandidates) { candidates ->
        ConfirmationDialog(
            title = stringResource(R.string.song_matching_dialog_title),
            confirmText = stringResource(R.string.song_matching_dialog_title),
            text = stringResource(R.string.song_matching_all_confirmation, candidates.size),
            onDismiss = { matchCandidates = null },
            onConfirm = { startMatch(candidates) },
        )
    }

    DialogPresence(visible = showDeleteConfirmDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.song_delete_dialog_title),
            confirmText = stringResource(R.string.common_action_delete),
            text = deleteDialogTitle,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                deleteDialogAction()
            }
        )
    }

    val runningMatch = songMatch?.takeIf { it.status == SongMatchStatus.Running }
    val showMatchProgress = runningMatch != null && runningMatch.requestId != hiddenMatchRequestId
    DialogPresence(visible = showMatchProgress) {
        InProgressDialog(
            total = songMatch?.total ?: 0,
            done = songMatch?.processed ?: 0,
            text = stringResource(R.string.song_matching_progress_title),
            message = stringResource(R.string.song_matching_background_message),
            onDismiss = { hiddenMatchRequestId = songMatch?.requestId },
            onCancel = { songMatch?.let { SongMatch.cancel(it.requestId) } },
        )
    }

    val matchResult = songMatch?.takeIf {
        it.status == SongMatchStatus.Finished || it.status == SongMatchStatus.Cancelled ||
            (it.status == SongMatchStatus.Failed && it.failedIds.isNotEmpty())
    }
    LaunchedEffect(matchResult?.requestId) {
        if (matchResult != null) matchRefreshKey++
    }
    DialogPresence(target = matchResult) { result ->
        key(result.requestId) {
            val failedSongs by produceState(emptyList<Song>(), result.requestId) {
                value = loadSongMatchFailures(result)
            }
            MatchResultsDialog(
                matched = result.matched,
                failed = result.failed,
                merged = result.merged,
                failedSongs = failedSongs,
                cancelled = result.status == SongMatchStatus.Cancelled,
                unprocessed = result.unprocessed,
                onRetry = if (!offlineMode && failedSongs.isNotEmpty()) {
                    {
                        if (!offlineMode && SongMatch.results.value[matchScopeKey]?.requestId == result.requestId) {
                            startMatch(failedSongs)
                        }
                    }
                } else null,
                onDismiss = { SongMatch.dismiss(result.requestId) },
            )
        }
    }

    val failedMatch = songMatch?.takeIf { it.status == SongMatchStatus.Failed && it.failedIds.isEmpty() }
    LaunchedEffect(failedMatch?.requestId) {
        if (failedMatch != null) {
            Toaster.e(R.string.song_matching_failed_message)
            SongMatch.dismiss(failedMatch.requestId)
        }
    }

    val importMenu = remember(builtInPlaylist) {
        ImportPlaylistsMenu(
            onImportNzik = { import.onShortClick() },
            onImportSpotify = { importSpotify.onShortClick() },
            onImportRiplay = { importRiplay.onShortClick() },
            onImportYoutubeLink = { showYouTubeLinkDialog = true },
            // Mixed action (spec-full-offline-mode): the button stays visible,
            // the dialog prunes the network choices (CSV/NZik local import only).
            networkOptionsEnabled = { !offlineMode }
        )
    }

    val shuffle = SongShuffler(::getSongs)
    val smartShuffle = SmartShuffle(
        isRecommendationEnabled = { isRecommendationEnabled },
        isRecommendationsLoading = { isRecommendationsLoading },
        onToggleRecommendation = { isRecommendationEnabled = !isRecommendationEnabled }
    )
    val queueMenuState = LocalMenuState.current
    fun queueSelection(playNext: Boolean) {
        val player = binder?.player ?: return
        val songs = getSongs().toList()
        if (songs.isEmpty()) return
        val endSelection = itemSelector.captureSelectionCompletion()
        val menuKey = queueMenuState.transitionKey
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            val items = withContext(NzikDispatchers.DATA) { songs.map(Song::asMediaItem) }
            val queued = if (playNext) player.addNextOffMain(items, context)
                else player.enqueueOffMain(items, context)
            if (queued) {
                endSelection()
                if (queueMenuState.isCurrentMenu(menuKey)) queueMenuState.hide()
            }
        }
    }

    val playNext = PlayNext(dismissOnClick = false) { queueSelection(playNext = true) }
    val enqueue = Enqueue(dismissOnClick = false) { queueSelection(playNext = false) }
    val addToFavorite = LikeComponent(::getSongs)
    val addToPlaylist = PlaylistsMenu.deferred(
        navController = navController,
        captureMediaItems = {
            val songs = getSongs()
            if (itemSelector.isActive && songs.isEmpty()) null else ({ songs.map(Song::asMediaItem) })
        },
        onFailure = { throwable, preview ->
            Timber.tag("HomeSongsScreen").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        itemSelector = itemSelector,
    )
    val smartTrash = SmartTrash(
        builtInPlaylist = { builtInPlaylist },
        getSongs = ::getSongs,
        isSelectionActive = { itemSelector.isActive }
    )

    val songSort = when( builtInPlaylist ) {
        BuiltInPlaylist.Favorites -> Sort( Preference.HOME_SONGS_FAVORITES_SORT_BY, Preference.HOME_SONGS_FAVORITES_SORT_ORDER, homeSongsFavoritesSortMenuOrderKey, "favs" )
        BuiltInPlaylist.Offline -> Sort( Preference.HOME_SONGS_OFFLINE_SORT_BY, Preference.HOME_SONGS_OFFLINE_SORT_ORDER, homeSongsCachedSortMenuOrderKey, "off" )
        BuiltInPlaylist.Downloaded -> Sort( Preference.HOME_SONGS_DOWNLOADED_SORT_BY, Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER, homeSongsDownloadedSortMenuOrderKey, "dl" )
        BuiltInPlaylist.Top -> Sort( Preference.HOME_SONGS_TOP_SORT_BY, Preference.HOME_SONGS_TOP_SORT_ORDER, homeSongsTopSortMenuOrderKey, "top" )
        BuiltInPlaylist.OnDevice -> Sort( Preference.HOME_ON_DEVICE_SONGS_SORT_BY, Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER, homeSongsOnDeviceSortMenuOrderKey, "dev" )
        BuiltInPlaylist.Disliked -> Sort( Preference.HOME_SONGS_DISLIKED_SORT_BY, Preference.HOME_SONGS_DISLIKED_SORT_ORDER, homeSongsDislikedSortMenuOrderKey, "disliked" )
        else -> Sort( Preference.HOME_SONGS_SORT_BY, Preference.HOME_SONGS_SORT_ORDER, homeSongsAllSortMenuOrderKey, "all" )
    }
    val positionLock = remember( songSort.sortOrder ) { PositionLock(songSort.sortOrder) }
    val topPlaylists = PeriodSelector( Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD, homeSongsTopSortMenuOrderKey, "top" )
    val downloadAllDialog = DownloadAllSongsDialog( ::getSongs )
    val deleteDownloadsDialog = DeleteAllDownloadedSongsDialog( ::getSongs )
    val updateSongsDialog = UpdateSongDialog( batchTargets = ::getSongs )

    val hasUnmatchedSongs by remember {
        derivedStateOf {
            itemsOnDisplayState.any { it.isUnmatched }
        }
    }

    val sync = object : MenuIcon, Descriptive {
        override val iconId: Int = R.drawable.sync
        override val messageId: Int = R.string.sync_likes
        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
        override fun onShortClick() {
            coroutineScope.launch(NzikDispatchers.DATA) { importYTMLikedSongs(force = true) }
        }
        override fun onLongClick() {
            coroutineScope.launch(NzikDispatchers.DATA) { removeYTMLikedSongs() }
        }
        override val menuLongClick: (() -> Unit) get() = ::onLongClick
    }

    val localMatchButton = remember {
        object : MenuIcon, Descriptive {
            override val iconId: Int = R.drawable.alert
            override val messageId: Int = R.string.song_matching_action_open
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { openMatch() }
            override fun onLongClick() {}
        }
    }

    val homeSongsToolbarOrderPrefAll by rememberPreference( homeSongsToolbarOrderKey, "" )
    val homeSongsToolbarOrderPrefFavorites by rememberPreference( homeSongsFavoritesToolbarOrderKey, "" )
    val homeSongsToolbarOrderPrefOffline by rememberPreference( homeSongsOfflineToolbarOrderKey, "" )
    val homeSongsToolbarOrderPrefDownloaded by rememberPreference( homeSongsDownloadedToolbarOrderKey, "" )
    val homeSongsToolbarOrderPrefTop by rememberPreference( homeSongsTopToolbarOrderKey, "" )
    val homeSongsToolbarOrderPrefOnDevice by rememberPreference( homeSongsOnDeviceToolbarOrderKey, "" )
    val homeSongsToolbarOrderPrefDisliked by rememberPreference( homeSongsDislikedToolbarOrderKey, "" )

    val currentToolbarOrderPref = when(builtInPlaylist) {
        BuiltInPlaylist.All -> homeSongsToolbarOrderPrefAll
        BuiltInPlaylist.Favorites -> homeSongsToolbarOrderPrefFavorites
        BuiltInPlaylist.Offline -> homeSongsToolbarOrderPrefOffline
        BuiltInPlaylist.Downloaded -> homeSongsToolbarOrderPrefDownloaded
        BuiltInPlaylist.Top -> homeSongsToolbarOrderPrefTop
        BuiltInPlaylist.OnDevice -> homeSongsToolbarOrderPrefOnDevice
        BuiltInPlaylist.Disliked -> homeSongsToolbarOrderPrefDisliked
    }

    val defaultToolbarOrder = HomeSongsToolbarSettingsDialog.tabAvailableIds[builtInPlaylist] ?: HomeSongsToolbarSettingsDialog.allButtonIds
    val order = try {
        if (currentToolbarOrderPref.isBlank()) defaultToolbarOrder else {
            val arr = JSONArray(currentToolbarOrderPref)
            val savedIds = (0 until arr.length()).map { arr.getString(it) }.distinct()
            val available = defaultToolbarOrder.toSet()
            val filtered = savedIds.filter { it in available }
            val missing = defaultToolbarOrder.filter { it !in filtered }
            filtered + missing
        }
    } catch (_: Exception) { defaultToolbarOrder }

    val buttons = mutableListOf<Button>().apply {
        order.forEach { id ->
            // Check toggle state - if disabled, skip this button
            val toggleKey = "${when (builtInPlaylist) {
                BuiltInPlaylist.All -> "all"
                BuiltInPlaylist.Favorites -> "favs"
                BuiltInPlaylist.Offline -> "off"
                BuiltInPlaylist.Downloaded -> "dl"
                BuiltInPlaylist.Top -> "top"
                BuiltInPlaylist.OnDevice -> "dev"
                BuiltInPlaylist.Disliked -> "disliked"
                else -> "x"
            }}_ts_$id"
            val isEnabled = id in HomeSongsToolbarSettingsDialog.lockedIds ||
                context.preferences.getBoolean(toggleKey, true)
            if (!isEnabled) return@forEach

            when (id) {
                "sort" -> add( if( builtInPlaylist == BuiltInPlaylist.Top ) topPlaylists else songSort )
                "position_lock" -> if ( builtInPlaylist != BuiltInPlaylist.Top && songSort.sortBy == SongSortBy.Custom ) add( positionLock )
                "search" -> add( search )
                "locator" -> add( locator )
                "download_all" -> if (!offlineMode && builtInPlaylist != BuiltInPlaylist.OnDevice) add(itemSelector.guard(downloadAllDialog, hasEligibleSelection))
                "delete_downloads" -> if (builtInPlaylist != BuiltInPlaylist.OnDevice) add(itemSelector.guard(deleteDownloadsDialog, hasEligibleSelection))
                "update" -> if (!offlineMode && builtInPlaylist != BuiltInPlaylist.OnDevice) add(itemSelector.guard(updateSongsDialog, hasEligibleSelection))
                "shuffle" -> add(itemSelector.guard(shuffle, hasEligibleSelection))
                "smart_shuffle" -> if (!offlineMode) add( smartShuffle )
                "item_selector" -> add( itemSelector )
                "play_next" -> add(itemSelector.guard(playNext, hasEligibleSelection))
                "enqueue" -> add(itemSelector.guard(enqueue, hasEligibleSelection))
                "add_to_favorite" -> add(itemSelector.guard(addToFavorite, hasEligibleSelection))
                "add_to_playlist" -> add(itemSelector.guard(addToPlaylist, hasEligibleSelection))
                "import_menu" -> if (builtInPlaylist == BuiltInPlaylist.All || builtInPlaylist == BuiltInPlaylist.Favorites) add( importMenu )
                "sync_ytm_likes" -> if (!offlineMode && builtInPlaylist == BuiltInPlaylist.Favorites && isYouTubeSyncEnabled()) add( sync )
                "export_dialog" -> if (builtInPlaylist != BuiltInPlaylist.OnDevice) add(itemSelector.guard(exportDialog, hasEligibleSelection))
                // The FFmpeg gate is gone (spec-ffmpeg-extension-module): the module is
                // downloaded on first use, so the export is always offered (Offline/Downloaded)
                "export_cache" -> if (builtInPlaylist == BuiltInPlaylist.Offline || builtInPlaylist == BuiltInPlaylist.Downloaded) add(
                    itemSelector.guard(object : MenuIcon, Descriptive {
                        override val iconId: Int = R.drawable.export_outline
                        override val messageId: Int = R.string.export_cached_songs_action
                        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                        override fun onShortClick() {
                            val targets = getSongs()
                            if (targets.isEmpty()) return
                            selectedCacheExport = itemSelector.isActive
                            selectedCacheTargets = targets.takeIf { selectedCacheExport }
                            showExportCacheConfirmDialog = true
                        }
                        override fun onLongClick() {}
                    }, hasEligibleSelection)
                )
                "smart_trash" -> if (builtInPlaylist != BuiltInPlaylist.OnDevice) add(itemSelector.guard(smartTrash, hasEligibleSelection))
                "match" -> if (!isThemePreview && builtInPlaylist != BuiltInPlaylist.OnDevice &&
                    (runningMatch != null || (!offlineMode && hasUnmatchedSongs))
                ) add(itemSelector.guard(localMatchButton) { runningMatch != null || getSongs().any(Song::isUnmatched) })
            }
        }
    }

    val header: @Composable (MutableFloatState, MutableIntState) -> Unit = { titleOffsetState, titleHeightState ->
        Column {
            search.HeaderContent {
                CollapsibleTitleRow( titleOffsetState, titleHeightState ) {
                    TabHeader( R.string.library_songs_title ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HeaderInfo( itemsOnDisplayState.size.toString(), R.drawable.musical_notes )
                                }
                                if (isRecommendationEnabled) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.smart_shuffle),
                                            contentDescription = null,
                                            tint = colorPalette().textSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        if (isRecommendationsLoading) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            InlineCircularLoader(
                                                modifier = Modifier.size(12.dp),
                                                color = colorPalette().textSecondary
                                            )
                                        } else if (recommendationCount > 0) {
                                            BasicText(
                                                text = recommendationCount.toString(),
                                                style = TextStyle(
                                                    color = colorPalette().textSecondary,
                                                    fontStyle = typography().xxxs.semiBold.fontStyle,
                                                    fontWeight = typography().xxxs.semiBold.fontWeight,
                                                    fontSize = typography().xxxs.semiBold.fontSize
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding( start = 4.dp )
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(5.dp))
                                    }
                                }
                            }
                        }
                    }
            }

                    importMenu.Render()
                    exportDialog.Render()

                    DialogPresence(visible = showExportCacheConfirmDialog) {
                        val count = resolveExportPayload(selectedCacheExport, selectedCacheTargets, ::visibleSongs)
                            .orEmpty().size
                        ConfirmationDialog(
                            title = stringResource(R.string.export_cached_songs_action),
                            confirmText = stringResource(R.string.export_cached_songs_action),
                            text = pluralStringResource(R.plurals.library_export_cached_song_confirmation_count, count, count),
                            onDismiss = { showExportCacheConfirmDialog = false },
                            onConfirm = {
                                showExportCacheConfirmDialog = false
                                showExportCacheLyricsDialog = true
                            }
                        )
                    }
                    DialogPresence(visible = showExportCacheLyricsDialog) {
                        CachedLyricsExportDialog(
                            onDismiss = { showExportCacheLyricsDialog = false },
                            onChoice = { choice ->
                                exportCacheLyricsChoice = choice
                                exportCacheFolderLauncher.launch(null)
                            }
                        )
                    }
                    ffmpegPreparation.Render()
                    DialogPresence(visible = isExportingCache) {
                        InProgressDialog(
                            total = exportCacheTotal,
                            done = exportCacheProgress,
                            text = stringResource(R.string.export_running_status),
                            onDismiss = null
                        )
                    }

                    downloadAllDialog.Render()
                    deleteDownloadsDialog.Render()
                    updateSongsDialog.Render()
                    smartTrash.Render()

                    GlassToolbarButtons( buttons )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding( bottom = 8.dp )
                            .fillMaxWidth()
                    ) {
                        Column {
                            val showFavoritesPlaylist by rememberPreference( showFavoritesPlaylistKey, true )
                            val showCachedPlaylist by rememberPreference( showCachedPlaylistKey, true )
                            val showMyTopPlaylist by rememberPreference( showMyTopPlaylistKey, true )
                            val showDownloadedPlaylist by rememberPreference( showDownloadedPlaylistKey, true )
                            val showDislikedChip by rememberPreference( excludeDislikedSongsKey, DislikeMode.Enabled )
                            val homeSongsOrderPref by rememberPreference( homeSongsOrderKey, "" )
                            val chips = remember( showFavoritesPlaylist, showCachedPlaylist, showMyTopPlaylist, showDownloadedPlaylist, showOnDeviceChip, showDislikedChip, homeSongsOrderPref ) {
                                val songsDefaultOrder = listOf("all", "favorites", "disliked", "cached", "downloaded", "top", "on_device")
                                val toggleMap = mapOf(
                                    "favorites" to showFavoritesPlaylist,
                                    "cached" to showCachedPlaylist,
                                    "downloaded" to showDownloadedPlaylist,
                                    "top" to showMyTopPlaylist,
                                    "on_device" to showOnDeviceChip,
                                    "disliked" to showDislikedChip.isEnabled
                                )
                                val builtinMap = mapOf(
                                    "all" to BuiltInPlaylist.All,
                                    "favorites" to BuiltInPlaylist.Favorites,
                                    "cached" to BuiltInPlaylist.Offline,
                                    "downloaded" to BuiltInPlaylist.Downloaded,
                                    "top" to BuiltInPlaylist.Top,
                                    "on_device" to BuiltInPlaylist.OnDevice,
                                    "disliked" to BuiltInPlaylist.Disliked
                                )
                                val order = try {
                                    val arr = JSONArray(homeSongsOrderPref)
                                    val parsed = (0 until arr.length()).map { arr.getString(it) }
                                    val valid = parsed.filter { it in songsDefaultOrder }.toMutableList()
                                    for (id in songsDefaultOrder) { if (id !in valid) valid.add(id) }
                                    valid
                                } catch (_: Exception) { songsDefaultOrder }
                                buildList {
                                    for (id in order) {
                                        if (id == "all" || toggleMap[id] == true) {
                                            builtinMap[id]?.let { add(it) }
                                        }
                                    }
                                }
                            }

                            GlassHomeChips(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 28.dp),
                                chips = chips,
                                currentValue = builtInPlaylist,
                                onValueUpdate = { builtInPlaylist = it },
                                modifier = Modifier
                            )

                            androidx.compose.animation.AnimatedVisibility(
                                modifier = Modifier.padding(start = 16.dp),
                                visible = showOriginFilter,
                                enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)) + androidx.compose.animation.expandVertically(animationSpec = tween(200)),
                                exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)) + androidx.compose.animation.shrinkVertically(animationSpec = tween(200))
                            ) {
                                val menuState = LocalMenuState.current
                                GlassHomeFilterChip(
                                    label = {
                                        Text(
                                            text = stringResource(filterBy.originLabelRes)
                                        )
                                    },
                                    selected = true,
                                    shape = uiRoundnessShape(),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colorPalette().background1,
                                        labelColor = colorPalette().text,
                                        selectedContainerColor = colorPalette().accent,
                                        selectedLabelColor = colorPalette().onAccent,
                                    ),
                                    onClick = {
                                        menuState.display {
                                            FilterMenu(selected = filterBy) { selected ->
                                                filterBy = selected
                                                menuState.hide()
                                            }
                                        }
                                    }
                                )
                            }

                            val exoPlayerDiskCacheMaxSize by rememberPreference(app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey, app.n_zik.android.enums.ExoPlayerDiskCacheMaxSize.`2GB`)
                            val exoPlayerDiskDownloadCacheMaxSize by rememberPreference(app.n_zik.android.utils.exoPlayerDiskDownloadCacheMaxSizeKey, app.n_zik.android.enums.ExoPlayerDiskDownloadCacheMaxSize.`2GB`)

                            val showCacheIndicator = when (builtInPlaylist) {
                                BuiltInPlaylist.Offline -> exoPlayerDiskCacheMaxSize != app.n_zik.android.enums.ExoPlayerDiskCacheMaxSize.Unlimited
                                BuiltInPlaylist.Downloaded -> exoPlayerDiskDownloadCacheMaxSize != app.n_zik.android.enums.ExoPlayerDiskDownloadCacheMaxSize.Unlimited
                                else -> false
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                modifier = Modifier.padding(start = 16.dp),
                                visible = showCacheIndicator
                            ) {
                                CacheSpaceIndicator(
                                    cacheType = when (builtInPlaylist) {
                                        BuiltInPlaylist.Downloaded -> CacheType.DownloadedSongs
                                        BuiltInPlaylist.Offline -> CacheType.CachedSongs
                                        else -> CacheType.CachedSongs
                                    }
                                )
                            }
                        }
                    }
                    search.SearchBar( columnScope = this@Column )
                }
    }

    val headerHeightState = remember { mutableIntStateOf(0) }
    val headerHeight by headerHeightState
    val headerOffsetState = remember { mutableFloatStateOf(0f) }
    val headerOffset by headerOffsetState

    val nestedScrollConnection = rememberHomeHeaderConnection(
        headerHeight, headerOffsetState, itemsOnDisplayState.isNotEmpty(), scrollState = lazyListState,
    )

    val floatingBackdrop = rememberGlassFloatingActionBackdrop()

    Box(
        modifier = Modifier.background( colorPalette().background0 )
            .fillMaxHeight()
            .fillMaxWidth()
            .nestedScroll(nestedScrollConnection)
    ) {
        HomeChromeLayout(Modifier.fillMaxSize(), header = {
            HomeCollapsingHeader(
                headerOffset = headerOffsetState,
                headerHeight = headerHeightState,
                topContentInset = topContentInsetProvider,
            ) { header(headerOffsetState, headerHeightState) }
        }) {
            Column( Modifier.fillMaxSize().glassFloatingActionContent(floatingBackdrop) ) {
                val headerPadding = with(androidx.compose.ui.platform.LocalDensity.current) { headerHeight.toDp() }
                when( builtInPlaylist ) {
                    BuiltInPlaylist.OnDevice -> OnDeviceSong( navController, lazyListState, itemSelector, search, buttons, itemsOnDisplayState, headerPadding, scrollContentInsetProvider )
                    else -> HomeSongs(
                        navController, builtInPlaylist, lazyListState, itemSelector, search, buttons, itemsOnDisplayState,
                        onRecommendationCountChange = { count -> recommendationCount = count },
                        onRecommendationsLoadingChange = { loading -> isRecommendationsLoading = loading },
                        isRecommendationEnabled = isRecommendationEnabled,
                        refreshKey = matchRefreshKey,
                        onMatchClick = openMatch,
                        headerPadding = headerPadding,
                        topContentInsetProvider = scrollContentInsetProvider,
                        sourceFilter = sourceFilter,
                    )
                }
            }
        }
        GlassFloatingActions(floatingBackdrop) {
            val showFloatingIcon by rememberPreference( showFloatingIconKey, false )
            val barsToggle = app.n_zik.android.components.navigation.sideAwareLandscapeBarsToggleButton()
            FloatingActionsContainerWithScrollToTop(
                reservedEndSpace = if (showFloatingIcon) FloatingActionSiblingSpace else 0.dp,
                lazyListState = lazyListState,
                iconId = barsToggle?.iconId,
                onClick = barsToggle?.onClick
            )

            if( showFloatingIcon )
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = { navController.navigate(NavRoutes.search.name) },
                    onClickSettings = { navController.navigate(NavRoutes.settings.name) },
                    onClickSearch = { navController.navigate(NavRoutes.search.name) }
                )
        }
    }
}


