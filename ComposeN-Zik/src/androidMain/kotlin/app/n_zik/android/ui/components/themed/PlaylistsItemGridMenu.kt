package app.n_zik.android.ui.components.themed

import app.n_zik.android.components.menu.playlist.rememberPlaylistPickerRead


import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.R

import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.dialog.playlist.NewPlaylistDialog
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.PlaylistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.typography
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.playlistSortByKey
import app.n_zik.android.utils.playlistSortOrderKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.menuSectionHorizontalAlignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.grid.GridItemSpan

@Composable
private fun SettingIcon(@DrawableRes icon: Int) {
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
            val online = isNetworkConnected(LocalContext.current)
            val sortBy by rememberPreference(playlistSortByKey, PlaylistSortBy.DateAdded)
            val sortOrder by rememberPreference(playlistSortOrderKey, SortOrder.Descending)
            val playlistRead = rememberPlaylistPickerRead(sortBy, sortOrder)
            val playlistPreviews = playlistRead.previews

            val (pinnedPlaylists, youtubePlaylists, unpinnedPlaylists) = playlistPickerSections(playlistPreviews, online)

            val search = Search()
            val title = stringResource(R.string.library_playlists_title)

            val newPlaylistDialog = NewPlaylistDialog { playlist ->
                onDismiss()
                onAddToPlaylist?.invoke(PlaylistPreview(playlist, 0))
            }

            if (onAddToPlaylist != null) {
                newPlaylistDialog.Render()
            }

            BackHandler {
                isViewingPlaylists = false
            }

            PlaylistPickerMenu(
                style = MenuStyle.Grid,
                playlistRead = playlistRead,
                query = search.inputValue,
                groups = listOf(
                    R.string.playlist_pinned_title to pinnedPlaylists,
                    R.string.sync_youtube_playlists_title to youtubePlaylists,
                    R.string.library_playlists_title to unpinnedPlaylists,
                ),
                header = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    ) {
                        IconButton(
                            onClick = { isViewingPlaylists = false },
                            icon = R.drawable.chevron_back,
                            contentDescription = stringResource(R.string.common_action_back),
                            color = colorPalette().accent,
                            modifier = Modifier.padding(4.dp).size(20.dp),
                        )
                        IconButton(
                            onClick = search::onShortClick,
                            icon = R.drawable.search_circle,
                            contentDescription = stringResource(R.string.search_title),
                            color = colorPalette().accent,
                            modifier = Modifier.padding(4.dp).size(24.dp),
                        )
                        BasicText(
                            text = title,
                            style = typography().m.semiBold,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        if (onAddToPlaylist != null) {
                            IconButton(
                                onClick = newPlaylistDialog::onShortClick,
                                icon = R.drawable.add_in_playlist,
                                contentDescription = stringResource(R.string.playlist_action_create),
                                color = colorPalette().accent,
                                modifier = Modifier.padding(4.dp).size(24.dp),
                            )
                        }
                    }
                    search.SearchBar(this)
                },
                heading = { label ->
                    BasicText(
                        text = stringResource(label),
                        style = typography().m.semiBold,
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 5.dp)
                            .menuSectionHorizontalAlignment(),
                    )
                },
                entry = { label, preview ->
                    val playlist = preview.playlist
                    val locked = !canEditPlaylistMembership(playlist)
                    PlaylistPickerEntry(
                        style = MenuStyle.Grid,
                        preview = preview,
                        icon = { SettingIcon(R.drawable.add_in_playlist) },
                        onClick = {
                            if (locked) {
                                warnPlaylistPickerLocked()
                            } else {
                                onAddToPlaylist?.invoke(preview)
                                onDismiss()
                            }
                        },
                        trailingContent = {
                            if (locked) PlaylistPickerLockIcon()
                            if (label == R.string.playlist_pinned_title && playlist.isYoutubePlaylist) {
                                Image(
                                    painter = painterResource(R.drawable.ytmusic),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(Color.Red.copy(0.75f).compositeOver(Color.White)),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                icon = R.drawable.open,
                                contentDescription = stringResource(R.string.playlist_action_open),
                                color = colorPalette().accent,
                                onClick = {
                                    openPlaylistPickerDestination(playlist.id, onGoToPlaylist, onDismiss) { id ->
                                        navController.navigate("${NavRoutes.localPlaylist.name}/$id")
                                    }
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        },
                    )
                },
            )
        } else {
            val selectText = "${stringResource(R.string.common_action_select)}/${stringResource(R.string.playlist_selection_action_deselect)}"
            val colorPalette = colorPalette()

            GridMenu.Menu(title = if (playlist == null) stringResource(R.string.library_playlists_title) else null) {
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
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.player_action_play_next),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.playlist_action_remove_songs_outside_library),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.player_action_enqueue),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.sync_action_sync),
                            iconHasOwnBackground = true,
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
                            text = if (playlist?.playlist?.isYoutubePlaylist == true) stringResource(R.string.playlist_action_unlink_youtube_library) else stringResource(R.string.playlist_action_unlink_youtube),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.playlist_action_import),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.library_favorites_action_add),
                            iconHasOwnBackground = true,
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
                                text = stringResource(R.string.playlist_action_like_favorites_on_youtube),
                                iconHasOwnBackground = true,
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
                            text = stringResource(R.string.playlist_action_add),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.playlist_action_rename),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.common_action_delete),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.playlist_action_shuffle_positions),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.player_action_listen_on_youtube),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.playlist_action_export),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.playlist_action_import),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.playlist_action_import_favorites),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.thumbnail_action_edit),
                            iconHasOwnBackground = true,
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
                            text = stringResource(R.string.thumbnail_action_reset),
                            iconHasOwnBackground = true,
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



