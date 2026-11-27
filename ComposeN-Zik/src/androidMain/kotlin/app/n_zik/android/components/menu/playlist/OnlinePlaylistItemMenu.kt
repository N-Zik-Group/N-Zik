package app.n_zik.android.components.menu.playlist

import app.n_zik.android.components.menu.MenuSectionTitle
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.components.menu.MenuMetadataText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.components.navigation.LibraryBrowseKind
import app.n_zik.android.components.navigation.rememberLibraryBrowseDestination
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.n_zik.android.components.podcast.podcastBookmarkOperation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.LocalMenuEntry
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Clickable
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.withPodcastSource
import app.n_zik.android.utils.canPushToYTM
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.components.menu.collectionMenuPickerSongs
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.playlist.ChangePlaylistBrowseIdDialog
import app.n_zik.android.components.dialog.playlist.RenamePlaylistAuthorDialog
import app.n_zik.android.components.dialog.playlist.RenamePlaylistDialog
import app.n_zik.android.components.dialog.playlist.ImportPlaylistDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import app.n_zik.android.components.menu.rememberMenuSongs
import app.n_zik.android.components.menu.ObserveMenuProfile
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.MenuBookmarkBadge
import app.n_zik.android.components.menu.MenuMediaHeaderDragHandle
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.PendingLibraryItemMenu
import app.n_zik.android.components.menu.menuLikeActionDescription
import app.n_zik.android.components.menu.menuSheetSectionBackground
import app.n_zik.android.components.menu.openExternalMenuLink
import app.n_zik.android.components.podcast.PodcastBookmarkOperation
import app.n_zik.android.components.podcast.togglePodcastBookmark
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.collectionQueueActions
import app.n_zik.android.components.menu.withMenuClick
import app.n_zik.android.components.menu.withMenuGuestLock
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.database.findMusicBookmarkByBrowseId
import app.n_zik.android.core.database.findMusicBookmarkRows
import app.n_zik.android.core.database.canBookmarkPlaylist
import app.n_zik.android.core.database.isPlaylistBookmarked
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.surfaceColor
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.utils.ToastRelay
import app.n_zik.android.utils.coroutines.NzikDispatchers

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class OnlinePlaylistItemMenu private constructor(
    private val navController: NavController,
    private val playlist: Innertube.PlaylistItem,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    private val ownerProfile = getActiveProfile(appContext())

    companion object {
        @Composable
        operator fun invoke(navController: NavController, playlist: Innertube.PlaylistItem): OnlinePlaylistItemMenu =
            OnlinePlaylistItemMenu(
                navController = navController,
                playlist = playlist,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    private data class MenuSection(val title: Int, val actions: List<MenuIcon>)

    private var sections by mutableStateOf(emptyList<MenuSection>())
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = PlaylistListMenu(sections)

    @Composable
    private fun PlaylistListMenu(sections: List<MenuSection>) = ListMenu.Menu(title = null, showDragHandle = false) {
        sections.forEach { section ->
            MenuSectionTitle(stringResource(section.title))
            section.actions.forEach { it.ListMenuItem() }
        }
    }

    @Composable
    override fun GridMenu() = PlaylistGridMenu(sections)

    @Composable
    private fun PlaylistGridMenu(sections: List<MenuSection>) = GridMenu.Menu(title = null, showDragHandle = false) {
        sections.forEach { section ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(section.title))
            }
            section.actions.forEach { action -> item { action.GridMenuItem() } }
        }
    }

    @Composable
    private fun PlaylistItemDisplay(
        sourceId: String,
        shareUrl: String?,
        title: String?,
        authorText: String?,
        thumbnailUrl: String?,
        songs: List<Song>?,
        contentLoadFailed: Boolean,
        owner: PlaylistOwner?,
        onExistingPodcastImport: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxWidth()
                .menuSheetSectionBackground(surfaceColor(colorPalette().background1))
        ) {
            MenuMediaHeaderDragHandle()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = Dimensions.itemsVerticalPadding,
                        horizontal = 16.dp
                    )
            ) {
                // Playlist's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2)
                ) {
                    // Clipped thumbnail content
                    Box(
                        modifier = Modifier
                            .size(Dimensions.thumbnails.album / 2)
                            .clip(thumbnailShape())
                    ) {
                        ImageCacheFactory.Thumbnail(
                            thumbnailUrl = thumbnailUrl,
                            modifier = Modifier.size(Dimensions.thumbnails.album / 2)
                        )
                    }

                    val localPlaylistFlow = remember(sourceId) { Database.playlistTable.findMusicBookmarkByBrowseId(sourceId) }
                    val localPlaylist by localPlaylistFlow.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
                    val isBookmarked = isPlaylistBookmarked(localPlaylist)

                    if (isBookmarked) MenuBookmarkBadge(bookmarked = true)
                }

                // Playlist's information
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    MenuMetadataText(
                        text = cleanPrefix(title ?: ""),
                        style = typography().xs.semiBold.copy(
                            color = colorPalette().text,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )

                    MenuMetadataText(
                        text = cleanPrefix(authorText.orEmpty()),
                        style = typography().xs.semiBold.secondary.copy(
                            color = colorPalette().textSecondary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                }

                // Trailing content (Bookmark & Share)
                val localPlaylistFlow = remember(sourceId) { Database.playlistTable.findMusicBookmarkByBrowseId(sourceId) }
                val localPlaylist by localPlaylistFlow.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
                val isBookmarked = isPlaylistBookmarked(localPlaylist)
                val bookmarkSource = Playlist(name = "", browseId = sourceId)
                val canBookmark = canBookmarkPlaylist(bookmarkSource)
                val coroutineScope = rememberCoroutineScope()
                var bookmarkBusy by remember(sourceId) { mutableStateOf(false) }
                val bookmarkContent by rememberUpdatedState(Triple(songs, contentLoadFailed, owner))
                val openExistingPodcastImport by rememberUpdatedState(onExistingPodcastImport)
                val isPodcast = isPodcastBrowseId(sourceId)

                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (canBookmark) IconButton(
                        contentDescription = menuLikeActionDescription(
                            isBookmarked, false, R.string.library_action_bookmark, R.string.library_action_remove_bookmark,
                        ),
                        icon = if (isBookmarked) R.drawable.bookmark else R.drawable.bookmark_outline,
                        color = colorPalette().favoritesIcon,
                        enabled = !bookmarkBusy,
                        onClick = {
                            if (bookmarkBusy || !canBookmark) return@IconButton
                            if (isPodcast) {
                                bookmarkBusy = true
                                coroutineScope.launch {
                                    try {
                                        val pendingOperation = podcastBookmarkOperation(localPlaylist)
                                        val ready = if (pendingOperation == PodcastBookmarkOperation.Remove) bookmarkContent
                                            else snapshotFlow { bookmarkContent }.first { it.first != null || it.second }
                                        if (pendingOperation != PodcastBookmarkOperation.Remove && ready.first == null) {
                                            warnPlaylistSongsUnavailable(true)
                                            return@launch
                                        }
                                        val operation = togglePodcastBookmark(
                                            sourceId,
                                            title.orEmpty(),
                                            ready.first.orEmpty().map { it.asMediaItem },
                                            owner = ready.third ?: authorText?.let { Innertube.Info(it, null) },
                                        )
                                        if (operation == PodcastBookmarkOperation.ExistingImport) openExistingPodcastImport()
                                        else Toaster.done()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        Timber.tag("PodcastBookmark").e(error, "Unable to update podcast bookmark from search")
                                        Toaster.e(R.string.playlist_import_failed)
                                    } finally {
                                        bookmarkBusy = false
                                    }
                                }
                                return@IconButton
                            }
                            if (!isBookmarked && owner == null && songs == null) {
                                warnPlaylistSongsUnavailable(contentLoadFailed)
                                return@IconButton
                            }
                            val app = context.applicationContext
                            val workOwner = ProfileWorkOwner.capture(app) ?: return@IconButton
                            if (workOwner.id != ownerProfile || !ProfileWorkOwner.isBound(app, workOwner)) return@IconButton
                            bookmarkBusy = true
                            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                try {
                                    if (!ProfileWorkOwner.isBound(app, workOwner)) return@launch
                                    val browseId = sourceId
                                    val playlistId = browseId.removePrefix("VL")
                                    val updated = Database.awaitWriteBatch {
                                        var updatedPlaylist: Playlist? = null
                                        transaction {
                                            if (!ProfileWorkOwner.isBound(app, workOwner)) return@transaction
                                            val rows = playlistTable.findMusicBookmarkRows(browseId).first()
                                            val bookmarkedRows = rows.filter { isPlaylistBookmarked(it) }
                                            if (!ProfileWorkOwner.isBound(app, workOwner)) return@transaction
                                            if (bookmarkedRows.isNotEmpty()) {
                                                bookmarkedRows.forEach { playlistTable.update(it.copy(isYoutubePlaylist = false)) }
                                                updatedPlaylist = bookmarkedRows.first().copy(isYoutubePlaylist = false)
                                                return@transaction
                                            }

                                            val existing = rows.firstOrNull()
                                            val saved = (existing?.copy(isYoutubePlaylist = true) ?: Playlist(
                                                name = title.orEmpty(),
                                                browseId = "VL$playlistId",
                                                isYoutubePlaylist = true,
                                                isEditable = false,
                                            )).withSourcePlaylistOwner(owner)
                                            updatedPlaylist = if (existing != null) {
                                                saved.takeIf { playlistTable.update(saved) > 0 }
                                            } else {
                                                playlistTable.insert(saved).takeIf { it > 0 }?.let { saved.copy(id = it) }
                                            }
                                        }
                                        updatedPlaylist
                                    } ?: return@launch
                                    if (!ProfileWorkOwner.isBound(app, workOwner)) return@launch
                                    val bookmarked = updated.isYoutubePlaylist
                                    if (bookmarked) {
                                        MyDownloadHelper.downloadBookmarkedPlaylist(app, browseId, updated)
                                    }
                                    if (!ProfileWorkOwner.isBound(app, workOwner)) return@launch
                                    Toaster.s(if (bookmarked) R.string.library_favorites_added else R.string.library_favorites_removed)
                                    if (isYouTubeSyncEnabled() && canPushToYTM() &&
                                        app.preferences.getBoolean(syncPushPlaylistKey, false) && isNetworkConnected(app) &&
                                        ProfileWorkOwner.isBound(app, workOwner)
                                    ) {
                                        val result = if (bookmarked) YtMusic.likePlaylistOrAlbum(playlistId)
                                        else YtMusic.removelikePlaylistOrAlbum(playlistId)
                                        val error = result.exceptionOrNull()
                                        if (error is CancellationException) throw error
                                        if (error != null) Toaster.e(R.string.common_load_failed)
                                    }
                                } finally {
                                    withContext(NzikDispatchers.UI) { bookmarkBusy = false }
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )

                    if (shareUrl != null) IconButton(
                        icon = R.drawable.share_social,
                        contentDescription = stringResource(R.string.player_mini_button_share),
                        color = colorPalette().text,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )
                }
            }

            GlassHorizontalSeparator(Modifier.height(1.dp))
        }
    }

    @Composable
    override fun MenuComponent() {
        if (!ObserveMenuProfile(ownerProfile, menuState::hide)) return
        val binder = LocalPlayerServiceBinder.current
        val context = LocalContext.current
        val offlineMode by OfflineMode.rememberEnabled()

        val kind = if (isPodcastBrowseId(playlist.key)) LibraryBrowseKind.Podcast else LibraryBrowseKind.Playlist
        val sourceId = rememberLibraryBrowseDestination(playlist.key, kind)?.browseId
        val currentSourceId by rememberUpdatedState(sourceId)
        if (sourceId == null) {
            PendingLibraryItemMenu()
            return
        }
        val shareUrl = onlinePlaylistShareUrl(sourceId)
        val isPodcastSource = isPodcastBrowseId(sourceId.removePrefix(MODIFIED_PREFIX))
        val sourceChanged = sourceId != playlist.key

        var fetchedSongs by remember(sourceId) { mutableStateOf<List<Song>?>(null) }
        var partialSongs by remember(sourceId) { mutableStateOf<List<Song>?>(null) }
        var contentLoadFailed by remember(sourceId) { mutableStateOf(false) }
        
        var displayTitle by remember(sourceId) { mutableStateOf((playlist.title ?: playlist.info?.name).takeUnless { sourceChanged }) }
        var displayAuthor by remember(sourceId) { mutableStateOf(playlist.channel?.name.takeUnless { sourceChanged }) }
        var ownerChannel by remember(sourceId) { mutableStateOf(playlist.channel.takeUnless { sourceChanged }) }
        var displayThumbnailUrl by remember(sourceId) { mutableStateOf(playlist.thumbnail?.url.takeUnless { sourceChanged }) }

        val localPlaylistFlow = remember(sourceId) { Database.playlistTable.findMusicBookmarkByBrowseId(sourceId) }
        val localPlaylist by localPlaylistFlow.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
        val savedPlaylistId = localPlaylist?.id?.takeIf { offlineMode || contentLoadFailed }
        val savedSongs by remember(savedPlaylistId) {
            savedPlaylistId?.let { Database.songPlaylistMapTable.allSongsOf(it) } ?: flowOf(null)
        }.collectAsStateWithLifecycle<List<Song>?>(null, context = NzikDispatchers.DATA)
        val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
        val importableSongs = remember(fetchedSongs, parentalControlEnabled) {
            fetchedSongs?.filterNot { parentalControlEnabled && it.title.hasExplicitPrefix() }
        }
        val menuSongs = remember(importableSongs, savedSongs, parentalControlEnabled) {
            importableSongs ?: savedSongs?.filterNot { parentalControlEnabled && it.title.hasExplicitPrefix() }
        }
        val songs by rememberMenuSongs(menuSongs)
        val contentUnavailable = contentLoadFailed || offlineMode
        val rename = RenamePlaylistDialog { localPlaylist }
        val renameAuthor = RenamePlaylistAuthorDialog { localPlaylist }
        val changePlaylistId = ChangePlaylistBrowseIdDialog(menuState = menuState) { localPlaylist }

        LaunchedEffect(sourceId, offlineMode) {
            if (offlineMode) return@LaunchedEffect
            contentLoadFailed = false
            if (isPodcastSource) partialSongs = null
            withContext(NzikDispatchers.DATA) {
                val result = loadOnlinePlaylistContent(
                    sourceId.removePrefix(MODIFIED_PREFIX),
                    fetchAllowed = { !OfflineMode.enabled(context) },
                )
                currentCoroutineContext().ensureActive()
                if (result != null) {
                    displayTitle = result.title.takeIf { !it.isNullOrBlank() } ?: displayTitle
                    displayAuthor = result.author.takeIf { !it.isNullOrBlank() } ?: displayAuthor
                    ownerChannel = result.channel ?: ownerChannel
                    displayThumbnailUrl = result.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: displayThumbnailUrl
                }
                fetchedSongs = result?.songs
                partialSongs = result?.takeIf { it.songs == null }?.availableSongs
                contentLoadFailed = result?.songs == null
            }
        }

        val (playNext, enqueue) = collectionQueueActions(context, menuState, { binder?.captureMenuQueueEdit() }, songs, contentUnavailable) {
            it.asMediaItem.withPodcastSource(sourceId)
        }

        val pickerTracks = remember(songs, partialSongs, parentalControlEnabled) {
            collectionMenuPickerSongs(songs, partialSongs, parentalControlEnabled)
        }
        val pickerSongs by rememberMenuSongs(pickerTracks)
        val playlistsMenu = PlaylistsMenu.init(
            navController = navController,
            mediaItems = {
                if (isPodcastSource && currentSourceId != sourceId) emptyList()
                else {
                    val filterExplicit = isPodcastSource && context.preferences.getBoolean(parentalControlEnabledKey, false)
                    pickerSongs.orEmpty().filterNot { filterExplicit && it.title.hasExplicitPrefix() }
                        .map { it.asMediaItem.withPodcastSource(sourceId) }
                }
            },
            onFailure = { _, _ -> },
            finalAction = { menuState.hide() }
        )

        val downloadAllDialog = DownloadAllSongsDialog { songs ?: emptyList() }
        val downloadAll = downloadAllDialog.withMenuClick {
            if (OfflineMode.enabled(context)) return@withMenuClick
            val currentSongs = songs
            when {
                currentSongs == null -> warnPlaylistSongsUnavailable(contentUnavailable)
                currentSongs.isEmpty() -> Toaster.e(R.string.player_no_song_found)
                else -> downloadAllDialog.onShortClick()
            }
        }

        val deleteAllDialog = DeleteAllDownloadedSongsDialog { songs ?: emptyList() }
        val deleteAll = deleteAllDialog.withMenuClick {
            val currentSongs = songs
            when {
                currentSongs == null -> warnPlaylistSongsUnavailable(contentUnavailable)
                currentSongs.isEmpty() -> Toaster.e(R.string.player_no_song_found)
                else -> deleteAllDialog.onShortClick()
            }
        }
        
        val importStatus = when {
            importableSongs?.isNotEmpty() == true -> CollectionImportStatus.Ready
            importableSongs != null -> CollectionImportStatus.Empty
            contentUnavailable -> CollectionImportStatus.Unavailable
            else -> CollectionImportStatus.Loading
        }
        val importDialog = key(sourceId) {
            ImportPlaylistDialog(
                initialValue = cleanPrefix(localPlaylist?.name ?: displayTitle ?: ""),
                unavailableMessage = importStatus.unavailableMessage,
            ) { text ->
                if (importableSongs.isNullOrEmpty()) return@ImportPlaylistDialog
                val importSongs = songs?.takeIf { it.isNotEmpty() } ?: return@ImportPlaylistDialog
                val appContext = context.applicationContext
                val owner = ProfileWorkOwner.capture(appContext) ?: return@ImportPlaylistDialog
                val newPlaylist = Playlist(name = text, browseId = sourceId).withSourcePlaylistOwner(ownerChannel)
                val menuKey = menuState.transitionKey
                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                    try {
                        val committed = Database.awaitWriteBatch {
                            if (!ProfileWorkOwner.isBound(appContext, owner)) return@awaitWriteBatch false
                            importOnlinePlaylist(newPlaylist, importSongs)
                            true
                        }
                        if (!committed || !ProfileWorkOwner.isBound(appContext, owner)) return@launch
                        withContext(NzikDispatchers.UI) {
                            if (menuState.isCurrentMenu(menuKey)) menuState.hide()
                            Toaster.done()
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.tag("OnlinePlaylistItemMenu").e(error, "Unable to import playlist")
                        Toaster.e(R.string.playlist_import_failed)
                    }
                }
            }
        }
        importDialog.RenderAboveMenu(menuState)
        val addToPlaylist = rememberCollectionAddAction(
            sourceId = sourceId,
            menuState = menuState,
            canImportCollection = importStatus.canOpenImport,
            hasTracks = !pickerSongs.isNullOrEmpty(),
            importDialogOpen = importDialog.isActive,
            openImport = importDialog::open,
            menuEntry = LocalMenuEntry.current,
            openPicker = playlistsMenu::openPicker,
        )

        val shuffleAction = SongShuffler.fromMediaItems {
            songs.orEmpty().map { it.asMediaItem.withPodcastSource(sourceId) }
        }
        val shuffle = shuffleAction.withMenuGuestLock {
            if (songs == null) warnPlaylistSongsUnavailable(contentUnavailable)
            else shuffleAction.onShortClick()
        }

        val navigationActions = buildList<MenuIcon> {
            add(OpenCollectionMenuAction(navController, menuState, sourceId, R.drawable.open, useExactSource = true))
            val savedOwner = localPlaylist?.sourcePlaylistOwner()
            val sourceOwner = ownerChannel?.takeIf { !it.endpoint?.browseId.isNullOrBlank() }
            val displayOwner = displayAuthor?.takeIf(String::isNotBlank)?.let { PlaylistOwner(it, null) }
            val navigationOwner = if (!savedOwner?.endpoint?.browseId.isNullOrBlank()) savedOwner
                else sourceOwner?.copy(name = savedOwner?.name ?: sourceOwner.name ?: displayAuthor)
                    ?: savedOwner ?: displayOwner
            playlistOwnerAction(navigationOwner, navController, menuState)?.let(::add)
            if (!offlineMode && shareUrl != null) add(object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.play
                override val messageId: Int = R.string.player_action_listen_on_youtube_music
                @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                override fun onShortClick() {
                    if (openExternalMenuLink {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl)))
                    }) {
                        menuState.hide()
                    }
                }
                override fun onLongClick() {}
            })
        }
        val menuSections = listOf(
            MenuSection(R.string.common_playback_section_title, listOf(shuffle, playNext, enqueue)),
            MenuSection(
                R.string.library_management_section_title,
                buildList {
                    add(addToPlaylist)
                    if (!offlineMode) add(downloadAll)
                    add(deleteAll)
                    if (localPlaylist != null) {
                        add(rename)
                        add(renameAuthor)
                        if (playlistLinkKind(localPlaylist?.browseId) == PlaylistLinkKind.YouTubePlaylist) {
                            add(changePlaylistId)
                        }
                    }
                },
            ),
            MenuSection(R.string.common_navigation_section_title, navigationActions),
        )
        SideEffect { sections = menuSections }

        MenuSheetSurface {
            downloadAllDialog.Render()
            deleteAllDialog.Render()
            rename.Render()
            renameAuthor.Render()
            changePlaylistId.Render()
            PlaylistItemDisplay(
                sourceId = sourceId,
                shareUrl = shareUrl,
                title = localPlaylist?.name ?: displayTitle,
                authorText = localPlaylist?.displayAuthorName() ?: displayAuthor,
                thumbnailUrl = displayThumbnailUrl,
                songs = songs,
                contentLoadFailed = contentUnavailable,
                owner = ownerChannel,
                onExistingPodcastImport = {
                    if (importStatus == CollectionImportStatus.Ready) importDialog.onShortClick()
                    else warnPlaylistSongsUnavailable(contentUnavailable)
                },
            )

            if (menuStyle == MenuStyle.List)
                PlaylistListMenu(menuSections)
            else
                PlaylistGridMenu(menuSections)
        }
    }

}

internal suspend fun Database.importOnlinePlaylist(playlist: Playlist, songs: List<Song>) {
    val importSongs = songs.distinctBy(Song::id)
    require(importSongs.isNotEmpty())
    transaction {
        val playlistId = playlistTable.insert(playlist)
        check(playlistId > 0) { "Online playlist could not be inserted" }
        importSongs.forEachIndexed { index, song ->
            insertIgnore(song.asMediaItem)
            songPlaylistMapTable.mapAtPosition(song.id, playlistId, index)
        }
    }
}

private fun warnPlaylistSongsUnavailable(loadFailed: Boolean) {
    if (loadFailed) Toaster.e(R.string.common_load_failed)
    else ToastRelay.loadingWarning()
}
