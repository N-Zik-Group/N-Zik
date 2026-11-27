package app.n_zik.android.ui.components.themed



import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.ui.iconBadgeBackground
import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R

import app.n_zik.android.colorPalette
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.components.menu.ListMenu
import androidx.compose.ui.res.painterResource


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
fun PlaylistsItemMenu(
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
    onOpenAddToPlaylist: (() -> Unit)? = null,
    onAddToPreferites: (() -> Unit)? = null,
    showonAddToPreferitesYoutube: Boolean = false,
    onAddToPreferitesYoutube: (() -> Unit)? = null,
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
    var height by remember {
        mutableStateOf(0.dp)
    }

    val menuStyle by rememberPreference(
        menuStyleKey,
        MenuStyle.List
    )

    if (menuStyle == MenuStyle.Grid) {
        PlaylistsItemGridMenu(
            navController = navController,
            onDismiss = onDismiss,
            playlist = playlist,
            onSelectUnselect = onSelectUnselect,
            onPlayNext = onPlayNext,
            onDeleteSongsNotInLibrary = onDeleteSongsNotInLibrary,
            onEnqueue = onEnqueue,
            onImportOnlinePlaylist = onImportOnlinePlaylist,
            onOpenAddToPlaylist = onOpenAddToPlaylist,
            onAddToPreferites = onAddToPreferites,
            onAddToPreferitesYoutube = onAddToPreferitesYoutube,
            showOnSyncronize = showOnSyncronize,
            showLinkUnlink = showLinkUnlink,
            onSyncronize = onSyncronize,
            onLinkUnlink = onLinkUnlink,
            onRenumberPositions = onRenumberPositions,
            onDelete = onDelete,
            onRename = onRename,
            showonListenToYT = showonListenToYT,
            onListenToYT = onListenToYT,
            onExport = onExport,
            onImport = onImport,
            onImportFavorites = onImportFavorites,
            onEditThumbnail = onEditThumbnail,
            onResetThumbnail = onResetThumbnail,
            onGoToPlaylist = onGoToPlaylist,
            disableScrollingText = disableScrollingText
        )
    } else {

        ListMenu.Menu(title = if (playlist == null) stringResource(R.string.library_playlists_title) else null) {
            val thumbnailSizeDp = Dimensions.thumbnails.song + 20.dp
            val thumbnailSizePx = thumbnailSizeDp.px
            //val thumbnailArtistSizeDp = Dimensions.thumbnails.song + 10.dp
            //val thumbnailArtistSizePx = thumbnailArtistSizeDp.px

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 12.dp)
            ) {
                if (playlist != null) {
                    PlaylistItem(
                        playlist = playlist,
                        thumbnailSizePx = thumbnailSizePx,
                        thumbnailSizeDp = thumbnailSizeDp,
                        disableScrollingText = disableScrollingText,
                        isEditable =  playlist.playlist.isEditable,
                        isYoutubePlaylist = playlist.playlist.isYoutubePlaylist
                    )
                }

            }

            Spacer(
                modifier = Modifier
                    .height(8.dp)
            )

            onSelectUnselect?.let { onSelectUnselect ->
                ListMenu.Entry(
                    text = "${stringResource(R.string.common_action_select)}/${stringResource(R.string.playlist_selection_action_deselect)}",
                    icon = { SettingIcon(R.drawable.checked_filled) },
                    onClick = {
                        onDismiss()
                        onSelectUnselect()
                    }
                )
            }
            onSelect?.let { onSelect ->
                ListMenu.Entry(
                    text = stringResource(R.string.common_action_select),
                    icon = { SettingIcon(R.drawable.checked_filled) },
                    onClick = {
                        onDismiss()
                        onSelect()
                    }
                )
            }
            onPlayNext?.let { onPlayNext ->
                ListMenu.Entry(
                    text = stringResource(R.string.player_action_play_next),
                    icon = { SettingIcon(R.drawable.play_skip_forward) },
                    onClick = {
                        onDismiss()
                        onPlayNext()
                    }
                )
            }
            onDeleteSongsNotInLibrary?.let { onDeleteSongsNotInLibrary ->
                ListMenu.Entry(
                    text = stringResource(R.string.playlist_action_remove_songs_outside_library),
                    icon = { SettingIcon(R.drawable.trash) },
                    onClick = {
                        onDismiss()
                        onDeleteSongsNotInLibrary()
                    }
                )
            }

            onEnqueue?.let { onEnqueue ->
                ListMenu.Entry(
                    text = stringResource(R.string.player_action_enqueue),
                    icon = { SettingIcon(R.drawable.enqueue) },
                    onClick = {
                        onDismiss()
                        onEnqueue()
                    }
                )
            }

            if (showOnSyncronize) onSyncronize?.let { onSyncronize ->
                ListMenu.Entry(
                    text = stringResource(R.string.sync_action_sync),
                    icon = { SettingIcon(R.drawable.sync) },
                    onClick = {
                        onDismiss()
                        onSyncronize()
                    }
                )
            }

            if (showLinkUnlink) onLinkUnlink?.let { onLinkUnlink ->
                ListMenu.Entry(
                    text = if (playlist?.playlist?.isYoutubePlaylist == true) stringResource(R.string.playlist_action_unlink_youtube_library) else stringResource(R.string.playlist_action_unlink_youtube),
                    icon = { SettingIcon(R.drawable.link) },
                    onClick = {
                        onDismiss()
                        onLinkUnlink()
                    }
                )
            }

            onImportOnlinePlaylist?.let { onImportOnlinePlaylist ->
                ListMenu.Entry(
                    text = stringResource(R.string.playlist_action_import),
                    icon = { SettingIcon(R.drawable.add_in_playlist) },
                    onClick = {
                        onDismiss()
                        onImportOnlinePlaylist()
                    }
                )
            }

            if (onAddToPreferites != null)
                ListMenu.Entry(
                    text = stringResource(R.string.library_favorites_action_add),
                    icon = { SettingIcon(R.drawable.heart) },
                    onClick = {
                        onDismiss()
                        onAddToPreferites()
                    }
                )

            if (showonAddToPreferitesYoutube) {
                if (onAddToPreferitesYoutube != null)
                    ListMenu.Entry(
                        text = stringResource(R.string.playlist_action_like_favorites_on_youtube),
                        icon = { SettingIcon(R.drawable.ytmusic) },
                        onClick = {
                            onDismiss()
                            onAddToPreferitesYoutube()
                        }
                    )
            }

            if (onOpenAddToPlaylist != null) {
                ListMenu.Entry(
                    text = stringResource(R.string.playlist_action_add),
                    icon = { SettingIcon(R.drawable.add_in_playlist) },
                    onClick = onOpenAddToPlaylist,
                    trailingContent = {
                        Image(
                            painter = painterResource(R.drawable.chevron_forward),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(
                                colorPalette().textSecondary
                            ),
                            modifier = Modifier
                                .size(16.dp)
                        )
                    }
                )
            }

            onRename?.let { onRename ->
                ListMenu.Entry(
                    text = stringResource(R.string.playlist_action_rename),
                    icon = { SettingIcon(R.drawable.title_edit) },
                    onClick = {
                        onDismiss()
                        onRename()
                    }
                )
            }

            onDelete?.let { onDelete ->
                ListMenu.Entry(
                    text = stringResource(R.string.common_action_delete),
                    icon = { SettingIcon(R.drawable.trash) },
                    onClick = {
                        onDismiss()
                        onDelete()
                    }
                )
            }

            onRenumberPositions?.let { onRenumberPositions ->
                ListMenu.Entry(
                    text = stringResource(R.string.playlist_action_shuffle_positions),
                    icon = { SettingIcon(R.drawable.position) },
                    onClick = {
                        onDismiss()
                        onRenumberPositions()
                    }
                )
            }

            if (showonListenToYT) onListenToYT?.let { onListenToYT ->
                ListMenu.Entry(
                    text = stringResource(R.string.player_action_listen_on_youtube),
                    icon = { SettingIcon(R.drawable.play) },
                    onClick = {
                        onDismiss()
                        onListenToYT()
                    }
                )
            }

            onExport?.let { onExport ->
                ListMenu.Entry(
                    text = stringResource(R.string.playlist_action_export),
                    icon = { SettingIcon(R.drawable.export_outline) },
                    onClick = {
                        onDismiss()
                        onExport()
                    }
                )
            }

            onImport?.let { onImport ->
                ListMenu.Entry(
                    text = stringResource(R.string.playlist_action_import),
                    icon = { SettingIcon(R.drawable.import_outline) },
                    onClick = {
                        onDismiss()
                        onImport()
                    }
                )
            }
            onImportFavorites?.let {
                ListMenu.Entry(
                    text = stringResource(R.string.playlist_action_import_favorites),
                    icon = { SettingIcon(R.drawable.import_outline) },
                    onClick = {
                        onDismiss()
                        onImportFavorites()
                    }
                )
            }

            onEditThumbnail?.let {
                ListMenu.Entry(
                    text = stringResource(R.string.thumbnail_action_edit),
                    icon = { SettingIcon(R.drawable.image) },
                    onClick = {
                        onDismiss()
                        onEditThumbnail()
                    }
                )
            }

            onResetThumbnail?.let {
                ListMenu.Entry(
                    text = stringResource(R.string.thumbnail_action_reset),
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
