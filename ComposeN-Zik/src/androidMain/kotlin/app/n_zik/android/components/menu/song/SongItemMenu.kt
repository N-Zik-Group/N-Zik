package app.n_zik.android.components.menu.song

import app.n_zik.android.core.database.*

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.nextPage
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberUpdatedState
import app.it.fast4x.rimusic.utils.getDownloadStateMedia
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import androidx.compose.ui.draw.alpha
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import androidx.compose.runtime.mutableStateOf
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.utils.forcePlay
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.song.ChangeAuthorDialog
import app.n_zik.android.components.dialog.song.ChangeCoverDialog
import app.n_zik.android.components.dialog.song.EditMetadataDialog
import app.n_zik.android.components.dialog.export.ExportCacheDialog
import app.n_zik.android.components.song.GoToAlbum
import app.n_zik.android.components.song.GoToArtist
import app.n_zik.android.components.dialog.song.RenameSongDialog
import app.n_zik.android.components.dialog.song.ResetSongDialog
import app.n_zik.android.components.dialog.tab.DeleteSongDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.n_zik.android.components.tab.LikeComponent
import app.n_zik.android.components.tab.Radio
import app.kreate.android.me.knighthat.sync.YouTubeSync
import timber.log.Timber
import java.util.Optional
import app.it.fast4x.rimusic.ui.components.themed.InProgressDialog
import app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreen
import kotlinx.coroutines.Dispatchers
import app.n_zik.android.BuildConfig
import androidx.compose.foundation.text.BasicText
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.enums.DownloadedStateMedia

@UnstableApi
@ExperimentalFoundationApi
class SongItemMenu private constructor(
    private val navController: NavController,
    private val song: Song,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
): Menu {

    companion object {
        @Composable
        operator fun invoke( navController: NavController, song: Song ) : SongItemMenu =
            SongItemMenu(
                navController = navController,
                song = song,
                menuState = LocalMenuState.current,
                styleState = rememberPreference( menuStyleKey, MenuStyle.List )
            )
    }

    lateinit var buttons: List<Button>
    var refreshBtn: Button? = null
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        // Section: Info
        SectionTitle(stringResource(R.string.information))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }

        if (song.isLocal) {
            // Local songs: editMetadata at index 1
            // Section: Management
            SectionTitle(stringResource(R.string.management))
            buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.filterIsInstance<ChangeAlbumBrowseIdDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.filterIsInstance<ChangeArtistBrowseIdDialog>().firstOrNull()?.let { it.ListMenuItem() }

            // Section: Playback
            SectionTitle(stringResource(R.string.playback))
            buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(6)?.let { if (it is MenuIcon) it.ListMenuItem() }
            refreshBtn?.let { if (it is MenuIcon) it.ListMenuItem() }

            // Delete/Export at the end
            for (i in 7 until buttons.size) {
                val btn = buttons.getOrNull(i)
                if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog) continue
                btn?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
        } else {
            // Remote songs: renameSong(1), changeAuthor(2), changeCover(3)
            // Section: Playback
            SectionTitle(stringResource(R.string.playback))
            buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(6)?.let { if (it is MenuIcon) it.ListMenuItem() }

            // Section: Management
            SectionTitle(stringResource(R.string.management))
            buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.filterIsInstance<ChangeAlbumBrowseIdDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.filterIsInstance<ChangeArtistBrowseIdDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.getOrNull(7)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(8)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.filterIsInstance<ResetSongDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.filterIsInstance<DeleteSongDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.filterIsInstance<ExportCacheDialog>().firstOrNull()?.let { it.ListMenuItem() }
            refreshBtn?.let { if (it is MenuIcon) it.ListMenuItem() }

            // Section: Navigation
            SectionTitle(stringResource(R.string.navigation))
            for (i in 9 until buttons.size) {
                val btn = buttons.getOrNull(i)
                if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog || btn is ResetSongDialog || btn is DeleteSongDialog || btn is ExportCacheDialog) continue
                btn?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
        }
    }

    @Composable
    override fun GridMenu() = GridMenu.Menu(title = null, showDragHandle = false) {
        // Section: Info
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.information))
        }
        buttons.getOrNull(0)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        if (song.isLocal) {
            // Local songs: editMetadata at index 1
            // Section: Management
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.management))
            }
            buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.filterIsInstance<ChangeAlbumBrowseIdDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<ChangeArtistBrowseIdDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }

            // Section: Playback
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.playback))
            }
            buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(6)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            refreshBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            // Delete/Export at the end
            for (i in 7 until buttons.size) {
                val btn = buttons.getOrNull(i)
                if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog) continue
                btn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        } else {
            // Remote songs: renameSong(1), changeAuthor(2), changeCover(3)
            // Section: Playback
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.playback))
            }
            buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(6)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            // Section: Management
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.management))
            }
            buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.filterIsInstance<ChangeAlbumBrowseIdDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<ChangeArtistBrowseIdDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.getOrNull(7)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(8)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.filterIsInstance<ResetSongDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<DeleteSongDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<ExportCacheDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            refreshBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            // Section: Navigation
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.navigation))
            }
            for (i in 9 until buttons.size) {
                val btn = buttons.getOrNull(i)
                if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog || btn is ResetSongDialog || btn is DeleteSongDialog || btn is ExportCacheDialog) continue
                btn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        }
    }

    @Composable
    override fun MenuComponent() {
        val context = LocalContext.current
        val binder = LocalPlayerServiceBinder.current

        val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
        val downloadStateMediaState = rememberUpdatedState(
            binder?.let { getDownloadStateMedia(it, song.id) } ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
        )
        
        refreshBtn = if (playerTimelineType == PlayerTimelineType.AudioWaves) {
            remember {
                object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.playing_indicator
                    override val messageId: Int = R.string.update_waveform
                    @get:Composable
                    override val menuIconTitle: String get() = stringResource(R.string.update_waveform)
                    
                    override val modifier: Modifier
                        get() = Modifier.alpha(
                            if (downloadStateMediaState.value == DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) 0.5f else 1f
                        )

                    override fun onShortClick() {
                        if (downloadStateMediaState.value == DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) {
                            Toaster.w(R.string.error_music_not_fully_cached)
                        } else {
                            Toaster.i(R.string.updating_waveform_in_progress)
                            CoroutineScope(Dispatchers.Main).launch {
                                WaveformExtractor.deleteWaveform(context, song.id)
                                val caches = listOfNotNull(binder?.cache, binder?.downloadCache)
                                val result = WaveformExtractor.getOrExtractWaveform(context, song.id, caches)
                                if (result != null) {
                                    Toaster.s(R.string.waveform_updated_successfully)
                                } else {
                                    Toaster.e(R.string.error_updating_waveform)
                                }
                            }
                            menuState.hide()
                        }
                    }
                    override fun onLongClick() {}
                }
            }
        } else null

        /*
         * This big chunk of code is currently running as singleton.
         * While it may not have a big impact on performance but
         * it's there. One way to mitigate this is to setup a
         * pre-defined buttons with each button has a function
         * to update song(s). This way the buttons only init once
         * but the song(s) can be updated as we go
         */
        //<editor-fold defaultstate="collapsed" desc="Buttons">
        val renameSong = RenameSongDialog{ song }
        val changeAuthor = ChangeAuthorDialog{ song }
        val changeCover = ChangeCoverDialog{ song }
        val editMetadata = EditMetadataDialog{ song }
        val startRadio = Radio { listOf(song) }
        val playNext = PlayNext {
            binder?.player?.addNext( listOf(song.asMediaItem), appContext() )
        }
        val enqueue = Enqueue {
            binder?.player?.enqueue( listOf(song.asMediaItem), appContext() )
        }

        // Information
        val albumForInfo by remember(song.id) {
            Database.albumTable.findBySongId(song.id)
        }.collectAsState(null, Dispatchers.IO)

        val infoButton = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.information
                override val messageId: Int = R.string.information
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId)

                override fun onShortClick() {
                    menuState.display {
                        VideoOrSongInfoScreen(
                            videoId = song.id,
                            songTitle = song.title,
                            songArtist = song.artistsText ?: "",
                            songThumbnailUrl = song.thumbnailUrl ?: "",
                            albumId = albumForInfo?.id ?: "",
                            albumTitle = albumForInfo?.title ?: "",
                            navController = navController,
                            onNavigateUp = { menuState.pop() },
                            onClose = { menuState.hide() },
                            onPlay = { binder?.player?.forcePlay(song.asMediaItem) }
                        )
                    }
                }
                override fun onLongClick() {}
            }
        }
        val addToFavorite = LikeComponent { listOf(song) }
        val addToPlaylist = PlaylistsMenu.init(
            navController = navController,
            mediaItems = { _ -> listOf(song.asMediaItem) },
            onFailure = { throwable, preview ->
                Timber.tag("SongItemMenu").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
            },
            finalAction = {},
            onDismiss = { openMenu() }
        )
        val deleteSongDialog = DeleteSongDialog().apply {
            song = Optional.of( this@SongItemMenu.song )
        }
        // Reactively collect artists from DB for per-artist "More of" buttons
        val artistsData by remember(song.id) {
            Database.artistTable.findBySongId(song.id)
        }.collectAsState(emptyList(), Dispatchers.IO)

        val goToArtistFallback = remember {
            GoToArtist( navController, song, menuState )
        }
        val goToAlbum = remember {
            GoToAlbum( navController, song, menuState )
        }
        val resetDialog = ResetSongDialog( song )
        val exportCacheDialog = ExportCacheDialog( binder ) { song }

        val changeAlbumId = ChangeAlbumBrowseIdDialog(menuState = menuState) { albumForInfo }
        val changeArtistId = ChangeArtistBrowseIdDialog(menuState = menuState) { artistsData.firstOrNull() }

        buttons = mutableListOf<Button>().apply {
            add( infoButton )
            if (song.isLocal) {
                if (BuildConfig.ENABLE_FFMPEG) add( editMetadata )
            } else {
                add( renameSong )
                add( changeAuthor )
                add( changeCover )
            }
            add( startRadio )
            add( playNext )
            add( enqueue )
            add( addToFavorite )
            add( addToPlaylist )
            if( !song.isLocal ) {
                add( goToAlbum )
                // Per-artist "More of" buttons
                if (artistsData.isEmpty()) {
                    // No DB data: split artistsText to create per-artist buttons
                    val artistNames = song.artistsText
                        ?.split(",", "&")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()

                    if (artistNames.size <= 1) {
                        // Single artist - use fallback with Innertube lookup
                        add( goToArtistFallback )
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
                                        // Try DB by name first (works after the search online populated it)
                                        val dbArtist = try {
                                            Database.artistTable.findByName(artistName).first()
                                        } catch (_: Exception) { null }
                                        if (dbArtist != null) {
                                            NavRoutes.artist.navigateHere(navController, dbArtist.id)
                                            return@launch
                                        }
                                        // Fallback: try Innertube nextPage
                                        Innertube.nextPage(NextBody(videoId = song.id))
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
                            override fun onLongClick() {}
                        })
                    }
                }
                add( changeAlbumId )
                add( changeArtistId )
                add( resetDialog )
            }
            if (!song.isLocal) {
                add( deleteSongDialog )
            }
            if (BuildConfig.ENABLE_FFMPEG) add( exportCacheDialog )
        }
        //</editor-fold>

        //<editor-fold desc="Dialog renders">
        if (song.isLocal) {
            editMetadata.Render()
        } else {
            renameSong.Render()
            changeAuthor.Render()
            changeCover.Render()
        }
        if (!song.isLocal) {
            changeAlbumId.Render()
            changeArtistId.Render()
            deleteSongDialog.Render()
        }
        resetDialog.Render()
        exportCacheDialog.Render()
        exportCacheDialog.RenderLyricsDialog(binder)

        if (exportCacheDialog.isExporting.value) {
            InProgressDialog(
                total = 0,
                done = 0,
                text = stringResource(R.string.exporting),
                onDismiss = null
            )
        }
        //</editor-fold>

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0)
        ) {
            // Song info header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background( colorPalette().background1 )
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 18.dp, bottom = 6.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )

                SongItem(
                    song = song,
                    backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.padding(
                        top = 5.dp,
                        bottom = 10.dp
                    ),
                    trailingContent = {
                        val isLiked by remember {
                            Database.songTable
                                    .isLiked( song.id )
                                    .distinctUntilChanged()
                        }.collectAsState( false, Dispatchers.IO )

                        Column(
                            Modifier.width( TabToolBar.TOOLBAR_ICON_SIZE )
                        ) {
                            IconButton(
                                icon = if ( isLiked ) R.drawable.heart else R.drawable.heart_outline,
                                color = colorPalette().favoritesIcon,
                                onClick = {
                                    CoroutineScope( Dispatchers.IO ).launch {
                                        YouTubeSync.toggleSongLike( context, song.asMediaItem )
                                    }
                                },
                                modifier = Modifier.padding( all = 4.dp ).size( 20.dp )
                            )

                            if( !song.isLocal )
                                IconButton(
                                    icon = R.drawable.share_social,
                                    color = colorPalette().text,
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra( Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}" )
                                        }

                                        context.startActivity(
                                            Intent.createChooser( intent, null )
                                        )
                                    },
                                    modifier = Modifier.padding( all = 4.dp ).size( 20.dp )
                                )
                        }
                    }
                )

                HorizontalDivider( Modifier.height(1.dp) )
            }

            if( menuStyle == MenuStyle.List )
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



