package app.n_zik.android.components.menu.song

import app.n_zik.android.components.menu.MenuSectionTitle
import java.util.Optional

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.PlayerTimelineType
import app.n_zik.android.models.Song
import app.n_zik.android.utils.resolveTrackArtistName
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Clickable
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.ui.screens.info.VideoOrSongInfoScreen
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.getDownloadStateMedia
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.playerTimelineTypeKey
import app.n_zik.android.utils.rememberEncryptedPreference
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.splitArtistNames
import app.n_zik.android.core.sync.YouTubeSync
import app.n_zik.android.core.sync.FavoriteAction
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.BuildConfig
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.components.menu.playlist.OpenCollectionMenuAction
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.n_zik.android.components.dialog.export.ExportCacheDialog
import app.n_zik.android.components.dialog.song.ChangeAuthorDialog
import app.n_zik.android.components.dialog.song.ChangeCoverDialog
import app.n_zik.android.components.dialog.song.EditMetadataDialog
import app.n_zik.android.components.dialog.song.RenameSongDialog
import app.n_zik.android.components.dialog.song.UpdateSongDialog
import app.n_zik.android.components.dialog.tab.DeleteSongDialog
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.rememberListenTogetherTrackMenuActions
import app.n_zik.android.components.menu.menuLikeActionDescription
import app.n_zik.android.components.menu.rememberListenOnMenuAction
import app.n_zik.android.components.song.GoToAlbum
import app.n_zik.android.components.menu.player.playerMenuMediaItem
import app.n_zik.android.components.song.GoToArtist
import app.n_zik.android.components.song.chooseTrackArtist
import app.n_zik.android.components.song.rememberTrackAlbumLink
import app.n_zik.android.components.tab.LikeComponent
import app.n_zik.android.components.tab.showAddToFavoritesShortcut
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.core.database.Database
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import app.n_zik.android.extensions.lastfm.LastFmActions
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.withPodcastSource
import app.n_zik.android.utils.withPlaybackAlbum

@UnstableApi
@ExperimentalFoundationApi
class SongItemMenu private constructor(
    private val navController: NavController,
    song: Song,
    playbackAlbumId: String?,
    playbackAlbumTitle: String?,
    sourceMediaItem: (() -> MediaItem)?,
    private val podcastBrowseId: String?,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>,
    private val onMenuDestinationOpened: (() -> Unit)?,
): Menu {

    private val source by lazy { sourceMediaItem?.invoke() }
    private val albumId by lazy { playbackAlbumId ?: source?.mediaMetadata?.extras?.getString("albumId") }
    private val albumTitle by lazy { playbackAlbumTitle ?: source?.mediaMetadata?.albumTitle?.toString() }

    private val snapshot = SongMenuSnapshot(song, getActiveProfile(appContext()))
    private val song: Song get() = snapshot.song

    companion object {
        @Composable
        operator fun invoke(
            navController: NavController,
            song: Song,
            playbackAlbumId: String? = null,
            playbackAlbumTitle: String? = null,
            podcastBrowseId: String? = null,
            sourceMediaItem: (() -> MediaItem)? = null,
            onMenuDestinationOpened: (() -> Unit)? = null,
        ): SongItemMenu =
            SongItemMenu(
                navController = navController,
                song = song,
                playbackAlbumId = playbackAlbumId,
                playbackAlbumTitle = playbackAlbumTitle,
                podcastBrowseId = podcastBrowseId,
                sourceMediaItem = sourceMediaItem,
                menuState = LocalMenuState.current,
                styleState = rememberPreference( menuStyleKey, MenuStyle.List ),
                onMenuDestinationOpened = onMenuDestinationOpened,
            )
    }

    override fun openMenu() = menuState.display(onMenuDestinationOpened) { MenuComponent() }

    private var buttons: List<Button> = emptyList()
    var refreshBtn: Button? = null
    private var showLastFmSection = false
    // Full offline mode (spec-full-offline-mode): network menu entries are pruned.
    // Set in MenuComponent before the List/Grid menus render.
    private var offlineMode = false
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu(buttons, rememberListenTogetherTrackMenuActions(song, menuState))

    @Composable
    private fun ListMenu(
        buttons: List<Button>,
        listenTogetherActions: List<MenuIcon>,
    ) = ListMenu.Menu(title = null, showDragHandle = false, drawSurface = false) {
        val lastFmSectionSize = if (showLastFmSection) 2 else 0
        // Section: Info
        MenuSectionTitle(stringResource(R.string.common_information_title))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }

        if (song.isLocal) {
            // Local songs: editMetadata at index 1 (always present — the FFmpeg module
            // is downloaded on first use, spec-ffmpeg-extension-module)
            val playbackStart = LOCAL_SONG_PLAYBACK_START
            // Section: Management
            MenuSectionTitle(stringResource(R.string.library_management_section_title))
            for (i in 1 until playbackStart) {
                buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
            buttons.filterIsInstance<ExportCacheDialog>().firstOrNull()?.ListMenuItem()
            for (i in (playbackStart + LOCAL_PLAYBACK_COUNT - 2) until playbackStart + LOCAL_PLAYBACK_COUNT) {
                buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }

            // Section: Playback
            MenuSectionTitle(stringResource(R.string.common_playback_section_title))
            // Full offline mode: startRadio (the first playback entry) needs the network — skipped
            for (i in (playbackStart + 1) until playbackStart + LOCAL_PLAYBACK_COUNT - 2) {
                buttons.getOrNull(i)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
            refreshBtn?.let { if (it is MenuIcon) it.ListMenuItem() }

            MenuSectionTitle(stringResource(R.string.common_navigation_section_title))
            buttons.localTrackNavigationActions().forEach { it.ListMenuItem() }
        } else {
            // Remote songs: renameSong(1), changeAuthor(2), changeCover(3)
            // Section: Playback
            MenuSectionTitle(stringResource(R.string.common_playback_section_title))
            if (!offlineMode) buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(6)?.let { if (it is MenuIcon) it.ListMenuItem() }

            // Section: Listen Together (room menu, then suggest-to-host for guests in a room) —
            // pruned in full offline mode (network feature)
            if (listenTogetherActions.isNotEmpty()) {
                MenuSectionTitle(stringResource(R.string.listen_together_title))
                listenTogetherActions.forEach { it.ListMenuItem() }
            }

            // Section: Management
            MenuSectionTitle(stringResource(R.string.library_management_section_title))
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
            MenuSectionTitle(stringResource(R.string.common_navigation_section_title))
            for (i in 9 until buttons.size - lastFmSectionSize) {
                val btn = buttons.getOrNull(i)
                if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog || btn is UpdateSongDialog || btn is DeleteSongDialog || btn is ExportCacheDialog) continue
                btn?.let { if (it is MenuIcon) it.ListMenuItem() }
            }

            if (showLastFmSection && !offlineMode) {
                // Section: Last.fm
                MenuSectionTitle(stringResource(R.string.settings_lastfm_title))
                buttons.getOrNull(buttons.size - 2)?.let { if (it is MenuIcon) it.ListMenuItem() }
                buttons.getOrNull(buttons.size - 1)?.let { if (it is MenuIcon) it.ListMenuItem() }
            }
        }
    }

    @Composable
    override fun GridMenu() = GridMenu(buttons, rememberListenTogetherTrackMenuActions(song, menuState))

    @Composable
    private fun GridMenu(
        buttons: List<Button>,
        listenTogetherActions: List<MenuIcon>,
    ) = GridMenu.Menu(title = null, showDragHandle = false, drawSurface = false) {
        val lastFmSectionSize = if (showLastFmSection) 2 else 0
        // Section: Info
        item(span = { GridItemSpan(maxLineSpan) }) {
            MenuSectionTitle(stringResource(R.string.common_information_title))
        }
        buttons.getOrNull(0)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        if (song.isLocal) {
            // Local songs: editMetadata at index 1 (always present — the FFmpeg module
            // is downloaded on first use, spec-ffmpeg-extension-module)
            val playbackStart = LOCAL_SONG_PLAYBACK_START
            // Section: Management
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.library_management_section_title))
            }
            for (i in 1 until playbackStart) {
                buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
            buttons.filterIsInstance<ExportCacheDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            for (i in (playbackStart + LOCAL_PLAYBACK_COUNT - 2) until playbackStart + LOCAL_PLAYBACK_COUNT) {
                val action = buttons.getOrNull(i) as? MenuIcon ?: continue
                if (action is LikeComponent && !action.isMenuVisible) continue
                item { action.GridMenuItem() }
            }

            // Section: Playback
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.common_playback_section_title))
            }
            // Full offline mode: startRadio (the first playback entry) needs the network — skipped
            for (i in (playbackStart + 1) until playbackStart + LOCAL_PLAYBACK_COUNT - 2) {
                buttons.getOrNull(i)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
            refreshBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.common_navigation_section_title))
            }
            buttons.localTrackNavigationActions().forEach { button -> item { button.GridMenuItem() } }
        } else {
            // Remote songs: renameSong(1), changeAuthor(2), changeCover(3)
            // Section: Playback
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.common_playback_section_title))
            }
            if (!offlineMode) buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(6)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            // Section: Listen Together (room menu, then suggest-to-host for guests in a room) —
            // pruned in full offline mode (network feature)
            if (listenTogetherActions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MenuSectionTitle(stringResource(R.string.listen_together_title))
                }
                listenTogetherActions.forEach { action -> item { action.GridMenuItem() } }
            }

            // Section: Management
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.library_management_section_title))
            }
            buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.filterIsInstance<ChangeAlbumBrowseIdDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<ChangeArtistBrowseIdDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            (buttons.getOrNull(7) as? LikeComponent)?.takeIf { it.isMenuVisible }?.let { item { it.GridMenuItem() } }
            buttons.getOrNull(8)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.filterIsInstance<UpdateSongDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<DeleteSongDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            buttons.filterIsInstance<ExportCacheDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
            refreshBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

            // Section: Navigation
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.common_navigation_section_title))
            }
            for (i in 9 until buttons.size - lastFmSectionSize) {
                val btn = buttons.getOrNull(i)
                if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog || btn is UpdateSongDialog || btn is DeleteSongDialog || btn is ExportCacheDialog) continue
                btn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }

            if (showLastFmSection && !offlineMode) {
                // Section: Last.fm
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MenuSectionTitle(stringResource(R.string.settings_lastfm_title))
                }
                buttons.getOrNull(buttons.size - 2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
                buttons.getOrNull(buttons.size - 1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            }
        }
    }

    @Composable
    override fun MenuComponent() {
        val context = LocalContext.current
        ObserveSongMenuSnapshot(snapshot, onProfileChanged = menuState::hide)
        val binder = LocalPlayerServiceBinder.current

        // Full offline mode (spec-full-offline-mode): prune network entries from the menu
        offlineMode = OfflineMode.rememberEnabled().value

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
                    override val messageId: Int = R.string.audio_waveform_action_refresh
                    @get:Composable
                    override val menuIconTitle: String get() = stringResource(R.string.audio_waveform_action_refresh)

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
                            Toaster.w(R.string.audio_waveform_complete_file_required_message)
                        } else {
                            Toaster.i(R.string.audio_waveform_updating_message)
                            val caches = listOfNotNull(binder?.cache, binder?.downloadCache)
                            WaveformExtractor.requestUpdateWaveform(appContext(), song.id, caches) { result ->
                                when (result) {
                                    is WaveformResult.Success -> Toaster.s(R.string.audio_waveform_updated_message)
                                    is WaveformResult.NotReady -> Toaster.i(R.string.audio_waveform_waiting_for_download_message)
                                    is WaveformResult.NoCache, is WaveformResult.Failed -> Toaster.e(R.string.audio_waveform_update_failed_message)
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
        fun mediaItemForPlayback(): MediaItem {
            val item = (source?.let { playerMenuMediaItem(it, song) } ?: song.asMediaItem)
                .withPodcastSource(podcastBrowseId)
            val explicitAlbum = albumId?.takeUnless { song.isLocal }
            return if (explicitAlbum != null) item.withPlaybackAlbum(explicitAlbum, albumTitle) else item
        }
        val startRadio = Radio.fromMediaItem { mediaItemForPlayback() }
        val canEditQueue = binder != null && !rememberListenTogetherGuestLock()
        val playNext = PlayNext(enabled = canEditQueue) {
            val queueEdit = binder?.captureMenuQueueEdit() ?: return@PlayNext
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                val mediaItem = withContext(NzikDispatchers.DATA) { mediaItemForPlayback() }
                queueEdit.queue(listOf(mediaItem), appContext(), playNext = true)
            }
        }
        val enqueue = Enqueue(enabled = canEditQueue) {
            val queueEdit = binder?.captureMenuQueueEdit() ?: return@Enqueue
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                val mediaItem = withContext(NzikDispatchers.DATA) { mediaItemForPlayback() }
                queueEdit.queue(listOf(mediaItem), appContext(), playNext = false)
            }
        }

        // Information
        val albumLink by rememberTrackAlbumLink(song, albumId, albumTitle)

        val infoButton = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.information
                override val messageId: Int = R.string.common_information_title
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId)

                override fun onShortClick() {
                    menuState.display {
                        VideoOrSongInfoScreen(
                            videoId = song.id,
                            songTitle = song.title,
                            songArtist = song.artistsText ?: "",
                            songThumbnailUrl = song.thumbnailUrl ?: "",
                            albumId = albumLink?.album?.id.orEmpty(),
                            albumTitle = albumLink?.album?.title.orEmpty(),
                            navController = navController,
                            source = source,
                            onNavigateUp = { menuState.pop() },
                            onClose = { menuState.hide() },
                            onPlay = {
                                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                                    val mediaItem = withContext(NzikDispatchers.DATA) { mediaItemForPlayback() }
                                    binder?.player?.forcePlay(mediaItem)
                                }
                            }
                        )
                    }
                }
                override fun onLongClick() {}
            }
        }
        val addToFavorite = LikeComponent.forMediaItem(
            isVisible = { showAddToFavoritesShortcut(song.likedAt, showDisliked.isEnabled) },
        ) {
            source?.let { playerMenuMediaItem(it, song) } ?: song.asMediaItem
        }
        val addToPlaylist = PlaylistsMenu.init(
            navController = navController,
            songId = song.id,
            mediaItems = { _ -> listOf(mediaItemForPlayback()) },
            localDestinationsOnly = song.isLocal,
            onFailure = { throwable, preview ->
                Timber.tag("SongItemMenu").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
            },
            finalAction = {}
        )
        val deleteSongDialog = DeleteSongDialog().apply {
            song = Optional.of( this@SongItemMenu.song )
        }
        // Reactively collect artists from DB for per-artist "More of" buttons
        val artistsData by remember(song.id) {
            Database.artistTable.findBySongId(song.id)
        }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

        val artists = rememberTrackMenuArtists(source, song, artistsData)
        val goToArtistFallback = remember(song) {
            GoToArtist( navController, song, menuState )
        }
        val goToAlbum = remember(song, albumId, albumTitle) {
            GoToAlbum(
                navController, song, menuState, albumId, albumTitle,
            )
        }
        val updateDialog = UpdateSongDialog( song )
        val exportCacheDialog = ExportCacheDialog( binder ) { song }
        val listenOn = if (!offlineMode && !song.isLocal) rememberListenOnMenuAction(song.id, menuState) else null

        val metadataTrack = song.takeUnless { it.isLocal }
        val changeAlbumId = ChangeAlbumBrowseIdDialog(
            menuState = menuState,
            targetTrack = metadataTrack,
            initialQuery = albumTitle,
        ) { albumLink?.album }
        val changeArtistId = ChangeArtistBrowseIdDialog(
            menuState = menuState,
            targetTrack = metadataTrack,
        ) { chooseTrackArtist(artistsData, null) }

        val isLastfmScrobblingEnabled by rememberEncryptedPreference(isLastfmScrobblingEnabledKey, false)
        val lastfmSession by rememberEncryptedPreference(lastfmSessionKey, "")
        showLastFmSection = !offlineMode && !song.isLocal &&
            isLastfmScrobblingEnabled &&
            lastfmSession.isNotEmpty() &&
            BuildConfig.LASTFM_API_KEY.isNotEmpty() &&
            BuildConfig.LASTFM_API_SECRET.isNotEmpty()

        val updatedButtons = buildList<Button> {
            add( infoButton )
            if (song.isLocal) {
                add(editMetadata)
                add(renameSong)
                add(changeCover)
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
            if (song.isLocal) {
                add(goToAlbum)
                if (artists.isEmpty()) {
                    goToArtistFallback.takeIf { it.isEnabled }?.let(::add)
                } else {
                    artists.forEach { artist ->
                        add(GoToArtist(navController, song, menuState, artistName = artist.name, artistId = artist.id))
                    }
                }
            }
            if( !song.isLocal ) {
                if (podcastBrowseId != null) {
                    add(OpenCollectionMenuAction(navController, menuState, podcastBrowseId, R.drawable.podcast))
                } else {
                    add(goToAlbum)
                }
                // Per-artist "More of" buttons
                if (artists.isEmpty()) {
                    // No DB data: split artistsText to create per-artist buttons
                    val artistNames = song.artistsText
                        .splitArtistNames()

                    if (artistNames.size <= 1) {
                        // Single artist - use fallback with Innertube lookup
                        goToArtistFallback.takeIf { it.isEnabled }?.let(::add)
                    } else {
                        artistNames.forEach { artistName ->
                            add(GoToArtist(navController, song, menuState, artistName = artistName))
                        }
                    }
                } else {
                    artists.forEach { artist ->
                        add(GoToArtist(navController, song, menuState, artistName = artist.name, artistId = artist.id))
                    }
                }
                add( changeAlbumId )
                add( changeArtistId )
                add( updateDialog )
                listenOn?.let(::add)
            }
            if (!song.isLocal) {
                add( deleteSongDialog )
            }
            add( exportCacheDialog )
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
        renameSong.Render()
        changeCover.Render()
        if (song.isLocal) editMetadata.Render() else changeAuthor.Render()
        if (!song.isLocal) {
            changeAlbumId.Render()
            changeArtistId.Render()
            deleteSongDialog.Render()
        }
        updateDialog.Render()
        exportCacheDialog.Render()

        exportCacheDialog.RenderProgress()
        //</editor-fold>

        buttons = updatedButtons

        SongMenuSurface(
            header = {
                MenuSheetDragHandle()

                SongItem(
                    song = song,
                    observeStoredMetadata = false,
                    animateMetadataChanges = true,
                    artistTextOverride = resolveTrackArtistName(
                        source?.mediaMetadata?.artist?.toString(),
                        song.artistsText,
                        artists,
                        stringResource(R.string.metadata_unknown_artist),
                    ),
                    backgroundColor = Color.Transparent,
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
                                contentDescription = menuLikeActionDescription(
                                    likeState, showDisliked.isEnabled,
                                    R.string.library_favorites_action_add, R.string.library_favorites_action_remove,
                                ),
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
                                    val item = source?.let { playerMenuMediaItem(it, song) } ?: song.asMediaItem
                                    val action = if (showDisliked.isEnabled) FavoriteAction.Rotate else FavoriteAction.Toggle
                                    YouTubeSync.launchFavoriteUpdate(context, listOf(item), action)
                                },
                                modifier = Modifier.padding( all = 4.dp ).size( 20.dp )
                            )

                            if( !song.isLocal )
                                IconButton(
                                    icon = R.drawable.share_social,
                                    contentDescription = stringResource(R.string.player_mini_button_share),
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

            },
        ) {
            val listenTogetherActions = rememberListenTogetherTrackMenuActions(song, menuState)
            if (menuStyle == MenuStyle.List)
                ListMenu(updatedButtons, listenTogetherActions)
            else
                GridMenu(updatedButtons, listenTogetherActions)
        }
    }

}





/**
 * Index of the first playback button (radio, play next, enqueue, favorite, add to
 * playlist) of a local song, behind the edit-metadata button at index 1. The FFmpeg
 * gate is gone (spec-ffmpeg-extension-module): the module is downloaded on first use,
 * so the button is always present and the menu layout is fixed.
 */
internal const val LOCAL_SONG_PLAYBACK_START = 4

/** Number of playback buttons of a local song's menu (radio, play next, enqueue, favorite, add to playlist). */
internal const val LOCAL_PLAYBACK_COUNT = 5

internal fun List<Button>.localTrackNavigationActions(): List<MenuIcon> =
    filterIsInstance<MenuIcon>().filter { it is GoToAlbum || it is GoToArtist }
