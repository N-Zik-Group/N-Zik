package app.n_zik.android.ui.components.themed

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
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.database.PlaylistAddResult
import app.n_zik.android.core.database.isPlaylistBookmarked
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.core.navigation.LocalPlaylistPickerNavigation
import app.n_zik.android.core.navigation.navigateToPickedPlaylist
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.addPlaylistItemsFromMenu
import app.n_zik.android.utils.isNetworkConnected
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
import app.n_zik.android.components.ui.screens.home.YouTubeLibraryFilter
import app.n_zik.android.components.ui.screens.home.YouTubeLibraryFilterChip
import app.n_zik.android.components.ui.screens.home.matchesYouTubeLibraryFilter
import app.n_zik.android.core.utils.Toaster
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.openDestination
import androidx.compose.material3.Icon

internal fun isPlaylistPickerTarget(playlist: Playlist, localDestinationsOnly: Boolean): Boolean =
    canEditPlaylistMembership(playlist) &&
        (!localDestinationsOnly || !isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist))

internal fun isPlaylistPickerVisible(playlist: Playlist): Boolean =
    canEditPlaylistMembership(playlist) || isPlaylistBookmarked(playlist)

internal fun isYouTubePlaylistPickerEntry(playlist: Playlist): Boolean =
    playlist.isYoutubePlaylist || playlistLinkKind(playlist.browseId) == PlaylistLinkKind.YouTubePodcast

internal fun isPinnedPlaylistPickerVisible(playlist: Playlist, online: Boolean): Boolean =
    playlist.name.startsWith(PINNED_PREFIX, ignoreCase = true) &&
        (online || !isYouTubePlaylistPickerEntry(playlist) || !canEditPlaylistMembership(playlist))

@Composable
internal fun PlaylistPickerSearchRows(
    previews: List<PlaylistPreview>,
    matches: List<PlaylistPreview>,
    content: @Composable (PlaylistPreview) -> Unit,
) {
    val visibleIds = remember(matches) { matches.mapTo(HashSet()) { it.playlist.id } }
    previews.forEach { preview ->
        key(preview.playlist.id) {
            SearchHeaderVisibility(preview.playlist.id in visibleIds) { content(preview) }
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(84.dp), contentAlignment = Alignment.CenterStart) {
            GlassSectionActions(
                buildList {
                    if (onBack != null) add(
                        GlassSectionAction(
                            icon = R.drawable.chevron_back,
                            contentDescription = stringResource(R.string.common_action_back),
                            nativeColor = textColor,
                            glassColor = textColor,
                            onClick = onBack,
                        )
                    )
                    add(
                        GlassSectionAction(
                            icon = R.drawable.search_circle,
                            contentDescription = stringResource(R.string.search_title),
                            nativeColor = textColor,
                            glassColor = textColor,
                            onClick = search::onShortClick,
                        )
                    )
                },
            )
        }
        BasicText(
            title,
            style = typography().m.copy(color = textColor, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box(Modifier.width(84.dp), contentAlignment = Alignment.CenterEnd) {
            GlassSectionActions(
                listOf(
                    GlassSectionAction(
                        icon = R.drawable.add_in_playlist,
                        contentDescription = stringResource(R.string.playlist_action_create),
                        nativeColor = textColor,
                        glassColor = textColor,
                        onClick = onCreate,
                    )
                )
            )
        }
    }
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
    groups: List<Pair<Int, List<PlaylistPreview>>>,
    filters: PlaylistPickerFilters,
    showDeviceTab: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    entry: @Composable (PlaylistPreview) -> Unit,
) {
    val all = R.string.common_option_all
    val availableGroups = groups.filter { (label, previews) ->
        previews.isNotEmpty() && (label != R.string.library_on_device_title || showDeviceTab)
    }.map { it.first }
    val activeGroup = filters.group.takeIf { it in availableGroups } ?: all
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
    val displayed = if (activeGroup == all) {
        val visibleIds = groups.flatMap { it.second }.mapTo(HashSet()) { it.playlist.id }
        playlistRead.libraryPreviews.filter { it.playlist.id in visibleIds }
    } else groups.first { it.first == activeGroup }.second
    val matches = displayed.filter { preview ->
        val playlist = preview.playlist
        playlist.matchesPickerQuery(query) &&
            (activeGroup != R.string.sync_youtube_playlists_title ||
                matchesYouTubeLibraryFilter(playlist, filters.youtube))
    }
    val empty = matches.isEmpty()
    val emptyBody = playlistPickerEmptyBody(playlistRead, query, empty)
    if (style == MenuStyle.List) {
        ListMenu.Menu(headerContent = header, bodyHeader = searchAndFilters, emptyBody = emptyBody) {
            PlaylistPickerFeedback(playlistRead, query, empty)
            PlaylistPickerSearchRows(displayed, matches, entry)
        }
    } else {
        GridMenu.Menu(headerContent = header, bodyHeader = searchAndFilters, emptyBody = emptyBody) {
            playlistPickerGridFeedback(playlistRead, query, empty)
            items(matches, key = { it.playlist.id }) { entry(it) }
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
    return PlaylistPickerSections(
        pinned = visible.filter { isPinnedPlaylistPickerVisible(it.playlist, online) },
        youtube = visible.filter {
            isYouTubePlaylistPickerEntry(it.playlist) &&
                (online || !canEditPlaylistMembership(it.playlist))
        },
        local = ordinary.filterNot { it.playlist.id in devicePlaylistIds },
        device = visible.filter { it.playlist.id in devicePlaylistIds },
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
    private val localDestinationsOnly: Boolean = false,
    private val onOpenPlaylist: () -> Unit = {},
    private val onMenuDestinationOpened: (() -> Unit)? = null,
): MenuIcon, Descriptive, Menu {

    private class AddRequest(
        val load: (PlaylistPreview) -> suspend () -> List<MediaItem>?,
        val onComplete: suspend (PlaylistPreview) -> Unit,
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
            { AddRequest(load = { preview -> { mediaItems(preview) } }, onComplete = { finalAction(it) }) },
            onFailure,
            LocalMenuState.current,
            rememberPreference( menuStyleKey, MenuStyle.List ),
            localDestinationsOnly = localDestinationsOnly,
            onOpenPlaylist = onOpenPlaylist,
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
        ) = PlaylistsMenu(
            navController,
            null,
            {
                captureMediaItems()?.let { load ->
                    val completeSelection = itemSelector.captureSelectionCompletion()
                    AddRequest(load = { load }, onComplete = { withContext(NzikDispatchers.UI) { completeSelection() } })
                }
            },
            onFailure,
            LocalMenuState.current,
            rememberPreference(menuStyleKey, MenuStyle.List),
            localDestinationsOnly = localDestinationsOnly,
            onMenuDestinationOpened = onMenuDestinationOpened,
        )
    }

    override val iconId: Int = R.drawable.add_in_playlist
    override val messageId: Int = R.string.playlist_action_add
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var menuStyle: MenuStyle by styleState

    private suspend fun addToPlaylist(preview: PlaylistPreview, request: AddRequest, menuKey: Int) {
        try {
            val items = request.load(preview)() ?: return
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
    fun SettingIcon(@DrawableRes icon: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .iconBadgeBackground(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                tint = colorPalette().accent,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    @Composable
    private fun PlaylistCard(
        playlistPreview: PlaylistPreview,
        containsSong: Boolean,
        onDevice: Boolean,
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
                    onDevice = onDevice,
                    onDismiss = {
                        onOpenPlaylist()
                        menuState.hide()
                    },
                    openDestination = { navigate -> menuState.openDestination(navigate = navigate) },
                    openPlaylist = navController?.let { controller ->
                        { id: Long -> navigateToPickedPlaylist(controller, id, onDevice) }
                    },
                )
            }
        )
    }

    override fun onShortClick() {
        openMenu()
    }

    override fun openMenu() {
        val request = captureRequest() ?: return
        menuState.display(onMenuDestinationOpened) { PlaylistPicker(request) }
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
        val playlistRead = rememberPlaylistPickerRead()
        val online = isNetworkConnected(LocalContext.current)
        val previews = playlistRead.result?.getOrNull()
        val showOnDevice by rememberPreference(showOnDevicePlaylistsKey, true)
        val showDeviceTab = deviceLibraryEnabled() && showOnDevice
        val base = playlistPickerSections(playlistRead.libraryPreviews, online, playlistRead.devicePlaylistIds)
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
        fun add(preview: PlaylistPreview) {
            if (request.isAdding || !menuState.isCurrentMenu(menuKey)) return
            request.isAdding = true
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                try {
                    withContext(NzikDispatchers.DATA) { addToPlaylist(preview, request, menuKey) }
                } finally {
                    request.isAdding = false
                }
            }
        }
        val newPlaylistButton = NewPlaylistDialog(localOnly = localDestinationsOnly) { playlist ->
            add(PlaylistPreview(playlist, 0))
        }
        newPlaylistButton.Render()

        PlaylistPickerMenu(
            style = menuStyle,
            playlistRead = playlistRead,
            search = search,
            groups = sections.groups(),
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
            entry = { preview ->
                PlaylistCard(
                    preview, preview.playlist.id in songPlaylistIds,
                    onDevice = preview.playlist.id in playlistRead.devicePlaylistIds,
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
