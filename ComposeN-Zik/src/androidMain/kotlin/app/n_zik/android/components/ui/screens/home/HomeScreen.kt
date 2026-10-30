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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.HomeScreenTabs
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.it.fast4x.rimusic.models.toUiMood
import app.n_zik.android.components.navigation.rememberTopContentInsetProvider
import app.n_zik.android.components.navigation.Skeleton
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.homeScreenTabIndexKey
import app.it.fast4x.rimusic.utils.indexNavigationTabKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.transitionEffectKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import app.kreate.android.me.knighthat.utils.Toaster
import kotlin.system.exitProcess
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.ui.components.themed.Loader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.n_zik.android.components.ui.screens.home.quickpicks.HomeQuickPicks
import app.it.fast4x.rimusic.utils.homeTabsOrderKey
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.compose.ui.unit.dp

// Shared scope for fire-and-forget background work started from this screen (issue #606):
// SupervisorJob (via NzikDispatchers.fireAndForget) so one failure doesn't cancel
//
// unrelated future launches on the same scope.
internal val homeScreenScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)

/** Sentinel passed as [HomeScreen.openTabFromShortcut] when a launcher shortcut requests the search screen instead of a home tab (see MainActivity). */
internal const val OPEN_SEARCH_SHORTCUT = -2

/**
 * Home tab id for a [HomeScreenTabs] index, or null when [tabIndex] isn't a home tab (e.g. the
 * [OPEN_SEARCH_SHORTCUT] sentinel, or -1/"no shortcut pending").
 */
private fun tabIdForIndex(tabIndex: Int): String? = when (tabIndex) {
    HomeScreenTabs.QuickPics.index -> "quickpicks"
    HomeScreenTabs.Songs.index -> "songs"
    HomeScreenTabs.Artists.index -> "artists"
    HomeScreenTabs.Albums.index -> "albums"
    HomeScreenTabs.Playlists.index -> "playlists"
    else -> null
}

/**
 * Resolves the initial home tab index for a launcher shortcut request.
 *
 * The search sentinel is not a tab index: it falls back to the preferred tab,
 * the search screen itself being opened through navigation in [HomeScreen].
 */
internal fun resolveInitialTabIndex(
    openTabFromShortcut: Int,
    preferredTab: HomeScreenTabs,
    activeTabIds: List<String>
): Int {
    val targetIndex = when (openTabFromShortcut) {
        OPEN_SEARCH_SHORTCUT, -1 -> when (preferredTab) {
            HomeScreenTabs.Default -> HomeScreenTabs.QuickPics.index
            else -> preferredTab.index
        }
        else -> openTabFromShortcut
    }

    val targetTabId = tabIdForIndex(targetIndex) ?: run {
        Timber.tag("HomeScreen").w("Unknown tab index $targetIndex, falling back to quickpicks")
        "quickpicks"
    }

    val foundIndex = activeTabIds.indexOf(targetTabId)
    return if (foundIndex != -1) foundIndex else 0
}

/**
 * One-shot gate for the launcher search shortcut.
 *
 * True while the search sentinel is pending and the search screen has not
 * been opened yet for this launch.
 */
internal fun shouldOpenSearchFromShortcut(openTabFromShortcut: Int, alreadyConsumed: Boolean): Boolean =
    openTabFromShortcut == OPEN_SEARCH_SHORTCUT && !alreadyConsumed

/**
 * True when [openTabFromShortcut] names an actual home tab (not the search sentinel, not "no
 * shortcut pending") -- used to switch tabs live while [HomeScreen] is already composed, e.g. a
 * shortcut tapped while the app is already running (see MainActivity.shortcutIntentAction).
 * [resolveInitialTabIndex] only ever applies once, at HomeScreen's first composition (its input is
 * a `remember`ed snapshot), and never reacts to a later value on its own.
 */
internal fun shouldSwitchTabFromShortcut(openTabFromShortcut: Int): Boolean =
    openTabFromShortcut != -1 && openTabFromShortcut != OPEN_SEARCH_SHORTCUT

/**
 * Launch-intent action `MainActivity.onCreate` should treat as a pending launcher shortcut.
 *
 * A recreated or restored activity (theme change, settings import, process death) is handed the
 * original launch intent again but has already consumed it. Re-applying it would make the shortcut
 * effect pop the restored back stack to home and drop the screen the user was on.
 */
internal fun initialShortcutAction(action: String?, isRestoredInstance: Boolean): String? =
    if (isRestoredInstance) null else action

/**
 * The active home tab ids in the user's configured [order], filtered by the per-tab
 * enable flags.
 *
 * Home always shows at least one tab: when every tab is disabled it falls back to a
 * single quickpicks tab, so the result is never empty. The bottom nav bar is only
 * drawn with two or more buttons, which is why `appNavBarPresentForRoute` takes the
 * size of this list for the home route (issue
 * https://github.com/N-Zik-Group/N-Zik/issues/832).
 *
 * @param order The tab ids in display order, as parsed from `homeTabsOrderKey`.
 * @param offlineMode When true (full offline mode), the quickpicks tab is dropped
 *   unconditionally and the never-empty fallback is the first local tab.
 * @return The active tab ids, never empty.
 */
fun activeHomeTabIds(
    order: List<String>,
    quickPicks: Boolean,
    songs: Boolean,
    artists: Boolean,
    albums: Boolean,
    playlists: Boolean,
    offlineMode: Boolean = false
): List<String> {
    val filtered = order.filter { id ->
        when (id) {
            "quickpicks" -> quickPicks && !offlineMode
            "songs" -> songs
            "artists" -> artists
            "albums" -> albums
            "playlists" -> playlists
            else -> false
        }
    }
    return if (filtered.isEmpty()) if (offlineMode) listOf("songs") else listOf("quickpicks") else filtered
}

/**
 * Preserves the tab identity across a full offline flip (both directions): maps the
 * tab id under [tabIndex] in the pre-flip list onto its position in the post-flip
 * list (quickpicks leaves the list on mode-on, so it maps to 0; on mode-off the
 * restored quickpicks shifts every later tab by one — the same remap applies),
 * returning the target index only when it differs from [tabIndex] — null when the
 * index already lands correctly.
 */
internal fun forceSwitchHomeTabIndex(
    preFlipTabs: List<String>,
    postFlipTabs: List<String>,
    tabIndex: Int
): Int? {
    val preTabId = preFlipTabs.getOrNull(tabIndex) ?: return null
    val target = if (preTabId == "quickpicks") 0 else postFlipTabs.indexOf(preTabId)
    // the range guard is unreachable in practice (only quickpicks leaves the list)
    // but keeps a stale id from ever producing index -1
    return target.takeIf { it in 0 until postFlipTabs.size && it != tabIndex }
}

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
    val enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)

    // Snapshot of the sentinel at first composition: drives the initial tab index only.
    // The live `openTabFromShortcut` parameter drives the one-shot search navigation below.
    val openTabFromShortcut1 by remember{ mutableIntStateOf(openTabFromShortcut) }

    val enableSongsTab by rememberPreference("hometab_songs_enabled", true)
    val enableArtistsTab by rememberPreference("hometab_artists_enabled", true)
    val enableAlbumsTab by rememberPreference("hometab_albums_enabled", true)
    val enablePlaylistsTab by rememberPreference("hometab_playlists_enabled", true)

    // Full offline mode (spec-full-offline-mode): the quickpicks tab leaves the nav
    // bar; if it was the active tab when the mode turned on, force-switch to the
    // first local tab so the user is never left on a removed surface.
    val offlineMode by OfflineMode.rememberEnabled()

    val homeTabsOrderSerialized by rememberPreference(homeTabsOrderKey, "")
    val tabOrder = remember(homeTabsOrderSerialized) {
        HomeTabsSettingsDialog.parseOrder(homeTabsOrderSerialized)
    }

    val activeTabs = remember(tabOrder, enableQuickPicksPage, enableSongsTab, enableArtistsTab, enableAlbumsTab, enablePlaylistsTab, offlineMode) {
        activeHomeTabIds(tabOrder, enableQuickPicksPage, enableSongsTab, enableArtistsTab, enableAlbumsTab, enablePlaylistsTab, offlineMode)
    }

    val initialtabIndex = run {
        resolveInitialTabIndex(
            openTabFromShortcut1,
            preferences.getEnum(indexNavigationTabKey, HomeScreenTabs.Default),
            activeTabs
        )
    }

    var (tabIndex, onTabChanged) = rememberPreference(homeScreenTabIndexKey, initialtabIndex)

    // Check if services are ready
    val binder = LocalPlayerServiceBinder.current
    val isReady by rememberHomeEntranceReady(binder)

    // The search shortcut is not a tab: open the search screen on top of the home tab.
    // `searchShortcutConsumed` only guards against the *same* sentinel occurrence re-firing
//
    // across an unrelated recomposition while it's still pending (e.g. MainActivity's own reset
//
    // effect hasn't applied yet) -- it resets as soon as the sentinel moves on, so a shortcut
    // tapped again later (app already running) is a fresh occurrence and opens search again.
    var searchShortcutConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openTabFromShortcut) {
        if (openTabFromShortcut != OPEN_SEARCH_SHORTCUT) {
            searchShortcutConsumed = false
        } else if (shouldOpenSearchFromShortcut(openTabFromShortcut, searchShortcutConsumed)) {
            searchShortcutConsumed = true
            navController.navigate(NavRoutes.search.name)
        }
    }

    // Tab shortcuts (songs/albums/artists/library) only drove the tab index once, via the
    // `openTabFromShortcut1` snapshot above -- a shortcut tapped while the app (and this
//
    // composable) is already running never switched tabs, unlike search's dedicated effect.
    // Redundant on the very first composition (the snapshot already applied the same value); a
//
    // no-op once the sentinel is consumed back to -1 by MainActivity.
    LaunchedEffect(openTabFromShortcut) {
        if (shouldSwitchTabFromShortcut(openTabFromShortcut)) {
            tabIdForIndex(openTabFromShortcut)?.let { targetTabId ->
                val foundIndex = activeTabs.indexOf(targetTabId)
                if (foundIndex != -1) onTabChanged(foundIndex)
            }
        }
    }

    // Full offline mode (spec-full-offline-mode): quickpicks leaves the list on mode-on
    // and re-enters it on mode-off — in both directions the tabs after it shift one
    // position, so preserve the tab identity across the flip (quickpicks maps to the
    // first local tab). The previous-mode guard keeps the effect from acting on the
    // initial composition, where no flip has happened yet.
    var previousOfflineMode by remember { mutableStateOf(offlineMode) }
    LaunchedEffect(offlineMode) {
        if (previousOfflineMode == offlineMode) return@LaunchedEffect
        val preFlipTabs = activeHomeTabIds(
            tabOrder,
            enableQuickPicksPage,
            enableSongsTab,
            enableArtistsTab,
            enableAlbumsTab,
            enablePlaylistsTab,
            previousOfflineMode
        )
        forceSwitchHomeTabIndex(preFlipTabs, activeTabs, tabIndex)?.let { onTabChanged(it) }
        previousOfflineMode = offlineMode
    }

    if (tabIndex >= activeTabs.size) tabIndex = 0

    val topContentInset = rememberTopContentInsetProvider(
        hasNavBar = activeTabs.size >= 2,
    )

    val homeScrollInset = rememberTopContentInsetProvider(
        hasNavBar = activeTabs.size >= 2, stableHomeScroll = true,
    )

    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    val contentAlpha = rememberHomeEntranceAlpha(isReady, transitionEffect != TransitionEffect.None)

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

    val shareHomeGlass = LocalSharedHomeHeaderGlass.current != null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha.value }
    ) {
        Skeleton(
            navController,
            tabIndex,
            onTabChanged,
            miniPlayer,
            topInsetHandledByContent = true,
            topContentInsetProvider = topContentInset,
            suppressTabTransition = { from, to ->
                shareHomeGlass &&
                    activeTabs.getOrNull(from) in listOf("songs", "artists", "albums", "playlists") &&
                    activeTabs.getOrNull(to) in listOf("songs", "artists", "albums", "playlists")
            },
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

                LaunchedEffect(currentTabId) {
                    app.n_zik.android.extensions.discord.DiscordUiState.currentHomeTab.value = currentTabId
                }

                when (currentTabId) {
                    "quickpicks" -> HomeQuickPicks(
                        topContentInsetProvider = topContentInset,
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

                    "songs" -> HomeSongsScreen(navController, topContentInsetProvider = topContentInset, scrollContentInsetProvider = homeScrollInset)

                    "artists" -> HomeArtists(
                        navController = navController,
                        topContentInsetProvider = topContentInset,
                        scrollContentInsetProvider = homeScrollInset,
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
                        topContentInsetProvider = topContentInset,
                        scrollContentInsetProvider = homeScrollInset,
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
                        topContentInsetProvider = topContentInset,
                        scrollContentInsetProvider = homeScrollInset,
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
    }

    // Exit app when user uses back
    val context = LocalContext.current
    var confirmCount by remember { mutableIntStateOf( 0 ) }
    val currentBackEntry by navController.currentBackStackEntryAsState()
    // Outgoing Home remains composed during transitions; it must not intercept another page's gesture.
    BackHandler(enabled = currentBackEntry?.destination?.route?.startsWith(NavRoutes.home.name) == true) {
        if( confirmCount == 0 ) {
            Toaster.i( R.string.press_once_again_to_exit )
            confirmCount++

            // Reset confirmCount after 5s
            homeScreenScope.launch {
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



