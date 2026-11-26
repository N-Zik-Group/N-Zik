package app.n_zik.android.components.menu.player

import app.n_zik.android.components.menu.playlist.PlaylistPickerFeedback
import app.n_zik.android.components.menu.playlist.matchesPickerQuery
import app.n_zik.android.components.menu.playlist.menuItemCount
import app.n_zik.android.components.menu.playlist.rememberPlaylistPickerRead
import kotlinx.coroutines.flow.first


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.n_zik.android.ui.components.themed.PlaylistPickerLockIcon
import app.n_zik.android.ui.components.themed.PlaylistPickerEntry
import app.n_zik.android.ui.components.themed.PlaylistPickerMenu
import app.n_zik.android.ui.components.themed.PlaylistPickerSearchRows
import app.n_zik.android.ui.components.themed.SongInPlaylistIcon
import app.n_zik.android.ui.components.themed.playlistPickerSections
import app.n_zik.android.ui.components.themed.warnPlaylistPickerLocked
import app.n_zik.android.ui.components.themed.rememberSongPlaylistIds
import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.core.database.PlaylistAddResult
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.ui.components.themed.isPlaylistPickerTarget
import app.n_zik.android.ui.components.themed.reportPlaylistAddResult
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.navigation.LocalPlaylistPickerNavigation
import app.n_zik.android.core.navigation.navigateToPickedPlaylist
import app.n_zik.android.core.rewind.RewindPlaylists

import app.n_zik.android.context
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.utils.addPlaylistItemsFromMenu
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.normalizedSongId
import app.n_zik.android.utils.rememberEqualizerLauncher
import app.n_zik.android.utils.rememberPreference

import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.core.utils.Toaster
import timber.log.Timber
import java.util.UUID
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.models.Playlist
import app.n_zik.android.components.search.SearchHeaderVisibility
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.n_zik.android.components.dialog.playlist.NewPlaylistDialog
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.utils.semiBold
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.n_zik.android.enums.PlaylistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.utils.Preference
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.Alignment
import app.n_zik.android.MONTHLY_PREFIX
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.components.tab.Search
import androidx.compose.ui.unit.times

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

    val sortBy by rememberPreference(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
    val sortOrder by rememberPreference(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
    val playlistRead = rememberPlaylistPickerRead(sortBy, sortOrder)
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
    val (pinnedPlaylists, youtubePlaylists, unpinnedPlaylists) = playlistPickerSections(playlistPreviews, online)

    val search = Search()
    val style by rememberPreference(menuStyleKey, MenuStyle.List)
    PlaylistPickerMenu(
        style = style,
        playlistRead = playlistRead,
        query = search.inputValue,
        groups = listOf(
            R.string.playlist_pinned_title to pinnedPlaylists,
            R.string.sync_youtube_playlists_title to youtubePlaylists,
            R.string.library_playlists_title to unpinnedPlaylists,
        ),
        header = {
            val title = stringResource(R.string.library_playlists_title)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = { search.onShortClick() },
                    icon = R.drawable.search_circle,
                    contentDescription = stringResource(R.string.search_title),
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .size(24.dp)
                )
                BasicText(
                    text = title,
                    style = typography().m.semiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(
                    onClick = { newPlaylistDialog.onShortClick() },
                    icon = R.drawable.add_in_playlist,
                    contentDescription = stringResource(R.string.playlist_action_create),
                    color = colorPalette().accent,
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .size(24.dp)
                )
            }
            search.SearchBar(this)
        },
        heading = { label ->
            BasicText(
                text = stringResource(label),
                style = typography().m.semiBold,
                modifier = Modifier.padding(start = 20.dp, top = 5.dp),
            )
        },
        entry = { label, preview ->
            val playlist = preview.playlist
            PlaylistPickerEntry(
                style = style,
                preview = preview,
                icon = { SettingIcon(R.drawable.add_in_playlist) },
                onClick = { addSong(preview) },
                trailingContent = {
                    if (!isPlaylistPickerTarget(playlist, localDestinationsOnly)) PlaylistPickerLockIcon()
                    if (playlist.id in playlistIds) SongInPlaylistIcon()
                    if (label == R.string.playlist_pinned_title && playlist.isYoutubePlaylist) {
                        Image(
                            painter = painterResource(R.drawable.ytmusic),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.Red.copy(0.75f).compositeOver(Color.White)),
                            modifier = Modifier.size(18.dp),
                        )
                    }
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

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun AddToPlaylistArtistSongsMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    onAddToPlaylist: ((PlaylistPreview) -> Unit),
    onGoToPlaylist: ((Long) -> Unit)? = null,
    onRemoveFromPlaylist: ((Playlist) -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val screenHeight = configuration.screenHeightDp.dp

    val newPlaylistDialog = NewPlaylistDialog { playlist ->
        onDismiss()
        Database.asyncTransaction {
            val pId = playlist.id
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

    newPlaylistDialog.Render()

    val sortBy by rememberPreference(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
    val sortOrder by rememberPreference(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
    val playlistRead = rememberPlaylistPickerRead(sortBy, sortOrder)
    val playlistPreviews = playlistRead.previews

    val editablePreviews = playlistPreviews.filter { canEditPlaylistMembership(it.playlist) }
    val pinnedPlaylists = editablePreviews.filter {
        it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
                && if (isNetworkConnected(context)) !(it.playlist.isYoutubePlaylist && !it.playlist.isEditable) else !it.playlist.isYoutubePlaylist
    }

    val youtubePlaylists = editablePreviews.filter { it.playlist.isEditable && it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX) }

    val unpinnedPlaylists = editablePreviews.filter {
        !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                // Spec 2: generated rewind-* playlists are never add-to targets
                !RewindPlaylists.isRewind(it.playlist.name) &&
                !it.playlist.isYoutubePlaylist
    }

    ListMenu.Menu {
        val search = Search()
        val title = stringResource(R.string.library_playlists_title)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            IconButton(
                onClick = onDismiss,
                icon = R.drawable.chevron_back,
                color = colorPalette().accent,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(20.dp)
            )
            IconButton(
                onClick = { search.onShortClick() },
                icon = R.drawable.search_circle,
                contentDescription = stringResource(R.string.search_title),
                color = colorPalette().favoritesIcon,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(24.dp)
            )
            BasicText(
                text = title,
                style = typography().m.semiBold,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            IconButton(
                onClick = { newPlaylistDialog.onShortClick() },
                icon = R.drawable.add_in_playlist,
                contentDescription = stringResource(R.string.playlist_action_create),
                color = colorPalette().accent,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(24.dp)
            )
        }
        search.SearchBar(this)
        val filteredPinnedPlaylists = pinnedPlaylists.filter { it.playlist.matchesPickerQuery(search.inputValue) }
        val filteredYoutubePlaylists = youtubePlaylists.filter { it.playlist.matchesPickerQuery(search.inputValue) }
        val filteredUnpinnedPlaylists = unpinnedPlaylists.filter { it.playlist.matchesPickerQuery(search.inputValue) }

        val noPlaylistResults = filteredPinnedPlaylists.isEmpty() && filteredUnpinnedPlaylists.isEmpty() && (!isNetworkConnected(context) || filteredYoutubePlaylists.isEmpty())
        PlaylistPickerFeedback(playlistRead, search.inputValue, noPlaylistResults)
        if (pinnedPlaylists.isNotEmpty()) {
            SearchHeaderVisibility(filteredPinnedPlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.playlist_pinned_title),
                    style = typography().m.semiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 5.dp)
                )
            }

            onAddToPlaylist.let { onAddToPlaylist ->
                PlaylistPickerSearchRows(pinnedPlaylists, filteredPinnedPlaylists) { playlistPreview ->
                    ListMenu.Entry(
                        text = playlistPreview.playlist.cleanName(),
                        icon = { SettingIcon(R.drawable.add_in_playlist) },
                        subtitle = playlistPreview.menuItemCount(),
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

        if (youtubePlaylists.isNotEmpty() && isNetworkConnected(context)) {
            SearchHeaderVisibility(filteredYoutubePlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.sync_youtube_playlists_title),
                    style = typography().m.semiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 5.dp)
                )
            }

            onAddToPlaylist.let { onAddToPlaylist ->
                PlaylistPickerSearchRows(youtubePlaylists, filteredYoutubePlaylists) { playlistPreview ->
                    ListMenu.Entry(
                        text = playlistPreview.playlist.cleanName(),
                        icon = { SettingIcon(R.drawable.add_in_playlist) },
                        subtitle = playlistPreview.menuItemCount(),
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

        if (unpinnedPlaylists.isNotEmpty()) {
            SearchHeaderVisibility(filteredUnpinnedPlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.library_playlists_title),
                    style = typography().m.semiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 5.dp)
                )
            }

            onAddToPlaylist.let { onAddToPlaylist ->
                PlaylistPickerSearchRows(unpinnedPlaylists, filteredUnpinnedPlaylists) { playlistPreview ->
                    ListMenu.Entry(
                        text = playlistPreview.playlist.cleanName(),
                        icon = { SettingIcon(R.drawable.add_in_playlist) },
                        subtitle = playlistPreview.menuItemCount(),
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
}








