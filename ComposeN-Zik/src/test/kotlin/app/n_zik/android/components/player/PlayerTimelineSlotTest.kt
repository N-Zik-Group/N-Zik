package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w900dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerTimelineSlotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `buffering indicator preserves each native timeline footprint in portrait and landscape widths`() {
        val timeline = mutableStateOf(PlayerTimelineType.Wavy)
        val loading = mutableStateOf(false)
        val playing = mutableStateOf(true)
        val width = mutableStateOf(360.dp)
        val heights = mapOf(
            PlayerTimelineType.Default to 6.dp, PlayerTimelineType.Wavy to 6.dp,
            PlayerTimelineType.ThinBar to 6.dp, PlayerTimelineType.ColoredBar to 6.dp,
            PlayerTimelineType.PinBar to 15.dp, PlayerTimelineType.BodiedBar to 15.dp,
            PlayerTimelineType.AudioWaves to 40.dp, PlayerTimelineType.VisualizerBar to 50.dp,
        )
        compose.setContent {
            Column(Modifier.requiredSize(width.value, 200.dp)) {
                Box(Modifier.fillMaxWidth().height(30.dp).testTag("information"))
                PlayerTimelineSlot(timeline.value, Modifier.fillMaxWidth().testTag("timeline")) {
                    if (loading.value) {
                        if (playing.value) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth())
                    } else Box(Modifier.fillMaxWidth().height(heights.getValue(timeline.value)))
                }
                Box(Modifier.fillMaxWidth().height(40.dp).testTag("controls"))
            }
        }
        for (w in listOf(360.dp, 640.dp)) for (type in PlayerTimelineType.entries) {
            compose.runOnIdle { width.value = w; timeline.value = type; loading.value = false }
            val info = compose.onNodeWithTag("information").fetchSemanticsNode().boundsInRoot
            val control = compose.onNodeWithTag("controls").fetchSemanticsNode().boundsInRoot
            val track = compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot
            assertEquals(heights.getValue(type).value * compose.density.density, track.height, 1f)
            for (play in listOf(true, false)) {
                compose.runOnIdle { loading.value = true; playing.value = play }
                assertEquals("Information stays put for $w $type $play", info,
                    compose.onNodeWithTag("information").fetchSemanticsNode().boundsInRoot)
                assertEquals("Transport stays put for $w $type $play", control,
                    compose.onNodeWithTag("controls").fetchSemanticsNode().boundsInRoot)
                assertEquals(track, compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot)
            }
            compose.runOnIdle { loading.value = false }
            assertEquals(control, compose.onNodeWithTag("controls").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun `short native track leaves optical and waveform visual overflow visible`() {
        compose.setContent {
            Box(Modifier.requiredSize(100.dp, 100.dp).background(Color.Black).testTag("canvas")) {
                Column {
                    Box(Modifier.height(30.dp))
                    PlayerTimelineSlot(PlayerTimelineType.Wavy, Modifier.fillMaxWidth()) {
                        Box(Modifier.requiredSize(30.dp, 30.dp).background(Color.Magenta))
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()
        val density = compose.density.density
        assertEquals(Color.Magenta, pixels[(50 * density).toInt(), (22 * density).toInt()])
        assertEquals(Color.Magenta, pixels[(50 * density).toInt(), (44 * density).toInt()])
    }
}
