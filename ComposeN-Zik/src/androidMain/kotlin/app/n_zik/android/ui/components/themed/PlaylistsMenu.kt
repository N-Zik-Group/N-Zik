package app.n_zik.android.ui.components.themed

import app.n_zik.android.components.ui.LeadingIconBadge
import app.n_zik.android.components.menu.playlist.LocalPlaylistAddChoice
import app.n_zik.android.components.menu.playlist.PlaylistActionsRequest
import app.n_zik.android.components.menu.playlist.PlaylistPickerFeedback
import app.n_zik.android.components.menu.playlist.playlistPickerEmptyBody
import app.n_zik.android.components.menu.playlist.PlaylistPickerRead
import app.n_zik.android.components.menu.playlist.playlistPickerGridFeedback
import app.n_zik.android.components.menu.playlist.matchesPickerQuery
import app.n_zik.android.components.menu.playlist.menuItemCount
import app.n_zik.android.components.menu.playlist.rememberPlaylistPickerRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.core.database.PlaylistAddResult
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.database.isPlaylistBookmarked
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.core.navigation.LocalPlaylistPickerNavigation
import app.n_zik.android.core.navigation.navigateToPickedPlaylist
import app.n_zik.android.core.network.utils.isNetworkAvailableComposable
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.normalizedSongId
import timber.log.Timber

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.MONTHLY_PREFIX
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.ondevice.deviceLibraryEnabled
import app.n_zik.android.utils.showOnDevicePlaylistsKey
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.models.Playlist
import app.n_zik.android.components.search.SearchHeaderVisibility
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.ui.components.LocalMenuBack
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.addPlaylistItemsFromMenu
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.dialog.playlist.NewPlaylistDialog
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.ui.screens.home.GlassHomeChips
import app.n_zik.android.components.ui.screens.home.HomeLibrarySubfilter
import app.n_zik.android.components.ui.screens.home.HomeLibrarySubfilterRow
import app.n_zik.android.components.dialog.settings.libraryPlaylistChipOrder
import app.n_zik.android.utils.homePlaylistsOrderKey
import app.n_zik.android.utils.showPinnedPlaylistsKey
import app.n_zik.android.utils.showYtPlaylistsKey
import app.n_zik.android.components.ui.screens.home.YouTubeLibraryFilter
import app.n_zik.android.components.ui.screens.home.YouTubeLibraryFilterChip
import app.n_zik.android.components.ui.screens.home.matchesOnDevicePlaylist
import app.n_zik.android.components.ui.screens.home.matchesYouTubeLibraryFilter
import app.n_zik.android.components.ui.screens.home.rememberAvailableDevicePlaylistIds
import app.n_zik.android.core.utils.Toaster
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.MenuHeaderTitle
import app.n_zik.android.components.menu.menuHeaderBackAction
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.menuPageInteraction
import app.n_zik.android.components.menu.openDestination
import androidx.compose.material3.Icon

internal fun isPlaylistPickerTarget(playlist: Playlist, localDestinationsOnly: Boolean): Boolean =
    canEditPlaylistMembership(playlist) &&
        (!localDestinationsOnly || !isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist))

internal fun isPlaylistPickerVisible(playlist: Playlist): Boolean =
    canEditPlaylistMembership(playlist) || isPlaylistBookmarked(playlist)

internal fun isYouTubePlaylistPickerEntry(playlist: Playlist): Boolean =
    isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist)

internal fun isPinnedPlaylistPickerVisible(playlist: Playlist, online: Boolean): Boolean =
    playlist.name.startsWith(PINNED_PREFIX, ignoreCase = true) &&
        (online || !playlist.isYoutubePlaylist || !canEditPlaylistMembership(playlist))

@Composable
internal fun PlaylistPickerSearchRows(
    previews: List<PlaylistPreview>,
    matchingIds: Set<Long>,
    content: @Composable (PlaylistPreview) -> Unit,
) {
    previews.forEach { preview ->
        key(preview.playlist.id) {
            val matches = preview.playlist.id in matchingIds
            SearchHeaderVisibility(matches) {
                Box(Modifier.menuPageInteraction(matches)) { content(preview) }
            }
        }
    }
}

@Composable
private fun PlaylistPickerHeader(
    title: String,
    search: Search,
    onBack: (() -> Unit)?,
    onCreate: () -> Unit,
) {
    val textColor = colorPalette().text
    val backAction = onBack?.let { menuHeaderBackAction(it) }
    MenuHeaderTitle(
        title = title,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        titleMaxLines = 2,
        leading = {
            GlassSectionActions(
                listOfNotNull(
                    backAction,
                    GlassSectionAction(
                        icon = R.drawable.search_circle,
                        contentDescription = stringResource(R.string.search_title),
                        nativeColor = textColor,
                        glassColor = textColor,
                        onClick = search::onShortClick,
                    ),
                ),
            )
        },
        trailing = {
            GlassSectionActions(
                listOf(
                    GlassSectionAction(
                        icon = R.drawable.add_in_playlist,
                        contentDescription = stringResource(R.string.playlist_action_create),
                        nativeColor = textColor,
                        glassColor = textColor,
                        onClick = onCreate,
                    ),
                ),
            )
        },
    )
}

private class PlaylistPickerFilters {
    var group by mutableIntStateOf(R.string.common_option_all)
    var youtube by mutableStateOf(YouTubeLibraryFilter.All)
    val input = mutableStateOf(TextFieldValue(""))
    val searchVisible = mutableStateOf(false)
}

@Composable
private fun PlaylistPickerMenu(
    style: MenuStyle,
    playlistRead: PlaylistPickerRead,
    search: Search,
    sections: PlaylistPickerSections,
    filters: PlaylistPickerFilters,
    showDeviceTab: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    entry: @Composable (PlaylistPreview, Boolean, () -> Boolean) -> Unit,
) {
    val all = R.string.common_option_all
    val showPinned by rememberPreference(showPinnedPlaylistsKey, true)
    val showYouTube by rememberPreference(showYtPlaylistsKey, true)
    val chipOrder by rememberPreference(homePlaylistsOrderKey, "")
    val availableGroups = libraryPlaylistChipOrder(chipOrder).mapNotNull { id ->
        when (id) {
            "pinned_playlists" -> R.string.playlist_pinned_title.takeIf { showPinned }
            "yt_playlists" -> R.string.sync_youtube_playlists_title.takeIf { showYouTube }
            "on_device" -> R.string.library_on_device_title.takeIf { showDeviceTab }
            else -> null
        }
    }
    val activeGroup = filters.group.takeIf { it in availableGroups } ?: all
    val openOnDevice = activeGroup == R.string.library_on_device_title
    val query = search.filterQuery
    val searchAndFilters: @Composable ColumnScope.() -> Unit = {
        search.SearchBar(this)
        if (availableGroups.isNotEmpty()) {
            GlassHomeChips(
                chips = (listOf(all) + availableGroups).map {
                    val label = if (it == R.string.sync_youtube_playlists_title) R.string.library_youtube_playlists_label else it
                    it to stringResource(label)
                },
                currentValue = activeGroup,
                onValueUpdate = { filters.group = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 28.dp),
            )
            val subfilter = HomeLibrarySubfilter.YouTube.takeIf {
                activeGroup == R.string.sync_youtube_playlists_title
            }
            HomeLibrarySubfilterRow(subfilter) {
                YouTubeLibraryFilterChip(filters.youtube) { filters.youtube = it }
            }
        }
    }
    val displayed = if (activeGroup == all) sections.all
        else sections.groups().first { it.first == activeGroup }.second
    val matches = displayed.filter { preview ->
        val playlist = preview.playlist
        playlist.matchesPickerQuery(query) &&
            (activeGroup != R.string.sync_youtube_playlists_title ||
                matchesYouTubeLibraryFilter(playlist, filters.youtube))
    }
    val matchingIds = remember(matches) { matches.mapTo(HashSet()) { it.playlist.id } }
    val currentMatchingIds by rememberUpdatedState(matchingIds)
    val currentGroup by rememberUpdatedState(activeGroup)
    val matchingEntry: @Composable (PlaylistPreview) -> Unit = { preview ->
        entry(preview, openOnDevice) {
            activeGroup == currentGroup && preview.playlist.id in currentMatchingIds
        }
    }
    val empty = matches.isEmpty()
    val emptyBody = playlistPickerEmptyBody(playlistRead, query, empty)
    if (style == MenuStyle.List) {
        ListMenu.Menu(headerContent = header, bodyHeader = searchAndFilters, emptyBody = emptyBody) {
            PlaylistPickerFeedback(playlistRead, query, empty)
            PlaylistPickerSearchRows(displayed, matchingIds, matchingEntry)
        }
    } else {
        GridMenu.Menu(headerContent = header, bodyHeader = searchAndFilters, emptyBody = emptyBody) {
            playlistPickerGridFeedback(playlistRead, query, empty)
            items(matches, key = { it.playlist.id }) { matchingEntry(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistPickerEntry(
    style: MenuStyle,
    preview: PlaylistPreview,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    trailingContent: @Composable () -> Unit,
) {
    val text = preview.playlist.cleanName()
    val subtitle = preview.menuItemCount()
    if (style == MenuStyle.List) {
        ListMenu.Entry(
            text = text, subtitle = subtitle, icon = { icon() }, onClick = onClick,
            trailingContent = trailingContent,
        )
    } else {
        GridMenu.Entry(
            text = text, subtitle = subtitle, icon = { icon() }, onClick = onClick,
            iconHasOwnBackground = true,
            trailingContent = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) { trailingContent() }
            },
        )
    }
}

internal data class PlaylistPickerSections(
    val all: List<PlaylistPreview>,
    val pinned: List<PlaylistPreview>,
    val youtube: List<PlaylistPreview>,
    val local: List<PlaylistPreview>,
    val device: List<PlaylistPreview>,
) {
    fun groups(): List<Pair<Int, List<PlaylistPreview>>> = listOf(
        R.string.playlist_pinned_title to pinned,
        R.string.sync_youtube_playlists_title to youtube,
        R.string.library_on_device_title to device,
        R.string.library_playlists_title to local,
    )
}

internal fun playlistPickerSections(
    previews: List<PlaylistPreview>,
    online: Boolean,
    devicePlaylistIds: Set<Long> = emptySet(),
    availableDevicePlaylistIds: Set<Long> = emptySet(),
): PlaylistPickerSections {
    val visible = previews.filter {
        val playlist = it.playlist
        !RewindPlaylists.isRewind(playlist.cleanName()) && isPlaylistPickerVisible(playlist)
    }
    val ordinary = visible.filter {
        !it.playlist.name.startsWith(PINNED_PREFIX, ignoreCase = true) &&
            !it.playlist.name.startsWith(MONTHLY_PREFIX, ignoreCase = true) &&
            !isYouTubePlaylistPickerEntry(it.playlist)
    }
    val pinned = visible.filter { isPinnedPlaylistPickerVisible(it.playlist, online) }
    val youtube = visible.filter {
        isYouTubePlaylistPickerEntry(it.playlist) &&
            (online || !it.playlist.isYoutubePlaylist || !canEditPlaylistMembership(it.playlist))
    }
    val local = ordinary.filterNot { it.playlist.id in devicePlaylistIds }
    val allIds = (pinned + youtube + local).mapTo(HashSet()) { it.playlist.id }
    return PlaylistPickerSections(
        all = visible.filter { it.playlist.id in allIds || it.playlist.matchesOnDevicePlaylist(devicePlaylistIds) },
        pinned = pinned,
        youtube = youtube,
        local = local,
        device = visible.filter { it.playlist.matchesOnDevicePlaylist(availableDevicePlaylistIds) },
    )
}

internal fun warnPlaylistPickerLocked() {
    Toaster.w(R.string.playlist_add_destination_locked)
}

internal fun reportPlaylistAddResult(result: PlaylistAddResult, localDestinationsOnly: Boolean = false) {
    when (result) {
        PlaylistAddResult.Added -> Toaster.done()
        PlaylistAddResult.AlreadyPresent -> Toaster.i(R.string.playlist_contains_song)
        PlaylistAddResult.Empty -> Toaster.w(R.string.player_no_song_found)
        PlaylistAddResult.Unavailable -> if (localDestinationsOnly) {
            Toaster.w(R.string.playlist_add_destination_unavailable)
        } else {
            warnPlaylistPickerLocked()
        }
    }
}

@Composable
private fun PlaylistPickerOrigin(playlist: Playlist, onDevice: Boolean) {
    val browseId = playlist.browseId.orEmpty()
    val (icon, tint) = when {
        isYouTubePlaylistPickerEntry(playlist) -> R.drawable.ytmusic to Color.Red.copy(0.75f).compositeOver(Color.White)
        onDevice -> R.drawable.folder to colorPalette().textSecondary
        browseId.startsWith("SPOTIFY_IMPORT") -> R.drawable.spotify to Color.Unspecified
        browseId.startsWith("RIPLAY_IMPORT") -> R.drawable.riplay to Color.Unspecified
        else -> R.drawable.ic_launcher to Color.Unspecified
    }
    Icon(
        painterResource(icon),
        contentDescription = stringResource(if (onDevice) R.string.library_on_device_title else R.string.accessibility_origin_indicator),
        tint = tint,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun PlaylistPickerLockIcon() {
    Icon(
        painter = painterResource(R.drawable.locked),
        contentDescription = stringResource(R.string.playlist_add_destination_locked),
        tint = colorPalette().textDisabled,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
internal fun rememberSongPlaylistIds(songId: String): List<Long> = key(songId) {
    val playlistIds by remember {
        Database.songPlaylistMapTable.mappedTo(songId)
    }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)
    playlistIds
}

@Composable
private fun SongInPlaylistIcon() {
    Icon(
        painter = painterResource(R.drawable.playlist),
        contentDescription = stringResource(R.string.playlist_contains_song),
        tint = colorPalette().accent,
        modifier = Modifier.size(18.dp)
    )
}

class PlaylistsMenu private constructor(
    private val navController: NavController?,
    private val songId: String?,
    private val captureRequest: () -> AddRequest?,
    private val onFailure: (Throwable, PlaylistPreview) -> Unit,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>,
    private val onOpenPlaylist: () -> Unit = {},
    private val onMenuDestinationOpened: (() -> Unit)? = null,
    private val openAddChoice: ((PlaylistActionsRequest) -> Unit)? = null,
    private val canImportFiles: Boolean = false,
): MenuIcon, Descriptive, Menu {

    private class AddRequest(
        val load: (PlaylistPreview) -> suspend () -> List<MediaItem>?,
        val onComplete: suspend (PlaylistPreview) -> Unit,
        val localDestinationsOnly: Boolean,
    ) {
        val filters = PlaylistPickerFilters()
        var isAdding = false
    }

    companion object {
        @JvmStatic
        @Composable
        fun init(
            navController: NavController?,
            mediaItems: (PlaylistPreview) -> List<MediaItem>,
            onFailure: (Throwable, PlaylistPreview) -> Unit,
            finalAction: (PlaylistPreview) -> Unit,
            songId: String? = null,
            localDestinationsOnly: Boolean = false,
            onOpenPlaylist: () -> Unit = {},
        ) = PlaylistsMenu(
            navController,
            songId,
            { AddRequest({ preview -> { mediaItems(preview) } }, { finalAction(it) }, localDestinationsOnly) },
            onFailure,
            LocalMenuState.current,
            rememberPreference( menuStyleKey, MenuStyle.List ),
            onOpenPlaylist = onOpenPlaylist,
            openAddChoice = LocalPlaylistAddChoice.current,
            canImportFiles = false,
        )

        @Composable
        internal fun forSong(
            mediaItem: MediaItem,
            navController: NavController? = null,
            onOpenPlaylist: () -> Unit = {},
        ): PlaylistsMenu {
            val menuState = LocalMenuState.current
            return init(
                navController = navController,
                mediaItems = { listOf(mediaItem) },
                onFailure = { error, _ ->
                    Timber.tag("PlaylistsMenu").e(error, "Could not add the current track to its playlist")
                },
                finalAction = { menuState.hide() },
                songId = mediaItem.normalizedSongId,
                localDestinationsOnly = mediaItem.isLocal,
                onOpenPlaylist = onOpenPlaylist,
            )
        }

        @Composable
        internal fun deferred(
            navController: NavController,
            captureMediaItems: () -> (suspend () -> List<MediaItem>?)?,
            onFailure: (Throwable, PlaylistPreview) -> Unit,
            itemSelector: ItemSelector<*>,
            localDestinationsOnly: Boolean = false,
            onMenuDestinationOpened: (() -> Unit)? = null,
            allowImportChoice: Boolean = true,
        ): PlaylistsMenu = rememberDeferred(
            navController = navController,
            onFailure = onFailure,
            itemSelector = itemSelector,
            onMenuDestinationOpened = onMenuDestinationOpened,
            canImportFiles = allowImportChoice && !localDestinationsOnly && !itemSelector.isActive,
            captureTracks = {
                captureMediaItems()?.let { load -> DeferredTracks(load, localDestinationsOnly) }
            },
        )

        @Composable
        internal fun forSongs(
            navController: NavController,
            songs: () -> List<Song>,
            onFailure: (Throwable, PlaylistPreview) -> Unit,
            itemSelector: ItemSelector<*>,
        ): PlaylistsMenu = rememberDeferred(
            navController = navController,
            onFailure = onFailure,
            itemSelector = itemSelector,
            captureTracks = {
                val snapshot = songs().toList()
                snapshot.takeIf { it.isNotEmpty() }?.let {
                    DeferredTracks({ snapshot.map(Song::asMediaItem) }, snapshot.any(Song::isLocal))
                }
            },
        )

        @Composable
        private fun rememberDeferred(
            navController: NavController,
            onFailure: (Throwable, PlaylistPreview) -> Unit,
            itemSelector: ItemSelector<*>,
            captureTracks: () -> DeferredTracks?,
            canImportFiles: Boolean = false,
            onMenuDestinationOpened: (() -> Unit)? = null,
        ) = PlaylistsMenu(
            navController,
            null,
            {
                captureTracks()?.let { tracks ->
                    val completeSelection = itemSelector.captureSelectionCompletion()
                    AddRequest(
                        load = { tracks.load },
                        onComplete = { withContext(NzikDispatchers.UI) { completeSelection() } },
                        localDestinationsOnly = tracks.localDestinationsOnly,
                    )
                }
            },
            onFailure,
            LocalMenuState.current,
            rememberPreference(menuStyleKey, MenuStyle.List),
            onMenuDestinationOpened = onMenuDestinationOpened,
            openAddChoice = LocalPlaylistAddChoice.current,
            canImportFiles = canImportFiles,
        )

        private class DeferredTracks(val load: suspend () -> List<MediaItem>?, val localDestinationsOnly: Boolean)
    }

    override val iconId: Int = R.drawable.playlist
    override val messageId: Int = R.string.playlist_actions_title
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var menuStyle: MenuStyle by styleState

    private suspend fun addToPlaylist(
        preview: PlaylistPreview,
        request: AddRequest,
        menuKey: Int,
        createdAfterConfirmation: Boolean,
    ) {
        try {
            val items = request.load(preview)() ?: return
            if (items.isEmpty() && createdAfterConfirmation) return
            val localDestinationsOnly = request.localDestinationsOnly
            val result = addPlaylistItemsFromMenu(preview.playlist, items, localDestinationsOnly) ?: return
            withContext(NzikDispatchers.UI) {
                reportPlaylistAddResult(result, localDestinationsOnly)
                if (result == PlaylistAddResult.Added && menuState.isCurrentMenu(menuKey)) request.onComplete(preview)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            withContext(NzikDispatchers.UI) {
                onFailure(error, preview)
                Toaster.e(R.string.common_load_failed)
            }
        }
    }

    @Composable
    fun SettingIcon(@DrawableRes icon: Int) = LeadingIconBadge(icon, contentDescription = null)

    @Composable
    private fun PlaylistCard(
        playlistPreview: PlaylistPreview,
        containsSong: Boolean,
        onDevice: Boolean,
        openOnDevice: Boolean,
        localDestinationsOnly: Boolean,
        canInteract: () -> Boolean,
        onAdd: () -> Unit,
    ) {
        val playlist = playlistPreview.playlist
        val locked = !isPlaylistPickerTarget(playlist, localDestinationsOnly)

        PlaylistPickerEntry(
            style = menuStyle,
            preview = playlistPreview,
            icon = { SettingIcon(R.drawable.add_in_playlist) },
            onClick = {
                when {
                    !canInteract() -> Unit
                    locked -> warnPlaylistPickerLocked()
                    containsSong -> Toaster.i(R.string.playlist_contains_song)
                    else -> onAdd()
                }
            },
            trailingContent = {
                if (locked) PlaylistPickerLockIcon()
                if (containsSong) SongInPlaylistIcon()
                PlaylistPickerOrigin(playlist, onDevice)
                PlaylistPickerOpenButton(
                    playlistId = playlist.id,
                    onDevice = openOnDevice,
                    onDismiss = {
                        onOpenPlaylist()
                        menuState.hide()
                    },
                    openDestination = { navigate ->
                        canInteract() && menuState.openDestination(navigate = navigate)
                    },
                    openPlaylist = navController?.let { controller ->
                        { id: Long -> navigateToPickedPlaylist(controller, id, openOnDevice) }
                    },
                )
            }
        )
    }

    override fun onShortClick() {
        openMenu()
    }

    override fun openMenu() {
        val request = captureRequest()
        val showPicker = request?.let { captured -> { onReturn: (() -> Unit)? -> openPicker(captured, onReturn) } }
        if (openAddChoice != null) {
            val parentMenu = menuState.presentationState.second.takeIf { menuState.isDisplayed }
            openAddChoice(PlaylistActionsRequest(showPicker, canImportFiles, parentMenu))
        } else showPicker?.invoke(null)
    }

    internal fun openPicker() = openPicker(onReturn = null)

    internal fun openPicker(onReturn: (() -> Unit)?) {
        captureRequest()?.let { openPicker(it, onReturn) }
    }

    private fun openPicker(request: AddRequest, onReturn: (() -> Unit)? = null) {
        menuState.display(onMenuDestinationOpened, onReturn) { PlaylistPicker(request) }
    }

    @Composable
    override fun ListMenu() { /* Does nothing */ }

    @Composable
    override fun GridMenu() { /* Does nothing */ }

    @Composable
    override fun MenuComponent() {
        remember { captureRequest() }?.let { PlaylistPicker(it) }
    }

    @Composable
    private fun PlaylistPicker(request: AddRequest) {
        val context = LocalContext.current
        val online by context.isNetworkAvailableComposable
        val showOnDevice by rememberPreference(showOnDevicePlaylistsKey, true)
        val showDeviceTab = deviceLibraryEnabled() && showOnDevice
        val availableDeviceIds = if (showDeviceTab && request.filters.group == R.string.library_on_device_title) {
            rememberAvailableDevicePlaylistIds()
        } else emptySet()
        val playlistRead = rememberPlaylistPickerRead(
            includeDeviceSort = showDeviceTab,
            deviceScanPending = availableDeviceIds == null,
        )
        val previews = playlistRead.result?.getOrNull()
        val base = playlistPickerSections(
            playlistRead.libraryPreviews, online, playlistRead.devicePlaylistIds, availableDeviceIds.orEmpty(),
        )
        fun List<PlaylistPreview>.ordered(rank: Map<Long, Int>?): List<PlaylistPreview> =
            if (rank.isNullOrEmpty()) this else sortedBy { rank[it.playlist.id] ?: Int.MAX_VALUE }
        val sections = base.copy(
            pinned = base.pinned.ordered(previews?.pinnedRank),
            youtube = base.youtube.ordered(previews?.youtubeRank),
            device = base.device.ordered(previews?.deviceRank),
        )
        val songPlaylistIds = if (songId != null) rememberSongPlaylistIds(songId) else emptyList()
        val search = Search.retained(request.filters.input, request.filters.searchVisible)
        val menuKey = menuState.transitionKey
        fun add(preview: PlaylistPreview, createdAfterConfirmation: Boolean = false) {
            if (!createdAfterConfirmation && (request.isAdding || !menuState.isCurrentMenu(menuKey))) return
            val owner = ProfileWorkOwner.capture(context) ?: return
            if (!createdAfterConfirmation) request.isAdding = true
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                try {
                    withContext(NzikDispatchers.DATA) {
                        if (ProfileWorkOwner.isBound(context, owner)) {
                            addToPlaylist(preview, request, menuKey, createdAfterConfirmation)
                        }
                    }
                } finally {
                    if (!createdAfterConfirmation) request.isAdding = false
                }
            }
        }
        val newPlaylistButton = NewPlaylistDialog(localOnly = request.localDestinationsOnly) { playlist ->
            add(PlaylistPreview(playlist, 0), createdAfterConfirmation = true)
        }
        newPlaylistButton.Render()

        PlaylistPickerMenu(
            style = menuStyle,
            playlistRead = playlistRead,
            search = search,
            sections = sections,
            filters = request.filters,
            showDeviceTab = showDeviceTab,
            header = {
                PlaylistPickerHeader(
                    title = stringResource(R.string.playlist_action_add),
                    search = search,
                    onBack = LocalMenuBack.current,
                    onCreate = newPlaylistButton::onShortClick,
                )
            },
            entry = { preview, openOnDevice, isMatching ->
                PlaylistCard(
                    preview, preview.playlist.id in songPlaylistIds,
                    onDevice = preview.playlist.matchesOnDevicePlaylist(playlistRead.devicePlaylistIds),
                    openOnDevice = openOnDevice,
                    localDestinationsOnly = request.localDestinationsOnly,
                    canInteract = { menuState.isCurrentMenu(menuKey) && isMatching() },
                ) {
                    add(preview)
                }
            },
        )
    }
}

@Composable
internal fun PlaylistPickerOpenButton(
    playlistId: Long,
    onDismiss: () -> Unit,
    onDevice: Boolean = false,
    openDestination: (() -> Unit) -> Boolean = { navigate ->
        navigate()
        true
    },
    openPlaylist: ((Long) -> Unit)? = null,
) {
    val host = LocalPlaylistPickerNavigation.current
    val navigate = openPlaylist ?: host?.let { { id: Long -> it(id, onDevice) } } ?: return
    IconButton(
        icon = R.drawable.open,
        color = colorPalette().accent,
        contentDescription = stringResource(R.string.playlist_action_open),
        onClick = {
            if (openDestination { navigate(playlistId) }) onDismiss()
        },
        modifier = Modifier.size(24.dp),
    )
}
