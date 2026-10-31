package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w500dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerGlassBudgetLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `compact portrait yields cover height to actual optional bottom content`() {
        val nextTrack = mutableStateOf(false)
        compose.setContent {
            PlayerGlassBudgetLayout(enabled = true, modifier = Modifier.requiredSize(400.dp, 600.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = { Box(Modifier.size(400.dp).testTag("cover")) },
                information = {
                    Column {
                        Box(Modifier.size(400.dp, 220.dp).testTag("controls"))
                        if (nextTrack.value) Box(Modifier.size(400.dp, 40.dp).testTag("next"))
                        Box(Modifier.size(400.dp, 56.dp).testTag("actions"))
                    }
                })
        }
        val density = compose.density.density
        val firstCover = compose.onNodeWithTag("cover").fetchSemanticsNode().boundsInRoot
        assertEquals(274f * density, firstCover.height, 1f)
        compose.runOnIdle { nextTrack.value = true }
        val cover = compose.onNodeWithTag("cover").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("controls").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("next").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        assertEquals(234f * density, cover.height, 1f)
        assertEquals(220f * density, controls.height, 1f)
        assertTrue(cover.bottom <= controls.top + 1f)
        assertTrue(controls.bottom <= next.top + 1f)
        assertTrue(next.bottom <= actions.top + 1f)
        assertEquals(600f * density, actions.bottom, 1f)
    }

    @Test
    fun `tall native carousel keeps the selected square cover size without Glass`() {
        compose.setContent {
            PlayerGlassBudgetLayout(enabled = false, expandedCarousel = true,
                modifier = Modifier.requiredSize(400.dp, 800.dp),
                header = { Box(Modifier.size(400.dp, 50.dp)) },
                artwork = { Box(Modifier.size(400.dp).testTag("cover")) },
                information = { Box(Modifier.size(400.dp, 250.dp)) })
        }
        val cover = compose.onNodeWithTag("cover").fetchSemanticsNode().boundsInRoot
        assertEquals(400f * compose.density.density, cover.height, 1f)
    }

    @Test
    fun `full cover preset paints to bottom of loose sheet and anchors lower content there`() {
        compose.setContent {
            Box(Modifier.requiredSize(400.dp, 800.dp).background(Color.Black)) {
                PlayerGlassBudgetLayout(enabled = true,
                    modifier = Modifier.fillMaxWidth().background(Color.Magenta).testTag("player"),
                    header = { Box(Modifier.size(400.dp, 50.dp)) },
                    artwork = { Box(Modifier.size(400.dp).testTag("preset.cover")) },
                    information = { Box(Modifier.size(400.dp, 250.dp).testTag("preset.lower")) })
            }
        }
        val player = compose.onNodeWithTag("player").fetchSemanticsNode().boundsInRoot
        val lower = compose.onNodeWithTag("preset.lower").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("preset.cover").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        assertEquals(800f * density, player.height, 1f)
        assertEquals(player.bottom, lower.bottom, 1f)
        assertEquals(400f * density, cover.height, 1f)
        val pixels = compose.onNodeWithTag("player").captureToImage().toPixelMap()
        assertEquals(Color.Magenta, pixels[(50f * density).toInt(), (790f * density).toInt()])
    }

}
