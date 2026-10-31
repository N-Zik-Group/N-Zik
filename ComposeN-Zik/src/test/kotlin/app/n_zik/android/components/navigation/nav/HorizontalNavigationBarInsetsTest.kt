package app.n_zik.android.components.navigation.nav

import android.app.Application
import android.content.Context
import android.view.View
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.navigation.dock.FloatingDockEnvironment
import app.n_zik.android.components.navigation.dock.FloatingDockState
import app.n_zik.android.components.navigation.dock.LocalFloatingDock
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HorizontalNavigationBarInsetsTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear()
            .putBoolean(glassEnabledKey, true)
            .putString(navigationBarPositionKey, NavigationBarPosition.Bottom.name)
            .putString(navigationBarTypeKey, NavigationBarType.IconOnly.name)

            .commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @Test fun `standard glass controls keep their full height above changing system bottom insets`() {
        lateinit var view: View
        var observedInset = 0
        var controlHeightPx = 0f
        val shape = RoundedCornerShape(16.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        val navController = mockk<NavController>(relaxed = true)
        composeRule.setContent {
            view = LocalView.current
            observedInset = WindowInsets.systemBars.getBottom(LocalDensity.current)
            controlHeightPx = with(LocalDensity.current) { GlassNavigationBarHeight.toPx() }
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.fillMaxSize().testTag("viewport")) {
                    val bar = HorizontalNavigationBar(0, {}, navController,
                        Modifier.align(Alignment.BottomCenter).testTag("bar"))
                    bar.add { item ->
                        item(0, "Songs", R.drawable.library)
                        item(1, "Albums", R.drawable.library)
                    }
                    bar.Draw()
                }
            }
        }
        // Three-button, gesture, then absent bottom navigation, without recreating the bar.
        for (insetPx in listOf(72, 24, 0)) {
            composeRule.runOnIdle {
                ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, insetPx))
                    .setVisible(WindowInsetsCompat.Type.navigationBars(), insetPx > 0).build())
            }
            composeRule.runOnIdle { assertEquals(insetPx, observedInset) }
            val viewport = composeRule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val bar = composeRule.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            assertEquals("controls retain 64dp plus system inset", controlHeightPx + insetPx, bar.height, 1f)
            for (label in listOf("Songs", "Albums")) {
                val tab = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                assertTrue("$label stays above system buttons: $tab", tab.bottom <= viewport.bottom - insetPx + 1f)
            }
        }
    }

    @Test fun `classic native bottom overlay keeps tabs above system navigation and clickable`() {
        context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        lateinit var view: View
        var observedInset = 0
        var selectedTab = -1
        val shape = RoundedCornerShape(16.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        val navController = mockk<NavController>(relaxed = true)
        composeRule.setContent {
            view = LocalView.current
            observedInset = WindowInsets.systemBars.getBottom(LocalDensity.current)
            CompositionLocalProvider(LocalAppearance provides appearance) {
                // Classic Bottom now uses this same full-viewport overlay host as floating navigation.
                Box(Modifier.fillMaxSize().testTag("viewport")) {
                    val bar = HorizontalNavigationBar(0, { selectedTab = it }, navController,
                        Modifier.align(Alignment.BottomCenter).testTag("bar"))
                    bar.add { item ->
                        item(0, "Songs", R.drawable.library)
                        item(1, "Albums", R.drawable.library)
                    }
                    bar.Draw()
                }
            }
        }
        for (insetPx in listOf(72, 24, 0)) {
            composeRule.runOnIdle {
                ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, insetPx))
                    .setVisible(WindowInsetsCompat.Type.navigationBars(), insetPx > 0).build())
            }
            composeRule.runOnIdle { assertEquals(insetPx, observedInset) }
            val viewport = composeRule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val bar = composeRule.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            assertEquals("overlay remains anchored at the viewport bottom", viewport.bottom, bar.bottom, 1f)
            for (label in listOf("Songs", "Albums")) {
                val tab = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                assertTrue("$label clears the Android gesture/button area", tab.bottom <= viewport.bottom - insetPx + 1f)
            }
            composeRule.runOnIdle { selectedTab = -1 }
            composeRule.onNodeWithContentDescription("Albums").performTouchInput { click(center) }
            composeRule.runOnIdle { assertEquals(1, selectedTab) }
        }
    }

    @Test fun `floating renderer never adds Search to any destination`() {
        context.preferences.edit()
            .putBoolean(glassEnabledKey, false)
            .putString(navigationBarPositionKey, NavigationBarPosition.BottomFloating.name).commit()
        val shape = RoundedCornerShape(16.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        val dock = FloatingDockEnvironment(FloatingDockState(), "unregistered", true, mutableStateOf(0f), false)
        lateinit var navigation: NavHostController
        composeRule.setContent {
            navigation = rememberNavController()
            CompositionLocalProvider(LocalAppearance provides appearance, LocalFloatingDock provides dock) {
                Box(Modifier.fillMaxSize()) {
                    NavHost(navigation, startDestination = "home") {
                        composable("home") { }
                        composable("search") { }
                        composable("searchResults/{query}") { }
                    }
                    val bar = HorizontalNavigationBar(0, {}, navigation)
                    bar.add { item ->
                        item(0, "Online", R.drawable.library)
                        item(1, "Local", R.drawable.library)
                        item(2, "Link", R.drawable.library)
                    }
                    bar.Draw()
                }
            }
        }
        composeRule.runOnIdle { assertFalse(dock.present) }
        val search = composeRule.onNodeWithContentDescription(context.getString(R.string.search))
        search.assertDoesNotExist()
        for (route in listOf("search", "searchResults/example")) {
            composeRule.runOnIdle { navigation.navigate(route) }
            search.assertDoesNotExist()
            listOf("Online", "Local", "Link").forEach { label ->
                composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
            }
        }
        composeRule.runOnIdle { navigation.navigate("home") }
        search.assertDoesNotExist()
    }

    @Test fun `outgoing search dock never gains a shortcut while the current destination returns home`() {
        context.preferences.edit()
            .putBoolean(glassEnabledKey, false)
            .putString(navigationBarPositionKey, NavigationBarPosition.BottomFloating.name).commit()
        val shape = RoundedCornerShape(16.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        val dock = FloatingDockEnvironment(FloatingDockState(), "animated", true, mutableStateOf(0f), false)
        lateinit var navigation: NavHostController
        @Composable fun PageBar(tag: String) {
            Box(Modifier.testTag(tag)) {
                val bar = HorizontalNavigationBar(0, {}, navigation)
                bar.add { item ->
                    item(0, "Online", R.drawable.library)
                    item(1, "Local", R.drawable.library)
                    item(2, "Link", R.drawable.library)
                }
                bar.Draw()
            }
        }
        composeRule.setContent {
            navigation = rememberNavController()
            CompositionLocalProvider(LocalAppearance provides appearance, LocalFloatingDock provides dock) {
                NavHost(navigation, startDestination = "home",
                    enterTransition = { fadeIn(tween(300)) }, exitTransition = { fadeOut(tween(300)) },
                    popEnterTransition = { fadeIn(tween(300)) }, popExitTransition = { fadeOut(tween(300)) },
                ) {
                    composable("home") { PageBar("homeBar") }
                    composable("search") { PageBar("searchBar") }
                }
            }
        }
        val searchInSearchBar = hasContentDescription(context.getString(R.string.search)) and
            hasAnyAncestor(hasTestTag("searchBar"))
        val searchInHomeBar = hasContentDescription(context.getString(R.string.search)) and
            hasAnyAncestor(hasTestTag("homeBar"))
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navigation.navigate("search") }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.onNodeWithTag("homeBar").assertExists()
        composeRule.onAllNodes(searchInHomeBar).assertCountEquals(0)
        composeRule.onAllNodes(searchInSearchBar).assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(96)
        composeRule.onAllNodes(searchInHomeBar).assertCountEquals(0)
        composeRule.onAllNodes(searchInSearchBar).assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithTag("homeBar").assertDoesNotExist()
        composeRule.onAllNodes(searchInSearchBar).assertCountEquals(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { navigation.popBackStack() }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.runOnIdle { assertEquals("home", navigation.currentDestination?.route) }
        composeRule.onNodeWithTag("searchBar").assertExists()
        composeRule.onAllNodes(searchInSearchBar).assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(96)
        composeRule.onNodeWithTag("searchBar").assertExists()
        composeRule.onAllNodes(searchInSearchBar).assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithTag("searchBar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.search)).assertDoesNotExist()
    }
}
