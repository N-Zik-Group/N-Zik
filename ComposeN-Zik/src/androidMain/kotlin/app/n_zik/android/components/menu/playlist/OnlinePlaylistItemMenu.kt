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
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.n_zik.android.components.dialog.playlist.ImportPlaylistDialog
import app.n_zik.android.components.dialog.playlist.ChangePlaylistBrowseIdDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.components.tab.SongShuffler
import android.net.Uri

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class OnlinePlaylistItemMenu private constructor(
    private val navController: NavController,
    private val playlist: Innertube.PlaylistItem,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

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
        buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Navigation
        if (buttons.size > 6) {
            SectionTitle(stringResource(R.string.navigation))
            for (i in 6 until buttons.size) {
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

        // Section: Management
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.management))
        }
        buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        // Section: Navigation
        if (buttons.size > 6) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.navigation))
            }
            for (i in 6 until buttons.size) {
                buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        }
    }

    @Composable
    private fun PlaylistItemDisplay(
        title: String?,
        authorText: String?,
        thumbnailUrl: String?,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
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
                // Playlist's thumbnail
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

                // Playlist's information
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

                    BasicText(
                        text = authorText ?: "",
                        style = typography().xs.semiBold.secondary.copy(
                            color = colorPalette().textSecondary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                }

                // Trailing content (Share / Open)
                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        icon = R.drawable.share_social,
                        color = colorPalette().text,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/playlist?list=${playlist.key}")
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
        val coroutineScope = rememberCoroutineScope()

        var songs by remember { mutableStateOf<List<Song>?>(null) }
        
        var displayTitle by remember { mutableStateOf(playlist.title ?: playlist.info?.name) }
        var displayAuthor by remember { mutableStateOf(playlist.channel?.name) }
        var displayThumbnailUrl by remember { mutableStateOf(playlist.thumbnail?.url) }

        val localPlaylistFlow = remember(playlist.key) { Database.playlistTable.findByBrowseId(playlist.key) }
        val localPlaylist by localPlaylistFlow.collectAsState(null, Dispatchers.IO)
        val changePlaylistId = localPlaylist?.let { lp ->
            ChangePlaylistBrowseIdDialog(menuState = menuState) { lp }
        }

        LaunchedEffect(playlist.key) {
            withContext(Dispatchers.IO) {
                val result = YtMusic.getPlaylist(playlist.key.removePrefix(MODIFIED_PREFIX)).getOrNull()
                if (result != null) {
                    displayTitle = result.playlist.title.takeIf { !it.isNullOrBlank() } ?: displayTitle
                    displayAuthor = result.playlist.channel?.name.takeIf { !it.isNullOrBlank() } ?: displayAuthor
                    displayThumbnailUrl = result.playlist.thumbnail?.url.takeIf { !it.isNullOrBlank() } ?: displayThumbnailUrl
                    songs = result.songs.mapNotNull { it.asSong }
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
        
        val importDialog = ImportPlaylistDialog(
            initialValue = cleanPrefix(playlist.title ?: "")
        ) { text ->
            coroutineScope.launch(Dispatchers.IO) {
                Database.asyncTransaction {
                    val newPlaylist = Playlist(name = text, browseId = playlist.key)
                    val playlistId = Database.playlistTable.insert(newPlaylist)
                    songs?.forEach { song ->
                        Database.insertIgnore(song.asMediaItem)
                        Database.songPlaylistMapTable.map(songId = song.id, playlistId = playlistId)
                    }
                }
            }
            menuState.hide()
            Toaster.done()
        }
        importDialog.Render()

        val importPlaylist = object : MenuIcon, Descriptive, Clickable {
            override val iconId: Int = R.drawable.add_in_playlist
            override val messageId: Int = R.string.import_playlist
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() {
                if (songs == null) {
                    Toaster.w(R.string.opening_url)
                } else if (songs?.isNotEmpty() == true) {
                    importDialog.onShortClick()
                } else {
                    Toaster.e(R.string.no_song_found)
                }
            }
            override fun onLongClick() {}
        }

        val shuffle = SongShuffler { songs ?: emptyList() }
        
        val list = mutableListOf<MenuIcon>()
        list.add(playNext)
        list.add(enqueue)
        list.add(shuffle)
        if (changePlaylistId != null) list.add(changePlaylistId)
        list.add(addToPlaylist)
        list.add(importPlaylist)
        list.add(downloadAll)
        list.add(deleteAll)
        
        buttons = list.apply {
            val artistName = playlist.channel?.name
            val browseId = playlist.channel?.endpoint?.browseId
            if (!artistName.isNullOrBlank() && !browseId.isNullOrBlank()) {
                add(object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.people
                    override val messageId: Int = R.string.artists
                    @get:Composable override val menuIconTitle: String get() = stringResource(R.string.more_of) + " $artistName"
                    override fun onShortClick() {
                        menuState.hide()
                        val path = "$browseId?params=${playlist.channel?.endpoint?.params.orEmpty()}"
                        NavRoutes.artist.navigateHere(navController, path)
                    }
                    override fun onLongClick() {}
                })
            }
            
            add(object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.ytmusic
                override val messageId: Int = R.string.listen_on_youtube
                @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                override fun onShortClick() {
                    menuState.hide()
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://music.youtube.com/playlist?list=${playlist.key}")
                    }
                    context.startActivity(intent)
                }
                override fun onLongClick() {}
            })
            
            list
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0)
        ) {
            downloadAllDialog.Render()
            deleteAllDialog.Render()
            changePlaylistId?.Render()
            PlaylistItemDisplay(
                title = displayTitle,
                authorText = displayAuthor,
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
