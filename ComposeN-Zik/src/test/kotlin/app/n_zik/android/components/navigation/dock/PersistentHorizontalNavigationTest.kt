package app.n_zik.android.components.navigation.dock

import android.app.Application
import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersistentHorizontalNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Before fun preferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, false).commit()
    }

    @Test fun topKeepsFrameBoundsAcrossSettingsPushPop() = frames(NavigationBarPosition.Top)
    @Test fun bottomKeepsFrameBoundsAcrossSettingsPushPop() = frames(NavigationBarPosition.Bottom)
    @Test fun floatingKeepsFrameBoundsAcrossSettingsPushPop() = frames(NavigationBarPosition.BottomFloating)

    @Test fun pendingOutgoingPresentationCannotDispatchAnOldTabAction() {
        val target = mutableStateOf("home")
        var clicks = 0
        compose.setContent {
            val state = remember { FloatingDockState() }
            val dock = remember { FloatingDockEnvironment(state, null, true, mutableStateOf(0f), false) }
            CompositionLocalProvider(LocalFloatingDock provides dock) {
                RegisterDockNavigation("home", 3, true) {
                    Box(Modifier.size(100.dp, 61.dp).testTag("pending-tab").clickable { clicks++ })
                }
                PersistentHorizontalNavigation(NavigationBarPosition.Bottom, { target.value }, { true },
                    state::registration, false, { 0 }, { 0f })
            }
        }
        compose.onNodeWithTag("pending-tab").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks); target.value = "search" }
        compose.onNodeWithTag("pending-tab", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private fun frames(position: NavigationBarPosition) {
        lateinit var controller: NavHostController
        var mounts = 0
        var disposals = 0
        var tabClicks = 0
        compose.setContent {
            controller = rememberNavController()
            val state = remember { FloatingDockState() }
            val entry by controller.currentBackStackEntryAsState()
            val dock = remember(state) {
                FloatingDockEnvironment(state, null, true, mutableStateOf(0f), false)
            }
            CompositionLocalProvider(LocalFloatingDock provides dock, LocalDensity provides Density(1f)) {
                Box(Modifier.size(300.dp, 600.dp)) {
                    NavHost(controller, "home",
                        enterTransition = { slideInHorizontally(tween(400)) { it } },
                        exitTransition = { slideOutHorizontally(tween(400)) { -it } },
                        popEnterTransition = { slideInHorizontally(tween(400)) { -it } },
                        popExitTransition = { slideOutHorizontally(tween(400)) { it } },
                    ) {
                        for ((route, count) in listOf("home" to 5, "search" to 3, "settings" to 1)) {
                            composable(route) { page ->
                                RegisterDockNavigation(page.id, count, true) {
                                    if (count >= 2) Box(Modifier.fillMaxWidth().height(61.dp).testTag("tabs-$route").clickable { tabClicks++ })
                                }
                                Box(Modifier.fillMaxSize().testTag("page-$route"))
                            }
                        }
                    }
                    DisposableEffect(Unit) {
                        mounts++
                        onDispose { disposals++ }
                    }
                    PersistentHorizontalNavigation(position, { entry?.id },
                        { entry?.destination?.route != "settings" }, state::registration, false,
                        topPx = { 64 }, bottomOffsetPx = { 0f }, modifier = Modifier.testTag("nav-root"))
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onNodeWithTag("nav-root").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("tabs-home", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithTag("page-home").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        fun checkFrame(source: String?, target: String?, targetPage: String): Boolean {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals(root, compose.onNodeWithTag("nav-root").fetchSemanticsNode().boundsInRoot)
            val incomingComposed = compose.onAllNodesWithTag("page-$targetPage").fetchSemanticsNodes().isNotEmpty()
            val expected = if (target == null || incomingComposed) target else source
            if (expected != null) assertEquals(tabs, compose.onNodeWithTag("tabs-$expected", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
            for (route in listOf("home", "search")) {
                if (route != expected) compose.onNodeWithTag("tabs-$route", useUnmergedTree = true).assertDoesNotExist()
            }
            assertEquals(1, mounts)
            assertEquals(0, disposals)
            return incomingComposed
        }
        fun checkFrames(source: String?, target: String?, targetPage: String) {
            var committed = false
            repeat(26) {
                val incoming = checkFrame(source, target, targetPage)
                if (committed) org.junit.Assert.assertTrue("Incoming page cannot revert after commit", incoming)
                committed = committed || incoming
            }
            org.junit.Assert.assertTrue("Incoming destination must commit", committed)
        }

        compose.runOnIdle { controller.navigate("search") }
        checkFrame("home", "search", "search")
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertNotEquals(page, compose.onNodeWithTag("page-search").fetchSemanticsNode().boundsInRoot)
        checkFrames("home", "search", "search")
        compose.runOnIdle { controller.navigate("settings") }
        checkFrames("search", null, "settings")
        compose.runOnIdle { controller.popBackStack() }
        checkFrames(null, "search", "search")
        compose.runOnIdle { controller.popBackStack() }
        checkFrames("search", "home", "home")
    }
}
