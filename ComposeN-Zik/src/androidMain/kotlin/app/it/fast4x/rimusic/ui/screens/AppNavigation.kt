package app.it.fast4x.rimusic.ui.screens

import app.n_zik.android.core.database.*

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import app.it.fast4x.rimusic.ui.components.navigation.header.AppHeader
import app.kreate.android.themed.rimusic.screen.artist.ArtistAlbums
import app.kreate.android.themed.rimusic.screen.artist.ArtistPlaylists
import app.kreate.android.themed.rimusic.screen.artist.ArtistVideos
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT
import app.it.fast4x.rimusic.enums.StatisticsType
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.n_zik.android.components.ui.screens.easter.EasterScreen
import app.it.fast4x.rimusic.models.Mood
import app.it.fast4x.rimusic.models.SearchQuery
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.n_zik.android.components.musicbrainz.insights.AlbumInsightsScreen
import app.n_zik.android.components.musicbrainz.insights.ArtistInsightsScreen
import app.n_zik.android.components.ui.screens.album.AlbumScreen
import app.n_zik.android.components.ui.screens.artist.ArtistScreen
import app.it.fast4x.rimusic.ui.screens.history.HistoryScreen
import app.n_zik.android.components.ui.screens.home.HomeScreen
import app.it.fast4x.rimusic.ui.screens.localplaylist.LocalPlaylistScreen
import app.it.fast4x.rimusic.ui.screens.mood.MoodScreen
import app.it.fast4x.rimusic.ui.screens.mood.MoodsPageScreen
import app.it.fast4x.rimusic.ui.screens.newreleases.NewreleasesScreen
import app.it.fast4x.rimusic.ui.screens.player.Queue
import app.it.fast4x.rimusic.ui.screens.playlist.PlaylistScreen
import app.it.fast4x.rimusic.ui.screens.podcast.PodcastScreen
import app.it.fast4x.rimusic.ui.screens.search.SearchScreen
import app.it.fast4x.rimusic.ui.screens.searchresult.SearchResultScreen
import app.it.fast4x.rimusic.ui.screens.settings.SettingsScreen
import app.it.fast4x.rimusic.ui.screens.statistics.StatisticsScreen
import app.n_zik.android.components.ui.screens.rewind.RewindScreen
import app.it.fast4x.rimusic.utils.clearPreference
import app.it.fast4x.rimusic.utils.homeScreenTabIndexKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.transitionEffectKey
import app.it.fast4x.rimusic.utils.disableNavigationBackStackKey
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.ui.layout.layout
import timber.log.Timber
import app.n_zik.android.updater.ui.UpdateScreen
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import app.n_zik.android.LocalTopBarOffset
import app.n_zik.android.uiRoundnessShape
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.spring
import androidx.compose.runtime.DisposableEffect

fun NavHostController.navigateClean(route: String, context: Context) {
    val disableBackStack = context.preferences.getBoolean(disableNavigationBackStackKey, false)
    if (disableBackStack) {
        navigate(route) {
            popUpTo(NavRoutes.home.name) { saveState = false }
            launchSingleTop = true
        }
    } else {
        navigate(route)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@androidx.annotation.OptIn()
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalTextApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
)
@UnstableApi
@Composable
fun AppNavigation(
    navController: NavHostController,
    miniPlayer: @Composable () -> Unit = {},
    openTabFromShortcut: Int
) {
    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)

    @Composable
    fun modalBottomSheetPage(content: @Composable () -> Unit) {
        var showSheet by rememberSaveable { mutableStateOf(true) }
        
        CustomModalBottomSheet(
            showSheet = showSheet,
            onDismissRequest = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED)
                    navController.popBackStack()
            },
            containerColor = Color.Transparent,
            modifier = Modifier.statusBarsPadding(),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            shape = uiRoundnessShape()
        ) {
            content()
        }
    }

    // Clearing homeScreenTabIndex in opening app.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        clearPreference(context, homeScreenTabIndexKey)
    }

    val enterTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) =
        {
            when (transitionEffect) {
                TransitionEffect.None -> EnterTransition.None
                TransitionEffect.Expand -> scaleIn(animationSpec = tween(350), initialScale = 2.0f)
                TransitionEffect.Fade -> fadeIn(animationSpec = tween(350))
                TransitionEffect.Scale -> scaleIn(animationSpec = tween(350))
                TransitionEffect.SlideVertical -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up)
                TransitionEffect.SlideHorizontal -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left)
            }
        }
    val exitTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) =
        {
            when (transitionEffect) {
                TransitionEffect.None -> ExitTransition.None
                TransitionEffect.Expand -> scaleOut(animationSpec = tween(350), targetScale = 2.0f)
                TransitionEffect.Fade -> fadeOut(animationSpec = tween(350))
                TransitionEffect.Scale -> scaleOut(animationSpec = tween(350))
                TransitionEffect.SlideVertical -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up)
                TransitionEffect.SlideHorizontal -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
            }
        }

    val popEnterTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) =
        {
            when (transitionEffect) {
                TransitionEffect.None -> EnterTransition.None
                TransitionEffect.Expand -> scaleIn(animationSpec = tween(350), initialScale = 2.0f)
                TransitionEffect.Fade -> fadeIn(animationSpec = tween(350))
                TransitionEffect.Scale -> scaleIn(animationSpec = tween(350))
                TransitionEffect.SlideVertical -> EnterTransition.None
                TransitionEffect.SlideHorizontal -> EnterTransition.None
            }
        }

    val popExitTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) =
        {
            when (transitionEffect) {
                TransitionEffect.None -> ExitTransition.None
                TransitionEffect.Expand -> scaleOut(animationSpec = tween(350), targetScale = 2.0f)
                TransitionEffect.Fade -> fadeOut(animationSpec = tween(350))
                TransitionEffect.Scale -> scaleOut(animationSpec = tween(350))
                TransitionEffect.SlideVertical -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down)
                TransitionEffect.SlideHorizontal -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
            }
        }

    val disableBackStack by rememberPreference(disableNavigationBackStackKey, false)
    val currentEntry by navController.currentBackStackEntryAsState()
    val isHome = currentEntry?.destination?.route?.startsWith(NavRoutes.home.name) ?: true
    val isRewind = currentEntry?.destination?.route?.startsWith(NavRoutes.rewind.name) ?: false

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = LocalLifecycleOwner.current

    val backCallback = remember {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                navController.popBackStack(NavRoutes.home.name, inclusive = false)
            }
        }
    }
    
    backCallback.isEnabled = disableBackStack && !isHome

    LaunchedEffect(currentEntry, disableBackStack) {
        if (disableBackStack && !isHome) {
            backCallback.remove()
            backDispatcher?.addCallback(lifecycleOwner, backCallback)
        } else {
            backCallback.remove()
        }
    }

    DisposableEffect(lifecycleOwner, backDispatcher) {
        onDispose { backCallback.remove() }
    }

    // Phone in landscape on the Songs/Album/Artist/Library tabs: the app header is hidden until
    // the toggle button slides it in (and it slides out again on the next tap or scroll)
    val isBarlessScreen = app.it.fast4x.rimusic.utils.isLandscapeBarlessScreen()
    val hideTopBar = app.it.fast4x.rimusic.utils.hideBarsInLandscapeMobile()
    // Kept as State objects and only read while laying out: the slide moves the content every
    // frame, and reading it here in composition would recompose the whole nav graph each frame
    val headerProgress = animateFloatAsState(
        targetValue = if (hideTopBar) 0f else 1f,
        animationSpec = tween(app.it.fast4x.rimusic.utils.LANDSCAPE_BARS_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "landscapeHeaderProgress"
    )
    // Rewind is a full-screen deck: the header slides up out of the screen on a jelly spring
    // (and slides back in with the same bounce when leaving the deck)
    val rewindHeaderProgress = animateFloatAsState(
        targetValue = if (isRewind) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "rewindHeaderProgress"
    )
    val isBarlessScreenState = rememberUpdatedState(isBarlessScreen)
    // On these screens (and until the slide has finished) the toggle drives the header
    // position; everywhere else the header keeps following the scroll-hide offset
    val toggleDrivesHeader = remember {
        derivedStateOf { isBarlessScreenState.value || headerProgress.value < 1f }
    }

    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.safeDrawing.getTop(density)
    // A top nav bar is the first thing of every screen: when the header slides out it has to keep
    // going up by its own height, or it stays stuck under the status bar
    val topNavBarPx = if (NavigationBarPosition.Top.isCurrent()) {
        with(density) { TOP_NAV_BAR_HEIGHT.roundToPx() }
    } else 0
    // A rail on a side runs the full height of the content: once the header is gone it must not
    // climb into the status bar, or its top buttons cannot be reached
    val hasSideRail = NavigationBarPosition.Left.isCurrent() || NavigationBarPosition.Right.isCurrent()
    // The header is the 64dp bar plus the status bar inset it pads for
    val headerHeightPx = with(density) { 64.dp.roundToPx() } + statusBarTopPx
    val scrollTopBarOffset = LocalTopBarOffset.current
    val topBarOffsetState = remember(headerHeightPx, scrollTopBarOffset) {
        derivedStateOf {
            if (toggleDrivesHeader.value) -(1f - headerProgress.value) * headerHeightPx
            // Rewind slide is additive: 0 on every normal screen, -headerHeight on the deck
            else scrollTopBarOffset.value - (1f - rewindHeaderProgress.value) * headerHeightPx
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Always composed: when slid out it simply sits above the screen, which keeps the
        // slide a pure layout/draw animation instead of adding and removing the header
        topBar = {
            CompositionLocalProvider(LocalTopBarOffset provides topBarOffsetState) {
                AppHeader(navController).Draw()
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
    NavHost(
        modifier = Modifier.fillMaxSize()
            .layout { measurable, constraints ->
            val toggleDriven = toggleDrivesHeader.value
            val topPaddingPx = if (toggleDriven) headerHeightPx
                               else innerPadding.calculateTopPadding().roundToPx()
            val offsetPx = topBarOffsetState.value.toInt()
            // Header slid out: the content still stays clear of the status bar (always, with a side
            // rail), except for a top nav bar which leaves the screen with the header
            val minPaddingPx = if (toggleDriven || hasSideRail) statusBarTopPx else -topNavBarPx
            val effectivePadding = (topPaddingPx + offsetPx).coerceAtLeast(minPaddingPx)

            val placeable = measurable.measure(
                constraints.copy(
                    minHeight = (constraints.minHeight - effectivePadding).coerceAtLeast(0),
                    maxHeight = (constraints.maxHeight - effectivePadding).coerceAtLeast(0)
                )
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(0, effectivePadding)
            }
        },
        navController = navController,
        startDestination = NavRoutes.home.name,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val navigateToPlaylist =
            { browseId: String -> navController.navigateClean("${NavRoutes.playlist.name}/$browseId", context) }

        composable(route = NavRoutes.home.name) {
            HomeScreen(
                navController = navController,
                onPlaylistUrl = navigateToPlaylist,
                miniPlayer = miniPlayer,
                openTabFromShortcut = openTabFromShortcut
            )
        }

        composable(route = NavRoutes.gamePacman.name) {
            EasterScreen(navController = navController, imageResId = app.n_zik.android.R.drawable.easter1)
        }

        composable(route = NavRoutes.gameSnake.name) {
            EasterScreen(navController = navController, imageResId = app.n_zik.android.R.drawable.easter2)
        }

        composable(route = NavRoutes.queue.name) {
            modalBottomSheetPage {
                Queue(
                    navController = navController,
                    onDismiss = {},
                    onDiscoverClick = {}
                )
            }
        }

        composable(
            route = "${NavRoutes.artist.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            ArtistScreen(
                navController = navController,
                browseId = id,
                miniPlayer = miniPlayer,
            )
        }

        composable(
            route = "${NavRoutes.album.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            AlbumScreen(
                navController = navController,
                browseId = id,
                miniPlayer = miniPlayer,
            )
        }

        composable(
            route = "artistInsights/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            ArtistInsightsScreen(
                navController = navController,
                artistId = id
            )
        }

        composable(
            route = "albumInsights/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            AlbumInsightsScreen(
                navController = navController,
                albumId = id
            )
        }

        composable(
            route = "${NavRoutes.playlist.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = {
                        type = NavType.StringType
                    }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            val params = navBackStackEntry.arguments?.getString( "params" )
            PlaylistScreen(
                navController = navController,
                browseId = id,
                params = params,
                miniPlayer = miniPlayer,
            )
        }

        composable(
            route = "${NavRoutes.podcast.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            PodcastScreen(
                navController = navController,
                browseId = id,
                params = null,
                miniPlayer = miniPlayer,
            )
        }

        composable(
            route = "${NavRoutes.settings.name}?tab={tab}&focus={focus}",
            arguments = listOf(
                navArgument(
                    name = "tab",
                    builder = {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                ),
                navArgument(
                    name = "focus",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val tab = navBackStackEntry.arguments?.getInt("tab", 0) ?: 0
            val focus = navBackStackEntry.arguments?.getString("focus", "") ?: ""

            SettingsScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                initialTab = tab,
                focus = focus,
            )
        }

        composable(route = NavRoutes.statistics.name) {
            StatisticsScreen(
                navController = navController,
                statisticsType = StatisticsType.Today,
                miniPlayer = miniPlayer,
            )
        }

        composable(route = NavRoutes.rewind.name) {
            RewindScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        composable(route = NavRoutes.history.name) {
            HistoryScreen(
                navController = navController,
                miniPlayer = miniPlayer,

                )
        }

        composable(
            route = "${NavRoutes.search.name}?text={text}",
            arguments = listOf(
                navArgument(
                    name = "text",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val text = navBackStackEntry.arguments?.getString("text") ?: ""

            SearchScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                initialTextInput = text,
                onViewPlaylist = {},
                onSearch = { query ->
                    Timber.tag("AppNavigation").d("onSearch: $query")

                    navController.navigate(
                        route = "${NavRoutes.searchResults.name}/${Uri.encode( query )}",
                    )

                    if ( !context.preferences.getBoolean(pauseSearchHistoryKey, false) )
                        Database.asyncTransaction {
                            // Must ignore to prevent "UNIQUE constraint" exception
                            searchTable.insertIgnore( SearchQuery(query = query) )
                        }
                },
            )
        }

        composable(
            route = "${NavRoutes.searchResults.name}/{query}",
            arguments = listOf(
                navArgument(
                    name = "query",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val query = navBackStackEntry.arguments?.getString("query") ?: ""

            SearchResultScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                query = query,
                onSearchAgain = {}
            )
        }

        composable(
            route = "${NavRoutes.localPlaylist.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.LongType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getLong("id") ?: 0L

            LocalPlaylistScreen(
                navController = navController,
                playlistId = id,
                miniPlayer = miniPlayer
            )
        }

        composable(
            route = NavRoutes.mood.name,
        ) { navBackStackEntry ->
            val mood: Mood? = navController.previousBackStackEntry?.savedStateHandle?.get("mood")
            if (mood != null) {
                MoodScreen(
                    navController = navController,
                    mood = mood,
                    miniPlayer = miniPlayer,
                )
            }
        }

        composable(
            route = NavRoutes.moodsPage.name
        ) { navBackStackEntry ->
            MoodsPageScreen(
                navController = navController,
                miniPlayer = miniPlayer
            )
        }

        composable(
            route = NavRoutes.newAlbums.name
        ) { navBackStackEntry ->
            NewreleasesScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        composable(
            route = "${NavRoutes.artistAlbums.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id").orEmpty()
            val params = navBackStackEntry.arguments?.getString("params").orEmpty()

            ArtistAlbums( navController, id, params, miniPlayer )
        }

        composable(
            route = "${NavRoutes.artistVideos.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id").orEmpty()
            val params = navBackStackEntry.arguments?.getString("params").orEmpty()

            ArtistVideos( navController, id, params, miniPlayer )
        }

        composable(
            route = "${NavRoutes.artistPlaylists.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id").orEmpty()
            val params = navBackStackEntry.arguments?.getString("params").orEmpty()

            ArtistPlaylists( navController, id, params, miniPlayer )
        }

        composable(route = NavRoutes.updater.name) {
            UpdateScreen(navController = navController)
        }
    }
    } // end Scaffold
}





