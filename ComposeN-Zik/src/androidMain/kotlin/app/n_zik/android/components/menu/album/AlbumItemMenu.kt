package app.n_zik.android.components.menu.album

import app.n_zik.android.core.database.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.nextPage
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.Enqueue

import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumTitleDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumAuthorsDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumCoverDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.n_zik.android.components.song.GoToArtist
import app.n_zik.android.components.tab.SongShuffler

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class AlbumItemMenu private constructor(
    private val navController: NavController,
    private val album: Album,
    private val binder: PlayerServiceModern.Binder?,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    companion object {
        @Composable
        operator fun invoke(
            navController: NavController,
            album: Album,
            binder: PlayerServiceModern.Binder?
        ): AlbumItemMenu =
            AlbumItemMenu(
                navController = navController,
                album = album,
                binder = binder,
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
        album: Album,
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
                // Album's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2) // Reduced size for the menu
                ) {
                    ImageCacheFactory.Thumbnail(
                        thumbnailUrl = album.thumbnailUrl,
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
                        text = cleanPrefix(album.title ?: ""),
                        style = typography().xs.semiBold.copy(
                            color = colorPalette().text,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )

                    album.authorsText?.let {
                        BasicText(
                            text = cleanPrefix(it),
                            style = typography().xs.semiBold.secondary.copy(
                                color = colorPalette().textSecondary,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                        )
                    }

                    album.year?.let {
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

                // Trailing content (Bookmark & Share)
                val isBookmarked by remember(album.id) {
                    Database.albumTable
                        .isBookmarked(album.id)
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
                            CoroutineScope(Dispatchers.IO).launch {
                                Database.albumTable.toggleBookmark(album.id)
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
                                putExtra(Intent.EXTRA_TEXT, album.shareUrl ?: "https://music.youtube.com/browse/${album.id}")
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
        val changeTitle = ChangeAlbumTitleDialog { album }
        val changeAuthors = ChangeAlbumAuthorsDialog { album }
        val changeCover = ChangeAlbumCoverDialog { album }
        val changeId = ChangeAlbumBrowseIdDialog(menuState = menuState) { album }

        // Fetch songs lazily when menu opens
        val songs by remember(album.id) {
            Database.songAlbumMapTable
                    .allSongsOf( album.id )
                    .distinctUntilChanged()
        }.collectAsState( emptyList(), Dispatchers.IO )

        // Collect artists from the first song if available
        // We observe the songs list to react to its population
        val artistsData by remember(album.id) {
            val songsFlow = Database.songAlbumMapTable.allSongsOf(album.id).distinctUntilChanged()
            songsFlow.flatMapLatest { songList ->
                val firstSongId = songList.firstOrNull()?.id
                if (firstSongId != null) {
                    Database.artistTable.findBySongId(firstSongId)
                } else {
                    flowOf(emptyList())
                }
            }
        }.collectAsState(emptyList(), Dispatchers.IO)
        
        val changeArtistId = ChangeArtistBrowseIdDialog(menuState = menuState) { artistsData.firstOrNull() }

        // Group actions (Download/Delete all)
        val downloadAll = DownloadAllSongsDialog { songs }
        val deleteAll = DeleteAllDownloadedSongsDialog { songs }
        // Initialize buttons
        val playNext = PlayNext {
            binder?.player?.addNext(songs.map { it.asMediaItem }, appContext())
        }
        val enqueue = Enqueue {
            binder?.player?.enqueue(songs.map { it.asMediaItem }, appContext())
        }
        val addToPlaylist = PlaylistsMenu.init(
            navController = navController,
            mediaItems = { songs.map { it.asMediaItem } },
            onFailure = { _, _ -> },
            finalAction = { menuState.hide() },
            onDismiss = { openMenu() }
        )

        val shuffle = SongShuffler { songs }

        buttons = mutableListOf<Button>().apply {
            add(shuffle)
            add(playNext)
            add(enqueue)
            add(addToPlaylist)
            add(downloadAll)
            add(deleteAll)
            
            if (artistsData.isEmpty()) {
                val artistNames = album.authorsText
                    ?.split(",", "&")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                val firstSong = songs.firstOrNull()
                if (firstSong != null) {
                    if (artistNames.size <= 1) {
                        add(GoToArtist(navController, firstSong, menuState))
                    } else {
                        artistNames.forEach { artistName ->
                            add(object : MenuIcon, Descriptive, Clickable {
                                override val iconId: Int = R.drawable.people
                                override val messageId: Int = R.string.artists
                                @get:Composable
                                override val menuIconTitle: String get() = stringResource(R.string.more_of) + " $artistName"
                                override fun onShortClick() {
                                    menuState.hide()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        Innertube.nextPage(NextBody(videoId = firstSong.id))
                                            ?.getOrNull()
                                            ?.itemsPage?.items?.firstOrNull()
                                            ?.authors
                                            ?.find { it.name?.equals(artistName, ignoreCase = true) == true }
                                            ?.endpoint
                                            ?.takeIf { !it.browseId.isNullOrBlank() }
                                            ?.let {
                                                val path = "${it.browseId}?params=${it.params.orEmpty()}"
                                                NavRoutes.artist.navigateHere(navController, path)
                                            }
                                    }
                                }
                                override fun onLongClick() {}
                            })
                        }
                    }
                }
            } else {
                artistsData.forEach { artist ->
                    add(object : MenuIcon, Descriptive, Clickable {
                        override val iconId: Int = R.drawable.people
                        override val messageId: Int = R.string.artists
                        @get:Composable
                        override val menuIconTitle: String get() = stringResource(R.string.more_of) + " ${artist.name ?: ""}"
                        override fun onShortClick() {
                            menuState.hide()
                            navController.navigate("${NavRoutes.artist.name}/${artist.id}")
                        }
                    })
                }
            }

            add(changeTitle)
            add(changeAuthors)
            add(changeCover)
            add(changeId)
            add(changeArtistId)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0)
        ) {
            downloadAll.Render()
            deleteAll.Render()
            changeTitle.Render()
            changeAuthors.Render()
            changeCover.Render()
            changeId.Render()
            changeArtistId.Render()
            AlbumItemDisplay(album = album)

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





