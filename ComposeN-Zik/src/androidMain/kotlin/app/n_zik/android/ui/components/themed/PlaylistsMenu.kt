package app.n_zik.android.ui.components.themed

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.core.database.editStoredPlaylistMembership
import app.n_zik.android.core.database.isYouTubePlaylistSource

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.MONTHLY_PREFIX
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
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

import app.n_zik.android.components.menu.ListMenu
import androidx.compose.material3.Icon

internal fun isPlaylistPickerTarget(playlist: Playlist, localDestinationsOnly: Boolean): Boolean =
    canEditPlaylistMembership(playlist) &&
        (!localDestinationsOnly || !isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist))

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
    private val loadBeforeTransaction: Boolean,
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
            songId: String? = null
        ) = PlaylistsMenu(
            navController,
            songId,
            false,
            { AddRequest(load = { preview -> { mediaItems(preview) } }, onComplete = { finalAction(it) }) },
            onFailure,
            LocalMenuState.current,
            rememberPreference( menuStyleKey, MenuStyle.List )
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
            true,
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

    private suspend fun Database.addToPlaylist(preview: PlaylistPreview, request: AddRequest) {
        var completeRequest = !localDestinationsOnly
        try {
            val load = request.load(preview)
            val preparedItems = if (loadBeforeTransaction) load() ?: return else null
            var alreadyPresent = false
            var added = false
            var targetAvailable = false
            val editable = editStoredPlaylistMembership(preview.playlist.id) {
                val target = if (localDestinationsOnly) {
                    playlistTable.findById(preview.playlist.id).first()
                        ?.takeIf { isPlaylistPickerTarget(it, localDestinationsOnly = true) }
                        ?: return@editStoredPlaylistMembership
                } else preview.playlist
                targetAvailable = true
                alreadyPresent = songId != null &&
                    songPlaylistMapTable.findById(songId, target.id).first() != null
                if (!alreadyPresent) {
                    val items = preparedItems ?: load() ?: return@editStoredPlaylistMembership
                    mapIgnore(target, *items.toTypedArray())
                    added = true
                }
            }
            when {
                localDestinationsOnly && (!editable || !targetAvailable) -> withContext(NzikDispatchers.UI) {
                    Toaster.w(R.string.playlist_add_destination_unavailable)
                }
                alreadyPresent -> Toaster.i(R.string.playlist_contains_song)
                editable && added -> {
                    completeRequest = true
                    Toaster.done()
                }
                else -> Unit
            }
        } catch (e: Throwable) {
            onFailure(e, preview)
        } finally {
            if (completeRequest) request.onComplete(preview)
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

        ListMenu.Entry(
            text = cleanPrefix(playlist.name),
            icon = { SettingIcon(R.drawable.add_in_playlist) },
            subtitle = pluralStringResource(R.plurals.library_song_count, playlistPreview.songCount, playlistPreview.songCount),
            onClick = {
                if (containsSong) {
                    Toaster.i(R.string.playlist_contains_song)
                } else {
                    onAdd()
                }
            },
            trailingContent = {
                if (containsSong) SongInPlaylistIcon()
                if (playlistPreview.playlist.isYoutubePlaylist) {
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
                    color = colorPalette().accent,
                    onClick = {
                        menuState.hide()
                        navController.navigate(route = "${NavRoutes.localPlaylist.name}/${playlist.id}")
                    },
                    modifier = Modifier.size( 24.dp )
                )
            }
        )
    }

    override fun onShortClick() {
        menuState.hide()
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
        var playlistsLoaded by remember { mutableStateOf(false) }

        val playlistPreviews by remember {
            Database.playlistTable.sortPreviewsByName().onEach { playlistsLoaded = true }
        }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

        val songPlaylistIds = if (songId != null) rememberSongPlaylistIds(songId) else emptyList()

        val editablePreviews = playlistPreviews.filter { isPlaylistPickerTarget(it.playlist, localDestinationsOnly) }
        val pinnedPlaylists = editablePreviews.filter {
            it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
        }
        val unpinnedPlaylists = editablePreviews.filter {
            !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                    !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                    // Spec 2: generated rewind-* playlists are never add-to targets
                    !RewindPlaylists.isRewind(it.playlist.name)
        }

        val search = Search()
        val filteredPinnedPlaylists = pinnedPlaylists.filter { it.playlist.name.contains(search.inputValue, true) }
        val filteredUnpinnedPlaylists = unpinnedPlaylists.filter { it.playlist.name.contains(search.inputValue, true) }

        val newPlaylistButton = NewPlaylistDialog()
        newPlaylistButton.Render()

        ListMenu.Menu(title = stringResource(R.string.library_playlists_title)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = { search.onShortClick() },
                    icon = R.drawable.search_circle,
                    color = colorPalette().accent,
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .size(24.dp)
                )
                BasicText(
                    text = stringResource(R.string.library_playlists_title),
                    style = typography().m.semiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                newPlaylistButton.ToolBarButton()
            }
            search.SearchBar(this)
            val noPlaylistResults = filteredPinnedPlaylists.isEmpty() && filteredUnpinnedPlaylists.isEmpty()
            SearchEmptyFeedback(
                query = search.inputValue,
                empty = noPlaylistResults,
                loading = !playlistsLoaded
            )
            if (search.inputValue.isNotBlank() && noPlaylistResults) {
                SearchEmptySpace(Modifier.height(160.dp))
            }
            if (filteredPinnedPlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.playlist_pinned_title),
                    style = typography().xxs.semiBold.copy(
                        color = colorPalette().accent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )

                filteredPinnedPlaylists.forEach { preview ->
                    PlaylistCard(preview, preview.playlist.id in songPlaylistIds) {
                        Database.asyncTransaction { addToPlaylist(preview, request) }
                    }
                }
            }

            if (filteredUnpinnedPlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.library_playlists_title),
                    style = typography().xxs.semiBold.copy(
                        color = colorPalette().accent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )

                filteredUnpinnedPlaylists.forEach { preview ->
                    PlaylistCard(preview, preview.playlist.id in songPlaylistIds) {
                        Database.asyncTransaction { addToPlaylist(preview, request) }
                    }
                }
            }
        }
    }
}



