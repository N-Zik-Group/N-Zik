package app.n_zik.android.components.player

import android.Manifest
import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.extensions.audiobar.views.ProgressPercentage
import app.n_zik.android.extensions.audiobar.views.SeekBarStaticAudioWaves
import app.n_zik.android.extensions.audiobar.views.SeekBarVisualizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerSeekWaveMarkerTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun `visualizer resting marker matches audio waveform in their actual different height fields with Glass off and on`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        var glass by mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                val scope = rememberCoroutineScope()
                val motion = remember { PlayerSeekGlassMotion(scope, .5f) }
                val thumb = remember { PlayerSeekGlassThumb(motion) }
                Column(Modifier.requiredWidth(300.dp).background(Color.Black)) {
                    SeekBarStaticAudioWaves(uiMedia = null, position = 50, duration = 100,
                        isPlaying = false, onPositionChange = {},
                        modifier = Modifier.height(40.dp).testTag("waveform.reference"),
                        glassThumb = if (glass) thumb else null)
                    SeekBarVisualizer(isPlaying = false,
                        progressPercentage = { ProgressPercentage(.5f) },
                        playedColor = Color.White, notPlayedColor = Color.Gray, waveInteraction = {},
                        modifier = Modifier.height(50.dp).testTag("visualizer.comparison"),
                        glassThumb = if (glass) thumb else null)
                }
            }
        }
        fun markerSize(tag: String): Pair<Int, Int> {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            fun lit(x: Int, y: Int) = pixels[x, y].let { it.red + it.green + it.blue > .15f }
            // Eight dp above the baseline excludes both paused waveform amplitude fields.
            val row = pixels.height / 2 - with(compose.density) { 8.dp.roundToPx() }
            return (0 until pixels.width).count { lit(it, row) } to
                (0 until pixels.height).count { lit(pixels.width / 2, it) }
        }
        val reference = markerSize("waveform.reference")
        assertEquals(with(compose.density) { 2.dp.roundToPx() }, reference.first)
        assertEquals(with(compose.density) { 40.dp.roundToPx() }, reference.second)
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { glass = enabled }
            assertEquals("Correct waveform marker stays unchanged with Glass=$enabled", reference,
                markerSize("waveform.reference"))
            assertEquals("Visualizer marker matches waveform with Glass=$enabled", reference,
                markerSize("visualizer.comparison"))
        }
    }

    @Test fun `both waveform cursors keep their full native height at rest and while Glass is pressed`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        var visualizer by mutableStateOf(false)
        var glass by mutableStateOf(false)
        var held by mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                val scope = rememberCoroutineScope()
                val motion = remember { PlayerSeekGlassMotion(scope, .5f) }
                val track = rememberLayerBackdrop()
                val thumb = remember { PlayerSeekGlassThumb(motion, track) }
                LaunchedEffect(held) { if (held) motion.press() else motion.release() }
                Column(Modifier.requiredWidth(300.dp).background(Color.Black)) {
                    val modifier = Modifier.height(40.dp).testTag("source")
                    if (visualizer) {
                        SeekBarVisualizer(isPlaying = false,
                            progressPercentage = { ProgressPercentage(.5f) },
                            playedColor = Color.White, notPlayedColor = Color.Gray,
                            waveInteraction = {}, modifier = modifier,
                            glassThumb = if (glass) thumb else null)
                    } else {
                        SeekBarStaticAudioWaves(uiMedia = null, position = 50, duration = 100,
                            isPlaying = false, onPositionChange = {}, modifier = modifier,
                            glassThumb = if (glass) thumb else null)
                    }
                    Canvas(Modifier.requiredWidth(300.dp).height(40.dp).testTag("capture")) {
                        drawLayer(track.graphicsLayer)
                    }
                }
            }
        }
        for (realVisualizer in listOf(false, true)) {
            compose.runOnIdle { visualizer = realVisualizer; glass = false; held = false }
            assertMarker("source", offsetDp = 15f, present = true)
            compose.runOnIdle { glass = true }
            assertMarker("source", offsetDp = 3f, present = true)
            assertMarker("source", offsetDp = 15f, present = true)
            compose.runOnIdle { held = true }
            compose.mainClock.advanceTimeBy(700)
            assertMarker("capture", offsetDp = 12f, present = true)
            assertMarker("capture", offsetDp = 18f, present = true)
            compose.runOnIdle { held = false }
            compose.mainClock.advanceTimeBy(700)
            assertMarker("source", offsetDp = 15f, present = true)
            compose.runOnIdle { glass = false; held = false }
            assertMarker("source", offsetDp = 15f, present = true)
        }
    }

    private fun assertMarker(tag: String, offsetDp: Float, present: Boolean) {
        val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val offset = with(compose.density) { offsetDp.dp.toPx() }.toInt()
        val pixel = image[image.width / 2, image.height / 2 + offset]
        val brightness = pixel.red + pixel.green + pixel.blue
        if (present) assertTrue("$tag marker missing at $offsetDp dp", brightness > .15f)
        else assertEquals("$tag cursor must fit the ball", 0f, brightness, .05f)
    }
}
