package app.n_zik.android.components.menu.player

import app.n_zik.android.components.menu.playlist.rememberPlaylistPickerRead


import app.n_zik.android.ui.components.themed.PlaylistPickerLockIcon
import app.n_zik.android.ui.components.themed.PlaylistPickerEntry
import app.n_zik.android.ui.components.themed.PlaylistPickerMenu
import app.n_zik.android.ui.components.themed.PlaylistPickerHeader
import app.n_zik.android.ui.components.themed.SongInPlaylistIcon
import app.n_zik.android.ui.components.themed.playlistPickerSections
import app.n_zik.android.ui.components.themed.warnPlaylistPickerLocked
import app.n_zik.android.ui.components.themed.rememberSongPlaylistIds
import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.core.database.PlaylistAddResult
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.ui.components.themed.isPlaylistPickerTarget
import app.n_zik.android.ui.components.themed.PlaylistPickerOrigin
import app.n_zik.android.ui.components.themed.reportPlaylistAddResult
import app.n_zik.android.R
import app.n_zik.android.core.navigation.LocalPlaylistPickerNavigation
import app.n_zik.android.core.navigation.navigateToPickedPlaylist

import app.n_zik.android.context
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.utils.addPlaylistItemsFromMenu
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.normalizedSongId
import app.n_zik.android.utils.rememberPreference

import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.core.utils.Toaster
import timber.log.Timber
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.n_zik.android.components.dialog.playlist.NewPlaylistDialog
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.menuSectionHeading
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.utils.semiBold
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import app.n_zik.android.components.tab.Search

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

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun PlayerMenu(
    navController: NavController,
    binder: PlayerServiceModern.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit,
    onMatchingSong: (() -> Unit)? = null,
    onShowSleepTimer: () -> Unit,
    disableScrollingText: Boolean,
    statsMenuControl: State<PlayerStatsMenuControl?>? = null,
    ) {
    
    val menuState = LocalMenuState.current
    val styleState = rememberPreference(menuStyleKey, MenuStyle.List)

    val menu = remember(mediaItem.mediaId, binder, menuState, styleState, statsMenuControl) {
        PlayerItemMenu.create(
            navController = navController,
            binder = binder,
            mediaItem = mediaItem,
            menuState = menuState,
            styleState = styleState,
            onDismiss = onDismiss,
            onClosePlayer = onClosePlayer,
            onShowSleepTimer = onShowSleepTimer,
            statsMenuControl = statsMenuControl,
        )
    }
    
    menu.MenuComponent()
}


@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AddToPlaylistPlayerMenu(
    navController: NavController? = null,
    binder: PlayerServiceModern.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit,
) {
    val menuState = LocalMenuState.current
    AddToPlaylistItemMenu(
        navController = navController,
        mediaItem = mediaItem,
        onGoToPlaylist = { onClosePlayer() },
        onAddToPlaylist = add@{ playlist, _ ->
            val localDestinationsOnly = mediaItem.isLocal
            if (!isPlaylistPickerTarget(playlist, localDestinationsOnly)) {
                warnPlaylistPickerLocked()
                return@add
            }

            val menuKey = menuState.transitionKey
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                val result = try {
                    addPlaylistItemsFromMenu(playlist, listOf(mediaItem), localDestinationsOnly)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("PlayerMenu").e(error, "Could not add the current track to its playlist")
                    Toaster.e(R.string.common_load_failed)
                    return@launch
                } ?: return@launch
                withContext(NzikDispatchers.UI) {
                    reportPlaylistAddResult(result, localDestinationsOnly)
                    if (result == PlaylistAddResult.Added && menuState.isCurrentMenu(menuKey)) onDismiss()
                }
            }
        },
        onDismiss = onDismiss,
    )
}

@UnstableApi
@ExperimentalAnimationApi
@Composable
fun AddToPlaylistItemMenu(
    navController: NavController?,
    onDismiss: () -> Unit,
    onAddToPlaylist: ((Playlist, Int) -> Unit),
    mediaItem: MediaItem,
    onGoToPlaylist: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val localDestinationsOnly = mediaItem.isLocal
    val openPlaylist: ((Long) -> Unit)? = navController?.let { controller ->
        { id: Long ->
            navigateToPickedPlaylist(controller, id)
            onGoToPlaylist?.invoke(id)
        }
    }

    val newPlaylistDialog = NewPlaylistDialog(localOnly = localDestinationsOnly) { playlist ->
        onAddToPlaylist(playlist, 0)
    }

    newPlaylistDialog.Render()

    val playlistRead = rememberPlaylistPickerRead()
    val playlistPreviews = playlistRead.previews

    val playlistIds = rememberSongPlaylistIds(mediaItem.normalizedSongId)

    fun addSong(preview: PlaylistPreview) {
        if (!isPlaylistPickerTarget(preview.playlist, localDestinationsOnly)) {
            warnPlaylistPickerLocked()
        } else if (preview.playlist.id in playlistIds) {
            Toaster.i(R.string.playlist_contains_song)
        } else {
            onAddToPlaylist(preview.playlist, preview.songCount)
        }
    }

    val online = isNetworkConnected(context)
    val sections = playlistPickerSections(playlistPreviews, online, playlistRead.devicePlaylistIds)

    val search = Search()
    val style by rememberPreference(menuStyleKey, MenuStyle.List)
    PlaylistPickerMenu(
        style = style,
        playlistRead = playlistRead,
        search = search,
        groups = sections.groups(),
        header = {
            val menuState = LocalMenuState.current
            PlaylistPickerHeader(
                title = stringResource(R.string.playlist_action_add),
                search = search,
                onBack = if (menuState.hasPrevious) menuState::pop else null,
                onCreate = newPlaylistDialog::onShortClick,
            )
        },
        heading = { label ->
            BasicText(
                text = stringResource(label),
                style = typography().m.semiBold,
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 5.dp)
                    .menuSectionHeading(),
            )
        },
        entry = { _, preview ->
            val playlist = preview.playlist
            PlaylistPickerEntry(
                style = style,
                preview = preview,
                icon = { SettingIcon(R.drawable.add_in_playlist) },
                onClick = { addSong(preview) },
                trailingContent = {
                    if (!isPlaylistPickerTarget(playlist, localDestinationsOnly)) PlaylistPickerLockIcon()
                    if (playlist.id in playlistIds) SongInPlaylistIcon()
                    PlaylistPickerOrigin(playlist, playlist.id in playlistRead.devicePlaylistIds)
                    PlaylistPickerOpenButton(playlist.id, onDismiss, openPlaylist)
                },
            )
        },
    )
}

@Composable
internal fun PlaylistPickerOpenButton(
    playlistId: Long,
    onDismiss: () -> Unit,
    openPlaylist: ((Long) -> Unit)? = null,
) {
    val navigate = openPlaylist ?: LocalPlaylistPickerNavigation.current ?: return
    IconButton(
        icon = R.drawable.open,
        color = colorPalette().accent,
        contentDescription = stringResource(R.string.playlist_action_open),
        onClick = {
            navigate(playlistId)
            onDismiss()
        },
        modifier = Modifier.size(24.dp),
    )
}
