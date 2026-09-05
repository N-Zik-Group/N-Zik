package app.n_zik.android.components.menu.album

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.innertube.requests.albumPage
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.album.ChangeAlbumTitleDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumAuthorsDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumCoverDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.it.fast4x.rimusic.models.Album
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.syncPushAlbumBookmarkKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.getSyncDirection
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.enums.SyncDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.components.tab.SongShuffler

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class OnlineAlbumItemMenu private constructor(
    private val navController: NavController,
    private val album: Innertube.AlbumItem,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

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

    lateinit var buttons: List<Button>
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        SectionTitle(stringResource(R.string.playback))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Management
        SectionTitle(stringResource(R.string.management))
        buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
        for (i in (buttons.size - 4) until buttons.size) {
            buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
        }

        // Section: Navigation
        val navRange = 6 until (buttons.size - 4)
        if (!navRange.isEmpty()) {
            SectionTitle(stringResource(R.string.navigation))
            for (i in navRange) {
                buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
        }
    }

    @Composable
    override fun GridMenu() = GridMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.playback))
        }
        buttons.getOrNull(0)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        // Section: Management
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.management))
        }
        buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        for (i in (buttons.size - 4) until buttons.size) {
            buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }

        // Section: Navigation
        val navRange = 6 until (buttons.size - 4)
        if (!navRange.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.navigation))
            }
            for (i in navRange) {
                buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        }
    }

    @Composable
    private fun AlbumItemDisplay(
        title: String?,
        authorsText: String?,
        year: String?,
        thumbnailUrl: String?,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxWidth()
                .background(colorPalette().background1)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )

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
                    ImageCacheFactory.Thumbnail(
                        thumbnailUrl = thumbnailUrl,
                        modifier = Modifier
                            .size(Dimensions.thumbnails.album / 2)
                            .clip(thumbnailShape())
                    )
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
                val isBookmarked by remember(album.key) {
                    Database.albumTable
                        .isBookmarked(album.key)
                        .distinctUntilChanged()
                }.collectAsState(false, Dispatchers.IO)

                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        icon = if (isBookmarked) R.drawable.bookmark else R.drawable.bookmark_outline,
                        color = colorPalette().favoritesIcon,
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val pushAlbumBookmark = appContext().preferences.getBoolean(syncPushAlbumBookmarkKey, false)
                                val syncDir = getSyncDirection()
                                if (isYouTubeSyncEnabled() && pushAlbumBookmark && syncDir != SyncDirection.YT_TO_APP && isNetworkConnected(appContext())) {
                                    val playlistId = album.playlistId
                                    if (playlistId != null) {
                                        if (isBookmarked) YtMusic.removelikePlaylistOrAlbum(playlistId)
                                        else YtMusic.likePlaylistOrAlbum(playlistId)
                                    }
                                }
                                Database.albumTable.toggleBookmark(album.key)
                                Toaster.s( if (isBookmarked) R.string.removed_from_favorites else R.string.added_to_favorites )
                            }
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )

                    IconButton(
                        icon = R.drawable.share_social,
                        color = colorPalette().text,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/browse/${album.key}")
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )
                }
            }

            HorizontalDivider(Modifier.height(1.dp))
        }
    }

    @Composable
    override fun MenuComponent() {
        val binder = LocalPlayerServiceBinder.current
        val context = LocalContext.current

        var songs by remember { mutableStateOf<List<Song>?>(null) }

        var displayTitle by remember { mutableStateOf(album.title ?: album.info?.name) }
        var displayAuthors by remember { mutableStateOf(album.authors.parseArtists().joinToString(", ")) }
        var displayYear by remember { mutableStateOf(album.year) }
        var displayThumbnailUrl by remember { mutableStateOf(album.thumbnail?.url) }

        val albumProvider = {
            Album(
                id = album.key,
                title = displayTitle,
                thumbnailUrl = displayThumbnailUrl,
                year = displayYear,
                authorsText = displayAuthors,
                shareUrl = "https://music.youtube.com/browse/${album.key}",
                timestamp = System.currentTimeMillis()
            )
        }
        val changeTitle = ChangeAlbumTitleDialog(albumProvider)
        val changeAuthors = ChangeAlbumAuthorsDialog(albumProvider)
        val changeCover = ChangeAlbumCoverDialog(albumProvider)
        val changeId = ChangeAlbumBrowseIdDialog(menuState = menuState, getAlbum = albumProvider)

        LaunchedEffect(album.key) {
            withContext(Dispatchers.IO) {
                if (album.key.startsWith("LOCAL_ALBUM_")) {
                    songs = emptyList()
                    return@withContext
                }
                
                val result = Innertube.albumPage(browseId = album.key.removePrefix(MODIFIED_PREFIX))?.getOrNull()
                if (result != null) {
                    displayTitle = result.title.takeIf { !it.isNullOrBlank() } ?: displayTitle
                    displayAuthors = result.authors.parseArtists().joinToString(", ").takeIf { it.isNotBlank() } ?: displayAuthors
                    displayYear = result.year.takeIf { !it.isNullOrBlank() } ?: displayYear
                    displayThumbnailUrl = result.thumbnail?.url.takeIf { !it.isNullOrBlank() } ?: displayThumbnailUrl
                    songs = result.songsPage?.items?.mapNotNull { it.asSong } ?: emptyList()
                    Database.asyncTransaction {
                        Database.albumTable.insertIgnore(Album(
                            id = album.key,
                            title = displayTitle,
                            thumbnailUrl = displayThumbnailUrl,
                            year = displayYear,
                            authorsText = displayAuthors,
                            shareUrl = result.url,
                            timestamp = System.currentTimeMillis()
                        ))
                        songs?.forEachIndexed { index, song ->
                            Database.insertIgnore(song.asMediaItem)
                            Database.songAlbumMapTable.upsert(
                                listOf(SongAlbumMap(songId = song.id, albumId = album.key, position = index))
                            )
                        }
                    }
                } else {
                    songs = emptyList() // Failed to fetch or no songs
                }
            }
        }

        val playNext = PlayNext {
            val currentSongs = songs
            if (currentSongs == null) {
                Toaster.w(R.string.opening_url)
            } else if (currentSongs.isNotEmpty()) {
                binder?.player?.addNext(currentSongs.map { it.asMediaItem }, appContext())
                menuState.hide()
            } else {
                Toaster.e(R.string.no_song_found)
            }
        }

        val enqueue = Enqueue {
            val currentSongs = songs
            if (currentSongs == null) {
                Toaster.w(R.string.opening_url)
            } else if (currentSongs.isNotEmpty()) {
                binder?.player?.enqueue(currentSongs.map { it.asMediaItem }, appContext())
                menuState.hide()
            } else {
                Toaster.e(R.string.no_song_found)
            }
        }

        val playlistsMenu = PlaylistsMenu.init(
            navController = navController,
            mediaItems = { songs?.map { it.asMediaItem } ?: emptyList() },
            onFailure = { _, _ -> },
            finalAction = { menuState.hide() },
            onDismiss = { openMenu() }
        )

        val addToPlaylist = object : MenuIcon by playlistsMenu, Descriptive by playlistsMenu, Clickable {
            override fun onShortClick() {
                val currentSongs = songs
                if (currentSongs == null) {
                    Toaster.w(R.string.opening_url)
                } else if (currentSongs.isNotEmpty()) {
                    playlistsMenu.onShortClick()
                } else {
                    Toaster.e(R.string.no_song_found)
                }
            }
            override fun onLongClick() {}
        }

        val downloadAllDialog = DownloadAllSongsDialog { songs ?: emptyList() }
        val downloadAll = object : MenuIcon by downloadAllDialog, Descriptive by downloadAllDialog, Clickable {
            override fun onShortClick() {
                val currentSongs = songs
                if (currentSongs == null) {
                    Toaster.w(R.string.opening_url)
                } else if (currentSongs.isNotEmpty()) {
                    downloadAllDialog.onShortClick()
                } else {
                    Toaster.e(R.string.no_song_found)
                }
            }
            override fun onLongClick() {}
        }

        val deleteAllDialog = DeleteAllDownloadedSongsDialog { songs ?: emptyList() }
        val deleteAll = object : MenuIcon by deleteAllDialog, Descriptive by deleteAllDialog, Clickable {
            override fun onShortClick() {
                val currentSongs = songs
                if (currentSongs == null) {
                    Toaster.w(R.string.opening_url)
                } else if (currentSongs.isNotEmpty()) {
                    deleteAllDialog.onShortClick()
                } else {
                    Toaster.e(R.string.no_song_found)
                }
            }
            override fun onLongClick() {}
        }

        val shuffle = object : MenuIcon, Descriptive, Clickable {
            override val iconId: Int = R.drawable.shuffle
            override val color: androidx.compose.ui.graphics.Color
                @Composable
                get() = colorPalette().text
            override val messageId: Int = R.string.shuffle
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() {
                val currentSongs = songs
                if (currentSongs == null) {
                    Toaster.w(R.string.opening_url)
                } else if (currentSongs.isNotEmpty()) {
                    SongShuffler.playShuffled(binder ?: return, currentSongs)
                    menuState.hide()
                } else {
                    Toaster.e(R.string.no_song_found)
                }
            }
            override fun onLongClick() {}
        }

        buttons = mutableListOf<Button>().apply {
            add(shuffle)
            add(playNext)
            add(enqueue)
            add(addToPlaylist)
            add(downloadAll)
            add(deleteAll)

            album.authors?.forEach { artist ->
                val artistName = artist.name
                val browseId = artist.endpoint?.browseId
                if (!artistName.isNullOrBlank() && !browseId.isNullOrBlank()) {
                    add(object : MenuIcon, Descriptive, Clickable {
                        override val iconId: Int = R.drawable.people
                        override val messageId: Int = R.string.artists
                        @get:Composable override val menuIconTitle: String get() = stringResource(R.string.more_of) + " $artistName"
                        override fun onShortClick() {
                            menuState.hide()
                            val path = "$browseId?params=${artist.endpoint?.params.orEmpty()}"
                            NavRoutes.artist.navigateHere(navController, path)
                        }
                        override fun onLongClick() {}
                    })
                }
            }

            add(changeTitle)
            add(changeAuthors)
            add(changeCover)
            add(changeId)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0)
        ) {
            changeTitle.Render()
            changeAuthors.Render()
            changeCover.Render()
            changeId.Render()
            downloadAllDialog.Render()
            deleteAllDialog.Render()
            AlbumItemDisplay(
                title = displayTitle,
                authorsText = displayAuthors,
                year = displayYear,
                thumbnailUrl = displayThumbnailUrl
            )

            if (menuStyle == MenuStyle.List)
                ListMenu()
            else
                GridMenu()
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
