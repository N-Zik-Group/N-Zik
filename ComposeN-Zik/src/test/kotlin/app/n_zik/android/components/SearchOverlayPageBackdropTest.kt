package app.n_zik.android.components

import android.app.Application
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.lastBuildTypeKey
import app.it.fast4x.rimusic.utils.lastVersionCodeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.seenChangelogsVersionKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.BuildConfig
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.updater.models.CheckUpdateState
import app.n_zik.android.updater.models.MajorUpdateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchOverlayPageBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `sibling recognition material samples only its mounted search page`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("search_overlay_page_test", context)
        context.preferences.edit().clear()
            .putString(navigationBarPositionKey, NavigationBarPosition.Bottom.name)
            .putString(checkUpdateStateKey, CheckUpdateState.Disabled.name)
            .putInt(lastVersionCodeKey, BuildConfig.VERSION_CODE)
            .putString(lastBuildTypeKey, MajorUpdateConfig.getCurrentBuildType())
            .putString(seenChangelogsVersionKey, BuildConfig.VERSION_NAME).commit()
        val shape = RoundedCornerShape(0.dp)
        val settings = GlassSettings(quality = GlassQuality.Balanced, film = 0f, vibrancy = 1f,
            tintStrength = 0f, blur = 0f, rim = 0f, amount = 0f, depth = 0f).inScope(GlassScope.Search)
        val pageColor = mutableStateOf(Color.Red)
        val overlayColor = mutableStateOf(Color.Green)
        val mounted = mutableStateOf(true)
        lateinit var navigation: NavHostController
        compose.setContent {
            navigation = rememberNavController()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalGlassPreviewSettings provides settings,
            ) {
                NavHost(navigation, "search?text={text}", enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None }) {
                    for (route in listOf("search?text={text}", "other")) composable(route) {
                        Box(Modifier.fillMaxSize()) {
                            if (mounted.value) {
                                Skeleton(navigation, navBarContent = {}, topInsetHandledByContent = true) {
                                    Box(Modifier.fillMaxSize().background(pageColor.value))
                                }
                            }
                            // This sibling belongs to the overlay and must never enter the page capture.
                            Box(Modifier.fillMaxSize().background(overlayColor.value))
                            Box(Modifier.align(Alignment.Center).size(96.dp, 48.dp).testTag("material")
                                .searchOverlaySurface(shape, Color.Transparent, settings = settings))
                        }
                    }
                }
            }
        }
        fun center(): Color {
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            repeat(2) { compose.onRoot().captureToImage() }
            val pixels = compose.onNodeWithTag("material").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        val red = center()
        assertTrue("Sibling overlay must sample the actual red search page: $red", red.red > red.blue + .06f)
        assertTrue("Page sample includes the recognition scrim", red.red < .3f)
        compose.runOnIdle { overlayColor.value = Color.Magenta }
        assertEquals("Overlay content must not contaminate its own material", red, center())
        compose.runOnIdle { pageColor.value = Color.Blue }
        val blue = center()
        assertTrue("Changed page pixels must reach the sibling material: $blue", blue.blue > blue.red + .06f)
        compose.runOnIdle { mounted.value = false }
        val detached = center()
        assertTrue("Disposed Skeleton must release its source", kotlin.math.abs(detached.red - detached.blue) < .04f)
        compose.runOnIdle { mounted.value = true }
        assertTrue("Remounted page publishes its new layer", center().blue > .06f)
        compose.runOnIdle { navigation.navigate("other") }
        val other = center()
        assertTrue("Another destination must not reuse search pixels", kotlin.math.abs(other.red - other.blue) < .04f)
        compose.runOnIdle { navigation.popBackStack() }
        val restored = center()
        assertTrue("Returning search page publishes its own capture", restored.blue > restored.red + .06f)
    }
}
