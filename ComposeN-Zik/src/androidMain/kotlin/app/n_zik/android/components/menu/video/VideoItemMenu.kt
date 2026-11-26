package app.n_zik.android.components.menu.video

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.PlayerTimelineType
import app.n_zik.android.models.Info
import app.n_zik.android.models.Song
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
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getDownloadStateMedia
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.playerTimelineTypeKey
import app.n_zik.android.utils.rememberEncryptedPreference
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.splitArtistNames
import app.n_zik.android.core.sync.YouTubeSync
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.BuildConfig
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.dialog.album.ChangeAlbumBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.n_zik.android.components.dialog.song.ChangeAuthorDialog
import app.n_zik.android.components.dialog.song.ChangeCoverDialog
import app.n_zik.android.components.dialog.song.RenameSongDialog
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.rememberListenOnMenuAction
import app.n_zik.android.components.menu.menuLikeActionDescription
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.menuSheetSectionBackground
import app.n_zik.android.components.menu.song.ObserveSongMenuSnapshot
import app.n_zik.android.components.menu.song.SongMenuSnapshot
import app.n_zik.android.components.song.GoToAlbum
import app.n_zik.android.components.menu.player.playerMenuMediaItem
import app.n_zik.android.utils.resolveTrackArtistName
import app.n_zik.android.components.menu.song.rememberTrackMenuArtists
import app.n_zik.android.components.song.GoToArtist
import app.n_zik.android.components.song.chooseTrackArtist
import app.n_zik.android.components.song.rememberTrackAlbumLink
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.ui.screens.listentogether.ListenTogetherMenu
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import app.n_zik.android.extensions.lastfm.LastFmActions
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.TrackInfo
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers

@UnstableApi
@ExperimentalFoundationApi
class VideoItemMenu private constructor(
    private val navController: NavController,
    private val source: MediaItem,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
): Menu {

    private val snapshot = SongMenuSnapshot(source.asSong, getActiveProfile(appContext()))
    private val song: Song get() = snapshot.song

    companion object {
        @Composable
        operator fun invoke(navController: NavController, mediaItem: MediaItem): VideoItemMenu =
            VideoItemMenu(
                navController = navController,
                source = mediaItem,
                menuState = LocalMenuState.current,
                styleState = rememberPreference( menuStyleKey, MenuStyle.List )
            )
    }

    private var buttons: List<Button> = emptyList()
    var refreshBtn: Button? = null
    private var showLastFmSection = false
    private var showListenTogetherSection = false
    private var offlineMode = false
    private var listenTogetherDialogBtn: Button? = null
    private var listenTogetherBtn: Button? = null
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu(buttons)

    @Composable
    private fun ListMenu(buttons: List<Button>) = ListMenu.Menu(title = null, showDragHandle = false) {
        val lastFmSectionSize = if (showLastFmSection) 2 else 0
        // Section: Info
        SectionTitle(stringResource(R.string.common_information_title))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Playback
        SectionTitle(stringResource(R.string.common_playback_section_title))
        if (!offlineMode) buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(6)?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Listen Together (room menu, then suggest-to-host for guests in a room)
        if (!offlineMode) {
            SectionTitle(stringResource(R.string.listen_together_title))
            listenTogetherDialogBtn?.let { if (it is MenuIcon) it.ListMenuItem() }
            listenTogetherBtn?.let { if (it is MenuIcon) it.ListMenuItem() }
        }

        // Section: Management
        SectionTitle(stringResource(R.string.library_management_section_title))
        buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.filterIsInstance<ChangeAlbumBrowseIdDialog>().firstOrNull()?.let { it.ListMenuItem() }
        buttons.filterIsInstance<ChangeArtistBrowseIdDialog>().firstOrNull()?.let { it.ListMenuItem() }
        buttons.getOrNull(7)?.let { if (it is MenuIcon) it.ListMenuItem() }
        refreshBtn?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Navigation
        SectionTitle(stringResource(R.string.common_navigation_section_title))
        for (i in 10 until buttons.size - lastFmSectionSize) {
            val btn = buttons.getOrNull(i)
            if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog) continue
            btn?.let { if (it is MenuIcon) it.ListMenuItem() }
        }

        if (showLastFmSection && !offlineMode) {
            // Section: Last.fm
            SectionTitle(stringResource(R.string.settings_lastfm_title))
            buttons.getOrNull(buttons.size - 2)?.let { if (it is MenuIcon) it.ListMenuItem() }
            buttons.getOrNull(buttons.size - 1)?.let { if (it is MenuIcon) it.ListMenuItem() }
        }
    }

    @Composable
    override fun GridMenu() = GridMenu(buttons)

    @Composable
    private fun GridMenu(buttons: List<Button>) = GridMenu.Menu(title = null, showDragHandle = false) {
        val lastFmSectionSize = if (showLastFmSection) 2 else 0
        // Section: Info
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.common_information_title))
        }
        buttons.getOrNull(0)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        // Section: Playback
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.common_playback_section_title))
        }
        if (!offlineMode) buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(6)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        // Section: Listen Together (room menu, then suggest-to-host for guests in a room)
        if (!offlineMode) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.listen_together_title))
            }
            listenTogetherDialogBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            listenTogetherBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }

        // Section: Management
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.library_management_section_title))
        }
        buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.filterIsInstance<ChangeAlbumBrowseIdDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
        buttons.filterIsInstance<ChangeArtistBrowseIdDialog>().firstOrNull()?.let { item { it.GridMenuItem() } }
        buttons.getOrNull(7)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        refreshBtn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        // Section: Navigation
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.common_navigation_section_title))
        }
        for (i in 10 until buttons.size - lastFmSectionSize) {
            val btn = buttons.getOrNull(i)
            if (btn is ChangeAlbumBrowseIdDialog || btn is ChangeArtistBrowseIdDialog) continue
            btn?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }

        if (showLastFmSection && !offlineMode) {
            // Section: Last.fm
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.settings_lastfm_title))
            }
            buttons.getOrNull(buttons.size - 2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
            buttons.getOrNull(buttons.size - 1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }
    }

    @Composable
    override fun MenuComponent() {
        ObserveSongMenuSnapshot(snapshot, onProfileChanged = menuState::hide)
        val context = LocalContext.current
        offlineMode = OfflineMode.enabled(context)
        val showDisliked by rememberPreference(excludeDislikedSongsKey, DislikeMode.Enabled)
        val binder = LocalPlayerServiceBinder.current
        val coroutineScope = rememberCoroutineScope()

        val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.Wavy)
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

        //region Buttons

        val renameVideo = RenameSongDialog{ song }
        val changeAuthor = ChangeAuthorDialog{ song }
        val changeCover = ChangeCoverDialog{ song }
        fun mediaItemForPlayback() = playerMenuMediaItem(source, song)
        val startRadio = Radio.fromMediaItem { mediaItemForPlayback() }
        val playNext = PlayNext {
            val queueEdit = binder?.captureMenuQueueEdit() ?: return@PlayNext
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                val mediaItem = withContext(NzikDispatchers.DATA) { mediaItemForPlayback() }
                queueEdit.queue(listOf(mediaItem), appContext(), playNext = true)
            }
        }
        val enqueue = Enqueue {
            val queueEdit = binder?.captureMenuQueueEdit() ?: return@Enqueue
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                val mediaItem = withContext(NzikDispatchers.DATA) { mediaItemForPlayback() }
                queueEdit.queue(listOf(mediaItem), appContext(), playNext = false)
            }
        }
        val addToPlaylist = PlaylistsMenu.init(
            navController = navController,
            songId = song.id,
            mediaItems = { _ -> listOf(mediaItemForPlayback()) },
            onFailure = { throwable, preview ->
                Timber.tag("VideoItemMenu").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
            },
            finalAction = {}
        )

        val albumLink by rememberTrackAlbumLink(song)

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

        val listenOnButton = if (offlineMode) null else rememberListenOnMenuAction(song.id, menuState)
        // Reactively collect artists from DB for per-artist "More of" buttons
        val artistsData by remember(song.id) {
            Database.artistTable.findBySongId(song.id)
        }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

        val artists = rememberTrackMenuArtists(source, song, artistsData)
        val goToArtistFallback = remember(song) {
            GoToArtist( navController, song, menuState )
        }

        val metadataTrack = song.takeUnless { it.isLocal }
        val changeAlbumId = ChangeAlbumBrowseIdDialog(
            menuState = menuState,
            targetTrack = metadataTrack,
            initialQuery = null,
        ) { albumLink?.album }
        val changeArtistId = ChangeArtistBrowseIdDialog(
            menuState = menuState,
            targetTrack = metadataTrack,
        ) { chooseTrackArtist(artistsData, null) }

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
//
        // video regardless of room state or role — it is the menu entry point to a room.
        listenTogetherDialogBtn = remember {
            object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.people
                override val messageId: Int = R.string.listen_together_title
                @get:Composable
                override val menuIconTitle: String get() = stringResource(R.string.listen_together_title)

                override fun onShortClick() {
                    menuState.display {
                        ListenTogetherMenu()
                    }
                }
                override fun onLongClick() {}
            }
        }

        val updatedButtons = buildList<Button> {
            add( infoButton )
            add( renameVideo )
            add( changeAuthor )
            add( changeCover )
            add( startRadio )
            add( playNext )
            add( enqueue )
            add( addToPlaylist )
            add( changeAlbumId )
            add( changeArtistId )
            add(GoToAlbum(
                navController, song, menuState,
            ))
            
            if (artists.isEmpty()) {
                val artistNames = song.artistsText
                    .splitArtistNames()

                if (artistNames.size <= 1) {
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

            listenOnButton?.let(::add)

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
        //endregion

        //region Dialog renders
        renameVideo.Render()
        changeAuthor.Render()
        changeCover.Render()
        changeAlbumId.Render()
        changeArtistId.Render()
        //endregion

        buttons = updatedButtons

        MenuSheetSurface {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.menuSheetSectionBackground(surfaceColor(colorPalette().background1))
            ) {
                MenuSheetDragHandle()

                SongItem(
                    song = song,
                    observeStoredMetadata = false,
                    artistTextOverride = resolveTrackArtistName(
                        source.mediaMetadata.artist?.toString(),
                        song.artistsText,
                        artists,
                        stringResource(R.string.metadata_unknown_artist),
                    ),
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
                                contentDescription = menuLikeActionDescription(
                                    likeState,
                                    showDisliked.isEnabled,
                                    R.string.library_favorites_action_add,
                                    R.string.library_favorites_action_remove,
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
                                    coroutineScope.launch( NzikDispatchers.DATA ) {
                                        YouTubeSync.rotateSongLikeState( context, song.asMediaItem )
                                    }
                                },
                                modifier = Modifier.padding( all = 4.dp ).size( 20.dp )
                            )

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

                GlassHorizontalSeparator( Modifier.height(1.dp) )
            }

            if( menuStyle == MenuStyle.List )
                ListMenu(updatedButtons)
            else
                GridMenu(updatedButtons)
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
