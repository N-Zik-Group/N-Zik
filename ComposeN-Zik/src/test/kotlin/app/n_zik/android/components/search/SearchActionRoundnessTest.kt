package app.n_zik.android.components.search

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchActionRoundnessTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `mic wave and clear actions keep matching forty eight dp UI outlines in native and Glass`() {
        val radius = mutableStateOf(0f)
        val glass = mutableStateOf(false)
        val clicks = IntArray(3)
        val icons = listOf(R.drawable.microphone, R.drawable.sound_effect, R.drawable.close)
        compose.setContent {
            val restored = Appearance.restore(listOf(null, null, 12f, radius.value, 48f))
            val appearance = restored.copy(colorPalette = restored.colorPalette.copy(
                background0 = Color.White, background1 = Color.White, background2 = Color.White))
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Row(Modifier.background(Color.Black)) {
                    icons.forEachIndexed { index, icon ->
                        SearchBarBubbleButton(SearchBarBubble(icon, R.string.clear) { clicks[index]++ },
                            GlassSettings(enabled = glass.value, quality = GlassQuality.Performance,
                                film = 1f, rim = 0f, blur = 0f, depth = 0f, amount = 0f))
                    }
                    Box(Modifier.size(48.dp).testTag("expected.ui.shape")
                        .clip(appearance.uiRoundnessShape).background(Color.White))
                }
            }
        }
        for (enabled in listOf(false, true)) for (value in listOf(0f, 8f, 48f)) {
            compose.runOnIdle { glass.value = enabled; radius.value = value }
            val expected = compose.onNodeWithTag("expected.ui.shape").captureToImage().toPixelMap()
            icons.forEachIndexed { index, icon ->
                val node = compose.onNodeWithTag("unified_search_bubble_$icon")
                node.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
                val actual = node.captureToImage().toPixelMap()
                val scale = actual.width / 48f
                // Samples avoid glyphs and shape antialiasing. At UI48, (6dp,6dp) is inside
                // the ordinary .4 corner and outside the former .5 circular bubble.
                for ((dx,dy) in listOf(0f to 0f, 6f to 6f, 24f to 2f)) {
                    val x = (dx * scale).toInt()
                    val y = (dy * scale).toInt()
                    assertEquals("icon=$icon Glass=$enabled UI=$value corner=$dx,$dy must match production UI outline",
                        expected[x,y].red > .7f, actual[x,y].red > .7f)
                }
                val before = clicks[index]
                for (edge in 0..3) node.performTouchInput {
                    click(when(edge) {
                        0 -> Offset(1f, center.y)
                        1 -> Offset(width - 1f, center.y)
                        2 -> Offset(center.x, 1f)
                        else -> Offset(center.x, height - 1f)
                    })
                }
                compose.runOnIdle { assertEquals(before + 4, clicks[index]) }
            }
            val bounds = icons.map { compose.onNodeWithTag("unified_search_bubble_$it").fetchSemanticsNode().boundsInRoot }
            bounds.zipWithNext().forEach { (left,right) -> assertTrue(left.right <= right.left) }
        }
    }
}
