package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.LandscapeBars
import app.it.fast4x.rimusic.utils.lastBuildTypeKey
import app.it.fast4x.rimusic.utils.lastVersionCodeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.seenChangelogsVersionKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.BuildConfig
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.navigation.dock.FloatingDockEnvironment
import app.n_zik.android.components.navigation.dock.FloatingDockState
import app.n_zik.android.components.navigation.dock.LocalFloatingDock
import app.n_zik.android.extensions.discord.DiscordUiState
import app.n_zik.android.updater.models.CheckUpdateState
import app.n_zik.android.updater.models.MajorUpdateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationChromeBackTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetLandscapeState() {
        LandscapeBars.hide()
        DiscordUiState.currentRoute.value = null
        DiscordUiState.currentHomeTab.value = null
    }

    @Test fun `native top preserves outgoing and returning padding`() = verify(NavigationBarPosition.Top, false)
    @Test fun `glass top preserves outgoing and returning padding`() = verify(NavigationBarPosition.Top, true)
    @Test fun `native bottom preserves outgoing and returning padding`() = verify(NavigationBarPosition.Bottom, false)
    @Test fun `glass bottom preserves outgoing and returning padding`() = verify(NavigationBarPosition.Bottom, true)
    @Test fun `native floating preserves outgoing and returning padding`() = verify(NavigationBarPosition.BottomFloating, false)
    @Test fun `glass floating preserves outgoing and returning padding`() = verify(NavigationBarPosition.BottomFloating, true)
    @Test fun `native left preserves outgoing and returning padding`() = verify(NavigationBarPosition.Left, false)
    @Test fun `glass left preserves outgoing and returning padding`() = verify(NavigationBarPosition.Left, true)
    @Test fun `native right preserves outgoing and returning padding`() = verify(NavigationBarPosition.Right, false)
    @Test fun `glass right preserves outgoing and returning padding`() = verify(NavigationBarPosition.Right, true)

    @Test
    @Config(qualifiers = "sw400dp-w800dp-h400dp-land-mdpi")
    fun `native landscape top preserves outgoing detail padding`() = verify(NavigationBarPosition.Top, false, true)

    @Test
    @Config(qualifiers = "sw400dp-w800dp-h400dp-land-mdpi")
    fun `glass landscape top preserves outgoing detail padding`() = verify(NavigationBarPosition.Top, true, true)

    @Test
    @Config(qualifiers = "sw400dp-w800dp-h400dp-land-mdpi")
    fun `native landscape bottom preserves outgoing detail padding`() = verify(NavigationBarPosition.Bottom, false, true)

    @Test
    @Config(qualifiers = "sw400dp-w800dp-h400dp-land-mdpi")
    fun `glass landscape bottom preserves outgoing detail padding`() = verify(NavigationBarPosition.Bottom, true, true)

    private data class Insets(val top: Dp, val bottom: Dp, val left: Dp, val right: Dp, val playerLeft: Int, val playerRight: Int)
    private data class Geometry(val viewport: Rect, val firstRow: Rect, val insets: Insets)

    private fun verify(position: NavigationBarPosition, glass: Boolean, landscape: Boolean = false) {
        LandscapeBars.hide()
        DiscordUiState.currentRoute.value = "home"
        DiscordUiState.currentHomeTab.value = "songs"
        val context = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("destination_chrome_${position.name}_$glass", context)
        context.preferences.edit().clear()
            .putString(navigationBarPositionKey, position.name)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name)
            .putBoolean(glassEnabledKey, glass)
            .putString(checkUpdateStateKey, CheckUpdateState.Disabled.name)
            .putInt(lastVersionCodeKey, BuildConfig.VERSION_CODE)
            .putString(lastBuildTypeKey, MajorUpdateConfig.getCurrentBuildType())
            .putString(seenChangelogsVersionKey, BuildConfig.VERSION_NAME).commit()
        val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, CircleShape, CircleShape, CircleShape)
        val header = mutableFloatStateOf(if (position.isHorizontal) 88f else 24f)
        val initialHeader = header.floatValue
        val sceneWidth = if (landscape) 720.dp else 360.dp
        val sceneHeight = if (landscape) 320.dp else 600.dp
        val routes = listOf("home", "album", "settings", "playlist", "easterOne") +
            if (landscape) listOf("artist", "search") else emptyList()
        val observedInsets = mutableMapOf<String, Insets>()
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            val currentEntry = controller.currentBackStackEntryAsState().value
            SideEffect { DiscordUiState.currentRoute.value = currentEntry?.destination?.route }
            val state = remember { FloatingDockState() }
            val progress = remember { mutableFloatStateOf(0f) }
            val rail = remember { SideRailState() }
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalAppearance provides appearance,
                LocalSideRail provides rail,
                LocalFloatingDock provides FloatingDockEnvironment(state, currentEntry?.id, true, progress, true),
                LocalPlayerAwareWindowInsets provides WindowInsets(left = 7.dp, right = 11.dp, bottom = 80.dp),
                LocalAppHeaderContentInset provides { header.floatValue.dp },
                LocalAppHeaderOverlayInset provides { header.floatValue.dp },
            ) {
                Box(Modifier.size(sceneWidth, sceneHeight)) {
                    NavHost(controller, "home", enterTransition = { fadeIn(tween(160)) },
                        exitTransition = { fadeOut(tween(160)) }, popEnterTransition = { fadeIn(tween(160)) },
                        popExitTransition = { fadeOut(tween(160)) }) {
                        routes.forEach { route ->
                            chromeComposable(route) {
                                if (route == "home" || route == "playlist" || route == "artist" || route == "search") {
                                    val topInset = rememberTopContentInsetProvider(hasNavBar = route != "playlist")
                                    Skeleton(controller, topInsetHandledByContent = true, topContentInsetProvider = topInset,
                                        navBarContent = { item ->
                                            item(0, "Songs", R.drawable.musical_notes)
                                            if (route != "playlist") item(1, "Albums", R.drawable.album)
                                        }) {
                                        Rows(route, topInset, observedInsets)
                                    }
                                } else Rows(route, null, observedInsets)
                            }
                        }
                    }
                }
            }
        }

        fun geometry(route: String): Geometry? {
            val lists = compose.onAllNodesWithTag("list-$route").fetchSemanticsNodes()
            if (lists.isEmpty()) return null
            val row = compose.onNodeWithTag("first-$route").fetchSemanticsNode().boundsInRoot
            return Geometry(lists.single().boundsInRoot, row, requireNotNull(observedInsets[route]))
        }
        val expected = mutableMapOf<String, Geometry>()
        fun assertVisiblePages() {
            routes.forEach { route ->
                val actual = geometry(route) ?: return@forEach
                val previous = expected.putIfAbsent(route, actual)
                if (previous != null) assertEquals("$position glass=$glass page=$route target=${controller.currentDestination?.route}", previous, actual)
                if (pageOwnsChromeInsets(route)) {
                    val leftRail = if (position == NavigationBarPosition.Left) Dimensions.navigationRailWidth else 0.dp
                    val rightRail = if (position == NavigationBarPosition.Right) Dimensions.navigationRailWidth else 0.dp
                    assertEquals(leftRail, actual.insets.left)
                    assertEquals(rightRail, actual.insets.right)
                    assertEquals((7.dp + leftRail).value.toInt(), actual.insets.playerLeft)
                    assertEquals((11.dp + rightRail).value.toInt(), actual.insets.playerRight)
                    assertEquals(sceneWidth.value, actual.viewport.width, 0f)
                    assertEquals(sceneHeight.value, actual.viewport.height, 0f)
                    assertTrue(actual.insets.bottom >= 80.dp)
                }
            }
        }
        compose.waitForIdle()
        assertVisiblePages()
        compose.mainClock.autoAdvance = false
        fun transition(action: () -> Unit) {
            compose.runOnIdle(action)
            repeat(7) {
                compose.mainClock.advanceTimeBy(32)
                compose.waitForIdle()
                assertVisiblePages()
            }
        }
        transition { controller.navigate("album") }

        val albumBefore = requireNotNull(geometry("album"))
        compose.runOnIdle { header.floatValue += 12f }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(albumBefore.firstRow.top + 12f, requireNotNull(geometry("album")).firstRow.top, 0f)
        compose.runOnIdle { header.floatValue = initialHeader }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val albumList = compose.onNodeWithTag("list-album")
        albumList.performScrollToIndex(10)
        assertEquals(albumBefore.viewport, albumList.fetchSemanticsNode().boundsInRoot)
        albumList.performScrollToIndex(0)
        assertVisiblePages()

        transition { controller.popBackStack() }
        transition { controller.navigate("settings") }
        transition { controller.navigate("album") }
        transition { controller.popBackStack() }
        transition { controller.navigate("playlist") }
        transition { controller.navigate("easterOne") }
        transition { controller.popBackStack() }
        transition { controller.popBackStack() }
        transition { controller.popBackStack() }
        assertEquals("home", controller.currentDestination?.route)
        if (landscape) {
            for (route in listOf("artist", "search")) {
                transition { controller.navigate(route) }
                val home = requireNotNull(expected["home"])
                val detail = requireNotNull(expected[route])
                if (position == NavigationBarPosition.Top) {
                    assertTrue("Detail keeps its top tabs while Home hides them", detail.insets.top > home.insets.top)
                } else {
                    assertTrue("Detail keeps its bottom tabs while Home hides them", detail.insets.bottom > home.insets.bottom)
                }
                transition { controller.popBackStack() }
            }
        }
    }

    @Composable
    private fun Rows(route: String, topInset: (() -> Dp)?, observed: MutableMap<String, Insets>) {
        val padding = rememberPageChromeContentPadding(PaddingValues(0.dp), topInset)
        val playerInsets = LocalPlayerAwareWindowInsets.current
        val density = LocalDensity.current
        SideEffect {
            observed[route] = Insets(padding.calculateTopPadding(), padding.calculateBottomPadding(),
                padding.calculateLeftPadding(LayoutDirection.Ltr), padding.calculateRightPadding(LayoutDirection.Ltr),
                playerInsets.getLeft(density, LayoutDirection.Ltr), playerInsets.getRight(density, LayoutDirection.Ltr))
        }
        LazyColumn(Modifier.fillMaxSize().testTag("list-$route"), contentPadding = padding) {
            items(20, key = { it }, contentType = { "row" }) { index ->
                Box(Modifier.fillMaxWidth().height(40.dp).then(if (index == 0) Modifier.testTag("first-$route") else Modifier))
            }
        }
    }
}
