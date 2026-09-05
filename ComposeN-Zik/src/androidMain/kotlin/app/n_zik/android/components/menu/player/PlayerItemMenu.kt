package app.n_zik.android.components.menu.player

import app.n_zik.android.core.database.*

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.nextPage
import it.fast4x.innertube.requests.song
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Info
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.components.themed.InProgressDialog
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.it.fast4x.rimusic.ui.components.themed.ListenOnDialog
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.dialog.song.ChangeAuthorDialog
import app.n_zik.android.components.dialog.song.ChangeCoverDialog
import app.n_zik.android.components.dialog.song.EditMetadataDialog
import app.n_zik.android.components.song.GoToAlbum
import app.n_zik.android.components.song.GoToArtist
import app.n_zik.android.components.dialog.export.ExportCacheDialog
import app.n_zik.android.components.dialog.song.RenameSongDialog
import app.n_zik.android.components.dialog.tab.DeleteSongDialog
import app.n_zik.android.components.tab.LikeComponent
import app.n_zik.android.components.tab.Radio
import java.util.Optional
import app.kreate.android.me.knighthat.sync.YouTubeSync
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreen
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.getDownloadStateMedia
import app.n_zik.android.typography
import app.n_zik.android.BuildConfig
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import androidx.compose.foundation.text.BasicText

@UnstableApi
@ExperimentalFoundationApi
class PlayerItemMenu private constructor(
    private val navController: NavController,
    private val binder: PlayerServiceModern.Binder,
    private val mediaItem: MediaItem,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>,
    private val onDismiss: () -> Unit,
    private val onClosePlayer: () -> Unit,
    private val onShowSleepTimer: () -> Unit
): Menu {

    companion object {
        fun create(
            navController: NavController,
            binder: PlayerServiceModern.Binder,
            mediaItem: MediaItem,
            menuState: MenuState,
            styleState: MutableState<MenuStyle>,
            onDismiss: () -> Unit,
            onClosePlayer: () -> Unit,
            onShowSleepTimer: () -> Unit
        ): PlayerItemMenu =
            PlayerItemMenu(
                navController = navController,
                binder = binder,
                mediaItem = mediaItem,
                menuState = menuState,
                styleState = styleState,
                onDismiss = onDismiss,
                onClosePlayer = onClosePlayer,
                onShowSleepTimer = onShowSleepTimer
            )
    }

    lateinit var buttons: List<Button>
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() {
        val song = remember(mediaItem) { mediaItem.asSong }
        val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
        ListMenu.Menu(title = null, showDragHandle = false) {
            // Section: Information
            SectionTitle(stringResource(R.string.information))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }

        if (song.isLocal) {
            // Local songs: 0=info, 1=waves, 2=editMetadata, 3=equalizer, 4=sleep, 5=fav, 6=playlist, 7+=delete/export
            SectionTitle(stringResource(R.string.management))
            buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }

            SectionTitle(stringResource(R.string.playback))
            if (playerTimelineType == PlayerTimelineType.AudioWaves) {
                buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
            buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(6)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(7)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(8)?.let { if (it is MenuIcon) it.ListMenuItem() }

            for (i in 9 until buttons.size) {
                buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
        } else {
            // Remote songs: 0=info, 1=waves, 2=rename, 3=author, 4=cover, 5=radio, 6=equalizer, 7=sleep, 8=fav, 9=playlist, 10=refetch, 11+=nav
            SectionTitle(stringResource(R.string.playback))
            buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(6)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(7)?.let { if (it is MenuIcon) it.ListMenuItem() }

            SectionTitle(stringResource(R.string.management))
            if (playerTimelineType == PlayerTimelineType.AudioWaves) {
                buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
            buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(8)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(9)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(10)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(11)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(12)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.filterIsInstance<DeleteSongDialog>().firstOrNull()?.let { it.ListMenuItem() }

            SectionTitle(stringResource(R.string.navigation))
            for (i in 13 until buttons.size) {
                val btn = buttons.getOrNull(i)
                if (btn is DeleteSongDialog) continue
                btn?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
        }
        }
    }

    @Composable
    override fun GridMenu() {
        val song = remember(mediaItem) { mediaItem.asSong }
        val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
        GridMenu.Menu(title = null, showDragHandle = false) {
            // Section: Information
            item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.information))
        }
        buttons.getOrNull(0)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        if (song.isLocal) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.management))
            }
            buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.playback))
            }
            if (playerTimelineType == PlayerTimelineType.AudioWaves) {
                buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
            buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(6)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(7)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(8)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            for (i in 9 until buttons.size) {
                buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.playback))
            }
            buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(6)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(7)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.management))
            }
            if (playerTimelineType == PlayerTimelineType.AudioWaves) {
                buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
            buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(8)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(9)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(10)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(11)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(12)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.filterIsInstance<DeleteSongDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.navigation))
            }
            for (i in 13 until buttons.size) {
                val btn = buttons.getOrNull(i)
                if (btn is DeleteSongDialog) continue
                btn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        }
        }
    }

    @Composable
    override fun MenuComponent() {
        val mContext = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val coroutineScope = rememberCoroutineScope()
        val song = remember(mediaItem) { mediaItem.asSong }
        val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)

        // Reactively collect Album and Artists (like the old menu)
        val albumData by remember(mediaItem.mediaId) {
            Database.albumTable.findBySongId(mediaItem.mediaId)
        }.collectAsState(null, Dispatchers.IO)
        
        val artistsData by remember(mediaItem.mediaId) {
            Database.artistTable.findBySongId(mediaItem.mediaId)
        }.collectAsState(emptyList(), Dispatchers.IO)

        // Pre-create GoTo objects to avoid race condition on channelId lookup
        val goToArtistObj = remember(song) { GoToArtist(navController, song, menuState) }
        val goToAlbumObj = remember(song) { GoToAlbum(navController, song, menuState) }

        //<editor-fold defaultstate="collapsed" desc="Buttons">
        val renameSong = RenameSongDialog { song }
        val changeAuthor = ChangeAuthorDialog { song }
        val changeCover = ChangeCoverDialog { song }
        val editMetadata = EditMetadataDialog { song }
        val startRadio = Radio { listOf(song) }
        val addToFavorite = LikeComponent { listOf(song) }
        
        val activityResultLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

        val equalizerButton = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.equalizer
                override val messageId: Int = R.string.equalizer
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId)

                override fun onShortClick() {
                    try {
                        activityResultLauncher.launch(
                            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                putExtra(
                                    AudioEffect.EXTRA_AUDIO_SESSION,
                                    binder.player.audioSessionId
                                )
                                putExtra(
                                    AudioEffect.EXTRA_PACKAGE_NAME,
                                    mContext.packageName
                                )
                                putExtra(
                                    AudioEffect.EXTRA_CONTENT_TYPE,
                                    AudioEffect.CONTENT_TYPE_MUSIC
                                )
                            }
                        )
                    } catch (e: ActivityNotFoundException) {
                        Toaster.w(R.string.info_not_find_application_audio)
                    }
                    menuState.hide()
                }
                override fun onLongClick() {}
            }
        }

        // Custom "Refetch" / "Update Song" button (from PlayerMenu logic)
        var showRefetchDialog by remember { mutableStateOf(false) }
        val refetchButton = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.refresh
                override val messageId: Int = R.string.update
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId)
                
                override fun onShortClick() {
                    showRefetchDialog = true
                }
                override fun onLongClick() {}
            }
        }

        val downloadStateMedia = getDownloadStateMedia(binder, mediaItem.mediaId)
        val downloadStateMediaState = rememberUpdatedState(downloadStateMedia)

        // Refresh Audio Waves
        val refreshAudioWavesButton = remember {
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
                    // Check state through rememberUpdatedState which holds the latest composition value
                    if (downloadStateMediaState.value == DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) {
                        Toaster.w(R.string.error_music_not_fully_cached)
                    } else {
                        // Toast info refresh in progress
                        Toaster.i(R.string.updating_waveform_in_progress)
                        
                        coroutineScope.launch(Dispatchers.Main) {
                            WaveformExtractor.deleteWaveform(mContext, mediaItem.mediaId)
                            val caches = listOfNotNull(binder.cache, binder.downloadCache)
                            val result = WaveformExtractor.getOrExtractWaveform(mContext, mediaItem.mediaId, caches)
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

        // Sleep Timer
        val sleepTimerButton = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.sleep
                override val messageId: Int = R.string.sleep_timer
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId)

                override fun onShortClick() {
                    onShowSleepTimer()
                }
                override fun onLongClick() {}
            }
        }

        // Add to Playlist
        val addToPlaylist = PlaylistsMenu.init(
            navController = navController,
            mediaItems = { _ -> listOf(song.asMediaItem) },
            onFailure = { throwable, preview ->
                Timber.tag("PlayerItemMenu").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
            },
            finalAction = {
                menuState.hide()
            },
            onDismiss = { openMenu() }
        )

        // Listen On
        var showListenOnDialog by remember { mutableStateOf(false) }
        val listenOnButton = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.play
                override val messageId: Int = R.string.listen_on
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId)

                override fun onShortClick() {
                    showListenOnDialog = true
                }
                override fun onLongClick() {}
            }
        }

        // Information
        val infoButton = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.information
                override val messageId: Int = R.string.information
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId)

                override fun onShortClick() {
                    menuState.display {
                        VideoOrSongInfoScreen(
                            videoId = mediaItem.mediaId,
                            songTitle = song.title,
                            songArtist = song.artistsText ?: "",
                            songThumbnailUrl = song.thumbnailUrl ?: "",
                            albumId = albumData?.id ?: "",
                            albumTitle = albumData?.title ?: "",
                            navController = navController,
                            onNavigateUp = { menuState.pop() },
                            onClose = { menuState.hide() },
                            onPlay = { binder.player.forcePlay(song.asMediaItem) }
                        )
                    }
                }
                override fun onLongClick() {}
            }
        }

        val deleteSongDialog = DeleteSongDialog().apply {
            this.song = Optional.of(this@PlayerItemMenu.mediaItem.asSong)
        }
        val exportCacheDialog = ExportCacheDialog(binder) { song }
        
        val changeAlbumId = ChangeAlbumBrowseIdDialog(menuState = menuState) { albumData }
        val changeArtistId = ChangeArtistBrowseIdDialog(menuState = menuState) { artistsData.firstOrNull() }

        // Re-order to match SongItemMenu layout exactly
        buttons = remember(song, albumData, artistsData) {
            mutableListOf<Button>().apply {
                add(infoButton)           // 0
                add(refreshAudioWavesButton) // 1

                if (song.isLocal) {
                    // Local songs: editMetadata, then player controls, then favorites/playlist, then export
                    if (BuildConfig.ENABLE_FFMPEG) add(editMetadata)        // 2
                    add(equalizerButton)     // 3
                    add(sleepTimerButton)    // 4
                    add(addToFavorite)       // 5
                    add(addToPlaylist)       // 6
                    
                    add(changeAlbumId)       // 7
                    add(changeArtistId)      // 8
                    
                    if (BuildConfig.ENABLE_FFMPEG) add(exportCacheDialog)
                } else {
                    // Remote songs
                    add(renameSong)          // 2
                    add(changeAuthor)        // 3
                    add(changeCover)         // 4
                    add(startRadio)          // 5
                    add(equalizerButton)     // 6
                    add(sleepTimerButton)    // 7
                    add(addToFavorite)       // 8
                    add(addToPlaylist)       // 9
                    add(refetchButton)       // 10
                    
                    add(changeAlbumId)       // 11
                    add(changeArtistId)      // 12

                    // Go to Album
                    add(object : MenuIcon, Descriptive, Clickable {
                        override val iconId: Int = R.drawable.album
                        override val messageId: Int = R.string.go_to_album
                        @get:Composable
                        override val menuIconTitle: String get() = stringResource(messageId)

                        override fun onShortClick() {
                            val albumId = albumData?.id
                            if (!albumId.isNullOrBlank()) {
                                onDismiss()
                                onClosePlayer()
                                navController.navigate(NavRoutes.album.name + "/$albumId")
                            } else if (song.title.isNotBlank()) {
                                onDismiss()
                                onClosePlayer()
                                goToAlbumObj.onShortClick()
                            } else {
                                Toaster.w(R.string.album_not_found)
                            }
                        }

                        override fun onLongClick() {}
                    })

                    // Go to Artist
                    if (artistsData.isEmpty()) {
                        val artistNames = song.artistsText
                            ?.split(",", "&")
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            ?: emptyList()

                        if (artistNames.size <= 1) {
                            add(object : MenuIcon, Descriptive, Clickable {
                                override val iconId: Int = R.drawable.people
                                override val messageId: Int = R.string.artists
                                @get:Composable
                                override val menuIconTitle: String get() = stringResource(R.string.more_of) + " ${song.cleanArtistsText()}"
                                override fun onShortClick() {
                                    menuState.hide()
                                    onClosePlayer()
                                    goToArtistObj.onShortClick()
                                }
                                override fun onLongClick() {}
                            })
                        } else {
                            artistNames.forEach { artistName ->
                                add(object : MenuIcon, Descriptive, Clickable {
                                    override val iconId: Int = R.drawable.people
                                    override val messageId: Int = R.string.artists
                                    @get:Composable
                                    override val menuIconTitle: String get() = stringResource(R.string.more_of) + " $artistName"
                                    override fun onShortClick() {
                                        menuState.hide()
                                        onClosePlayer()
                                        coroutineScope.launch(Dispatchers.IO) {
                                            Innertube.nextPage(videoId = song.id)
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
                                    onClosePlayer()
                                    navController.navigate("${NavRoutes.artist.name}/${artist.id}")
                                }
                                override fun onLongClick() {}
                            })
                        }
                    }

                    add(listenOnButton)
                    add(deleteSongDialog)
                }
            }
        }
        //</editor-fold>

        //<editor-fold desc="Dialog renders">
        if (song.isLocal) {
            editMetadata.Render()
            exportCacheDialog.Render()

            if (exportCacheDialog.isExporting.value) {
                InProgressDialog(
                    total = 0,
                    done = 0,
                    text = stringResource(R.string.exporting),
                    onDismiss = null
                )
            }
        } else {
            renameSong.Render()
            changeAuthor.Render()
            changeCover.Render()
            deleteSongDialog.Render()
        }
        
        changeAlbumId.Render()
        changeArtistId.Render()
        
        if (showRefetchDialog) {
            ConfirmationDialog(
                text = stringResource(R.string.update_song),
                onDismiss = { showRefetchDialog = false },
                onConfirm = {
                    showRefetchDialog = false
                    menuState.hide()
                    binder.cache.removeResource(mediaItem.mediaId)
                    binder.downloadCache.removeResource(mediaItem.mediaId)
                    val videoId = mediaItem.mediaId.split("/").lastOrNull() ?: mediaItem.mediaId
                    coroutineScope.launch(Dispatchers.IO) {
                        Database.asyncTransaction {
                            Database.songTable.updateTotalPlayTime(mediaItem.mediaId, 0)
                        }
                        val songItem = Innertube.song(videoId)?.getOrNull()
                        if (songItem != null) {
                            Database.asyncTransaction {
                                val fetchedSong = songItem.asSong
                                val dbSong = Database.songTable.findByIdDirect(videoId)
                                if (dbSong != null && fetchedSong != null) {
                                    Database.songTable.updateReplace(dbSong.copy(
                                        title = fetchedSong.title ?: dbSong.title,
                                        artistsText = fetchedSong.artistsText ?: dbSong.artistsText,
                                        thumbnailUrl = fetchedSong.thumbnailUrl ?: dbSong.thumbnailUrl,
                                        durationText = fetchedSong.durationText ?: dbSong.durationText,
                                        likedAt = dbSong.likedAt,
                                        totalPlayTimeMs = dbSong.totalPlayTimeMs,
                                        position = dbSong.position
                                    ))
                                }
                            }
                        }
                    }
                }
            )
        }

        if (showListenOnDialog) {
             ListenOnDialog(
                mediaId = mediaItem.mediaId,
                onDismiss = { showListenOnDialog = false },
                onPlayOnUrl = {
                    binder.player.pause()
                    showListenOnDialog = false
                    menuState.hide()
                    uriHandler.openUri(it)
                }
            )
        }
        //</editor-fold>
        

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(colorPalette().background1)
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
                        val likeState by remember(song.id) {
                            Database.songTable
                                    .likeState(song.id)
                                    .distinctUntilChanged()
                        }.collectAsState(null, Dispatchers.IO)

                        Column {
                            IconButton(
                                icon = when(likeState) {
                                    false -> R.drawable.heart_dislike
                                    null -> R.drawable.heart_outline
                                    else -> R.drawable.heart
                                },
                                color = colorPalette().favoritesIcon,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    YouTubeSync.rotateSongLikeState( mContext, song.asMediaItem )
                                }
                            },
                                modifier = Modifier.padding(all = 4.dp).size(20.dp)
                            )

                            IconButton(
                                icon = R.drawable.share_social,
                                color = colorPalette().text,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                                    }

                                    mContext.startActivity(
                                        Intent.createChooser(intent, null)
                                    )
                                },
                                modifier = Modifier.padding(all = 4.dp).size(20.dp)
                            )
                        }
                    }
                )

                HorizontalDivider(Modifier.height(1.dp))
            }

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




