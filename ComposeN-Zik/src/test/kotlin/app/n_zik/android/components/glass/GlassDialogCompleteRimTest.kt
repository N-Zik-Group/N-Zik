package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassDialogCompleteRimTest {
    @get:Rule val compose = createComposeRule()
    private val radius = mutableStateOf(24.dp)
    private val rim = mutableStateOf(0f)

    @Test fun `actual shared DialogFrame keeps its bottom and lower corner rim`() {
        lateinit var sharedHost: GlassMenuHostState
        compose.setContent {
            // Compare settled presentations, not an Android-window-to-shared-host
            // handoff while changing the test's contour configuration.
            key(radius.value, rim.value) {
                val shape = RoundedCornerShape(radius.value)
                val rootView = LocalView.current.rootView
                val safeBackdrop = rememberCanvasBackdrop { drawRect(Color.Black) }
                val host = remember(rootView, safeBackdrop) {
                    GlassMenuHostState().apply {
                        backdrop = safeBackdrop
                        dialogs.windowRoot = rootView
                    }
                }
                sharedHost = host
                val settings = GlassSettings(film = 0f, blur = 0f, vibrancy = 0f,
                    depth = 0f, amount = 0f, rim = rim.value)
                CompositionLocalProvider(
                    LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                    LocalGlassPreviewSettings provides settings,
                    LocalGlassMenuHost provides host,
                    LocalGlassBackdrop provides safeBackdrop,
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("dialog.rim.scene")) {
                        DialogFrame(onDismiss = {}, bodyPadding = PaddingValues(0.dp)) {
                            Box(Modifier.fillMaxWidth().height(120.dp))
                        }
                        GlassDialogOverlayHost(host)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("Fixture must exercise the optical shared dialog", sharedHost.dialogs.hasDialogs) }
        assertCompleteRim("dialogFrameBody")
    }

    @Test fun `fallback dialog button keeps its complete rim`() {
        compose.setContent {
            val shape = RoundedCornerShape(radius.value)
            val settings = GlassSettings(quality = GlassQuality.Performance, film = 0f,
                rim = rim.value)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
            ) {
                Box(Modifier.background(Color.Black).testTag("dialog.rim.scene")) {
                    GlassDialogOutlinedButton({},
                        Modifier.size(120.dp, 64.dp).testTag("fallback.button")
                            .glassDialogButtonBackground(Color.Black, shape, settings = settings),
                        shape = shape, border = null, contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                    ) {}
                }
            }
        }
        assertCompleteRim("fallback.button")
    }

    private fun assertCompleteRim(tag: String) {
        for (roundness in listOf(0.dp, 24.dp)) {
            compose.runOnIdle { radius.value = roundness; rim.value = 0f }
            compose.waitForIdle()
            val baseline = capture(tag)
            for (strength in listOf(.2f, 1f)) {
                compose.runOnIdle { rim.value = strength }
                compose.waitForIdle()
                val visible = capture(tag)
                val center = visible.width / 2
                val middle = visible.height / 2
                val tail = if (roundness.value == 0f) 2 else 20
                val points = listOf(
                    center to 0, 0 to middle, visible.width - 1 to middle,
                    center to visible.height - 1,
                    tail to 0, visible.width - 1 - tail to 0,
                    tail to visible.height - 1, visible.width - 1 - tail to visible.height - 1,
                )
                points.forEach { (x, y) ->
                    assertTrue("$tag radius=$roundness rim=$strength must retain contour at ($x,$y): " +
                        "off=${baseline[x, y]} on=${visible[x, y]}",
                        visible[x, y].red - baseline[x, y].red > .004f)
                }
            }
        }
    }

    private fun capture(tag: String): PixelMap {
        // Shared dialogs have dialog semantics but no separate Android window.
        // Capture their activity scene, then read the same target rectangle.
        val scene = compose.onNodeWithTag("dialog.rim.scene")
        val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        return scene.captureToImage().toPixelMap(
            startX = (target.left - sceneBounds.left).roundToInt(),
            startY = (target.top - sceneBounds.top).roundToInt(),
            width = target.width.roundToInt(),
            height = target.height.roundToInt(),
        )
    }
}
