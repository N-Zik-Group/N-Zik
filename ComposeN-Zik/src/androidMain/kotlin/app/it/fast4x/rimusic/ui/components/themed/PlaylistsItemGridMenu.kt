package app.it.fast4x.rimusic.ui.components.themed

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.n_zik.android.core.rewind.RewindPlaylists

import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.dialog.playlist.NewPlaylistDialog
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.playlistSortByKey
import app.it.fast4x.rimusic.utils.playlistSortOrderKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.tab.Search
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.GridMenu
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.grid.GridItemSpan

@Composable
private fun SettingIcon(@DrawableRes icon: Int) {
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

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun PlaylistsItemGridMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    onSelectUnselect: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onUncheck: (() -> Unit)? = null,
    playlist: PlaylistPreview? = null,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onDeleteSongsNotInLibrary: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onImportOnlinePlaylist: (() -> Unit)? = null,
    onAddToPreferites: (() -> Unit)? = null,
    showonAddToPreferitesYoutube: Boolean = false,
    onAddToPreferitesYoutube: (() -> Unit)? = null,
    onAddToPlaylist: ((PlaylistPreview) -> Unit)? = null,
    showOnSyncronize: Boolean = false,
    showLinkUnlink: Boolean = false,
    onSyncronize: (() -> Unit)? = null,
    onRenumberPositions: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    showonListenToYT: Boolean = false,
    onListenToYT: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onImportFavorites: (() -> Unit)? = null,
    onEditThumbnail: (() -> Unit)? = null,
    onResetThumbnail: (() -> Unit)? = null,
    onGoToPlaylist: ((Long) -> Unit)? = null,
    onLinkUnlink: (() -> Unit)? = null,
    disableScrollingText: Boolean
    ) {
    var isViewingPlaylists by remember {
        mutableStateOf(false)
    }

    var height by remember {
        mutableStateOf(0.dp)
    }

    val binder = LocalPlayerServiceBinder.current
    val menuStyle by rememberPreference(
        menuStyleKey,
        MenuStyle.List
    )
    val thumbnailSizeDp = Dimensions.thumbnails.song + 20.dp
    val thumbnailSizePx = thumbnailSizeDp.px
    val thumbnailArtistSizeDp = Dimensions.thumbnails.song + 10.dp
    val thumbnailArtistSizePx = thumbnailArtistSizeDp.px


    AnimatedContent(
        targetState = isViewingPlaylists,
        transitionSpec = {
            val animationSpec = tween<IntOffset>(400)
            val slideDirection = if (targetState) AnimatedContentTransitionScope.SlideDirection.Left
            else AnimatedContentTransitionScope.SlideDirection.Right

            slideIntoContainer(slideDirection, animationSpec) togetherWith
                    slideOutOfContainer(slideDirection, animationSpec)
        }, label = ""
    ) { currentIsViewingPlaylists ->
        if (currentIsViewingPlaylists) {
            val context = LocalContext.current
            val sortBy by rememberPreference(playlistSortByKey, PlaylistSortBy.DateAdded)
            val sortOrder by rememberPreference(playlistSortOrderKey, SortOrder.Descending)
            val playlistPreviews by remember {
                Database.playlistTable.sortPreviews( sortBy, sortOrder )
            }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

            val pinnedPlaylists = playlistPreviews.filter {
                it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
                        && if (isNetworkConnected(context)) !(it.playlist.isYoutubePlaylist && !it.playlist.isEditable) else !it.playlist.isYoutubePlaylist
            }

            val youtubePlaylists = playlistPreviews.filter { it.playlist.isEditable && it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX) }

            val unpinnedPlaylists = playlistPreviews.filter {
                !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                        !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                        // Spec 2: generated rewind-* playlists are never add-to targets
                        !RewindPlaylists.isRewind(it.playlist.name) &&
                        !it.playlist.isYoutubePlaylist
            }

            val search = Search()
            val title = stringResource(R.string.playlists)

            val newPlaylistDialog = NewPlaylistDialog { playlist ->
                onDismiss()
                Database.asyncTransaction {
                    val pId = playlist.id
                    if (onAddToPlaylist != null) {
                        onAddToPlaylist(
                            PlaylistPreview(
                                Playlist(
                                    id = pId,
                                    name = playlist.name
                                ),
                                0
                            )
                        )
                    }
                }
                Toaster.done()
            }

            if (onAddToPlaylist != null) {
                newPlaylistDialog.Render()
            }

            BackHandler {
                isViewingPlaylists = false
            }

            ListMenu.Menu(title = stringResource(R.string.playlists)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { isViewingPlaylists = false },
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
                        text = title,
                        style = typography().m.semiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    if (onAddToPlaylist != null) {
                        IconButton(
                            onClick = { newPlaylistDialog.onShortClick() },
                            icon = R.drawable.add_in_playlist,
                            color = colorPalette().accent,
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(24.dp)
                        )
                    }
                }
                if (search.isVisible) {
                    search.SearchBar(this)
                }
                val filteredPinnedPlaylists = pinnedPlaylists.filter { it.playlist.name.contains(search.inputValue, true) }
                val filteredYoutubePlaylists = youtubePlaylists.filter { it.playlist.name.contains(search.inputValue, true) }
                val filteredUnpinnedPlaylists = unpinnedPlaylists.filter { it.playlist.name.contains(search.inputValue, true) }

                if (filteredPinnedPlaylists.isNotEmpty()) {
                    BasicText(
                        text = stringResource(R.string.pinned_playlists),
                        style = typography().m.semiBold,
                        modifier = modifier.padding(start = 20.dp, top = 5.dp)
                    )

                    onAddToPlaylist?.let { onAddToPlaylist ->
                        filteredPinnedPlaylists.forEach { playlistPreview ->
                            ListMenu.Entry(
                                text = cleanPrefix(playlistPreview.playlist.name),
                                icon = { SettingIcon(R.drawable.add_in_playlist) },
                                subtitle = "${playlistPreview.songCount} " + stringResource(
                                    R.string.songs
                                ),
                                onClick = {
                                    onAddToPlaylist(
                                        PlaylistPreview(
                                            playlistPreview.playlist,
                                            playlistPreview.songCount
                                        )
                                    )
                                    Toaster.done()
                                    onDismiss()
                                },
                                trailingContent = {
                                    if (playlistPreview.playlist.isYoutubePlaylist) {
                                        Image(
                                            painter = painterResource(R.drawable.ytmusic),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(
                                                Color.Red.copy(0.75f).compositeOver(Color.White)
                                            ),
                                            modifier = Modifier
                                                .size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        icon = R.drawable.open,
                                        color = colorPalette().accent,
                                        onClick = {
                                            if (onGoToPlaylist != null) {
                                                onGoToPlaylist(playlistPreview.playlist.id)
                                                onDismiss()
                                            }
                                            navController.navigate(route = "${NavRoutes.localPlaylist.name}/${playlistPreview.playlist.id}")
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (filteredYoutubePlaylists.isNotEmpty() && isNetworkConnected(context)) {
                    BasicText(
                        text = stringResource(R.string.ytm_playlists),
                        style = typography().m.semiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 5.dp)
                    )

                    onAddToPlaylist?.let { onAddToPlaylist ->
                        filteredYoutubePlaylists.forEach { playlistPreview ->
                            ListMenu.Entry(
                                text = cleanPrefix(playlistPreview.playlist.name),
                                icon = { SettingIcon(R.drawable.add_in_playlist) },
                                subtitle = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                                onClick = {
                                    onAddToPlaylist(
                                        PlaylistPreview(
                                            playlistPreview.playlist,
                                            playlistPreview.songCount
                                        )
                                    )
                                    Toaster.done()
                                    onDismiss()
                                },
                                trailingContent = {
                                    IconButton(
                                        icon = R.drawable.open,
                                        color = colorPalette().accent,
                                        onClick = {
                                            if (onGoToPlaylist != null) {
                                                onGoToPlaylist(playlistPreview.playlist.id)
                                                onDismiss()
                                            }
                                            navController.navigate(route = "${NavRoutes.localPlaylist.name}/${playlistPreview.playlist.id}")
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (filteredUnpinnedPlaylists.isNotEmpty()) {
                    BasicText(
                        text = stringResource(R.string.playlists),
                        style = typography().m.semiBold,
                        modifier = modifier.padding(start = 20.dp, top = 5.dp)
                    )

                    onAddToPlaylist?.let { onAddToPlaylist ->
                        filteredUnpinnedPlaylists.forEach { playlistPreview ->
                            ListMenu.Entry(
                                text = cleanPrefix(playlistPreview.playlist.name),
                                icon = { SettingIcon(R.drawable.add_in_playlist) },
                                subtitle = "${playlistPreview.songCount} " + stringResource(
                                    R.string.songs
                                ),
                                onClick = {
                                    onAddToPlaylist(
                                        PlaylistPreview(
                                            playlistPreview.playlist,
                                            playlistPreview.songCount
                                        )
                                    )
                                    Toaster.done()
                                    onDismiss()
                                },
                                trailingContent = {


                                    IconButton(
                                        icon = R.drawable.open,
                                        color = colorPalette().accent,
                                        onClick = {
                                            if (onGoToPlaylist != null) {
                                                onGoToPlaylist(playlistPreview.playlist.id)
                                                onDismiss()
                                            }
                                            navController.navigate(route = "${NavRoutes.localPlaylist.name}/${playlistPreview.playlist.id}")
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        } else {
            val selectText = "${stringResource(R.string.item_select)}/${stringResource(R.string.item_deselect)}"
            val colorPalette = colorPalette()

            GridMenu.Menu(title = null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (playlist != null) {
                        PlaylistItem(
                            playlist = playlist,
                            thumbnailSizePx = thumbnailSizePx,
                            thumbnailSizeDp = thumbnailSizeDp,
                            modifier = Modifier.height(90.dp),
                            disableScrollingText = disableScrollingText,
                            isYoutubePlaylist = playlist.playlist.isYoutubePlaylist,
                            isEditable = playlist.playlist.isEditable
                        )
                    }
                }

                onSelectUnselect?.let { onSelectUnselect ->
                    item {
                        GridMenu.Entry(
                            text = selectText,
                            icon = { SettingIcon(R.drawable.checked_filled) },
                            onClick = {
                                onDismiss()
                                onSelectUnselect()
                            }
                        )
                    }
                }

                onPlayNext?.let { onPlayNext ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.play_next),
                            icon = { SettingIcon(R.drawable.play_skip_forward) },
                            onClick = {
                                onDismiss()
                                onPlayNext()
                            }
                        )
                    }
                }

                onDeleteSongsNotInLibrary?.let { onDeleteSongsNotInLibrary ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.delete_songs_not_in_library),
                            icon = { SettingIcon(R.drawable.trash) },
                            onClick = {
                                onDismiss()
                                onDeleteSongsNotInLibrary()
                            }
                        )
                    }
                }

                onEnqueue?.let { onEnqueue ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.enqueue),
                            icon = { SettingIcon(R.drawable.enqueue) },
                            onClick = {
                                onDismiss()
                                onEnqueue()
                            }
                        )
                    }
                }

                if (showOnSyncronize) onSyncronize?.let { onSyncronize ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.sync),
                            icon = { SettingIcon(R.drawable.sync) },
                            onClick = {
                                onDismiss()
                                onSyncronize()
                            }
                        )
                    }
                }

                if (showLinkUnlink) onLinkUnlink?.let { onLinkUnlink ->
                    item {
                        GridMenu.Entry(
                            text = if (playlist?.playlist?.isYoutubePlaylist == true) stringResource(R.string.unlink_from_ytm) else stringResource(R.string.unlink_from_yt),
                            icon = { SettingIcon(R.drawable.link) },
                            onClick = {
                                onDismiss()
                                onLinkUnlink()
                            }
                        )
                    }
                }

                onImportOnlinePlaylist?.let { onImportOnlinePlaylist ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.import_playlist),
                            icon = { SettingIcon(R.drawable.add_in_playlist) },
                            onClick = {
                                onDismiss()
                                onImportOnlinePlaylist()
                            }
                        )
                    }
                }

                if (onAddToPreferites != null)
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.add_to_favorites),
                            icon = { SettingIcon(R.drawable.heart) },
                            onClick = {
                                onDismiss()
                                onAddToPreferites()
                            }
                        )
                    }

                if (showonAddToPreferitesYoutube) {
                    if (onAddToPreferitesYoutube != null)
                        item {
                            GridMenu.Entry(
                                text = stringResource(R.string.add_rimusic_to_ytm_favorites),
                                icon = { SettingIcon(R.drawable.ytmusic) },
                                onClick = {
                                    onDismiss()
                                    onAddToPreferitesYoutube()
                                }
                            )
                        }
                }

                onAddToPlaylist?.let { onAddToPlaylist ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.add_to_playlist),
                            icon = { SettingIcon(R.drawable.add_in_playlist) },
                            onClick = {
                                isViewingPlaylists = true
                            }
                        )
                    }
                }

                onRename?.let { onRename ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.rename),
                            icon = { SettingIcon(R.drawable.title_edit) },
                            onClick = {
                                onDismiss()
                                onRename()
                            }
                        )
                    }
                }

                onDelete?.let { onDelete ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.delete),
                            icon = { SettingIcon(R.drawable.trash) },
                            onClick = {
                                onDismiss()
                                onDelete()
                            }
                        )
                    }
                }

                onRenumberPositions?.let { onRenumberPositions ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.renumber_songs_positions),
                            icon = { SettingIcon(R.drawable.position) },
                            onClick = {
                                onDismiss()
                                onRenumberPositions()
                            }
                        )
                    }
                }

                if (showonListenToYT) onListenToYT?.let { onListenToYT ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.listen_on_youtube),
                            icon = { SettingIcon(R.drawable.play) },
                            onClick = {
                                onDismiss()
                                onListenToYT()
                            }
                        )
                    }
                }

                onExport?.let { onExport ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.export_playlist),
                            icon = { SettingIcon(R.drawable.export_outline) },
                            onClick = {
                                onDismiss()
                                onExport()
                            }
                        )
                    }
                }

                onImport?.let { onImport ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.import_playlist),
                            icon = { SettingIcon(R.drawable.import_outline) },
                            onClick = {
                                onDismiss()
                                onImport()
                            }
                        )
                    }
                }

                onImportFavorites?.let { onImport ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.import_favorites),
                            icon = { SettingIcon(R.drawable.import_outline) },
                            onClick = {
                                onDismiss()
                                onImportFavorites()
                            }
                        )
                    }
                }

                onEditThumbnail?.let { onEditThumbnail ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.edit_thumbnail),
                            icon = { SettingIcon(R.drawable.image) },
                            onClick = {
                                onDismiss()
                                onEditThumbnail()
                            }
                        )
                    }
                }

                onResetThumbnail?.let { onResetThumbnail ->
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.reset_thumbnail),
                            icon = { SettingIcon(R.drawable.image) },
                            onClick = {
                                onDismiss()
                                onResetThumbnail()
                            }
                        )
                    }
                }
            }

        }
    }

}



