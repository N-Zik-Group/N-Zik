package app.n_zik.android.components.ui.screens.home

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.HomeScreenTabs
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.toUiMood
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.homeScreenTabIndexKey
import app.it.fast4x.rimusic.utils.indexNavigationTabKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.utils.Toaster
import kotlin.system.exitProcess
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.ui.components.themed.Loader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.n_zik.android.components.ui.screens.home.quickpicks.HomeQuickPicks
import app.it.fast4x.rimusic.utils.homeTabsOrderKey
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog


@RequiresApi(Build.VERSION_CODES.O)
@ExperimentalMaterial3Api
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomeScreen(
    navController: NavController,
    onPlaylistUrl: (String) -> Unit,
    miniPlayer: @Composable () -> Unit = {},
    openTabFromShortcut: Int
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val preferences = LocalContext.current.preferences
    //val showSearchTab by rememberPreference(showSearchTabKey, false)
    //val showStatsInNavbar by rememberPreference(showStatsInNavbarKey, false)
    val enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)

//    PersistMapCleanup("home/")

    val openTabFromShortcut1 by remember{ mutableIntStateOf(openTabFromShortcut) }

    val enableSongsTab by rememberPreference("hometab_songs_enabled", true)
    val enableArtistsTab by rememberPreference("hometab_artists_enabled", true)
    val enableAlbumsTab by rememberPreference("hometab_albums_enabled", true)
    val enablePlaylistsTab by rememberPreference("hometab_playlists_enabled", true)

    val homeTabsOrderSerialized by rememberPreference(homeTabsOrderKey, "")
    val tabOrder = remember(homeTabsOrderSerialized) {
        HomeTabsSettingsDialog.parseOrder(homeTabsOrderSerialized)
    }

    val activeTabs = remember(tabOrder, enableQuickPicksPage, enableSongsTab, enableArtistsTab, enableAlbumsTab, enablePlaylistsTab) {
        val filtered = tabOrder.filter { id ->
            when (id) {
                "quickpicks" -> enableQuickPicksPage
                "songs" -> enableSongsTab
                "artists" -> enableArtistsTab
                "albums" -> enableAlbumsTab
                "playlists" -> enablePlaylistsTab
                else -> false
            }
        }
        if (filtered.isEmpty()) listOf("quickpicks") else filtered
    }

    val initialtabIndex = run {
        val targetIndex = when (openTabFromShortcut1) {
            -1 -> when (val pref = preferences.getEnum(indexNavigationTabKey, HomeScreenTabs.Default)) {
                HomeScreenTabs.Default -> HomeScreenTabs.QuickPics.index
                else -> pref.index
            }
            else -> openTabFromShortcut1
        }
        
        val targetTabId = when (targetIndex) {
            HomeScreenTabs.QuickPics.index -> "quickpicks"
            HomeScreenTabs.Songs.index -> "songs"
            HomeScreenTabs.Artists.index -> "artists"
            HomeScreenTabs.Albums.index -> "albums"
            HomeScreenTabs.Playlists.index -> "playlists"
            else -> "quickpicks"
        }
        
        val foundIndex = activeTabs.indexOf(targetTabId)
        if (foundIndex != -1) foundIndex else 0
    }

    var (tabIndex, onTabChanged) = rememberPreference(homeScreenTabIndexKey, initialtabIndex)

    // Check if services are ready
    val binder = LocalPlayerServiceBinder.current
    var isReady by remember { mutableStateOf(false) }
    
    LaunchedEffect(binder) {
        delay(100) // Small delay to ensure services are initialized
        isReady = true
    }

    if (tabIndex == -2) navController.navigate(NavRoutes.search.name)

    if (tabIndex >= activeTabs.size) tabIndex = 0

    // Show loader while services are not ready
    if (!isReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Loader()
        }
        return
    }

    Skeleton(
        navController,
        tabIndex,
        onTabChanged,
        miniPlayer,
        navBarContent = { Item ->
            activeTabs.forEachIndexed { index, id ->
                when (id) {
                    "quickpicks" -> Item(index, stringResource(R.string.quick_picks), R.drawable.sparkles)
                    "songs" -> Item(index, stringResource(R.string.songs), R.drawable.musical_notes)
                    "artists" -> Item(index, stringResource(R.string.artists), R.drawable.people)
                    "albums" -> Item(index, stringResource(R.string.albums), R.drawable.album)
                    "playlists" -> Item(index, stringResource(R.string.playlists), R.drawable.library)
                }
            }
        }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
            val currentTabId = activeTabs.getOrNull(currentTabIndex)
            when (currentTabId) {
                "quickpicks" -> HomeQuickPicks(
                    onAlbumClick = {
                        navController.navigate(route = "${NavRoutes.album.name}/$it")
                    },
                    onArtistClick = {
                        navController.navigate(route = "${NavRoutes.artist.name}/$it")
                    },
                    onPlaylistClick = {
                        navController.navigate(route = "${NavRoutes.playlist.name}/$it")
                    },
                    onSearchClick = {
                        navController.navigate(NavRoutes.search.name)
                    },
                    onMoodClick = { mood ->
                        navController.currentBackStackEntry?.savedStateHandle?.set(
                            "mood",
                            mood.toUiMood()
                        )
                        navController.navigate(NavRoutes.mood.name)
                    },
                    onChipClick = { chip ->
                        navController.currentBackStackEntry?.savedStateHandle?.set(
                            "mood",
                            chip.toUiMood()
                        )
                        navController.navigate(NavRoutes.mood.name)
                    },
                    onSettingsClick = {
                        navController.navigate(NavRoutes.settings.name)
                    },
                    navController = navController

                )

                "songs" -> HomeSongsScreen( navController )

                "artists" -> HomeArtists(
                    navController = navController,
                    onArtistClick = {
                        navController.navigate(route = "${NavRoutes.artist.name}/${it.id}")
                    },
                    onSearchClick = {
                        //searchRoute("")
                        navController.navigate(NavRoutes.search.name)
                    },
                    onSettingsClick = {
                        //settingsRoute()
                        navController.navigate(NavRoutes.settings.name)
                    }
                )

                "albums" -> HomeAlbums(
                    navController = navController,
                    onAlbumClick = {
                        //albumRoute(it.id)
                        navController.navigate(route = "${NavRoutes.album.name}/${it.id}")
                    },
                    onSearchClick = {
                        //searchRoute("")
                        navController.navigate(NavRoutes.search.name)
                    },
                    onSettingsClick = {
                        //settingsRoute()
                        navController.navigate(NavRoutes.settings.name)
                    }
                )

                "playlists" -> HomeLibrary(
                    navController = navController,
                    onPlaylistClick = {
                        //localPlaylistRoute(it.id)
                        navController.navigate(route = "${NavRoutes.localPlaylist.name}/${it.id}")
                    },
                    onSearchClick = {
                        //searchRoute("")
                        navController.navigate(NavRoutes.search.name)
                    },
                    onSettingsClick = {
                        //settingsRoute()
                        navController.navigate(NavRoutes.settings.name)
                    }

                )
            }
        }
    }

    // Exit app when user uses back
    val context = LocalContext.current
    var confirmCount by remember { mutableIntStateOf( 0 ) }
    BackHandler {
        // Prevent this from being applied when user is not on HomeScreen
        if( NavRoutes.home.isNotHere( navController ) )  {
            if ( navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED )
                navController.popBackStack()

            return@BackHandler
        }

        if( confirmCount == 0 ) {
            Toaster.i( R.string.press_once_again_to_exit )
            confirmCount++

            // Reset confirmCount after 5s
            CoroutineScope( Dispatchers.Default ).launch {
                delay( 5000L )
                confirmCount = 0
            }
        } else {
            val activity = context as? Activity
            activity?.finishAffinity()
            // Close app with exit 0 notify that no problem occurred
            exitProcess( 0 )
        }
    }
}



