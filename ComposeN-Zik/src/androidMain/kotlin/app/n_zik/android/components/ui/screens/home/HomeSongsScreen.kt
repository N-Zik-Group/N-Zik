package app.n_zik.android.components.ui.screens.home

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.*
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.ButtonsRow
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.TabHeader
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.themed.*
import app.it.fast4x.rimusic.utils.*
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.enums.FilterBy
import app.n_zik.android.components.menu.FilterMenu
import app.it.fast4x.rimusic.utils.filterByKey
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.ui.screens.home.HomeSongs
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import app.it.fast4x.rimusic.utils.importYTMLikedSongs
import app.it.fast4x.rimusic.utils.autoSyncToolbutton
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.removeYTMLikedSongs
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import app.n_zik.android.components.ui.screens.home.onDevice.OnDeviceSong
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.BuildConfig
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.tab.*
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.dialog.export.ExportCacheDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.typography
import app.n_zik.android.utils.getAlbumVersionFromVideoGlobal
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.playlistPage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import app.it.fast4x.rimusic.ui.components.themed.InProgressDialog
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.n_zik.android.components.dialog.search.MatchResultsDialog
import app.n_zik.android.components.dialog.media.YouTubeLinkImportDialog
import app.n_zik.android.components.tab.ImportPlaylistsMenu
import app.n_zik.android.components.tab.ImportSongsFromServices
import app.n_zik.android.core.database.ImportSong
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.components.Sort
import app.kreate.android.themed.rimusic.component.playlist.PositionLock
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.song.PeriodSelector
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.utils.homeSongsDownloadedToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsFavoritesToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsOnDeviceToolbarOrderKey
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.utils.homeSongsTopToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeSongsOfflineToolbarOrderKey
import java.util.concurrent.atomic.AtomicInteger
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.n_zik.android.enums.lyrics.LyricsType

@RequiresApi(Build.VERSION_CODES.O)
@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun HomeSongsScreen(navController: NavController ) {
    val binder = LocalPlayerServiceBinder.current
    val lazyListState = rememberLazyListState()

    var builtInPlaylist by rememberPreference( builtInPlaylistKey, BuiltInPlaylist.All )
    var isRecommendationEnabled by remember { mutableStateOf(false) }
    var recommendationCount by remember { mutableStateOf(0) }
    var isRecommendationsLoading by remember { mutableStateOf(false) }

    var showConfirmMatchAllDialog by remember { mutableStateOf(false) }
    var showMatchingProgressDialog by remember { mutableStateOf(false) }
    var cancelMatch by remember { mutableStateOf(false) }
    var matchRunning by remember { mutableStateOf(false) }
    var matchJob by remember { mutableStateOf<Job?>(null) }
    var totalSongsToMatch by remember { mutableStateOf(0) }
    var songsMatched by remember { mutableStateOf(0) }
    var retryMatchMode by remember { mutableStateOf(false) }
    var retryMatchSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    var showMatchResultsDialog by remember { mutableStateOf(false) }
    var matchResultsMatched by remember { mutableStateOf(0) }
    var matchResultsFailed by remember { mutableStateOf(0) }
    var matchResultsMerged by remember { mutableStateOf(0) }
    var matchResultsFailedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var matchRefreshKey by remember { mutableIntStateOf(0) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteDialogTitle by remember { mutableStateOf("") }
    var deleteDialogAction by remember { mutableStateOf({}) }

    val itemsOnDisplayState = remember { mutableStateListOf<Song>() }

    val itemSelector = ItemSelector<Song>()
    fun getSongs() = itemSelector.ifEmpty { itemsOnDisplayState }.toList()
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val search = Search(lazyListState)
    val locator = Locator( lazyListState, ::getSongs )
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
        songs = ::getSongs
    )

    var showExportCacheConfirmDialog by remember { mutableStateOf(false) }
    var showExportCacheLyricsDialog by remember { mutableStateOf(false) }
    var exportCacheLyricsType by remember { mutableStateOf<String?>(null) }
    var isExportingCache by remember { mutableStateOf(false) }
    var exportCacheProgress by remember { mutableIntStateOf(0) }
    var exportCacheTotal by remember { mutableIntStateOf(0) }

    val exportCacheFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { folderUri: Uri? ->
        folderUri ?: return@rememberLauncherForActivityResult
        val currentBinder = binder ?: return@rememberLauncherForActivityResult
        val songs = getSongs()
        if (songs.isEmpty()) return@rememberLauncherForActivityResult

        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            appContext().contentResolver.takePersistableUriPermission(folderUri, takeFlags)
        } catch (e: Exception) {
            Timber.tag("HomeSongsScreen").w(e, "Failed to take persistable URI permission")
        }

        isExportingCache = true
        exportCacheTotal = songs.size
        exportCacheProgress = 0

        ExportCacheDialog.batchExport(
            folderUri = folderUri,
            songs = songs,
            binder = currentBinder,
            lyricsType = exportCacheLyricsType,
            onProgress = { current, _, _ ->
                exportCacheProgress = current
            },
            onComplete = { successCount, failCount ->
                isExportingCache = false
                if (successCount > 0) Toaster.done()
                else Toaster.e(R.string.export_failed, "No songs exported")
            }
        )
    }
    val coroutineScope = rememberCoroutineScope()

    var showYouTubeLinkDialog by remember { mutableStateOf(false) }
    if (showYouTubeLinkDialog) {
        YouTubeLinkImportDialog(
            onImport = { urlPlaylistId ->
                coroutineScope.launch(Dispatchers.IO) {
                    val browseId = if (urlPlaylistId.startsWith("VL")) urlPlaylistId else "VL$urlPlaylistId"
                    Innertube.playlistPage(browseId = browseId)?.getOrNull()?.let { playlistPage ->
                        val playlistName = playlistPage.title ?: appContext().getString(R.string.youtube_playlist)
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

    if (showConfirmMatchAllDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.match_all_confirmation, getSongs().count { it.id.length != 11 || (it.durationText == "00:00" && it.totalPlayTimeMs == 1L) }),
            onDismiss = { showConfirmMatchAllDialog = false },
            onConfirm = {
                showConfirmMatchAllDialog = false
                retryMatchMode = false
                retryMatchSongs = emptyList()
                showMatchingProgressDialog = true
                cancelMatch = false
                matchRunning = true
            }
        )
    }

    if (showDeleteConfirmDialog) {
        ConfirmationDialog(
            text = deleteDialogTitle,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                deleteDialogAction()
            }
        )
    }

    if (showMatchingProgressDialog) {
        InProgressDialog(
            total = totalSongsToMatch,
            done = songsMatched,
            text = stringResource(R.string.matching_songs),
            onDismiss = {
                cancelMatch = true
                showMatchingProgressDialog = false
                matchJob?.cancel()
            }
        )
    }

    if (showMatchResultsDialog) {
        MatchResultsDialog(
            matched = matchResultsMatched,
            failed = matchResultsFailed,
            merged = matchResultsMerged,
            failedSongs = matchResultsFailedSongs,
            onRetry = if (matchResultsFailed > 0) {{
                showMatchResultsDialog = false
                retryMatchMode = true
                retryMatchSongs = matchResultsFailedSongs
                showMatchingProgressDialog = true
                cancelMatch = false
                matchRunning = true
            }} else null,
            onDismiss = { showMatchResultsDialog = false }
        )
    }

    LaunchedEffect(matchRunning) {
        if (!matchRunning) return@LaunchedEffect
        val mergedCounter = AtomicInteger(0)
        val job = launch(Dispatchers.IO) {
            try {
                val unmatched = if (retryMatchMode && retryMatchSongs.isNotEmpty()) {
                    retryMatchSongs
                } else {
                    getSongs().filter { (it.id.length != 11 || (it.durationText == "00:00" && it.totalPlayTimeMs == 1L)) && !it.id.startsWith(LOCAL_KEY_PREFIX) }
                }
                totalSongsToMatch = unmatched.size
                songsMatched = 0

                val jobs = mutableListOf<Job>()
                unmatched.forEachIndexed { index, song ->
                    ensureActive()
                    jobs.add(launch(Dispatchers.IO) {
                        var wasCancelled = false
                        try {
                            if (cancelMatch) return@launch
                            getAlbumVersionFromVideoGlobal(song, mergedCounter)
                        } catch (e: CancellationException) {
                            wasCancelled = true
                            throw e
                        } catch (e: Exception) {
                            Timber.tag("HomeSongsScreen").e(e, "Failed to match song to album version")
                        } finally {
                            if (!wasCancelled) songsMatched++
                        }
                    })
                    delay(800)
                }
                jobs.forEach { it.join() }
            } catch (e: CancellationException) {
            } finally {
                withContext(NonCancellable) {
                    delay(1500)
                    var failedCount = 0
                    val allEntries = Database.importSongTable.getAllEntries()
                    val failedEntries = mutableListOf<ImportSong>()
                    for (entry in allEntries) {
                        val count = Database.songTable.countById(entry.originalId)
                        val isYouTubeId = entry.originalId.length == 11 && !entry.originalId.startsWith(LOCAL_KEY_PREFIX)
                        if (count > 0) {
                            if (isYouTubeId) {
                                val song = Database.songTable.findById(entry.originalId).first()
                                if (song != null && song.durationText == "00:00") {
                                    failedCount++
                                    failedEntries.add(entry)
                                } else {
                                    Database.importSongTable.deleteByOriginalId(entry.originalId)
                                }
                            } else {
                                failedCount++
                                failedEntries.add(entry)
                            }
                        } else {
                            Database.importSongTable.deleteByOriginalId(entry.originalId)
                        }
                    }

                    matchRefreshKey++
                    val matchedCount = maxOf(0, totalSongsToMatch - failedCount)
                    matchResultsMatched = matchedCount
                    matchResultsFailed = failedCount
                    matchResultsMerged = mergedCounter.get()
                    val failedOriginalIds = failedEntries.map { it.originalId }.toSet()
                    val failedSongsList = getSongs().filter { it.id in failedOriginalIds }
                    matchResultsFailedSongs = failedSongsList
                    showMatchResultsDialog = true
                }
                showMatchingProgressDialog = false
                retryMatchMode = false
                retryMatchSongs = emptyList()
                matchRunning = false
                cancelMatch = false
                matchJob = null
            }
        }
        matchJob = job
        job.join()
    }

    val importMenu = remember(builtInPlaylist) {
        ImportPlaylistsMenu(
            onImportNzik = { import.onShortClick() },
            onImportSpotify = { importSpotify.onShortClick() },
            onImportRiplay = { importRiplay.onShortClick() },
            onImportYoutubeLink = { showYouTubeLinkDialog = true }
        )
    }

    val shuffle = SongShuffler(::getSongs)
    val smartShuffle = SmartShuffle(
        isRecommendationEnabled = { isRecommendationEnabled },
        isRecommendationsLoading = { isRecommendationsLoading },
        onToggleRecommendation = { isRecommendationEnabled = !isRecommendationEnabled }
    )
    val playNext = PlayNext {
        binder?.player?.addNext( getMediaItems(), appContext() )
        itemSelector.isActive = false
    }
    val enqueue = Enqueue {
        binder?.player?.enqueue( getMediaItems(), appContext() )
        itemSelector.isActive = false
    }
    val addToFavorite = LikeComponent(::getSongs)
    val addToPlaylist = PlaylistsMenu.init(
        navController = navController,
        mediaItems = { _ -> getMediaItems() },
        onFailure = { throwable, preview ->
            Timber.tag("HomeSongsScreen").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        finalAction = { itemSelector.isActive = false }
    )
    val smartTrash = SmartTrash(
        builtInPlaylist = { builtInPlaylist },
        getSongs = ::getSongs,
        itemsOnDisplay = { itemsOnDisplayState }
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

    val hasUnmatchedSongs by remember {
        derivedStateOf {
            itemsOnDisplayState.any { (it.id.length != 11 || (it.durationText == "00:00" && it.totalPlayTimeMs == 1L)) && !it.id.startsWith(LOCAL_KEY_PREFIX) }
        }
    }

    val sync = object : MenuIcon, Descriptive {
        override val iconId: Int = R.drawable.sync
        override val messageId: Int = R.string.autosync_likes
        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
        override fun onShortClick() {
            coroutineScope.launch(Dispatchers.IO) { importYTMLikedSongs(force = true) }
        }
        override fun onLongClick() {
            coroutineScope.launch(Dispatchers.IO) { removeYTMLikedSongs() }
        }
    }

    val localMatchButton = remember {
        object : MenuIcon, Descriptive {
            override val iconId: Int = R.drawable.alert
            override val messageId: Int = R.string.match_album_audio_version
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { showConfirmMatchAllDialog = true }
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
            val isEnabled = appContext().preferences.getBoolean(toggleKey, true)
            if (!isEnabled) return@forEach

            when (id) {
                "sort" -> add( if( builtInPlaylist == BuiltInPlaylist.Top ) topPlaylists else songSort )
                "position_lock" -> if ( builtInPlaylist != BuiltInPlaylist.Top && songSort.sortBy == SongSortBy.Custom ) add( positionLock )
                "search" -> add( search )
                "locator" -> add( locator )
                "download_all" -> if (builtInPlaylist != BuiltInPlaylist.OnDevice) add( downloadAllDialog )
                "delete_downloads" -> if (builtInPlaylist != BuiltInPlaylist.OnDevice) add( deleteDownloadsDialog )
                "shuffle" -> add( shuffle )
                "smart_shuffle" -> add( smartShuffle )
                "item_selector" -> add( itemSelector )
                "play_next" -> add( playNext )
                "enqueue" -> add( enqueue )
                "add_to_favorite" -> add( addToFavorite )
                "add_to_playlist" -> add( addToPlaylist )
                "import_menu" -> if (builtInPlaylist == BuiltInPlaylist.All || builtInPlaylist == BuiltInPlaylist.Favorites) add( importMenu )
                "sync_ytm_likes" -> if (builtInPlaylist == BuiltInPlaylist.Favorites && isYouTubeSyncEnabled()) add( sync )
                "export_dialog" -> if (builtInPlaylist != BuiltInPlaylist.OnDevice) add( exportDialog )
                "export_cache" -> if (BuildConfig.ENABLE_FFMPEG && (builtInPlaylist == BuiltInPlaylist.Offline || builtInPlaylist == BuiltInPlaylist.Downloaded)) add(
                    object : MenuIcon, Descriptive {
                        override val iconId: Int = R.drawable.export_outline
                        override val messageId: Int = R.string.export_cached
                        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                        override fun onShortClick() {
                            val count = getSongs().size
                            if (count > 0) showExportCacheConfirmDialog = true
                        }
                        override fun onLongClick() {}
                    }
                )
                "smart_trash" -> if (builtInPlaylist != BuiltInPlaylist.OnDevice) add( smartTrash )
                "match" -> if ( hasUnmatchedSongs && builtInPlaylist != BuiltInPlaylist.OnDevice ) add( localMatchButton )
            }
        }
    }

    Box(
        modifier = Modifier.background( colorPalette().background0 )
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        Column( Modifier.fillMaxSize() ) {
            // Header is rendered directly in the Column (not inside LazyColumn)
            // so it stays stable when switching between HomeSongs and OnDeviceSong
            Column {
                TabHeader( R.string.songs ) {
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
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
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

                importMenu.Render()
                exportDialog.Render()

                if (showExportCacheConfirmDialog) {
                    val count = getSongs().size
                    ConfirmationDialog(
                        text = stringResource(R.string.do_you_really_want_to_export_cached_count, count),
                        onDismiss = { showExportCacheConfirmDialog = false },
                        onConfirm = {
                            showExportCacheConfirmDialog = false
                            showExportCacheLyricsDialog = true
                        }
                    )
                }
                if (showExportCacheLyricsDialog) {
                    val lyricsOptions = listOf(
                        null to stringResource(R.string.no_lyrics),
                        LyricsType.Synced.name to stringResource(R.string.lyrics_synced),
                        LyricsType.Unsynced.name to stringResource(R.string.lyrics_unsynced),
                        LyricsType.Karaoke.name to stringResource(R.string.lyrics_karaoke)
                    )
                    var selectedLyricsType by remember { mutableStateOf(exportCacheLyricsType) }
                    ValueSelectorDialog(
                        onDismiss = { showExportCacheLyricsDialog = false },
                        title = stringResource(R.string.export_lyrics_choice),
                        selectedValue = selectedLyricsType,
                        values = lyricsOptions.map { it.first },
                        onValueSelected = { type ->
                            exportCacheLyricsType = type
                            exportCacheFolderLauncher.launch(null)
                        },
                        valueText = { type -> lyricsOptions.find { it.first == type }?.second ?: "" }
                    )
                }
                if (isExportingCache) {
                    InProgressDialog(
                        total = exportCacheTotal,
                        done = exportCacheProgress,
                        text = stringResource(R.string.exporting),
                        onDismiss = null
                    )
                }

                downloadAllDialog.Render()
                deleteDownloadsDialog.Render()
                smartTrash.Render()

                TabToolBar.Buttons( buttons )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding( horizontal = 16.dp )
                        .padding( bottom = 8.dp )
                        .fillMaxWidth()
                ) {
                    Column {
                        val showFavoritesPlaylist by rememberPreference( showFavoritesPlaylistKey, true )
                        val showCachedPlaylist by rememberPreference( showCachedPlaylistKey, true )
                        val showMyTopPlaylist by rememberPreference( showMyTopPlaylistKey, true )
                        val showDownloadedPlaylist by rememberPreference( showDownloadedPlaylistKey, true )
                        val showOnDeviceChip by rememberPreference( showOnDevicePlaylistKey, true )
                        val showDislikedChip by rememberPreference( showDislikedPlaylistKey, true )
                        val homeSongsOrderPref by rememberPreference( homeSongsOrderKey, "" )
                        val chips = remember( showFavoritesPlaylist, showCachedPlaylist, showMyTopPlaylist, showDownloadedPlaylist, showOnDeviceChip, showDislikedChip, homeSongsOrderPref ) {
                            val songsDefaultOrder = listOf("all", "favorites", "disliked", "cached", "downloaded", "top", "on_device")
                            val toggleMap = mapOf(
                                "favorites" to showFavoritesPlaylist,
                                "cached" to showCachedPlaylist,
                                "downloaded" to showDownloadedPlaylist,
                                "top" to showMyTopPlaylist,
                                "on_device" to showOnDeviceChip,
                                "disliked" to showDislikedChip
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

                        ButtonsRow(
                            chips = chips,
                            currentValue = builtInPlaylist,
                            onValueUpdate = { builtInPlaylist = it },
                            modifier = Modifier.padding(end = 12.dp)
                        )

                        if (isYouTubeSyncEnabled() && (builtInPlaylist == BuiltInPlaylist.Favorites || builtInPlaylist == BuiltInPlaylist.Disliked)) {
                            val menuState = LocalMenuState.current
                            var filterBy by rememberPreference(filterByKey, FilterBy.All)
                            BasicText(
                                text = when (filterBy) {
                                    FilterBy.All -> stringResource(R.string.all)
                                    FilterBy.Local -> stringResource(R.string.on_device)
                                    FilterBy.YoutubeLibrary -> stringResource(R.string.ytm_library)
                                },
                                style = typography().xs.semiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(uiRoundnessShape()).clickable {
                                        menuState.display {
                                            FilterMenu(
                                                title = stringResource(R.string.filter_by),
                                                onDismiss = menuState::hide,
                                                onAll = { filterBy = FilterBy.All },
                                                onYoutubeLibrary = { filterBy = FilterBy.YoutubeLibrary },
                                                onLocal = { filterBy = FilterBy.Local }
                                            )
                                        }
                                    }
                                    .background(colorPalette().background2)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        val exoPlayerDiskCacheMaxSize by rememberPreference(app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey, app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize.`512MB`)
                        val exoPlayerDiskDownloadCacheMaxSize by rememberPreference(app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey, app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize.`2GB`)

                        val showCacheIndicator = when (builtInPlaylist) {
                            BuiltInPlaylist.Offline -> exoPlayerDiskCacheMaxSize != app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize.Unlimited
                            BuiltInPlaylist.Downloaded -> exoPlayerDiskDownloadCacheMaxSize != app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize.Unlimited
                            else -> false
                        }

                        androidx.compose.animation.AnimatedVisibility(
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

            when( builtInPlaylist ) {
                BuiltInPlaylist.OnDevice -> OnDeviceSong( navController, lazyListState, itemSelector, search, buttons, itemsOnDisplayState, ::getSongs )
                else                     -> HomeSongs( navController, builtInPlaylist, lazyListState, itemSelector, search, buttons, itemsOnDisplayState, ::getSongs, matchButton = null, onRecommendationCountChange = { count -> recommendationCount = count }, onRecommendationsLoadingChange = { loading -> isRecommendationsLoading = loading }, isRecommendationEnabled = isRecommendationEnabled, refreshKey = matchRefreshKey, onMatchClick = { showConfirmMatchAllDialog = true } )
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)

        val showFloatingIcon by rememberPreference( showFloatingIconKey, false )
        if( showFloatingIcon )
            MultiFloatingActionsContainer(
                iconId = R.drawable.search,
                onClick = { navController.navigate(NavRoutes.search.name) },
                onClickSettings = { navController.navigate(NavRoutes.settings.name) },
                onClickSearch = { navController.navigate(NavRoutes.search.name) }
            )
    }
}


