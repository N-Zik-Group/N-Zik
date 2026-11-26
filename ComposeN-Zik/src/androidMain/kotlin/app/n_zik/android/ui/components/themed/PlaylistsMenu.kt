package app.n_zik.android.ui.components.themed

import app.n_zik.android.components.menu.playlist.PlaylistPickerFeedback
import app.n_zik.android.components.menu.playlist.PlaylistPickerRead
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
import app.n_zik.android.core.navigation.navigateToPickedPlaylist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.MONTHLY_PREFIX
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.PlaylistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Playlist
import app.n_zik.android.components.search.SearchHeaderVisibility
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
import app.n_zik.android.core.utils.Toaster
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver

import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.isCurrentMenu
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
internal fun PlaylistPickerMenu(
    style: MenuStyle,
    playlistRead: PlaylistPickerRead,
    query: String,
    groups: List<Pair<Int, List<PlaylistPreview>>>,
    header: @Composable ColumnScope.() -> Unit,
    heading: @Composable (Int) -> Unit,
    entry: @Composable (Int, PlaylistPreview) -> Unit,
) {
    val matchingGroups = groups.map { (label, previews) ->
        label to previews.filter { it.playlist.matchesPickerQuery(query) }
    }
    val empty = matchingGroups.all { (_, matches) -> matches.isEmpty() }
    if (style == MenuStyle.List) {
        ListMenu.Menu {
            header()
            PlaylistPickerFeedback(playlistRead, query, empty)
            groups.zip(matchingGroups).forEach { (group, matchingGroup) ->
                val (label, previews) = group
                val (_, matches) = matchingGroup
                if (previews.isNotEmpty()) {
                    SearchHeaderVisibility(matches.isNotEmpty()) { heading(label) }
                    PlaylistPickerSearchRows(previews, matches) { entry(label, it) }
                }
            }
        }
    } else {
        GridMenu.Menu {
            item(span = { GridItemSpan(maxLineSpan) }) { Column(content = header) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column { PlaylistPickerFeedback(playlistRead, query, empty) }
            }
            matchingGroups.forEach { (label, matches) ->
                if (matches.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { heading(label) }
                    items(matches, key = { it.playlist.id }) { entry(label, it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaylistPickerEntry(
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
)

internal fun playlistPickerSections(previews: List<PlaylistPreview>, online: Boolean): PlaylistPickerSections {
    val visible = previews.filter { isPlaylistPickerVisible(it.playlist) }
    return PlaylistPickerSections(
        pinned = visible.filter { isPinnedPlaylistPickerVisible(it.playlist, online) },
        youtube = visible.filter {
            isYouTubePlaylistPickerEntry(it.playlist) &&
                !it.playlist.name.startsWith(PINNED_PREFIX, ignoreCase = true) &&
                (online || !canEditPlaylistMembership(it.playlist))
        },
        local = visible.filter {
            !it.playlist.name.startsWith(PINNED_PREFIX, ignoreCase = true) &&
                !it.playlist.name.startsWith(MONTHLY_PREFIX, ignoreCase = true) &&
                !RewindPlaylists.isRewind(it.playlist.name) &&
                !isYouTubePlaylistPickerEntry(it.playlist)
        },
    )
}

internal fun openPlaylistPickerDestination(
    playlistId: Long,
    onNavigate: ((Long) -> Unit)?,
    onDismiss: () -> Unit,
    navigate: (Long) -> Unit,
) {
    (onNavigate ?: navigate)(playlistId)
    onDismiss()
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
internal fun PlaylistPickerLockIcon() {
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
internal fun SongInPlaylistIcon() {
    Icon(
        painter = painterResource(R.drawable.playlist),
        contentDescription = stringResource(R.string.playlist_contains_song),
        tint = colorPalette().accent,
        modifier = Modifier.size(18.dp)
    )
}

class PlaylistsMenu private constructor(
    private val navController: NavController,
    private val songId: String?,
    private val captureRequest: () -> AddRequest?,
    private val onFailure: (Throwable, PlaylistPreview) -> Unit,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>,
    private val localDestinationsOnly: Boolean = false,
): MenuIcon, Descriptive, Menu {

    private class AddRequest(
        val load: (PlaylistPreview) -> suspend () -> List<MediaItem>?,
        val onComplete: suspend (PlaylistPreview) -> Unit,
    )

    companion object {
        @JvmStatic
        @Composable
        fun init(
            navController: NavController,
            mediaItems: (PlaylistPreview) -> List<MediaItem>,
            onFailure: (Throwable, PlaylistPreview) -> Unit,
            finalAction: (PlaylistPreview) -> Unit,
            songId: String? = null,
            localDestinationsOnly: Boolean = false,
        ) = PlaylistsMenu(
            navController,
            songId,
            { AddRequest(load = { preview -> { mediaItems(preview) } }, onComplete = { finalAction(it) }) },
            onFailure,
            LocalMenuState.current,
            rememberPreference( menuStyleKey, MenuStyle.List ),
            localDestinationsOnly = localDestinationsOnly,
        )

        @Composable
        internal fun deferred(
            navController: NavController,
            captureMediaItems: () -> (suspend () -> List<MediaItem>?)?,
            onFailure: (Throwable, PlaylistPreview) -> Unit,
            itemSelector: ItemSelector<*>,
            localDestinationsOnly: Boolean = false,
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
    private fun PlaylistCard(playlistPreview: PlaylistPreview, containsSong: Boolean, onAdd: () -> Unit) {
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
                if (isYouTubePlaylistPickerEntry(playlist)) {
                    Image(
                        painter = painterResource(R.drawable.ytmusic),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            Color.Red.copy(0.75f).compositeOver(Color.White)
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    icon = R.drawable.open,
                    contentDescription = stringResource(R.string.playlist_action_open),
                    color = colorPalette().accent,
                    onClick = {
                        openPlaylistPickerDestination(playlist.id, null, menuState::hide) { id ->
                            navigateToPickedPlaylist(navController, id)
                        }
                    },
                    modifier = Modifier.size( 24.dp )
                )
            }
        )
    }

    override fun onShortClick() {
        openMenu()
    }

    override fun openMenu() {
        val request = captureRequest() ?: return
        menuState.display { PlaylistPicker(request) }
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
        val playlistRead = rememberPlaylistPickerRead(PlaylistSortBy.Name, SortOrder.Ascending)
        val online = isNetworkConnected(LocalContext.current)
        val sections = playlistPickerSections(playlistRead.previews, online)
        val songPlaylistIds = if (songId != null) rememberSongPlaylistIds(songId) else emptyList()
        val search = Search()
        val groups = listOf(
            R.string.playlist_pinned_title to sections.pinned,
            R.string.sync_youtube_playlists_title to sections.youtube,
            R.string.library_playlists_title to sections.local,
        )

        val newPlaylistButton = NewPlaylistDialog(localOnly = localDestinationsOnly)
        newPlaylistButton.Render()

        PlaylistPickerMenu(
            style = menuStyle,
            playlistRead = playlistRead,
            query = search.inputValue,
            groups = groups,
            header = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    if (menuState.hasPrevious) {
                        IconButton(
                            onClick = menuState::pop,
                            icon = R.drawable.chevron_back,
                            contentDescription = stringResource(R.string.common_action_back),
                            color = colorPalette().accent,
                            modifier = Modifier.padding(4.dp).size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { search.onShortClick() },
                        icon = R.drawable.search_circle,
                        contentDescription = stringResource(R.string.search_title),
                        color = colorPalette().accent,
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(24.dp)
                    )
                    BasicText(
                        text = stringResource(R.string.playlist_action_add),
                        style = typography().m.semiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    newPlaylistButton.ToolBarButton()
                }
                search.SearchBar(this)
            },
            heading = { label ->
                BasicText(
                    text = stringResource(label),
                    style = typography().xxs.semiBold.copy(color = colorPalette().accent),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
                )
            },
            entry = { _, preview ->
                PlaylistCard(preview, preview.playlist.id in songPlaylistIds) {
                    val menuKey = menuState.transitionKey
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        addToPlaylist(preview, request, menuKey)
                    }
                }
            },
        )
    }
}



