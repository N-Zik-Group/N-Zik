package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
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
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val LocalRailPageName = staticCompositionLocalOf { "root" }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SideRailStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun productionRailIsRootOwnedAndSkeletonOnlyRegistersItsBody() {
        fun source(name: String): String {
            val relative = "src/androidMain/kotlin/app/n_zik/android/components/navigation/$name.kt"
            return listOf(File(relative), File("ComposeN-Zik/$relative"))
                .first { it.exists() }.readText()
        }
        val skeleton = source("Skeleton")
        org.junit.Assert.assertTrue(skeleton.contains("RegisterSideRailBody("))
        org.junit.Assert.assertFalse(skeleton.contains("SideNavigationOverlay("))
        val navigation = source("AppNavigation")
        org.junit.Assert.assertTrue(navigation.contains("LocalSideRail provides sideRail"))
        org.junit.Assert.assertTrue(navigation.contains("registration = sideRail::registration"))
        org.junit.Assert.assertFalse(navigation.contains("if (standaloneSideHeader) CompositionLocalProvider("))
    }

    @Test fun staleOwnerCannotRemoveReplacementOrSupplySettingsTabs() {
        val state = SideRailState()
        val old = SideRailState.Registration(Any()) { }
        val replacement = SideRailState.Registration(Any()) { }
        state.register("home-1", old)
        state.register("home-1", replacement)
        state.unregister("home-1", old.owner)
        assertSame(replacement, state.registration("home-1"))
        assertNull(state.registration("settings-2"))
        state.register("search-3", old)
        assertSame(replacement, state.registration("home-1"))
        state.unregister("search-3", old.owner)
        assertSame(replacement, state.registration("home-1"))
    }

    @Test fun leftRootRailKeepsBoundsDuringSettingsPushPopAndPageSlides() = verifyFrames(false)
    @Test fun rightRootRailKeepsBoundsDuringSettingsPushPopAndPageSlides() = verifyFrames(true)

    private fun verifyFrames(onRight: Boolean) {
        lateinit var controller: NavHostController
        var headerMounts = 0
        var headerDisposals = 0
        var tabClicks = 0
        compose.setContent {
            controller = rememberNavController()
            val state = remember { SideRailState() }
            val entry by controller.currentBackStackEntryAsState()
            val surface = rememberLayerBackdrop()
            CompositionLocalProvider(LocalSideRail provides state, LocalDensity provides Density(1f)) {
                Box(Modifier.size(300.dp, 600.dp)) {
                    NavHost(controller, startDestination = "home",
                        enterTransition = { slideInHorizontally(tween(400)) { it } },
                        exitTransition = { slideOutHorizontally(tween(400)) { -it } },
                        popEnterTransition = { slideInHorizontally(tween(400)) { -it } },
                        popExitTransition = { slideOutHorizontally(tween(400)) { it } },
                    ) {
                        for (route in listOf("home", "search")) {
                            composable(route) { page ->
                                CompositionLocalProvider(LocalRailPageName provides route) {
                                    RegisterSideRailBody(page.id, enabled = true) {
                                        Box(Modifier.size(64.dp, 136.dp)
                                            .testTag("tabs-${LocalRailPageName.current}").clickable { tabClicks++ })
                                    }
                                    Box(Modifier.fillMaxSize().testTag("page-$route"))
                                }
                            }
                        }
                        composable("settings") { Box(Modifier.fillMaxSize().testTag("page-settings")) }
                    }
                    SideNavigationOverlay(onRight, 64, { 24 }, { 40 }, { 12 }, { 20 }, Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().testTag("root-rail")) {
                            DisposableEffect(Unit) {
                                headerMounts++
                                onDispose { headerDisposals++ }
                            }
                            Box(Modifier.height(160.dp).testTag("root-header"))
                            SideRailBodySubcompose({ entry?.id }, { entry?.destination?.route != "settings" }, state::registration, surface)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val railBounds = compose.onNodeWithTag("root-rail").fetchSemanticsNode().boundsInRoot
        val headerBounds = compose.onNodeWithTag("root-header").fetchSemanticsNode().boundsInRoot
        val initialTabs = compose.onNodeWithTag("tabs-home", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val initialPage = compose.onNodeWithTag("page-home").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false

        fun frame(source: String?, target: String?, targetPage: String): Boolean {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals(railBounds, compose.onNodeWithTag("root-rail").fetchSemanticsNode().boundsInRoot)
            assertEquals(headerBounds, compose.onNodeWithTag("root-header").fetchSemanticsNode().boundsInRoot)
            val incomingComposed = compose.onAllNodesWithTag("page-$targetPage").fetchSemanticsNodes().isNotEmpty()
            val expected = if (target == null || incomingComposed) target else source
            if (expected != null) assertEquals(initialTabs, compose.onNodeWithTag("tabs-$expected", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
            for (route in listOf("home", "search")) {
                if (route != expected) compose.onNodeWithTag("tabs-$route", useUnmergedTree = true).assertDoesNotExist()
            }
            assertEquals(1, headerMounts)
            assertEquals(0, headerDisposals)
            return incomingComposed
        }
        fun frames(source: String?, target: String?, targetPage: String) {
            var committed = false
            repeat(26) {
                val incoming = frame(source, target, targetPage)
                if (committed) org.junit.Assert.assertTrue("Incoming page cannot revert after commit", incoming)
                committed = committed || incoming
            }
            org.junit.Assert.assertTrue("Incoming destination must commit", committed)
        }

        compose.runOnIdle { controller.navigate("search") }
        frame("home", "search", "search")
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        assertNotEquals(initialPage, compose.onNodeWithTag("page-search").fetchSemanticsNode().boundsInRoot)
        frames("home", "search", "search")
        compose.runOnIdle { controller.navigate("settings") }
        frames("search", null, "settings")
        compose.onNodeWithTag("tabs-search", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { controller.popBackStack() }
        frames(null, "search", "search")
        compose.runOnIdle { controller.popBackStack() }
        frames("search", "home", "home")
    }
}
