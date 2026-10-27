package app.n_zik.android.core.navigation

import android.app.Application
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.n_zik.android.LocalBottomBarOffset
import app.n_zik.android.LocalTopBarOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ScaffoldNavigationVisibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stableOffsetProvidersKeepLayoutReactiveAcrossNavigationRecomposition() {
        val bars = ScaffoldBarVisibilityState()
        val route = mutableStateOf("home")
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            Text(route.value)
            CompositionLocalProvider(
                LocalTopBarOffset provides bars.topOffset,
                LocalBottomBarOffset provides bars.bottomOffset,
            ) {
                OffsetProbes()
            }
        }
        compose.runOnIdle {
            bars.topOffset.floatValue = 20f * pixelsPerDp
            bars.bottomOffset.floatValue = 40f * pixelsPerDp
        }
        compose.onNodeWithTag("topOffset").assertTopPositionInRootIsEqualTo(20.dp)
        compose.onNodeWithTag("bottomOffset").assertTopPositionInRootIsEqualTo(40.dp)
        compose.runOnIdle { route.value = "settings" }
        compose.onNodeWithTag("topOffset").assertTopPositionInRootIsEqualTo(20.dp)
        compose.onNodeWithTag("bottomOffset").assertTopPositionInRootIsEqualTo(40.dp)
        compose.runOnIdle { bars.reveal() }
        compose.onNodeWithTag("topOffset").assertTopPositionInRootIsEqualTo(0.dp)
        compose.onNodeWithTag("bottomOffset").assertTopPositionInRootIsEqualTo(0.dp)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun navigationCancelsAnOldFlingBeforeResettingBothBars() = runTest {
        val bars = ScaffoldBarVisibilityState()
        bars.topOffset.floatValue = -80f
        bars.bottomOffset.floatValue = 240f
        bars.visible.value = false
        val oldFling = launch {
            delay(150)
            bars.topOffset.floatValue = -80f
            bars.bottomOffset.floatValue = 240f
            bars.visible.value = false
        }
        bars.animationJob.value = oldFling
        bars.reveal()
        advanceUntilIdle()
        assertTrue(oldFling.isCancelled)
        assertNull(bars.animationJob.value)
        assertEquals(0f, bars.topOffset.floatValue, 0f)
        assertEquals(0f, bars.bottomOffset.floatValue, 0f)
        assertTrue(bars.visible.value)
    }

    @Test fun changingArgumentsAndGoingBackRevealBarsButRecompositionDoesNot() {
        val bars = ScaffoldBarVisibilityState()
        val unrelated = mutableStateOf(0)
        lateinit var navigation: NavHostController
        compose.setContent {
            navigation = rememberNavController()
            RevealScaffoldOnNavigation(navigation, bars::reveal)
            NavHost(navigation, startDestination = "album/one") {
                composable("album/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    Text("${it.arguments?.getString("id")}:${unrelated.value}")
                }
            }
        }
        compose.waitForIdle()
        fun hide() {
            bars.topOffset.floatValue = -80f
            bars.bottomOffset.floatValue = 240f
            bars.visible.value = false
        }
        compose.runOnIdle { hide(); unrelated.value++ }
        compose.waitForIdle()
        assertFalse("same-page recomposition must not override scrolling", bars.visible.value)
        compose.runOnIdle { navigation.navigate("album/two") }
        compose.waitForIdle()
        assertTrue(bars.visible.value)
        assertEquals(0f, bars.topOffset.floatValue, 0f)
        compose.runOnIdle { hide(); navigation.popBackStack() }
        compose.waitForIdle()
        assertTrue(bars.visible.value)
        assertEquals(0f, bars.bottomOffset.floatValue, 0f)
    }

    @Test fun aNestedDestinationUsesTheSharedScaffoldRevealCallback() {
        val bars = ScaffoldBarVisibilityState()
        lateinit var innerNavigation: NavHostController
        compose.setContent {
            CompositionLocalProvider(LocalRevealScaffold provides bars::reveal) {
                innerNavigation = rememberNavController()
                RevealScaffoldOnNavigation(innerNavigation)
                NavHost(innerNavigation, startDestination = "global") {
                    composable("global") { Text("Global") }
                    composable("category") { Text("Category") }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            bars.topOffset.floatValue = -80f
            bars.bottomOffset.floatValue = 240f
            bars.visible.value = false
            innerNavigation.navigate("category")
        }
        compose.waitForIdle()
        assertTrue(bars.visible.value)
        assertEquals(0f, bars.topOffset.floatValue, 0f)
        assertEquals(0f, bars.bottomOffset.floatValue, 0f)
    }
    @Test fun internalTabsRevealBarsButStableSelectionRecompositionDoesNot() {
        val bars = ScaffoldBarVisibilityState()
        val tab = mutableStateOf(0)
        val unrelated = mutableStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalRevealScaffold provides bars::reveal) {
                RevealScaffoldOnPageSelection(tab.value)
                Text("${tab.value}:${unrelated.value}")
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            bars.visible.value = false
            bars.topOffset.floatValue = -80f
            bars.bottomOffset.floatValue = 240f
            unrelated.value++
        }
        compose.waitForIdle()
        assertFalse(bars.visible.value)
        compose.runOnIdle { tab.value = 1 }
        compose.waitForIdle()
        assertTrue(bars.visible.value)
        assertEquals(0f, bars.topOffset.floatValue, 0f)
        assertEquals(0f, bars.bottomOffset.floatValue, 0f)
    }

}

@Composable
private fun OffsetProbes() {
    val top = LocalTopBarOffset.current
    val bottom = LocalBottomBarOffset.current
    Text("Top", Modifier.offset { IntOffset(0, top.value.toInt()) }.testTag("topOffset"))
    Text("Bottom", Modifier.offset { IntOffset(0, bottom.value.toInt()) }.testTag("bottomOffset"))
}
