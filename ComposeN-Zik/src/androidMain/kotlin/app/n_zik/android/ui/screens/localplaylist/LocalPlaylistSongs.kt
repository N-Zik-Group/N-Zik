package app.n_zik.android.ui.screens.localplaylist

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import timber.log.Timber

import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.MONTHLY_PREFIX
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.RawUrlImportDialog
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.dialog.playlist.ChangePlaylistCoverDialog
import app.n_zik.android.components.dialog.playlist.PlaylistInsertionDialog
import app.n_zik.android.components.dialog.playlist.RenamePlaylistDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistToolbarSettingsDialog
import app.n_zik.android.components.dialog.song.UpdateSongDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassControlOverflowColumn
import app.n_zik.android.components.glass.GlassToolbarButtons
import app.n_zik.android.components.glass.GlassToolbarColumn
import app.n_zik.android.components.glass.GlassToolbarLoading
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.components.glass.glassGlobalIndication
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.playlist.LocalPlaylistHeaderSurfaceScope
import app.n_zik.android.components.playlist.PinPlaylist
import app.n_zik.android.components.playlist.PlaylistSongsSort
import app.n_zik.android.components.playlist.RefreshPlaylistOnOpening
import app.n_zik.android.components.playlist.Reposition
import app.n_zik.android.components.playlist.canOfferPlaylistDeleteAction
import app.n_zik.android.components.playlist.launchPlaylistSourceRefresh
import app.n_zik.android.components.playlist.localPlaylistHeaderSurface
import app.n_zik.android.components.playlist.playlistMembershipSwipeAction
import app.n_zik.android.components.playlist.playlistRefreshOnOpenAction
import app.n_zik.android.components.podcast.launchSavedPodcastBookmarkToggle
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewAssets
import app.n_zik.android.components.shuffleButtonIcon
import app.n_zik.android.components.tab.ImportPlaylistsMenu
import app.n_zik.android.components.tab.ImportSongsFromCSV
import app.n_zik.android.components.tab.ImportSongsFromServices
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.LikeComponent
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.components.ui.InlineCircularLoader
import app.n_zik.android.components.ui.screens.home.GlassHomeChips
import app.n_zik.android.components.ui.screens.home.onDevice.OnDeviceCoverBadge
import app.n_zik.android.compose.persist.persistList
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.database.canBookmarkPlaylist
import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.core.database.editStoredPlaylistMembership
import app.n_zik.android.core.database.isPlaylistBookmarked
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.ondevice.DeviceLibraryResults
import app.n_zik.android.core.ondevice.observeDeviceLibrary
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.components.playlist.rememberPlaylistDownloadStates
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.PlaylistSongSortBy
import app.n_zik.android.enums.QueueSwipeAction
import app.n_zik.android.enums.RecommendationsNumber
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.isUnmatched
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.themed.n_zik.component.playlist.PositionLock
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.ui.components.SwipeableQueueItem
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Dialog
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.Bookmark
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.themed.HeaderIconButton
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.components.themed.IconInfo
import app.n_zik.android.ui.components.themed.ListenOnYouTube
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.ui.components.themed.Playlist
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.ui.components.themed.ResetThumbnail
import app.n_zik.android.ui.components.themed.Synchronize
import app.n_zik.android.ui.components.themed.ThumbnailPicker
import app.n_zik.android.ui.items.FullSongListPlaceholderCount
import app.n_zik.android.ui.items.ItemArtworkPlaceholder
import app.n_zik.android.ui.items.ItemContainer
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.ui.styling.overlay
import app.n_zik.android.ui.styling.px
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.ExternalUris
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.center
import app.n_zik.android.utils.checkFileExists
import app.n_zik.android.utils.color
import app.n_zik.android.utils.compactPlayTimeLabel
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.deleteFileIfExists
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.excludeMediaItems
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getSyncDirection
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.localPlaylistToolbarOrderKey
import app.n_zik.android.utils.manageDownload
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.playlist.importSongsToPlaylist
import app.n_zik.android.utils.playlist.supportsInsertionPosition
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.queueSwipeLeftActionKey
import app.n_zik.android.utils.queueSwipeRightActionKey
import app.n_zik.android.utils.recommendationsNumberKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.playlistPage
import app.n_zik.innertube.requests.relatedSongs

@KotlinCsvExperimental
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation", "UnrememberedMutableState")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun LocalPlaylistSongs(
    navController: NavController,
    playlistId: Long,
    onDelete: () -> Unit,
    forceOnDevice: Boolean = false,
) {
    // Essentials
    val context = LocalContext.current
    val forceShimmer = forceLoadingShimmerPreview()
    val glassSettings = rememberGlassSettings()
    val glassActions = glassSettings.usesGlassMaterial(GlassArea.ActionButtons)
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    // Settings
    val parentalControlEnabled by rememberPreference( parentalControlEnabledKey, false )

    val disableScrollingText by rememberPreference( disableScrollingTextKey, false )
    // Full offline mode (spec-full-offline-mode): network toolbar actions removed,
    // the import dialog prunes its network choices; listen_on_yt is EXTERNAL and stays.
    val offlineModeState = OfflineMode.rememberEnabled()
    val offlineMode by offlineModeState
    var onDeviceSelected by rememberSaveable(playlistId) { mutableStateOf(false) }
    val onDeviceOnly = forceOnDevice || onDeviceSelected
    var isRecommendationEnabled by remember { mutableStateOf(false) }

    // Non-vital

    val preview = app.n_zik.android.components.settings.appearance.preview.LocalThemePreview.current
    val thumbnailUrl = remember(playlistId, preview) {
        mutableStateOf(if (preview == null) "" else android.net.Uri.fromFile(ThemePreviewAssets.file(appContext(), "tide.jpg")).toString())
    }
    val playlistResult by remember(playlistId, preview) {
        val source = if (preview != null) {
            kotlinx.coroutines.flow.flowOf(Playlist(id = playlistId, name = "Sunday Headphones"))
        } else {
            Database.playlistTable.findById(playlistId)
        }
        source.map { Result.success(it) }
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    val playlist = playlistResult?.getOrNull()

    // Spec 2: generated `rewind-*` playlists are protected (Spotify principle) and get
    // the "Rewind Top" sort option; declared before the sort wiring that consumes it
    val membershipEditableState = rememberUpdatedState(!forceOnDevice && playlist?.let(::canEditPlaylistMembership) == true)
    val membershipEditable by membershipEditableState
    if (preview == null && !onDeviceOnly) RefreshPlaylistOnOpening(playlistId, playlist, offlineMode)
    val refreshOnOpen = playlistRefreshOnOpenAction(playlist)

    val isRewindPlaylist = playlist?.name?.let { RewindPlaylists.isRewind(it) } == true

    val sort = PlaylistSongsSort(playlistId, isRewind = isRewindPlaylist)

    val loadedItems by remember( sort.sortBy, sort.sortOrder, preview ) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo.queue)
        else if (sort.sortBy == PlaylistSongSortBy.Downloaded) {
            val downloaded: List<String> = MyDownloadHelper.downloads
                                                           .value
                                                           .values
                                                           .filter { it.state == Download.STATE_COMPLETED }
                                                           .map { it.request.id }
            Database.songPlaylistMapTable
                    .sortSongs( playlistId, PlaylistSongSortBy.Title, SortOrder.Ascending )
                    .map { list ->
                        if (sort.sortOrder == SortOrder.Ascending) {
                            list.sortedByDescending { it.id in downloaded }
                        } else {
                            list.sortedBy { it.id in downloaded }
                        }
                    }
                    .flowOn( NzikDispatchers.DATA )
                    .distinctUntilChanged()
        } else {
            Database.songPlaylistMapTable
                    .sortSongs( playlistId, sort.sortBy, sort.sortOrder )
                    .flowOn( NzikDispatchers.DATA )
                    .distinctUntilChanged()
        }
    }.collectAsStateWithLifecycle( null, context = NzikDispatchers.DATA )
    val playlistTracks = loadedItems.orEmpty()
    val hasDeviceTracks = playlistTracks.any { it.isLocal }
    val deviceOnlyPlaylist = playlistTracks.isNotEmpty() && playlistTracks.all { it.isLocal }
    LaunchedEffect(loadedItems, hasDeviceTracks) {
        if (loadedItems != null && !hasDeviceTracks) onDeviceSelected = false
    }
    val profile = getActiveProfile(context)
    val scan by remember(context, profile, onDeviceOnly) {
        if (onDeviceOnly) context.observeDeviceLibrary() else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(DeviceLibraryResults.current(profile))
    val items = loadedItems.orEmpty().let { songs ->
        if (onDeviceOnly) songs.filter { it.isLocal && it.id in scan?.songIds.orEmpty() } else songs
    }
    var filteredQuery by remember { mutableStateOf<String?>(null) }
    var itemsOnDisplay by persistList<Song>("localPlaylist/$playlistId/${if (forceOnDevice) "on_device/" else ""}songs/on_display")

    val importedBrowseIds = remember {
        listOf("SPOTIFY_IMPORT", "RIPLAY_IMPORT") +
        listOf("SPOTIFY_IMPORT_HOMESONGS", "RIPLAY_IMPORT_HOMESONGS")
    }
    LaunchedEffect( playlist?.browseId, items ) {
        if (items.isEmpty()) return@LaunchedEffect
        val browseId = playlist?.browseId.orEmpty()
        val isSpotifyRiplay = importedBrowseIds.any { browseId == it || browseId.startsWith("${it}_") }
        val hasImportedSongs = items.any { it.totalPlayTimeMs == 1L }
        val prefs = appContext().preferences
        val currentSort = prefs.getString("PlaylistSongsSortBy_$playlistId", null)
        // Only force Custom if no saved preference exists AND playlist has imports
        if ((isSpotifyRiplay || hasImportedSongs) && currentSort == null) {
            sort.sortBy = PlaylistSongSortBy.Custom
        } else if (isRewindPlaylist && currentSort == null) {
            // Spec 2: a generated rewind-* playlist defaults to its top-order snapshot
            sort.sortBy = PlaylistSongSortBy.RewindTop
        }
    }

    val itemSelector = ItemSelector<Song>()

    fun getSongs() = localPlaylistActionSongs(itemsOnDisplay, itemSelector, onDeviceOnly)

    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val search = Search(lazyListState)

    val isSpecialPlaylist = playlist?.browseId?.removePrefix("VL") in listOf("LM", "SE")
    
    var isBookmarked by remember(playlist) { mutableStateOf(isPlaylistBookmarked(playlist)) }

    val bookmark = Bookmark(isBookmarked = isBookmarked) {
        if (!canBookmarkPlaylist(playlist)) return@Bookmark
        if (launchSavedPodcastBookmarkToggle(
                playlist, coroutineScope,
                onRemoved = { navController.popBackStack() },
                onExistingImport = { playlist?.browseId?.let { NavRoutes.podcast.navigateHere(navController, it) } },
            )) return@Bookmark
        if (isSpecialPlaylist) {
            Toaster.e(R.string.playlist_bookmark_unsupported_message)
            return@Bookmark
        }
        val wasBookmarked = isBookmarked
        coroutineScope.launch(NzikDispatchers.DATA) {
            val p = playlist ?: return@launch
            val browseId = p.browseId
            val pushPlaylist = appContext().preferences.getBoolean(syncPushPlaylistKey, false)
            val syncDir = getSyncDirection()
            if (browseId != null && isYouTubeSyncEnabled() && pushPlaylist && syncDir != SyncDirection.YT_TO_APP && isNetworkConnected(appContext())) {
                runCatching {
                    if (wasBookmarked) {
                        YtMusic.removelikePlaylistOrAlbum(browseId.removePrefix("VL"))
                    } else {
                        YtMusic.likePlaylistOrAlbum(browseId.removePrefix("VL"))
                    }
                }.onFailure { e ->
                    Timber.tag("LocalPlaylistSongs").e(e, "Failed to toggle YTM bookmark")
                }
            }
            Database.playlistTable.update(
                p.copy(isYoutubePlaylist = !wasBookmarked)
            )
            withContext(NzikDispatchers.UI) {
                isBookmarked = !wasBookmarked
            }
            Toaster.s( if (!wasBookmarked) R.string.library_favorites_added else R.string.library_favorites_removed )
        }
    }

    val matchAction = localPlaylistSongMatching(
        playlistId = playlistId,
        inPreview = preview != null,
        onDeviceOnly = onDeviceOnly,
        offlineModeState = offlineModeState,
        membershipEditableState = membershipEditableState,
        items = items,
        getSongs = ::getSongs,
    )

    val shuffle = SongShuffler ( ::getSongs )
    val insertionDialog = PlaylistInsertionDialog { playlist }
    insertionDialog.Render()
    val renameDialog = RenamePlaylistDialog { playlist }
    val exportDialog = ExportSongsToCSVDialog(
        playlistBrowseId = playlist?.browseId.orEmpty(),
        playlistName = playlist?.name ?: "",
        songs = ::getSongs
    )
    val importNzikDialog = ImportSongsFromCSV(targetPlaylistId = playlistId, onImportComplete = {
        appContext().preferences.edit().putString("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Custom.name).apply()
    })
    val importSpotifyDialog = ImportSongsFromServices.init(
        afterTransaction = { finalPosition, song, _, _ ->
            // Already handled by ImportSongsFromServices internally
        },
        playlistIdForMatch = playlistId,
        playlistName = playlist?.name ?: "",
        source = "SPOTIFY_IMPORT",
        onImportComplete = {
            appContext().preferences.edit().putString("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Custom.name).apply()
        }
    )
    val importRiplayDialog = ImportSongsFromServices.init(
        afterTransaction = { finalPosition, song, _, _ ->
        },
        playlistIdForMatch = playlistId,
        playlistName = playlist?.name ?: "",
        source = "RIPLAY_IMPORT",
        onImportComplete = {
            appContext().preferences.edit().putString("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Custom.name).apply()
        }
    )

    var showYouTubeLinkDialog by remember { mutableStateOf(false) }
    DialogPresence(visible = showYouTubeLinkDialog) {
        RawUrlImportDialog(
            title = stringResource(R.string.playlist_import_youtube_link),
            onDismiss = { showYouTubeLinkDialog = false },
            placeholder = stringResource(R.string.playlist_import_youtube_link_hint),
            onImport = { url ->
                showYouTubeLinkDialog = false
                val targetPlaylist = playlist ?: return@RawUrlImportDialog
                coroutineScope.launch(NzikDispatchers.DATA) {
                    val urlPlaylistId = listOf(
                        "https://www.youtube.com/playlist?",
                        "https://youtube.com/playlist?",
                        "https://music.youtube.com/playlist?",
                        "https://m.youtube.com/playlist?"
                    ).find { url.startsWith(it) }?.let { url.toUri().getQueryParameter("list") }

                    if (urlPlaylistId != null) {
                        val browseId = if (urlPlaylistId.startsWith("VL")) urlPlaylistId else "VL$urlPlaylistId"
                        Innertube.playlistPage(browseId = browseId)?.getOrNull()?.let { playlistPage ->
                            val songs = playlistPage.songsPage?.items?.map { it.asSong.copy(totalPlayTimeMs = 1L) }.orEmpty()
                            if (songs.isNotEmpty()) {
                                Database.asyncTransaction {
                                    importSongsToPlaylist(targetPlaylist, songs)
                                }
                                appContext().preferences.edit().putString("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Custom.name).apply()
                                Toaster.done()
                            }
                        }
                    }
                }
            }
        )
    }

    val importMenu = remember { ImportPlaylistsMenu(
        onImportNzik = { importNzikDialog.onShortClick() },
        onImportSpotify = { importSpotifyDialog.onShortClick() },
        onImportRiplay = { importRiplayDialog.onShortClick() },
        onImportYoutubeLink = { showYouTubeLinkDialog = true },
        networkOptionsEnabled = { !offlineMode }
    ) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val deleteDialog = remember {
        object : MenuIcon, Descriptive, Dialog {
            override val iconId: Int = R.drawable.trash
            override val messageId: Int = R.string.common_action_delete
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            @get:Composable override val dialogTitle: String get() = stringResource(messageId)
            override fun onShortClick() {
                if (canOfferPlaylistDeleteAction(playlist, itemSelector.isActive && itemSelector.isNotEmpty())) {
                    showDeleteConfirmDialog = true
                }
            }
            override fun onLongClick() {}
            override var isActive: Boolean
                get() = showDeleteConfirmDialog
                set(value) { showDeleteConfirmDialog = value }

            @Composable
            override fun Render() {
                DialogPresence(visible = showDeleteConfirmDialog &&
                    canOfferPlaylistDeleteAction(playlist, itemSelector.isActive && itemSelector.isNotEmpty())) {
                    val selectedSongs = itemSelector.toList()
                    val hasSelection = itemSelector.isActive && selectedSongs.isNotEmpty()
                    val text = if (hasSelection) {
                        stringResource(R.string.playlist_remove_songs_confirmation, selectedSongs.size)
                    } else {
                        stringResource(R.string.playlist_delete_confirmation)
                    }
                    ConfirmationDialog(
                        title = stringResource(if (hasSelection) R.string.playlist_action_remove_songs else R.string.playlist_delete_dialog_title),
                        confirmText = stringResource(if (hasSelection) R.string.common_action_remove else R.string.common_action_delete),
                        text = text,
                        onDismiss = { showDeleteConfirmDialog = false },
                        onConfirm = {
                            showDeleteConfirmDialog = false
                            if (hasSelection) {
                                if (!membershipEditable) return@ConfirmationDialog
                                coroutineScope.launch(NzikDispatchers.DATA) {
                                    val removed = editStoredPlaylistMembership(playlistId) {
                                        selectedSongs.forEach { song ->
                                            songPlaylistMapTable.deleteBySongId(song.id, playlistId)
                                        }
                                    }
                                    if (removed) withContext(NzikDispatchers.UI) {
                                        Toaster.s(
                                            context.getString(
                                                R.string.playlist_remove_songs_success_format,
                                                context.getString(R.string.common_delete_success),
                                                selectedSongs.size,
                                            ),
                                        )
                                        itemSelector.isActive = false
                                    }
                                }
                            } else {
                                coroutineScope.launch(NzikDispatchers.DATA) {
                                    Database.asyncTransaction {
                                        playlist?.let { playlistTable.delete(it) }
                                    }
                                    withContext(NzikDispatchers.UI) {
                                        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED)
                                            navController.popBackStack()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    val renumberDialog = Reposition(playlistId)
    val downloadAllDialog = DownloadAllSongsDialog ( ::getSongs )
    val deleteDownloadsDialog = DeleteAllDownloadedSongsDialog ( ::getSongs )
    val pin = PinPlaylist( playlist )
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }
    LaunchedEffect( itemSelector.isActive ) {
        // Setting this field to true means disable it
        if( itemSelector.isActive )
            positionLock.isFirstIcon = true
    }
    // Either position lock or item selector can be turned on at a time
    LaunchedEffect( positionLock.isFirstIcon ) {
        if( !positionLock.isFirstIcon ) {
            // Open to move position
            itemSelector.isActive = false
            // Disable smart recommendation, it breaks the index
            isRecommendationEnabled = false
        }
    }

    val playNext = PlayNext {
        val songs = getSongs().toList()

        // Turn of selector clears the selected list
        itemSelector.isActive = false
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            val mediaItems = withContext(NzikDispatchers.DATA) {
                val player = binder?.player ?: return@withContext emptyList()
                player.excludeMediaItems(songs.map(Song::asMediaItem), appContext())
            }
            val player = binder?.player ?: return@launch
            player.addNext(mediaItems)
        }
    }
    val enqueue = Enqueue {
        val songs = getSongs().toList()

        // Turn of selector clears the selected list
        itemSelector.isActive = false
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            val mediaItems = withContext(NzikDispatchers.DATA) {
                val player = binder?.player ?: return@withContext emptyList()
                player.excludeMediaItems(songs.map(Song::asMediaItem), context)
            }
            val player = binder?.player ?: return@launch
            player.enqueue(mediaItems)
        }
    }
    val addToFavorite = LikeComponent( ::getSongs )

    val addToPlaylist = PlaylistsMenu.init(
        navController,
        {


            getMediaItems()
        },
        { throwable, preview ->
            Timber.tag("LocalPlaylistSongs").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        {
            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    )

    fun sync() {
        launchPlaylistSourceRefresh(playlist, coroutineScope)
    }
    val syncComponent = Synchronize { sync() }
    val listenOnYT = ListenOnYouTube {
        val browseId = playlist?.browseId?.removePrefix(MODIFIED_PREFIX)?.removePrefix( "VL" )

        binder?.player?.pause()
        uriHandler.openUri( ExternalUris.youtubePlaylist(browseId ?: "") )
    }
    val updateSongsDialog = UpdateSongDialog( batchTargets = ::getSongs )

    val changePlaylistCover = ChangePlaylistCoverDialog(
        getPlaylistId = { playlistId.takeIf { playlist?.id == it } },
        getThumbnail = { thumbnailUrl.value.takeIf(String::isNotBlank) },
        setThumbnail = { thumbnailUrl.value = it },
    )
    val thumbnailPicker = ThumbnailPicker { changePlaylistCover.showDialog() }

    fun resetThumbnail() {
        if(thumbnailUrl.value == "") {
            Toaster.w( R.string.thumbnail_empty )
            return
        }
        val thumbnailName = "thumbnail/playlist_${playlist?.id}"
        val retVal = deleteFileIfExists(context, thumbnailName)
        if(retVal == true){
            Toaster.s( R.string.thumbnail_remove_success )
            thumbnailUrl.value = ""
        } else
            Toaster.e( R.string.thumbnail_remove_failed )
    }
    val resetThumbnail = ResetThumbnail { resetThumbnail() }

    val locator = Locator( lazyListState, ::getSongs )

    //<editor-fold defaultstate="collapsed" desc="Smart recommendation">
    val recommendationsNumber by rememberPreference( recommendationsNumberKey, RecommendationsNumber.Adaptive )
    var relatedSongs by rememberSaveable {
        // SongEntity before Int in case random position is equal
        mutableStateOf( emptyMap<Song, Int>() )
    }
    var isRecommendationsLoading by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect( isRecommendationEnabled ) {
        if( !isRecommendationEnabled ) {
            relatedSongs = emptyMap()
            isRecommendationsLoading = false
            return@LaunchedEffect
        }

        while( items.isEmpty() )
            delay( 100L )

        isRecommendationsLoading = true

        relatedSongs = loadLocalPlaylistRecommendations(items, recommendationsNumber)
        isRecommendationsLoading = false
        
        // Enable position lock
        positionLock.isFirstIcon = true
    }
    //</editor-fold>
    LaunchedEffect(items, relatedSongs, search.inputValue, parentalControlEnabled, onDeviceOnly, isRecommendationEnabled) {
        val query = search.inputValue
        itemsOnDisplay = projectLocalPlaylistSongs(
            songs = items,
            recommendations = if (isRecommendationEnabled) relatedSongs else emptyMap(),
            query = query,
            onDeviceOnly = onDeviceOnly,
            parentalControlEnabled = parentalControlEnabled,
        )
        filteredQuery = query
    }
    SearchEmptyFeedback(
        query = search.inputValue,
        empty = itemsOnDisplay.isEmpty(),
        loading = loadedItems == null || filteredQuery != search.inputValue
    )
    LaunchedEffect( playlistId, playlist?.name ) {
        if (preview != null) return@LaunchedEffect
        val thumbnailName = "thumbnail/playlist_${playlistId}"
        val presentThumbnailUrl: String? = checkFileExists(context, thumbnailName)
        if (presentThumbnailUrl != null) {
            thumbnailUrl.value = presentThumbnailUrl
        }
    }


    

    val hapticFeedback = LocalHapticFeedback.current
    val reorderingState = rememberReorderableLazyListState(
        lazyListState = lazyListState
    ) { from, to ->
        if (onDeviceOnly) return@rememberReorderableLazyListState
        val mutableItems = itemsOnDisplay.toMutableList()
        val fromIndex = mutableItems.indexOfFirst { it.id == from.key }
        val toIndex = mutableItems.indexOfFirst { it.id == to.key }
        
        if (fromIndex != -1 && toIndex != -1) {
            val movedSong = mutableItems.removeAt( fromIndex )
            mutableItems.add( toIndex, movedSong )
            itemsOnDisplay = mutableItems

            // asyncTransaction is non-suspending: its block runs on the Room transaction executor, not on NzikDispatchers.MEDIA (the launch is a no-op).
            NzikDispatchers.fireAndForget(NzikDispatchers.MEDIA).launch {
                Database.asyncTransaction {
                    mutableItems.forEachIndexed { index, song ->
                        Database.songPlaylistMapTable.updatePosition( playlistId, song.id, index )
                    }
                }
            }
        }
    }

    changePlaylistCover.Render()
    renameDialog.Render()
    exportDialog.Render()
    deleteDialog.Render()
    (renumberDialog as Dialog).Render()
    downloadAllDialog.Render()
    deleteDownloadsDialog.Render()
    importMenu.Render()
    updateSongsDialog.Render()

    val playlistThumbnailSizeDp = Dimensions.thumbnails.playlist
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val rippleIndication = glassGlobalIndication(ripple(bounded = false))

    val playlistNotMonthlyType =
        playlist?.name?.startsWith(MONTHLY_PREFIX, 0, true) == false &&
        // Spec 2: pinning prefixes the name, breaking the rewind-* naming contract
        !isRewindPlaylist

    val songIds = remember(itemsOnDisplay) { itemsOnDisplay.map { it.id } }
    val likeStatesMap by remember(songIds) {
        LikeStateManager.getLikeStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistStatesMap by remember(songIds) {
        PlaylistStateManager.getPlaylistStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    // Download state cache
    val (downloadsMapState, downloadStatesMap) = rememberPlaylistDownloadStates(songIds)

    // Hoisted swipe action preferences
    val queueSwipeLeftAction by rememberPreference(queueSwipeLeftActionKey, QueueSwipeAction.RemoveFromQueue)
    val queueSwipeRightAction by rememberPreference(queueSwipeRightActionKey, QueueSwipeAction.PlayNext)

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
                .fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
        LazyColumn(
                contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
            state = lazyListState,
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                GlassControlOverflowColumn {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {

                    HeaderWithIcon(
                        // Spec 2: localized display name for generated rewind-* playlists
                        title = context.rewindDisplayName( cleanPrefix( playlist?.name ?: "" ) ),
                        iconId = R.drawable.playlist,
                        enabled = true,
                        showIcon = false,
                        modifier = Modifier
                            .padding(bottom = 8.dp),
                        onClick = {}
                    )

                }

                LocalPlaylistHeaderSurfaceScope(glassSettings) { exported ->
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        //.background(colorPalette().background4)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .localPlaylistHeaderSurface(glassSettings, exported)
                ) {

                    if (forceShimmer || playlistResult == null) {
                        ItemContainer(
                            alternative = true,
                            thumbnailSizeDp = playlistThumbnailSizeDp,
                        ) {
                            ItemArtworkPlaceholder(playlistThumbnailSizeDp, card = true, shape = thumbnailShape())
                        }
                    } else playlist?.let {
                        Playlist(
                            playlist = it,
                            songCount = items.size,
                            thumbnailSizeDp = playlistThumbnailSizeDp,
                            thumbnailSizePx = playlistThumbnailSizePx,
                            alternative = true,
                            showName = false,
                            disableScrollingText = disableScrollingText,
                            thumbnailUrl = thumbnailUrl.value.takeIf(String::isNotEmpty),
                            coverOverlay = {
                                if (preview == null && (forceOnDevice || deviceOnlyPlaylist)) {
                                    OnDeviceCoverBadge(Modifier.align(Alignment.TopEnd))
                                }
                            },
                        )
                    }


                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            //.fillMaxHeight()
                            .padding(end = 10.dp)
                            .weight(1f)
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val totalSongs = if (!onDeviceOnly && isRecommendationEnabled && !isRecommendationsLoading && relatedSongs.isNotEmpty()) {
                            items.size + relatedSongs.size
                        } else {
                            items.size
                        }
                        val metadataReady = loadedItems != null && (!onDeviceOnly || scan != null)
                        IconInfo(
                            title = if (!metadataReady) null
                                else if (preview == null) totalSongs.toString()
                                else pluralStringResource(R.plurals.library_song_count, totalSongs, totalSongs),
                            icon = painterResource(R.drawable.musical_notes)
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        val recommendedSongsDuration = if (!onDeviceOnly && isRecommendationEnabled && !isRecommendationsLoading) {
                            relatedSongs.keys.sumOf { durationTextToMillis(it.durationText ?: "0:0") }
                        } else {
                            0L
                        }
                        IconInfo(
                            title = if (metadataReady) {
                                formatAsTime(items.sumOf { durationTextToMillis(it.durationText ?: "0:0") } + recommendedSongsDuration)
                            } else null,
                            icon = painterResource(R.drawable.time)
                        )
                        if (isRecommendationEnabled) {
                            Spacer(modifier = Modifier.height(5.dp))
                            if (isRecommendationsLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Keyed on the effective tone, not a hardcoded white (spec-achromatic-ramp-luminance-cap)
                                    Icon(
                                        painter = painterResource(R.drawable.smart_shuffle),
                                        contentDescription = null,
                                        tint = colorPalette().text,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    InlineCircularLoader(
                                        modifier = Modifier.size(16.dp),
                                        color = colorPalette().text
                                    )
                                }
                            } else {
                                IconInfo(
                                    title = relatedSongs.size.toString(),
                                    icon = painterResource(R.drawable.smart_shuffle)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(30.dp))
                    }

                    val recommendationButton: @Composable () -> Unit = {
                        if (isRecommendationsLoading) {
                            if (!GlassToolbarLoading()) InlineCircularLoader(
                                modifier = Modifier.size(24.dp),
                                color = colorPalette().text,
                            )
                        } else {
                            HeaderIconButton(
                                icon = R.drawable.smart_shuffle,
                                enabled = true,
                                color = if (isRecommendationEnabled) colorPalette().text else colorPalette().textDisabled,
                                modifier = Modifier.clip(uiRoundnessShape()),
                                        onClick = {
                                            isRecommendationEnabled = !isRecommendationEnabled
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.recommendations_action_smart_shuffle )
                                        }
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        if (glassActions) {
                            GlassToolbarColumn(actionCount = 2, settings = glassSettings) {
                                if (!onDeviceOnly) recommendationButton()
                                shuffle.ToolBarButton()
                            }
                            if (canBookmarkPlaylist(playlist)) {
                                Spacer(modifier = Modifier.height(10.dp))
                                GlassToolbarRow(actionCount = 1, settings = glassSettings) {
                                    bookmark.ToolBarButton()
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.size(48.dp), // Standard IconButton size
                                contentAlignment = Alignment.Center
                            ) {
                                if (!onDeviceOnly) recommendationButton()
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            shuffle.ToolBarButton()
                            // Bookmarking saves to the YouTube Music library: meaningless for a local playlist
                            if (canBookmarkPlaylist(playlist)) {
                                Spacer(modifier = Modifier.height(10.dp))
                                bookmark.ToolBarButton()
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val toolbarOrderPref by rememberPreference(localPlaylistToolbarOrderKey, "")

                val hasUnmatchedSongs = remember(items, preview) { preview == null && items.any { it.isUnmatched } }

                val order = LocalPlaylistToolbarSettingsDialog.parseOrder(toolbarOrderPref)

                val toolbarPreferences = LocalContext.current.preferences
                val toolbarButtons = buildList {
                    order.forEach { id ->
                        // Check toggle state - if disabled, skip this button
                        val toggleKey = "pl_ts_$id"
                        val isEnabled = toolbarPreferences.getBoolean(toggleKey, true)
                        if (!isEnabled) return@forEach

                        when (id) {
                            "insertion_position" -> if (playlist?.supportsInsertionPosition() == true) add(insertionDialog)
                            "pin" -> if (!forceOnDevice && playlistNotMonthlyType) add( pin )
                            "search" -> add( search )
                            // Spec 2: renumbering/position-lock rewrites position, destroying the top order
                            "position_lock" -> if ( !onDeviceOnly && sort.sortBy == PlaylistSongSortBy.Custom && !isRewindPlaylist ) add( positionLock )
                            "match" -> if (!onDeviceOnly &&
                                (matchAction.isRunning || (membershipEditable && !offlineMode && hasUnmatchedSongs))
                            ) add(matchAction.button)
                            "renumber" -> if ( !onDeviceOnly && sort.sortBy == PlaylistSongSortBy.Custom && !isRewindPlaylist ) add( renumberDialog )
                            "download_all" -> if (!onDeviceOnly && !offlineMode) add(downloadAllDialog)
                            "delete_downloads" -> if (!onDeviceOnly) add(deleteDownloadsDialog)
                            "item_selector" -> add( itemSelector )
                            "play_next" -> add( playNext )
                            "enqueue" -> add( enqueue )
                            "add_to_favorite" -> add( addToFavorite )
                            "add_to_playlist" -> add( addToPlaylist )
                            "sync" -> if (!onDeviceOnly && !offlineMode && canRefreshPlaylist(playlist)) add( syncComponent )
                            "refresh_on_open" -> if (!onDeviceOnly && canRefreshPlaylist(playlist)) add(refreshOnOpen)
                            "listen_on_yt" -> if (!onDeviceOnly && playlistLinkKind(playlist?.browseId) != PlaylistLinkKind.Local) add( listenOnYT )
                            "import_menu" -> if (!onDeviceOnly && membershipEditable) add( importMenu )
                            // Spec 2: rewind-* keep their language-neutral name â€” no rename
                            "rename" -> if (!forceOnDevice && !isRewindPlaylist) add( renameDialog )
                            "delete" -> if (!forceOnDevice && canOfferPlaylistDeleteAction(playlist, itemSelector.isActive && itemSelector.isNotEmpty())) add(deleteDialog)
                            "export" -> add( exportDialog )
                            "thumbnail_picker" -> if (!forceOnDevice) add(thumbnailPicker)
                            "reset_thumbnail" -> if (!forceOnDevice) add(resetThumbnail)
                            "update" -> if (!onDeviceOnly && !offlineMode) add( updateSongsDialog )
                        }
                    }
                }

                GlassToolbarButtons( toolbarButtons )


                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .fillMaxWidth()
                ) {

                    if (glassActions) {
                        GlassToolbarRow(actionCount = 1, settings = glassSettings) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                sort.ToolBarButton()
                            }
                        }
                    } else {
                        sort.ToolBarButton()
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (glassActions) {
                            GlassToolbarRow(actionCount = 1, settings = glassSettings) {
                                locator.ToolBarButton()
                            }
                        } else {
                            locator.ToolBarButton()
                        }
                    }

                }

                if (preview == null && !forceOnDevice && hasDeviceTracks) GlassHomeChips(
                    chips = listOf(
                        false to stringResource(R.string.common_option_all),
                        true to stringResource(R.string.library_on_device_title),
                    ),
                    currentValue = onDeviceOnly,
                    onValueUpdate = { selected ->
                        if (onDeviceOnly != selected) {
                            itemSelector.clear()
                            onDeviceSelected = selected
                            if (selected) isRecommendationEnabled = false
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                )
                Column { search.SearchBar( this ) }
                }
            }
                if (search.inputValue.isNotBlank() && loadedItems != null &&
                    filteredQuery == search.inputValue && itemsOnDisplay.isEmpty()
                ) {
                    item(key = "search_no_results") {
                        SearchEmptySpace(fillRemainingViewport = true)
                    }
                }
            val displayedSongs = if (onDeviceOnly) itemsOnDisplay.filter { it.isLocal } else itemsOnDisplay
            if (loadedItems == null && displayedSongs.isEmpty()) {
                item(key = "loading_songs") { SongItemPlaceholders(count = FullSongListPlaceholderCount) }
            }
            itemsIndexed(
                items = displayedSongs,
                key = { _, song -> song.id },
                contentType = { _, song -> song },
            ) { index, song ->
                if (forceShimmer) {
                    SongItemPlaceholder(title = song.title, artist = song.artistsText, onDevice = song.isLocal)
                    return@itemsIndexed
                }

                    ReorderableItem(
                        reorderingState,
                        key = song.id
                    ) { isDraggingItem ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(2f)
                                .then(if (preview != null && index == 0) {
                                    Modifier.onGloballyPositioned { preview.previewRowsPlaced.value = it.size.height > 0 }
                                } else Modifier)
                        ) {
                            val isLocal by remember { derivedStateOf { song.asMediaItem.isLocal } }

                            // Drag anchor
                            if ( !onDeviceOnly && !positionLock.isLocked() && sort.sortBy == PlaylistSongSortBy.Custom && sort.sortOrder == SortOrder.Ascending ) {
                                Box(
                                    modifier = Modifier.padding( end = 16.dp ) // Accommodate horizontal padding of SongItem
                                                       .size( 24.dp )
                                                       .zIndex( 2f )
                                                       .align( Alignment.CenterEnd ),
                                    contentAlignment = Alignment.Center
                                ) {

                                    IconButton(
                                        icon = R.drawable.reorder,
                                        color = colorPalette().textDisabled,
                                        indication = rippleIndication,
                                        onClick = {},
                                        modifier = Modifier
                                            .draggableHandle(
                                                onDragStarted = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )
                                    )
                                }
                            }

                            SwipeableQueueItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(song.asMediaItem)
                        },
                        onRemoveFromQueue = {
                            if (membershipEditable) {
                                Database.asyncTransaction {
                                    if (editStoredPlaylistMembership(playlistId) {
                                        songPlaylistMapTable.deleteBySongId(song.id, playlistId)
                                    }) {
                                        Toaster.s(
                                            "${context.resources.getString(R.string.common_delete_success)} \"${cleanPrefix(song.title)}\""
                                        )
                                    }
                                }
                            }
                        },
                        onDownload = {
                            binder?.cache?.removeResource(song.asMediaItem.mediaId)
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( song.id )
                            }

                            if (!isLocal) {
                                manageDownload(
                                    context = context,
                                    mediaItem = song.asMediaItem,
                                    downloadState = song.isLocal
                                )
                            }
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(
                                song.asMediaItem,
                                context
                            )
                        },
                        downloadStateParam = downloadsMapState[song.id]?.state ?: Download.STATE_STOPPED,
                        downloadedStateMediaParam = downloadStatesMap[song.id] ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                        swipeLeftActionParam = playlistMembershipSwipeAction(playlist, queueSwipeLeftAction),
                        swipeRightActionParam = playlistMembershipSwipeAction(playlist, queueSwipeRightAction),
                    ) {
                        SongItem(
                            song = song,
                            isLiked = likeStatesMap[song.id],
                            inPlaylist = playlistStatesMap[song.id],
                            itemSelector = itemSelector,
                            navController = navController,
                            isRecommended = song in relatedSongs,
                            modifier = Modifier,

                            trailingContent = {
                                if (preview == null && song.isUnmatched) {
                                    Icon(
                                        painter = painterResource(R.drawable.alert),
                                        contentDescription = stringResource(R.string.song_matching_unmatched_label),
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.padding(start = 8.dp).size(18.dp)
                                    )
                                }
                                if( !onDeviceOnly && !positionLock.isLocked() && sort.sortBy == PlaylistSongSortBy.Custom && sort.sortOrder == SortOrder.Ascending )
                                    // Create a fake box to store drag anchor and checkbox
                                    Box( Modifier.width( 24.dp ) )
                            },
                            thumbnailOverlay = {
                                if (sort.sortBy == PlaylistSongSortBy.PlayTime || sort.sortBy == PlaylistSongSortBy.RelativePlayTime || sort.sortBy == PlaylistSongSortBy.PlayCount) {
                                    val text = if (sort.sortBy == PlaylistSongSortBy.PlayCount) {
                                        song.playCount.toString()
                                    } else {
                                        compactPlayTimeLabel(song.totalPlayTimeMs)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(thumbnailShape())
                                            .background(colorPalette().overlay)
                                    ) {
                                        BasicText(
                                            text = text,
                                            style = typography().xxs.semiBold.center.color(
                                                colorPalette().onOverlay
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .align(Alignment.BottomCenter)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                if (itemSelector.isActive) {
                                    search.hideIfEmpty()
                                    return@SongItem
                                }
                                if (song.isUnmatched) {
                                    Toaster.w(R.string.player_matching_required_message)
                                } else {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(
                                        displayedSongs.map(Song::asMediaItem),
                                        index
                                    )

                                    search.hideIfEmpty()
                                }
                            }
                        )
                    }
                }
            }
        }
        }
        } // CompositionLocalProvider

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)

        val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
        if ( showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                // Issue #866 (gh-866): app-wide shuffle confirmation flash.
                iconId = shuffleButtonIcon(),
                visible = !reorderingState.isAnyItemDragging,
                onClick = {
                    getSongs().let { songs ->
                        val playableSongs = songs.filter { !it.isUnmatched }
                        if (playableSongs.isNotEmpty()) {
                            binder?.let {
                                it.triggerShuffleOkFlash()
                                Shuffler.play(it, playableSongs)
                            }
                        } else {
                            Toaster.w(R.string.player_matching_required_message)
                        }
                    }
                }
            )
    }
}











internal fun localPlaylistActionSongs(
    visibleSongs: List<Song>,
    selectedSongs: List<Song>,
    onDeviceOnly: Boolean,
): List<Song> {
    if (!onDeviceOnly) return selectedSongs.ifEmpty { visibleSongs }
    val localSongs = visibleSongs.filter { it.isLocal }
    if (selectedSongs.isEmpty()) return localSongs
    val selectedIds = selectedSongs.mapTo(HashSet()) { it.id }
    return localSongs.filter { it.id in selectedIds }
}
