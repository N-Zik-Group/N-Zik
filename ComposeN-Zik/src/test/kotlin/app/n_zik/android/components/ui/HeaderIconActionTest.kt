package app.n_zik.android.components.ui

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class HeaderIconActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `share has one full native feedback canvas while the Glass toolbar keeps its existing seat`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val roundness = mutableStateOf(0.dp)
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(roundness.value)
            val settings = GlassSettings(quality = GlassQuality.Performance,
                areaModes = mapOf(GlassArea.ActionButtons to mode.value))
            CompositionLocalProvider(LocalAppearance provides Appearance(ColorPalette.fallback,
                Typography.fallback, shape, shape, shape), LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    GlassToolbarRow(1, settings = settings) {
                        HeaderIconAction({ clicks++ }, R.drawable.share_social, Color.Blue,
                            contentDescription = "share", iconSize = 24.dp)
                    }
                }
            }
        }
        for (radius in listOf(0.dp, 24.dp)) {
            compose.runOnIdle { roundness.value = radius }
            for (nativeMode in listOf(GlassAreaMode.Disabled, GlassAreaMode.ContourOnly)) {
                compose.runOnIdle { mode.value = nativeMode }
                val action = compose.onNodeWithContentDescription("share")
                action.assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
                compose.onNodeWithContentDescription("share", useUnmergedTree = true)
                    .assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
                repeat(4) { edge -> action.performTouchInput {
                    click(when (edge) {
                        0 -> Offset(1f, center.y)
                        1 -> Offset(width - 1f, center.y)
                        2 -> Offset(center.x, 1f)
                        else -> Offset(center.x, height - 1f)
                    })
                } }
            }
            for (glassMode in listOf(GlassAreaMode.GlassOnly, GlassAreaMode.GlassAndContour)) {
                compose.runOnIdle { mode.value = glassMode }
                compose.onNodeWithContentDescription("share").assertWidthIsEqualTo(40.dp)
                    .assertHeightIsEqualTo(40.dp).performTouchInput { click() }
            }
        }
        compose.runOnIdle { assertEquals(20, clicks) }
    }
}
