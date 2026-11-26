package app.n_zik.android.components.menu.playlist

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.innertube.YtMusic
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.cleanPrefix
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.podcastPage
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Clickable
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.DynamicColor
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.withPodcastSource
import app.n_zik.android.utils.checkFileExists
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.getSyncDirection
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.playlist.ChangePlaylistBrowseIdDialog
import app.n_zik.android.components.dialog.playlist.canChangePlaylistBrowseId
import app.n_zik.android.components.dialog.playlist.PlaylistInsertionDialog
import app.n_zik.android.components.dialog.playlist.RenamePlaylistDialog
import app.n_zik.android.components.dialog.playlist.RenamePlaylistAuthorDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.components.menu.EntityMenuSnapshot
import app.n_zik.android.components.menu.ObserveEntityMenuSnapshot
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.MenuBookmarkBadge
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.menuLikeActionDescription
import app.n_zik.android.components.menu.menuSheetSectionBackground
import app.n_zik.android.components.menu.openExternalMenuLink
import app.n_zik.android.components.playlist.launchPlaylistSourceRefresh
import app.n_zik.android.components.playlist.DeletePlaylistConfirmation
import app.n_zik.android.components.playlist.launchLocalPlaylistDeletion
import app.n_zik.android.components.playlist.playlistRefreshOnOpenAction
import app.n_zik.android.components.podcast.launchSavedPodcastBookmarkToggle
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.collectionQueueActions
import app.n_zik.android.components.menu.withMenuClick
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.canBookmarkPlaylist
import app.n_zik.android.core.database.canRefreshPlaylist
import app.n_zik.android.core.database.isPlaylistBookmarked
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.surfaceColor
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.utils.ToastRelay
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.playlist.supportsInsertionPosition

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class LocalPlaylistItemMenu private constructor(
    private val navController: NavController,
    playlistPreview: PlaylistPreview,
    override val menuState: MenuState,
    private val onDevice: Boolean,
    styleState: MutableState<MenuStyle>
) : Menu {

    private val initialPreview = playlistPreview
    private val snapshot = EntityMenuSnapshot(playlistPreview.playlist, getActiveProfile(appContext())) { it.id }
    private var songs by mutableStateOf<List<Song>?>(null)
    private val playlistPreview: PlaylistPreview
        get() = initialPreview.copy(playlist = snapshot.value, songCount = songs?.size ?: initialPreview.songCount)

    companion object {
        @Composable
        operator fun invoke(navController: NavController, playlistPreview: PlaylistPreview, onDevice: Boolean = false): LocalPlaylistItemMenu =
            LocalPlaylistItemMenu(
                navController = navController,
                playlistPreview = playlistPreview,
                onDevice = onDevice,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    lateinit var buttons: List<Button>
    private var offlineMode = false
    private val hasYouTubeLink: Boolean
        get() = !onDevice && playlistLinkKind(playlistPreview.playlist.browseId) != PlaylistLinkKind.Local
    private val externalPlaylistUrl: String?
        get() = playlistPreview.playlist.browseId?.takeIf { hasYouTubeLink && !offlineMode }?.let(::onlinePlaylistShareUrl)
    private val hasExternalPlaylistUrl: Boolean
        get() = externalPlaylistUrl != null
    override var menuStyle: MenuStyle by styleState

    private fun openPlaylistAction(): OpenCollectionMenuAction? {
        val browseId = playlistPreview.playlist.browseId?.takeIf { hasYouTubeLink } ?: return null
        return OpenCollectionMenuAction(navController, menuState, browseId, R.drawable.open, useExactSource = true)
    }

    @Composable
    override fun ListMenu() = LocalPlaylistListMenu(buttons, ownerAction = null)

    @Composable
    private fun LocalPlaylistListMenu(buttons: List<Button>, ownerAction: MenuIcon?) = ListMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        SectionTitle(stringResource(R.string.common_playback_section_title))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }

        buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Management
        SectionTitle(stringResource(R.string.library_management_section_title))
        val mgmtEnd = if (hasExternalPlaylistUrl) buttons.size - 1 else buttons.size
        for (i in 3 until mgmtEnd) {
            // idx 3 = download_all (network)
            if (offlineMode && !onDevice && i == 3) continue
            buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
        }

        val openAction = openPlaylistAction()
        if (openAction != null || ownerAction != null || hasExternalPlaylistUrl) {
            SectionTitle(stringResource(R.string.common_navigation_section_title))
        }
        openAction?.ListMenuItem()
        ownerAction?.ListMenuItem()
        if (hasExternalPlaylistUrl) {
            buttons.lastOrNull()?.let { if (it is MenuIcon) it.ListMenuItem() }
        }
    }

    @Composable
    override fun GridMenu() = LocalPlaylistGridMenu(buttons, ownerAction = null)

    @Composable
    private fun LocalPlaylistGridMenu(buttons: List<Button>, ownerAction: MenuIcon?) = GridMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.common_playback_section_title))
        }
        buttons.getOrNull(0)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        // Section: Management
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.library_management_section_title))
        }
        val mgmtEnd = if (hasExternalPlaylistUrl) buttons.size - 1 else buttons.size
        for (i in 3 until mgmtEnd) {
            // idx 3 = download_all (network)
            if (offlineMode && !onDevice && i == 3) continue
            buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }

        val openAction = openPlaylistAction()
        if (openAction != null || ownerAction != null || hasExternalPlaylistUrl) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.common_navigation_section_title))
            }
        }
        openAction?.let { item { it.GridMenuItem() } }
        ownerAction?.let { item { it.GridMenuItem() } }
        if (hasExternalPlaylistUrl) {
            buttons.lastOrNull()?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }
    }

    @Composable
    private fun PlaylistItemDisplay(
        playlistPreview: PlaylistPreview,
        isBookmarked: Boolean,
        ownerName: String?,
        bookmarkEnabled: Boolean,
        onBookmarkToggle: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        // Bookmarking saves to the YouTube Music library: meaningless for a local playlist
        val canBookmark = canBookmarkPlaylist(playlistPreview.playlist)
        val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
        val context = LocalContext.current
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxWidth()
                .menuSheetSectionBackground(surfaceColor(colorPalette().background1))
        ) {
            MenuSheetDragHandle()

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
                    val thumbnails by remember(snapshot) {
                        playlistMenuThumbnails(
                            songs = Database.songPlaylistMapTable.sortSongsByPlayTime(snapshot.id),
                            revisions = ArtworkRevisions.changes,
                            ownsProfile = { getActiveProfile(context) == snapshot.profile },
                            customThumbnail = { checkFileExists(context, "thumbnail/playlist_${snapshot.id}") }
                        )
                    }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

                    // Clipped thumbnail content
                    Box(
                        modifier = Modifier
                            .size(Dimensions.thumbnails.album / 2)
                            .clip(thumbnailShape())
                            .glassImageRim(thumbnailShape())
                    ) {
                        if (thumbnails.isEmpty()) {
                            Image(
                                painter = painterResource(R.drawable.library),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colorPalette().textSecondary),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(Dimensions.thumbnails.album / 4)
                            )
                        } else if (thumbnails.size == 1) {
                            ImageCacheFactory.Thumbnail(
                                shape = null,
                                thumbnailUrl = thumbnails[0],
                                modifier = Modifier.size(Dimensions.thumbnails.album / 2)
                            )
                        } else {
                            // 4 grid
                            Row(modifier = Modifier.size(Dimensions.thumbnails.album / 2)) {
                                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    ImageCacheFactory.Thumbnail(shape = null, thumbnailUrl = thumbnails[0], modifier = Modifier.weight(1f).fillMaxWidth())
                                    if (thumbnails.size > 2) {
                                        ImageCacheFactory.Thumbnail(shape = null, thumbnailUrl = thumbnails[2], modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                }
                                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (thumbnails.size > 1) {
                                        ImageCacheFactory.Thumbnail(shape = null, thumbnailUrl = thumbnails[1], modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                    if (thumbnails.size > 3) {
                                        ImageCacheFactory.Thumbnail(shape = null, thumbnailUrl = thumbnails[3], modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }

                    if (canBookmark && isBookmarked) MenuBookmarkBadge(bookmarked = true)
                }

                // Playlist's information
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
                    BasicText(
                        text = cleanPrefix(playlistPreview.playlist.name),
                        style = typography().xs.semiBold.copy(color = colorPalette().text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                    ownerName?.takeIf(String::isNotBlank)?.let { name ->
                        BasicText(
                            text = name,
                            style = typography().xs.semiBold.secondary.copy(color = colorPalette().textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicText(
                        text = playlistPreview.menuItemCount(),
                        style = typography().xs.semiBold.secondary.copy(color = colorPalette().textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                }

                // Trailing content (Bookmark & Open)
                val coroutineScope = rememberCoroutineScope()
                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (canBookmark)
                        IconButton(
                            contentDescription = menuLikeActionDescription(
                                isBookmarked, false, R.string.library_action_bookmark, R.string.library_action_remove_bookmark,
                            ),
                            icon = if (isBookmarked) R.drawable.bookmark else R.drawable.bookmark_outline,
                            color = colorPalette().favoritesIcon,
                            enabled = bookmarkEnabled,
                            onClick = onBookmarkToggle,
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
        if (!ObserveEntityMenuSnapshot(snapshot, { Database.playlistTable.findById(it) }, menuState::hide)) return
        val observedSongs = remember(snapshot) { Database.songPlaylistMapTable.allSongsOf(snapshot.id) }
            .collectAsStateWithLifecycle<List<Song>?>(null, context = NzikDispatchers.DATA).value
        SideEffect { songs = if (onDevice) observedSongs?.filter { it.isLocal } else observedSongs }
        val context = LocalContext.current
        val observedOfflineMode by OfflineMode.rememberEnabled()
        offlineMode = observedOfflineMode
        val binder = LocalPlayerServiceBinder.current
        val coroutineScope = rememberCoroutineScope()
        
        val savedPlaylist = playlistPreview.playlist
        val ownerBrowseId = savedPlaylist.browseId?.takeIf {
            !onDevice && !offlineMode && savedPlaylist.authorBrowseId == null &&
                playlistLinkKind(it) != PlaylistLinkKind.Local
        }
        LaunchedEffect(savedPlaylist.id, ownerBrowseId) {
            val id = ownerBrowseId ?: return@LaunchedEffect
            withContext(NzikDispatchers.DATA) {
                val table = Database.playlistTable
                val source = id.removePrefix(MODIFIED_PREFIX)
                val owner = if (playlistLinkKind(id) == PlaylistLinkKind.YouTubePodcast) {
                    Innertube.podcastPage(source).getOrNull()?.owner
                } else {
                    YtMusic.getPlaylist(source).getOrNull()?.playlist?.channel
                }
                val enriched = savedPlaylist.withSourcePlaylistOwner(owner)
                val authorId = enriched.authorBrowseId
                val authorName = enriched.authorName ?: return@withContext
                table.updateSourceAuthor(savedPlaylist.id, id, authorId, authorName)
            }
        }
        val ownerAction = playlistOwnerAction(savedPlaylist.sourcePlaylistOwner(), navController, menuState)

        val insertionDialog = PlaylistInsertionDialog { playlistPreview.playlist }
        var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

        val downloadAllDialog = DownloadAllSongsDialog { songs ?: emptyList() }
        val downloadAll = downloadAllDialog.withMenuClick {
            if (OfflineMode.enabled(context)) return@withMenuClick
            val currentSongs = songs
            when {
                currentSongs == null -> ToastRelay.loadingWarning()
                currentSongs.isEmpty() -> Toaster.e(R.string.player_no_song_found)
                else -> downloadAllDialog.onShortClick()
            }
        }

        val refreshOnOpen = playlistRefreshOnOpenAction(playlistPreview.playlist)

        val deleteAllDialog = DeleteAllDownloadedSongsDialog { songs ?: emptyList() }
        val deleteAll = deleteAllDialog.withMenuClick {
            val currentSongs = songs
            when {
                currentSongs == null -> ToastRelay.loadingWarning()
                currentSongs.isEmpty() -> Toaster.e(R.string.player_no_song_found)
                else -> deleteAllDialog.onShortClick()
            }
        }

        val renamePlaylist = RenamePlaylistDialog { playlistPreview.playlist }
        val renameAuthor = RenamePlaylistAuthorDialog { playlistPreview.playlist }
        val changePlaylistId = ChangePlaylistBrowseIdDialog(menuState = menuState) { playlistPreview.playlist }

        val rename = object : MenuIcon, Descriptive, Clickable {
            override val iconId: Int = R.drawable.title_edit
            override val messageId: Int = R.string.metadata_action_change_title
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { renamePlaylist.onShortClick() }
            override fun onLongClick() {}
        }

        renamePlaylist.Render()
        renameAuthor.Render()
        changePlaylistId.Render()
        val source = savedPlaylist.browseId
        val (playNext, enqueue) = collectionQueueActions(context, menuState, { binder?.captureMenuQueueEdit() }, songs) {
            it.asMediaItem.withPodcastSource(source)
        }

        val shuffleAction = SongShuffler.fromMediaItems {
            songs.orEmpty().map { it.asMediaItem.withPodcastSource(playlistPreview.playlist.browseId) }
        }
        val shuffle = shuffleAction.withMenuClick {
            if (songs == null) ToastRelay.loadingWarning()
            else shuffleAction.onShortClick()
        }

        val isSpecialPlaylist = playlistPreview.playlist.browseId?.removePrefix("VL") in listOf("LM", "SE")
        
        val isBookmarked = isPlaylistBookmarked(savedPlaylist)
        var bookmarkPending by remember { mutableStateOf(false) }

        fun toggleBookmark() {
            if (bookmarkPending || !canBookmarkPlaylist(savedPlaylist)) return
            val browseId = savedPlaylist.browseId ?: return
            val menuKey = menuState.transitionKey
            bookmarkPending = true
            if (launchSavedPodcastBookmarkToggle(
                    savedPlaylist, coroutineScope,
                    onRemoved = { if (menuState.isCurrentMenu(menuKey)) menuState.hide() },
                    onExistingImport = {
                        if (menuState.isCurrentMenu(menuKey)) {
                            menuState.hide()
                            NavRoutes.podcast.navigateHere(navController, browseId)
                        }
                    },
                    onFinished = { bookmarkPending = false },
                )
            ) return

            val app = context.applicationContext
            val owner = ProfileWorkOwner.capture(app) ?: run {
                bookmarkPending = false
                return Toaster.e(R.string.common_error_title)
            }
            val bookmarked = !isBookmarked
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                try {
                    val updated = withContext(NzikDispatchers.DATA) {
                        if (!ProfileWorkOwner.isBound(app, owner)) return@withContext null
                        val current = Database.playlistTable.findById(savedPlaylist.id).first()
                            ?.takeIf { it.browseId == browseId && isPlaylistBookmarked(it) == isBookmarked }
                            ?: return@withContext null
                        val pushPlaylist = app.preferences.getBoolean(syncPushPlaylistKey, false)
                        if (isYouTubeSyncEnabled() && pushPlaylist && getSyncDirection() != SyncDirection.YT_TO_APP &&
                            isNetworkConnected(app)) {
                            val sourceId = browseId.removePrefix("VL")
                            if (bookmarked) YtMusic.likePlaylistOrAlbum(sourceId).getOrThrow()
                            else YtMusic.removelikePlaylistOrAlbum(sourceId).getOrThrow()
                        }
                        Database.awaitWriteBatch {
                            var saved: Playlist? = null
                            transaction {
                                if (!ProfileWorkOwner.isBound(app, owner)) return@transaction
                                val latest = playlistTable.findById(current.id).first()
                                    ?.takeIf { it.browseId == browseId && isPlaylistBookmarked(it) == isBookmarked }
                                    ?: return@transaction
                                val changed = latest.copy(isYoutubePlaylist = bookmarked)
                                if (playlistTable.update(changed) > 0) saved = changed
                            }
                            saved
                        }
                    }
                    if (!ProfileWorkOwner.isBound(app, owner)) return@launch
                    if (updated != null) {
                        Toaster.s(if (bookmarked) R.string.library_favorites_added else R.string.library_favorites_removed)
                        if (bookmarked) MyDownloadHelper.downloadBookmarkedPlaylist(app, browseId, updated)
                    } else {
                        Toaster.e(R.string.common_error_title)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("LocalPlaylistItemMenu").e(error, "Failed to toggle playlist bookmark")
                    if (ProfileWorkOwner.isBound(app, owner)) Toaster.e(R.string.common_error_title)
                } finally {
                    bookmarkPending = false
                }
            }
        }

        // Define buttons
        buttons = run {
            val list = mutableListOf<Button>()
            
            list.add(shuffle)
            list.add(playNext)
            list.add(enqueue)
            if (!onDevice) {
                list.add(downloadAll)
                list.add(deleteAll)
            }
            

            if (playlistPreview.playlist.supportsInsertionPosition()) list.add(insertionDialog)

            val canChangeBrowseId = !onDevice && canChangePlaylistBrowseId(savedPlaylist)
            val canEditMetadata = !isSpecialPlaylist && !RewindPlaylists.isRewind(savedPlaylist.name) &&
                (savedPlaylist.isEditable || hasYouTubeLink || canChangeBrowseId)
            if (canEditMetadata) {
                list.add(rename)
                val sourceKind = playlistLinkKind(savedPlaylist.browseId)
                if (sourceKind != PlaylistLinkKind.Local) list.add(renameAuthor)
                if (canChangeBrowseId) list.add(changePlaylistId)
            }

            if (savedPlaylist.isEditable) {
                list.add(object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.trash
                    override val messageId: Int = R.string.common_action_delete
                    @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                    override fun onShortClick() {
                        playlistToDelete = playlistPreview.playlist
                    }
                    override fun onLongClick() {}
                })
            }
            
            // Auto-sync toggle for this playlist
            if (!onDevice && playlistPreview.playlist.isYoutubePlaylist && playlistLinkKind(playlistPreview.playlist.browseId) == PlaylistLinkKind.YouTubePlaylist && isYouTubeSyncEnabled()) {
                list.add(object : MenuIcon, Descriptive, DynamicColor {
                    override var isFirstColor: Boolean = playlistPreview.playlist.isAutoSync
                    override val iconId: Int = R.drawable.sync
                    override val messageId: Int = R.string.sync_per_playlist_auto
                    @get:Composable override val menuIconTitle: String
                        get() = if (playlistPreview.playlist.isAutoSync) stringResource(R.string.sync_per_playlist_auto_on) else stringResource(R.string.sync_per_playlist_auto_off)
                    override fun onShortClick() {
                        val id = playlistPreview.playlist.id
                        val app = context.applicationContext
                        val owner = ProfileWorkOwner.capture(app) ?: return
                        Database.asyncTransaction {
                            if (!ProfileWorkOwner.isBound(app, owner)) return@asyncTransaction
                            if (playlistTable.toggleAutoSync(id) > 0) Toaster.done()
                            else Toaster.e(R.string.common_error_title)
                        }
                        menuState.hide()
                    }
                    override fun onLongClick() {}
                })
            }

            if (!onDevice && canRefreshPlaylist(playlistPreview.playlist) && !offlineMode) {
                list.add(object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.sync
                    override val messageId: Int = R.string.sync_action_sync
                    @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                    override fun onShortClick() {
                        launchPlaylistSourceRefresh(playlistPreview.playlist, NzikDispatchers.fireAndForget(NzikDispatchers.UI))
                        menuState.hide()
                    }
                    override fun onLongClick() {}
                })
            }

            if (!onDevice && canRefreshPlaylist(playlistPreview.playlist)) list.add(refreshOnOpen)

            if (hasExternalPlaylistUrl) {
                list.add(object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.play
                    override val messageId: Int = R.string.player_action_listen_on_youtube_music
                    @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                    override fun onShortClick() {
                        val url = externalPlaylistUrl ?: return
                        if (openExternalMenuLink {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) {
                            menuState.hide()
                        }
                    }
                    override fun onLongClick() {}
                })
            }

            list
        }

        MenuSheetSurface {
            DialogPresence(target = playlistToDelete) { playlist ->
                DeletePlaylistConfirmation(
                    playlist = playlist,
                    onDismiss = { playlistToDelete = null },
                    onConfirm = {
                        if (playlistToDelete?.id == playlist.id) {
                            playlistToDelete = null
                            launchLocalPlaylistDeletion(context, listOf(playlist))
                            menuState.hide()
                        }
                    },
                )
            }
            insertionDialog.Render()
            downloadAllDialog.Render()
            deleteAllDialog.Render()
            PlaylistItemDisplay(
                playlistPreview, isBookmarked, savedPlaylist.displayAuthorName(),
                bookmarkEnabled = !bookmarkPending, onBookmarkToggle = ::toggleBookmark,
            )

            if (menuStyle == MenuStyle.List)
                LocalPlaylistListMenu(buttons, ownerAction)
            else
                LocalPlaylistGridMenu(buttons, ownerAction)
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        BasicText(
            text = title,
            style = typography().xxs.semiBold.copy(
                color = colorPalette().accent,
                textAlign = TextAlign.Start
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp)
        )
    }
}
