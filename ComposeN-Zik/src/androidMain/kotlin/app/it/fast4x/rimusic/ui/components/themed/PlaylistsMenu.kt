package app.it.fast4x.rimusic.ui.components.themed

import app.n_zik.android.core.database.*

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.dialog.playlist.NewPlaylistDialog
import app.n_zik.android.components.tab.Search
import app.kreate.android.me.knighthat.utils.Toaster
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver

import app.n_zik.android.components.menu.ListMenu
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource

class PlaylistsMenu private constructor(
    private val navController: NavController,
    private val mediaItems: (PlaylistPreview) -> List<MediaItem>,
    private val onFailure: (Throwable, PlaylistPreview) -> Unit,
    private val finalAction: (PlaylistPreview) -> Unit,
    private val onDismiss: (() -> Unit)?,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
): MenuIcon, Descriptive, Menu {

    companion object {
        @JvmStatic
        @Composable
        fun init(
            navController: NavController,
            mediaItems: (PlaylistPreview) -> List<MediaItem>,
            onFailure: (Throwable, PlaylistPreview) -> Unit,
            finalAction: (PlaylistPreview) -> Unit,
            onDismiss: (() -> Unit)? = null
        ) = PlaylistsMenu(
            navController,
            mediaItems,
            onFailure,
            finalAction,
            onDismiss,
            LocalMenuState.current,
            rememberPreference( menuStyleKey, MenuStyle.List )
        )
    }

    override val iconId: Int = R.drawable.add_in_playlist
    override val messageId: Int = R.string.add_to_playlist
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var menuStyle: MenuStyle by styleState

    private fun onAdd( preview: PlaylistPreview ) = Database.asyncTransaction {
        try {
            mapIgnore(preview.playlist, *mediaItems(preview).toTypedArray())
            Toaster.done()
        } catch (e: Throwable) {
            onFailure(e, preview)
        } finally {
            finalAction(preview)
        }
    }

    @Composable
    fun SettingIcon(@DrawableRes icon: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
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
    private fun PlaylistCard( playlistPreview: PlaylistPreview ) {
        val playlist = playlistPreview.playlist

        ListMenu.Entry(
            text = playlist.name.substringAfter( PINNED_PREFIX ),
            icon = { SettingIcon(R.drawable.add_in_playlist) },
            subtitle = "${playlistPreview.songCount} ${stringResource( R.string.songs )}",
            onClick = {
                onAdd( playlistPreview )
            },
            trailingContent = {
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

    @Composable
    override fun ListMenu() { /* Does nothing */ }

    @Composable
    override fun GridMenu() { /* Does nothing */ }

    @Composable
    override fun MenuComponent() {

        val playlistPreviews by remember {
            Database.playlistTable.sortPreviewsByName()
        }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

        val pinnedPlaylists = playlistPreviews.filter {
            it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
        }
        val unpinnedPlaylists = playlistPreviews.filter {
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

        ListMenu.Menu(title = stringResource(R.string.playlists)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = { onDismiss?.invoke() ?: menuState.hide() },
                    icon = R.drawable.chevron_back,
                    color = colorPalette().accent,
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .size(20.dp)
                )
                IconButton(
                    onClick = { search.isVisible = !search.isVisible },
                    icon = R.drawable.search_circle,
                    color = colorPalette().accent,
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .size(24.dp)
                )
                BasicText(
                    text = stringResource(R.string.playlists),
                    style = typography().m.semiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                newPlaylistButton.ToolBarButton()
            }
            search.SearchBar(this)
            if (filteredPinnedPlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.pinned_playlists),
                    style = typography().xxs.semiBold.copy(
                        color = colorPalette().accent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )

                filteredPinnedPlaylists.forEach { PlaylistCard(it) }
            }

            if (filteredUnpinnedPlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.playlists),
                    style = typography().xxs.semiBold.copy(
                        color = colorPalette().accent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )

                filteredUnpinnedPlaylists.forEach { PlaylistCard(it) }
            }
        }
    }
}



