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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import it.fast4x.innertube.requests.nextPage
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import app.n_zik.android.download.utils.MyDownloadHelper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
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
import app.it.fast4x.rimusic.utils.showDislikedPlaylistKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.enums.DislikeMode
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
import app.n_zik.android.utils.player.addNextOffMain
import app.it.fast4x.rimusic.utils.asMediaItem
import app.n_zik.android.utils.player.enqueueOffMain
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.LastFmActions
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
import app.it.fast4x.rimusic.utils.splitArtistNames
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.song.ChangeAuthorDialog
import app.n_zik.android.components.dialog.song.ChangeCoverDialog
import app.n_zik.android.components.dialog.song.EditMetadataDialog
import app.n_zik.android.components.dialog.export.ExportCacheDialog
import app.n_zik.android.components.song.GoToAlbum
import app.n_zik.android.components.song.GoToArtist
import app.n_zik.android.components.dialog.song.RenameSongDialog
import app.n_zik.android.components.dialog.song.UpdateSongDialog
import app.n_zik.android.components.dialog.tab.DeleteSongDialog
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.n_zik.android.components.tab.LikeComponent
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.ui.screens.listentogether.ListenTogetherMenu
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.TrackInfo
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.kreate.android.me.knighthat.sync.YouTubeSync
import timber.log.Timber
import java.util.Optional
import app.it.fast4x.rimusic.ui.components.themed.InProgressDialog
import app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreen
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
    private var showLastFmSection = false
    private var showListenTogetherSection = false
    private var listenTogetherBtn: Button? = null
    private var listenTogetherDialogBtn: Button? = null
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        val lastFmSectionSize = if (showLastFmSection) 2 else 0
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

            // Section: Listen Together (room menu, then suggest-to-host for guests in a room)
            SectionTitle(stringResource(R.string.listen_together))
            listenTogetherDialogBtn?.let { if (it is MenuIcon) it.ListMenuItem() }
            listenTogetherBtn?.let { if (it is MenuIcon) it.ListMenuItem() }

            // Section: Management
            SectionTitle(stringResource(R.string.management))
            buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.filterIsInstance<ChangeAlbumBrowseIdDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.filterIsInstance<ChangeArtistBrowseIdDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.getOrNull(7)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(8)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.filterIsInstance<UpdateSongDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.filterIsInstance<DeleteSongDialog>().firstOrNull()?.let { it.ListMenuItem() }
            buttons.filterIsInstance<ExportCacheDialog>().firstOrNull()?.let { it.ListMenuItem() }
            refreshBtn?.let { if (it is MenuIcon) it.ListMenuItem() }

            // Section: Navigation
            SectionTitle(stringResource(R.string.navigation))
            for (i in 9 until buttons.size - lastFmSectionSize) {
                val btn = buttons.getOrNull(i)
                if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog || btn is UpdateSongDialog || btn is DeleteSongDialog || btn is ExportCacheDialog) continue
                btn?.let { if (it is MenuIcon) it.ListMenuItem() }
            }

            if (showLastFmSection) {
                // Section: Last.fm
                SectionTitle(stringResource(R.string.social_lastfm))
                buttons.getOrNull(buttons.size - 2)?.let { if (it is MenuIcon) it.ListMenuItem() }
                buttons.getOrNull(buttons.size - 1)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
        }
    }

    @Composable
    override fun GridMenu() = GridMenu.Menu(title = null, showDragHandle = false) {
        val lastFmSectionSize = if (showLastFmSection) 2 else 0
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

            // Section: Listen Together (room menu, then suggest-to-host for guests in a room)
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.listen_together))
            }
            listenTogetherDialogBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            listenTogetherBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

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
            buttons.filterIsInstance<UpdateSongDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<DeleteSongDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<ExportCacheDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            refreshBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            // Section: Navigation
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.navigation))
            }
            for (i in 9 until buttons.size - lastFmSectionSize) {
                val btn = buttons.getOrNull(i)
                if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog || btn is UpdateSongDialog || btn is DeleteSongDialog || btn is ExportCacheDialog) continue
                btn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }

            if (showLastFmSection) {
                // Section: Last.fm
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(stringResource(R.string.social_lastfm))
                }
                buttons.getOrNull(buttons.size - 2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
                buttons.getOrNull(buttons.size - 1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        }
    }

    @Composable
    override fun MenuComponent() {
        val context = LocalContext.current
        val binder = LocalPlayerServiceBinder.current
        val coroutineScope = rememberCoroutineScope()

        val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
        val showDisliked by rememberPreference(excludeDislikedSongsKey, DislikeMode.Enabled)
        val downloadStateMediaState = rememberUpdatedState(
            binder?.let { getDownloadStateMedia(it, song.id) } ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
        )
        val isDownloadInProgress by remember(song.id) {
            MyDownloadHelper.getDownload(song.id)
                .map {
                    it?.state == Download.STATE_QUEUED ||
                        it?.state == Download.STATE_DOWNLOADING ||
                        it?.state == Download.STATE_RESTARTING
                }
        }.collectAsStateWithLifecycle(initialValue = false, context = NzikDispatchers.DATA)
        val isDownloadInProgressState = rememberUpdatedState(isDownloadInProgress)

        refreshBtn = if (playerTimelineType == PlayerTimelineType.AudioWaves) {
            remember {
                object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.playing_indicator
                    override val messageId: Int = R.string.update_waveform
                    @get:Composable
                    override val menuIconTitle: String get() = stringResource(R.string.update_waveform)

                    override val modifier: Modifier
                        get() = Modifier.alpha(
                            if (downloadStateMediaState.value == DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED ||
                                isDownloadInProgressState.value
                            ) 0.5f else 1f
                        )

                    override fun onShortClick() {
                        if (downloadStateMediaState.value == DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED ||
                            isDownloadInProgressState.value
                        ) {
                            Toaster.w(R.string.error_music_not_fully_cached)
                        } else {
                            Toaster.i(R.string.updating_waveform_in_progress)
                            val caches = listOfNotNull(binder?.cache, binder?.downloadCache)
                            WaveformExtractor.requestUpdateWaveform(appContext(), song.id, caches) { result ->
                                when (result) {
                                    is WaveformResult.Success -> Toaster.s(R.string.waveform_updated_successfully)
                                    is WaveformResult.NotReady -> Toaster.i(R.string.waveform_update_after_download)
                                    is WaveformResult.NoCache, is WaveformResult.Failed -> Toaster.e(R.string.error_updating_waveform)
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
            // Issue #606 review fix: launch on a scope independent of the popup menu's lifecycle.
            // MenuComponent.kt hides the menu (cancelling its rememberCoroutineScope()) right after
            // onShortClick() returns, so a coroutineScope.launch here could be cancelled mid-flight
            // before player.addNextOffMain ever runs, silently dropping the tap.
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                val mediaItem = withContext(NzikDispatchers.DATA) { song.asMediaItem }
                binder?.player?.addNextOffMain( listOf(mediaItem), appContext() )
            }
        }
        val enqueue = Enqueue {
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                val mediaItem = withContext(NzikDispatchers.DATA) { song.asMediaItem }
                binder?.player?.enqueueOffMain( listOf(mediaItem), appContext() )
            }
        }

        // Information
        val albumForInfo by remember(song.id) {
            Database.albumTable.findBySongId(song.id)
        }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

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
                            onPlay = {
                                coroutineScope.launch {
                                    val mediaItem = withContext(NzikDispatchers.DATA) { song.asMediaItem }
                                    binder?.player?.forcePlay(mediaItem)
                                }
                            }
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
        }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

        val goToArtistFallback = remember {
            GoToArtist( navController, song, menuState )
        }
        val goToAlbum = remember {
            GoToAlbum( navController, song, menuState )
        }
        val updateDialog = UpdateSongDialog( song )
        val exportCacheDialog = ExportCacheDialog( binder ) { song }

        val changeAlbumId = ChangeAlbumBrowseIdDialog(menuState = menuState) { albumForInfo }
        val changeArtistId = ChangeArtistBrowseIdDialog(menuState = menuState) { artistsData.firstOrNull() }

        val isLastfmScrobblingEnabled by rememberEncryptedPreference(isLastfmScrobblingEnabledKey, false)
        val lastfmSession by rememberEncryptedPreference(lastfmSessionKey, "")
        showLastFmSection = !song.isLocal &&
            isLastfmScrobblingEnabled &&
            lastfmSession.isNotEmpty() &&
            BuildConfig.LASTFM_API_KEY.isNotEmpty() &&
            BuildConfig.LASTFM_API_SECRET.isNotEmpty()

        // Listen Together: guests in a room can suggest tracks to the host
        // (remote songs only — the host resolves YouTube ids to play).
        val ltManager = ListenTogetherManager.getInstance()
        showListenTogetherSection = !song.isLocal &&
            ltManager?.let { it.isInRoom && !it.isHost } == true

        listenTogetherBtn = if (showListenTogetherSection) {
            remember {
                object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.musical_notes
                    override val messageId: Int = R.string.listen_together_suggest
                    @get:Composable
                    override val menuIconTitle: String get() = stringResource(R.string.listen_together_suggest)

                    override fun onShortClick() {
                        menuState.hide()
                        val manager = ListenTogetherManager.getInstance() ?: return
                        if (!manager.isInRoom || manager.isHost) return
                        val durationMs = song.durationText
                            ?.let { durationTextToMillis(it) }
                            ?.takeIf { it > 0 }
                            ?: 180000L
                        manager.suggestTrack(
                            TrackInfo(
                                id = song.id,
                                title = song.cleanTitle(),
                                artist = song.cleanArtistsText(),
                                duration = durationMs,
                                thumbnail = song.thumbnailUrl.orEmpty(),
                            )
                        )
                    }
                    override fun onLongClick() {}
                }
            }
        } else null

        // Listen Together room dialog (Metrolist PlayerMenu dialog port): opens the
        // create/join/manage room popup through the shared menu state. Shown for every
        // song regardless of room state or role — it is the menu entry point to a room.
        listenTogetherDialogBtn = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.people
                override val messageId: Int = R.string.listen_together
                @get:Composable
                override val menuIconTitle: String get() = stringResource(R.string.listen_together)

                override fun onShortClick() {
                    menuState.display {
                        ListenTogetherMenu()
                    }
                }
                override fun onLongClick() {}
            }
        }

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
                        .splitArtistNames()

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
                                    coroutineScope.launch(NzikDispatchers.DATA) {
                                        // Try DB by name first (works after the search online populated it)
                                        val dbArtist = try {
                                            Database.artistTable.findByName(artistName).first()
                                        } catch (_: Exception) { null }
                                        if (dbArtist != null) {
                                            NavRoutes.artist.navigateHere(navController, dbArtist.id)
                                            return@launch
                                        }
                                        // Fallback: try Innertube nextPage
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
                                navController.navigate("${NavRoutes.artist.name}/${artist.id}")
                            }
                            override fun onLongClick() {}
                        })
                    }
                }
                add( changeAlbumId )
                add( changeArtistId )
                add( updateDialog )
            }
            if (!song.isLocal) {
                add( deleteSongDialog )
            }
            if (BuildConfig.ENABLE_FFMPEG) add( exportCacheDialog )
            if (showLastFmSection) {
                add( object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.heart
                    override val messageId: Int = R.string.lastfm_love
                    @get:Composable
                    override val menuIconTitle: String get() = stringResource(R.string.lastfm_love)

                    override fun onShortClick() {
                        menuState.hide()
                        LastFmActions.setLoveStatus(song.cleanArtistsText(), song.cleanTitle(), love = true)
                    }
                    override fun onLongClick() {}
                })
                add( object : MenuIcon, Descriptive, Clickable {
                    override val iconId: Int = R.drawable.heart_dislike
                    override val messageId: Int = R.string.lastfm_unlove
                    @get:Composable
                    override val menuIconTitle: String get() = stringResource(R.string.lastfm_unlove)

                    override fun onShortClick() {
                        menuState.hide()
                        LastFmActions.setLoveStatus(song.cleanArtistsText(), song.cleanTitle(), love = false)
                    }
                    override fun onLongClick() {}
                })
            }
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
        updateDialog.Render()
        exportCacheDialog.Render()

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
                        val likeState by remember(song.id) {
                            Database.songTable
                                    .likeState( song.id )
                                    .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

                        Column(
                            Modifier.width( TabToolBar.TOOLBAR_ICON_SIZE )
                        ) {
                            IconButton(
                                icon = when(likeState) {
                                    false -> R.drawable.heart_dislike
                                    null -> R.drawable.heart_outline
                                    else -> R.drawable.heart
                                },
                                color = when(likeState) {
                                    false -> colorPalette().red
                                    null -> colorPalette().text
                                    else -> colorPalette().favoritesIcon
                                },
                                onClick = {
                                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                        if (showDisliked.isEnabled) {
                                            YouTubeSync.rotateSongLikeState( context, song.asMediaItem )
                                        } else {
                                            YouTubeSync.toggleSongLikeState( context, song.asMediaItem )
                                        }
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



