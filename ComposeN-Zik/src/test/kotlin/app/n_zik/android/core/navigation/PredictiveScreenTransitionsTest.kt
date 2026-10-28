package app.n_zik.android.core.navigation

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.it.fast4x.rimusic.enums.TransitionEffect
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PredictiveScreenTransitionsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: NavHostController
    private val effect = mutableStateOf(TransitionEffect.None)
    private var committedPops = 0
    private val activity: ComponentActivity
        get() = (compose as AndroidComposeTestRule<*, *>).activity

    @Test
    fun noneKeepsAFullSizePageDuringProgressAndRestoresItOnCancel() {
        showHost(TransitionEffect.None)
        startGesture(0.5f)
        val image = pixels()
        val center = image[image.width / 2, image.height / 2]
        assertTrue("None paints a full page", center == Color.Red || center == Color.Blue)
        assertUniformPage(image, center)
        cancelThenCommit()
    }

    @Test
    fun updatedFadePreferenceSeeksOpacityWithoutShrinking() {
        showHost(TransitionEffect.None)
        compose.runOnIdle { effect.value = TransitionEffect.Fade }
        frames(3)
        startGesture(0.2f)
        val early = pixels()
        val earlyColor = early[early.width / 2, early.height / 2]
        progress(0.5f)
        val middle = pixels()
        val middleColor = middle[middle.width / 2, middle.height / 2]
        assertTrue("Fade responds to predictive progress", middleColor.red < earlyColor.red)
        assertTrue("Both destinations contribute at mid-gesture", middleColor.red > 0f && middleColor.blue > 0f)
        assertUniformPage(middle, middleColor)
        progress(0.2f)
        val reversed = pixels()
        assertColor("Fade reverses to the earlier gesture progress", earlyColor,
            reversed[reversed.width / 2, reversed.height / 2])
        cancelThenCommit()
    }

    @Test
    fun horizontalSlideMovesRightAtMidGestureEvenFromTheRightEdge() {
        showHost(TransitionEffect.SlideHorizontal)
        startGesture(0.2f, BackEventCompat.EDGE_RIGHT)
        val earlyRedWidth = redWidth(pixels())
        progress(0.5f, BackEventCompat.EDGE_RIGHT)
        val middle = pixels()
        val middleRedWidth = redWidth(middle)
        assertTrue("Slide remains interactive", middleRedWidth in 1 until earlyRedWidth)
        listOf(2, middle.height / 2, middle.height - 3).forEach { y ->
            assertColor("Previous page is revealed on the left", Color.Blue, middle[2, y])
            assertColor("Outgoing page slides right without shrinking vertically", Color.Red, middle[middle.width - 3, y])
        }
        cancelThenCommit(BackEventCompat.EDGE_RIGHT)
    }

    @Test
    fun productionHostsWireBothPredictiveCallbacksToTheSharedPolicy() {
        val root = listOf(File("src/androidMain/kotlin"), File("ComposeN-Zik/src/androidMain/kotlin"))
            .first { it.isDirectory }
        listOf(
            "app/n_zik/android/components/ui/screens/settings/SettingsScreen.kt",
            "app/it/fast4x/rimusic/ui/screens/AppNavigation.kt",
        ).forEach { path ->
            val source = File(root, path).readText()
            listOf("predictivePopEnterTransition", "predictivePopExitTransition").forEach { callback ->
                assertTrue("$path must supply $callback to NavHost",
                    Regex("$callback\\s*=\\s*transitions\\.$callback").containsMatchIn(source))
            }
            assertTrue("$path must refresh its policy when the preference changes",
                source.contains("remember(transitionEffect) { ScreenTransitions(transitionEffect) }"))
        }
    }

    private fun showHost(initialEffect: TransitionEffect) {
        effect.value = initialEffect
        compose.mainClock.autoAdvance = false
        compose.setContent {
            controller = rememberNavController()
            val transitions = remember(effect.value) { ScreenTransitions(effect.value) }
            Box(Modifier.size(240.dp).background(Color.Black).testTag("viewport")) {
                NavHost(
                    navController = controller,
                    startDestination = "previous",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = transitions.enterTransition,
                    exitTransition = transitions.exitTransition,
                    popEnterTransition = transitions.popEnterTransition,
                    popExitTransition = transitions.popExitTransition,
                    predictivePopEnterTransition = transitions.predictivePopEnterTransition,
                    predictivePopExitTransition = transitions.predictivePopExitTransition,
                ) {
                    composable("previous") { Box(Modifier.fillMaxSize().background(Color.Blue)) }
                    composable("current") { Box(Modifier.fillMaxSize().background(Color.Red)) }
                }
            }
        }
        frames(5)
        compose.runOnIdle { controller.navigate("current") }
        frames(90)
        assertUniformPage(pixels(), Color.Red)
        compose.runOnIdle {
            controller.addOnDestinationChangedListener { _, destination, _ ->
                if (destination.route == "previous") committedPops++
            }
        }
    }

    private fun startGesture(fraction: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        compose.runOnIdle {
            activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, edge))
        }
        frames(2)
        progress(fraction, edge)
    }

    private fun progress(fraction: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        compose.runOnIdle {
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, fraction, edge))
        }
        frames(5)
        compose.runOnIdle { assertEquals("current", controller.currentDestination?.route) }
    }

    private fun cancelThenCommit(edge: Int = BackEventCompat.EDGE_LEFT) {
        compose.runOnIdle { activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        frames(90)
        compose.runOnIdle {
            assertEquals("Cancellation keeps the destination", "current", controller.currentDestination?.route)
            assertEquals(0, committedPops)
        }
        assertUniformPage(pixels(), Color.Red)
        startGesture(0.5f, edge)
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        frames(90)
        compose.runOnIdle {
            assertEquals("previous", controller.currentDestination?.route)
            assertEquals("One committed gesture pops once", 1, committedPops)
            assertNull(controller.previousBackStackEntry)
        }
        assertUniformPage(pixels(), Color.Blue)
    }

    private fun frames(count: Int) {
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            compose.waitForIdle()
        }
    }

    private fun pixels() = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()

    private fun redWidth(image: PixelMap): Int =
        (0 until image.width).count { image[it, image.height / 2].red > 0.9f }

    private fun assertUniformPage(image: PixelMap, color: Color) {
        listOf(2, image.width / 2, image.width - 3).forEach { x ->
            listOf(2, image.height / 2, image.height - 3).forEach { y ->
                assertColor("Page must fill its viewport at ($x, $y)", color, image[x, y])
            }
        }
    }

    private fun assertColor(message: String, expected: Color, actual: Color) {
        assertEquals(message, expected.red, actual.red, 2f / 255f)
        assertEquals(message, expected.green, actual.green, 2f / 255f)
        assertEquals(message, expected.blue, actual.blue, 2f / 255f)
        assertEquals(message, expected.alpha, actual.alpha, 2f / 255f)
    }
}
