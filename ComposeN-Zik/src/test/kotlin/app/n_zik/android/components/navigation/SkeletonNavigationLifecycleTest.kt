package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
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
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.navigation.dock.FloatingDockEnvironment
import app.n_zik.android.components.navigation.dock.FloatingDockState
import app.n_zik.android.components.navigation.dock.LocalFloatingDock
import app.n_zik.android.components.navigation.dock.PersistentHorizontalNavigation
import app.n_zik.android.core.navigation.currentSearchShortcutRoute
import app.n_zik.android.updater.models.CheckUpdateState
import app.n_zik.android.updater.models.MajorUpdateConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the production page registration, not a replacement body made of test boxes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkeletonNavigationLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rightTabsSurviveRecompositionAndPushPop() = verify(NavigationBarPosition.Right)
    @Test fun leftTabsSurviveRecompositionAndPushPop() = verify(NavigationBarPosition.Left)
    @Test fun topTabsSurviveRecompositionAndPushPop() = verify(NavigationBarPosition.Top)
    @Test fun bottomTabsSurviveRecompositionAndPushPop() = verify(NavigationBarPosition.Bottom)
    @Test fun floatingTabsSurviveRecompositionAndPushPop() = verify(NavigationBarPosition.BottomFloating)

    private fun verify(position: NavigationBarPosition) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("skeleton_lifecycle_test", context)
        context.preferences.edit().clear()
            .putString(navigationBarPositionKey, position.name)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name)
            .putBoolean(glassEnabledKey, false)
            .putString(checkUpdateStateKey, CheckUpdateState.Disabled.name)
            .putInt(lastVersionCodeKey, BuildConfig.VERSION_CODE)
            .putString(lastBuildTypeKey, MajorUpdateConfig.getCurrentBuildType())
            .putString(seenChangelogsVersionKey, BuildConfig.VERSION_NAME).commit()
        val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback,
            CircleShape, CircleShape, CircleShape)
        val revision = mutableIntStateOf(0)
        var deliveredRevision = -1
        lateinit var controller: NavHostController
        lateinit var railState: SideRailState
        lateinit var dockState: FloatingDockState
        val pageIds = mutableMapOf<String, String>()
        compose.setContent {
            controller = rememberNavController()
            val entry by controller.currentBackStackEntryAsState()
            val rail = remember { SideRailState() }
            val dock = remember { FloatingDockEnvironment(FloatingDockState(), null, true, mutableFloatStateOf(0f), false) }
            railState = rail
            dockState = dock.state
            val surface = rememberLayerBackdrop()
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalSideRail provides rail, LocalFloatingDock provides dock) {
                Box(Modifier.fillMaxSize()) {
                    NavHost(controller, "home", enterTransition = { fadeIn(tween(160)) },
                        exitTransition = { fadeOut(tween(160)) }) {
                        for (route in listOf("home", "search")) composable(route) { page ->
                            pageIds[route] = page.id
                            val shortcutRoute = currentSearchShortcutRoute(controller)
                            SideEffect {
                                assertEquals("Search controls must keep the route of their own page during transitions",
                                    route, shortcutRoute)
                            }
                            val selected = remember { mutableIntStateOf(0) }
                            val callbackRevision = revision.intValue
                            Skeleton(controller, selected.intValue, onTabChanged = {
                                deliveredRevision = callbackRevision
                                selected.intValue = it
                            }, navBarContent = { emit ->
                                // Distinct callsites match HomeScreen's conditional tab branches.
                                emit(0, "$route songs", R.drawable.musical_notes)
                                emit(1, "$route albums", R.drawable.album)
                                emit(2, "$route artists", R.drawable.people)
                            }) { BasicText("Page $route selected $it") }
                        }
                        composable("settings") { BasicText("Settings page") }
                    }
                    if (position.isHorizontal) {
                        PersistentHorizontalNavigation(position, { entry?.id },
                            { entry?.destination?.route != "settings" }, dock.state::registration,
                            false, { 0 }, { 0f })
                    } else Box(Modifier.width(64.dp)) {
                        SideRailBodySubcompose({ entry?.id },
                            { entry?.destination?.route != "settings" }, rail::registration, surface)
                    }
                }
            }
        }
        val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        fun assertTabs(count: Int) { compose.onAllNodes(tabs).assertCountEquals(count) }
        assertTabs(3)
        repeat(3) { next ->
            compose.runOnIdle { revision.intValue = next + 1 }
            compose.onNodeWithContentDescription("home albums").performClick()
            compose.runOnIdle { assertEquals(next + 1, deliveredRevision) }
            assertTabs(3)
            compose.onNodeWithContentDescription("home songs").performClick()
            assertTabs(3)
        }
        compose.mainClock.autoAdvance = false
        fun frames(count: Int) {
            val target = controller.currentBackStackEntry
            var observedTargetRegistration = false
            fun targetIsRegistered(): Boolean = if (position.isHorizontal) {
                dockState.registration(target?.id)?.present == true
            } else railState.registration(target?.id) != null
            repeat(14) { frame ->
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val actual = compose.onAllNodes(tabs).fetchSemanticsNodes().size
                val registrations = pageIds.mapValues { (_, id) ->
                    if (position.isHorizontal) dockState.registration(id)?.present
                    else railState.registration(id) != null
                }
                assertEquals("$position frame=$frame target=${controller.currentDestination?.route} registrations=$registrations",
                    count, actual)
                if (count > 0) {
                    val registered = targetIsRegistered()
                    if (observedTargetRegistration) {
                        assertEquals("$position frame=$frame target=${target?.destination?.route}: " +
                            "a committed incoming registration cannot disappear when the outgoing page disposes",
                            true, registered)
                    }
                    observedTargetRegistration = observedTargetRegistration || registered
                }
            }
            if (count > 0) {
                assertEquals("$position target=${target?.destination?.route} must register by the end of its transition",
                    true, observedTargetRegistration && targetIsRegistered())
            }
        }
        repeat(2) {
            compose.runOnIdle { controller.navigate("search") }
            frames(3)
            compose.runOnIdle { controller.popBackStack() }
            frames(3)
        }
        compose.runOnIdle { controller.navigate("settings") }
        frames(0)
        compose.runOnIdle { controller.popBackStack() }
        // A returning page is registered once NavHost composes it; assert every subsequent frame.
        compose.mainClock.advanceTimeBy(32)
        frames(3)
    }
}
