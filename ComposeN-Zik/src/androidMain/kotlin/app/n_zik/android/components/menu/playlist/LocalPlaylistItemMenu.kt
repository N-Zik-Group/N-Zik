package app.n_zik.android.components.menu.playlist

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.syncPushPlaylistKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.getSyncDirection
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.enums.SyncDirection
import app.n_zik.android.appContext
import it.fast4x.innertube.YtMusic
import timber.log.Timber
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.playlist.RenamePlaylistDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.n_zik.android.components.dialog.playlist.ChangePlaylistBrowseIdDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.core.coil.ImageCacheFactory
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import app.it.fast4x.rimusic.utils.checkFileExists
import app.n_zik.android.components.tab.SongShuffler
import android.net.Uri

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class LocalPlaylistItemMenu private constructor(
    private val navController: NavController,
    private val playlistPreview: PlaylistPreview,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    companion object {
        @Composable
        operator fun invoke(navController: NavController, playlistPreview: PlaylistPreview): LocalPlaylistItemMenu =
            LocalPlaylistItemMenu(
                navController = navController,
                playlistPreview = playlistPreview,
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

        // Section: Management
        SectionTitle(stringResource(R.string.management))
        val mgmtEnd = if (playlistPreview.playlist.isYoutubePlaylist) buttons.size - 1 else buttons.size
        for (i in 2 until mgmtEnd) {
            buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
        }

        // Section: Navigation
        if (playlistPreview.playlist.isYoutubePlaylist) {
            SectionTitle(stringResource(R.string.navigation))
            buttons.lastOrNull()?.let { if (it is MenuIcon) it.ListMenuItem() }
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

        // Section: Management
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.management))
        }
        val mgmtEnd = if (playlistPreview.playlist.isYoutubePlaylist) buttons.size - 1 else buttons.size
        for (i in 2 until mgmtEnd) {
            buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }

        // Section: Navigation
        if (playlistPreview.playlist.isYoutubePlaylist) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.navigation))
            }
            buttons.lastOrNull()?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }
    }

    @Composable
    private fun PlaylistItemDisplay(
        playlistPreview: PlaylistPreview,
        isBookmarked: Boolean,
        onBookmarkToggle: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
        val context = LocalContext.current
        
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
                // Playlist's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2)
                ) {
                    val thumbnails by remember {
                        val customThumbnail = checkFileExists( context, "thumbnail/playlist_${playlistPreview.playlist.id}" )

                        if( customThumbnail != null )
                            kotlinx.coroutines.flow.flowOf( listOf( customThumbnail ) )
                        else
                            Database.songPlaylistMapTable
                                    .sortSongsByPlayTime( playlistPreview.playlist.id )
                                    .distinctUntilChanged()
                                    .map { list: List<Song> ->
                                        list.mapNotNull( Song::thumbnailUrl ).takeLast( 4 )
                                    }
                    }.collectAsStateWithLifecycle( emptyList() )

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
                            thumbnailUrl = thumbnails[0],
                            modifier = Modifier
                                .size(Dimensions.thumbnails.album / 2)
                                .clip(thumbnailShape())
                        )
                    } else {
                        // 4 grid
                        Row(modifier = Modifier.size(Dimensions.thumbnails.album / 2).clip(thumbnailShape())) {
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                ImageCacheFactory.Thumbnail(thumbnailUrl = thumbnails[0], modifier = Modifier.weight(1f).fillMaxWidth())
                                if (thumbnails.size > 2) {
                                    ImageCacheFactory.Thumbnail(thumbnailUrl = thumbnails[2], modifier = Modifier.weight(1f).fillMaxWidth())
                                }
                            }
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                if (thumbnails.size > 1) {
                                    ImageCacheFactory.Thumbnail(thumbnailUrl = thumbnails[1], modifier = Modifier.weight(1f).fillMaxWidth())
                                }
                                if (thumbnails.size > 3) {
                                    ImageCacheFactory.Thumbnail(thumbnailUrl = thumbnails[3], modifier = Modifier.weight(1f).fillMaxWidth())
                                }
                            }
                        }
                    }
                }

                // Playlist's information
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
                    BasicText(
                        text = playlistPreview.playlist.name,
                        style = typography().xs.semiBold.copy(color = colorPalette().text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                    BasicText(
                        text = "${playlistPreview.songCount} ${stringResource(R.string.songs)}",
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
                    IconButton(
                        icon = if (isBookmarked) R.drawable.bookmark else R.drawable.bookmark_outline,
                        color = colorPalette().favoritesIcon,
                        onClick = { onBookmarkToggle() },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )

                    IconButton(
                        icon = R.drawable.open,
                        color = colorPalette().text,
                        onClick = {
                            menuState.hide()
                            val browseId = playlistPreview.playlist.browseId
                            if (!browseId.isNullOrBlank()) {
                                navController.navigate(route = "${NavRoutes.playlist.name}/$browseId")
                            } else {
                                navController.navigate(route = "${NavRoutes.localPlaylist.name}/${playlistPreview.playlist.id}")
                            }
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
        val context = LocalContext.current
        val binder = LocalPlayerServiceBinder.current
        val coroutineScope = rememberCoroutineScope()
        
        // Options like Play Next, Enqueue, Rename, Delete...
        var showRenameDialog by remember { mutableStateOf(false) }
        
        var songs by remember { mutableStateOf<List<Song>?>(null) }
        
        LaunchedEffect(playlistPreview.playlist.id) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                songs = Database.songPlaylistMapTable.allSongsOf(playlistPreview.playlist.id).firstOrNull() ?: emptyList()
            }
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

        val renamePlaylist = RenamePlaylistDialog { playlistPreview.playlist }
        val changePlaylistId = ChangePlaylistBrowseIdDialog(menuState = menuState) { playlistPreview.playlist }

        val rename = object : MenuIcon, Descriptive, Clickable {
            override val iconId: Int = R.drawable.title_edit
            override val messageId: Int = R.string.rename_playlist
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { renamePlaylist.onShortClick() }
            override fun onLongClick() {}
        }

        renamePlaylist.Render()
        changePlaylistId.Render()
        downloadAllDialog.Render()
        deleteAllDialog.Render()

        val playNext = PlayNext {
            val currentSongs = songs
            if (currentSongs == null) {
                Toaster.w(R.string.opening_url)
            } else if (currentSongs.isNotEmpty()) {
                binder?.player?.addNext(currentSongs.map { it.asMediaItem }, context)
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
                binder?.player?.enqueue(currentSongs.map { it.asMediaItem }, context)
                menuState.hide()
            } else {
                Toaster.e(R.string.no_song_found)
            }
        }

        val shuffle = SongShuffler { songs ?: emptyList() }

        val isSpecialPlaylist = playlistPreview.playlist.browseId?.removePrefix("VL") in listOf("LM", "SE")
        
        var isBookmarked by remember(playlistPreview.playlist.id) { mutableStateOf(playlistPreview.playlist.isYoutubePlaylist) }

        val bookmark = object : MenuIcon, Descriptive, Clickable {
            override val iconId: Int = if (isBookmarked) R.drawable.bookmark else R.drawable.bookmark_outline
            override val messageId: Int = R.string.bookmark
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() {
                if (isSpecialPlaylist) {
                    Toaster.e(R.string.cannot_bookmark_special_playlist)
                    return
                }
                val wasBookmarked = isBookmarked
                coroutineScope.launch(Dispatchers.IO) {
                    val browseId = playlistPreview.playlist.browseId
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
                            Timber.tag("LocalPlaylistItemMenu").e(e, "Failed to toggle YTM bookmark")
                        }
                    }
                    Database.playlistTable.update(
                        playlistPreview.playlist.copy(isYoutubePlaylist = !wasBookmarked)
                    )
                    withContext(Dispatchers.Main) {
                        isBookmarked = !wasBookmarked
                    }
                    Toaster.s( if (!wasBookmarked) R.string.added_to_favorites else R.string.removed_from_favorites )
                }
            }
            override fun onLongClick() {}
        }

        // Define buttons
        buttons = remember(playlistPreview, isBookmarked) {
            val list = mutableListOf<Button>()
            
            list.add(shuffle)
            list.add(playNext)
            list.add(enqueue)
            list.add(downloadAll)
            list.add(deleteAll)
            

            if (playlistPreview.playlist.isEditable) {
                list.add(rename)
                if (playlistPreview.playlist.isYoutubePlaylist || playlistPreview.playlist.browseId?.startsWith(MODIFIED_PREFIX) == true || playlistPreview.playlist.browseId?.startsWith("VL") == true) {
                    list.add(changePlaylistId)
                }
                
                list.add(object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.trash
                    override val messageId: Int = R.string.delete
                    @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                    override fun onShortClick() {
                        menuState.hide()
                        coroutineScope.launch(Dispatchers.IO) {
                            val pushPlaylist = appContext().preferences.getBoolean(syncPushPlaylistKey, false)
                            val syncDirection = getSyncDirection()
                            if (playlistPreview.playlist.isYoutubePlaylist && isYouTubeSyncEnabled() && pushPlaylist && syncDirection != SyncDirection.YT_TO_APP && isNetworkConnected(appContext())) {
                                val browseId = playlistPreview.playlist.browseId
                                if (browseId != null) {
                                    YtMusic.deletePlaylist(browseId.removePrefix("VL"))
                                }
                            }
                            Database.playlistTable.delete(playlistPreview.playlist)
                        }
                        Toaster.done()
                    }
                    override fun onLongClick() {}
                })
            }
            
            if (playlistPreview.playlist.isYoutubePlaylist) {
                list.add(object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.play
                    override val messageId: Int = R.string.listen_on_youtube
                    @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                    override fun onShortClick() {
                        menuState.hide()
                        val browseId = playlistPreview.playlist.browseId ?: return
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://music.youtube.com/playlist?list=$browseId")
                        }
                        context.startActivity(intent)
                    }
                    override fun onLongClick() {}
                })
            }

            // Auto-sync toggle for this playlist
            if (playlistPreview.playlist.isYoutubePlaylist && isYouTubeSyncEnabled()) {
                list.add(object : MenuIcon, Descriptive, DynamicColor {
                    override var isFirstColor: Boolean = playlistPreview.playlist.isAutoSync
                    override val iconId: Int = R.drawable.sync
                    override val messageId: Int = R.string.sync_per_playlist_auto
                    @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                    override fun onShortClick() {
                        menuState.hide()
                        coroutineScope.launch(Dispatchers.IO) {
                            Database.playlistTable.toggleAutoSync(playlistPreview.playlist.id)
                            Toaster.done()
                        }
                    }
                    override fun onLongClick() {}
                })
            }

            list
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0)
        ) {
            downloadAllDialog.Render()
            deleteAllDialog.Render()
            PlaylistItemDisplay(playlistPreview, isBookmarked, onBookmarkToggle = {
                val wasBookmarked = isBookmarked
                coroutineScope.launch(Dispatchers.IO) {
                    val browseId = playlistPreview.playlist.browseId
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
                            Timber.tag("LocalPlaylistItemMenu").e(e, "Failed to toggle YTM bookmark from header")
                        }
                    }
                    Database.playlistTable.update(
                        playlistPreview.playlist.copy(isYoutubePlaylist = !wasBookmarked)
                    )
                    withContext(Dispatchers.Main) {
                        isBookmarked = !wasBookmarked
                    }
                    Toaster.s( if (!wasBookmarked) R.string.added_to_favorites else R.string.removed_from_favorites )
                }
            })

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
