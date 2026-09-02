package app.n_zik.android.components.ui.screens.home.onDevice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.Preference.HOME_ON_DEVICE_SONGS_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER
import app.it.fast4x.rimusic.utils.homeSongsOnDeviceSortMenuOrderKey
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.isAtLeastAndroid13
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showFoldersOnDeviceKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import app.n_zik.android.components.AppPullToRefreshBox
import app.n_zik.android.components.FolderItem
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.Sort
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Search
import app.kreate.android.me.knighthat.utils.PathUtils
import app.kreate.android.me.knighthat.utils.Toaster
import app.kreate.android.me.knighthat.utils.getLocalSongs

@UnstableApi
@ExperimentalFoundationApi
@Composable
fun OnDeviceSong(
    navController: NavController,
    lazyListState: LazyListState,
    itemSelector: ItemSelector<Song>,
    search: Search,
    buttons: MutableList<Button>,
    itemsOnDisplay: MutableList<Song>,
    getSongs: () -> List<Song>
) {
    // Essentials
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current

    //<editor-fold defaultstate="collapsed" desc="Settings">
    val parentalControlEnabled by rememberPreference( parentalControlEnabledKey, false )
    val showFolder4LocalSongs by rememberPreference( showFoldersOnDeviceKey, true )
    //</editor-fold>

    var songsOnDevice by remember {
        mutableStateOf( emptyMap<Song, String>() )
    }
    var currentPath by remember {
        mutableStateOf("")
    }
    var refreshKey by remember { mutableStateOf(0) }

    //<editor-fold defaultstate="collapsed" desc="Permission handler">
    val permission = rememberSaveable {
        if( isAtLeastAndroid13 ) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var isPermissionGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission( context, permission ) == PackageManager.PERMISSION_GRANTED
    ) }

    /**
     * Opens a prompt saying that a permission (should be either [Manifest.permission.READ_MEDIA_AUDIO] or [Manifest.permission.READ_EXTERNAL_STORAGE])
     * Then apply result of that prompt.
     */
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isPermissionGranted = it }

    /**
     * Starts new activity (should be [Settings.ACTION_APPLICATION_DETAILS_SETTINGS]).
     * Then wait until user exits the activity, check for permission changes.
     */
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isPermissionGranted = ContextCompat.checkSelfPermission( context, permission ) == PackageManager.PERMISSION_GRANTED
    }
    //</editor-fold>

    val odSort = Sort( HOME_ON_DEVICE_SONGS_SORT_BY, HOME_ON_DEVICE_SONGS_SORT_ORDER, homeSongsOnDeviceSortMenuOrderKey, "dev")

    LaunchedEffect( isPermissionGranted, odSort.sortBy, odSort.sortOrder, refreshKey ) {
        if( !isPermissionGranted ) return@LaunchedEffect

        context.getLocalSongs( odSort.sortBy, odSort.sortOrder )
               .distinctUntilChanged()
               .onEach { lazyListState.scrollToItem( 0, 0 ) }
               .collect {
                   songsOnDevice = it
                   currentPath = PathUtils.findCommonPath( it.values )
               }
    }
    LaunchedEffect( songsOnDevice, search.inputValue, currentPath ) {
        songsOnDevice.keys.filter { !parentalControlEnabled || !it.title.startsWith( EXPLICIT_PREFIX, true ) }
                          .filter {
                              // [showFolder4LocalSongs] must be false and
                              // this song must be inside [currentPath] to show song
                              !showFolder4LocalSongs
                                      || currentPath.equals( songsOnDevice[it], true )
                                      || "$currentPath/".equals( songsOnDevice[it], true )
                          }
                          .filter {
                              // Without cleaning, user can search explicit songs with "e:"
                              // I kinda want this to be a feature, but it seems unnecessary
                              val containsTitle = it.cleanTitle().contains( search.inputValue, true )
                              val containsArtist = it.cleanArtistsText().contains( search.inputValue, true )

                              containsTitle || containsArtist
                          }
                          .let { newItems ->
                              androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                                  itemsOnDisplay.clear()
                                  itemsOnDisplay.addAll( newItems )
                              }
                          }
    }
    LaunchedEffect( Unit ) {
        if( !isPermissionGranted )
            try {
                permissionLauncher.launch( permission )
            } catch ( e: Exception ) {
                e.message?.let( Toaster::e )
            }
    }



    AppPullToRefreshBox(
        isRefreshing = false,
        onRefresh = { refreshKey++ },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        state = lazyListState,
        userScrollEnabled = songsOnDevice.isNotEmpty(),
        contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
    ) {


        if( !isPermissionGranted ) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillParentMaxSize()
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            tint = colorPalette().textDisabled,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize( .4f )
                        )

                        BasicText(
                            text = stringResource( R.string.media_permission_required_please_grant ),
                            style = typography().m.copy( color = colorPalette().textDisabled )
                        )

                        Spacer( Modifier.height( 20.dp ) )

                        Button(
                            border = BorderStroke( 2.dp, colorPalette().accent ),
                            colors = ButtonDefaults.buttonColors().copy( containerColor = Color.Transparent ),
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null )
                                    }
                                    settingsLauncher.launch( intent )
                                } catch ( e: Exception ) {
                                    e.message?.let( Toaster::e )
                                }
                            }
                        ) {
                            BasicText(
                                text = stringResource( R.string.open_permission_settings ),
                                style = typography().l.bold.copy( color = colorPalette().accent )
                            )
                        }
                    }
                }
            }
        } else {
            if( showFolder4LocalSongs && songsOnDevice.isNotEmpty() ) {
                item( "folder_paths" ) {
                    PathUtils.AddressBar(
                        paths = songsOnDevice.values,
                        currentPath = currentPath,
                        onSpecificAddressClick = { currentPath = it }
                    )
                }

            items(
                items = PathUtils.getAvailablePaths( songsOnDevice.values, currentPath ),
                key = { it }
            ) {
                FolderItem( it ) { currentPath += "/$it" }
            }
        }

        val folders = if (showFolder4LocalSongs && songsOnDevice.isNotEmpty()) {
            PathUtils.getAvailablePaths( songsOnDevice.values, currentPath )
        } else emptyList()

        if (isPermissionGranted && itemsOnDisplay.isEmpty() && folders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = stringResource(R.string.no_items),
                        style = typography().m.semiBold.copy(
                            color = colorPalette().textSecondary
                        )
                    )
                }
            }
        }

        itemsIndexed(
            items = itemsOnDisplay,
            key = { _, song -> song.id }
        ) { index, song ->
            val mediaItem = song.asMediaItem

            SwipeablePlaylistItem(
                mediaItem = mediaItem,
                onPlayNext = { binder?.player?.addNext( mediaItem ) },
                onEnqueue = {
                    binder?.player?.enqueue(mediaItem)
                }
            ) {
                SongItem(
                    song = song,
                    itemSelector = itemSelector,
                    navController = navController,
                    modifier = Modifier.animateItem(),

                    onClick = {
                        search.hideIfEmpty()

                            val mediaItems = getSongs().fastMap( Song::asMediaItem )
                            binder?.player?.forcePlayAtIndex( mediaItems, index )
                        }
                    )
                }
            }
        }
    }
    }
}

