package app.n_zik.android.components.menu.album

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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.PressFeedbackHorizontalInset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.albumPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.components.navigation.LibraryBrowseKind
import app.n_zik.android.components.navigation.rememberLibraryBrowseDestination
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongAlbumMap
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Clickable
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.ui.screens.playlist.loadPlaylistTrackContinuations
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.withPlaybackAlbum
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.excludeDislikedAlbumsKey
import app.n_zik.android.utils.getSyncDirection
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.syncPushAlbumBookmarkKey
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.album.ChangeAlbumAuthorsDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumCoverDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumTitleDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import app.n_zik.android.components.menu.artistMoreFromTitle
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.android.components.menu.artistSearchMenuAction
import app.n_zik.android.components.menu.rememberMenuSongs
import app.n_zik.android.components.menu.ObserveMenuProfile
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.MenuBookmarkBadge
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.OpenEntityMenuAction
import app.n_zik.android.components.menu.menuLikeActionDescription
import app.n_zik.android.components.menu.menuSheetSectionBackground
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.components.shuffleButtonIconRes
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.components.menu.collectionQueueActions
import app.n_zik.android.components.menu.withMenuClick
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.surfaceColor
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.utils.ToastRelay
import app.n_zik.android.utils.coroutines.NzikDispatchers

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class OnlineAlbumItemMenu private constructor(
    private val navController: NavController,
    private val album: Innertube.AlbumItem,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    private val ownerProfile = getActiveProfile(appContext())

    companion object {
        @Composable
        operator fun invoke(navController: NavController, album: Innertube.AlbumItem): OnlineAlbumItemMenu =
            OnlineAlbumItemMenu(
                navController = navController,
                album = album,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    private var buttons: List<Button> = emptyList()
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu(buttons, OfflineMode.rememberEnabled().value)

    @Composable
    private fun ListMenu(buttons: List<Button>, offlineMode: Boolean) = ListMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        SectionTitle(stringResource(R.string.common_playback_section_title))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Management
        SectionTitle(stringResource(R.string.library_management_section_title))
        buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
        if (!offlineMode) buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
        for (i in (buttons.size - 4) until buttons.size) {
            buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
        }

        // Section: Navigation
        val navRange = 6 until (buttons.size - 4)
        if (!navRange.isEmpty()) {
            SectionTitle(stringResource(R.string.common_navigation_section_title))
            for (i in navRange) {
                buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
        }
    }

    @Composable
    override fun GridMenu() = GridMenu(buttons, OfflineMode.rememberEnabled().value)

    @Composable
    private fun GridMenu(buttons: List<Button>, offlineMode: Boolean) = GridMenu.Menu(title = null, showDragHandle = false) {
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
        buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        if (!offlineMode) buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        for (i in (buttons.size - 4) until buttons.size) {
            buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }

        // Section: Navigation
        val navRange = 6 until (buttons.size - 4)
        if (!navRange.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.common_navigation_section_title))
            }
            for (i in navRange) {
                buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        }
    }

    @Composable
    private fun AlbumItemDisplay(
        browseId: String,
        sourcePlaylistId: String?,
        title: String?,
        authorsText: String?,
        year: String?,
        thumbnailUrl: String?,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
        val showDisliked by rememberPreference(excludeDislikedAlbumsKey, DislikeMode.Enabled)

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
                // Album's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2)
                ) {
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

                    val likeState by remember(browseId) {
                        Database.albumTable
                            .likeState(browseId)
                            .distinctUntilChanged()
                    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

                    likeState?.let { MenuBookmarkBadge(bookmarked = it) }
                }

                // Album's information
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = cleanPrefix(title ?: ""),
                        style = typography().xs.semiBold.copy(
                            color = colorPalette().text,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )

                    authorsText?.let { authors ->
                        if (authors.isNotBlank()) {
                            BasicText(
                                text = cleanPrefix(authors),
                                style = typography().xs.semiBold.secondary.copy(
                                    color = colorPalette().textSecondary,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                            )
                        }
                    }

                    year?.let {
                        if (it.isNotBlank()) {
                            BasicText(
                                text = it,
                                style = typography().xxs.semiBold.secondary.copy(
                                    color = colorPalette().textSecondary,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Trailing content (Bookmark & Share)
                val isBookmarked by remember(browseId) {
                    Database.albumTable
                        .isBookmarked(browseId)
                        .distinctUntilChanged()
                }.collectAsStateWithLifecycle(false, context = NzikDispatchers.DATA)

                val likeState by remember(browseId) {
                    Database.albumTable
                        .likeState(browseId)
                        .distinctUntilChanged()
                }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        contentDescription = menuLikeActionDescription(
                            likeState, showDisliked.isEnabled, R.string.album_action_bookmark, R.string.library_action_remove_bookmark,
                        ),
                        icon = when(likeState) {
                            true -> R.drawable.bookmark
                            false -> R.drawable.bookmark_slash
                            null -> R.drawable.bookmark_outline
                        },
                        color = when(likeState) {
                            true -> colorPalette().favoritesIcon
                            false -> colorPalette().red
                            null -> colorPalette().text
                        },
                        onClick = {
                            val previous = likeState
                            val dislikesEnabled = showDisliked.isEnabled
                            val newState = when (previous) {
                                true -> if (dislikesEnabled) false else null
                                false -> if (dislikesEnabled) null else true
                                null -> true
                            }
                            val messageId = when (newState) {
                                true -> R.string.library_favorites_added
                                false -> R.string.library_dislikes_added
                                null -> R.string.library_favorites_removed
                            }
                            coroutineScope.launch(NzikDispatchers.DATA) {
                                try {
                                    Database.albumTable.insertIgnore(
                                        app.n_zik.android.models.Album(
                                            id = browseId,
                                            title = title,
                                            thumbnailUrl = thumbnailUrl,
                                            year = year,
                                            authorsText = authorsText
                                        )
                                    )
                                    val push = appContext().preferences.getBoolean(syncPushAlbumBookmarkKey, false)
                                    val remoteBookmark = onlineAlbumBookmarkChange(previous, dislikesEnabled)
                                    val playlistId = sourcePlaylistId
                                    if (remoteBookmark != null && playlistId != null && isYouTubeSyncEnabled() &&
                                        push && getSyncDirection() != SyncDirection.YT_TO_APP && isNetworkConnected(appContext())) {
                                        if (remoteBookmark) YtMusic.likePlaylistOrAlbum(playlistId).getOrThrow()
                                        else YtMusic.removelikePlaylistOrAlbum(playlistId).getOrThrow()
                                    }
                                    val cachedFetch = Database.albumTable.findByIdDirect(browseId)?.lastFetch
                                    val updated = if (dislikesEnabled) Database.albumTable.rotateLikeState(browseId)
                                        else Database.albumTable.toggleBookmark(browseId)
                                    if (updated == 0) {
                                        Toaster.e(R.string.common_error_title)
                                        return@launch
                                    }
                                    val albumName = title
                                    if (albumName != null) Toaster.s(messageId, "\"$albumName\"")
                                    else Toaster.s(messageId)
                                    if (Database.albumTable.isBookmarked(browseId).first()) {
                                        MyDownloadHelper.downloadBookmarkedAlbum(appContext(), browseId, cachedFetch)
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    timber.log.Timber.tag("OnlineAlbumItemMenu").e(error, "Failed to update album bookmark")
                                    Toaster.e(R.string.common_error_title)
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )

                    if (!browseId.startsWith("LOCAL_ALBUM_")) IconButton(
                        icon = R.drawable.share_social,
                        contentDescription = stringResource(R.string.player_mini_button_share),
                        color = colorPalette().text,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/browse/${Uri.encode(browseId.removePrefix(MODIFIED_PREFIX))}")
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
        val browseId = rememberLibraryBrowseDestination(album.key, LibraryBrowseKind.Album)?.browseId
            ?: return
        key(browseId) { ResolvedMenu(browseId) }
    }

    @Composable
    private fun ResolvedMenu(browseId: String) {
        val sourceAlbum = album.takeIf { browseId == it.key }
        val binder = LocalPlayerServiceBinder.current
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val offlineMode by OfflineMode.rememberEnabled()
        val isLocalAlbum = browseId.startsWith("LOCAL_ALBUM_")

        var fetchedSongs by remember { mutableStateOf<List<Song>?>(null) }
        var songsLoadFailed by remember(browseId) { mutableStateOf(false) }

        var displayTitle by remember { mutableStateOf(sourceAlbum?.title ?: sourceAlbum?.info?.name) }
        var displayAuthors by remember { mutableStateOf(sourceAlbum?.authors.artistEntryNames().joinToString(", ")) }
        var sourcePlaylistId by remember { mutableStateOf(sourceAlbum?.playlistId) }
        var displayYear by remember { mutableStateOf(sourceAlbum?.year) }
        var displayThumbnailUrl by remember { mutableStateOf(sourceAlbum?.thumbnail?.url) }

        val dbAlbum by remember(browseId) { Database.albumTable.findById(browseId) }
            .collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
        val storedSongs = if (offlineMode || isLocalAlbum) {
            remember(browseId) { Database.songAlbumMapTable.allSongsOf(browseId) }
                .collectAsStateWithLifecycle<List<Song>?>(null, context = NzikDispatchers.DATA).value
        } else null
        val songs by rememberMenuSongs(fetchedSongs ?: storedSongs)
        val songsUnavailable = songsLoadFailed || (offlineMode && !isLocalAlbum)
        val listedCredits = remember(sourceAlbum) { sourceAlbum?.authors.toAlbumCreditArtists() }
        var headerArtists by remember(browseId, listedCredits) {
            mutableStateOf(if (isLocalAlbum) emptyList() else listedCredits)
        }
        val storedCredits = dbAlbum?.authorsText?.takeIf { it.startsWith(MODIFIED_PREFIX, ignoreCase = true) }
            ?: displayAuthors.takeIf(String::isNotBlank)
            ?: dbAlbum?.authorsText
        val creditedArtists by rememberAlbumMenuArtists(storedCredits, headerArtists, context.preferences)

        val albumProvider = {
            dbAlbum ?: Album(
                id = browseId,
                title = displayTitle,
                thumbnailUrl = displayThumbnailUrl,
                year = displayYear,
                authorsText = displayAuthors,
                shareUrl = "https://music.youtube.com/browse/${Uri.encode(browseId.removePrefix(MODIFIED_PREFIX))}",
                timestamp = System.currentTimeMillis()
            )
        }
        val changeTitle = ChangeAlbumTitleDialog(albumProvider)
        val changeAuthors = ChangeAlbumAuthorsDialog(albumProvider)
        val changeCover = ChangeAlbumCoverDialog(albumProvider)
        val changeId = ChangeAlbumBrowseIdDialog(menuState = menuState, getAlbum = albumProvider)

        LaunchedEffect(browseId, offlineMode) {
            if (isLocalAlbum || offlineMode) return@LaunchedEffect
            songsLoadFailed = false
            withContext(NzikDispatchers.DATA) {
                if (OfflineMode.enabled(context)) return@withContext
                val result = Innertube.albumPage(browseId = browseId.removePrefix(MODIFIED_PREFIX))?.getOrNull()
                if (result != null) {
                    sourcePlaylistId = result.url?.let { Uri.parse(it).getQueryParameter("list") } ?: sourcePlaylistId
                    headerArtists = result.authors.toAlbumCreditArtists().ifEmpty { listedCredits }
                    displayTitle = result.title.takeIf { !it.isNullOrBlank() } ?: displayTitle
                    displayAuthors = result.authors.artistEntryNames().joinToString(", ").takeIf { it.isNotBlank() } ?: displayAuthors
                    displayYear = result.year.takeIf { !it.isNullOrBlank() } ?: displayYear
                    displayThumbnailUrl = result.thumbnail?.url.takeIf { !it.isNullOrBlank() } ?: displayThumbnailUrl
                    val tracks = result.songsPage?.let { page ->
                        if (page.continuation == null) page else loadPlaylistTrackContinuations(page)
                    }
                    if (tracks?.continuation != null) {
                        songsLoadFailed = true
                        return@withContext
                    }
                    val completeSongs = tracks?.items.orEmpty().mapNotNull { it.asSong }
                    fetchedSongs = completeSongs
                    Database.asyncTransaction {
                        Database.albumTable.insertIgnore(Album(
                            id = browseId,
                            title = displayTitle,
                            thumbnailUrl = displayThumbnailUrl,
                            year = displayYear,
                            authorsText = displayAuthors,
                            shareUrl = result.url,
                            timestamp = System.currentTimeMillis()
                        ))
                        completeSongs.forEachIndexed { index, song ->
                            Database.insertIgnore(song.asMediaItem)
                            Database.songAlbumMapTable.upsert(
                                listOf(SongAlbumMap(songId = song.id, albumId = browseId, position = index))
                            )
                        }
                    }
                } else {
                    songsLoadFailed = true
                }
            }
        }

        val displayedAlbum = onlineAlbumMenuDisplay(
            stored = albumProvider(),
            title = displayTitle,
            thumbnailUrl = displayThumbnailUrl,
            year = displayYear,
            credits = storedCredits,
        )
        val playbackAlbumTitle = displayedAlbum.cleanTitle()
        val (playNext, enqueue) = collectionQueueActions(context, menuState, { binder?.captureMenuQueueEdit() }, songs, songsUnavailable) {
            it.asMediaItem.withPlaybackAlbum(browseId, playbackAlbumTitle)
        }

        fun warnAlbumSongsUnavailable() {
            if (songsUnavailable) Toaster.e(R.string.common_load_failed) else ToastRelay.loadingWarning()
        }

        val playlistsMenu = PlaylistsMenu.init(
            navController = navController,
            mediaItems = { songs?.map { it.asMediaItem } ?: emptyList() },
            onFailure = { _, _ -> },
            finalAction = { menuState.hide() }
        )

        val addToPlaylist = playlistsMenu.withMenuClick {
            val currentSongs = songs
            when {
                currentSongs == null -> warnAlbumSongsUnavailable()
                currentSongs.isEmpty() -> Toaster.e(R.string.player_no_song_found)
                else -> playlistsMenu.onShortClick()
            }
        }

        val downloadAllDialog = DownloadAllSongsDialog { songs ?: emptyList() }
        val downloadAll = downloadAllDialog.withMenuClick {
            if (OfflineMode.enabled(context)) return@withMenuClick
            val currentSongs = songs
            when {
                currentSongs == null -> warnAlbumSongsUnavailable()
                currentSongs.isEmpty() -> Toaster.e(R.string.player_no_song_found)
                else -> downloadAllDialog.onShortClick()
            }
        }

        val deleteAllDialog = DeleteAllDownloadedSongsDialog { songs ?: emptyList() }
        val deleteAll = deleteAllDialog.withMenuClick {
            val currentSongs = songs
            when {
                currentSongs == null -> warnAlbumSongsUnavailable()
                currentSongs.isEmpty() -> Toaster.e(R.string.player_no_song_found)
                else -> deleteAllDialog.onShortClick()
            }
        }

        val shuffle = object : MenuIcon, Descriptive, Clickable {
            // Issue #866 (gh-866): app-wide shuffle confirmation flash - the icon follows the
            // binder's flash state (shuffle_ok ~1 s), triggered in onShortClick below.
            override val iconId: Int get() = shuffleButtonIconRes(binder)
            override val color: androidx.compose.ui.graphics.Color
                @Composable
                get() = colorPalette().text
            override val messageId: Int = R.string.player_action_shuffle
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() {
                val currentSongs = songs
                if (currentSongs == null) {
                    warnAlbumSongsUnavailable()
                } else if (currentSongs.isNotEmpty()) {
                    val activeBinder = binder ?: return
                    if (!Shuffler.canStart()) return
                    Shuffler.play(
                        activeBinder,
                        currentSongs.map { it.asMediaItem.withPlaybackAlbum(browseId, playbackAlbumTitle) },
                        onQueueApplied = activeBinder::triggerShuffleOkFlash,
                    )
                    menuState.hide()
                } else {
                    Toaster.e(R.string.player_no_song_found)
                }
            }
            override fun onLongClick() {}
        }

        val updatedButtons = buildList<Button> {
            add(shuffle)
            add(playNext)
            add(enqueue)
            add(addToPlaylist)
            add(downloadAll)
            add(deleteAll)

            add(OpenEntityMenuAction(
                navController, menuState, NavRoutes.album, browseId, R.drawable.album, R.string.album_action_open,
            ))
            creditedArtists.forEach { artist ->
                add(object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.people
                    override val messageId: Int = R.string.library_artists_title
                    @get:Composable override val menuIconTitle: String get() = artistMoreFromTitle(artist.name)
                    override fun onShortClick() {
                        navigateToAlbumMenuArtist(
                            artist, storedCredits, headerArtists, context.preferences,
                            menuState, navController, coroutineScope,
                        )
                    }
                    override fun onLongClick() {}
                })
            }

            albumMenuArtistFallbackNames(storedCredits, headerArtists, creditedArtists)
                .forEach { name -> add(artistSearchMenuAction(name, navController, menuState)) }

            add(changeTitle)
            add(changeAuthors)
            add(changeCover)
            add(changeId)
        }

        buttons = updatedButtons

        MenuSheetSurface {
            changeTitle.Render()
            changeAuthors.Render()
            changeCover.Render()
            changeId.Render()
            downloadAllDialog.Render()
            deleteAllDialog.Render()
            AlbumItemDisplay(
                browseId = browseId,
                sourcePlaylistId = sourcePlaylistId,
                title = displayedAlbum.title,
                authorsText = displayedAlbum.authorsText,
                year = displayedAlbum.year,
                thumbnailUrl = displayedAlbum.thumbnailUrl
            )

            if (menuStyle == MenuStyle.List)
                ListMenu(updatedButtons, offlineMode)
            else
                GridMenu(updatedButtons, offlineMode)
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
                .padding(vertical = 12.dp, horizontal = PressFeedbackHorizontalInset)
        )
    }
}

internal fun onlineAlbumMenuDisplay(
    stored: Album,
    title: String?,
    thumbnailUrl: String?,
    year: String?,
    credits: String?,
): Album = stored.copy(
    title = PropUtils.retainIfModified(stored.title, title ?: stored.title),
    thumbnailUrl = PropUtils.retainIfModified(stored.thumbnailUrl, thumbnailUrl ?: stored.thumbnailUrl),
    year = year ?: stored.year,
    authorsText = credits,
)

internal fun onlineAlbumBookmarkChange(likeState: Boolean?, dislikesEnabled: Boolean): Boolean? =
    when (likeState) {
        true -> false
        false -> if (dislikesEnabled) null else true
        null -> true
    }
