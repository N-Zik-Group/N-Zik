package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import app.n_zik.android.components.ui.UiIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.n_zik.android.R
import app.n_zik.android.components.player.GlassMiniPlayerPlayPauseButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class SmallActionShapePolicyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `production appearance and MiniPlayer reference preserve shape policy with distinct native seats`() {
        val glass = mutableStateOf(false)
        val radius = mutableStateOf(48f)
        var clicks = 0
        compose.setContent {
            val appearance = Appearance.restore(listOf(null, null, 12f, radius.value, 48f))
            val settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance)
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassPreviewSettings provides settings) {
                Column {
                    GlassMiniPlayerPlayPauseButton({ clicks++ }, Color.Gray, "reference.play", settings) {}
                    GlassOverlayIconButton({ clicks++ }, R.drawable.settings, Color.White,
                        Modifier.size(32.dp).testTag("overlay.seat"), settings = settings)
                }
            }
        }
        for (enabled in listOf(false, true)) for (value in listOf(48f, 0f, 8f)) {
            compose.runOnIdle { glass.value = enabled; radius.value = value }
            compose.onNodeWithContentDescription("reference.play").assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
            compose.onNodeWithTag("overlay.seat", useUnmergedTree = true).assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
            compose.onAllNodes(hasClickAction()).assertCountEquals(2)
            val expected = Appearance.restore(listOf(null, null, 12f, value, 48f)).uiRoundnessShape
            for (canvas in listOf(40f, 42f)) {
                val outline = expected.createOutline(Size(canvas, canvas), LayoutDirection.Ltr, Density(1f))
                val actual = when (outline) {
                    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                    is Outline.Rectangle -> 0f
                    is Outline.Generic -> error("Unexpected production UI shape")
                }
                assertEquals(minOf(value, canvas * .4f), actual, .001f)
            }
        }
        compose.onNodeWithContentDescription("reference.play").performTouchInput { click() }
        compose.onNodeWithTag("overlay.seat", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test fun `shared direct action adapter retains exact Material geometry and callback in both modes`() {
        val glass = mutableStateOf(false)
        val radius = mutableStateOf(48f)
        var clicks = 0
        compose.setContent {
            val appearance = Appearance.restore(listOf(null, null, 12f, radius.value, 48f))
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = glass.value, quality = GlassQuality.Performance)) {
                Row {
                    UiIconButton({ clicks++ }, Modifier.testTag("ui.action")) {
                        Icon(painterResource(R.drawable.close), null, Modifier.size(20.dp))
                    }
                    androidx.compose.material3.IconButton({ clicks++ }, Modifier.testTag("material.baseline"),
                        shape = appearance.uiRoundnessShape) {
                        Icon(painterResource(R.drawable.close), null, Modifier.size(20.dp))
                    }
                    GlassDialogIconButton({ clicks++ }, Modifier.testTag("dialog.action")) {
                        Icon(painterResource(R.drawable.close), null, Modifier.size(20.dp))
                    }
                }
            }
        }
        for (enabled in listOf(false, true)) for (value in listOf(0f, 8f, 48f)) {
            compose.runOnIdle { glass.value = enabled; radius.value = value }
            val baseline = compose.onNodeWithTag("material.baseline").fetchSemanticsNode().boundsInRoot
            for (tag in listOf("ui.action", "dialog.action")) {
                val action = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertEquals("An icon policy adapter cannot expand the caller's reserved width", baseline.width, action.width, .1f)
                assertEquals("An icon policy adapter cannot expand the caller's reserved height", baseline.height, action.height, .1f)
            }
        }
        listOf("ui.action", "material.baseline", "dialog.action").forEach {
            compose.onNodeWithTag(it).performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(3, clicks) }
    }

}
