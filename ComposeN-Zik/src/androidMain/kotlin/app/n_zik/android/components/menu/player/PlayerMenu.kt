package app.n_zik.android.components.menu.player

import app.n_zik.android.core.database.*

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.n_zik.android.R
import app.n_zik.android.core.database.Database

import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.context
import app.it.fast4x.rimusic.enums.MenuStyle
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.addSongToYtPlaylist
import app.it.fast4x.rimusic.utils.addToYtPlaylist
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberEqualizerLauncher
import app.it.fast4x.rimusic.utils.rememberPreference

import app.it.fast4x.rimusic.utils.removeYTSongFromPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.UUID
import androidx.compose.foundation.text.BasicText
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.n_zik.android.components.dialog.playlist.NewPlaylistDialog
import app.n_zik.android.components.menu.ListMenu
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.utils.semiBold
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.utils.Preference
import androidx.compose.runtime.collectAsState
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.Alignment
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.n_zik.android.components.tab.Search
import androidx.compose.ui.unit.times
import app.n_zik.android.components.menu.player.PlayerItemMenu

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
    disableScrollingText: Boolean
    ) {
    
    val menuState = LocalMenuState.current
    val styleState = rememberPreference(menuStyleKey, MenuStyle.List)

    val menu = remember(mediaItem.mediaId, binder, menuState, styleState) {
        PlayerItemMenu.create(
            navController = navController,
            binder = binder,
            mediaItem = mediaItem,
            menuState = menuState,
            styleState = styleState,
            onDismiss = onDismiss,
            onClosePlayer = onClosePlayer,
            onShowSleepTimer = onShowSleepTimer
        )
    }
    
    menu.MenuComponent()
}


@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AddToPlaylistPlayerMenu(
    navController: NavController,
    binder: PlayerServiceModern.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    AddToPlaylistItemMenu(
        navController = navController,
        mediaItem = mediaItem,
        onGoToPlaylist = {
            onClosePlayer()
        },
        onAddToPlaylist = { playlist, position ->
            if (!isYouTubeSyncEnabled() || !playlist.isYoutubePlaylist){
                Database.asyncTransaction {
                    insertIgnore( mediaItem )
                    mapIgnore( playlist, mediaItem.asSong )
                }
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    addSongToYtPlaylist(playlist.id, position, playlist.browseId ?: "", mediaItem)
                }
            }

        },
        onRemoveFromPlaylist = { playlist ->
            Database.asyncTransaction {
                val position = songPlaylistMapTable.findPositionOf( mediaItem.mediaId, playlist.id )
                if( position == -1 ) return@asyncTransaction


            }
            if(isYouTubeSyncEnabled() && playlist.isYoutubePlaylist && playlist.isEditable) {
                Database.asyncTransaction {
                    coroutineScope.launch(Dispatchers.IO) {
                        if (removeYTSongFromPlaylist(
                                mediaItem.mediaId,
                                playlist.browseId ?: "",
                                playlist.id
                            )
                        )
                            songPlaylistMapTable.deleteBySongId( mediaItem.mediaId, playlist.id )

                    }
                }
            } else
                Database.asyncTransaction {
                    songPlaylistMapTable.deleteBySongId( mediaItem.mediaId, playlist.id )
                }
        },
        onDismiss = onDismiss,
    )
}

@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AddToPlaylistArtistSongs(
    navController: NavController,
    mediaItems: List<MediaItem>,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var position by remember {
        mutableIntStateOf(0)
    }
    AddToPlaylistArtistSongsMenu(
        navController = navController,
        onGoToPlaylist = {
            onClosePlayer()
        },
        onAddToPlaylist = { playlistPreview ->
            position = playlistPreview.songCount.minus(1)
            if (position > 0) position++ else position = 0

            Database.asyncTransaction {
                if ( !isYouTubeSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist )
                    mapIgnore( playlistPreview.playlist, *mediaItems.toTypedArray() )
                else
                    coroutineScope.launch(Dispatchers.IO) {
                        addToYtPlaylist(playlistPreview.playlist.id, position, playlistPreview.playlist.browseId ?: "", mediaItems)
                    }


            }

            onDismiss()
        },
        onDismiss = onDismiss,
    )
}




@UnstableApi
@ExperimentalAnimationApi
@Composable
fun AddToPlaylistItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    onAddToPlaylist: ((Playlist, Int) -> Unit),
    onRemoveFromPlaylist: ((Playlist) -> Unit),
    mediaItem: MediaItem,
    onGoToPlaylist: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val screenHeight = configuration.screenHeightDp.dp

    val newPlaylistDialog = NewPlaylistDialog { playlist ->
        onDismiss()
        onAddToPlaylist(playlist, 0)
    }

    newPlaylistDialog.Render()

    val sortBy by rememberPreference(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount)
    val sortOrder by rememberPreference(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending)
    val playlistPreviews by remember {
        Database.playlistTable.sortPreviews( sortBy, sortOrder )
    }.collectAsState( emptyList(), Dispatchers.IO )

    val playlistIds by remember {
        Database.songPlaylistMapTable.mappedTo( mediaItem.mediaId )
    }.collectAsState( emptyList(), Dispatchers.IO )

    val pinnedPlaylists = playlistPreviews.filter {
        it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
                && if (isNetworkConnected(context)) !(it.playlist.isYoutubePlaylist && !it.playlist.isEditable) else !it.playlist.isYoutubePlaylist
    }

    val youtubePlaylists = playlistPreviews.filter { it.playlist.isEditable && it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX) }

    val unpinnedPlaylists = playlistPreviews.filter {
        !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                !it.playlist.isYoutubePlaylist
    }

    ListMenu.Menu(title = stringResource(R.string.playlists)) {
        val search = Search()
        val title = stringResource(R.string.playlists)
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
                onClick = { search.isVisible = !search.isVisible },
                icon = R.drawable.search_circle,
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
                color = colorPalette().accent,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(24.dp)
            )
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
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                filteredPinnedPlaylists.forEach { playlistPreview ->
                    ListMenu.Entry(
                        text = cleanPrefix(playlistPreview.playlist.name),
                        icon = { SettingIcon(R.drawable.add_in_playlist) },
                        subtitle = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
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

            onAddToPlaylist.let { onAddToPlaylist ->
                filteredYoutubePlaylists.forEach { playlistPreview ->
                    ListMenu.Entry(
                        text = cleanPrefix(playlistPreview.playlist.name),
                        icon = { SettingIcon(R.drawable.add_in_playlist) },
                        subtitle = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
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
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                filteredUnpinnedPlaylists.forEach { playlistPreview ->
                    ListMenu.Entry(
                        text = cleanPrefix(playlistPreview.playlist.name),
                        icon = { SettingIcon(R.drawable.add_in_playlist) },
                        subtitle = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
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
    val playlistPreviews by remember {
        Database.playlistTable.sortPreviews( sortBy, sortOrder )
    }.collectAsState( emptyList(), Dispatchers.IO )

    val pinnedPlaylists = playlistPreviews.filter {
        it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
                && if (isNetworkConnected(context)) !(it.playlist.isYoutubePlaylist && !it.playlist.isEditable) else !it.playlist.isYoutubePlaylist
    }

    val youtubePlaylists = playlistPreviews.filter { it.playlist.isEditable && it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX) }

    val unpinnedPlaylists = playlistPreviews.filter {
        !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                !it.playlist.isYoutubePlaylist
    }

    ListMenu.Menu(title = stringResource(R.string.playlists)) {
        val search = Search()
        val title = stringResource(R.string.playlists)
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
                onClick = { search.isVisible = !search.isVisible },
                icon = R.drawable.search_circle,
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
                color = colorPalette().accent,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(24.dp)
            )
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
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                filteredPinnedPlaylists.forEach { playlistPreview ->
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

            onAddToPlaylist.let { onAddToPlaylist ->
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
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                filteredUnpinnedPlaylists.forEach { playlistPreview ->
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
    }
}








