@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class)

package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.screens.player.rememberPlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.CustomBottomSheet
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassNavigationContent
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.navigation.AppHeaderOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerDeployNavigationBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real deploying sheet samples moving navigation and falls back to the page when unavailable`() {
        val host = GlassMenuHostState()
        val navigationY = mutableStateOf(220)
        val navigationColor = mutableStateOf(Color.Green)
        val captureNavigation = mutableStateOf(true)
        lateinit var sheet: PlayerSheetState
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val page = rememberLayerBackdrop()
            SideEffect { host.backdrop = page }
            CompositionLocalProvider(
                LocalGlassMenuHost provides host,
                LocalGlassPreviewSettings provides GlassSettings(film = 0f, blur = 0f,
                    vibrancy = 0f, depth = 0f, amount = 0f, rim = 0f),
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback,
                    shape, shape, shape),
            ) {
                sheet = rememberPlayerSheetState(0.dp, 320.dp, 60.dp)
                Box(Modifier.size(240.dp, 320.dp).testTag("deploy.viewport")) {
                    GlassNavigationContent(host, captureNavigation.value) {
                        AppHeaderOverlay(header = {}, navigation = {
                            Box(Modifier.offset { IntOffset(0, navigationY.value) }
                                .fillMaxWidth().height(32.dp)
                                .drawBehind { drawRect(navigationColor.value) })
                        }) {
                            // The fallback contains only blue; navigation is recorded by the outer host.
                            Box(Modifier.fillMaxSize().layerBackdrop(page).background(Color.Blue))
                        }
                    }
                    // Exercise the actual source selection and moving deploy surface in the sheet.
                    CustomBottomSheet(sheet, collapsedContent = {}) {}
                }
            }
        }
        compose.runOnIdle { sheet.snapTo(112.dp) }
        val deploy = compose.onNodeWithTag("player_deploy_glass")
        val viewportTop = compose.onNodeWithTag("deploy.viewport").fetchSemanticsNode().boundsInRoot.top
        val top = (deploy.fetchSemanticsNode().boundsInRoot.top - viewportTop).roundToInt()
        compose.runOnIdle { navigationY.value = top + 16 }

        fun assertReflected(topColor: Color, bottomColor: Color) {
            val pixels = deploy.captureToImage().toPixelMap()
            fun check(expected: Color, actual: Color) {
                assertEquals(expected.red, actual.red, .04f)
                assertEquals(expected.green, actual.green, .04f)
                assertEquals(expected.blue, actual.blue, .04f)
            }
            check(topColor, pixels[pixels.width / 2, 32])
            check(bottomColor, pixels[pixels.width / 2, 80])
        }
        compose.runOnIdle { assertNotNull(host.miniPlayerBackdrop) }
        assertReflected(Color.Green, Color.Blue)
        compose.runOnIdle { navigationColor.value = Color.Red }
        assertReflected(Color.Red, Color.Blue)
        compose.runOnIdle { navigationY.value = top + 64 }
        assertReflected(Color.Blue, Color.Red)

        // A subsequent card movement must resample window coordinates, not retain old pixels.
        compose.runOnIdle { sheet.snapTo(125.dp) }
        val movedTop = (deploy.fetchSemanticsNode().boundsInRoot.top - viewportTop).roundToInt()
        compose.runOnIdle { navigationY.value = movedTop + 16 }
        assertReflected(Color.Red, Color.Blue)

        compose.runOnIdle { captureNavigation.value = false }
        compose.runOnIdle { assertNull(host.miniPlayerBackdrop) }
        assertReflected(Color.Blue, Color.Blue)
        compose.runOnIdle { captureNavigation.value = true }
        assertReflected(Color.Red, Color.Blue)
    }
}
