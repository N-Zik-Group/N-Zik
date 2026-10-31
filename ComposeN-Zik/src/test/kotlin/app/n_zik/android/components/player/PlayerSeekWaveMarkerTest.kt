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

    @Test fun `both waveform cursors stay full height at rest and contract inside the captured Glass ball while pressed`() {
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
            assertMarker("capture", offsetDp = 18f, present = false)
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
